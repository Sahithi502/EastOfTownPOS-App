package com.eastoftown.pos;

import java.nio.file.*;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@org.springframework.scheduling.annotation.EnableScheduling
public class Application {
    public static void main(String[] args) throws Exception {
        String base = System.getenv("EOT_DATA_DIR");
        if (base == null || base.isBlank()) {
            String local = System.getenv("LOCALAPPDATA");
            base = Path.of(local == null ? System.getProperty("user.home") : local, "EastOfTown", "data").toString();
        }
        Path directory = Path.of(base).toAbsolutePath();
        Files.createDirectories(directory);
        BackupFiles.restorePending(directory);
        System.setProperty("spring.datasource.url", "jdbc:sqlite:" + directory.resolve("eastoftown.db"));
        SpringApplication.run(Application.class, args);
    }
}
