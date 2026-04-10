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
    private String prefixCache;

    public AllowlistCommand(ListEventPlugin plugin, AllowlistService allowlistService) {
        this.plugin = plugin;
        this.allowlistService = allowlistService;
        this.prefixCache = plugin.getConfig().getString("messages.prefix", "&8[&bListEvent&8] &7");
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
            case "list" -> handleList(sender, args);
            case "info" -> handleInfo(sender, args);
            case "extend" -> handleExtend(sender, args);
            case "expired" -> handleExpired(sender);
            case "history" -> handleHistory(sender, args);
            case "search" -> handleSearch(sender, args);
            case "active" -> handleFilter(sender, args, true);
            case "inactive" -> handleFilter(sender, args, false);
            case "import" -> handleImport(sender, args);
            case "export" -> handleExport(sender, args);
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
            sender.sendMessage(MessageUtil.toComponent(prefix() + "&cUso: /plist add (jogador/uuid) [tempo] [nota]"));
            return;
        }
        String target = args[1];

        long timeMs = 0;
        String note = "";

        if (args.length > 2) {
            timeMs = MessageUtil.parseTime(args[2]);
            if (timeMs > 0) {
                note = args.length > 3 ? String.join(" ", java.util.Arrays.copyOfRange(args, 3, args.length)) : "";
            } else {
                note = String.join(" ", java.util.Arrays.copyOfRange(args, 2, args.length));
            }
        }

        java.time.Instant expiresAt = timeMs > 0 ? java.time.Instant.now().plusMillis(timeMs) : null;

        AllowlistService.AddResult result = allowlistService.addPlayer(target, note, expiresAt, sender);
        if (result.success()) {
            sender.sendMessage(MessageUtil.toComponent(prefix() + "&aJogador &f" + result.name() + " &a(" + result.uuid() + ") adicionado."));
        } else if (result.alreadyAdded()) {
            sender.sendMessage(MessageUtil.toComponent(prefix() + "&eJogador &f" + result.name() + " &eja esta na allowlist."));
        } else {
            sender.sendMessage(MessageUtil.toComponent(prefix() + "&cJogador &f" + target + " &cnao encontrado."));
        }
    }

    private void handleExtend(CommandSender sender, String[] args) {
        if (!sender.hasPermission("listevent.admin")) {
            sender.sendMessage(MessageUtil.toComponent(prefix() + "&cSem permissao."));
            return;
        }
        if (args.length < 3) {
            sender.sendMessage(MessageUtil.toComponent(prefix() + "&cUso: /plist extend (jogador/uuid) (tempo)"));
            return;
        }
        String target = args[1];
        long timeMs = MessageUtil.parseTime(args[2]);
        if (timeMs == 0) {
            sender.sendMessage(MessageUtil.toComponent(prefix() + "&cFormato de tempo invalido (ex: 7d, 24h, 30m)."));
            return;
        }

        AllowlistService.RemoveResult result = allowlistService.extendPlayer(target, timeMs, sender);
        if (result.success()) {
            sender.sendMessage(MessageUtil.toComponent(prefix() + "&aTempo de &f" + result.name() + " &aestendido."));
        } else {
            sender.sendMessage(MessageUtil.toComponent(prefix() + "&cJogador &f" + target + " &cnao encontrado ou ja esta inativo."));
        }
    }

    private void handleRemove(CommandSender sender, String[] args) {
        if (!sender.hasPermission("listevent.admin")) {
            sender.sendMessage(MessageUtil.toComponent(prefix() + "&cSem permissao."));
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(MessageUtil.toComponent(prefix() + "&cUso: /plist remove (jogador/uuid)"));
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

    private void handleList(CommandSender sender, String[] args) {
        Collection<AllowedPlayerRecord> records = allowlistService.listAll();
        if (records.isEmpty()) {
            sender.sendMessage(MessageUtil.toComponent(prefix() + "&eA allowlist esta vazia."));
            return;
        }

        int page = 1;
        if (args.length > 1) {
            try {
                page = Integer.parseInt(args[1]);
            } catch (NumberFormatException ignored) {}
        }

        List<AllowedPlayerRecord> activeRecords = records.stream().filter(AllowedPlayerRecord::isActive).collect(Collectors.toList());
        int totalPages = (int) Math.ceil(activeRecords.size() / 10.0);
        page = Math.max(1, Math.min(page, totalPages));

        sender.sendMessage(MessageUtil.toComponent(prefix() + "&aJogadores ativos (&f" + page + "/" + totalPages + "&a):"));

        int start = (page - 1) * 10;
        int end = Math.min(start + 10, activeRecords.size());

        for (int i = start; i < end; i++) {
            AllowedPlayerRecord record = activeRecords.get(i);
            sender.sendMessage(MessageUtil.toComponent(" &8- &f" + record.getLastKnownName() + " &7(" + record.getUuid() + ")"));
        }
    }

    private void handleExpired(CommandSender sender) {
        Collection<AllowedPlayerRecord> records = allowlistService.listAll();
        List<AllowedPlayerRecord> expired = records.stream()
                .filter(r -> !r.isActive() && "Expirado".equals(r.getReason()))
                .toList();

        if (expired.isEmpty()) {
            sender.sendMessage(MessageUtil.toComponent(prefix() + "&eNenhum jogador expirado."));
            return;
        }

        sender.sendMessage(MessageUtil.toComponent(prefix() + "&aJogadores expirados:"));
        for (AllowedPlayerRecord record : expired) {
            sender.sendMessage(MessageUtil.toComponent(" &8- &f" + record.getLastKnownName() + " &7(" + record.getUuid() + ")"));
        }
    }

    private void handleSearch(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(MessageUtil.toComponent(prefix() + "&cUso: /plist search (texto)"));
            return;
        }
        String text = args[1].toLowerCase(Locale.ROOT);
        List<AllowedPlayerRecord> results = allowlistService.listAll().stream()
                .filter(r -> (r.getLastKnownName() != null && r.getLastKnownName().toLowerCase().contains(text)) || r.getUuid().toString().contains(text))
                .toList();

        if (results.isEmpty()) {
            sender.sendMessage(MessageUtil.toComponent(prefix() + "&eNenhum jogador encontrado com '&f" + text + "&e'."));
            return;
        }

        sender.sendMessage(MessageUtil.toComponent(prefix() + "&aResultados da busca:"));
        for (AllowedPlayerRecord record : results) {
            String status = record.isActive() ? "&a[Ativo]" : "&c[Inativo]";
            sender.sendMessage(MessageUtil.toComponent(" &8- " + status + " &f" + record.getLastKnownName() + " &7(" + record.getUuid() + ")"));
        }
    }

    private void handleFilter(CommandSender sender, String[] args, boolean active) {
        int page = 1;
        if (args.length > 1) {
            try {
                page = Integer.parseInt(args[1]);
            } catch (NumberFormatException ignored) {}
        }

        List<AllowedPlayerRecord> filtered = allowlistService.listAll().stream()
                .filter(r -> r.isActive() == active)
                .toList();

        if (filtered.isEmpty()) {
            sender.sendMessage(MessageUtil.toComponent(prefix() + "&eNenhum jogador encontrado."));
            return;
        }

        int totalPages = (int) Math.ceil(filtered.size() / 10.0);
        page = Math.max(1, Math.min(page, totalPages));

        sender.sendMessage(MessageUtil.toComponent(prefix() + "&aJogadores " + (active ? "ativos" : "inativos") + " (&f" + page + "/" + totalPages + "&a):"));

        int start = (page - 1) * 10;
        int end = Math.min(start + 10, filtered.size());

        for (int i = start; i < end; i++) {
            AllowedPlayerRecord record = filtered.get(i);
            sender.sendMessage(MessageUtil.toComponent(" &8- &f" + record.getLastKnownName() + " &7(" + record.getUuid() + ")"));
        }
    }

    private void handleImport(CommandSender sender, String[] args) {
        if (!sender.hasPermission("listevent.admin")) {
            sender.sendMessage(MessageUtil.toComponent(prefix() + "&cSem permissao."));
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(MessageUtil.toComponent(prefix() + "&cUso: /plist import (arquivo.txt)"));
            return;
        }

        String filename = args[1];
        java.io.File file = new java.io.File(plugin.getDataFolder(), filename);
        if (!file.exists()) {
            sender.sendMessage(MessageUtil.toComponent(prefix() + "&cArquivo &f" + filename + " &cnao encontrado na pasta do plugin."));
            return;
        }

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                java.util.List<String> lines = java.nio.file.Files.readAllLines(file.toPath());
                int count = 0;
                for (String line : lines) {
                    if (line.isBlank() || line.startsWith("#")) continue;
                    String[] parts = line.split(",");
                    String target = parts[0].trim();
                    if (!target.isEmpty()) {
                        AllowlistService.AddResult res = allowlistService.addPlayer(target, "Importado", null, sender);
                        if (res.success()) count++;
                    }
                }
                final int finalCount = count;
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    sender.sendMessage(MessageUtil.toComponent(prefix() + "&aImportacao concluida. &f" + finalCount + " &ajogadores adicionados."));
                });
            } catch (Exception e) {
                plugin.getLogger().warning("Erro ao importar: " + e.getMessage());
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    sender.sendMessage(MessageUtil.toComponent(prefix() + "&cErro ao ler o arquivo de importacao. Verifique o console."));
                });
            }
        });
    }

    private void handleExport(CommandSender sender, String[] args) {
        if (!sender.hasPermission("listevent.admin")) {
            sender.sendMessage(MessageUtil.toComponent(prefix() + "&cSem permissao."));
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(MessageUtil.toComponent(prefix() + "&cUso: /plist export (arquivo.txt)"));
            return;
        }

        String filename = args[1];
        java.io.File file = new java.io.File(plugin.getDataFolder(), filename);

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try (java.io.PrintWriter pw = new java.io.PrintWriter(new java.io.FileWriter(file))) {
                pw.println("# Arquivo exportado pelo ListEvent");
                for (AllowedPlayerRecord record : allowlistService.listAll()) {
                    if (record.isActive()) {
                        String name = record.getLastKnownName() != null ? record.getLastKnownName() : record.getUuid().toString();
                        pw.println(name + "," + record.getUuid().toString());
                    }
                }
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    sender.sendMessage(MessageUtil.toComponent(prefix() + "&aExportacao concluida para &f" + filename + "&a."));
                });
            } catch (Exception e) {
                plugin.getLogger().warning("Erro ao exportar: " + e.getMessage());
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    sender.sendMessage(MessageUtil.toComponent(prefix() + "&cErro ao salvar o arquivo de exportacao. Verifique o console."));
                });
            }
        });
    }

    private void handleHistory(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(MessageUtil.toComponent(prefix() + "&cUso: /plist history (jogador/uuid)"));
            return;
        }
        String target = args[1];
        var optRecord = allowlistService.getInfo(target);
        if (optRecord.isPresent()) {
            AllowedPlayerRecord record = optRecord.get();
            sender.sendMessage(MessageUtil.toComponent(prefix() + "&aHistorico de &f" + record.getLastKnownName() + "&a:"));
            sender.sendMessage(MessageUtil.toComponent(" &8- &7Adicionado em: &f" + FORMATTER.format(record.getAddedAt()) + " por " + record.getAddedBy()));
            if (record.getLastJoinAt() != null) sender.sendMessage(MessageUtil.toComponent(" &8- &7Ultimo join: &f" + FORMATTER.format(record.getLastJoinAt())));
            if (record.getExpiresAt() != null) sender.sendMessage(MessageUtil.toComponent(" &8- &7Expiracao: &f" + FORMATTER.format(record.getExpiresAt())));
            if (!record.isActive()) {
                sender.sendMessage(MessageUtil.toComponent(" &8- &7Removido em: &f" + (record.getRemovedAt() != null ? FORMATTER.format(record.getRemovedAt()) : "N/A")));
                sender.sendMessage(MessageUtil.toComponent(" &8- &7Removido por: &f" + (record.getRemovedBy() != null ? record.getRemovedBy() : "N/A")));
                sender.sendMessage(MessageUtil.toComponent(" &8- &7Motivo: &f" + (record.getReason() != null ? record.getReason() : "N/A")));
            }
        } else {
            sender.sendMessage(MessageUtil.toComponent(prefix() + "&cJogador &f" + target + " &cnao encontrado no historico."));
        }
    }

    private void handleInfo(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(MessageUtil.toComponent(prefix() + "&cUso: /plist info (jogador/uuid)"));
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
            if (record.getExpiresAt() != null) {
                sender.sendMessage(MessageUtil.toComponent(" &8- &7Expiracao: &f" + FORMATTER.format(record.getExpiresAt())));
            }
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
        sender.sendMessage(MessageUtil.toComponent(" &8- &f/" + label + " add (jogador/uuid) [tempo] [nota]"));
        sender.sendMessage(MessageUtil.toComponent(" &8- &f/" + label + " remove (jogador/uuid)"));
        sender.sendMessage(MessageUtil.toComponent(" &8- &f/" + label + " extend (jogador/uuid) (tempo)"));
        sender.sendMessage(MessageUtil.toComponent(" &8- &f/" + label + " list [pagina]"));
        sender.sendMessage(MessageUtil.toComponent(" &8- &f/" + label + " expired"));
        sender.sendMessage(MessageUtil.toComponent(" &8- &f/" + label + " info (jogador/uuid)"));
        sender.sendMessage(MessageUtil.toComponent(" &8- &f/" + label + " history (jogador/uuid)"));
        sender.sendMessage(MessageUtil.toComponent(" &8- &f/" + label + " search (texto)"));
        sender.sendMessage(MessageUtil.toComponent(" &8- &f/" + label + " active|inactive [pagina]"));
        sender.sendMessage(MessageUtil.toComponent(" &8- &f/" + label + " import|export (arquivo)"));
        sender.sendMessage(MessageUtil.toComponent(" &8- &f/" + label + " reload"));
        sender.sendMessage(MessageUtil.toComponent(" &8- &f/" + label + " on|off"));
    }

    private String prefix() {
        return prefixCache;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            java.util.Set<String> subs = new java.util.HashSet<>();
            if (sender.hasPermission("listevent.admin")) {
                subs.addAll(List.of("add", "remove", "del", "extend", "expired", "history", "search", "active", "inactive", "import", "export", "reload", "on", "off"));
            }
            if (sender.hasPermission("listevent.view") || sender.hasPermission("listevent.admin")) {
                subs.addAll(List.of("list", "info", "search", "active", "inactive"));
            }
            return subs.stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .sorted()
                    .collect(Collectors.toList());
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("remove") || args[0].equalsIgnoreCase("del") || args[0].equalsIgnoreCase("info") || args[0].equalsIgnoreCase("extend") || args[0].equalsIgnoreCase("history"))) {
            return allowlistService.listAll().stream()
                    .map(AllowedPlayerRecord::getLastKnownName)
                    .filter(name -> name != null && name.toLowerCase().startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("import")) {
            java.io.File folder = plugin.getDataFolder();
            if (folder.exists() && folder.isDirectory()) {
                String prefix = args[1].toLowerCase();
                java.io.File[] files = folder.listFiles((dir, name) -> name.toLowerCase().startsWith(prefix) && (name.endsWith(".txt") || name.endsWith(".csv")));
                if (files != null) {
                    return java.util.Arrays.stream(files)
                            .map(java.io.File::getName)
                            .collect(Collectors.toList());
                }
            }
        }
        return List.of();
    }
}