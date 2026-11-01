// Copyright (c) 2026 AoSankaku. OpenMapMC Delta modifications; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.mixin;

import net.aosankaku.openmapdelta.client.SleepPointTracker;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public abstract class SleepSpawnMessageMixin {
    // RETURN runs after vanilla has moved packet handling to the client thread.
    @Inject(method = "handleSystemChat", at = @At("RETURN"))
    private void openmapdelta$recordSpawnConfirmation(ClientboundSystemChatPacket packet, CallbackInfo ci) {
        SleepPointTracker.onSystemMessage(packet.content());
    }
}
