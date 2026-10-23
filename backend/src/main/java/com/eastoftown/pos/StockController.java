package com.eastoftown.pos;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/stock")
public class StockController {
    final AuthService auth;final StockService stock;
    public StockController(AuthService auth,StockService stock){this.auth=auth;this.stock=stock;}
    @PostMapping("/adjustments") public Object adjust(@Valid @RequestBody StockService.Adjustment input){return stock.adjust(input,auth.admin().id());}
    @GetMapping("/movements/{id}") public Object history(@PathVariable String id){auth.current();return stock.history(id);}
}
