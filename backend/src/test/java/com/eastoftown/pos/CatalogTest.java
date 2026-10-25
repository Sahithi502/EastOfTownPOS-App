package com.eastoftown.pos;

import java.math.BigDecimal;
import java.nio.file.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.sqlite.SQLiteDataSource;

@SpringBootTest
class CatalogTest {
    static Path file;
    static { try { file=Files.createTempDirectory("eot-test-").resolve("test.db"); } catch(Exception e) { throw new RuntimeException(e); } }
    @DynamicPropertySource static void properties(DynamicPropertyRegistry r) { r.add("spring.datasource.url",()->"jdbc:sqlite:"+file); }
    @Autowired CatalogService service;
    @Autowired CatalogRepository repo;
    CatalogService.VariantInput input(String sku,int stock) {return new CatalogService.VariantInput(null,"Cotton shirt","shirts",sku,"M","Blue",null,new BigDecimal("450.25"),new BigDecimal("999.99"),stock,3,true,0);}
    @Test void initialCategoriesAndMigrationAreStable() throws Exception {
        assertEquals(8,repo.categories().size());
        new Migrations(repo.jdbc(),new org.springframework.transaction.support.TransactionTemplate(new org.springframework.jdbc.datasource.DataSourceTransactionManager(repo.jdbc().getDataSource()))).migrate();
        assertEquals(5,repo.jdbc().queryForObject("SELECT COUNT(*) FROM schema_migrations",Integer.class));
    }
    @Test void createsPersistentVariantAndOpeningLedger() throws Exception {
        String id=service.save(null,input("PERSIST-1",7));
        var v=repo.variants("PERSIST-1").getFirst();
        assertEquals(id,v.get("id"));assertEquals(99999,((Number)v.get("selling_paise")).intValue());
        assertEquals(7,((Number)v.get("current_stock")).intValue());assertTrue(v.get("barcode").toString().matches("20[0-9]{12}"));
        SQLiteDataSource independent=new SQLiteDataSource();independent.setUrl("jdbc:sqlite:"+file);
        assertEquals(1,new JdbcTemplate(independent).queryForObject("SELECT COUNT(*) FROM product_variants WHERE id=?",Integer.class,id));
    }
    @Test void duplicateSkuRollsBackProductAndStock() {
        service.save(null,input("DUP-1",4));
        int before=repo.jdbc().queryForObject("SELECT COUNT(*) FROM products",Integer.class);
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,()->service.save(null,input("dup-1",9)));
        assertEquals(before,repo.jdbc().queryForObject("SELECT COUNT(*) FROM products",Integer.class));
        assertEquals(1,repo.variants("DUP-1").size());
    }
    @Test void editPreservesBarcodeAndStockAndRejectsStaleWrites() {
        String id=service.save(null,input("EDIT-1",6));var v=repo.variants("EDIT-1").getFirst();
        String barcode=v.get("barcode").toString();
        var edit=new CatalogService.VariantInput(null,"New name","shirts","EDIT-1","M","Blue",barcode,null,new BigDecimal("899.50"),0,2,false,0);
        service.save(id,edit);
        var result=repo.variants("EDIT-1").getFirst();
        assertEquals(barcode,result.get("barcode"));assertEquals(6,((Number)result.get("current_stock")).intValue());
        assertEquals(0,((Number)result.get("active")).intValue());
        assertThrows(IllegalArgumentException.class,()->service.save(id,edit));
        assertThrows(IllegalArgumentException.class,()->service.save(id,input("EDIT-1",10)));
    }
    @Test void exactMoneyConversion() {
        assertEquals(10001,CatalogService.paise(new BigDecimal("100.01")));
        assertThrows(ArithmeticException.class,()->CatalogService.paise(new BigDecimal("1.001")));
    }
    @Test void wildcardSearchIsLiteral() { assertTrue(repo.variants("%__%missing").isEmpty()); }
    @Test void foreignKeyFailureIsAtomic() {
        var i=new CatalogService.VariantInput(null,"Bad category","missing","FK-1","L","Black",null,null,new BigDecimal("100"),1,0,true,0);
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,()->service.save(null,i));
        assertTrue(repo.variants("FK-1").isEmpty());
    }
}
