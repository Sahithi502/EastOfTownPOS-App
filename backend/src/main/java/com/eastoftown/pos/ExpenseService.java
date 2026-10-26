package com.eastoftown.pos;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ExpenseService {
    public record Input(@NotNull UUID requestKey,@NotBlank String categoryId,@Min(1) @Max(BillingService.MAX_MONEY) long amountPaise,@NotNull LocalDate date,@NotNull BillingService.Method method,@NotBlank @Size(min=3,max=300) String note){}
    final CatalogRepository repo;final BillingService billing;
    public ExpenseService(CatalogRepository repo,BillingService billing){this.repo=repo;this.billing=billing;}
    public Object categories(){return repo.jdbc().queryForList("SELECT * FROM expense_categories ORDER BY name");}
    @Transactional public Object category(String name,String actor){billing.lock();if(name==null || name.trim().isEmpty() || name.length()>80)throw new IllegalArgumentException("Enter category name (1–80 characters)");String id=UUID.randomUUID().toString();repo.jdbc().update("INSERT INTO expense_categories VALUES (?,?)",id,name.trim());billing.audit("EXPENSE_CATEGORY_CREATED",id,actor);return categories();}
    public Object list(){return repo.jdbc().queryForList("SELECT e.*,c.name AS category FROM expenses e JOIN expense_categories c ON c.id=e.category_id ORDER BY e.effective_date DESC,e.created_at DESC LIMIT 500");}
    @Transactional public Object create(@Valid Input input,String actor){
        billing.lock();String hash=billing.hash(billing.encode(input));var prior=repo.jdbc().queryForList("SELECT * FROM expenses WHERE request_key=?",input.requestKey().toString());
        if(!prior.isEmpty()){if(!prior.getFirst().get("request_hash").equals(hash)||!prior.getFirst().get("actor_id").equals(actor))throw new IllegalArgumentException("Expense request key already used");return prior.getFirst();}
        if(input.amountPaise()<=0 || input.date().isAfter(LocalDate.now(ReportService.ZONE)) || input.date().getYear()<1900 || input.note().trim().length()<3)throw new IllegalArgumentException("Enter a positive amount, valid past/today date and expense description");
        String id=UUID.randomUUID().toString();repo.jdbc().update("INSERT INTO expenses(id,request_key,request_hash,category_id,amount_paise,effective_date,method,note,actor_id,created_at) VALUES (?,?,?,?,?,?,?,?,?,?)",id,input.requestKey().toString(),hash,input.categoryId(),input.amountPaise(),input.date().toString(),input.method().name(),input.note().trim(),actor,BillingService.timestamp(Instant.now()));billing.audit("EXPENSE_CREATED",id,actor);return repo.jdbc().queryForMap("SELECT * FROM expenses WHERE id=?",id);
    }
    @Transactional public Object voidExpense(String id,String reason,String actor){billing.lock();if(reason==null || reason.trim().length()<3 || reason.length()>300)throw new IllegalArgumentException("Enter a void reason (3–300 characters)");if(repo.jdbc().update("UPDATE expenses SET voided_at=?,void_reason=?,voided_by=? WHERE id=? AND voided_at IS NULL",BillingService.timestamp(Instant.now()),reason.trim(),actor,id)!=1)throw new IllegalArgumentException("Expense is missing or already voided");billing.audit("EXPENSE_VOIDED",id,actor);return Map.of("ok",true);}
}
