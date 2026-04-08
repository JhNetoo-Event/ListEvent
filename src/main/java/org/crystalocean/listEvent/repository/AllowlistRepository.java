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

                for (AllowedPlayerRecord record : data.getPlayers()) {
                    if (record != null && record.getUuid() != null) {
                        if (record.getUpdatedAt() == null) {
                            record.setUpdatedAt(record.getAddedAt() == null ? Instant.now() : record.getAddedAt());
                        }
                        records.put(record.getUuid(), record);
                    }
                }
            }
        } catch (Exception e) {
            plugin.getLogger().severe("Falha ao carregar allowlist JSON: " + e.getMessage());
        }
    }

    public synchronized void save() {
        try {
            Files.createDirectories(file.getParent());

            if (plugin.getConfig().getBoolean("storage.backup-on-save", true) && Files.exists(file)) {
                Path backup = file.resolveSibling(file.getFileName() + ".bak");
                Files.copy(file, backup, StandardCopyOption.REPLACE_EXISTING);
            }

            AllowlistData data = new AllowlistData();
            data.setPlayers(new ArrayList<>(records.values()));

        }