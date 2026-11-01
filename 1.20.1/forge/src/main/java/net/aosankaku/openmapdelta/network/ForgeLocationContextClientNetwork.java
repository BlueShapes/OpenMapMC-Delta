// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.network;

import net.aosankaku.openmapdelta.client.LocationContextClient;
import net.minecraft.client.Minecraft;

/** Registers the optional request sender on the physical client only. */
public final class ForgeLocationContextClientNetwork {
    private ForgeLocationContextClientNetwork() {}

    public static void init() {
        LocationContextClient.configureSender(request -> {
            var listener = Minecraft.getInstance().getConnection();
            var connection = listener == null ? null : listener.getConnection();
            if (!ForgeNetwork.canSendLocationContext(connection)) return false;
            ForgeNetwork.sendLocationContextRequest(request);
            return true;
        });
    }
}
