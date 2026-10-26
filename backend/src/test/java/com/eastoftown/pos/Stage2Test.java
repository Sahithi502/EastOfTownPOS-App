package com.eastoftown.pos;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.zxing.*;
import com.google.zxing.common.HybridBinarizer;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.*;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import org.springframework.test.context.*;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.sqlite.SQLiteDataSource;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.math.BigDecimal;
import java.io.*;

@SpringBootTest @AutoConfigureMockMvc @TestInstance(TestInstance.Lifecycle.PER_CLASS)
class Stage2Test {
    static final String TOKEN="stage2-integration-secret-0123456789abcdef";
    static final Path DATABASE;
    static{try{DATABASE=Files.createTempDirectory("eot-stage2-test-").resolve("test.db");}catch(IOException e){throw new RuntimeException(e);}}
    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry){registry.add("spring.datasource.url",()->"jdbc:sqlite:"+DATABASE);registry.add("eot.api-token",()->TOKEN);}
    @Autowired CatalogRepository repo;@Autowired CatalogService catalog;@Autowired ImportService imports;@Autowired ImportRepository importRepo;@Autowired StockService stock;@Autowired AuthService auth;@Autowired LabelService labels;@Autowired ExcelFiles excel;@Autowired BarcodeService barcodes;@Autowired MockMvc mvc;@Autowired ObjectMapper json;
    String actor,adminSession,billingSession;
    @BeforeAll void users()throws IOException{
        actor=auth.setup("test-owner","test-password-owner").id();auth.create("test-cashier","test-password-cashier","BILLING");adminSession=auth.login("test-owner","test-password-owner").get("session").toString();billingSession=auth.login("test-cashier","test-password-cashier").get("session").toString();
        Path fixtures=Files.createDirectories(Path.of("target","test-fixtures"));
        String[] a=row("TEST-UI-M","M","7"),b=row("TEST-UI-L","L","3");b[0]=a[0]="TEST ONLY Cotton Shirt";
        Files.write(fixtures.resolve("desktop-valid.xlsx"),workbook(a,b));
        var invalid=row("TEST-INVALID","M","-1");invalid[0]="";invalid[7]="bad-price";
        Files.write(fixtures.resolve("desktop-invalid.xlsx"),workbook(invalid));
    }
    String[] row(String sku,String size,String qty){return new String[]{"Test "+sku,"Shirts",sku,size,"Blue","","450.25","999.99",qty,"3","true"};}
    byte[] workbook(String[]...rows)throws IOException{try(Workbook book=new XSSFWorkbook()){Sheet sheet=book.createSheet("Stock");Row head=sheet.createRow(0);for(int i=0;i<ExcelFiles.FIELDS.size();i++)head.createCell(i).setCellValue(ExcelFiles.FIELDS.get(i));int r=1;for(String[] data:rows){Row row=sheet.createRow(r++);for(int c=0;c<data.length;c++)row.createCell(c).setCellValue(data[c]);}return ExcelFiles.bytes(book);}}
    String upload(String[]...rows)throws IOException{return ((Map<?,?>)imports.upload("test.xlsx",workbook(rows))).get("batchId").toString();}
    ImportService.Config config(ImportService.Mode mode){Map<String,Integer> map=new LinkedHashMap<>();for(int i=0;i<ExcelFiles.FIELDS.size();i++)map.put(ExcelFiles.FIELDS.get(i),i);return new ImportService.Config(map,null,mode,"Verified test adjustment");}
    String variant(String sku,int quantity){return catalog.save(null,new CatalogService.VariantInput(null,"Test "+sku,"shirts",sku,"M","Blue",null,new BigDecimal("450.25"),new BigDecimal("999.99"),quantity,3,true,0),actor);}
    ImportService.Preview preview(String batch,ImportService.Mode mode){return imports.preview(batch,config(mode));}

    @Test void successfulMultiCategoryImportGroupsVariantsAndIsIdempotent()throws Exception{
        var a=row("IMPORT-A","M","7");var b=row("IMPORT-B","L","4");b[0]=a[0];var c=row("IMPORT-C","XL","2");c[1]="New Imported Category";
        String batch=upload(a,b,c);var plan=preview(batch,ImportService.Mode.OPENING_NEW);
        assertEquals(0,plan.failed());assertEquals(3,plan.created());assertTrue(plan.newCategories().contains("New Imported Category"));
        imports.commit(batch,plan.token(),true,actor);int movements=repo.jdbc().queryForObject("SELECT COUNT(*) FROM stock_movements",Integer.class);
        imports.commit(batch,plan.token(),true,actor);assertEquals(movements,repo.jdbc().queryForObject("SELECT COUNT(*) FROM stock_movements",Integer.class));
        var first=repo.variants("IMPORT-A").stream().filter(v->v.get("sku").equals("IMPORT-A")).findFirst().orElseThrow();var second=repo.variants("IMPORT-B").stream().filter(v->v.get("sku").equals("IMPORT-B")).findFirst().orElseThrow();assertEquals(first.get("product_id"),second.get("product_id"));assertNotEquals(first.get("barcode"),second.get("barcode"));assertEquals(7,((Number)first.get("current_stock")).intValue());
        assertEquals(1,repo.jdbc().queryForObject("SELECT COUNT(*) FROM import_history WHERE batch_id=?",Integer.class,batch));
    }
    @Test void rowErrorsBlockEntireBatchAndCanBeExported()throws Exception{
        String[] valid=row("ERR-VALID","M","5"),a=row("ERR-DUP","L","2"),b=row("err-dup","XL","2"),c=row("ERR-PRICE","S","1"),d=row("ERR-NAME","S","-1");
        b[5]=a[5]="1234567890";c[7]="1.001";d[0]="";
        String id=upload(valid,a,b,c,d);var plan=preview(id,ImportService.Mode.OPENING_NEW);assertEquals(4,plan.failed());
        assertThrows(IllegalArgumentException.class,()->imports.commit(id,plan.token(),true,actor));assertTrue(repo.variants("ERR-").isEmpty());
        try(Workbook book=WorkbookFactory.create(new ByteArrayInputStream(imports.errors(id)))){assertTrue(book.getSheetAt(0).getLastRowNum()>=4);}
    }
    @Test void lateDatabaseFailureRollsBackProductsCategoriesLedgerAndHistory()throws Exception{
        String[] a=row("ROLLBACK-A","M","4"),b=row("ROLLBACK-B","L","2");a[1]=b[1]="Rollback Category";
        String id=upload(a,b);var plan=preview(id,ImportService.Mode.OPENING_NEW);assertEquals(0,plan.failed());
        repo.jdbc().execute("CREATE TEMP TRIGGER fail_test_import BEFORE INSERT ON product_variants WHEN NEW.sku='ROLLBACK-B' BEGIN SELECT RAISE(ABORT,'test fault'); END");
        try{assertThrows(org.springframework.dao.DataIntegrityViolationException.class,()->imports.commit(id,plan.token(),true,actor));}
        finally{repo.jdbc().execute("DROP TRIGGER fail_test_import");}
        assertTrue(repo.variants("ROLLBACK-").isEmpty());assertEquals(0,repo.jdbc().queryForObject("SELECT COUNT(*) FROM categories WHERE name='Rollback Category'",Integer.class));assertEquals(0,repo.jdbc().queryForObject("SELECT COUNT(*) FROM import_history WHERE batch_id=?",Integer.class,id));assertEquals("PREVIEWED",importRepo.batch(id).get("state"));
        imports.commit(id,plan.token(),true,actor);assertEquals(2,repo.variants("ROLLBACK-").size());
    }
    @Test void detailsAndOpeningPolicyNeverOverwriteExistingStock()throws Exception{
        String id=variant("POLICY",9);String barcode=repo.variants("POLICY").getFirst().get("barcode").toString();var row=row("POLICY","M","900");row[7]="899.50";
        String batch=upload(row);var plan=preview(batch,ImportService.Mode.DETAILS_ONLY);assertEquals(0,plan.rows().getFirst().stockDelta());imports.commit(batch,plan.token(),true,actor);
        assertEquals(9,stock.stock(id));assertEquals(barcode,repo.variants("POLICY").getFirst().get("barcode"));
        batch=upload(row);plan=preview(batch,ImportService.Mode.OPENING_NEW);assertEquals(1,plan.skipped());assertFalse(plan.rows().getFirst().warnings().isEmpty());imports.commit(batch,plan.token(),true,actor);assertEquals(9,stock.stock(id));
    }
    @Test void signedDeltasUseLedgerAndCannotOverdraw()throws Exception{
        String id=variant("DELTA",5);var row=row("DELTA","M","-2");String batch=upload(row);var plan=preview(batch,ImportService.Mode.ADJUST_DELTA);assertEquals(0,plan.failed());imports.commit(batch,plan.token(),true,actor);assertEquals(3,stock.stock(id));
        row[8]="-4";String failed=upload(row);var invalid=preview(failed,ImportService.Mode.ADJUST_DELTA);assertEquals(1,invalid.failed());assertThrows(IllegalArgumentException.class,()->imports.commit(failed,invalid.token(),true,actor));assertEquals(3,stock.stock(id));
    }
    @Test void stalePreviewRequiresExplicitRevalidation()throws Exception{
        String id=upload(row("STALE","M","1"));var plan=preview(id,ImportService.Mode.OPENING_NEW);variant("CHANGED",1);
        assertThrows(IllegalArgumentException.class,()->imports.commit(id,plan.token(),true,actor));assertTrue(repo.variants("STALE").isEmpty());
        var next=preview(id,ImportService.Mode.OPENING_NEW);assertThrows(IllegalArgumentException.class,()->imports.commit(id,plan.token(),true,actor));imports.commit(id,next.token(),true,actor);
    }
    @Test void barcodeCollisionAndExistingReplacementAreRejected()throws Exception{
        variant("BAR-OWNER",1);String code=repo.variants("BAR-OWNER").getFirst().get("barcode").toString();var collision=row("BAR-COLLISION","M","1");collision[5]=code;
        assertEquals(1,preview(upload(collision),ImportService.Mode.OPENING_NEW).failed());var replacement=row("BAR-OWNER","M","1");replacement[5]="99999999999999";
        assertEquals(1,preview(upload(replacement),ImportService.Mode.DETAILS_ONLY).failed());assertEquals(code,repo.variants("BAR-OWNER").getFirst().get("barcode"));
    }
    @Test void mismatchedSharedProductChangesAreBlocked()throws Exception{
        String[] a=row("SHARED-A","M","1"),b=row("SHARED-B","L","1");b[0]=a[0];String batch=upload(a,b);var plan=preview(batch,ImportService.Mode.OPENING_NEW);imports.commit(batch,plan.token(),true,actor);
        a[0]="Different shared name";String update=upload(a,b);assertTrue(preview(update,ImportService.Mode.DETAILS_ONLY).failed()>0);
    }
    @Test void explicitCategoryMappingAndAssignmentWork()throws Exception{
        var source=row("MAPPING","M","2");String id=upload(source);var original=config(ImportService.Mode.OPENING_NEW);var mapping=new HashMap<>(original.mapping());mapping.remove("category");var custom=new ImportService.Config(mapping,"T-shirts",original.mode(),null);
        var preview=imports.preview(id,custom);assertEquals(0,preview.failed());imports.commit(id,preview.token(),true,actor);assertEquals("T-shirts",repo.variants("MAPPING").getFirst().get("category"));
        mapping.put("name",mapping.get("sku"));assertThrows(IllegalArgumentException.class,()->imports.preview(upload(row("BAD-MAP","M","1")),new ImportService.Config(mapping,"Other",original.mode(),null)));
    }
    @Test void numericIdentifiersFormulasAndBadFilesAreRejected()throws Exception{
        try(Workbook book=WorkbookFactory.create(new ByteArrayInputStream(workbook(row("NUMERIC","M","1"))))){book.getSheetAt(0).getRow(1).getCell(2).setCellValue(1234);String batch=((Map<?,?>)imports.upload("test.xlsx",ExcelFiles.bytes(book))).get("batchId").toString();assertEquals(1,preview(batch,ImportService.Mode.OPENING_NEW).failed());}
        try(Workbook book=WorkbookFactory.create(new ByteArrayInputStream(workbook(row("FORMULA","M","1"))))){book.getSheetAt(0).getRow(1).getCell(7).setCellFormula("1+2");String batch=((Map<?,?>)imports.upload("test.xlsx",ExcelFiles.bytes(book))).get("batchId").toString();assertEquals(1,preview(batch,ImportService.Mode.OPENING_NEW).failed());}
        assertThrows(IllegalArgumentException.class,()->imports.upload("bad.xlsx",new byte[]{1,2,3}));assertThrows(IllegalArgumentException.class,()->imports.upload("bad.csv",new byte[]{1}));
    }
    @Test void xlsAndEmptyTemplateAreSupported()throws Exception{
        try(Workbook book=new HSSFWorkbook()){var sheet=book.createSheet("Stock");Row h=sheet.createRow(0),r=sheet.createRow(1);var values=row("XLS","M","1");for(int i=0;i<ExcelFiles.FIELDS.size();i++){h.createCell(i).setCellValue(ExcelFiles.FIELDS.get(i));r.createCell(i).setCellValue(values[i]);}assertEquals(1,excel.read(ExcelFiles.bytes(book)).rows().size());}
        try(Workbook book=WorkbookFactory.create(new ByteArrayInputStream(excel.template(repo.categories())))){assertEquals(0,book.getSheet("Stock").getLastRowNum());assertNotNull(book.getSheet("Instructions"));assertNotNull(book.getSheet("Categories"));}
    }
    @Test void adjustmentsAreAtomicIdempotentAndRejectStaleStock()throws Exception{
        String id=variant("STOCK",5);UUID key=UUID.randomUUID();var request=new StockService.Adjustment(id,-2,5,"Damaged units",LocalDate.now(ZoneId.of("Asia/Kolkata")),key);
        try(var pool=java.util.concurrent.Executors.newFixedThreadPool(2)){var a=pool.submit(()->stock.adjust(request,actor));var b=pool.submit(()->stock.adjust(request,actor));a.get();b.get();}
        assertEquals(3,stock.stock(id));assertEquals(1,repo.jdbc().queryForObject("SELECT COUNT(*) FROM stock_adjustments WHERE request_key=?",Integer.class,key.toString()));
        assertThrows(IllegalArgumentException.class,()->stock.adjust(new StockService.Adjustment(id,-4,3,"Bad count",LocalDate.now(),UUID.randomUUID()),actor));
        assertThrows(IllegalArgumentException.class,()->stock.adjust(new StockService.Adjustment(id,1,5,"Stale count",LocalDate.now(),UUID.randomUUID()),actor));
        assertThrows(IllegalArgumentException.class,()->stock.adjust(new StockService.Adjustment(id,1,3,"Changed request",request.date(),key),actor));assertEquals(3,stock.stock(id));
    }
    @Test void unknownPrinterFailsClearlyAndInactiveVariantCannotPrint()throws Exception{
        String id=variant("PRINT-FAIL",0);var request=new LabelService.Request(List.of(new LabelService.Item(id,1)),80,40);
        assertThrows(IllegalArgumentException.class,()->labels.print(request,"printer-that-does-not-exist",false));
        repo.jdbc().update("UPDATE product_variants SET active=0 WHERE id=?",id);assertThrows(IllegalArgumentException.class,()->labels.preview(request));
    }
    @Test void code128RoundTripsAndPdfHasCorrectPhysicalPages()throws Exception{
        String id=variant("LABELS",2);String code=repo.variants("LABELS").getFirst().get("barcode").toString();var image=javax.imageio.ImageIO.read(new ByteArrayInputStream(labels.png(code)));
        var source=new RGBLuminanceSource(image.getWidth(),image.getHeight(),image.getRGB(0,0,image.getWidth(),image.getHeight(),null,0,image.getWidth()));assertEquals(code,new com.google.zxing.oned.Code128Reader().decode(new BinaryBitmap(new HybridBinarizer(source))).getText());
        var request=new LabelService.Request(List.of(new LabelService.Item(id,2)),50,30);
        try(var pdf=Loader.loadPDF(labels.pdf(request))){assertEquals(2,pdf.getNumberOfPages());assertEquals(50*72/25.4,pdf.getPage(0).getMediaBox().getWidth(),0.1);var rendered=new PDFRenderer(pdf).renderImageWithDPI(0,203);assertTrue(rendered.getWidth()>300);}
        assertThrows(IllegalArgumentException.class,()->labels.pdf(new LabelService.Request(List.of(new LabelService.Item(id,201)),80,40)));
    }
    @Test void legacyBarcodesAreKeptAndNeedSufficientLabelWidth()throws Exception{
        String id=variant("LEGACY",1);String legacy="EOTABCDEFGHIJKLMNOPQRSTUVWXYZ12345678";repo.jdbc().update("UPDATE product_variants SET barcode=? WHERE id=?",legacy,id);
        assertThrows(IllegalArgumentException.class,()->labels.pdf(new LabelService.Request(List.of(new LabelService.Item(id,1)),35,30)));
        assertTrue(labels.pdf(new LabelService.Request(List.of(new LabelService.Item(id,1)),110,40)).length>100);assertEquals(legacy,repo.variants("LEGACY").getFirst().get("barcode"));
    }
    @Test void thermalRollGrowsAndPreviewMatchesPdfExactly()throws Exception{
        String id=variant("THERMAL",1);
        var one=new LabelService.Request(List.of(new LabelService.Item(id,1)),80,40,true);
        var two=new LabelService.Request(List.of(new LabelService.Item(id,2)),80,40,true);
        try(var a=Loader.loadPDF(labels.pdf(one));var b=Loader.loadPDF(labels.pdf(two))){
            assertEquals(1,b.getNumberOfPages());assertEquals(80,b.getPage(0).getMediaBox().getWidth()*25.4/72,.01);
            assertTrue(b.getPage(0).getMediaBox().getHeight()>a.getPage(0).getMediaBox().getHeight());
            var preview=(List<Map<String,Object>>)labels.preview(two);
            String image=preview.getFirst().get("image").toString();
            var png=javax.imageio.ImageIO.read(new ByteArrayInputStream(Base64.getDecoder().decode(image.substring(image.indexOf(',')+1))));
            var rendered=new PDFRenderer(b).renderImageWithDPI(0,203);
            assertEquals(rendered.getWidth(),png.getWidth());assertEquals(rendered.getHeight(),png.getHeight());
            assertArrayEquals(rendered.getRGB(0,0,png.getWidth(),png.getHeight(),null,0,png.getWidth()),png.getRGB(0,0,png.getWidth(),png.getHeight(),null,0,png.getWidth()));
            assertTrue(new org.apache.pdfbox.text.PDFTextStripper().getText(b).contains("SKU: THERMAL"));
        }
        assertThrows(IllegalArgumentException.class,()->labels.pdf(new LabelService.Request(List.of(new LabelService.Item(id,100)),80,40,true)));
        mvc.perform(post("/api/labels/pdf").header("X-EOT-Token",TOKEN).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(one))).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/labels/pdf").header("X-EOT-Token",TOKEN).header("X-EOT-Session",adminSession).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(two))).andExpect(status().isOk()).andExpect(content().contentType(MediaType.APPLICATION_PDF));
    }
    @Test void longLegacyBarcodesFitThermalRollAndDecodeAt203Dpi()throws Exception{
        String id=variant("EOT990909",1);
        for(String code:List.of("EOTROTATEDABCDEFGHIJKLMNOPQRSTUVWXYZ12345678","ABCDEFGHIJKLMNOPQRSTUVWXYZ".repeat(3)+"AB")){
            repo.jdbc().update("UPDATE product_variants SET barcode=? WHERE id=?",code,id);
            var request=new LabelService.Request(List.of(new LabelService.Item(id,1)),80,40,true);
            try(var pdf=Loader.loadPDF(labels.pdf(request))){
                assertEquals(80,pdf.getPage(0).getMediaBox().getWidth()*25.4/72,.01);
                var image=new PDFRenderer(pdf).renderImageWithDPI(0,203);
                var turned=new java.awt.image.BufferedImage(image.getHeight(),image.getWidth(),java.awt.image.BufferedImage.TYPE_INT_RGB);
                var g=turned.createGraphics();g.translate(turned.getWidth(),0);g.rotate(Math.PI/2);g.drawImage(image,0,0,null);g.dispose();
                var source=new RGBLuminanceSource(turned.getWidth(),turned.getHeight(),turned.getRGB(0,0,turned.getWidth(),turned.getHeight(),null,0,turned.getWidth()));
                assertEquals(code,new MultiFormatReader().decode(new BinaryBitmap(new HybridBinarizer(source)),Map.of(DecodeHintType.TRY_HARDER,true)).getText());
                assertEquals(code,repo.jdbc().queryForObject("SELECT barcode FROM product_variants WHERE id=?",String.class,id));
                Files.write(Path.of("target","test-fixtures","long-thermal-preview.png"),Base64.getDecoder().decode(((List<Map<String,Object>>)labels.preview(request)).getFirst().get("image").toString().split(",",2)[1]));
            }
        }
    }
    @Test void roleChecksCannotBeBypassedThroughRest()throws Exception{
        String id=variant("PERMISSION",3);String body=json.writeValueAsString(new StockService.Adjustment(id,1,3,"Received",LocalDate.now(),UUID.randomUUID()));
        mvc.perform(post("/api/stock/adjustments").header("X-EOT-Token",TOKEN).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/stock/adjustments").header("X-EOT-Token",TOKEN).header("X-EOT-Session",billingSession).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
        mvc.perform(post("/api/stock/adjustments").header("X-EOT-Token",TOKEN).header("X-EOT-Session",adminSession).contentType(MediaType.APPLICATION_JSON).content(body.replace("\"quantity\":1","\"quantity\":1.5"))).andExpect(status().isBadRequest());
        mvc.perform(multipart("/api/imports/upload").file(new MockMultipartFile("file","test.xlsx","application/octet-stream",workbook(row("NO-AUTH","M","1")))).header("X-EOT-Token",TOKEN).header("X-EOT-Session",billingSession)).andExpect(status().isForbidden());
        mvc.perform(post("/api/stock/adjustments").header("X-EOT-Token",TOKEN).header("X-EOT-Session",adminSession).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk());assertEquals(4,stock.stock(id));
    }
    @Test void adminCredentialsAreHashedAndSetupCannotOverwriteOwner()throws Exception{
        String hash=repo.jdbc().queryForObject("SELECT password_hash FROM users WHERE id=?",String.class,actor);assertTrue(hash.startsWith("$2"));assertFalse(hash.contains("test-password-owner"));
        assertThrows(IllegalArgumentException.class,()->auth.setup("replacement","replacement-password"));
        assertThrows(org.springframework.web.server.ResponseStatusException.class,()->auth.login("test-owner","incorrect"));
    }
    @Test void administratorRecoveryCodeResetsPasswordAndIsSingleUse(){
        String code="EOT-TEST-RECOVERY-CODE-123456";
        AuthService.User recoveryUser=auth.create("recovery-owner","test-password-recovery","ADMIN");
        repo.jdbc().update("UPDATE users SET recovery_code_hash=?,recovery_code_created_at=? WHERE id=?",new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder(12).encode(code),Instant.now().toString(),recoveryUser.id());
        assertEquals(true,auth.recover("recovery-owner",code,"test-password-recovered").get("ok"));
        assertTrue(auth.login("recovery-owner","test-password-recovered").containsKey("session"));
        assertThrows(org.springframework.web.server.ResponseStatusException.class,()->auth.recover("recovery-owner",code,"test-password-again"));
    }
    @Test void upgradePreservesStage1DataAndMigrationChecksums()throws Exception{
        SQLiteDataSource source=new SQLiteDataSource();source.setUrl("jdbc:sqlite:"+Files.createTempDirectory("eot-upgrade-").resolve("old.db"));JdbcTemplate db=new JdbcTemplate(source);
        String sql=new org.springframework.core.io.ClassPathResource("db/migration/V001__catalog.sql").getContentAsString(java.nio.charset.StandardCharsets.UTF_8);for(String statement:sql.split(";"))if(!statement.isBlank())db.execute(statement);
        String hash=HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(sql.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        db.execute("CREATE TABLE schema_migrations(version INTEGER PRIMARY KEY,checksum TEXT NOT NULL,applied_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP)");db.update("INSERT INTO schema_migrations(version,checksum) VALUES(1,?)",hash);
        db.update("INSERT INTO products VALUES ('old-p','Legacy shirt','shirts','time','time')");db.update("INSERT INTO product_variants(id,product_id,sku,size,colour,barcode,selling_paise,reorder_level,active,created_at,updated_at) VALUES ('old-v','old-p','OLD','M','Blue','EOTPERMANENT',99999,3,1,'time','time')");db.update("INSERT INTO stock_movements VALUES ('old-s','old-v',7,'OPENING','Legacy',NULL,'local-setup','time')");
        new Migrations(db,new TransactionTemplate(new DataSourceTransactionManager(source))).migrate();
        assertEquals("EOTPERMANENT",db.queryForObject("SELECT barcode FROM product_variants",String.class));assertEquals(7,db.queryForObject("SELECT SUM(quantity) FROM stock_movements",Integer.class));assertEquals(hash,db.queryForObject("SELECT checksum FROM schema_migrations WHERE version=1",String.class));
    }
}
