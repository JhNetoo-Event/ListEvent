package org.crystalocean.listEvent.listener;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerLoginEvent;
import org.crystalocean.listEvent.ListEventPlugin;
import org.crystalocean.listEvent.service.AllowlistService;
import org.crystalocean.listEvent.util.MessageUtil;

import java.lang.reflect.Method;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class PlayerPreLoginListener implements Listener {

    private final ListEventPlugin plugin;
    private final Cache<UUID, Boolean> pendingChecks = CacheBuilder.newBuilder()
            .expireAfterWrite(1, TimeUnit.MINUTES)
            .build();
    public PlayerPreLoginListener(ListEventPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onAsyncPreLogin(AsyncPlayerPreLoginEvent event) {
        UUID uuid = event.getUniqueId();
        String name = event.getName();

        AllowlistService allowlistService = plugin.getAllowlistService();
        if (!allowlistService.isPluginEnabled()) {
            return;
        }

        // 1. Check if allowed in repository/cache
        if (allowlistService.isAllowedInCache(uuid, name)) {
            return;
        }

        // Note: OP bypass and permissions cannot be checked reliably in AsyncPlayerPreLoginEvent 
        // without a Player object. However, for a high-performance event server, 
        // it's better to keep the allowlist as the primary source of truth.
        // We will keep a small check in PlayerLoginEvent only for OPs/Permissions if enabled.
        pendingChecks.put(uuid, Boolean.TRUE);
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onPlayerLogin(PlayerLoginEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        Boolean needsCheck = pendingChecks.getIfPresent(uuid);
        pendingChecks.invalidate(uuid);

        if (needsCheck == null) {
            AllowlistService allowlistService = plugin.getAllowlistService();
            if (!allowlistService.isPluginEnabled() || allowlistService.isAllowedInCache(uuid, player.getName())) {
                return;
            }
        }

        // Bypass checks
        if (plugin.getConfig().getBoolean("behavior.allow-ops-bypass", true) && player.isOp()) {
            return;
        }

        for (String perm : plugin.getConfig().getStringList("behavior.allowed-permissions")) {
            if (player.hasPermission(perm)) {
                return;
            }
        }

        // Deny access
        if (plugin.getConfig().getBoolean("behavior.log-joins-denied", true)) {
            plugin.getAuditLogger().log("Entrada negada para " + player.getName() + " (" + uuid + ")");
        }

        String rawKickMessage = plugin.getAllowlistService().getKickMessage();
        event.disallow(PlayerLoginEvent.Result.KICK_WHITELIST, MessageUtil.toComponent(rawKickMessage));
    }
}