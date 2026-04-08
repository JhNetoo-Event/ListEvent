package org.crystalocean.listEvent;

import org.crystalocean.listEvent.command.AllowlistCommand;
import org.crystalocean.listEvent.listener.PlayerPreLoginListener;
import org.crystalocean.listEvent.repository.AllowlistRepository;
import org.crystalocean.listEvent.service.AllowlistService;
import org.crystalocean.listEvent.util.AuditLogger;
import org.jetbrains.annotations.NotNull;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class ListEventPlugin extends JavaPlugin {

    private AllowlistRepository repository;
    private AllowlistService allowlistService;
    private AuditLogger auditLogger;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        this.auditLogger = new AuditLogger(this);
        this.repository = new AllowlistRepository(this);
        this.repository.load();

        this.allowlistService = new AllowlistService(this, repository, auditLogger);

        registerCommands();
        registerListeners();

        getLogger().info("ListEvent habilitado com sucesso.");
    }

    @Override
    public void onDisable() {
        if (repository != null) {
            repository.save();
        }
        getLogger().info("ListEvent desabilitado.");
    }

    public void reloadPlugin() {
        reloadConfig();
        repository.load();
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
        getServer().getPluginManager().registerEvents(new PlayerPreLoginListener(this, allowlistService), this);
    }

    public @NotNull AllowlistService getAllowlistService() {
        return allowlistService;
    }

    public @NotNull AuditLogger getAuditLogger() {
        return auditLogger;
    }
}