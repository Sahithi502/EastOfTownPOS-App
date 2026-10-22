package com.eastoftown.pos;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CatalogService {
    public record VariantInput(String productId, @NotBlank @Size(max=160) String name,
        @NotBlank String categoryId, @NotBlank @Size(max=80) String sku,
        @NotBlank @Size(max=30) String size, @NotBlank @Size(max=50) String colour,
        @Pattern(regexp="[ -~]{1,80}") String barcode,
        @DecimalMin("0") @Digits(integer=9,fraction=2) BigDecimal cost,
        @NotNull @DecimalMin("0") @Digits(integer=9,fraction=2) BigDecimal price,
        @Min(0) @Max(1000000) int openingStock, @Min(0) @Max(1000000) int reorderLevel,
        boolean active, @Min(0) long version) {}
    private final CatalogRepository repo;
    public CatalogService(CatalogRepository repo) { this.repo=repo; }
    static long paise(BigDecimal value) { return value.movePointRight(2).longValueExact(); }
    @Transactional public String category(String name) {
        String id=UUID.randomUUID().toString();
        repo.jdbc().update("INSERT INTO categories(id,name,created_at) VALUES (?,?,?)",id,name.trim(),Instant.now().toString());
        audit("CATEGORY_CREATED",id); return id;
    }
    @Transactional public String save(String id, VariantInput input) {
        String now=Instant.now().toString();
        if (id==null) {
            id=UUID.randomUUID().toString();
            String productId=input.productId();
            if (productId==null || productId.isBlank()) {
                productId=UUID.randomUUID().toString();
                repo.jdbc().update("INSERT INTO products VALUES (?,?,?,?,?)",productId,input.name().trim(),input.categoryId(),now,now);
            } else if (repo.jdbc().queryForObject("SELECT COUNT(*) FROM products WHERE id=?",Integer.class,productId)!=1) throw new IllegalArgumentException("Product does not exist");
            String barcode=input.barcode()==null ? "EOT"+id.replace("-","").toUpperCase(Locale.ROOT) : input.barcode();
            repo.jdbc().update("INSERT INTO product_variants(id,product_id,sku,size,colour,barcode,cost_paise,selling_paise,reorder_level,active,created_at,updated_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)",
                id,productId,input.sku().trim(),input.size().trim(),input.colour().trim(),barcode,input.cost()==null?null:paise(input.cost()),paise(input.price()),input.reorderLevel(),input.active()?1:0,now,now);
            repo.jdbc().update("INSERT INTO stock_movements VALUES (?,?,?,?,?,?,?,?)",UUID.randomUUID().toString(),id,input.openingStock(),"OPENING","Initial stock",null,"local-setup",now);
            audit("VARIANT_CREATED",id);
        } else {
            if(input.openingStock()!=0) throw new IllegalArgumentException("Opening stock cannot be changed through product editing");
            var rows=repo.jdbc().queryForList("SELECT * FROM product_variants WHERE id=?",id);
            if(rows.isEmpty()) throw new IllegalArgumentException("Variant not found");
            var old=rows.getFirst();
            if(input.barcode()==null || !input.barcode().equals(old.get("barcode"))) throw new IllegalArgumentException("Existing barcode must be preserved");
            int changed=repo.jdbc().update("UPDATE product_variants SET sku=?,size=?,colour=?,cost_paise=?,selling_paise=?,reorder_level=?,active=?,version=version+1,updated_at=? WHERE id=? AND version=?",
                input.sku().trim(),input.size().trim(),input.colour().trim(),input.cost()==null?null:paise(input.cost()),paise(input.price()),input.reorderLevel(),input.active()?1:0,now,id,input.version());
            if(changed!=1) throw new IllegalArgumentException("This variant changed. Reload before editing");
            repo.jdbc().update("UPDATE products SET name=?,category_id=?,updated_at=? WHERE id=?",input.name().trim(),input.categoryId(),now,old.get("product_id"));
            audit("VARIANT_UPDATED",id);
        }
        return id;
    }
    private void audit(String action,String id) { repo.jdbc().update("INSERT INTO audit_logs VALUES (?,?,?,?,?)",UUID.randomUUID().toString(),action,id,"local-setup",Instant.now().toString()); }
}
