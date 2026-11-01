// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.network;

import net.aosankaku.openmapdelta.client.LocationContextClient;
import net.minecraft.client.Minecraft;

/** Applies a response only to the connection that delivered it. */
final class NeoForgeLocationContextClientHandler {
    private NeoForgeLocationContextClientHandler() {}

    static void apply(LocationContextPayload payload, Object packetConnection) {
        if (payload.kind() != LocationContextPayload.RESPONSE) return;
        var listener = Minecraft.getInstance().getConnection();
        Object current = listener == null ? null : listener.getConnection();
        try {
            LocationContextClient.apply(packetConnection, current, payload.response());
        } catch (IllegalArgumentException malformed) {
            // Invalid replies leave the pending notification unchanged.
        }
    }
}
