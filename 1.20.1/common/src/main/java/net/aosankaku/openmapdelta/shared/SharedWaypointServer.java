// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.shared;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import net.aosankaku.openmapdelta.OpenMapDeltaCommon;
import net.aosankaku.openmapdelta.server.ServerConfig;
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
import java.util.Optional;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.function.BiConsumer;
import java.util.function.Predicate;

/** Server-authoritative, world-local shared waypoint state. All mutating calls run on the server thread. */
public final class SharedWaypointServer {
    public static final String FILE_NAME = "odysseymap-shared-waypoints.json";
    public static final int MUTATION_RATE_TICKS = 5;
    private static final Map<MinecraftServer, WorldState> STATES = new WeakHashMap<>();
    private static final Gson EVENT_GSON = new Gson();
    private static final String CREATED = "odysseymap.shared.created";
    private static final String UPDATED = "odysseymap.shared.updated";
    private static final String DELETED = "odysseymap.shared.deleted";

    public record ApplyResult(SharedWaypointFeedback feedback, boolean changed) {}
    private record SyncStamp(long revision, boolean multiplayer, boolean administrator) {}
    private static final class WorldState {
        final UUID worldId;
        final Path path;
        final Map<UUID, SharedWaypoint> waypoints;
        final Map<UUID, Long> lastRequestTick = new HashMap<>();
        final Map<UUID, SyncStamp> sent = new HashMap<>();
        long revision;
        boolean available;
        WorldState(UUID worldId, Path path, SharedWaypointStore.Stored stored, boolean available) {
            this.worldId = worldId; this.path = path; this.revision = stored.revision(); this.available = available;
            this.waypoints = new LinkedHashMap<>();
            for (SharedWaypoint waypoint : stored.waypoints()) this.waypoints.put(waypoint.id(), waypoint);
        }
    }

    private SharedWaypointServer() {}

    /** Load exactly once at server start. A broken identity or store permanently disables writes for this run. */
    public static synchronized void initialize(MinecraftServer server) {
        if (STATES.containsKey(server)) return;
        Optional<UUID> id = ServerWorldIdentity.get(server);
        Path file = server.getWorldPath(LevelResource.ROOT).resolve("data").resolve(FILE_NAME);
        if (id.isEmpty()) {
            STATES.put(server, new WorldState(null, file, new SharedWaypointStore.Stored(0, List.of()), false));
            OpenMapDeltaCommon.LOGGER.warn("Shared waypoints are disabled because this world's persistent identity is unavailable");
            return;
        }
        SharedWaypointStore.LoadResult loaded = SharedWaypointStore.load(file);
        if (!loaded.valid()) {
            STATES.put(server, new WorldState(id.get(), file, new SharedWaypointStore.Stored(0, List.of()), false));
            OpenMapDeltaCommon.LOGGER.error("Shared waypoints are disabled because {} could not be read; the file was left untouched", file, loaded.failure());
            return;
        }
        STATES.put(server, new WorldState(id.get(), file, loaded.stored(), true));
    }

    public static ApplyResult apply(MinecraftServer server, ServerPlayer actor, SharedWaypointRequest request,
                                    boolean administrator, boolean multiplayer, Predicate<String> knownDimension) {
        if (server == null || actor == null) return failure("odysseymap.shared.denied");
        if (request == null || knownDimension == null) return failure("odysseymap.shared.invalid");
        WorldState state = state(server);
        if (state == null || !state.available || state.worldId == null) return failure("odysseymap.shared.unavailable");
        if (takeRateToken(server, actor, state)) return failure("odysseymap.shared.rate_limited");
        return applyAfterRate(server, actor, request, administrator, multiplayer, knownDimension, state);
    }

