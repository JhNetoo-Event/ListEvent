package org.crystalocean.listEvent.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.crystalocean.listEvent.ListEventPlugin;
import org.crystalocean.listEvent.model.AllowedPlayerRecord;
import org.crystalocean.listEvent.service.AllowlistService;
import org.crystalocean.listEvent.util.MessageUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public class AllowlistCommand implements CommandExecutor, TabCompleter {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter
            .ofPattern("dd/MM/yyyy HH:mm:ss")
            .withLocale(new Locale("pt", "BR"))
            .withZone(ZoneId.systemDefault());

    private final ListEventPlugin plugin;
    private final AllowlistService allowlistService;

    public AllowlistCommand(ListEventPlugin plugin, AllowlistService allowlistService) {
        this.plugin = plugin;
        this.allowlistService = allowlistService;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("listevent.admin") && !sender.hasPermission("listevent.view")) {
            sender.sendMessage(prefix() + "&cVoce nao tem permissao para usar este comando.");
            return true;
        }

        if (args.length == 0) {
            sendHelp(sender, label);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "add" -> handleAdd(sender, args);
            case "remove", "del" -> handleRemove(sender, args);
            case "list" -> handleList(sender);
            case "info" -> handleInfo(sender, args);
            case "reload" -> handleReload(sender);
            case "on" -> handleToggle(sender, true);
            case "off" -> handleToggle(sender, false);
            default -> sendHelp(sender, label);
        }

        return true;
    }

    private void handleAdd(CommandSender sender, String[] args) {
        if (!sender.hasPermission("listevent.admin")) {
            sender.sendMessage(prefix() + "&cSem permissao.");
            return;
        }