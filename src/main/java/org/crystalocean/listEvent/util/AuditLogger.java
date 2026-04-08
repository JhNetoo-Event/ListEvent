package org.crystalocean.listEvent.util;

import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;

public class AuditLogger {

    private final JavaPlugin plugin;
    private final Path auditFile;

    public AuditLogger(JavaPlugin plugin) {
        this.plugin = plugin;
        this.auditFile = plugin.getDataFolder().toPath().resolve("audit.log");
    }

    public void log(String action) {
        try {
            Files.createDirectories(auditFile.getParent());
            String line = "[" + Instant.now() + "] " + action + System.lineSeparator();
            Files.writeString(
                    auditFile,
                    line,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.WRITE,
                    StandardOpenOption.APPEND
            );
        } catch (IOException e) {
            plugin.getLogger().warning("Falha ao escrever audit.log: " + e.getMessage());
        }
    }
}