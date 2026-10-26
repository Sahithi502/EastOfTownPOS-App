package com.eastoftown.pos;

import java.nio.file.*;
import java.sql.*;
import java.security.MessageDigest;
import java.util.*;
import org.springframework.core.io.ClassPathResource;

/** SQLite-specific file operations live here, outside the business ledgers. */
public final class BackupFiles {
    private BackupFiles(){}
    static Connection read(Path file)throws Exception{
        if(!Files.isRegularFile(file))throw new IllegalArgumentException("Database file not found");
        return DriverManager.getConnection("jdbc:sqlite:"+file.toAbsolutePath().toUri()+"?mode=ro");
    }
    public static Map<String,Object> validate(Path file)throws Exception{
        var counts=new TreeMap<String,Long>();
        try(var c=read(file);var s=c.createStatement()){
            try(var r=s.executeQuery("PRAGMA integrity_check")){if(!r.next()||!r.getString(1).equals("ok")||r.next())throw new IllegalArgumentException("Database integrity check failed");}
            try(var r=s.executeQuery("PRAGMA foreign_key_check")){if(r.next())throw new IllegalArgumentException("Database foreign keys are invalid");}
            String[] names={"catalog","imports_stock_users","administrator_recovery","billing","checkout_safety","customer_reports","controls"};int count=0;
            try(var r=s.executeQuery("SELECT version,checksum FROM schema_migrations ORDER BY version")){
                while(r.next()){
                    int v=r.getInt(1);if(v!=++count || v>names.length)throw new IllegalArgumentException("Unsupported backup schema");
                    byte[] sql=new ClassPathResource("db/migration/V%03d__%s.sql".formatted(v,names[v-1])).getContentAsByteArray();String expected=hash(sql),stored=r.getString(2);
                    if(!expected.equals(stored) && !(v==5 && stored.equals("11a29fe62f4b0dc74be40cc87075c75064eaae011f9e3c498a551ff13a0eb710") && expected.equals("5e991ab6b0ddf97a31afbeddb7ff62a25008957ba6e23b989332861c17a8684c")))throw new IllegalArgumentException("Backup migration checksum differs at version "+v);
                }
            }
            if(count<5)throw new IllegalArgumentException("Restore requires a Stage 3 or newer database");
            var tables=new ArrayList<String>();try(var r=s.executeQuery("SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' ORDER BY name")){while(r.next())tables.add(r.getString(1));}
            for(String name:tables)try(var r=s.executeQuery("SELECT COUNT(*) FROM \""+name.replace("\"","\"\"")+"\"")){r.next();counts.put(name,r.getLong(1));}
            return Map.of("counts",counts,"migrations",count,"health","ok");
        }
    }
    static String hash(byte[] data)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));}
    static String hash(Path file)throws Exception{var digest=MessageDigest.getInstance("SHA-256");try(var in=Files.newInputStream(file)){byte[] buffer=new byte[65536];for(int n;(n=in.read(buffer))!=-1;)digest.update(buffer,0,n);}return HexFormat.of().formatHex(digest.digest());}
    public static void snapshot(Path source,Path destination)throws Exception{
        if(Files.exists(destination))throw new IllegalArgumentException("Backup destination already exists");
        try(var c=read(source);var s=c.createStatement()){s.execute("PRAGMA busy_timeout=10000");s.execute("VACUUM INTO '"+destination.toAbsolutePath().toString().replace("'","''")+"'");}
        validate(destination);
    }
    static void atomic(Path from,Path to)throws Exception{try{Files.move(from,to,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}catch(AtomicMoveNotSupportedException e){throw new IllegalStateException("This disk cannot atomically restore the database; original retained",e);}}
    /** Called before a datasource is opened. Never overwrites the pre-restore rescue copy. */
    public static void restorePending(Path directory)throws Exception{
        Path marker=directory.resolve("restore-request.txt");if(!Files.exists(marker))return;
        var values=Files.readAllLines(marker);if(values.size()!=2 || !values.get(1).matches("rescue-[a-f0-9-]{36}\\.db"))throw new IllegalArgumentException("Invalid pending restore request");
        Path staged=directory.resolve("pending-restore.db"),live=directory.resolve("eastoftown.db"),rescue=directory.resolve(values.get(1));
        if(!hash(staged).equals(values.getFirst()))throw new IllegalArgumentException("Pending restore checksum differs; original database retained");validate(staged);
        if(Files.exists(live) && !Files.exists(rescue))snapshot(live,rescue);
        if(Files.exists(live))try(var c=DriverManager.getConnection("jdbc:sqlite:"+live);var s=c.createStatement();var r=s.executeQuery("PRAGMA wal_checkpoint(TRUNCATE)")){if(!r.next() || r.getInt(1)!=0)throw new IllegalStateException("Database is still busy; close other instances before restoring");}
        Path replacement=directory.resolve("restore-replacement.db");Files.copy(staged,replacement,StandardCopyOption.REPLACE_EXISTING);validate(replacement);
        // No application datasource exists here. The rescue includes committed WAL contents.
        Files.deleteIfExists(directory.resolve("eastoftown.db-wal"));Files.deleteIfExists(directory.resolve("eastoftown.db-shm"));
        atomic(replacement,live);Files.move(marker,directory.resolve("restore-completed.txt"),StandardCopyOption.REPLACE_EXISTING);
        Files.deleteIfExists(staged);
    }
}
