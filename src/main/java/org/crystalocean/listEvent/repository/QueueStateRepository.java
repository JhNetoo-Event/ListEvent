package org.crystalocean.listEvent.repository;

import com.google.gson.Gson;
import org.crystalocean.listEvent.ListEventPlugin;
import org.crystalocean.listEvent.model.QueueStateData;
import org.crystalocean.listEvent.util.JsonUtil;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public class QueueStateRepository {

    private final ListEventPlugin plugin;
    private final Gson gson;
    private final Path file;

    public QueueStateRepository(ListEventPlugin plugin) {
        this.plugin = plugin;
        boolean pretty = plugin.getConfig().getBoolean("storage.pretty-print", true);
        this.gson = JsonUtil.createGson(pretty);
        String fileName = plugin.getConfig().getString("queue.storage.file-name", "queue-state.json");
        this.file = plugin.getDataFolder().toPath().resolve(fileName);
    }

    public synchronized QueueStateData load() {
        try {
            Files.createDirectories(file.getParent());
            if (!Files.exists(file)) {
                return new QueueStateData();
            }

            try (Reader reader = Files.newBufferedReader(file)) {
                QueueStateData data = gson.fromJson(reader, QueueStateData.class);
                return data != null ? data : new QueueStateData();
            }
        } catch (Exception exception) {
            plugin.getLogger().severe("Falha ao carregar estado da fila JSON: " + exception.getMessage());
            return new QueueStateData();
        }
    }

    public synchronized void save(QueueStateData data) {
        try {
            Files.createDirectories(file.getParent());

            if (plugin.getConfig().getBoolean("storage.backup-on-save", true) && Files.exists(file)) {
                Path backup = file.resolveSibling(file.getFileName() + ".bak");
                Files.copy(file, backup, StandardCopyOption.REPLACE_EXISTING);
            }

            Path tempFile = file.resolveSibling(file.getFileName() + ".tmp");
            try (Writer writer = Files.newBufferedWriter(tempFile)) {
                gson.toJson(data, writer);
            }

            Files.move(tempFile, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException exception) {
            plugin.getLogger().severe("Falha ao salvar estado da fila JSON: " + exception.getMessage());
        }
    }
}
