// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.network;

import net.aosankaku.openmapdelta.client.ServerConfigClient;
import net.aosankaku.openmapdelta.server.ServerConfigSnapshot;
import net.minecraft.client.Minecraft;

/** Client-only bridge for the optional Forge server configuration channel. */
final class ForgeServerConfigClientHandler {
    private ForgeServerConfigClientHandler() {}

    static void apply(Object packetConnection, ServerConfigSnapshot snapshot) {
        var listener = Minecraft.getInstance().getConnection();
        Object currentConnection = listener == null ? null : listener.getConnection();
        ServerConfigClient.apply(packetConnection, currentConnection, snapshot);
    }
}
