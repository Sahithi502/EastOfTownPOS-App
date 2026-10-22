package com.eastoftown.pos;

import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class CatalogRepository {
    private final JdbcTemplate db;
    public JdbcTemplate jdbc() { return db; }
    public CatalogRepository(JdbcTemplate db, Migrations migrations) {
        this.db=db;
        var fallback = new org.springframework.jdbc.support.SQLExceptionSubclassTranslator();
        db.setExceptionTranslator((task,sql,error) -> {
            if ((error.getErrorCode() & 255)==19) return new org.springframework.dao.DataIntegrityViolationException("Database constraint rejected the record",error);
            return fallback.translate(task,sql,error);
        });
    }
    public List<Map<String,Object>> categories() { return db.queryForList("SELECT id,name FROM categories ORDER BY name"); }
    public List<Map<String,Object>> variants(String search) {
        String pattern="%"+search.replace("!","!!").replace("%","!%").replace("_","!_")+"%";
        return db.queryForList("""
            SELECT v.*, p.name, p.category_id, c.name AS category,
            COALESCE((SELECT SUM(quantity) FROM stock_movements s WHERE s.variant_id=v.id),0) AS current_stock
            FROM product_variants v JOIN products p ON p.id=v.product_id JOIN categories c ON c.id=p.category_id
            WHERE p.name LIKE ? ESCAPE '!' OR v.sku LIKE ? ESCAPE '!' OR v.barcode LIKE ? ESCAPE '!'
            ORDER BY p.name,v.size,v.colour LIMIT 500
            """,pattern,pattern,pattern);
    }
}
