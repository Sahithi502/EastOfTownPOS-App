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
        String sql = new ClassPathResource("db/migration/V001__catalog.sql").getContentAsString(StandardCharsets.UTF_8);
        String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(sql.getBytes(StandardCharsets.UTF_8)));
        var existing = db.queryForList("SELECT checksum FROM schema_migrations WHERE version=1", String.class);
        if (!existing.isEmpty()) {
            if (!existing.getFirst().equals(hash)) throw new IllegalStateException("Migration checksum mismatch; restore original migration");
            return;
        }
        tx.executeWithoutResult(status -> {
            for (String statement : sql.split(";")) if (!statement.isBlank()) db.execute(statement);
            db.update("INSERT INTO schema_migrations(version,checksum) VALUES (1,?)", hash);
        });
    }
}
