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
            sender.sendMessage(MessageUtil.toComponent(prefix() + "&cVoce nao tem permissao para usar este comando."));
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
            sender.sendMessage(MessageUtil.toComponent(prefix() + "&cSem permissao."));
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(MessageUtil.toComponent(prefix() + "&cUso: /plist add <jogador/uuid> [nota]"));
            return;
        }
        String target = args[1];
        String note = args.length > 2 ? String.join(" ", java.util.Arrays.copyOfRange(args, 2, args.length)) : "";

        AllowlistService.AddResult result = allowlistService.addPlayer(target, note, sender);
        if (result.success()) {
            sender.sendMessage(MessageUtil.toComponent(prefix() + "&aJogador &f" + result.name() + " &a(" + result.uuid() + ") adicionado."));
        } else if (result.alreadyAdded()) {
            sender.sendMessage(MessageUtil.toComponent(prefix() + "&eJogador &f" + result.name() + " &eja esta na allowlist."));
        } else {
            sender.sendMessage(MessageUtil.toComponent(prefix() + "&cJogador &f" + target + " &cnao encontrado."));
        }
    }

    private void handleRemove(CommandSender sender, String[] args) {
        if (!sender.hasPermission("listevent.admin")) {
            sender.sendMessage(MessageUtil.toComponent(prefix() + "&cSem permissao."));
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(MessageUtil.toComponent(prefix() + "&cUso: /plist remove <jogador/uuid>"));
            return;
        }
        String target = args[1];
        AllowlistService.RemoveResult result = allowlistService.removePlayer(target, sender);
        if (result.success()) {
            sender.sendMessage(MessageUtil.toComponent(prefix() + "&aJogador &f" + result.name() + " &aremovido."));
        } else {
            sender.sendMessage(MessageUtil.toComponent(prefix() + "&cJogador &f" + target + " &cnao encontrado ou ja esta inativo."));
        }
    }

    private void handleList(CommandSender sender) {
        Collection<AllowedPlayerRecord> records = allowlistService.listAll();
        if (records.isEmpty()) {
            sender.sendMessage(MessageUtil.toComponent(prefix() + "&eA allowlist esta vazia."));
            return;
        }
        sender.sendMessage(MessageUtil.toComponent(prefix() + "&aJogadores na allowlist:"));
        for (AllowedPlayerRecord record : records) {
            if (record.isActive()) {
                sender.sendMessage(MessageUtil.toComponent(" &8- &f" + record.getLastKnownName() + " &7(" + record.getUuid() + ")"));
            }
        }
    }

    private void handleInfo(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(MessageUtil.toComponent(prefix() + "&cUso: /plist info <jogador/uuid>"));
            return;
        }
        String target = args[1];
        var optRecord = allowlistService.getInfo(target);
        if (optRecord.isPresent()) {
            AllowedPlayerRecord record = optRecord.get();
            sender.sendMessage(MessageUtil.toComponent(prefix() + "&aInformacoes de &f" + record.getLastKnownName() + "&a:"));
            sender.sendMessage(MessageUtil.toComponent(" &8- &7UUID: &f" + record.getUuid()));
            sender.sendMessage(MessageUtil.toComponent(" &8- &7Adicionado por: &f" + record.getAddedBy()));
            sender.sendMessage(MessageUtil.toComponent(" &8- &7Nota: &f" + record.getNote()));
            sender.sendMessage(MessageUtil.toComponent(" &8- &7Adicionado em: &f" + FORMATTER.format(record.getAddedAt())));
            sender.sendMessage(MessageUtil.toComponent(" &8- &7Atualizado em: &f" + FORMATTER.format(record.getUpdatedAt())));
            sender.sendMessage(MessageUtil.toComponent(" &8- &7Ativo: &f" + (record.isActive() ? "&aSim" : "&cNao")));
        } else {
            sender.sendMessage(MessageUtil.toComponent(prefix() + "&cJogador &f" + target + " &cnao encontrado."));
        }
    }

    private void handleReload(CommandSender sender) {
        if (!sender.hasPermission("listevent.admin")) {
            sender.sendMessage(MessageUtil.toComponent(prefix() + "&cSem permissao."));
            return;
        }
        plugin.reloadPlugin();
        sender.sendMessage(MessageUtil.toComponent(prefix() + "&aConfiguracao e allowlist recarregados."));
    }

    private void handleToggle(CommandSender sender, boolean enable) {
        if (!sender.hasPermission("listevent.admin")) {
            sender.sendMessage(MessageUtil.toComponent(prefix() + "&cSem permissao."));
            return;
        }
        if (allowlistService.isPluginEnabled() == enable) {
            sender.sendMessage(MessageUtil.toComponent(prefix() + "&eA allowlist ja esta " + (enable ? "ativada" : "desativada") + "."));
            return;
        }
        allowlistService.setPluginEnabled(enable);
        sender.sendMessage(MessageUtil.toComponent(prefix() + "&aA allowlist foi " + (enable ? "ativada" : "desativada") + "."));
    }

    private void sendHelp(CommandSender sender, String label) {
        sender.sendMessage(MessageUtil.toComponent(prefix() + "&aComandos disponiveis:"));
        sender.sendMessage(MessageUtil.toComponent(" &8- &f/" + label + " add <jogador/uuid> [nota]"));
        sender.sendMessage(MessageUtil.toComponent(" &8- &f/" + label + " remove <jogador/uuid>"));
        sender.sendMessage(MessageUtil.toComponent(" &8- &f/" + label + " list"));
        sender.sendMessage(MessageUtil.toComponent(" &8- &f/" + label + " info <jogador/uuid>"));
        sender.sendMessage(MessageUtil.toComponent(" &8- &f/" + label + " reload"));
        sender.sendMessage(MessageUtil.toComponent(" &8- &f/" + label + " on|off"));
    }

    private String prefix() {
        return plugin.getConfig().getString("messages.prefix", "&8[&bListEvent&8] &7");
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            List<String> subs = new ArrayList<>();
            if (sender.hasPermission("listevent.admin")) {
                subs.addAll(List.of("add", "remove", "del", "reload", "on", "off"));
            }
            if (sender.hasPermission("listevent.view") || sender.hasPermission("listevent.admin")) {
                subs.addAll(List.of("list", "info"));
            }
            return subs.stream().filter(s -> s.startsWith(args[0].toLowerCase())).collect(Collectors.toList());
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("remove") || args[0].equalsIgnoreCase("del") || args[0].equalsIgnoreCase("info"))) {
            return allowlistService.listAll().stream()
                    .filter(AllowedPlayerRecord::isActive)
                    .map(AllowedPlayerRecord::getLastKnownName)
                    .filter(name -> name != null && name.toLowerCase().startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
        }
        return List.of();
    }
}