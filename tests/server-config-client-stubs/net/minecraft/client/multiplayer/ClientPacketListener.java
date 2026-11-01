package net.minecraft.client.multiplayer;

import net.minecraft.network.Connection;

public final class ClientPacketListener {
    private final Connection connection;
    public ClientPacketListener(Connection connection) { this.connection = connection; }
    public Connection getConnection() { return connection; }
}
