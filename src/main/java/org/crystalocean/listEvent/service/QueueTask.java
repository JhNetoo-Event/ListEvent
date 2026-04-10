package org.crystalocean.listEvent.service;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.crystalocean.listEvent.model.QueueEntry;
import org.crystalocean.listEvent.util.MessageUtil;

import java.util.List;

public class QueueTask implements Runnable {

    private final JavaPlugin plugin;
    private final QueueManager queueManager;
    private final ProxyBridgeService proxyBridgeService;

    public QueueTask(JavaPlugin plugin, QueueManager queueManager, ProxyBridgeService proxyBridgeService) {
        this.plugin = plugin;
        this.queueManager = queueManager;
        this.proxyBridgeService = proxyBridgeService;
    }

    @Override
    public void run() {
        if (!queueManager.isEnabled()) {
            return;
        }

        queueManager.refreshAutoOpenState();
        queueManager.cleanupGraceEntries();
        queueManager.sendPositionUpdates();
        proxyBridgeService.requestTargetPlayerCount();

        if (!queueManager.isQueueOpen() || !proxyBridgeService.hasFreshCount()) {
            return;
        }

        int playersPerRelease = plugin.getConfig().getInt("queue.release-rate.players-per-interval", 5);
        int maxOnline = plugin.getConfig().getInt("queue.flow-control.max-online", 1000);
        int reserveSlots = plugin.getConfig().getInt("queue.flow-control.reserve-slots", 5);
        int targetOnline = proxyBridgeService.getTargetPlayerCount();
        int availableSlots = Math.max(0, maxOnline - targetOnline - reserveSlots);
        int releaseAmount = Math.min(playersPerRelease, availableSlots);
        if (releaseAmount <= 0) {
            return;
        }

        String connectingMessage = plugin.getConfig().getString("queue.messages.connecting", "&aConectando ao servidor principal...");

        List<QueueEntry> batch = queueManager.pollNextBatch(releaseAmount);
        for (QueueEntry entry : batch) {
            Player player = Bukkit.getPlayer(entry.uuid());
            if (player == null || !player.isOnline()) {
                continue;
            }

            player.sendMessage(MessageUtil.toLegacyText(connectingMessage));
            proxyBridgeService.connect(player);
        }
    }
}