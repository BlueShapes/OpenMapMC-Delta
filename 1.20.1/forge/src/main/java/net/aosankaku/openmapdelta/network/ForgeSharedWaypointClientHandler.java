// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.network;

import net.aosankaku.openmapdelta.shared.SharedWaypointClient;
import net.aosankaku.openmapdelta.shared.SharedWaypointFeedback;
import net.aosankaku.openmapdelta.shared.SharedWaypointSnapshot;
import net.minecraft.client.Minecraft;

/** Client-only packet application stays out of the server bootstrap class. */
final class ForgeSharedWaypointClientHandler {
    private ForgeSharedWaypointClientHandler() {}

    static void applySnapshot(SharedWaypointSnapshot snapshot, Object packetConnection) {
        var listener = Minecraft.getInstance().getConnection();
        Object current = listener == null ? null : listener.getConnection();
        SharedWaypointClient.apply(packetConnection, current, snapshot);
    }

    static void applyFeedback(SharedWaypointFeedback feedback, Object packetConnection) {
        var listener = Minecraft.getInstance().getConnection();
        Object current = listener == null ? null : listener.getConnection();
        SharedWaypointClient.feedback(packetConnection, current, feedback);
    }
}
