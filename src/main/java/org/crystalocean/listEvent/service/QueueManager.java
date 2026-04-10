package org.crystalocean.listEvent.service;

import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.crystalocean.listEvent.ListEventPlugin;
import org.crystalocean.listEvent.model.QueueEntry;
import org.crystalocean.listEvent.model.QueueStateData;
import org.crystalocean.listEvent.repository.QueueStateRepository;
import org.crystalocean.listEvent.util.MessageUtil;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.atomic.AtomicLong;

public class QueueManager {

    private final ListEventPlugin plugin;
    private final PriorityBlockingQueue<QueueEntry> queue = new PriorityBlockingQueue<>();
    private final Map<UUID, QueueEntry> activeEntries = new ConcurrentHashMap<>();
    private final Map<UUID, GraceEntry> graceEntries = new ConcurrentHashMap<>();
    private final AtomicLong sequenceGenerator = new AtomicLong(0);
    private final QueueStateRepository queueStateRepository;
    private volatile boolean dirty = false;

    private volatile boolean queueOpen = true;

    public QueueManager(ListEventPlugin plugin) {
        this.plugin = plugin;
        this.queueStateRepository = new QueueStateRepository(plugin);
        loadState();
        refreshAutoOpenState();
    }

    public boolean isEnabled() {
        return plugin.getConfig().getBoolean("queue.enabled", false);
    }

    public boolean isQueueOpen() {
        return queueOpen;
    }

    public boolean isQueueModeActive(int targetOnlinePlayers) {
        if (!isEnabled()) {
            return false;
        }

        int activateAt = plugin.getConfig().getInt("queue.flow-control.activate-queue-at-online", 0);
        return targetOnlinePlayers >= activateAt;
    }

    public void refreshAutoOpenState() {
        if (!isEnabled()) {
            queueOpen = false;
            return;
        }

        String openAtRaw = plugin.getConfig().getString("queue.auto-open.datetime", "");
        String zoneRaw = plugin.getConfig().getString("queue.auto-open.zone", "UTC");

        if (openAtRaw == null || openAtRaw.isBlank()) {
            queueOpen = true;
            return;
        }

        try {
            LocalDateTime openAt = LocalDateTime.parse(openAtRaw);
            ZoneId zoneId = ZoneId.of(zoneRaw);
            queueOpen = !Instant.now().isBefore(openAt.atZone(zoneId).toInstant());
        } catch (DateTimeParseException | IllegalArgumentException ex) {
            plugin.getLogger().warning("queue.auto-open inválido. Abrindo fila imediatamente.");
            queueOpen = true;
        }
    }

    public void enqueue(Player player) {
        if (!isEnabled()) {
            return;
        }

        UUID uuid = player.getUniqueId();
        if (activeEntries.containsKey(uuid)) {
            return;
        }

        long now = System.currentTimeMillis();
        GraceEntry graceEntry = graceEntries.remove(uuid);
        QueueEntry entry;

        if (graceEntry != null && graceEntry.expiresAtMillis() >= now) {
            entry = new QueueEntry(uuid, graceEntry.enqueueTimestamp(), graceEntry.priorityWeight(), graceEntry.sequence());
        } else {
            int priorityWeight = resolvePriorityWeight(player);
            entry = new QueueEntry(uuid, now, priorityWeight, sequenceGenerator.incrementAndGet());
        }

        activeEntries.put(uuid, entry);
        queue.offer(entry);
        markDirty();
    }

    public void handleQuit(Player player) {
        if (!isEnabled()) {
            return;
        }

        QueueEntry removed = remove(player.getUniqueId());
        if (removed == null) {
            return;
        }

        long graceMs = plugin.getConfig().getLong("queue.reconnect-grace-seconds", 180L) * 1000L;
        graceEntries.put(
                player.getUniqueId(),
                new GraceEntry(removed.enqueueTimestamp(), removed.priorityWeight(), removed.sequence(), System.currentTimeMillis() + graceMs)
        );
        markDirty();
    }

    public QueueEntry remove(UUID uuid) {
        QueueEntry entry = activeEntries.remove(uuid);
        if (entry != null) {
            queue.remove(entry);
            markDirty();
        }
        return entry;
    }

    public List<QueueEntry> pollNextBatch(int amount) {
        List<QueueEntry> polled = new ArrayList<>();
        for (int i = 0; i < amount; i++) {
            QueueEntry entry = queue.poll();
            if (entry == null) {
                break;
            }

            if (activeEntries.remove(entry.uuid()) == null) {
                continue;
            }

            graceEntries.remove(entry.uuid());
            polled.add(entry);
        }
        if (!polled.isEmpty()) {
            markDirty();
        }
        return polled;
    }

    public int getPosition(UUID uuid) {
        List<QueueEntry> ordered = getOrderedEntries();
        for (int i = 0; i < ordered.size(); i++) {
            if (ordered.get(i).uuid().equals(uuid)) {
                return i + 1;
            }
        }
        return -1;
    }

