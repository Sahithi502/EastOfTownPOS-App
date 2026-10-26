package com.eastoftown.pos;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;
@RestController @RequestMapping("/api/backups")
public class BackupController {
    final BackupService backups;final AuthService auth;
    public BackupController(BackupService backups,AuthService auth){this.backups=backups;this.auth=auth;}
    @GetMapping public Object status(){auth.admin();return backups.status();}
    @PostMapping("/settings") public Object configure(@RequestBody BackupService.Config input){return backups.configure(input,auth.admin().id());}
    @PostMapping public Object backup()throws Exception{return backups.backup(auth.admin().id());}
    public record Restore(String path){}
    @PostMapping("/restore") public Object restore(@RequestBody Restore input)throws Exception{return backups.stageRestore(input.path(),auth.admin().id());}
    @PostMapping("/cancel-restore") public Object cancel()throws Exception{return backups.cancelRestore(auth.admin().id());}
    @GetMapping("/export") public ResponseEntity<byte[]> export()throws Exception{auth.admin();return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(backups.exportDatabase());}
}
