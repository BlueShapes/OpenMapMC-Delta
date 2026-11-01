package net.aosankaku.openmapdelta.world;
import net.minecraft.server.MinecraftServer;
import java.util.*;
public final class ServerWorldIdentity { public static Optional<UUID> get(MinecraftServer server) { return Optional.of(server.worldId); } }
