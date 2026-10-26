package com.eastoftown.pos;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

@Service @Validated
public class CustomerService {
    final CatalogRepository repo;final ReportRepository reports;final BillingService billing;
    public CustomerService(CatalogRepository repo,ReportRepository reports,BillingService billing){this.repo=repo;this.reports=reports;this.billing=billing;}
    public record Correction(@NotBlank @Size(max=120) String name,@NotNull @Pattern(regexp="[0-9]{7,15}") String mobile,@Email @Size(max=160) String email,@Min(0) int version){}
    @Transactional(readOnly=true) public Object search(String search,int page){page(page);return Map.of("rows",reports.customers(search,page*50,50),"total",reports.customerCount(search),"page",page);}
    @Transactional(readOnly=true) public Object detail(String id,int page){page(page);var customer=find(id);var summary=repo.jdbc().queryForMap("SELECT COUNT(*) AS completed_purchases,COALESCE(SUM(total_paise),0) AS amount_paise,MAX(created_at) AS last_purchase FROM invoices WHERE customer_id=? AND status='COMPLETED'",id);
        var last=repo.jdbc().queryForList("SELECT invoice_number FROM invoices WHERE customer_id=? AND status='COMPLETED' ORDER BY created_at DESC,id DESC LIMIT 1",id);summary.put("last_invoice",last.isEmpty()?null:last.getFirst().get("invoice_number"));
        return Map.of("customer",customer,"summary",summary,"invoices",reports.customerInvoices(id,page*50,50),"total",repo.jdbc().queryForObject("SELECT COUNT(*) FROM invoices WHERE customer_id=?",Integer.class,id),"page",page);}
    @Transactional(readOnly=true) public Object duplicates(String name){return repo.jdbc().queryForList("SELECT id,name,mobile FROM customers WHERE LOWER(TRIM(name))=LOWER(?) ORDER BY name,id LIMIT 50",name.trim());}
    @Transactional public Object correct(String id,@Valid Correction input,String actor){billing.lock();var before=find(id);
        if(input.name().trim().isEmpty())throw new IllegalArgumentException("Enter a customer name");
        if(repo.jdbc().queryForObject("SELECT COUNT(*) FROM customers WHERE mobile=? AND id<>?",Integer.class,input.mobile(),id)>0)throw new IllegalArgumentException("Another customer already has this mobile number");
        String now=BillingService.timestamp(Instant.now());int changed=repo.jdbc().update("UPDATE customers SET name=?,mobile=?,email=?,version=version+1,updated_at=? WHERE id=? AND version=?",input.name().trim(),input.mobile(),input.email(),now,id,input.version());
        if(changed!=1)throw new IllegalArgumentException("Customer was changed by another action. Reload before correcting it.");
        var after=find(id);repo.jdbc().update("INSERT INTO customer_changes VALUES (?,?,?,?,?,?)",UUID.randomUUID().toString(),id,billing.encode(before),billing.encode(after),actor,now);billing.audit("CUSTOMER_CORRECTED",id,actor);return after;}
    public Object changes(String id){find(id);return repo.jdbc().queryForList("SELECT * FROM customer_changes WHERE customer_id=? ORDER BY created_at DESC,id DESC",id);}
    private Map<String,Object> find(String id){var rows=repo.jdbc().queryForList("SELECT * FROM customers WHERE id=?",id);if(rows.isEmpty())throw new IllegalArgumentException("Customer not found");return rows.getFirst();}
    private void page(int page){if(page<0 || page>100000)throw new IllegalArgumentException("Invalid page");}
}
