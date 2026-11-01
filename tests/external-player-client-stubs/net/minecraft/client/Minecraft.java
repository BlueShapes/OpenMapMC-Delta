package net.minecraft.client;

import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

public final class Minecraft {
    public Level level;
    public Player player;
    private ClientPacketListener connection;
    private ServerData currentServer;
    public ClientPacketListener getConnection() { return connection; }
    public void setConnection(ClientPacketListener connection) { this.connection = connection; }
    public ServerData getCurrentServer() { return currentServer; }
    public void setCurrentServer(ServerData currentServer) { this.currentServer = currentServer; }
}
