// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.mixin;

import net.aosankaku.openmapdelta.marker.WaypointImportManager;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import net.minecraft.client.multiplayer.ClientPacketListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Handles only Odyssey's local import action; it never sends the action to a server. */
@Mixin(ClientPacketListener.class)
public abstract class MixinWaypointImportCommand {
    @Inject(method = "sendUnsignedCommand", at = @At("HEAD"), cancellable = true)
    private void odysseymap$importClick(String command, CallbackInfoReturnable<Boolean> ci) {
        if (WaypointImportManager.get().requestOpen(command)) ci.setReturnValue(true);
    }

    @Inject(method = "sendCommand", at = @At("HEAD"), cancellable = true)
    private void odysseymap$importCommand(String command, CallbackInfo ci) {
        if (WaypointImportManager.get().requestOpen(command)) ci.cancel();
    }
}
