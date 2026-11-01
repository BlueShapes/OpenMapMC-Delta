// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.world;

import net.aosankaku.openmapdelta.OpenMapDeltaCommon;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.WeakHashMap;

/** Server-side cache for a persistent UUID stored in the save root. */
public final class ServerWorldIdentity {
    private static final String FILE_NAME = "odysseymap-world-id";
    private static final Map<MinecraftServer, Optional<UUID>> IDENTITIES = new WeakHashMap<>();

    private ServerWorldIdentity() {}

    /** Loads the identity during server startup so ticks never perform disk IO. */
    public static Optional<UUID> initialize(MinecraftServer server) {
        return get(server);
    }

    /** Returns the cached identity, including a cached empty result after any IO failure. */
    public static synchronized Optional<UUID> get(MinecraftServer server) {
        return IDENTITIES.computeIfAbsent(server, current -> {
            Path file = current.getWorldPath(LevelResource.ROOT).resolve("data").resolve(FILE_NAME);
            Optional<UUID> identity = WorldIdentityFile.loadOrCreate(file);
            if (identity.isEmpty()) {
                OpenMapDeltaCommon.LOGGER.warn("Could not load or create the world identity file at {}; persistent map identity is unavailable for this world", file);
            }
            return identity;
        });
    }
}