    /** Applies raw bytes after charging the per-player limit, including malformed but bounded requests. */
    public static ApplyResult applyEncoded(MinecraftServer server, ServerPlayer actor, byte[] encoded,
                                           boolean administrator, boolean multiplayer, Predicate<String> knownDimension) {
        if (server == null || actor == null) return failure("odysseymap.shared.denied");
        if (encoded == null || encoded.length > SharedWaypointRequest.MAX_BYTES || knownDimension == null) return failure("odysseymap.shared.invalid");
        WorldState state = state(server);
        if (state == null || !state.available || state.worldId == null) return failure("odysseymap.shared.unavailable");
        if (takeRateToken(server, actor, state)) return failure("odysseymap.shared.rate_limited");
        try {
            return applyAfterRate(server, actor, SharedWaypointRequest.decode(encoded), administrator, multiplayer, knownDimension, state);
        } catch (IllegalArgumentException malformed) {
            return failure("odysseymap.shared.invalid");
        }
    }

    private static boolean takeRateToken(MinecraftServer server, ServerPlayer actor, WorldState state) {
        long tick = server.getTickCount();
        Long previous = state.lastRequestTick.put(actor.getUUID(), tick);
        return previous != null && tick - previous < MUTATION_RATE_TICKS;
    }

    private static ApplyResult applyAfterRate(MinecraftServer server, ServerPlayer actor, SharedWaypointRequest request,
                                              boolean administrator, boolean multiplayer, Predicate<String> knownDimension,
                                              WorldState state) {
        try {
            if (request.operation() == SharedWaypointRequest.Operation.CREATE) {
                if (!multiplayer) return failure("odysseymap.shared.unavailable");
                if (state.waypoints.size() >= Math.min(SharedWaypointSnapshot.MAX_WAYPOINTS, ServerConfig.maxSharedWaypoints())) return failure("odysseymap.shared.full");
                validateRequest(request, knownDimension);
                UUID id;
                do { id = UUID.randomUUID(); } while (state.waypoints.containsKey(id));
                var waypoint = new SharedWaypoint(id, actor.getUUID(), actor.getName().getString(),
                        request.dimension(), request.x(), request.y(), request.z(), request.label(),
                        request.color(), request.iconId(), request.category());
                var candidate = new LinkedHashMap<>(state.waypoints);
                candidate.put(id, waypoint);
                ApplyResult result = commit(state, candidate, CREATED);
                if (result.changed()) logEvent("shared_waypoint_created", state.waypoints.get(id), null);
                return result;
            }
            SharedWaypoint current = state.waypoints.get(request.id());
            if (current == null) return failure("odysseymap.shared.invalid");
            if (!SharedWaypointAccess.canManage(current, actor.getUUID(), administrator)) return failure("odysseymap.shared.denied");
            var candidate = new LinkedHashMap<>(state.waypoints);
            if (request.operation() == SharedWaypointRequest.Operation.DELETE) {
                candidate.remove(current.id());
                ApplyResult result = commit(state, candidate, DELETED);
                if (result.changed()) logEvent("shared_waypoint_deleted", current, actor);
                return result;
            }
            validateRequest(request, knownDimension);
            var updated = new SharedWaypoint(current.id(), current.ownerId(), current.ownerName(), request.dimension(),
                    request.x(), request.y(), request.z(), request.label(), request.color(), request.iconId(), request.category());
            candidate.put(updated.id(), updated);
            ApplyResult result = commit(state, candidate, UPDATED);
            if (result.changed()) logEvent("shared_waypoint_updated", state.waypoints.get(updated.id()), actor);
            return result;
        } catch (IllegalArgumentException malformed) {
            return failure("odysseymap.shared.invalid");
        }
    }

    private static void validateRequest(SharedWaypointRequest request, Predicate<String> knownDimension) {
        SharedWaypointData.validateDimension(request.dimension());
        if (!knownDimension.test(request.dimension())) throw new IllegalArgumentException("Unknown dimension");
        SharedWaypointData.validateCoordinates(request.x(), request.y(), request.z());
        SharedWaypointData.validateLabel(request.label());
        SharedWaypointData.validateIcon(request.iconId());
        SharedWaypointData.validateCategory(request.category());
    }

    private static ApplyResult commit(WorldState state, Map<UUID, SharedWaypoint> candidate, String successKey) {
        long revision = state.revision + 1;
        if (revision <= state.revision) return failure("odysseymap.shared.storage_error");
        try {
            SharedWaypointStore.save(state.path, new SharedWaypointStore.Stored(revision, new ArrayList<>(candidate.values())));
        } catch (IOException | RuntimeException failure) {
            return failure("odysseymap.shared.storage_error");
        }
        state.waypoints.clear(); state.waypoints.putAll(candidate);
        state.revision = revision;
        state.sent.clear();
        return new ApplyResult(SharedWaypointFeedback.success(successKey), true);
    }

