// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.mixin;

import net.aosankaku.openmapdelta.render.MinimapHudRenderer;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
public class MixinGui {

    @Shadow
    @Final
    private Minecraft minecraft;

    @Shadow
    @Final
    private GuiRenderState guiRenderState;

    // Toasts start their own stratum, so extract the whole minimap before them.
    @Inject(
            method = "extractRenderState",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/components/toasts/ToastManager;extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;)V",
                    shift = At.Shift.BEFORE
            )
    )
    private void odysseymap$renderMinimap(
            DeltaTracker deltaTracker,
            boolean shouldRenderLevel,
            boolean resourcesLoaded,
            CallbackInfo ci
    ) {
        if (!shouldRenderLevel) return;
        if (this.minecraft.getWindow() == null) return;

        int xMouse = (int) this.minecraft.mouseHandler.getScaledXPos(this.minecraft.getWindow());
        int yMouse = (int) this.minecraft.mouseHandler.getScaledYPos(this.minecraft.getWindow());
        GuiGraphicsExtractor graphics = new GuiGraphicsExtractor(this.minecraft, this.guiRenderState, xMouse, yMouse);

        float partialTick = deltaTracker.getGameTimeDeltaPartialTick(false);
        MinimapHudRenderer.render(
                graphics,
                partialTick,
                this.minecraft.getWindow().getGuiScaledWidth(),
                this.minecraft.getWindow().getGuiScaledHeight()
        );
    }
}
