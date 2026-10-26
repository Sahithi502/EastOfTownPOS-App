package com.eastoftown.pos;
import java.util.*;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/auth")
public class AuthController {
    final AuthService auth;
    public AuthController(AuthService auth){this.auth=auth;}
    public record Credentials(String username,String password){}
    public record Recovery(String username,String recoveryCode,String newPassword){}
    public record NewUser(String username,String password,String role){}
    @GetMapping("/status") public Object status(){return Map.of("needsSetup",auth.needsSetup());}
    @PostMapping("/setup") public Object setup(@RequestBody Credentials input){return auth.setupWithRecovery(input.username(),input.password());}
    @PostMapping("/login") public Object login(@RequestBody Credentials input){return auth.login(input.username(),input.password());}
    @PostMapping("/logout") public Object logout(){auth.logout();return Map.of("ok",true);}
    @PostMapping("/users") public Object create(@RequestBody NewUser input){return auth.create(input.username(),input.password(),input.role(),auth.admin().id());}
    @PostMapping("/recover") public Object recover(@RequestBody Recovery input){return auth.recover(input.username(),input.recoveryCode(),input.newPassword());}
    @PostMapping("/recovery-code") public Object rotateRecoveryCode(){return Map.of("recoveryCode",auth.rotateRecoveryCode());}
    public record Reset(String password){} public record State(String role,boolean active){}
    @GetMapping("/users") public Object users(){return auth.users();}
    @PostMapping("/users/{id}/reset") public Object reset(@PathVariable String id,@RequestBody Reset input){return auth.resetBilling(id,input.password());}
    @PostMapping("/users/{id}/state") public Object state(@PathVariable String id,@RequestBody State input){return auth.userState(id,input.role(),input.active());}
}
