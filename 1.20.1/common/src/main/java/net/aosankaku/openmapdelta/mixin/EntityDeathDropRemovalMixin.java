// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.mixin;

import net.aosankaku.openmapdelta.death.CorpseCompat;
import net.aosankaku.openmapdelta.server.DeathRecoveryServer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Captures the final location and removal kind before an entity leaves its loaded level. */
@Mixin(Entity.class)
public abstract class EntityDeathDropRemovalMixin {
    @Inject(method = "setRemoved(Lnet/minecraft/world/entity/Entity$RemovalReason;)V", at = @At("HEAD"))
    private void odysseymap$recordDeathEntityRemoval(Entity.RemovalReason reason, CallbackInfo callback) {
        Entity entity = (Entity) (Object) this;
        if (entity instanceof ItemEntity || CorpseCompat.isCorpse(entity))
            DeathRecoveryServer.entityRemoved(entity, reason);
    }
}
