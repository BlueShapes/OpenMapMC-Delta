// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.shared;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/** Client-side view of the server-owned shared waypoint collection. */
public final class SharedWaypointClient {
    private static Consumer<SharedWaypointRequest> sender;
    private static Object connection;
    private static UUID worldId;
    private static UUID pendingWorldId;
    private static long serverRevision = -1;
    private static long localRevision;
    private static boolean multiplayer;
    private static boolean administrator;
    private static List<SharedWaypoint> points = List.of();
    private static final SharedWaypointSnapshotAssembler assembler = new SharedWaypointSnapshotAssembler();

    private SharedWaypointClient() {}

    public static synchronized void configureSender(Consumer<SharedWaypointRequest> consumer) {
        sender = consumer;
    }

    /** Called on the client thread; a complete new revision replaces the current collection atomically. */
    public static synchronized void apply(Object packetConnection, Object currentConnection,
                                          SharedWaypointSnapshot snapshot) {
        if (snapshot == null || packetConnection == null || packetConnection != currentConnection) return;
        if (connection != packetConnection) {
            clearSession();
            connection = packetConnection;
        }
        if (assembler.isOlderThanPending(snapshot)) return;
        if (pendingWorldId != null && !pendingWorldId.equals(snapshot.worldId())) return;
        if (serverRevision >= 0 && snapshot.worldId().equals(worldId)) {
            if (snapshot.revision() < serverRevision) return;
            if (snapshot.revision() == serverRevision && snapshot.multiplayer() == multiplayer
                    && snapshot.administrator() == administrator) return;
        }

        if (worldId != null && !worldId.equals(snapshot.worldId())) {
            points = List.of();
            worldId = null;
            multiplayer = false;
            administrator = false;
            serverRevision = -1;
            localRevision++;
        }
        if (pendingWorldId == null) pendingWorldId = snapshot.worldId();
        var assembled = assembler.accept(snapshot);
        if (assembled.isEmpty()) return;
        SharedWaypointSnapshotAssembler.Complete complete = assembled.orElseThrow();
        points = complete.waypoints();
        worldId = complete.worldId();
        pendingWorldId = null;
        serverRevision = complete.revision();
        multiplayer = complete.multiplayer();
        administrator = complete.administrator();
        localRevision++;
    }

    public static synchronized void feedback(Object packetConnection, Object currentConnection,
                                             SharedWaypointFeedback feedback) {
        if (feedback == null || packetConnection == null || packetConnection != currentConnection
                || (connection != null && connection != packetConnection)) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) mc.player.displayClientMessage(Component.translatable(feedback.messageKey()), false);
    }

    /** Drops all server state on logout while retaining the configured platform transport. */
    public static synchronized void reset() {
        clearSession();
        localRevision++;
    }

    private static void clearSession() {
        connection = null;
        worldId = null;
        pendingWorldId = null;
        serverRevision = -1;
        multiplayer = false;
        administrator = false;
        points = List.of();
        assembler.reset();
    }

    public static synchronized boolean canCreate() { return connection != null && multiplayer; }

    public static synchronized boolean canManage(UUID ownerId) {
        Minecraft mc = Minecraft.getInstance();
        return ownerId != null && (administrator || (mc.player != null && ownerId.equals(mc.player.getUUID())));
    }

    public static synchronized List<SharedWaypoint> points() { return points; }
    public static synchronized UUID worldId() { return worldId; }
    public static synchronized long revision() { return localRevision; }
    public static synchronized boolean administrator() { return administrator; }

    /** Queues a request only while a supported multiplayer session is active. */
    public static synchronized boolean send(SharedWaypointRequest request) {
        if (request == null || connection == null || sender == null) return false;
        if (request.operation() == SharedWaypointRequest.Operation.CREATE && !multiplayer) return false;
        try {
            sender.accept(request);
            return true;
        } catch (RuntimeException exception) {
            return false;
        }
    }
}
