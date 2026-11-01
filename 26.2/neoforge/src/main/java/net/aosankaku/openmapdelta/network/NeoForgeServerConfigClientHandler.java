// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.network;

import net.aosankaku.openmapdelta.client.ServerConfigClient;
import net.aosankaku.openmapdelta.server.ServerConfigSnapshot;
import net.minecraft.client.Minecraft;

/** Client-only connection guard for server configuration snapshots. */
final class NeoForgeServerConfigClientHandler {
    private NeoForgeServerConfigClientHandler() {}

    static void apply(ServerConfigSnapshotPayload payload, Object packetConnection) {
        var listener = Minecraft.getInstance().getConnection();
        Object currentConnection = listener == null ? null : listener.getConnection();
        try {
            ServerConfigClient.apply(packetConnection, currentConnection,
                    ServerConfigSnapshot.decode(payload.data()));
        } catch (IllegalArgumentException malformed) {
            // Invalid snapshots never alter the active client state.
        }
    }
}
