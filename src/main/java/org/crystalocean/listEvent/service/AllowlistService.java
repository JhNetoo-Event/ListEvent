package org.crystalocean.listEvent.service;

import com.destroystokyo.paper.profile.PlayerProfile;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.crystalocean.listEvent.model.AllowedPlayerRecord;
import org.crystalocean.listEvent.repository.AllowlistRepository;
import org.crystalocean.listEvent.util.AuditLogger;

import java.time.Instant;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

public class AllowlistService {

    private final JavaPlugin plugin;
    private final AllowlistRepository repository;
    private final AuditLogger auditLogger;

    public AllowlistService(JavaPlugin plugin, AllowlistRepository repository, AuditLogger auditLogger) {
        this.plugin = plugin;
        this.repository = repository;
        this.auditLogger = auditLogger;
    }

    public boolean isPluginEnabled() {
        return plugin.getConfig().getBoolean("enabled", true);
    }

    public void setPluginEnabled(boolean enabled) {
        plugin.getConfig().set("enabled", enabled);
        plugin.saveConfig();
        auditLogger.log("Sistema " + (enabled ? "ativado" : "desativado"));
    }

    public boolean canJoin(UUID uuid, String name) {
        if (!isPluginEnabled()) {
            return true;
        }

        if (repository.isAllowed(uuid)) {
            if (plugin.getConfig().getBoolean("behavior.update-name-on-join", true) && name != null && !name.isBlank()) {
                repository.updateLastKnownName(uuid, name);
                repository.save();
            }
            return true;
        }

        if (plugin.getConfig().getBoolean("behavior.allow-ops-bypass", true)) {
            OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(uuid);
            return offlinePlayer.isOp();
        }

        return false;
    }

    public Optional<AllowedPlayerRecord> getInfo(String target) {
        Optional<UUID> uuid = parseUuid(target);
        if (uuid.isPresent()) {
            return repository.findByUuid(uuid.get());
        }
        return repository.findByName(target);
    }

    public Collection<AllowedPlayerRecord> listAll() {
        return repository.getAllSorted();
    }

    public AddResult addPlayer(String target, String note, CommandSender actor) {
        ResolvedPlayer resolved = resolvePlayer(target);
        if (resolved == null) {
            return AddResult.notFound(target);
        }

        Instant now = Instant.now();
        AllowedPlayerRecord record = repository.findByUuid(resolved.uuid())
                .orElseGet(() -> new AllowedPlayerRecord(
                        resolved.uuid(),
                        resolved.name(),
                        actor.getName(),
                        note,
                        now,
    }