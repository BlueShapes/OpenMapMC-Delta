// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.network;

import net.minecraft.client.Minecraft;

/** Client-only work reached only when the clientbound identity packet is received. */
final class ForgeWorldIdentityClientHandler {
    private ForgeWorldIdentityClientHandler() {}

    static void apply(WorldIdentityMessage message, Object packetConnectionToken) {
        var listener = Minecraft.getInstance().getConnection();
        Object currentConnectionToken = listener == null ? null : listener.getConnection();
        ClientWorldIdentityState.apply(packetConnectionToken, currentConnectionToken, message.worldId());
    }
}
