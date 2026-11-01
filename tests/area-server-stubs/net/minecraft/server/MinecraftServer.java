package net.minecraft.server;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Minimal server surface used by AreaServer. */
public final class MinecraftServer {
    private final Path root;
    private final PlayerList players = new PlayerList();
    private long tickCount;

    public MinecraftServer(Path root) { this.root = root; }
    public Path getWorldPath(LevelResource resource) { return root; }
    public long getTickCount() { return tickCount; }
    public void setTickCount(long tickCount) { this.tickCount = tickCount; }
    public PlayerList getPlayerList() { return players; }

    public static final class PlayerList {
        private final List<ServerPlayer> players = new ArrayList<>();
        public List<ServerPlayer> getPlayers() { return players; }
    }
}
