package net.aosankaku.openmapdelta.world;

import net.minecraft.server.MinecraftServer;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.WeakHashMap;

/** Test-controlled persistent identity cache. */
public final class ServerWorldIdentity {
    private static final Map<MinecraftServer, UUID> IDENTITIES = new WeakHashMap<>();

    private ServerWorldIdentity() {}

    public static void set(MinecraftServer server, UUID identity) { IDENTITIES.put(server, identity); }
    public static Optional<UUID> get(MinecraftServer server) { return Optional.ofNullable(IDENTITIES.get(server)); }
}
