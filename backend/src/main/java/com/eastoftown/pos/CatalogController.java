package com.eastoftown.pos;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;
import org.springframework.dao.DataIntegrityViolationException;

@RestController
@RequestMapping("/api")
public class CatalogController {
    private final CatalogRepository repo; private final CatalogService service;
    public CatalogController(CatalogRepository repo,CatalogService service) { this.repo=repo;this.service=service; }
    @GetMapping("/health") public Map<String,Object> health() { return Map.of("status","ready","database",repo.jdbc().queryForObject("PRAGMA quick_check",String.class)); }
    @GetMapping("/categories") public Object categories() {return repo.categories();}
    public record CategoryInput(@NotBlank @Size(max=80) String name) {}
    @PostMapping("/categories") public Object category(@Valid @RequestBody CategoryInput input) {return Map.of("id",service.category(input.name()));}
    @GetMapping("/variants") public Object variants(@RequestParam(defaultValue="") String search) {return repo.variants(search);}
    @PostMapping("/variants") public Object create(@Valid @RequestBody CatalogService.VariantInput input) {return Map.of("id",service.save(null,input));}
    @PutMapping("/variants/{id}") public Object update(@PathVariable String id,@Valid @RequestBody CatalogService.VariantInput input) {return Map.of("id",service.save(id,input));}
}

@RestControllerAdvice
class Errors {
    @ExceptionHandler(DataIntegrityViolationException.class) ResponseEntity<?> conflict() {return ResponseEntity.status(409).body(Map.of("message","Duplicate SKU, barcode or variant, or invalid category. Check your entries."));}
    @ExceptionHandler({IllegalArgumentException.class,ArithmeticException.class}) ResponseEntity<?> bad(Exception e) {return ResponseEntity.badRequest().body(Map.of("message",e.getMessage()));}
    @ExceptionHandler(org.springframework.web.bind.MethodArgumentNotValidException.class) ResponseEntity<?> validation(org.springframework.web.bind.MethodArgumentNotValidException e) {
        return ResponseEntity.badRequest().body(Map.of("message",e.getBindingResult().getFieldErrors().stream().map(f->f.getField()+": "+f.getDefaultMessage()).toList().toString()));
    }
}
