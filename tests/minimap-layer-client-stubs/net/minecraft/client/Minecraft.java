package net.minecraft.client;

import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;

public final class Minecraft {
    private static Minecraft instance = new Minecraft();
    public Level level;
    public Player player;
    private MinecraftServer integratedServer;
    private ServerData serverData;
    private ClientPacketListener connection;
    public static Minecraft getInstance() { return instance; }
    public static void setInstance(Minecraft minecraft) { instance = minecraft; }
    public MinecraftServer getSingleplayerServer() { return integratedServer; }
    public ServerData getCurrentServer() { return serverData; }
    public ClientPacketListener getConnection() { return connection; }
    public void setSingleplayerServer(MinecraftServer server) { integratedServer = server; }
    public void setCurrentServer(ServerData data) { serverData = data; }
    public void setConnection(ClientPacketListener connection) { this.connection = connection; }
    public static final class Player {
        private double y;
        private double eyeY;
        public Player(double y) { this.y = y; eyeY = y + 1.62; }
        public double getY() { return y; }
        public double getEyeY() { return eyeY; }
        public void setY(double y) { this.y = y; eyeY = y + 1.62; }
        public void setEyeY(double eyeY) { this.eyeY = eyeY; }
        public net.minecraft.core.BlockPos blockPosition() { return new net.minecraft.core.BlockPos(12, (int) Math.floor(y), -20); }
    }
}
