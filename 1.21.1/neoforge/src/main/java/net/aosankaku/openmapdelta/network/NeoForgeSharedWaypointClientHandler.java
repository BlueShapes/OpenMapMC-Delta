// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.network;

import net.aosankaku.openmapdelta.shared.SharedWaypointClient;
import net.minecraft.client.Minecraft;

/** Client-only state application, guarded by the connection that received the packet. */
final class NeoForgeSharedWaypointClientHandler {
    private NeoForgeSharedWaypointClientHandler() {}

    static void apply(SharedWaypointPayload payload, Object packetConnection) {
        var listener = Minecraft.getInstance().getConnection();
        Object current = listener == null ? null : listener.getConnection();
        if (payload.kind() == SharedWaypointPayload.SNAPSHOT) {
            SharedWaypointClient.apply(packetConnection, current, payload.snapshot());
        } else if (payload.kind() == SharedWaypointPayload.FEEDBACK) {
            SharedWaypointClient.feedback(packetConnection, current, payload.feedback());
        }
    }
}
