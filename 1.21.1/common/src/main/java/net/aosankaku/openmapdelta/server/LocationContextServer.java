// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.server;

import net.aosankaku.openmapdelta.network.LocationContextRequest;
import net.aosankaku.openmapdelta.network.LocationContextResponse;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.WeakHashMap;

/** Authenticated, rate-limited context queries over already-loaded chunks only. */
public final class LocationContextServer {
    private static final Logger LOGGER = LoggerFactory.getLogger("OpenMapDelta");
    private static final int MAX_REFERENCE_CHECKS = 256;
    private static final int MAX_STRUCTURE_IDS = LocationContextResponse.MAX_STRUCTURE_IDS;
    private static final Map<MinecraftServer, LinkedHashMap<java.util.UUID, Long>> LAST_REQUEST_TICKS = new WeakHashMap<>();

    private LocationContextServer() {}

    /** Called from a play receiver on the server thread. The rate gate runs before request decoding. */
    public static LocationContextResponse handle(MinecraftServer server, ServerPlayer actor, byte[] encoded) {
        if (server == null || actor == null) return null;
        boolean rateLimited = rateLimit(server, actor);
        if (encoded == null || encoded.length > LocationContextRequest.MAX_BYTES) return null;
        final LocationContextRequest request;
        try {
            request = LocationContextRequest.decode(encoded);
        } catch (IllegalArgumentException malformed) {
            return null;
        }
        if (rateLimited) return LocationContextResponse.unavailable(request);
        try {
            return query(server, request);
        } catch (RuntimeException failure) {
            LOGGER.warn("Unable to inspect loaded world context at {} {} {} {}",
                    request.dimension(), request.x(), request.y(), request.z(), failure);
            return LocationContextResponse.unavailable(request);
        }
    }

    private static boolean rateLimit(MinecraftServer server, ServerPlayer actor) {
        synchronized (LAST_REQUEST_TICKS) {
            long tick = server.getTickCount();
            var requests = LAST_REQUEST_TICKS.computeIfAbsent(server, ignored -> new LinkedHashMap<>());
            if (tick % 200 == 0) requests.entrySet().removeIf(entry -> tick - entry.getValue() > 1200L);
            Long previous = requests.remove(actor.getUUID());
            boolean limited = previous != null && tick - previous < 20L;
            requests.put(actor.getUUID(), tick);
            while (requests.size() > 4096) requests.remove(requests.keySet().iterator().next());
            return limited;
        }
    }

    private static LocationContextResponse query(MinecraftServer server, LocationContextRequest request) {
        ServerLevel selectedLevel = null;
        for (ServerLevel candidate : server.getAllLevels()) {
            if (candidate.dimension().location().toString().equals(request.dimension())) {
                selectedLevel = candidate;
                break;
            }
        }
        final ServerLevel level = selectedLevel;
        if (level == null || request.y() < level.getMinBuildHeight() || request.y() >= level.getMaxBuildHeight()) {
            return LocationContextResponse.unavailable(request);
        }

        BlockPos position = new BlockPos(request.x(), request.y(), request.z());
        var targetChunk = level.getChunkSource().getChunkNow(request.x() >> 4, request.z() >> 4);
        if (targetChunk == null) return LocationContextResponse.unavailable(request);

        var biomeKey = level.getBiome(position).unwrapKey().orElse(null);
        String biomeId = biomeKey == null ? "" : biomeKey.location().toString();
        boolean complete = !biomeId.isEmpty();
        var structureRegistry = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
        var structures = LocationContextStructureQuery.aggregate(targetChunk.getAllStarts().entrySet(),
                targetChunk.getAllReferences().entrySet(),
                reference -> level.getChunkSource().getChunkNow(net.minecraft.world.level.ChunkPos.getX(reference),
                        net.minecraft.world.level.ChunkPos.getZ(reference)),
                (chunk, structure) -> chunk.getStartForStructure(structure),
                start -> start != null && start.isValid() && start.getBoundingBox().isInside(position),
                structure -> {
                    var key = structureRegistry.getKey(structure);
                    return key == null ? null : key.toString();
                }, MAX_REFERENCE_CHECKS, MAX_STRUCTURE_IDS, complete);
        return new LocationContextResponse(request.requestId(), request.dimension(), request.x(), request.y(), request.z(),
                true, structures.complete(), biomeId, structures.structureIds());
    }
}
