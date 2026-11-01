// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; Apache-2.0.
package net.aosankaku.openmapdelta.area;

import net.aosankaku.openmapdelta.OpenMapDeltaCommon;
import net.aosankaku.openmapdelta.config.OpenMapDeltaConfig;
import net.aosankaku.openmapdelta.network.ClientWorldIdentityState;
import net.aosankaku.openmapdelta.platform.DataPaths;
import net.aosankaku.openmapdelta.world.OpenMapDeltaClient;
import net.aosankaku.openmapdelta.world.ServerWorldIdentity;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Client-thread facade: private areas never pass through the shared-area sender. */
public final class AreaManager {
    public record SaveResult(boolean success, boolean pending, String messageKey, UUID requestId) {}
    private record Pending(Object connection, UUID worldId, long sentAtNanos) {}
    private static final long TIMEOUT_NANOS = 20_000_000_000L;
    private static AreaWorkspace workspace;
    private static Object connection;
    private static UUID actor;
    private static String actorName = "";
    private static String dimension;
    private static AreaDocument empty = new AreaDocument(0, List.of());
    private static AreaDocument shared = empty;
    private static UUID sharedWorld;
    private static long sharedRevision = -1;
    private static UUID requestedWorld;
    private static int retryTicks;
    private static boolean enabledLastTick;
    private static boolean pathFailed;
    private static final Map<UUID, Pending> pending = new LinkedHashMap<>();
    private static final Map<UUID, SaveResult> completed = new LinkedHashMap<>();

    private AreaManager() {}

    public static boolean enabled() { return OpenMapDeltaConfig.AREAS_ENABLED.get(); }

    public static void tick(Minecraft mc) {
        if (mc == null || mc.level == null || mc.player == null || mc.getConnection() == null) {
            if (connection != null) reset();
            return;
        }
        Object token = mc.getConnection().getConnection();
        if (connection != token) {
            reset();
            connection = token;
        }
        actor = mc.player.getUUID();
        actorName = mc.player.getName().getString();
        dimension = mc.level.dimension().identifier().toString();
        AreaClient.bind(token);
        UUID expectedWorld = mc.getSingleplayerServer() == null
                ? ClientWorldIdentityState.get(token).orElse(null)
                : ServerWorldIdentity.get(mc.getSingleplayerServer()).orElse(null);
        if (expectedWorld != null && (!expectedWorld.equals(requestedWorld)
                || (!AreaClient.available() && ++retryTicks >= 100))) {
            AreaClient.requestSync(expectedWorld);
            requestedWorld = expectedWorld;
            retryTicks = 0;
        }
        boolean active = enabled();
        if (!active) {
            if (enabledLastTick && workspace != null) workspace.reset();
            enabledLastTick = false;
            return;
        }
        enabledLastTick = true;
        if (workspace == null && !pathFailed) {
            try { workspace = new AreaWorkspace(DataPaths.configDirectory()); }
            catch (RuntimeException failure) {
                pathFailed = true;
                OpenMapDeltaCommon.LOGGER.error("Private area storage is unavailable; existing data was left untouched", failure);
            }
        }
        if (workspace != null) workspace.open(token, OpenMapDeltaClient.persistentWorldIdentity(mc));
        refreshShared();
        consumeFeedback();
    }

    private static void refreshShared() {
        UUID world = AreaClient.worldId();
        long revision = AreaClient.serverRevision();
        if (!Objects.equals(world, sharedWorld) || revision != sharedRevision) {
            shared = new AreaDocument(Math.max(0, revision), AreaClient.areas());
            sharedWorld = world;
            sharedRevision = revision;
        }
    }

    public static AreaDocument document(MapArea.Scope scope) {
        if (scope == MapArea.Scope.PERSONAL) return workspace == null ? empty : workspace.document();
        refreshShared();
        return shared;
    }

    public static List<MapArea> areas(String targetDimension) {
        if (!enabled() || connection == null || targetDimension == null) return List.of();
        var values = new ArrayList<MapArea>();
        for (MapArea.Scope scope : MapArea.Scope.values()) {
            for (MapArea area : document(scope).areas()) {
                if (area.dimension().equals(targetDimension)) values.add(area);
            }
        }
        return List.copyOf(values);
    }

    public static boolean available(MapArea.Scope scope) {
        return enabled() && connection != null && actor != null && (scope == MapArea.Scope.PERSONAL
                ? workspace != null && workspace.available() : AreaClient.available());
    }

    public static boolean canManage(MapArea area) {
        return area != null && available(area.scope()) && (area.scope() == MapArea.Scope.PERSONAL
                || AreaClient.canManage(actor, area));
    }

    public static UUID actorId() { return actor; }
    public static String actorName() { return actorName; }
    public static String dimension() { return dimension; }

