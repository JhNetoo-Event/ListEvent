package org.crystalocean.listEvent.service;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Optional;

public class ProxyBridgeService implements PluginMessageListener {

    private final JavaPlugin plugin;
    private volatile int targetPlayerCount = -1;
    private volatile long lastUpdateAt = 0L;

    public ProxyBridgeService(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public String getChannel() {
        return plugin.getConfig().getString("queue.proxy.messaging-channel", "BungeeCord");
    }

    public String getTargetServer() {
        return plugin.getConfig().getString("queue.target-server", "principal");
    }

    public List<String> getTargetServerAliases() {
        return plugin.getConfig().getStringList("queue.proxy.target-server-aliases");
    }

    public void connect(Player player) {
        sendPluginMessage(player, out -> {
            out.writeUTF("Connect");
            out.writeUTF(getTargetServer());
        });
    }

    public void requestTargetPlayerCount() {
        Optional<? extends Player> samplePlayer = Bukkit.getOnlinePlayers().stream().findFirst();
        if (samplePlayer.isEmpty()) {
            return;
        }

        sendPluginMessage(samplePlayer.get(), out -> {
            out.writeUTF("PlayerCount");
            out.writeUTF(getTargetServer());
        });
    }

    public int getTargetPlayerCount() {
        return targetPlayerCount;
    }

    public boolean hasFreshCount() {
        long maxAgeMs = plugin.getConfig().getLong("queue.flow-control.player-count-max-age-seconds", 15L) * 1000L;
        return targetPlayerCount >= 0 && (System.currentTimeMillis() - lastUpdateAt) <= maxAgeMs;
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (!channel.equals(getChannel())) {
            return;
        }

        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(message))) {
            String subchannel = in.readUTF();
            if (!"PlayerCount".equals(subchannel)) {
                return;
            }

            String server = in.readUTF();
            int count = in.readInt();
            if (matchesTargetServer(server)) {
                targetPlayerCount = count;
                lastUpdateAt = System.currentTimeMillis();
            }
        } catch (IOException ignored) {
            // ignored
        }
    }

    private void sendPluginMessage(Player player, MessageWriter writer) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bytes);
            writer.write(out);
            player.sendPluginMessage(plugin, getChannel(), bytes.toByteArray());
        } catch (IOException exception) {
            plugin.getLogger().warning("Falha de plugin message para proxy: " + exception.getMessage());
        }
    }

    private boolean matchesTargetServer(String serverName) {
        if (serverName.equalsIgnoreCase(getTargetServer())) {
            return true;
        }

        for (String alias : getTargetServerAliases()) {
            if (serverName.equalsIgnoreCase(alias)) {
                return true;
            }
        }

        return false;
    }

    @FunctionalInterface
    private interface MessageWriter {
        void write(DataOutputStream out) throws IOException;
    }
}