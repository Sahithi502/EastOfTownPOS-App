package com.eastoftown.pos;

import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Repository;

@Repository
public class ImportRepository {
    final CatalogRepository catalog;
    public ImportRepository(CatalogRepository catalog){this.catalog=catalog;}
    Map<String,Object> batch(String id){var rows=catalog.jdbc().queryForList("SELECT * FROM import_batches WHERE id=?",id);if(rows.isEmpty())throw new IllegalArgumentException("Import batch not found");return rows.getFirst();}
    String upload(String filename,String raw){String id=UUID.randomUUID().toString();catalog.jdbc().update("INSERT INTO import_batches(id,filename,raw_json,state,created_at) VALUES (?,?,?,'UPLOADED',?)",id,filename,raw,Instant.now().toString());return id;}
    void preview(String id,String config,String token,String revision){catalog.jdbc().update("UPDATE import_batches SET config_json=?,preview_token=?,revision=?,state='PREVIEWED' WHERE id=? AND state!='COMMITTED'",config,token,revision,id);}
    public List<Map<String,Object>> history(){return catalog.jdbc().queryForList("SELECT h.*,u.username FROM import_history h JOIN users u ON u.id=h.actor_id ORDER BY h.created_at DESC LIMIT 100");}
    String revision(){
        try{
            String snapshot=catalog.jdbc().queryForList("SELECT v.*,p.name,p.category_id FROM product_variants v JOIN products p ON p.id=v.product_id ORDER BY v.id").toString()
                +catalog.jdbc().queryForList("SELECT variant_id,SUM(quantity) AS quantity FROM stock_movements GROUP BY variant_id ORDER BY variant_id")
                +catalog.jdbc().queryForList("SELECT * FROM categories ORDER BY id");
            return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(snapshot.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        }catch(Exception e){throw new IllegalStateException(e);}
    }
}
