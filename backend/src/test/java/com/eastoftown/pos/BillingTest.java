package com.eastoftown.pos;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.rendering.PDFRenderer;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.sqlite.SQLiteDataSource;
import java.nio.file.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import javax.imageio.ImageIO;

@SpringBootTest @AutoConfigureMockMvc @TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BillingTest {
    static final Path DATABASE;
    static {try{DATABASE=Files.createTempDirectory("eot-stage3-tests-").resolve("test.db");}catch(Exception e){throw new RuntimeException(e);}}
    static final String TOKEN="stage3-test-only-api-secret-0123456789abcdef";
    @DynamicPropertySource static void properties(DynamicPropertyRegistry r){r.add("spring.datasource.url",()->"jdbc:sqlite:"+DATABASE);r.add("eot.api-token",()->TOKEN);}
    @Autowired BillingService billing;@Autowired CatalogService catalog;@Autowired CatalogRepository repo;@Autowired StockService stock;@Autowired AuthService auth;@Autowired ReceiptService receipts;@Autowired MockMvc mvc;@Autowired ObjectMapper json;
    AuthService.User owner,cashier;String ownerSession,cashierSession;
    @BeforeAll void setup(){owner=auth.setup("test-owner","test-only-owner-password");cashier=auth.create("test-cashier","test-only-cashier-password","BILLING");ownerSession=auth.login(owner.username(),"test-only-owner-password").get("session").toString();cashierSession=auth.login(cashier.username(),"test-only-cashier-password").get("session").toString();}
    @BeforeEach void resetConfig(){billing.configure(new BillingService.Config("TEST ONLY East of Town","Test street","0000000000","Test receipt","EOT",0,80,"",false),owner.id());}
    String variant(String price,int qty){String sku="TEST-"+UUID.randomUUID();return catalog.save(null,new CatalogService.VariantInput(null,"TEST ONLY Long Cotton Shirt "+sku,"shirts",sku,"XL","Midnight blue",null,new BigDecimal("4.25"),new BigDecimal(price),qty,2,true,0),owner.id());}
    BillingService.Cart cart(String id,int qty,long discount){return new BillingService.Cart(List.of(new BillingService.Line(id,qty,0)),discount,null);}
    BillingService.Checkout request(BillingService.Cart cart){var q=billing.quote(cart,owner);return new BillingService.Checkout(UUID.randomUUID(),cart,q.token(),q.totalPaise()==0?List.of():List.of(new BillingService.Payment(BillingService.Method.CASH,q.totalPaise())));}
    @SuppressWarnings("unchecked") Map<String,Object> complete(BillingService.Checkout input){return (Map<String,Object>)billing.checkout(input,owner);}
    @SuppressWarnings("unchecked") Map<String,Object> header(Map<String,Object> sale){return (Map<String,Object>)sale.get("invoice");}
    int count(String table){return repo.jdbc().queryForObject("SELECT COUNT(*) FROM "+table,Integer.class);}
    @Test void mixedTaxesAndDiscountsReconcileToThePaise(){
        String a=variant("10.01",5),b=variant("19.99",5);billing.tax(new BillingService.Tax(a,500),owner.id());billing.tax(new BillingService.Tax(b,1200),owner.id());
        var cart=new BillingService.Cart(List.of(new BillingService.Line(a,2,101),new BillingService.Line(b,1,0)),301,null);var q=billing.quote(cart,owner);
        assertEquals(4001,q.subtotalPaise());assertEquals(402,q.discountPaise());assertEquals(301,q.items().stream().mapToLong(BillingService.Item::billDiscountPaise).sum());
        assertEquals(309,q.taxPaise());assertEquals(3908,q.totalPaise());assertEquals(q.totalPaise(),q.items().stream().mapToLong(BillingService.Item::lineTotalPaise).sum());
        var req=new BillingService.Checkout(UUID.randomUUID(),cart,q.token(),List.of(new BillingService.Payment(BillingService.Method.CASH,1000),new BillingService.Payment(BillingService.Method.UPI,2000),new BillingService.Payment(BillingService.Method.CARD,908)));
        var saved=complete(req);assertEquals(3908,BillingService.number(header(saved),"total_paise"));assertEquals(3,((List<?>)saved.get("payments")).size());assertEquals(3,stock.stock(a));assertEquals(4,stock.stock(b));
    }
    @Test void defaultTaxIsZeroAndHalfPaiseRoundsUp(){
        String id=variant("0.10",3);assertEquals(0,billing.quote(cart(id,1,0),owner).taxPaise());billing.tax(new BillingService.Tax(id,500),owner.id());assertEquals(1,billing.quote(cart(id,1,0),owner).taxPaise());
    }
    @Test void identicalRetryAndRestartLookupDoNotDeductAgain(){
        String id=variant("100.01",8);var input=request(cart(id,2,1));var first=complete(input);complete(input);
        assertEquals(6,stock.stock(id));assertEquals(1,repo.jdbc().queryForObject("SELECT COUNT(*) FROM stock_movements WHERE reference_id=? AND kind='SALE'",Integer.class,header(first).get("id")));
        SQLiteDataSource source=new SQLiteDataSource();source.setUrl("jdbc:sqlite:"+DATABASE);var independent=new JdbcTemplate(source);
        assertEquals(1,independent.queryForObject("SELECT COUNT(*) FROM checkout_requests WHERE request_key=?",Integer.class,input.requestKey().toString()));assertTrue((Boolean)((Map<?,?>)billing.requestStatus(input.requestKey(),owner)).get("found"));
        assertThrows(IllegalArgumentException.class,()->billing.checkout(input,cashier));
        var changed=new BillingService.Checkout(input.requestKey(),input.cart(),input.quoteToken(),List.of(new BillingService.Payment(BillingService.Method.UPI,20001)));
        assertThrows(IllegalArgumentException.class,()->complete(changed));
    }
    @Test void retryStillWorksAfterCatalogPriceChange(){
        String id=variant("10",2);var input=request(cart(id,1,0));var first=complete(input);repo.jdbc().update("UPDATE product_variants SET selling_paise=12345 WHERE id=?",id);assertEquals(header(first).get("id"),header(complete(input)).get("id"));assertEquals(1,stock.stock(id));
    }
    @Test void paymentMismatchAndInsufficientStockLeaveNoPartialInvoice(){
        String id=variant("10",1);var r=request(cart(id,1,0));int before=count("invoices");
        assertThrows(IllegalArgumentException.class,()->complete(new BillingService.Checkout(r.requestKey(),r.cart(),r.quoteToken(),List.of(new BillingService.Payment(BillingService.Method.CASH,1)))));assertEquals(before,count("invoices"));assertEquals(1,stock.stock(id));
        complete(r);assertThrows(IllegalArgumentException.class,()->billing.quote(cart(id,1,0),owner));assertEquals(0,stock.stock(id));
    }
    @Test void writeFailureAfterInvoiceAndMovementRollsBackEverything(){
        String id=variant("10",2);var input=request(cart(id,1,0));int invoices=count("invoices"),payments=count("payments"),moves=count("stock_movements");String sequence=repo.jdbc().queryForObject("SELECT value FROM application_settings WHERE key='invoice_sequence'",String.class);
        repo.jdbc().execute("CREATE TRIGGER test_payment_failure BEFORE INSERT ON payments BEGIN SELECT RAISE(ABORT,'Injected test failure'); END");
        try{assertThrows(RuntimeException.class,()->complete(input));}finally{repo.jdbc().execute("DROP TRIGGER test_payment_failure");}
        assertEquals(invoices,count("invoices"));assertEquals(payments,count("payments"));assertEquals(moves,count("stock_movements"));assertEquals(2,stock.stock(id));assertEquals(sequence,repo.jdbc().queryForObject("SELECT value FROM application_settings WHERE key='invoice_sequence'",String.class));assertFalse((Boolean)((Map<?,?>)billing.requestStatus(input.requestKey(),owner)).get("found"));
    }
    @Test void concurrentDuplicateCheckoutCommitsOnce() throws Exception {
        String id=variant("10",3);var input=request(cart(id,1,0));try(var executor=Executors.newFixedThreadPool(2)){
            var a=executor.submit(()->header(complete(input)).get("id"));var b=executor.submit(()->header(complete(input)).get("id"));assertEquals(a.get(20,TimeUnit.SECONDS),b.get(20,TimeUnit.SECONDS));
        }assertEquals(2,stock.stock(id));
    }
    @Test void competingSalesCannotOversellAndInvoiceNumbersAreSequential()throws Exception{
        String id=variant("10",1);var a=request(cart(id,1,0));var b=request(cart(id,1,0));try(var executor=Executors.newFixedThreadPool(2)){
            Callable<Boolean> first=()->{try{complete(a);return true;}catch(IllegalArgumentException e){return false;}};Callable<Boolean> second=()->{try{complete(b);return true;}catch(IllegalArgumentException e){return false;}};
            var results=executor.invokeAll(List.of(first,second));assertEquals(1,(results.get(0).get()?1:0)+(results.get(1).get()?1:0));
        }assertEquals(0,stock.stock(id));
        var x=header(complete(request(cart(variant("1",1),1,0)))).get("invoice_number").toString();var y=header(complete(request(cart(variant("1",1),1,0)))).get("invoice_number").toString();assertEquals(Long.parseLong(x.substring(x.lastIndexOf('-')+1))+1,Long.parseLong(y.substring(y.lastIndexOf('-')+1)));
    }
    @Test void stalePriceOrTaxRequiresFreshReview(){
        String id=variant("10",3);var r=request(cart(id,1,0));billing.tax(new BillingService.Tax(id,500),owner.id());assertThrows(IllegalArgumentException.class,()->complete(r));assertEquals(3,stock.stock(id));var r2=request(cart(id,1,0));repo.jdbc().update("UPDATE product_variants SET selling_paise=1100 WHERE id=?",id);assertThrows(IllegalArgumentException.class,()->complete(r2));
    }
    @Test void discountsAreAuthorizedAndOverDiscountsRejected(){
        String id=variant("10",5);assertThrows(IllegalArgumentException.class,()->billing.quote(cart(id,1,1),cashier));var c=billing.config();billing.configure(new BillingService.Config(c.shopName(),c.address(),c.phone(),c.thankYou(),c.invoicePrefix(),1000,80,"",false),owner.id());
        assertEquals(900,billing.quote(cart(id,1,100),cashier).totalPaise());assertThrows(IllegalArgumentException.class,()->billing.quote(cart(id,1,101),cashier));assertThrows(IllegalArgumentException.class,()->billing.quote(cart(id,1,1001),owner));
        assertThrows(IllegalArgumentException.class,()->billing.quote(new BillingService.Cart(List.of(new BillingService.Line(id,1,1001)),0,null),owner));
    }
    @Test void zeroTotalSaleNeedsNoPaymentAndStillRecordsStock(){
        String id=variant("10",1);var sale=complete(request(cart(id,1,1000)));assertEquals(0,BillingService.number(header(sale),"total_paise"));assertTrue(((List<?>)sale.get("payments")).isEmpty());assertEquals(0,stock.stock(id));
    }
    @Test void duplicateCustomersBlockedAndPurchaseRetainsCustomerSnapshot(){
        String mobile="9"+String.valueOf(System.nanoTime()).substring(0,9);var c=(Map<?,?>)billing.customer(new BillingService.CustomerInput("TEST ONLY Customer",mobile,null),owner.id());
        assertThrows(IllegalArgumentException.class,()->billing.customer(new BillingService.CustomerInput("Duplicate",mobile,null),owner.id()));
        String id=variant("10",2);var cart=new BillingService.Cart(List.of(new BillingService.Line(id,1,0)),0,c.get("id").toString());var sale=header(complete(request(cart)));
        repo.jdbc().update("UPDATE customers SET name='Corrected customer' WHERE id=?",c.get("id"));assertEquals("TEST ONLY Customer",sale.get("customer_name"));assertEquals(c.get("id"),sale.get("customer_id"));assertEquals(1,((List<?>)billing.history(mobile,null,null,1000L)).size());
    }
    @Test void savedReceiptsUseSnapshotsAtBothWidthsAndPrinterFailurePreservesSale()throws Exception{
        String id=variant("123.45",4);var sale=header(complete(request(cart(id,2,100))));String invoice=sale.get("id").toString();repo.jdbc().update("UPDATE product_variants SET selling_paise=1 WHERE id=?",id);
        for(int width:List.of(58,80))try(var pdf=Loader.loadPDF(receipts.pdf(invoice,width))){
            assertEquals(width*72/25.4,pdf.getPage(0).getMediaBox().getWidth(),0.01);String text=new PDFTextStripper().getText(pdf);assertTrue(text.contains("123.45"));assertTrue(text.contains("245.90"));assertTrue(text.contains(sale.get("invoice_number").toString()));
            Path path=Files.createDirectories(Path.of("target","test-fixtures"));Files.write(path.resolve("receipt-"+width+".pdf"),receipts.pdf(invoice,width));ImageIO.write(new PDFRenderer(pdf).renderImageWithDPI(0,144),"png",path.resolve("receipt-"+width+".png").toFile());
        }
        assertThrows(IllegalArgumentException.class,()->receipts.print(invoice,80,"TEST-MISSING-PRINTER",owner.id()));assertEquals(2,stock.stock(id));assertEquals(invoice,((Map<?,?>)billing.invoice(invoice).get("invoice")).get("id"));
    }
    @Test void longReceiptPaginatesWithoutDroppingTotals()throws Exception{
        String id=variant("1",200);List<String> lines=new ArrayList<>();for(int i=0;i<400;i++)lines.add("Long product "+i);lines.add("FINAL TOTAL INR 100.00");
        try(var pdf=Loader.loadPDF(receipts.render(lines,58))){assertTrue(pdf.getNumberOfPages()>1);assertTrue(new PDFTextStripper().getText(pdf).contains("FINAL TOTAL INR 100.00"));}
    }
    @Test void historyIncludesFirstFractionalSecondOfIndianDay(){
        var sale=header(complete(request(cart(variant("1",1),1,0))));String number=sale.get("invoice_number").toString();
        LocalDate date=LocalDate.of(2026,10,1);Instant start=date.atStartOfDay(ZoneId.of("Asia/Kolkata")).toInstant();
        repo.jdbc().update("UPDATE invoices SET created_at=? WHERE id=?",start.plusMillis(100).toString(),sale.get("id"));
        assertEquals(1,((List<?>)billing.history(number,date,date,null)).size());
        assertTrue(((List<?>)billing.history(number,date.plusDays(1),date.plusDays(1),null)).isEmpty());
        repo.jdbc().update("UPDATE invoices SET created_at=? WHERE id=?",start.plusSeconds(86400).toString(),sale.get("id"));
        assertTrue(((List<?>)billing.history(number,date,date,null)).isEmpty());
    }
    @Test void httpAuthorizationAndInvalidAmountsAreEnforced()throws Exception{
        mvc.perform(post("/api/billing/checkout").header("X-EOT-Token",TOKEN).contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/billing/invoices").header("X-EOT-Token",TOKEN)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/billing/settings").header("X-EOT-Token",TOKEN).header("X-EOT-Session",cashierSession).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(billing.config()))).andExpect(status().isForbidden());
        mvc.perform(post("/api/billing/quote").header("X-EOT-Token",TOKEN).header("X-EOT-Session",ownerSession).contentType(MediaType.APPLICATION_JSON).content("{\"lines\":[{\"variantId\":\"missing\",\"quantity\":1.5,\"discountPaise\":0}],\"billDiscountPaise\":0}")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/billing/quote").header("X-EOT-Token",TOKEN).header("X-EOT-Session",ownerSession).contentType(MediaType.APPLICATION_JSON).content("{\"lines\":[{\"variantId\":\"missing\",\"quantity\":0,\"discountPaise\":-1}],\"billDiscountPaise\":0}")).andExpect(status().isBadRequest());
    }
}
