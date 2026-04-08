package org.crystalocean.listEvent.listener;

import net.kyori.adventure.text.Component;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.crystalocean.listEvent.ListEventPlugin;
import org.crystalocean.listEvent.service.AllowlistService;
import org.crystalocean.listEvent.util.MessageUtil;

import java.util.UUID;

public class PlayerPreLoginListener implements Listener {

    private final ListEventPlugin plugin;
    private final AllowlistService allowlistService;

    public PlayerPreLoginListener(ListEventPlugin plugin, AllowlistService allowlistService) {
        this.plugin = plugin;
        this.allowlistService = allowlistService;
    }

    @EventHandler
    @SuppressWarnings("unused")
    public void onAsyncPreLogin(AsyncPlayerPreLoginEvent event) {
        UUID uuid = event.getUniqueId();
        String name = event.getName();

        if (allowlistService.canJoin(uuid, name)) {
            return;
        }

        if (plugin.getConfig().getBoolean("behavior.log-joins-denied", true)) {
            plugin.getAuditLogger().log("Entrada negada para " + name + " (" + uuid + ")");
        }

        Component kickMessage = MessageUtil.toComponent(allowlistService.getKickMessage());
        event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_WHITELIST, kickMessage);
    }
}