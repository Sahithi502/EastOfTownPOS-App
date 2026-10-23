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
    record Session(User user,Instant expires) {}
    private final CatalogRepository repo;
    private final BCryptPasswordEncoder encoder=new BCryptPasswordEncoder(12);
    private final Map<String,Session> sessions=new ConcurrentHashMap<>();
    private final Map<String,List<Instant>> attempts=new ConcurrentHashMap<>();
    public AuthService(CatalogRepository repo){this.repo=repo;}
    public boolean needsSetup(){return repo.jdbc().queryForObject("SELECT COUNT(*) FROM users",Integer.class)==0;}
    @Transactional public synchronized User setup(String username,String password){
        if(!needsSetup()) throw new IllegalArgumentException("Administrator already configured. Please sign in.");
        return create(username,password,"ADMIN");
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
    public User current(){
        var attributes=RequestContextHolder.getRequestAttributes();
        String key=attributes instanceof ServletRequestAttributes a?a.getRequest().getHeader("X-EOT-Session"):null;
        Session session=key==null?null:sessions.get(key);
        if(session==null || session.expires().isBefore(Instant.now())) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Please sign in again");
        return session.user();
    }
    public User admin(){User user=current();if(!user.role().equals("ADMIN")) throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Administrator permission required");return user;}
    public void logout(){var request=((ServletRequestAttributes)RequestContextHolder.currentRequestAttributes()).getRequest();String key=request.getHeader("X-EOT-Session");if(key!=null)sessions.remove(key);}
}
