package com.eastoftown.pos;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/customers")
public class CustomerController {
    final CustomerService customers;final BillingService billing;final AuthService auth;
    public CustomerController(CustomerService customers,BillingService billing,AuthService auth){this.customers=customers;this.billing=billing;this.auth=auth;}
    @GetMapping public Object search(@RequestParam(defaultValue="") String search,@RequestParam(defaultValue="0") int page){auth.current();return customers.search(search,page);}
    @GetMapping("/duplicates") public Object duplicates(@RequestParam String name){auth.current();return customers.duplicates(name);}
    @GetMapping("/countries") public Object countries(){auth.current();return PhoneNumbers.countries();}
    @GetMapping("/{id}") public Object detail(@PathVariable String id,@RequestParam(defaultValue="0") int page){auth.current();return customers.detail(id,page);}
    @PostMapping public Object create(@Valid @RequestBody BillingService.CustomerInput input){return billing.customer(input,auth.current().id());}
    @PutMapping("/{id}") public Object correct(@PathVariable String id,@Valid @RequestBody CustomerService.Correction input){return customers.correct(id,input,auth.admin().id());}
    @GetMapping("/{id}/changes") public Object changes(@PathVariable String id){auth.admin();return customers.changes(id);}
}
