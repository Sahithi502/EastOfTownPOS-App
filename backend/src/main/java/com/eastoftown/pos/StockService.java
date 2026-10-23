package com.eastoftown.pos;

import jakarta.validation.constraints.*;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StockService {
    public record Adjustment(@NotBlank String variantId,@Min(-1000000) @Max(1000000) int quantity,
        @Min(0) long expectedStock,@NotBlank @Size(min=3,max=300) String reason,@NotNull LocalDate date,@NotNull UUID requestKey){}
    final CatalogRepository repo;
    public StockService(CatalogRepository repo){this.repo=repo;}
    @Transactional public Object adjust(Adjustment input,String actor){
        if(input.reason()==null || input.reason().trim().length()<3 || input.reason().length()>300)throw new IllegalArgumentException("Enter a reason with 3–300 characters");
        repo.jdbc().update("UPDATE application_settings SET value=value WHERE key='write_lock'");
        var existing=repo.jdbc().queryForList("SELECT * FROM stock_adjustments WHERE request_key=?",input.requestKey().toString());
        if(!existing.isEmpty()){
            var old=existing.getFirst();
            if(!old.get("variant_id").equals(input.variantId()) || ((Number)old.get("quantity")).intValue()!=input.quantity() || !old.get("reason").equals(input.reason().trim()) || !old.get("effective_date").equals(input.date().toString()) || !old.get("actor_id").equals(actor)) throw new IllegalArgumentException("Adjustment request key was already used for different details");
            return old;
        }
        if(input.quantity()==0 || Math.abs((long)input.quantity())>1000000)throw new IllegalArgumentException("Enter a non-zero quantity within one million units");
        if(input.date().isAfter(LocalDate.now(ZoneId.of("Asia/Kolkata"))))throw new IllegalArgumentException("Stock adjustment date cannot be in the future");
        if(repo.jdbc().queryForObject("SELECT COUNT(*) FROM product_variants WHERE id=?",Integer.class,input.variantId())!=1)throw new IllegalArgumentException("Variant not found");
        long current=stock(input.variantId());
        if(current!=input.expectedStock())throw new IllegalArgumentException("Stock changed. Refresh and check the quantity again");
        if(current+input.quantity()<0)throw new IllegalArgumentException("Adjustment would make stock negative");
        String id=UUID.randomUUID().toString(),now=Instant.now().toString();
        repo.jdbc().update("INSERT INTO stock_adjustments VALUES (?,?,?,?,?,?,?,?)",id,input.requestKey().toString(),input.variantId(),input.quantity(),input.reason().trim(),input.date().toString(),actor,now);
        movement(input.variantId(),input.quantity(),"ADJUSTMENT",input.reason().trim(),id,actor);
        repo.jdbc().update("INSERT INTO audit_logs VALUES (?,?,?,?,?)",UUID.randomUUID().toString(),"STOCK_ADJUSTED",id,actor,now);
        return Map.of("id",id,"currentStock",current+input.quantity());
    }
    public long stock(String id){return repo.jdbc().queryForObject("SELECT COALESCE(SUM(quantity),0) FROM stock_movements WHERE variant_id=?",Long.class,id);}
    void movement(String variant,int quantity,String kind,String reason,String reference,String actor){repo.jdbc().update("INSERT INTO stock_movements VALUES (?,?,?,?,?,?,?,?)",UUID.randomUUID().toString(),variant,quantity,kind,reason,reference,actor,Instant.now().toString());}
    public Object history(String id){return repo.jdbc().queryForList("SELECT s.*, a.effective_date,COALESCE(u.username,s.actor) AS username FROM stock_movements s LEFT JOIN stock_adjustments a ON a.id=s.reference_id LEFT JOIN users u ON u.id=s.actor WHERE s.variant_id=? ORDER BY s.created_at DESC,s.id DESC LIMIT 500",id);}
}
