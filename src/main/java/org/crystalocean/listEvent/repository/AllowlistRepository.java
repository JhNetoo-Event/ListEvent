package org.crystalocean.listEvent.repository;

import com.google.gson.Gson;
import org.crystalocean.listEvent.ListEventPlugin;
import org.crystalocean.listEvent.model.AllowlistData;
import org.crystalocean.listEvent.model.AllowedPlayerRecord;
import org.crystalocean.listEvent.util.JsonUtil;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class AllowlistRepository {

    private final ListEventPlugin plugin;
    private final Gson gson;
    private final Path file;
    private final Map<UUID, AllowedPlayerRecord> records = new ConcurrentHashMap<>();
    private volatile boolean dirty = false;

    public AllowlistRepository(ListEventPlugin plugin) {
        this.plugin = plugin;
        boolean pretty = plugin.getConfig().getBoolean("storage.pretty-print", true);
        this.gson = JsonUtil.createGson(pretty);
        String fileName = plugin.getConfig().getString("storage.file-name", "allowed-players.json");
        this.file = plugin.getDataFolder().toPath().resolve(fileName);
    }

    public synchronized void load() {
        records.clear();

        try {
            Files.createDirectories(file.getParent());
            if (!Files.exists(file)) {
                save();
                return;
            }

            try (Reader reader = Files.newBufferedReader(file)) {
                AllowlistData data = gson.fromJson(reader, AllowlistData.class);
                if (data == null || data.getPlayers() == null) {
                    return;
                }

                boolean manualEditDetected = false;
                for (AllowedPlayerRecord record : data.getPlayers()) {
                    if (record != null && record.getUuid() != null) {
                        if (record.getUpdatedAt() == null) {
                            record.setUpdatedAt(record.getAddedAt() == null ? Instant.now() : record.getAddedAt());
                        }
                        records.put(record.getUuid(), record);
                    } else {
                        manualEditDetected = true;
                    }
                }

                if (manualEditDetected) {
                    plugin.getLogger().warning("AVISO: Edicao manual incorreta detectada no arquivo allowed-players.json.");
                    plugin.getLogger().warning("Por favor, use os comandos /plist add ou /plist import. Registros mal formatados foram ignorados.");
                }
            }
        } catch (com.google.gson.JsonSyntaxException e) {
            plugin.getLogger().severe("=====================================================");
            plugin.getLogger().severe("ERRO FATAL: O arquivo allowed-players.json esta corrompido!");
            plugin.getLogger().severe("Isso ocorre por edicao manual incorreta (ex: falta de aspas, chaves).");
            plugin.getLogger().severe("O plugin NAO vai carregar essa lista quebrada. Use comandos.");
            plugin.getLogger().severe("=====================================================");

            try {
                Path broken = file.resolveSibling(file.getFileName() + ".broken");
                Files.move(file, broken, StandardCopyOption.REPLACE_EXISTING);
                plugin.getLogger().warning("Arquivo corrompido movido para " + broken.getFileName() + ". Uma nova lista vazia sera criada.");
                save();
            } catch (IOException ioException) {
                plugin.getLogger().severe("Falha ao mover arquivo corrompido: " + ioException.getMessage());
            }
        } catch (Exception e) {
            plugin.getLogger().severe("Falha ao carregar allowlist JSON: " + e.getMessage());
        }
    }

    public synchronized void save() {
        if (!dirty && Files.exists(file)) {
            return;
        }

        try {
            Files.createDirectories(file.getParent());

            if (plugin.getConfig().getBoolean("storage.backup-on-save", true) && Files.exists(file)) {
                Path backup = file.resolveSibling(file.getFileName() + ".bak");
                Files.copy(file, backup, StandardCopyOption.REPLACE_EXISTING);
            }

            AllowlistData data = new AllowlistData();
            data.setPlayers(new ArrayList<>(records.values()));

            Path tempFile = file.resolveSibling(file.getFileName() + ".tmp");
            try (Writer writer = Files.newBufferedWriter(tempFile)) {
                gson.toJson(data, writer);
            }

            Files.move(tempFile, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            dirty = false;
        } catch (IOException e) {
            plugin.getLogger().severe("Falha ao salvar allowlist JSON: " + e.getMessage());
        }
    }

    public boolean isAllowed(UUID uuid) {
        AllowedPlayerRecord record = records.get(uuid);
        if (record != null && record.isActive()) {
            if (record.getExpiresAt() != null && Instant.now().isAfter(record.getExpiresAt())) {
                record.setActive(false);
                record.setReason("Expirado");
                record.setRemovedAt(Instant.now());
                record.setRemovedBy("Sistema");
                markDirty();
                return false;
            }
            return true;
        }
        return false;
    }

    public Optional<AllowedPlayerRecord> findByUuid(UUID uuid) {
        return Optional.ofNullable(records.get(uuid));
    }

    public Optional<AllowedPlayerRecord> findByName(String name) {
        return records.values().stream()
                .filter(record -> record.getLastKnownName() != null && record.getLastKnownName().equalsIgnoreCase(name))
                .findFirst();
    }

    public void updateLastKnownName(UUID uuid, String name) {
        AllowedPlayerRecord record = records.get(uuid);
        if (record != null && (record.getLastKnownName() == null || !record.getLastKnownName().equals(name))) {
            record.setLastKnownName(name);
            record.setUpdatedAt(Instant.now());
            markDirty();
        }
    }

    public void updateLastJoin(UUID uuid) {
        AllowedPlayerRecord record = records.get(uuid);
        if (record != null) {
            record.setLastJoinAt(Instant.now());
            markDirty();
        }
    }

    public Collection<AllowedPlayerRecord> getAllSorted() {
        return records.values().stream()
                .sorted(Comparator.comparing(AllowedPlayerRecord::getAddedAt))
                .toList();
    }

    public void saveRecord(AllowedPlayerRecord record) {
        records.put(record.getUuid(), record);
        markDirty();
    }

    public void deleteRecord(UUID uuid) {
        records.remove(uuid);
        markDirty();
    }

    public void markDirty() {
        this.dirty = true;
    }
}