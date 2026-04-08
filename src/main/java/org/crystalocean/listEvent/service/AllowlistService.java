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
                        now,
                        true
                ));

        if (repository.findByUuid(resolved.uuid()).isPresent() && record.isActive()) {
            return AddResult.alreadyAdded(resolved.name());
        }

        record.setActive(true);
        record.setUpdatedAt(now);

        repository.saveRecord(record);
        auditLogger.log(actor.getName() + " adicionou " + resolved.name() + " (" + resolved.uuid() + ") à allowlist. Nota: " + note);

        return AddResult.success(resolved.name(), resolved.uuid());
    }

    public RemoveResult removePlayer(String target, CommandSender actor) {
        Optional<AllowedPlayerRecord> optionalRecord = getInfo(target);
        if (optionalRecord.isEmpty() || !optionalRecord.get().isActive()) {
            return RemoveResult.notFound(target);
        }

        AllowedPlayerRecord record = optionalRecord.get();
        if (plugin.getConfig().getBoolean("behavior.persist-removals-as-inactive", true)) {
            record.setActive(false);
            record.setUpdatedAt(Instant.now());
            repository.saveRecord(record);
        } else {
            repository.deleteRecord(record.getUuid());
        }

        auditLogger.log(actor.getName() + " removeu " + record.getLastKnownName() + " (" + record.getUuid() + ") da allowlist.");

        Player player = Bukkit.getPlayer(record.getUuid());
        if (player != null && player.isOnline()) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                player.kick(org.crystalocean.listEvent.util.MessageUtil.toComponent(getKickMessage()));
            });
        }

        return RemoveResult.success(record.getLastKnownName());
    }

    public String getKickMessage() {
        return plugin.getConfig().getString("kick-message", "&cVoce nao esta autorizado a entrar neste servidor.");
    }

    private ResolvedPlayer resolvePlayer(String target) {
        Optional<UUID> uuidOpt = parseUuid(target);
        if (uuidOpt.isPresent()) {
            UUID uuid = uuidOpt.get();
            OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(uuid);
            String name = offlinePlayer.getName() != null ? offlinePlayer.getName() : target;
            return new ResolvedPlayer(uuid, name);
        }

        Player player = Bukkit.getPlayerExact(target);
        if (player != null) {
            return new ResolvedPlayer(player.getUniqueId(), player.getName());
        }

        PlayerProfile profile = Bukkit.createProfile(target);
        try {
            profile.complete(true);
            if (profile.getId() != null) {
                return new ResolvedPlayer(profile.getId(), profile.getName());
            }
        } catch (Exception e) {
             plugin.getLogger().warning("Failed to resolve profile for " + target);
        }

        OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(target);
        if (offlinePlayer.hasPlayedBefore() || offlinePlayer.isOnline()) {
             return new ResolvedPlayer(offlinePlayer.getUniqueId(), target);
        }

        return null;
    }

    private Optional<UUID> parseUuid(String uuidStr) {
        try {
            return Optional.of(UUID.fromString(uuidStr));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    public record ResolvedPlayer(UUID uuid, String name) {}

    public record AddResult(boolean success, boolean alreadyAdded, String name, UUID uuid) {
        public static AddResult success(String name, UUID uuid) {
            return new AddResult(true, false, name, uuid);
        }
        public static AddResult alreadyAdded(String name) {
            return new AddResult(false, true, name, null);
        }
        public static AddResult notFound(String name) {
            return new AddResult(false, false, name, null);
        }
    }

    public record RemoveResult(boolean success, String name) {
        public static RemoveResult success(String name) {
            return new RemoveResult(true, name);
        }
        public static RemoveResult notFound(String name) {
            return new RemoveResult(false, name);
        }
    }
}