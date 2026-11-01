// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.network;

import net.aosankaku.openmapdelta.death.DeathPointInbox;
import net.aosankaku.openmapdelta.death.DeathPointUpdate;
import net.minecraft.network.Connection;

/** Applies owner-only death reports only while their originating connection is current. */
public final class ForgeDeathPointClientHandler {
    private ForgeDeathPointClientHandler() {}

    public static void apply(byte[] bytes, Connection packetConnection) {
        var client = net.minecraft.client.Minecraft.getInstance();
        var currentHandler = client.getConnection();
        var currentConnection = currentHandler == null ? null : currentHandler.getConnection();
        if (bytes == null || bytes.length == 0) {
            DeathPointInbox.markSupported(packetConnection, currentConnection);
            return;
        }
        try {
            DeathPointInbox.apply(packetConnection, currentConnection, DeathPointUpdate.decode(bytes));
        } catch (IllegalArgumentException malformed) {
            // Invalid owner reports never alter the current deathpoint state.
        }
    }
}
