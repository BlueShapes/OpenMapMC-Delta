// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.mixin;

import net.aosankaku.openmapdelta.client.ClientEvents;
import net.aosankaku.openmapdelta.world.OpenMapDeltaClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public class MixinMinecraft {

    @Inject(method = "setLevel", at = @At("HEAD"))
    private void onSetLevel(ClientLevel level, CallbackInfo ci) {
        if (level == null && OpenMapDeltaClient.getTileCache() != null) {
            ClientEvents events = new ClientEvents();
            events.onLevelUnload();
        }
    }
}
