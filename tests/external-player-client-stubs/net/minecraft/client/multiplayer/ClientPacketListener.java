package net.minecraft.client.multiplayer;

import net.minecraft.network.Connection;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public final class ClientPacketListener {
    private Connection connection;
    private final List<OnlinePlayer> onlinePlayers = new ArrayList<>();
    public ClientPacketListener(Connection connection, Collection<OnlinePlayer> players) {
        this.connection = connection; onlinePlayers.addAll(players);
    }
    public Connection getConnection() { return connection; }
    public Collection<OnlinePlayer> getOnlinePlayers() { return onlinePlayers; }
    public void setConnection(Connection connection) { this.connection = connection; }
}
