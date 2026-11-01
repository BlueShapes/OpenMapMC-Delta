// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.network;

import net.aosankaku.openmapdelta.death.DeathPointInbox;
import net.aosankaku.openmapdelta.death.DeathPointUpdate;
import net.minecraft.client.Minecraft;

/** Connection-scoped client application kept out of dedicated-server bootstrap classes. */
final class NeoForgeDeathPointClientHandler {
    private NeoForgeDeathPointClientHandler() {}
    static void apply(DeathPointPayload payload, Object packetConnection) {
        var listener = Minecraft.getInstance().getConnection();
        var current = listener == null ? null : listener.getConnection();
        if (payload.data().length == 0) { DeathPointInbox.markSupported(packetConnection, current); return; }
        try { DeathPointInbox.apply(packetConnection, current, payload.report()); }
        catch (IllegalArgumentException malformed) { /* An invalid report leaves local deathpoints unchanged. */ }
    }
}