    /** Accepts the editor's entire draft, but sends only changed SHARED records in an explicit transaction. */
    public static SaveResult save(MapArea.Scope scope, AreaDocument base, AreaDocument edited) {
        if (!available(scope)) return failure("gui.openmapdelta.area.unavailable", null);
        if (base == null || edited == null || edited.areas().stream().anyMatch(area -> area.scope() != scope)) {
            return failure("gui.openmapdelta.area.invalid", null);
        }
        if (scope == MapArea.Scope.PERSONAL) {
            AreaWorkspace.Result result = workspace.save(base, edited);
            return switch (result) {
                case SAVED -> new SaveResult(true, false, "gui.openmapdelta.area.saved", null);
                case CONFLICT -> failure("gui.openmapdelta.area.conflict", null);
                case IO_ERROR -> failure("gui.openmapdelta.area.storage_error", null);
                case INVALID -> failure("gui.openmapdelta.area.invalid", null);
                case UNAVAILABLE -> failure("gui.openmapdelta.area.unavailable", null);
            };
        }
        refreshShared();
        if (base != shared || AreaClient.worldId() == null) return failure("gui.openmapdelta.area.conflict", null);
        if (!pending.isEmpty()) return failure("gui.openmapdelta.area.pending", null);
        var upserts = new ArrayList<MapArea>();
        var deletes = new ArrayList<UUID>();
        for (MapArea area : edited.areas()) {
            MapArea previous = base.find(area.id()).orElse(null);
            if (!area.equals(previous)) {
                if (previous != null && !canManage(previous)) return failure("gui.openmapdelta.area.denied", null);
                upserts.add(area);
            }
        }
        for (MapArea previous : base.areas()) {
            if (edited.find(previous.id()).isEmpty()) {
                if (!canManage(previous)) return failure("gui.openmapdelta.area.denied", null);
                deletes.add(previous.id());
            }
        }
        if (upserts.isEmpty() && deletes.isEmpty()) return new SaveResult(true, false, "gui.openmapdelta.area.saved", null);
        UUID requestId = UUID.randomUUID();
        try {
            var request = new AreaWire.Request(requestId, AreaClient.worldId(), base.revision(),
                    AreaWire.Operation.MUTATE, upserts, deletes);
            // Enforce the actual transport byte budget before the request can enter a loader's sender.
            AreaWire.encode(request);
            pending.put(requestId, new Pending(connection, AreaClient.worldId(), System.nanoTime()));
            if (!AreaClient.send(request)) {
                pending.remove(requestId);
                return failure("gui.openmapdelta.area.unavailable", requestId);
            }
            return new SaveResult(false, true, "gui.openmapdelta.area.pending", requestId);
        } catch (IllegalArgumentException invalid) {
            pending.remove(requestId);
            return failure("gui.openmapdelta.area.limit", requestId);
        }
    }

    private static void consumeFeedback() {
        AreaWire.Feedback feedback = AreaClient.lastFeedback();
        for (var entry : new ArrayList<>(pending.entrySet())) {
            UUID id = entry.getKey();
            Pending request = entry.getValue();
            if (request.connection() != connection || !Objects.equals(request.worldId(), AreaClient.worldId())) {
                finish(id, failure("gui.openmapdelta.area.unavailable", id));
            } else if (feedback != null && id.equals(feedback.requestId())) {
                if (!feedback.success()) finish(id, failure(feedback.messageKey(), id));
                else if (AreaClient.serverRevision() >= feedback.revision()) {
                    finish(id, new SaveResult(true, false, feedback.messageKey(), id));
                }
            }
            if (pending.containsKey(id) && System.nanoTime() - request.sentAtNanos() > TIMEOUT_NANOS) {
                finish(id, failure("gui.openmapdelta.area.timeout", id));
            }
        }
    }

    private static void finish(UUID id, SaveResult result) {
        pending.remove(id);
        completed.put(id, result);
        while (completed.size() > 16) completed.remove(completed.keySet().iterator().next());
    }

    public static SaveResult pendingResult(UUID id) {
        consumeFeedback();
        if (id == null) return failure("gui.openmapdelta.area.invalid", null);
        SaveResult done = completed.get(id);
        if (done != null) return done;
        return pending.containsKey(id) ? new SaveResult(false, true, "gui.openmapdelta.area.pending", id)
                : failure("gui.openmapdelta.area.unavailable", id);
    }

    private static SaveResult failure(String key, UUID id) { return new SaveResult(false, false, key, id); }

    public static void reset() {
        if (workspace != null) workspace.reset();
        AreaClient.reset();
        connection = null;
        actor = null;
        actorName = "";
        dimension = null;
        empty = new AreaDocument(0, List.of());
        shared = empty;
        sharedWorld = null;
        sharedRevision = -1;
        requestedWorld = null;
        retryTicks = 0;
        enabledLastTick = false;
        pathFailed = false;
        pending.clear();
        completed.clear();
    }
}
