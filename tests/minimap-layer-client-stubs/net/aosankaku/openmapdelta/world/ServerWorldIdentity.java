package net.aosankaku.openmapdelta.world;

import java.util.Optional;
import net.minecraft.server.MinecraftServer;

public final class ServerWorldIdentity {
    private ServerWorldIdentity() {}
    public static Optional<String> get(MinecraftServer server) { return Optional.empty(); }
}
