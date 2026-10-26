package com.eastoftown.pos;

import java.time.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.security.SecureRandom;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@Service
public class AuthService {
    public record User(String id,String username,String role) {}
    public record SetupResult(User user,String recoveryCode) {}
    record Session(User user,Instant expires) {}
    private final CatalogRepository repo;
    private final BCryptPasswordEncoder encoder=new BCryptPasswordEncoder(12);
    private final Map<String,Session> sessions=new ConcurrentHashMap<>();
    private final Map<String,List<Instant>> attempts=new ConcurrentHashMap<>();
    public AuthService(CatalogRepository repo){this.repo=repo;}
    public boolean needsSetup(){return repo.jdbc().queryForObject("SELECT COUNT(*) FROM users",Integer.class)==0;}
    @Transactional public synchronized User setup(String username,String password){
        if(!needsSetup()) throw new IllegalArgumentException("Administrator already configured. Please sign in.");
        return setupWithRecovery(username,password).user();
    }
    @Transactional public synchronized SetupResult setupWithRecovery(String username,String password){
        if(!needsSetup()) throw new IllegalArgumentException("Administrator already configured. Please sign in.");
        String code=generateRecoveryCode();
        User user=create(username,password,"ADMIN");
        repo.jdbc().update("UPDATE users SET recovery_code_hash=?,recovery_code_created_at=? WHERE id=?",encoder.encode(code),Instant.now().toString(),user.id());
        return new SetupResult(user,code);
    }
    @Transactional public User create(String username,String password,String role){
        return create(username,password,role,null);
    }
    @Transactional public User create(String username,String password,String role,String creator){
        if(username==null || !username.matches("[A-Za-z0-9._-]{3,40}")) throw new IllegalArgumentException("Username must be 3–40 letters, digits, dots, underscores or hyphens");
        if(password==null || password.length()<12 || password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>72) throw new IllegalArgumentException("Password must contain at least 12 characters and at most 72 UTF-8 bytes");
        if(!Set.of("ADMIN","BILLING").contains(role)) throw new IllegalArgumentException("Invalid role");
        String id=UUID.randomUUID().toString();
        repo.jdbc().update("INSERT INTO users(id,username,password_hash,role,created_at) VALUES (?,?,?,?,?)",id,username,encoder.encode(password),role,Instant.now().toString());
        repo.jdbc().update("INSERT INTO audit_logs VALUES (?,?,?,?,?)",UUID.randomUUID().toString(),"USER_CREATED",id,creator==null?id:creator,Instant.now().toString());
        return new User(id,username,role);
    }
    public synchronized Map<String,Object> login(String username,String password){
        Instant now=Instant.now();
        // One local endpoint: cap the total attempts as well as per-account attempts.
        var recent=attempts.computeIfAbsent("local",k->new ArrayList<>());
        recent.removeIf(t->t.isBefore(now.minusSeconds(60)));
        if(recent.size()>=10) throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,"Too many login attempts. Wait one minute.");
        recent.add(now);
        var rows=repo.jdbc().queryForList("SELECT * FROM users WHERE username=? AND active=1",username==null?"":username);
        if(rows.isEmpty() || password==null || !encoder.matches(password,rows.getFirst().get("password_hash").toString())) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Username or password is incorrect");
        var row=rows.getFirst();User user=new User(row.get("id").toString(),row.get("username").toString(),row.get("role").toString());
        sessions.entrySet().removeIf(e->e.getValue().expires().isBefore(now));
        byte[] bytes=new byte[32];new SecureRandom().nextBytes(bytes);String session=HexFormat.of().formatHex(bytes);
        sessions.put(session,new Session(user,now.plusSeconds(8*3600)));
        return Map.of("session",session,"user",user);
    }
    @Transactional public synchronized Map<String,Object> recover(String username,String recoveryCode,String newPassword){
        Instant now=Instant.now();
        var recent=attempts.computeIfAbsent("recovery",k->new ArrayList<>());
        recent.removeIf(t->t.isBefore(now.minusSeconds(60)));
        if(recent.size()>=5) throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,"Too many recovery attempts. Wait one minute.");
        recent.add(now);
        if(recoveryCode==null || recoveryCode.length()<16) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Recovery code or username is incorrect");
        var rows=repo.jdbc().queryForList("SELECT * FROM users WHERE username=? AND role='ADMIN' AND active=1",username==null?"":username);
        if(rows.isEmpty() || rows.getFirst().get("recovery_code_hash")==null || !encoder.matches(recoveryCode,rows.getFirst().get("recovery_code_hash").toString())) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Recovery code or username is incorrect");
        validatePassword(newPassword);
        String id=rows.getFirst().get("id").toString();
        repo.jdbc().update("UPDATE users SET password_hash=?,recovery_code_hash=NULL,recovery_code_created_at=NULL WHERE id=?",encoder.encode(newPassword),id);
        sessions.entrySet().removeIf(e->e.getValue().user().id().equals(id));
        repo.jdbc().update("INSERT INTO audit_logs VALUES (?,?,?,?,?)",UUID.randomUUID().toString(),"ADMIN_PASSWORD_RECOVERED",id,id,now.toString());
        return Map.of("ok",true,"message","Password updated. Sign in with the new password.");
    }
    @Transactional public synchronized String rotateRecoveryCode(){
        User admin=admin(); String code=generateRecoveryCode();
        repo.jdbc().update("UPDATE users SET recovery_code_hash=?,recovery_code_created_at=? WHERE id=?",encoder.encode(code),Instant.now().toString(),admin.id());
        repo.jdbc().update("INSERT INTO audit_logs VALUES (?,?,?,?,?)",UUID.randomUUID().toString(),"ADMIN_RECOVERY_CODE_ROTATED",admin.id(),admin.id(),Instant.now().toString());
        return code;
    }
    private void validatePassword(String password){
        if(password==null || password.length()<12 || password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>72) throw new IllegalArgumentException("Password must contain at least 12 characters and at most 72 UTF-8 bytes");
    }
    private String generateRecoveryCode(){byte[] bytes=new byte[18];new SecureRandom().nextBytes(bytes);return "EOT-"+HexFormat.of().formatHex(bytes).toUpperCase(Locale.ROOT);}
    public Object users(){admin();return repo.jdbc().queryForList("SELECT id,username,role,active,created_at FROM users ORDER BY username");}
    @Transactional public synchronized Object resetBilling(String id,String password){
        User owner=admin();validatePassword(password);repo.jdbc().update("UPDATE application_settings SET value=value WHERE key='write_lock'");
        if(repo.jdbc().update("UPDATE users SET password_hash=? WHERE id=? AND role='BILLING'",encoder.encode(password),id)!=1)throw new IllegalArgumentException("Select a billing user; administrators use their recovery code");
        sessions.entrySet().removeIf(e->e.getValue().user().id().equals(id));
        repo.jdbc().update("INSERT INTO audit_logs VALUES (?,?,?,?,?)",UUID.randomUUID().toString(),"BILLING_PASSWORD_RESET",id,owner.id(),Instant.now().toString());return Map.of("ok",true);
    }
    @Transactional public synchronized Object userState(String id,String role,boolean active){
        User owner=admin();if(!Set.of("ADMIN","BILLING").contains(role))throw new IllegalArgumentException("Invalid role");
        repo.jdbc().update("UPDATE application_settings SET value=value WHERE key='write_lock'");
        var old=repo.jdbc().queryForMap("SELECT role,active FROM users WHERE id=?",id);
        if(id.equals(owner.id()) && (!active || !role.equals("ADMIN")))throw new IllegalArgumentException("You cannot disable or demote your own administrator account");
        if(old.get("role").equals("ADMIN") && ((Number)old.get("active")).intValue()==1 && (!active || !role.equals("ADMIN")) && repo.jdbc().queryForObject("SELECT COUNT(*) FROM users WHERE role='ADMIN' AND active=1",Integer.class)<=1)throw new IllegalArgumentException("Keep at least one active administrator");
        repo.jdbc().update("UPDATE users SET role=?,active=? WHERE id=?",role,active?1:0,id);sessions.entrySet().removeIf(e->e.getValue().user().id().equals(id));
        repo.jdbc().update("INSERT INTO audit_logs VALUES (?,?,?,?,?)",UUID.randomUUID().toString(),"USER_STATE_CHANGED_"+role+"_"+(active?"ACTIVE":"INACTIVE"),id,owner.id(),Instant.now().toString());return Map.of("ok",true);
    }
    public User current(){
        var attributes=RequestContextHolder.getRequestAttributes();
        String key=attributes instanceof ServletRequestAttributes a?a.getRequest().getHeader("X-EOT-Session"):null;
        Session session=key==null?null:sessions.get(key);
        if(session==null || session.expires().isBefore(Instant.now())) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Please sign in again");
        var rows=repo.jdbc().queryForList("SELECT role FROM users WHERE id=? AND active=1",session.user().id());
        if(rows.isEmpty() || !rows.getFirst().get("role").equals(session.user().role())){sessions.remove(key);throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Account changed. Sign in again");}
        return session.user();
    }
    public User admin(){User user=current();if(!user.role().equals("ADMIN")) throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Administrator permission required");return user;}
    public void logout(){var request=((ServletRequestAttributes)RequestContextHolder.currentRequestAttributes()).getRequest();String key=request.getHeader("X-EOT-Session");if(key!=null)sessions.remove(key);}
}
