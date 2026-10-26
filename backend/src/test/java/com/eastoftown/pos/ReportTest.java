package com.eastoftown.pos;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import org.springframework.http.MediaType;
import java.nio.file.*;
import java.io.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.rendering.PDFRenderer;
import javax.imageio.ImageIO;

@SpringBootTest @AutoConfigureMockMvc @TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ReportTest {
    static final Path DATABASE;static {try{DATABASE=Files.createTempDirectory("eot-stage4-tests-").resolve("test.db");}catch(Exception e){throw new RuntimeException(e);}}
    static final String TOKEN="stage4-isolated-tests-api-secret-0123456789";
    @DynamicPropertySource static void properties(DynamicPropertyRegistry r){r.add("spring.datasource.url",()->"jdbc:sqlite:"+DATABASE);r.add("eot.api-token",()->TOKEN);}
    @Autowired ReportService reports;@Autowired CustomerService customers;@Autowired BillingService billing;@Autowired CatalogRepository repo;@Autowired CatalogService catalog;@Autowired AuthService auth;@Autowired ReportExport exports;@Autowired MockMvc mvc;@Autowired ObjectMapper json;
    AuthService.User owner;String session,billingSession,a,b,customer,invoice;
    final LocalDate day=LocalDate.of(2026,10,1);
    ReportService.Range range(){return ReportService.range("custom",day,day,day);}
    @BeforeAll void setup(){owner=auth.setup("test-owner","test-only-owner-password");auth.create("test-biller","test-only-biller-password","BILLING");session=auth.login("test-owner","test-only-owner-password").get("session").toString();billingSession=auth.login("test-biller","test-only-biller-password").get("session").toString();}
    @BeforeEach void fixtures(){
        for(String table:List.of("checkout_requests","customer_changes","invoice_items","payments","stock_movements","invoices","customers","variant_taxes","product_variants","products"))repo.jdbc().update("DELETE FROM "+table);
        a=catalog.save(null,new CatalogService.VariantInput(null,"TEST ONLY Known shirt","shirts","TEST-KNOWN","M","Blue",null,new BigDecimal("60"),new BigDecimal("100"),10,9,true,0),owner.id());
        b=catalog.save(null,new CatalogService.VariantInput(null,"TEST ONLY Unknown cost tee","tshirts","TEST-UNKNOWN","L","Black",null,null,new BigDecimal("50"),10,2,true,0),owner.id());
        customer=((Map<?,?>)billing.customer(new BillingService.CustomerInput("TEST ONLY Customer","9999900001",null),owner.id())).get("id").toString();
        billing.tax(new BillingService.Tax(a,500),owner.id());
        invoice=complete(new BillingService.Cart(List.of(new BillingService.Line(a,2,1000)),1000,customer),List.of(new BillingService.Payment(BillingService.Method.CASH,10000),new BillingService.Payment(BillingService.Method.CARD,8900)),day.atStartOfDay(ReportService.ZONE).toInstant().plusMillis(100));
        complete(new BillingService.Cart(List.of(new BillingService.Line(b,1,0)),0,null),List.of(new BillingService.Payment(BillingService.Method.UPI,5000)),day.atTime(12,0).atZone(ReportService.ZONE).toInstant());
    }
    @SuppressWarnings("unchecked") String complete(BillingService.Cart cart,List<BillingService.Payment> payments,Instant at){var q=billing.quote(cart,owner);var result=(Map<String,Object>)billing.checkout(new BillingService.Checkout(UUID.randomUUID(),cart,q.token(),payments),owner);String id=((Map<?,?>)result.get("invoice")).get("id").toString();repo.jdbc().update("UPDATE invoices SET created_at=? WHERE id=?",at.toString(),id);repo.jdbc().update("UPDATE payments SET created_at=? WHERE invoice_id=?",at.toString(),id);repo.jdbc().update("UPDATE stock_movements SET created_at=? WHERE reference_id=?",at.toString(),id);return id;}
    @SuppressWarnings("unchecked") Map<String,Object> dashboard(){return (Map<String,Object>)reports.dashboard(range());}
    @SuppressWarnings("unchecked") Map<String,Object> part(String name){return (Map<String,Object>)dashboard().get(name);}
    long n(Map<String,Object> r,String key){return ReportService.n(r,key);}
    @Test void exactDashboardAndCollectionsReconcile(){var s=part("sales");assertEquals(25000,n(s,"gross_paise"));assertEquals(2000,n(s,"discount_paise"));assertEquals(23000,n(s,"net_sales_paise"));assertEquals(900,n(s,"tax_paise"));assertEquals(23900,n(s,"total_paise"));assertEquals(2,n(s,"bills"));assertEquals(11950,n(s,"average_paise"));var rows=reports.report("payments",range(),null).rows();assertEquals(23900,rows.stream().mapToLong(x->n(x,"collections_paise")).sum());assertEquals(3,rows.size());}
    @Test void productCategoryAndVariantTotalsMatchInvoices(){for(String type:List.of("products","categories","variants")){var rows=reports.report(type,range(),null).rows();assertEquals(2,rows.size());assertEquals(3,ReportService.sum(rows,"quantity"));assertEquals(25000,ReportService.sum(rows,"gross_paise"));assertEquals(23000,ReportService.sum(rows,"net_sales_paise"));assertEquals(900,ReportService.sum(rows,"tax_paise"));assertEquals(23900,ReportService.sum(rows,"total_paise"));}}
    @Test void profitUsesHistoricalCostsAndMarksUnknown(){repo.jdbc().update("UPDATE product_variants SET cost_paise=99999,selling_paise=88888 WHERE id=?",a);var rows=reports.report("profit",range(),null).rows();assertEquals(18000,ReportService.sum(rows,"known_revenue_paise"));assertEquals(12000,ReportService.sum(rows,"known_cost_paise"));assertEquals(6000,ReportService.sum(rows,"profit_estimate_paise"));assertEquals(1,ReportService.sum(rows,"unknown_cost_units"));}
    @Test void inventoryUsesLedgerAndCurrentCostsIncludingInactive(){repo.jdbc().update("UPDATE product_variants SET active=0 WHERE id=?",b);var inv=part("inventory");assertEquals(17,n(inv,"units"));assertEquals(48000,n(inv,"known_cost_value_paise"));assertEquals(9,n(inv,"unknown_cost_units"));assertEquals(1,n(dashboard(),"lowStockCount"));var rows=reports.report("inventory",range(),null).rows();assertEquals(2,rows.size());assertEquals(17,ReportService.sum(rows,"units"));assertTrue(rows.stream().filter(x->x.get("id").equals(b)).findFirst().orElseThrow().get("value_paise")==null);}
    @Test void dailyMonthlyYearlyUseIndianDateAndExactBoundary(){for(String type:List.of("daily","monthly","yearly")){var rows=reports.report(type,range(),null).rows();assertEquals(1,rows.size());assertEquals(23900,n(rows.getFirst(),"total_paise"));}repo.jdbc().update("UPDATE invoices SET created_at=? WHERE id=?",day.plusDays(1).atStartOfDay(ReportService.ZONE).toInstant().toString(),invoice);assertEquals(5000,n(part("sales"),"total_paise"));}
    @Test void emptyRangeDoesNotFabricateSales(){var r=ReportService.range("custom",day.minusDays(1),day.minusDays(1),day);assertEquals(0,n((Map<String,Object>)((Map<?,?>)reports.dashboard(r)).get("sales"),"total_paise"));assertTrue(reports.report("daily",r,null).rows().isEmpty());}
    @Test void datePresetsHandleWeekAndFinancialYear(){assertEquals(LocalDate.of(2026,9,28),ReportService.range("week",null,null,day).from());assertEquals(LocalDate.of(2025,4,1),ReportService.range("financial-year",null,null,LocalDate.of(2026,3,31)).from());assertEquals(LocalDate.of(2026,4,1),ReportService.range("financial-year",null,null,LocalDate.of(2026,4,1)).from());assertEquals(day.minusDays(1),ReportService.range("yesterday",null,null,day).from());assertThrows(IllegalArgumentException.class,()->ReportService.range("custom",day,day.minusDays(1),day));assertThrows(IllegalArgumentException.class,()->ReportService.range("custom",null,null,day));assertThrows(IllegalArgumentException.class,()->reports.report("returns",range(),null));}
    @Test void correctedCustomerKeepsHistorySnapshotsAndAudit(){var corrected=(Map<?,?>)customers.correct(customer,new CustomerService.Correction("Corrected","9999900002","test@example.test",0),owner.id());assertEquals(1,((Number)corrected.get("version")).intValue());var result=(Map<?,?>)customers.detail(customer,0);var summary=(Map<String,Object>)result.get("summary");assertEquals(1,n(summary,"completed_purchases"));assertEquals(18900,n(summary,"amount_paise"));assertEquals("TEST ONLY Customer",((Map<?,?>)billing.invoice(invoice).get("invoice")).get("customer_name"));assertEquals(1,((List<?>)customers.changes(customer)).size());assertEquals(1,repo.jdbc().queryForObject("SELECT COUNT(*) FROM audit_logs WHERE action='CUSTOMER_CORRECTED' AND entity_id=?",Integer.class,customer));assertThrows(IllegalArgumentException.class,()->customers.correct(customer,new CustomerService.Correction("Stale","9999900003",null,0),owner.id()));assertEquals(1,((List<?>)customers.changes(customer)).size());}
    @Test void customerDuplicatesAndLiteralSearch(){assertEquals(1,((List<?>)customers.duplicates(" test only customer ")).size());assertEquals(0,((List<?>)((Map<?,?>)customers.search("%",0)).get("rows")).size());assertThrows(IllegalArgumentException.class,()->billing.customer(new BillingService.CustomerInput("Duplicate","9999900001",null),owner.id()));var other=(Map<?,?>)billing.customer(new BillingService.CustomerInput("Other","9999900002",null),owner.id());assertThrows(IllegalArgumentException.class,()->customers.correct(customer,new CustomerService.Correction("Same","9999900002",null,0),owner.id()));assertEquals(0,((List<?>)customers.changes(customer)).size());}
    @Test void customerReportsExcludeGuestsAndShowPaymentDetails(){var rows=reports.report("customers",range(),null).rows();assertEquals(1,rows.size());assertEquals(18900,n(rows.getFirst(),"total_paise"));var lines=reports.report("customer-history",range(),customer).rows();assertEquals(1,lines.size());assertEquals(2000,n(lines.getFirst(),"discount_total_paise"));assertTrue(lines.getFirst().get("payment_summary").toString().contains("CARD INR 89.00"));assertThrows(IllegalArgumentException.class,()->reports.report("customer-history",range(),null));}
    @Test void paginationNeverDropsOlderPurchases(){for(int i=0;i<51;i++)repo.jdbc().update("INSERT INTO invoices(id,invoice_number,status,subtotal_paise,discount_paise,tax_paise,total_paise,created_by,created_at,customer_id) VALUES (?,?,?,?,?,?,?,?,?,?)",UUID.randomUUID().toString(),"TEST-PAGE-"+i,"COMPLETED",0,0,0,0,owner.id(),BillingService.timestamp(Instant.now()),customer);var detail=(Map<?,?>)customers.detail(customer,1);assertEquals(52,((Number)detail.get("total")).intValue());assertEquals(2,((List<?>)detail.get("invoices")).size());assertThrows(IllegalArgumentException.class,()->customers.detail(customer,-1));}
    @Test void cancelledInvoicesAreExcludedFromSalesAndCustomerTotals(){repo.jdbc().update("UPDATE invoices SET status='CANCELLED' WHERE id=?",invoice);assertEquals(5000,n(part("sales"),"total_paise"));assertEquals(1,reports.report("payments",range(),null).rows().size());assertEquals(0,n((Map<String,Object>)((Map<?,?>)customers.detail(customer,0)).get("summary"),"completed_purchases"));assertEquals(1,((List<?>)((Map<?,?>)customers.detail(customer,0)).get("invoices")).size());}
    @Test void stockHistoryIncludesOnlySignedPeriodMovements(){var rows=reports.report("stock",range(),null).rows();assertEquals(2,rows.size());assertEquals(-3,ReportService.sum(rows,"quantity"));assertTrue(rows.stream().allMatch(x->x.get("kind").equals("SALE")));}
    @Test void excelExportsNumbersMissingCostsAndLiteralText()throws Exception{var report=reports.report("inventory",range(),null);try(var book=new XSSFWorkbook(new ByteArrayInputStream(exports.excel(report)))){assertEquals("Current units",book.getSheetAt(0).getRow(0).getCell(6).getStringCellValue());assertEquals(CellType.NUMERIC,book.getSheetAt(0).getRow(1).getCell(7).getCellType());assertEquals(60,book.getSheetAt(0).getRow(1).getCell(7).getNumericCellValue());assertEquals(CellType.BLANK,book.getSheetAt(0).getRow(2).getCell(7).getCellType());}var custom=new ReportService.Report("Test",range(),"Test",List.of(ReportService.text("x","Text")),List.of(ReportService.row("x","=HYPERLINK(\"bad\")")));try(var book=new XSSFWorkbook(new ByteArrayInputStream(exports.excel(custom)))){assertEquals(CellType.STRING,book.getSheetAt(0).getRow(1).getCell(0).getCellType());}}
    @Test void pdfHasRepeatedHeadersAndAllRowsWithoutTruncation()throws Exception{
        var source=reports.report("profit",range(),null);var known=source.rows().stream().filter(x->x.get("sku").equals("TEST-KNOWN")).findFirst().orElseThrow();
        List<Map<String,Object>> rows=new ArrayList<>();for(int i=0;i<130;i++){var row=new HashMap<>(known);row.put("name","TEST ONLY Long product description "+i+" cotton shirt extended description ".repeat(5));rows.add(row);}rows.getLast().put("name","FINAL TEST RECORD");
        var report=new ReportService.Report(source.title(),range(),source.note(),source.columns(),rows);byte[] bytes=exports.pdf(report);
        try(var doc=Loader.loadPDF(bytes)){Path path=Files.createDirectories(Path.of("target","test-fixtures"));Files.write(path.resolve("stage4-profit.pdf"),bytes);ImageIO.write(new PDFRenderer(doc).renderImageWithDPI(0,100),"png",path.resolve("stage4-profit.png").toFile());
            assertTrue(doc.getNumberOfPages()>1,"Long report must paginate");String text=new PDFTextStripper().getText(doc);assertTrue(text.replaceAll("\\s","").contains("FINALTESTRECORD"),"Final row must survive wrapping and pagination");assertTrue(text.contains("180.00"),"Known-cost discounted revenue must be exported");}
    }
    @Test void httpPermissionsValidationAndDownload()throws Exception{
        mvc.perform(get("/api/reports/dashboard").header("X-EOT-Token",TOKEN)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/reports/profit").header("X-EOT-Token",TOKEN).header("X-EOT-Session",billingSession)).andExpect(status().isForbidden());
        mvc.perform(put("/api/customers/"+customer).header("X-EOT-Token",TOKEN).header("X-EOT-Session",billingSession).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(new CustomerService.Correction("Denied","9999900002",null,0)))).andExpect(status().isForbidden());
        mvc.perform(get("/api/customers/"+customer).header("X-EOT-Token",TOKEN).header("X-EOT-Session",billingSession)).andExpect(status().isOk());
        mvc.perform(get("/api/reports/daily?preset=custom&from=bad&to=2026-10-01").header("X-EOT-Token",TOKEN).header("X-EOT-Session",session)).andExpect(status().isBadRequest());
        mvc.perform(get("/api/reports/daily/export?format=pdf&preset=custom&from=2026-10-01&to=2026-10-01").header("X-EOT-Token",TOKEN).header("X-EOT-Session",session)).andExpect(status().isOk()).andExpect(content().contentType("application/pdf"));
        mvc.perform(get("/api/reports/daily/export?format=exe").header("X-EOT-Token",TOKEN).header("X-EOT-Session",session)).andExpect(status().isBadRequest());
    }
}
