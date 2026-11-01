package net.minecraft.client.multiplayer;

public final class ClientPacketListener {
    private final ServerData data;
    private final Object connection;
    public ClientPacketListener(ServerData data, Object connection) { this.data = data; this.connection = connection; }
    public ServerData getServerData() { return data; }
    public Object getConnection() { return connection; }
}
