// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.mixin;

import net.aosankaku.openmapdelta.server.DeathRecoveryServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Tracks only entities accepted by the level, including items spilled by an optional Corpse body. */
@Mixin(ServerLevel.class)
public abstract class ServerLevelDeathDropMixin {
    @Inject(method = "addFreshEntity(Lnet/minecraft/world/entity/Entity;)Z", at = @At("RETURN"))
    private void odysseymap$trackAddedDeathEntity(Entity entity, CallbackInfoReturnable<Boolean> callback) {
        if (Boolean.TRUE.equals(callback.getReturnValue()))
            DeathRecoveryServer.entityAdded((ServerLevel) (Object) this, entity);
    }
}
