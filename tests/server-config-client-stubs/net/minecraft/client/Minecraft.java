package net.minecraft.client;

import net.minecraft.client.multiplayer.ClientPacketListener;

public final class Minecraft {
    private static final Minecraft INSTANCE = new Minecraft();
    private ClientPacketListener connection;
    private Minecraft() {}
    public static Minecraft getInstance() { return INSTANCE; }
    public ClientPacketListener getConnection() { return connection; }
    public void setConnection(ClientPacketListener connection) { this.connection = connection; }
}
