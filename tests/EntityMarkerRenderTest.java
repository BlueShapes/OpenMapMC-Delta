// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.marker;

import net.minecraft.client.gui.GuiGraphics;
import com.mojang.blaze3d.systems.RenderSystem;

public final class EntityMarkerRenderTest {
    private static int assertions;

    public static void main(String[] args) {
        drawsCroppedMobSpriteWithAlpha();
        unknownIconsUseAnAlphaAwareFallback();
        failuresRestoreDrawingState();
        System.out.println("Entity marker render tests passed (" + assertions + " assertions).");
    }

    private static void drawsCroppedMobSpriteWithAlpha() {
        RenderSystem.setShaderColor(1, 1, 1, 1);
        GuiGraphics graphics = EntityMarkerRenderFixture.createGraphics();
        Marker mob = new Marker("mob:minecraft:zombie", 0xFF123456);
        EntityMarkerRenderFixture.drawEntity(graphics, mob, 30, 40, 5, 153);

        check(graphics.blitCount == 1, "known mob icon draws a cropped sprite");
        check(graphics.lastLeft == 28 && graphics.lastTop == 38, "sprite is centered on the map position");
        check(graphics.lastWidth == 8 && graphics.lastHeight == 8, "sprite uses the mob face dimensions");
        check(graphics.lastU == 8 && graphics.lastV == 8, "sprite uses the mob face crop origin");
        check(graphics.lastTextureWidth == 64 && graphics.lastTextureHeight == 64,
                "sprite uses its complete texture dimensions for cropping");
        check(graphics.lastAlpha == 153, "sprite receives the selected opacity");
        check(graphics.pose().depth == 0, "pose is restored after drawing a mob icon");

        Marker symbol = new Marker("symbol:test:opaque", 0xFF123456);
        EntityMarkerRenderFixture.drawOrdinary(graphics, symbol, 50, 60, 8, false);
        check(graphics.lastAlpha == 255, "following ordinary markers remain fully opaque");
        check(graphics.pose().depth == 0, "following ordinary marker pose is restored");
    }

    private static void unknownIconsUseAnAlphaAwareFallback() {
        GuiGraphics graphics = EntityMarkerRenderFixture.createGraphics();
        Marker mob = new Marker("mob:example:unregistered", 0xFF336699);
        EntityMarkerRenderFixture.drawEntity(graphics, mob, 7, 9, 4, 102);

        check(graphics.fillCount == 1, "missing mob texture falls back to a simple marker");
        check(graphics.lastFillColor == 0x66336699,
                "fallback marker retains its tint and receives the selected alpha");
    }

    private static void failuresRestoreDrawingState() {
        RenderSystem.setShaderColor(0.8F, 0.7F, 0.6F, 0.5F);
        GuiGraphics graphics = EntityMarkerRenderFixture.createGraphics();
        graphics.throwOnBlit = true;
        boolean threw = false;
        try {
            EntityMarkerRenderFixture.drawEntity(graphics,
                    new Marker("mob:minecraft:zombie", 0xFFFFFFFF), 0, 0, 5, 51);
        } catch (RuntimeException expected) {
            threw = true;
        }
        check(threw, "sprite rendering failure is observable to the caller");
        float[] restored = RenderSystem.getShaderColor();
        check(restored[0] == 0.8F && restored[1] == 0.7F && restored[2] == 0.6F && restored[3] == 0.5F,
                "sprite rendering failure restores the previous shader color");
        check(graphics.pose().depth == 0, "sprite rendering failure restores the pose stack");
    }

    private static void check(boolean condition, String description) {
        assertions++;
        if (!condition) throw new AssertionError(description);
    }
}