    private static ApplyResult failure(String key) { return new ApplyResult(SharedWaypointFeedback.failure(key), false); }

    private static void logEvent(String type, SharedWaypoint waypoint, ServerPlayer actor) {
        JsonObject event = new JsonObject();
        event.addProperty("schema_version", 1);
        event.addProperty("event", type);
        event.addProperty("waypoint_id", waypoint.id().toString());
        event.addProperty("creator_uuid", waypoint.ownerId().toString());
        event.addProperty("creator_name", waypoint.ownerName());
        event.addProperty("name", waypoint.label());
        event.addProperty("dimension", waypoint.dimension());
        event.addProperty("x", waypoint.x());
        event.addProperty("y", waypoint.y());
        event.addProperty("z", waypoint.z());
        if (actor != null) {
            event.addProperty("actor_uuid", actor.getUUID().toString());
            event.addProperty("actor_name", actor.getName().getString());
        }
        OpenMapDeltaCommon.LOGGER.info("OPENMAPMC_EVENT {}", EVENT_GSON.toJson(event));
    }

    public static boolean syncIfChanged(MinecraftServer server, ServerPlayer player, boolean multiplayer,
                                        boolean administrator, BiConsumer<ServerPlayer, SharedWaypointSnapshot> sender) {
        WorldState state = state(server);
        if (state == null || !state.available || state.worldId == null) return false;
        SyncStamp stamp = new SyncStamp(state.revision, multiplayer, administrator);
        if (stamp.equals(state.sent.get(player.getUUID()))) return false;
        sendSnapshot(state, player, multiplayer, administrator, sender);
        state.sent.put(player.getUUID(), stamp);
        return true;
    }

    public static void sendSnapshot(MinecraftServer server, ServerPlayer player, boolean multiplayer,
                                    boolean administrator, BiConsumer<ServerPlayer, SharedWaypointSnapshot> sender) {
        WorldState state = state(server);
        if (state == null || !state.available || state.worldId == null) return;
        sendSnapshot(state, player, multiplayer, administrator, sender);
        state.sent.put(player.getUUID(), new SyncStamp(state.revision, multiplayer, administrator));
    }

    private static void sendSnapshot(WorldState state, ServerPlayer player, boolean multiplayer,
                                     boolean administrator, BiConsumer<ServerPlayer, SharedWaypointSnapshot> sender) {
        List<SharedWaypoint> all = List.copyOf(state.waypoints.values());
        int pages = Math.max(1, (all.size() + SharedWaypointSnapshot.PAGE_SIZE - 1) / SharedWaypointSnapshot.PAGE_SIZE);
        for (int index = 0; index < pages; index++) {
            int start = index * SharedWaypointSnapshot.PAGE_SIZE;
            int end = Math.min(all.size(), start + SharedWaypointSnapshot.PAGE_SIZE);
            sender.accept(player, new SharedWaypointSnapshot(state.worldId, state.revision, multiplayer, administrator,
                    index, pages, all.subList(start, end)));
        }
    }

    public static void playerJoined(MinecraftServer server, ServerPlayer player) {
        WorldState state = state(server);
        if (state != null) state.sent.remove(player.getUUID());
    }

    public static void trimDisconnected(MinecraftServer server) {
        WorldState state = state(server);
        if (state == null) return;
        var connected = new java.util.HashSet<UUID>();
        server.getPlayerList().getPlayers().forEach(player -> connected.add(player.getUUID()));
        state.sent.keySet().retainAll(connected);
        state.lastRequestTick.keySet().retainAll(connected);
    }

    public static boolean available(MinecraftServer server) {
        WorldState state = state(server);
        return state != null && state.available && state.worldId != null;
    }

    public static int waypointCount(MinecraftServer server) {
        WorldState state = state(server);
        return state == null || !state.available ? 0 : state.waypoints.size();
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
