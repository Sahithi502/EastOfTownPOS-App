package com.eastoftown.pos;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.sqlite.SQLiteDataSource;

class MigrationUpgradeTest {
    record Database(JdbcTemplate db,Migrations migrations){}
    private Database legacy(boolean releasedFinalCrlf)throws Exception{
        var source=new SQLiteDataSource();source.setUrl("jdbc:sqlite:"+Files.createTempDirectory("eot-stage3-upgrade-").resolve("test.db"));
        var db=new JdbcTemplate(source);db.execute("PRAGMA foreign_keys=ON");
        db.execute("CREATE TABLE schema_migrations(version INTEGER PRIMARY KEY,checksum TEXT NOT NULL,applied_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP)");
        String[] names={"catalog","imports_stock_users","administrator_recovery","billing","checkout_safety"};
        for(int v=1;v<=5;v++){
            String sql=new ClassPathResource("db/migration/V%03d__%s.sql".formatted(v,names[v-1])).getContentAsString(StandardCharsets.UTF_8);
            if(v==5 && releasedFinalCrlf){assertTrue(sql.endsWith("\n"));sql=sql.substring(0,sql.length()-1)+"\r\n";assertEquals("11a29fe62f4b0dc74be40cc87075c75064eaae011f9e3c498a551ff13a0eb710",hash(sql));}
            for(String statement:sql.split(";"))if(!statement.isBlank())db.execute(statement);
            db.update("INSERT INTO schema_migrations(version,checksum) VALUES (?,?)",v,hash(sql));
        }
        db.update("INSERT INTO users(id,username,password_hash,role,created_at) VALUES ('test-owner','test-only-owner','test-only-unused-hash','ADMIN','2026-10-01T00:00:00Z')");
        db.update("INSERT INTO products VALUES ('test-product','TEST ONLY Legacy shirt','shirts','time','time')");
        db.update("INSERT INTO product_variants(id,product_id,sku,size,colour,barcode,selling_paise,reorder_level,active,created_at,updated_at) VALUES ('test-variant','test-product','TEST-LEGACY','M','Blue','TEST-PERMANENT-CODE',10000,2,1,'time','time')");
        db.update("INSERT INTO customers VALUES ('test-customer','TEST ONLY Customer','9999900012',NULL,'2026-10-01T00:00:00Z')");
        db.update("INSERT INTO invoices(id,invoice_number,status,subtotal_paise,discount_paise,tax_paise,total_paise,created_by,created_at,customer_id,customer_name) VALUES ('test-invoice','TEST-LEGACY-000001','COMPLETED',20000,1000,0,19000,'test-owner','2026-10-01T00:00:00Z','test-customer','TEST ONLY Customer')");
        db.update("INSERT INTO invoice_items(id,invoice_id,variant_id,product_name,sku,size,colour,unit_price_paise,quantity,discount_paise,tax_paise,line_total_paise) VALUES ('test-item','test-invoice','test-variant','TEST ONLY Legacy shirt','TEST-LEGACY','M','Blue',10000,2,1000,0,19000)");
        db.update("INSERT INTO payments VALUES ('test-payment','test-invoice','CASH',19000,'2026-10-01T00:00:00Z')");
        db.update("INSERT INTO stock_movements VALUES ('test-opening','test-variant',7,'OPENING','Test opening',NULL,'test-owner','2026-10-01T00:00:00Z')");
        db.update("INSERT INTO stock_movements VALUES ('test-sale','test-variant',-2,'SALE','Test sale','test-invoice','test-owner','2026-10-01T00:00:01Z')");
        db.update("INSERT INTO checkout_requests VALUES ('test-key','test-request-hash','test-owner','test-invoice')");
        return new Database(db,new Migrations(db,new TransactionTemplate(new DataSourceTransactionManager(source))));
    }
    private String hash(String sql)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(sql.getBytes(StandardCharsets.UTF_8)));}
    @Test void shippedStage3DatabaseUpgradesWithoutRewritingSavedHashOrBusinessRecords()throws Exception{
        var fixture=legacy(true);var db=fixture.db();String old=db.queryForObject("SELECT checksum FROM schema_migrations WHERE version=5",String.class);
        fixture.migrations().migrate();fixture.migrations().migrate();
        assertEquals(old,db.queryForObject("SELECT checksum FROM schema_migrations WHERE version=5",String.class));assertEquals(8,db.queryForObject("SELECT COUNT(*) FROM schema_migrations",Integer.class));
        assertEquals(5,db.queryForObject("SELECT SUM(quantity) FROM stock_movements",Integer.class));assertEquals("TEST-PERMANENT-CODE",db.queryForObject("SELECT barcode FROM product_variants",String.class));
        assertEquals(19000,db.queryForObject("SELECT total_paise FROM invoices",Integer.class));assertEquals(19000,db.queryForObject("SELECT amount_paise FROM payments",Integer.class));assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM checkout_requests",Integer.class));assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM invoice_items",Integer.class));
        assertEquals(0,db.queryForObject("SELECT version FROM customers",Integer.class));assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM customer_changes",Integer.class));assertTrue(db.queryForList("PRAGMA foreign_key_check").isEmpty());assertEquals("ok",db.queryForObject("PRAGMA quick_check",String.class));
    }
    @Test void unknownChecksumStillBlocksUpgrade()throws Exception{var fixture=legacy(true);fixture.db().update("UPDATE schema_migrations SET checksum='unknown-checksum' WHERE version=5");var e=assertThrows(IllegalStateException.class,()->fixture.migrations().migrate());assertTrue(e.getMessage().contains("V005__checkout_safety.sql"));assertEquals(5,fixture.db().queryForObject("SELECT COUNT(*) FROM schema_migrations",Integer.class));}
    @Test void canonicalStage3HashWorksAndOtherMigrationTamperingIsRejected()throws Exception{var fixture=legacy(false);fixture.migrations().migrate();assertEquals(8,fixture.db().queryForObject("SELECT COUNT(*) FROM schema_migrations",Integer.class));fixture.db().update("UPDATE schema_migrations SET checksum='tampered' WHERE version=1");assertThrows(IllegalStateException.class,()->fixture.migrations().migrate());}
}
