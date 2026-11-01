// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.mixin;

import net.aosankaku.openmapdelta.server.DeathRecoveryServer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// ServerPlayer's override delegates to the LivingEntity implementation; hook its returned entity there.
@Mixin(LivingEntity.class)
public abstract class PlayerDeathDropMixin {
    @Inject(method = "drop(Lnet/minecraft/world/item/ItemStack;ZZ)Lnet/minecraft/world/entity/item/ItemEntity;", at = @At("RETURN"))
    private void odysseymap$trackDeathDrop(ItemStack stack, boolean scatter, boolean retainOwnership,
            CallbackInfoReturnable<ItemEntity> callback) {
        if ((Object) this instanceof ServerPlayer player) DeathRecoveryServer.recordDrop(player, callback.getReturnValue());
    }
}
