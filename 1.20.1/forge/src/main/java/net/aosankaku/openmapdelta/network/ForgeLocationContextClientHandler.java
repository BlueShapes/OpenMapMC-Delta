// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.network;

import net.aosankaku.openmapdelta.client.LocationContextClient;
import net.aosankaku.openmapdelta.network.LocationContextResponse;
import net.minecraft.client.Minecraft;

/** Client-only response application, scoped to the connection that delivered the packet. */
final class ForgeLocationContextClientHandler {
    private ForgeLocationContextClientHandler() {}

    static void apply(LocationContextResponse response, Object packetConnection) {
        var listener = Minecraft.getInstance().getConnection();
        Object current = listener == null ? null : listener.getConnection();
        LocationContextClient.apply(packetConnection, current, response);
    }
}
