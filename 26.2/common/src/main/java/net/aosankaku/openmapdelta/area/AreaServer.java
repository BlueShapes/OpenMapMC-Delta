// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.area;

import net.aosankaku.openmapdelta.OpenMapDeltaCommon;
import net.aosankaku.openmapdelta.world.ServerWorldIdentity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.function.BiConsumer;
import java.util.function.Predicate;

/** Server-authoritative area state persisted independently in the active save. */
public final class AreaServer {
    public static final String FILE_NAME = "openmapdelta-areas.json";
    public static final int MUTATION_RATE_TICKS = 20;
    private static final Map<MinecraftServer, WorldState> STATES = new WeakHashMap<>();

    public record ApplyResult(AreaWire.Feedback feedback, boolean changed, boolean syncRequested) {}
    private record SyncStamp(long revision, boolean administrator, boolean available) {}
    private static final class WorldState {
        final UUID worldId;
        final Path path;
        final Map<UUID, MapArea> areas = new LinkedHashMap<>();
        final Map<UUID, Long> lastMutationTick = new HashMap<>();
        final Map<UUID, Long> lastSyncTick = new HashMap<>();
        final Map<UUID, SyncStamp> sent = new HashMap<>();
        long revision;
        boolean available;

        WorldState(UUID worldId, Path path, AreaDocument document, boolean available) {
            this.worldId = worldId;
            this.path = path;
            this.revision = document.revision();
            this.available = available;
            for (MapArea area : document.areas()) areas.put(area.id(), area);
        }

        AreaDocument document() { return new AreaDocument(revision, new ArrayList<>(areas.values())); }
    }

    private AreaServer() {}

    /** Reads the save identity and data file only during server start; failed reads leave the store untouched. */
    public static synchronized void initialize(MinecraftServer server) {
        if (server == null || STATES.containsKey(server)) return;
        UUID identity = ServerWorldIdentity.get(server).orElse(null);
        Path file = server.getWorldPath(LevelResource.ROOT).resolve("data").resolve(FILE_NAME);
        if (identity == null) {
            STATES.put(server, new WorldState(null, file, AreaDocument.empty(), false));
            OpenMapDeltaCommon.LOGGER.warn("Shared areas are disabled because this world's persistent identity is unavailable");
            return;
        }
        AreaStore.LoadResult loaded = AreaStore.load(file, identity.toString());
        if (!loaded.valid()) {
            STATES.put(server, new WorldState(identity, file, AreaDocument.empty(), false));
            OpenMapDeltaCommon.LOGGER.error("Shared areas are disabled because {} could not be read; the file was left untouched", file, loaded.failure());
            return;
        }
        AreaDocument document = loaded.document();
        try {
            if (document.areas().stream().anyMatch(area -> area.scope() != MapArea.Scope.SHARED)) {
                throw new IllegalArgumentException("Server area store contains a personal feature");
            }
            // Verify every persisted feature is encodable in the bounded shared protocol before enabling the channel.
            AreaWire.snapshotPages(identity, document.revision(), false, true, document.areas());
        } catch (RuntimeException invalidSharedData) {
            STATES.put(server, new WorldState(identity, file, AreaDocument.empty(), false));
            OpenMapDeltaCommon.LOGGER.error("Shared areas are disabled because {} contains data that cannot be served; the file was left untouched",
                    file, invalidSharedData);
            return;
        }
        STATES.put(server, new WorldState(identity, file, document, true));
    }

    /** Charges bounded malformed requests before decoding, then validates and commits only on the server thread. */
    public static ApplyResult applyEncoded(MinecraftServer server, ServerPlayer actor, byte[] encoded,
                                           boolean administrator, Predicate<String> knownDimension) {
        if (server == null || actor == null) return null;
        WorldState state = state(server);
        if (state == null || state.worldId == null) return null;
        AreaWire.Operation hintedOperation = AreaWire.operationHint(encoded);
        boolean rateLimited = takeRateToken(server, actor, state, hintedOperation == AreaWire.Operation.SYNC);
        if (encoded == null || encoded.length > AreaWire.MAX_REQUEST_BYTES) {
            return null;
        }
        if (rateLimited) {
            AreaWire.RequestHeader header = AreaWire.decodeRequestHeader(encoded);
            if (header == null) return null;
            return feedback(header, state, false, "rate_limited", false);
        }
        final AreaWire.Request request;
        try {
            request = AreaWire.decodeRequest(encoded);
        } catch (IllegalArgumentException malformed) {
            return null;
        }
        return applyRequest(server, actor, request, administrator, knownDimension, state);
    }

