package com.eastoftown.pos;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/controls")
public class ControlsController {
    final AuthService auth;final ReversalService reversals;final ExpenseService expenses;
    public ControlsController(AuthService auth,ReversalService reversals,ExpenseService expenses){this.auth=auth;this.reversals=reversals;this.expenses=expenses;}
    @GetMapping("/returns/{invoice}") public Object history(@PathVariable String invoice){auth.admin();return java.util.Map.of("history",reversals.history(invoice),"items",reversals.remaining(invoice));}
    @GetMapping("/return/{id}") public Object detail(@PathVariable String id){auth.admin();return reversals.detail(id);}
    @GetMapping("/return-status/{key}") public Object status(@PathVariable java.util.UUID key){return reversals.status(key,auth.admin().id());}
    @PostMapping("/returns/preview") public Object preview(@Valid @RequestBody ReversalService.Input input){auth.admin();return reversals.preview(input);}
    @PostMapping("/returns") public Object reverse(@Valid @RequestBody ReversalService.Input input){return reversals.commit(input,auth.admin().id());}
    @GetMapping("/expenses") public Object list(){auth.admin();return expenses.list();}
    @GetMapping("/expense-categories") public Object categories(){auth.admin();return expenses.categories();}
    public record Name(String name){} public record Reason(String reason){}
    @PostMapping("/expense-categories") public Object category(@RequestBody Name input){return expenses.category(input.name(),auth.admin().id());}
    @PostMapping("/expenses") public Object expense(@Valid @RequestBody ExpenseService.Input input){return expenses.create(input,auth.admin().id());}
    @PostMapping("/expenses/{id}/void") public Object voidExpense(@PathVariable String id,@RequestBody Reason input){return expenses.voidExpense(id,input.reason(),auth.admin().id());}
}
