package com.eastoftown.pos;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import jakarta.annotation.PostConstruct;

@Component
public class Migrations {
    private final JdbcTemplate db;
    private final TransactionTemplate tx;
    public Migrations(JdbcTemplate db, TransactionTemplate tx) { this.db=db; this.tx=tx; }
    @PostConstruct void migrate() throws Exception {
        db.execute("PRAGMA journal_mode=WAL");
        db.execute("PRAGMA busy_timeout=5000");
        db.execute("CREATE TABLE IF NOT EXISTS schema_migrations (version INTEGER PRIMARY KEY, checksum TEXT NOT NULL, applied_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP)");
        String[] migrations={"V001__catalog.sql","V002__imports_stock_users.sql","V003__administrator_recovery.sql","V004__billing.sql","V005__checkout_safety.sql","V006__customer_reports.sql","V007__controls.sql"};
        for(int index=0;index<migrations.length;index++) {
        final int version=index+1;
        String sql = new ClassPathResource("db/migration/"+migrations[index]).getContentAsString(StandardCharsets.UTF_8);
        String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(sql.getBytes(StandardCharsets.UTF_8)));
        var existing = db.queryForList("SELECT checksum FROM schema_migrations WHERE version=?", String.class,version);
        if (!existing.isEmpty()) {
            if (!existing.getFirst().equals(hash) && !releasedStage3Checksum(version,existing.getFirst(),hash))
                throw new IllegalStateException("Migration checksum mismatch for "+migrations[index]+"; restore the original migration or install a compatible application release");
            continue;
        }
        tx.executeWithoutResult(status -> {
            for (String statement : sql.split(";")) if (!statement.isBlank()) db.execute(statement);
            db.update("INSERT INTO schema_migrations(version,checksum) VALUES (?,?)", version,hash);
        });
        }
    }
    // The shipped 0.3.0 JAR had a final CRLF in V005; the committed file has LF.
    // Accept only this verified pair. Keep the saved hash and all other checks strict.
    private static boolean releasedStage3Checksum(int version,String stored,String current){
        return version==5
            && stored.equals("11a29fe62f4b0dc74be40cc87075c75064eaae011f9e3c498a551ff13a0eb710")
            && current.equals("5e991ab6b0ddf97a31afbeddb7ff62a25008957ba6e23b989332861c17a8684c");
    }
}
