// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.network;

import net.minecraft.client.Minecraft;

/** Client-only work reached only when the clientbound identity payload is received. */
final class NeoForgeWorldIdentityClientHandler {
    private NeoForgeWorldIdentityClientHandler() {}

    static void apply(WorldIdentityPayload payload, Object packetConnectionToken) {
        var listener = Minecraft.getInstance().getConnection();
        Object currentConnectionToken = listener == null ? null : listener.getConnection();
        ClientWorldIdentityState.apply(packetConnectionToken, currentConnectionToken, payload.worldId());
    }
}
