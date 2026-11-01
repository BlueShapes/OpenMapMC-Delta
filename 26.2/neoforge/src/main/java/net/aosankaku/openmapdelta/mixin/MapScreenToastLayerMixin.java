// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalBooleanRef;
import net.aosankaku.openmapdelta.gui.FullscreenMapScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.toasts.ToastManager;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

import java.util.Stack;

@Mixin(Gui.class)
public class MapScreenToastLayerMixin {
    @Shadow @Final private Minecraft minecraft;

    @WrapOperation(method = "extractRenderState", at = @At(value = "INVOKE",
            target = "Lnet/neoforged/neoforge/client/ClientHooks;extractScreen(Lnet/minecraft/client/gui/screens/Screen;Ljava/util/Stack;"
                    + "Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V"), require = 1)
    private void openmapdelta$toastsBehindMap(Screen screen, Stack<Screen> layers, GuiGraphicsExtractor graphics,
                                             int mouseX, int mouseY, float partialTick, Operation<Void> original,
                                             @Share("mapToastsExtracted") LocalBooleanRef toastsExtracted) {
        if (screen instanceof FullscreenMapScreen) {
            // Keep toast updates and vanilla screen strata; only move this frame's extraction.
            minecraft.gui.toastManager().extractRenderState(graphics);
            toastsExtracted.set(true);
        }
        original.call(screen, layers, graphics, mouseX, mouseY, partialTick);
    }

    @WrapOperation(method = "extractRenderState", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/components/toasts/ToastManager;extractRenderState"
                    + "(Lnet/minecraft/client/gui/GuiGraphicsExtractor;)V"), require = 1)
    private void openmapdelta$remainingToasts(ToastManager toasts, GuiGraphicsExtractor graphics,
                                             Operation<Void> original,
                                             @Share("mapToastsExtracted") LocalBooleanRef toastsExtracted) {
        if (!toastsExtracted.get()) {
            original.call(toasts, graphics);
        }
    }
}