    public List<QueueEntry> getOrderedEntries() {
        List<QueueEntry> entries = new ArrayList<>(activeEntries.values());
        entries.sort(Comparator.naturalOrder());
        return entries;
    }

    public void cleanupGraceEntries() {
        long now = System.currentTimeMillis();
        boolean removed = graceEntries.entrySet().removeIf(entry -> entry.getValue().expiresAtMillis() < now);
        if (removed) {
            markDirty();
        }
    }

    public void sendPositionUpdates() {
        if (!isEnabled()) {
            return;
        }

        String template = plugin.getConfig().getString("queue.messages.position-actionbar", "&eSua posição na fila: &b#%pos%");
        for (QueueEntry entry : getOrderedEntries()) {
            Player player = Bukkit.getPlayer(entry.uuid());
            if (player == null || !player.isOnline()) {
                continue;
            }

            int position = getPosition(entry.uuid());
            String message = template
                    .replace("%pos%", String.valueOf(position))
                    .replace("%weight%", String.valueOf(entry.priorityWeight()));
            player.sendActionBar(MessageUtil.toComponent(message));
        }
    }

    private int resolvePriorityWeight(Player player) {
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("queue.priority-permissions");
        if (section == null) {
            return 0;
        }

        int weight = 0;
        for (String permission : section.getKeys(false)) {
            if (player.hasPermission(permission)) {
                weight = Math.max(weight, section.getInt(permission, 0));
            }
        }

        return weight;
    }

    public synchronized void loadState() {
        activeEntries.clear();
        queue.clear();
        graceEntries.clear();

        QueueStateData data = queueStateRepository.load();
        long highestSequence = 0L;

        if (data.getQueueEntries() != null) {
            for (QueueStateData.QueueEntrySnapshot snapshot : data.getQueueEntries()) {
                try {
                    UUID uuid = UUID.fromString(snapshot.getUuid());
                    QueueEntry entry = new QueueEntry(uuid, snapshot.getEnqueueTimestamp(), snapshot.getPriorityWeight(), snapshot.getSequence());
                    activeEntries.put(uuid, entry);
                    queue.offer(entry);
                    highestSequence = Math.max(highestSequence, snapshot.getSequence());
                } catch (IllegalArgumentException ignored) {
                }
            }
        }

        if (data.getGraceEntries() != null) {
            for (QueueStateData.GraceEntrySnapshot snapshot : data.getGraceEntries()) {
                try {
                    UUID uuid = UUID.fromString(snapshot.getUuid());
                    graceEntries.put(uuid, new GraceEntry(
                            snapshot.getEnqueueTimestamp(),
                            snapshot.getPriorityWeight(),
                            snapshot.getSequence(),
                            snapshot.getExpiresAtMillis()
                    ));
                    highestSequence = Math.max(highestSequence, snapshot.getSequence());
                } catch (IllegalArgumentException ignored) {
                }
            }
        }

        sequenceGenerator.set(highestSequence);
        dirty = false;
    }

    @SuppressWarnings("unused")
    public synchronized void saveStateIfDirty() {
        if (!dirty) {
            return;
        }

        QueueStateData data = new QueueStateData();

        List<QueueStateData.QueueEntrySnapshot> queueSnapshots = new ArrayList<>();
        for (QueueEntry entry : activeEntries.values()) {
            QueueStateData.QueueEntrySnapshot snapshot = new QueueStateData.QueueEntrySnapshot();
            snapshot.setUuid(entry.uuid().toString());
            snapshot.setEnqueueTimestamp(entry.enqueueTimestamp());
            snapshot.setPriorityWeight(entry.priorityWeight());
            snapshot.setSequence(entry.sequence());
            queueSnapshots.add(snapshot);
        }
        data.setQueueEntries(queueSnapshots);

        List<QueueStateData.GraceEntrySnapshot> graceSnapshots = new ArrayList<>();
        for (Map.Entry<UUID, GraceEntry> mapEntry : graceEntries.entrySet()) {
            GraceEntry grace = mapEntry.getValue();
            QueueStateData.GraceEntrySnapshot snapshot = new QueueStateData.GraceEntrySnapshot();
            snapshot.setUuid(mapEntry.getKey().toString());
            snapshot.setEnqueueTimestamp(grace.enqueueTimestamp());
            snapshot.setPriorityWeight(grace.priorityWeight());
            snapshot.setSequence(grace.sequence());
            snapshot.setExpiresAtMillis(grace.expiresAtMillis());
            graceSnapshots.add(snapshot);
        }
        data.setGraceEntries(graceSnapshots);

        queueStateRepository.save(data);
        dirty = false;
    }

    private void markDirty() {
        dirty = true;
    }

    private record GraceEntry(long enqueueTimestamp, int priorityWeight, long sequence, long expiresAtMillis) {}
}
