// Copyright (c) 2026 AoSankaku. OpenMapMC Delta modifications; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.mixin;

import net.aosankaku.openmapdelta.client.SleepPointTracker;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MultiPlayerGameMode.class)
public abstract class SleepBedInteractionMixin {
    @Inject(method = "useItemOn", at = @At("HEAD"))
    private void openmapdelta$captureSleepInteraction(LocalPlayer player, InteractionHand hand,
                                                      BlockHitResult hit, CallbackInfoReturnable<InteractionResult> ci) {
        SleepPointTracker.onBlockInteraction(hit.getBlockPos(), hand);
    }
}
