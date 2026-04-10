package org.crystalocean.listEvent.listener;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.crystalocean.listEvent.service.ProxyBridgeService;
import org.crystalocean.listEvent.service.QueueManager;
import org.crystalocean.listEvent.util.MessageUtil;

public class PlayerQueueListener implements Listener {

    private final QueueManager queueManager;
    private final ProxyBridgeService proxyBridgeService;
    private final JavaPlugin plugin;

    public PlayerQueueListener(JavaPlugin plugin, QueueManager queueManager, ProxyBridgeService proxyBridgeService) {
        this.plugin = plugin;
        this.queueManager = queueManager;
        this.proxyBridgeService = proxyBridgeService;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    @SuppressWarnings("unused")
    public void onJoin(PlayerJoinEvent event) {
        if (!queueManager.isEnabled()) {
            proxyBridgeService.connect(event.getPlayer());
            return;
        }

        if (!proxyBridgeService.hasFreshCount()) {
            queueManager.enqueue(event.getPlayer());
            return;
        }

        int currentOnline = proxyBridgeService.getTargetPlayerCount();
        if (queueManager.isQueueModeActive(currentOnline)) {
            queueManager.enqueue(event.getPlayer());
            return;
        }

        String connectingMessage = plugin.getConfig().getString("queue.messages.connecting", "&aConectando ao servidor principal...");
        event.getPlayer().sendMessage(MessageUtil.toLegacyText(connectingMessage));
        proxyBridgeService.connect(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    @SuppressWarnings("unused")
    public void onQuit(PlayerQuitEvent event) {
        queueManager.handleQuit(event.getPlayer());
    }
}