package com.minipaas.config;

import io.github.cdimascio.dotenv.Dotenv;
import lombok.extern.slf4j.Slf4j;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Loads root {@code .env} before Spring starts so {@code SPRING_DATASOURCE_URL} and other
 * keys work with {@code mvn spring-boot:run} without exporting variables manually.
 * Does not override real OS environment variables.
 */
@Slf4j
public final class DotenvLoader {

    private DotenvLoader() {
    }

    public static void load() {
        Path cwd = Path.of(System.getProperty("user.dir", ".")).normalize();
        Path envFile = cwd.resolve(".env");
        if (!Files.isRegularFile(envFile) && cwd.getParent() != null) {
            Path parentEnv = cwd.getParent().resolve(".env");
            if (Files.isRegularFile(parentEnv)) {
                envFile = parentEnv;
            }
        }

        if (!Files.isRegularFile(envFile)) {
            log.debug("No .env file at {} — using process environment only", envFile);
            return;
        }

        Dotenv dotenv = Dotenv.configure()
                .directory(envFile.getParent().toString())
                .filename(envFile.getFileName().toString())
                .ignoreIfMalformed()
                .load();

        dotenv.entries().forEach(e -> {
            String key = e.getKey();
            if (key == null || key.isBlank()) {
                return;
            }
            if (System.getenv(key) != null) {
                return;
            }
            if (System.getProperty(key) != null) {
                return;
            }
            System.setProperty(key, e.getValue());
        });
        log.info("Loaded environment entries from {}", envFile.toAbsolutePath());
    }
}
