package com.eastoftown.pos;
import jakarta.validation.Valid;
import java.util.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/labels")
public class LabelController {
    final LabelService labels;final AuthService auth;final CatalogRepository repo;
    public LabelController(LabelService labels,AuthService auth,CatalogRepository repo){this.labels=labels;this.auth=auth;this.repo=repo;}
    @PostMapping("/preview") public Object preview(@Valid @RequestBody LabelService.Request request){auth.current();return labels.preview(request);}
    @PostMapping("/pdf") public ResponseEntity<byte[]> pdf(@Valid @RequestBody LabelService.Request request){auth.current();return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF).body(labels.pdf(request));}
    @GetMapping("/printers") public Object printers(){auth.current();return labels.printers();}
    public record PrintInput(@Valid @jakarta.validation.constraints.NotNull LabelService.Request labels,String printer,boolean test){}
    @PostMapping("/print") public Object print(@Valid @RequestBody PrintInput input){auth.current();return labels.print(input.labels(),input.printer(),input.test());}
    @GetMapping("/settings") public Object settings(){auth.current();return repo.jdbc().queryForList("SELECT key,value FROM application_settings WHERE key LIKE 'label.%'");}
    public record Settings(@jakarta.validation.constraints.Min(35) @jakarta.validation.constraints.Max(110) double widthMm,@jakarta.validation.constraints.Min(25) @jakarta.validation.constraints.Max(100) double heightMm,String printer){}
    @PostMapping("/settings") @org.springframework.transaction.annotation.Transactional public Object settings(@Valid @RequestBody Settings settings){
        auth.admin();for(var entry:Map.of("label.width",String.valueOf(settings.widthMm()),"label.height",String.valueOf(settings.heightMm()),"label.printer",settings.printer()==null?"":settings.printer()).entrySet()){
            int changed=repo.jdbc().update("UPDATE application_settings SET value=? WHERE key=?",entry.getValue(),entry.getKey());if(changed==0)repo.jdbc().update("INSERT INTO application_settings VALUES (?,?)",entry.getKey(),entry.getValue());
        }return Map.of("saved",true);
    }
}
