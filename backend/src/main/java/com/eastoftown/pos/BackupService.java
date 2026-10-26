package com.eastoftown.pos;

import java.nio.file.*;
import java.sql.*;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Scheduled;

@Service
public class BackupService {
    public record Config(String location,int intervalHours,int retain){}
    final CatalogRepository repo;final BillingService billing;volatile String lastError="";
    public BackupService(CatalogRepository repo,BillingService billing){this.repo=repo;this.billing=billing;}
    Path live(){String url=repo.jdbc().execute((org.springframework.jdbc.core.ConnectionCallback<String>)c->c.getMetaData().getURL());return Path.of(url.substring("jdbc:sqlite:".length())).toAbsolutePath();}
    public Config config(){var rows=repo.jdbc().queryForList("SELECT value FROM application_settings WHERE key='backup_config'",String.class);try{return rows.isEmpty()?new Config(live().getParent().resolve("backups").toString(),0,10):new com.fasterxml.jackson.databind.ObjectMapper().readValue(rows.getFirst(),Config.class);}catch(Exception e){throw new IllegalStateException("Backup settings unavailable",e);}}
    public synchronized Object configure(Config input,String actor){
        if(input.location()==null || !Path.of(input.location()).isAbsolute() || input.intervalHours()<0 || input.intervalHours()>720 || input.retain()<2 || input.retain()>100)throw new IllegalArgumentException("Use an absolute backup folder, 0–720 hour interval and 2–100 retained copies");
        repo.jdbc().update("INSERT INTO application_settings(key,value) VALUES ('backup_config',?) ON CONFLICT(key) DO UPDATE SET value=excluded.value",billing.encode(input));billing.audit("BACKUP_SETTINGS_CHANGED","backup_config",actor);return status();
    }
    public Object status(){return Map.of("config",config(),"database",live().toString(),"lastSuccess",setting("backup_last_success"),"lastError",lastError,"restorePending",Files.exists(live().getParent().resolve("restore-request.txt")));}
    String setting(String key){var rows=repo.jdbc().queryForList("SELECT value FROM application_settings WHERE key=?",String.class,key);return rows.isEmpty()?"":rows.getFirst();}
    public synchronized Object backup(String actor)throws Exception{
        Config cfg=config();Path folder=Path.of(cfg.location());Files.createDirectories(folder);String name="eot-backup-"+UUID.randomUUID()+".db";Path partial=folder.resolve(name+".partial"),target=folder.resolve(name);
        try{
            BackupFiles.snapshot(live(),partial);var validation=BackupFiles.validate(partial);BackupFiles.atomic(partial,target);
            Files.writeString(folder.resolve(name+".json"),billing.encode(Map.of("sha256",BackupFiles.hash(target),"createdAt",Instant.now().toString(),"validation",validation)),StandardOpenOption.CREATE_NEW);
            repo.jdbc().update("INSERT INTO application_settings(key,value) VALUES ('backup_last_success',?) ON CONFLICT(key) DO UPDATE SET value=excluded.value",Instant.now().toString());billing.audit("BACKUP_VALIDATED",name,actor);lastError="";
            // Retain only our own verified backups; never remove the newly verified copy.
            List<Path> verified=new ArrayList<>();try(var files=Files.list(folder)){for(Path f:files.filter(p->p.getFileName().toString().matches("eot-backup-[a-f0-9-]{36}\\.db")).toList())try{BackupFiles.validate(f);verified.add(f);}catch(Exception ignored){}}
            verified.sort(Comparator.comparingLong(p->p.toFile().lastModified()));while(verified.size()>cfg.retain()){Path old=verified.removeFirst();if(!old.equals(target)){Files.delete(old);Files.deleteIfExists(Path.of(old+".json"));}}
            return Map.of("path",target.toString(),"validation",validation);
        }catch(Exception e){lastError="Backup failed. Check the destination and available disk space.";throw e;}
    }
    public synchronized Object stageRestore(String source,String actor)throws Exception{
        Path candidate=Path.of(source).toAbsolutePath(),directory=live().getParent();
        if(candidate.equals(live()))throw new IllegalArgumentException("Select a backup, not the running database");
        if(Files.exists(directory.resolve("restore-request.txt")))throw new IllegalArgumentException("Restore already pending. Restart or cancel it first");
        Path manifest=Path.of(candidate+".json");if(Files.exists(manifest)){var metadata=new com.fasterxml.jackson.databind.ObjectMapper().readTree(Files.readAllBytes(manifest));if(!BackupFiles.hash(candidate).equals(metadata.path("sha256").asText()))throw new IllegalArgumentException("Backup file differs from its SHA-256 manifest; select another verified copy");}
        // A live consistent rescue is also created before swap at the next launch.
        backup(actor);Path staged=directory.resolve("pending-restore.db");Files.deleteIfExists(staged);BackupFiles.snapshot(candidate,staged);
        var validation=BackupFiles.validate(staged);Path marker=directory.resolve("restore-request.tmp");Files.writeString(marker,BackupFiles.hash(staged)+"\nrescue-"+UUID.randomUUID()+".db\n");BackupFiles.atomic(marker,directory.resolve("restore-request.txt"));billing.audit("RESTORE_STAGED","backup",actor);return Map.of("validation",validation,"message","Backup validated. Close the application and restart to restore. Sign in using credentials from the backup. Current records will be replaced; a rescue copy is retained.");
    }
    public synchronized Object cancelRestore(String actor)throws Exception{Files.deleteIfExists(live().getParent().resolve("restore-request.txt"));Files.deleteIfExists(live().getParent().resolve("pending-restore.db"));billing.audit("RESTORE_CANCELLED","backup",actor);return Map.of("ok",true);}
    public boolean restorePending(){return Files.exists(live().getParent().resolve("restore-request.txt"));}
    @Scheduled(fixedDelay=60000,initialDelay=60000) public void scheduled(){try{if(restorePending())return;var cfg=config();String last=setting("backup_last_success");if(cfg.intervalHours()>0 && (last.isEmpty()||Instant.parse(last).plusSeconds(cfg.intervalHours()*3600L).isBefore(Instant.now())))backup("scheduled-backup");}catch(Exception e){lastError="Scheduled backup failed. Check backup settings and destination.";}}
    public byte[] exportDatabase()throws Exception{
        Path snapshot=Files.createTempDirectory("eot-export-").resolve("export.db");try{BackupFiles.snapshot(live(),snapshot);var tables=new LinkedHashMap<String,Object>();try(var c=BackupFiles.read(snapshot);var s=c.createStatement()){
            var names=new ArrayList<String>();try(var r=s.executeQuery("SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' ORDER BY name")){while(r.next())names.add(r.getString(1));}
            for(String name:names){var rows=new ArrayList<Map<String,Object>>();try(var r=s.executeQuery("SELECT * FROM \""+name.replace("\"","\"\"")+"\"")){while(r.next()){var row=new LinkedHashMap<String,Object>();for(int i=1;i<=r.getMetaData().getColumnCount();i++)row.put(r.getMetaData().getColumnName(i),r.getObject(i));rows.add(row);}}tables.put(name,rows);}
        }return billing.encode(Map.of("format","eastoftown-portable-v1","money","integer INR paise","tables",tables)).getBytes(java.nio.charset.StandardCharsets.UTF_8);}finally{Files.deleteIfExists(snapshot);Files.deleteIfExists(snapshot.getParent());}
    }
}
