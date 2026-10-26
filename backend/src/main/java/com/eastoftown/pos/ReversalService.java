package com.eastoftown.pos;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import com.eastoftown.pos.BillingService.Payment;
import java.math.BigInteger;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReversalService {
    public record Line(@NotBlank String itemId,@Min(1) @Max(1000) int quantity,boolean restock){}
    public record Input(@NotNull UUID requestKey,@NotBlank String invoiceId,@Pattern(regexp="RETURN|CANCELLATION") @NotNull String kind,
        @NotBlank @Size(min=3,max=300) String reason,@NotEmpty @Size(max=100) List<@NotNull @Valid Line> lines,
        @NotNull @Size(max=3) List<@NotNull @Valid Payment> refunds){}
    final BillingService billing;final CatalogRepository repo;final StockService stock;
    public ReversalService(BillingService billing,CatalogRepository repo,StockService stock){this.billing=billing;this.repo=repo;this.stock=stock;}
    public Object history(String invoiceId){return repo.jdbc().queryForList("SELECT * FROM returns WHERE invoice_id=? ORDER BY created_at,id",invoiceId);}
    public Object detail(String id){return Map.of("reversal",repo.jdbc().queryForMap("SELECT * FROM returns WHERE id=?",id),"items",repo.jdbc().queryForList("SELECT * FROM return_items WHERE return_id=?",id),"refunds",repo.jdbc().queryForList("SELECT * FROM refunds WHERE return_id=?",id));}
    @Transactional public Object status(UUID key,String actor){billing.lock();var rows=repo.jdbc().queryForList("SELECT id,actor_id FROM returns WHERE request_key=?",key.toString());if(rows.isEmpty())return Map.of("found",false);if(!rows.getFirst().get("actor_id").equals(actor))throw new IllegalArgumentException("Reversal belongs to another user");return Map.of("found",true,"detail",detail(rows.getFirst().get("id").toString()));}
    public Object remaining(String id){return repo.jdbc().queryForList("SELECT x.*,x.quantity-COALESCE((SELECT SUM(r.quantity) FROM return_items r WHERE r.invoice_item_id=x.id),0) AS remaining FROM invoice_items x WHERE invoice_id=? ORDER BY line_position",id);}
    static long portion(long amount,long count,long all){return BigInteger.valueOf(amount).multiply(BigInteger.valueOf(count)).divide(BigInteger.valueOf(all)).longValueExact();}
    @Transactional public Map<String,Object> preview(@Valid Input input){billing.lock();return calculate(input);}
    private Map<String,Object> calculate(Input input){
        if(input.reason()==null || input.reason().trim().length()<3)throw new IllegalArgumentException("Enter a reason of at least 3 characters");
        if(!Set.of("RETURN","CANCELLATION").contains(input.kind()))throw new IllegalArgumentException("Unknown reversal kind");
        var invoice=repo.jdbc().queryForMap("SELECT * FROM invoices WHERE id=?",input.invoiceId());
        if(!invoice.get("status").equals("COMPLETED"))throw new IllegalArgumentException("Only completed invoices can be reversed");
        if(repo.jdbc().queryForObject("SELECT COUNT(*) FROM returns WHERE invoice_id=? AND kind='CANCELLATION'",Integer.class,input.invoiceId())>0)throw new IllegalArgumentException("Invoice already cancelled");
        var all=repo.jdbc().queryForList("SELECT * FROM invoice_items WHERE invoice_id=?",input.invoiceId());
        Map<String,Map<String,Object>> byId=new HashMap<>();for(var x:all)byId.put(x.get("id").toString(),x);
        var seen=new HashSet<String>();var result=new ArrayList<Map<String,Object>>();long total=0;
        for(var line:input.lines()){
            if(!seen.add(line.itemId()) || !byId.containsKey(line.itemId()))throw new IllegalArgumentException("Choose each original invoice item once");
            var item=byId.get(line.itemId());long quantity=BillingService.number(item,"quantity");
            long prior=repo.jdbc().queryForObject("SELECT COALESCE(SUM(quantity),0) FROM return_items WHERE invoice_item_id=?",Long.class,line.itemId());
            if(line.quantity()<1 || line.quantity()>quantity-prior)throw new IllegalArgumentException("Return exceeds remaining quantity for "+item.get("sku"));
            if(input.kind().equals("CANCELLATION") && (!line.restock() || prior!=0 || line.quantity()!=quantity))throw new IllegalArgumentException("Cancellation requires all original units restored and no prior returns");
            long gross=BillingService.number(item,"unit_price_paise")*line.quantity();
            // Cumulative floor allocation makes repeated partial refunds sum to the exact original paise.
            long end=prior+line.quantity(),discount=0;
            for(String key:List.of("discount_paise","bill_discount_paise"))discount+=portion(BillingService.number(item,key),end,quantity)-portion(BillingService.number(item,key),prior,quantity);
            long tax=portion(BillingService.number(item,"tax_paise"),end,quantity)-portion(BillingService.number(item,"tax_paise"),prior,quantity);
            long originalTotal=BillingService.number(item,"line_total_paise");
            long amount=portion(originalTotal,end,quantity)-portion(originalTotal,prior,quantity);
            discount=gross+tax-amount;total=Math.addExact(total,amount);
            result.add(ReportService.row("itemId",line.itemId(),"variantId",item.get("variant_id"),"quantity",line.quantity(),"restock",line.restock(),"grossPaise",gross,"discountPaise",discount,"taxPaise",tax,"totalPaise",amount));
        }
        if(input.lines().isEmpty() || input.kind().equals("CANCELLATION") && seen.size()!=all.size())throw new IllegalArgumentException("Select items; cancellation must include the complete invoice");
        return Map.of("items",result,"totalPaise",total);
    }
    @Transactional public Object commit(@Valid Input input,String actor){
        billing.lock();String digest=billing.hash(billing.encode(input));
        var previous=repo.jdbc().queryForList("SELECT * FROM returns WHERE request_key=?",input.requestKey().toString());
        if(!previous.isEmpty()){
            var old=previous.getFirst();if(!old.get("request_hash").equals(digest)||!old.get("actor_id").equals(actor))throw new IllegalArgumentException("Reversal key already used for different details");return detail(old.get("id").toString());
        }
        var quote=calculate(input);long total=((Number)quote.get("totalPaise")).longValue(),paid=0;var methods=new HashSet<BillingService.Method>();
        for(var payment:input.refunds()){if(payment.amountPaise()<=0 || !methods.add(payment.method()))throw new IllegalArgumentException("Use one positive refund per method");paid=Math.addExact(paid,payment.amountPaise());}
        if(paid!=total)throw new IllegalArgumentException("Refunds must equal the calculated reversal total");
        String id=UUID.randomUUID().toString(),now=BillingService.timestamp(Instant.now());
        repo.jdbc().update("INSERT INTO returns VALUES (?,?,?,?,?,?,?,?,?)",id,input.invoiceId(),input.requestKey().toString(),digest,input.kind(),input.reason().trim(),total,actor,now);
        for(Object value:(List<?>)quote.get("items")){
            var x=(Map<?,?>)value;String itemId=UUID.randomUUID().toString();
            repo.jdbc().update("INSERT INTO return_items VALUES (?,?,?,?,?,?,?,?,?)",itemId,id,x.get("itemId"),x.get("quantity"),(Boolean)x.get("restock")?1:0,x.get("grossPaise"),x.get("discountPaise"),x.get("taxPaise"),x.get("totalPaise"));
            if((Boolean)x.get("restock"))stock.movement(x.get("variantId").toString(),((Number)x.get("quantity")).intValue(),input.kind(),input.reason().trim(),itemId,actor);
        }
        for(var payment:input.refunds())repo.jdbc().update("INSERT INTO refunds VALUES (?,?,?,?,?)",UUID.randomUUID().toString(),id,payment.method().name(),payment.amountPaise(),now);
        billing.audit("INVOICE_"+input.kind(),id,actor);return detail(id);
    }
}
