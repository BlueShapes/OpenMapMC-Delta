// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.mixin;

import net.aosankaku.openmapdelta.server.DeathRecoveryServer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Optional integration: Corpse spills its remaining inventory synchronously from remove(). */
@Pseudo
@Mixin(targets = "de.maxhenkel.corpse.entities.CorpseEntity")
public abstract class CorpseRemovalMixin extends Entity {
    protected CorpseRemovalMixin(EntityType<?> type, Level level) {
        super(type, level);
    }

    @Inject(method = "remove(Lnet/minecraft/world/entity/Entity$RemovalReason;)V", at = @At("HEAD"), require = 0)
    private void odysseymap$beginCorpseRemoval(RemovalReason reason, CallbackInfo callback) {
        DeathRecoveryServer.beginCorpseRemoval((Entity) (Object) this);
    }

    @Inject(method = "remove(Lnet/minecraft/world/entity/Entity$RemovalReason;)V", at = @At("RETURN"), require = 0)
    private void odysseymap$endCorpseRemoval(RemovalReason reason, CallbackInfo callback) {
        DeathRecoveryServer.endCorpseRemoval((Entity) (Object) this);
    }
}
