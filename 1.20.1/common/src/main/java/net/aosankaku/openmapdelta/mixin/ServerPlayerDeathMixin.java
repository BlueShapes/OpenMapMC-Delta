// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.mixin;

import net.aosankaku.openmapdelta.server.DeathRecoveryServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayer.class)
public abstract class ServerPlayerDeathMixin {
    @Inject(method = "die", at = @At("HEAD"))
    private void odysseymap$beginDeath(DamageSource source, CallbackInfo callback) {
        DeathRecoveryServer.beginDeath((ServerPlayer) (Object) this);
    }

    @Inject(method = "die", at = @At("RETURN"))
    private void odysseymap$finishDeath(DamageSource source, CallbackInfo callback) {
        DeathRecoveryServer.finishDeath((ServerPlayer) (Object) this);
    }
}
