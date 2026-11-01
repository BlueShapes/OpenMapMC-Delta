// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.mixin;

import net.aosankaku.openmapdelta.server.DeathRecoveryServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.ArrayDeque;

@Mixin(ItemEntity.class)
public abstract class ItemEntityRecoveryMixin {
    @Unique private int odysseymap$beforePickup;
    @Unique private static final ThreadLocal<ArrayDeque<Integer>> odysseymap$mergeCounts = ThreadLocal.withInitial(ArrayDeque::new);

    @Inject(method = "playerTouch", at = @At("HEAD"))
    private void odysseymap$beforePickup(Player player, CallbackInfo callback) {
        odysseymap$beforePickup = ((ItemEntity) (Object) this).getItem().getCount();
    }

    @Inject(method = "playerTouch", at = @At("RETURN"))
    private void odysseymap$afterPickup(Player player, CallbackInfo callback) {
        if (!(player instanceof ServerPlayer serverPlayer)) return;
        var item = (ItemEntity) (Object) this;
        // Vanilla restores the stack's original count after discarding a fully collected entity.
        int amount = item.isRemoved() ? odysseymap$beforePickup : odysseymap$beforePickup - item.getItem().getCount();
        if (amount > 0) DeathRecoveryServer.pickedUp(item, serverPlayer, amount, odysseymap$beforePickup);
    }

    @Inject(method = "merge(Lnet/minecraft/world/entity/item/ItemEntity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/item/ItemEntity;Lnet/minecraft/world/item/ItemStack;)V", at = @At("HEAD"))
    private static void odysseymap$beforeMerge(ItemEntity destination, ItemStack destinationStack,
            ItemEntity origin, ItemStack originStack, CallbackInfo callback) {
        odysseymap$mergeCounts.get().push(originStack.getCount());
    }

    @Inject(method = "merge(Lnet/minecraft/world/entity/item/ItemEntity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/item/ItemEntity;Lnet/minecraft/world/item/ItemStack;)V", at = @At("RETURN"))
    private static void odysseymap$afterMerge(ItemEntity destination, ItemStack destinationStack,
            ItemEntity origin, ItemStack originStack, CallbackInfo callback) {
        var counts = odysseymap$mergeCounts.get();
        int beforeTotal = counts.pop();
        int amount = beforeTotal - originStack.getCount();
        if (counts.isEmpty()) odysseymap$mergeCounts.remove();
        if (amount > 0) DeathRecoveryServer.merged(destination, origin, amount, beforeTotal);
    }
}
