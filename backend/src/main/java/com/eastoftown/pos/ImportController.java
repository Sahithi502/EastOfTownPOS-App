package com.eastoftown.pos;

import java.util.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController @RequestMapping("/api/imports")
public class ImportController {
    final AuthService auth;final ImportService service;final ImportRepository repo;final ExcelFiles excel;final CatalogRepository catalog;
    public ImportController(AuthService auth,ImportService service,ImportRepository repo,ExcelFiles excel,CatalogRepository catalog){this.auth=auth;this.service=service;this.repo=repo;this.excel=excel;this.catalog=catalog;}
    @PostMapping("/upload") public Object upload(@RequestParam MultipartFile file)throws java.io.IOException{auth.admin();return service.upload(file.getOriginalFilename(),file.getBytes());}
    @PostMapping("/{id}/preview") public Object preview(@PathVariable String id,@RequestBody ImportService.Config config){auth.admin();return service.preview(id,config);}
    public record Confirmation(String token,boolean confirm){}
    @PostMapping("/{id}/commit") public Object commit(@PathVariable String id,@RequestBody Confirmation input){return service.commit(id,input.token(),input.confirm(),auth.admin().id());}
    @GetMapping("/history") public Object history(){auth.admin();return repo.history();}
    @GetMapping("/template") public ResponseEntity<byte[]> template(){auth.current();return download(excel.template(catalog.categories()),"stock-template.xlsx");}
    @GetMapping("/{id}/errors") public ResponseEntity<byte[]> errors(@PathVariable String id){auth.admin();return download(service.errors(id),"import-validation.xlsx");}
    static ResponseEntity<byte[]> download(byte[] bytes,String name){return ResponseEntity.ok().header("Content-Disposition","attachment; filename=\""+name+"\"").contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")).body(bytes);}
}
