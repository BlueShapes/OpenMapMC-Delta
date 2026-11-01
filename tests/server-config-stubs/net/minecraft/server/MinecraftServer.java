package net.minecraft.server;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import java.util.*;
import java.nio.file.Path;
public final class MinecraftServer {
    public final UUID worldId;
    public Path directory;
    public int tick;
    public UUID owner;
    public final List<ServerPlayer> players = new ArrayList<>();
    public MinecraftServer() { this(UUID.randomUUID()); }
    public MinecraftServer(UUID worldId) { this.worldId = worldId; }
    public final class PlayerList { public List<ServerPlayer> getPlayers() { return players; } }
    public PlayerList getPlayerList() { return new PlayerList(); }
    public int getTickCount() { return tick; }
    public boolean isSingleplayerOwner(ServerPlayer.Profile profile) { return profile.id().equals(owner); }
    public Path getWorldPath(LevelResource resource) { return directory; }
}
