// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.mixin;

import net.aosankaku.openmapdelta.client.LocationSharing;
import net.aosankaku.openmapdelta.client.MinimapInfoProvider;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundSetTimePacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public abstract class ServerTimeMixin {
    @Inject(method = "handleSetTime", at = @At("TAIL"))
    private void odysseymap$observeServerTime(ClientboundSetTimePacket packet, CallbackInfo callback) {
        LocationSharing.observeServerTime(packet.gameTime());
        MinimapInfoProvider.observeServerTime(packet.gameTime());
    }
}
