// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.area;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;

/** Connection-scoped view of areas stored on the active Minecraft server. */
public final class AreaClient {
    private static final long PENDING_TIMEOUT_NANOS = 20_000_000_000L;
    private static Consumer<AreaWire.Request> sender;
    private static Object connection;
    private static UUID expectedWorldId;
    private static UUID worldId;
    private static long serverRevision = -1;
    private static long localRevision;
    private static boolean available;
    private static boolean administrator;
    private static List<MapArea> areas = List.of();
    private static AreaWire.Feedback lastFeedback;
    private static final Map<UUID, Long> pendingRequests = new HashMap<>();
    private static UUID pendingSyncRequestId;
    private static final AreaSnapshotAssembler assembler = new AreaSnapshotAssembler();

    private AreaClient() {}

    public static synchronized boolean configureSender(Consumer<AreaWire.Request> consumer) {
        sender = Objects.requireNonNull(consumer, "sender");
        return true;
    }

    /** Binds explicitly to a connection. Receiving a packet never establishes or switches this binding. */
    public static synchronized void bind(Object activeConnection) {
        if (activeConnection == connection) return;
        clearSession();
        connection = activeConnection;
        bumpRevision();
    }

    /** Requests a fresh server snapshot only for the world UUID announced on the current connection. */
    public static synchronized boolean requestSync(UUID expectedWorld) {
        if (expectedWorld == null || isZero(expectedWorld) || connection == null || sender == null) return false;
        expirePendingRequests(System.nanoTime());
        if (!expectedWorld.equals(expectedWorldId)) {
            areas = List.of();
            worldId = null;
            serverRevision = -1;
            available = false;
            administrator = false;
            lastFeedback = null;
            pendingRequests.clear();
            pendingSyncRequestId = null;
            assembler.reset();
            bumpRevision();
            expectedWorldId = expectedWorld;
        }
        if (expectedWorld.equals(worldId) && serverRevision >= 0) return true;
        if (pendingSyncRequestId != null) return true;
        long requestedRevision = serverRevision < 0 ? 0 : serverRevision;
        UUID requestId = UUID.randomUUID();
        return sendInternal(new AreaWire.Request(requestId, expectedWorld,
                requestedRevision, AreaWire.Operation.SYNC, List.of(), List.of()), true);
    }

    public static synchronized void apply(Object packetConnection, Object currentConnection,
                                          AreaWire.Snapshot snapshot) {
        if (snapshot == null || packetConnection == null || packetConnection != currentConnection
                || packetConnection != connection) return;
        if (expectedWorldId != null && !expectedWorldId.equals(snapshot.worldId())) return;
        if (worldId != null && worldId.equals(snapshot.worldId()) && snapshot.revision() < serverRevision) return;
        if (worldId != null && !worldId.equals(snapshot.worldId()) && expectedWorldId == null) return;
        expirePendingRequests(System.nanoTime());
        var assembled = assembler.accept(snapshot);
        if (assembled.isEmpty()) return;
        List<MapArea> complete = assembled.orElseThrow();
        if (complete.size() > AreaDocument.MAX_AREAS
                || complete.stream().anyMatch(area -> area.scope() != MapArea.Scope.SHARED)) return;
        try {
            new AreaDocument(snapshot.revision(), complete);
        } catch (IllegalArgumentException malformedCollection) {
            return;
        }
        areas = List.copyOf(complete);
        worldId = snapshot.worldId();
        expectedWorldId = snapshot.worldId();
        serverRevision = snapshot.revision();
        available = snapshot.available();
        administrator = snapshot.administrator();
        if (pendingSyncRequestId != null) {
            pendingRequests.remove(pendingSyncRequestId);
            pendingSyncRequestId = null;
        }
        bumpRevision();
    }

    public static synchronized void feedback(Object packetConnection, Object currentConnection,
                                             AreaWire.Feedback feedback) {
        if (feedback == null || packetConnection == null || packetConnection != currentConnection
                || packetConnection != connection) return;
        if (expectedWorldId != null && !expectedWorldId.equals(feedback.worldId())) return;
        if (worldId != null && !worldId.equals(feedback.worldId())) return;
        expirePendingRequests(System.nanoTime());
        if (pendingRequests.remove(feedback.requestId()) == null) return;
        if (feedback.requestId().equals(pendingSyncRequestId)) pendingSyncRequestId = null;
        lastFeedback = feedback;
        if (feedback.success() && feedback.revision() > serverRevision && feedback.revision() >= 0) {
            // The following server snapshot is authoritative; do not advance state before it arrives.
        }
        bumpRevision();
    }

    /** Clears remote state after logout while keeping the platform transport configured. */
    public static synchronized void reset() {
        clearSession();
        bumpRevision();
    }

    private static void clearSession() {
        connection = null;
        expectedWorldId = null;
        worldId = null;
        serverRevision = -1;
        available = false;
        administrator = false;
        areas = List.of();
        lastFeedback = null;
        pendingRequests.clear();
        pendingSyncRequestId = null;
        assembler.reset();
    }

    public static synchronized List<MapArea> areas() { return areas; }
    public static synchronized UUID worldId() { return worldId; }
    public static synchronized long serverRevision() { return serverRevision; }
    public static synchronized long revision() { return localRevision; }
    public static synchronized boolean available() { return available && worldId != null; }
    public static synchronized boolean canCreate() { return available(); }
    public static synchronized boolean administrator() { return administrator; }
    public static synchronized AreaWire.Feedback lastFeedback() { return lastFeedback; }

    public static synchronized boolean canManage(UUID actorId, MapArea area) {
        return available() && area != null && area.scope() == MapArea.Scope.SHARED
                && AreaTransactions.canManage(area, actorId, administrator);
    }

    public static synchronized boolean send(AreaWire.Request request) {
        if (request == null || request.operation() != AreaWire.Operation.MUTATE || !available()
                || worldId == null || !worldId.equals(request.worldId()) || request.expectedRevision() != serverRevision) return false;
        return sendInternal(request, false);
    }

    private static boolean sendInternal(AreaWire.Request request, boolean sync) {
        expirePendingRequests(System.nanoTime());
        if (connection == null || sender == null || pendingRequests.size() >= 32
                || pendingRequests.containsKey(request.requestId())
                || (!sync && (!available || request.operation() != AreaWire.Operation.MUTATE))) return false;
        pendingRequests.put(request.requestId(), System.nanoTime());
        if (sync) pendingSyncRequestId = request.requestId();
        try {
            sender.accept(request);
            return true;
        } catch (RuntimeException failedTransport) {
            pendingRequests.remove(request.requestId());
            if (request.requestId().equals(pendingSyncRequestId)) pendingSyncRequestId = null;
            return false;
        }
    }

    private static void expirePendingRequests(long now) {
        Iterator<Map.Entry<UUID, Long>> iterator = pendingRequests.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, Long> entry = iterator.next();
            if (now - entry.getValue() >= PENDING_TIMEOUT_NANOS) {
                if (entry.getKey().equals(pendingSyncRequestId)) pendingSyncRequestId = null;
                iterator.remove();
            }
        }
    }

    private static boolean isZero(UUID id) {
        return id.getMostSignificantBits() == 0L && id.getLeastSignificantBits() == 0L;
    }

    private static void bumpRevision() { localRevision = localRevision == Long.MAX_VALUE ? 0 : localRevision + 1; }
}
