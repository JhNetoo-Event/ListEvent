package org.crystalocean.listEvent;

import org.crystalocean.listEvent.command.AllowlistCommand;
import org.crystalocean.listEvent.listener.PlayerPreLoginListener;
import org.crystalocean.listEvent.listener.PlayerQueueListener;
import org.crystalocean.listEvent.repository.AllowlistRepository;
import org.crystalocean.listEvent.service.AllowlistService;
import org.crystalocean.listEvent.service.ProxyBridgeService;
import org.crystalocean.listEvent.service.QueueManager;
import org.crystalocean.listEvent.service.QueueTask;
import org.crystalocean.listEvent.util.AuditLogger;
import org.jetbrains.annotations.NotNull;
import org.bukkit.command.PluginCommand;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.plugin.java.JavaPlugin;

public final class ListEventPlugin extends JavaPlugin {

    private AllowlistRepository repository;
    private AllowlistService allowlistService;
    private AuditLogger auditLogger;
    private QueueManager queueManager;
    private ProxyBridgeService proxyBridgeService;
    private BukkitTask queueReleaseTask;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        this.auditLogger = new AuditLogger(this);
        this.repository = new AllowlistRepository(this);
        this.repository.load();

        this.allowlistService = new AllowlistService(this, repository, auditLogger);
        this.queueManager = new QueueManager(this);
        this.proxyBridgeService = new ProxyBridgeService(this);

        registerCommands();
        registerListeners();
        registerQueueTask();
        registerPluginChannels();
        getLogger().info("Fila configurada para target '" + getConfig().getString("queue.target-server", "principal")
                + "' via canal '" + getConfig().getString("queue.proxy.messaging-channel", "BungeeCord") + "'.");

        getServer().getScheduler().runTaskTimerAsynchronously(this, () -> {
            if (repository != null) {
                repository.save();
            }
            if (queueManager != null) {
                queueManager.saveStateIfDirty();
            }
        }, 20L * 60L, 20L * 60L); // flush every 60 seconds

        getLogger().info("ListEvent habilitado com sucesso.");
    }

    @Override
    public void onDisable() {
        if (repository != null) {
            repository.save();
        }
        if (queueManager != null) {
            queueManager.saveStateIfDirty();
        }

        if (queueReleaseTask != null) {
            queueReleaseTask.cancel();
        }
        unregisterPluginChannels();
        getLogger().info("ListEvent desabilitado.");
    }

    public void reloadPlugin() {
        reloadConfig();
        if (repository != null) {
            repository.save();
        }
        if (queueManager != null) {
            queueManager.saveStateIfDirty();
        }
        this.repository = new AllowlistRepository(this);
        this.repository.load();
        this.allowlistService = new AllowlistService(this, repository, auditLogger);

        PluginCommand command = getCommand("plist");
        if (command != null) {
            AllowlistCommand executor = new AllowlistCommand(this, allowlistService);
            command.setExecutor(executor);
            command.setTabCompleter(executor);
        }
        getLogger().info("Plugin recarregado com sucesso.");

        if (queueReleaseTask != null) {
            queueReleaseTask.cancel();
        }
        unregisterPluginChannels();
        registerPluginChannels();
        registerQueueTask();
    }

    private void registerCommands() {
        PluginCommand command = getCommand("plist");
        if (command == null) {
            throw new IllegalStateException("Comando /plist nao encontrado no plugin.yml");
        }

        AllowlistCommand executor = new AllowlistCommand(this, allowlistService);
        command.setExecutor(executor);
        command.setTabCompleter(executor);
    }

    private void registerListeners() {
        getServer().getPluginManager().registerEvents(new PlayerPreLoginListener(this), this);
        getServer().getPluginManager().registerEvents(new PlayerQueueListener(this, queueManager, proxyBridgeService), this);
    }

    private void registerQueueTask() {
        long intervalSeconds = getConfig().getLong("queue.release-rate.interval-seconds", 10L);
        long ticks = Math.max(20L, intervalSeconds * 20L);
        queueReleaseTask = getServer().getScheduler().runTaskTimer(this, new QueueTask(this, queueManager, proxyBridgeService), ticks, ticks);
    }

    private void registerPluginChannels() {
        String channel = getConfig().getString("queue.proxy.messaging-channel", "BungeeCord");
        getServer().getMessenger().registerOutgoingPluginChannel(this, channel);
        getServer().getMessenger().registerIncomingPluginChannel(this, channel, proxyBridgeService);
    }

    private void unregisterPluginChannels() {
        String channel = getConfig().getString("queue.proxy.messaging-channel", "BungeeCord");
        getServer().getMessenger().unregisterIncomingPluginChannel(this, channel);
        getServer().getMessenger().unregisterOutgoingPluginChannel(this, channel);
    }

    public @NotNull AllowlistService getAllowlistService() {
        return allowlistService;
    }

    public @NotNull AuditLogger getAuditLogger() {
        return auditLogger;
    }
}