    private static ApplyResult applyRequest(MinecraftServer server, ServerPlayer actor, AreaWire.Request request,
                                             boolean administrator, Predicate<String> knownDimension,
                                             WorldState state) {
        if (request.operation() == AreaWire.Operation.SYNC) {
            if (!state.available) return feedback(request, state, false, "unavailable", false);
            if (!request.worldId().equals(state.worldId)) return feedback(request, state, false, "conflict", false);
            return feedback(request, state, true, "synced", false);
        }
        if (!state.available) return feedback(request, state, false, "unavailable", false);
        AreaTransactions.Result transaction = AreaTransactions.apply(state.document(), state.worldId, request,
                actor.getUUID(), actor.getName().getString(), administrator, knownDimension);
        if (!transaction.success()) {
            return feedback(request, state, false, feedbackReason(transaction.messageKey()), false);
        }
        if (!transaction.changed()) return feedback(request, state, true, "synced", false);
        AreaDocument candidate = transaction.document();
        try {
            AreaStore.save(state.path, state.worldId.toString(), candidate);
        } catch (IOException | RuntimeException failure) {
            OpenMapDeltaCommon.LOGGER.error("Could not persist shared areas to {}; the in-memory state was not changed", state.path, failure);
            return feedback(request, state, false, "storage_error", false);
        }
        state.areas.clear();
        for (MapArea area : candidate.areas()) state.areas.put(area.id(), area);
        state.revision = candidate.revision();
        state.sent.clear();
        return feedback(request, state, true, "saved", true);
    }

    private static String feedbackReason(String transactionKey) {
        if (transactionKey.endsWith(".denied")) return "denied";
        if (transactionKey.endsWith(".conflict")) return "conflict";
        if (transactionKey.endsWith(".limit")) return "limit";
        return "invalid";
    }

    private static ApplyResult feedback(AreaWire.Request request, WorldState state, boolean success,
                                        String reason, boolean changed) {
        return feedback(request, state, success, reason, changed, request.operation() == AreaWire.Operation.SYNC);
    }

    private static ApplyResult feedback(AreaWire.Request request, WorldState state, boolean success,
                                        String reason, boolean changed, boolean syncRequested) {
        return new ApplyResult(new AreaWire.Feedback(request.requestId(), state.worldId, success,
                "openmapdelta.area.feedback." + reason, state.revision), changed, syncRequested);
    }

    private static ApplyResult feedback(AreaWire.RequestHeader header, WorldState state, boolean success,
                                        String reason, boolean changed) {
        return new ApplyResult(new AreaWire.Feedback(header.requestId(), state.worldId, success,
                "openmapdelta.area.feedback." + reason, state.revision), changed, false);
    }

    private static boolean takeRateToken(MinecraftServer server, ServerPlayer actor, WorldState state, boolean sync) {
        long tick = server.getTickCount();
        Map<UUID, Long> bucket = sync ? state.lastSyncTick : state.lastMutationTick;
        Long previous = bucket.put(actor.getUUID(), tick);
        return previous != null && tick - previous < MUTATION_RATE_TICKS;
    }

    public static boolean syncIfChanged(MinecraftServer server, ServerPlayer player, boolean administrator,
                                        BiConsumer<ServerPlayer, AreaWire.Snapshot> sender) {
        WorldState state = state(server);
        if (state == null || state.worldId == null) return false;
        SyncStamp stamp = new SyncStamp(state.revision, administrator, state.available);
        if (stamp.equals(state.sent.get(player.getUUID()))) return false;
        sendSnapshot(state, player, administrator, sender);
        state.sent.put(player.getUUID(), stamp);
        return true;
    }

    public static void sendSnapshot(MinecraftServer server, ServerPlayer player, boolean administrator,
                                    BiConsumer<ServerPlayer, AreaWire.Snapshot> sender) {
        WorldState state = state(server);
        if (state == null || state.worldId == null) return;
        sendSnapshot(state, player, administrator, sender);
        state.sent.put(player.getUUID(), new SyncStamp(state.revision, administrator, state.available));
    }

    private static void sendSnapshot(WorldState state, ServerPlayer player, boolean administrator,
                                     BiConsumer<ServerPlayer, AreaWire.Snapshot> sender) {
        List<MapArea> all = state.available ? List.copyOf(state.areas.values()) : List.of();
        for (AreaWire.Snapshot page : AreaWire.snapshotPages(
                state.worldId, state.revision, administrator, state.available, all)) {
            sender.accept(player, page);
        }
    }

    public static void playerJoined(MinecraftServer server, ServerPlayer player) {
        WorldState state = state(server);
        if (state != null) {
            state.sent.remove(player.getUUID());
            state.lastMutationTick.remove(player.getUUID());
            state.lastSyncTick.remove(player.getUUID());
        }
    }

    public static void trimDisconnected(MinecraftServer server) {
        WorldState state = state(server);
        if (state == null) return;
        var connected = new java.util.HashSet<UUID>();
        server.getPlayerList().getPlayers().forEach(player -> connected.add(player.getUUID()));
        state.sent.keySet().retainAll(connected);
        state.lastMutationTick.keySet().retainAll(connected);
        state.lastSyncTick.keySet().retainAll(connected);
    }

    public static synchronized void stop(MinecraftServer server) {
        if (server != null) STATES.remove(server);
    }

    public static boolean available(MinecraftServer server) {
        WorldState state = state(server);
        return state != null && state.available && state.worldId != null;
    }

    public static int areaCount(MinecraftServer server) {
        WorldState state = state(server);
        return state == null || !state.available ? 0 : state.areas.size();
    }

    private static synchronized WorldState state(MinecraftServer server) {
        if (server == null) return null;
        WorldState state = STATES.get(server);
        if (state == null) {
            initialize(server);
            state = STATES.get(server);
        }
        return state;
    }
}
