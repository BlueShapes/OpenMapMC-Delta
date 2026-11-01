// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

import java.util.UUID;

public final class RenderUtil {
    private static final Identifier VANILLA_PLAYER_POINTER_TEXTURE =
            Identifier.parse("minecraft:textures/map/decorations/player.png");

    private RenderUtil() {}

    public static void drawCircleBorder(GuiGraphicsExtractor graphics, int x, int y, int size, int thickness, int color) {
        float centerX = x + size / 2.0f;
        float centerY = y + size / 2.0f;
        float outer = size / 2.0f + thickness;
        float inner = size / 2.0f;
        for (int angle = 0; angle < 360; angle += 2) {
            double rad = Math.toRadians(angle);
            float ox = centerX + (float) (Math.cos(rad) * outer);
            float oy = centerY + (float) (Math.sin(rad) * outer);
            graphics.fill((int) ox, (int) oy, (int) ox + 1, (int) oy + 1, color);
        }
    }

    /** Rasterized annulus: every scanline is filled, including the diagonal edges. */
    public static void drawSolidCircleBorder(GuiGraphicsExtractor graphics, int x, int y, int size, int thickness, int color) {
        double center = size / 2.0;
        double outer = center + thickness;
        for (int row = -thickness; row < size + thickness; row++) {
            double dy = row + 0.5 - center;
            double outerSpan = Math.sqrt(Math.max(0, outer * outer - dy * dy));
            int left = (int) Math.ceil(center - outerSpan - 0.5);
            int right = (int) Math.floor(center + outerSpan - 0.5) + 1;
            if (Math.abs(dy) >= center) {
                graphics.fill(x + left, y + row, x + right, y + row + 1, color);
            } else {
                double innerSpan = Math.sqrt(center * center - dy * dy);
                int innerLeft = (int) Math.ceil(center - innerSpan - 0.5);
                int innerRight = (int) Math.floor(center + innerSpan - 0.5) + 1;
                graphics.fill(x + left, y + row, x + innerLeft, y + row + 1, color);
                graphics.fill(x + innerRight, y + row, x + right, y + row + 1, color);
            }
        }
    }
    public static void drawMarkerDot(GuiGraphicsExtractor graphics, int x, int y, int color, int size) {
        graphics.fill(x - size / 2, y - size / 2, x + size / 2, y + size / 2, color);
    }

    /** Outlined north-pointing triangle; rotation follows the map's screen coordinates. */
    public static void drawPlayerArrow(GuiGraphicsExtractor graphics, int x, int y, int size, float rotationDegrees) {
        graphics.pose().pushMatrix();
        try {
            graphics.pose().translate(x, y);
            graphics.pose().rotate((float) Math.toRadians(rotationDegrees));
            float scale = size / 14.0f;
            // Keep a 3:2 height-to-width ratio for a readable directional silhouette.
            graphics.pose().scale(scale * (2.0f / 3.0f), scale);
            for (int row = 0; row < 14; row++) {
                int halfWidth = (row + 2) / 2;
                graphics.fill(-halfWidth, row - 7, halfWidth, row - 6, 0xFF000000);
                if (row >= 2 && row < 13) {
                    int innerHalfWidth = halfWidth - 1;
                    graphics.fill(-innerHalfWidth, row - 7, innerHalfWidth, row - 6, 0xFFFFFFFF);
                }
            }
        } finally {
            graphics.pose().popMatrix();
        }
    }

    /** Draws vanilla's monochrome player cursor. */
    public static void drawVanillaPlayerPointer(GuiGraphicsExtractor graphics, int x, int y, int size,
                                                float rotationDegrees) {
        drawVanillaPlayerPointer(graphics, x, y, size, rotationDegrees, 0xFFFFFFFF);
    }

    /** Draws vanilla's monochrome player cursor with a per-player tint. */
    public static void drawVanillaPlayerPointer(GuiGraphicsExtractor graphics, int x, int y, int size,
                                                float rotationDegrees, int color) {
        float scale = size / 8.0f;
        graphics.pose().pushMatrix();
        try {
            graphics.pose().translate(x, y);
            graphics.pose().rotate((float) Math.toRadians(rotationDegrees));
            graphics.pose().scale(scale, scale);
            // The sprite has transparent padding that shifts its visible pixel-edge center by (+0.5, -0.5).
            graphics.pose().translate(-0.5f, 0.5f);
            graphics.blit(RenderPipelines.GUI_TEXTURED, VANILLA_PLAYER_POINTER_TEXTURE,
                    -4, -4, 0.0f, 0.0f, 8, 8, 8, 8, color);
        } finally {
            graphics.pose().popMatrix();
        }
    }

    public static void drawPlayerHead(GuiGraphicsExtractor graphics, int x, int y, int size) {
        drawPlayerHead(graphics, x, y, size, 0.0f);
    }

    public static void drawPlayerHead(GuiGraphicsExtractor graphics, int x, int y, int size, float rotationDegrees) {
        LocalPlayer lp = Minecraft.getInstance().player;
        if (lp == null) {
            drawMarkerDot(graphics, x, y, 0xFFFFFFFF, 5);
            return;
        }
        drawPlayerHead(graphics, lp.getUUID(), lp.getGameProfile().name(), x, y, size,
                rotationDegrees, 0xFFFFFFFF);
    }

    /** Draws the cached skin face for any player, including remote players. */
    public static void drawPlayerHead(GuiGraphicsExtractor graphics, UUID playerId, String playerName,
                                      int x, int y, int size, float rotationDegrees, int fallbackColor) {
        if (playerId == null) {
            drawPlayerHeadFallback(graphics, x, y, size, rotationDegrees, fallbackColor);
            return;
        }
        Identifier tex;
        try {
            tex = net.aosankaku.openmapdelta.render.WaypointOwnerFace.texture(playerId, playerName);
            if (tex == null) {
                drawPlayerHeadFallback(graphics, x, y, size, rotationDegrees, fallbackColor);
                return;
            }
        } catch (Exception e) {
            drawPlayerHeadFallback(graphics, x, y, size, rotationDegrees, fallbackColor);
            return;
        }
        float scale = size / 8.0f;
        boolean rendered = false;
        graphics.pose().pushMatrix();
        try {
            graphics.pose().translate(x, y);
            graphics.pose().rotate((float) Math.toRadians(rotationDegrees));
            graphics.pose().scale(scale, scale);
            graphics.blit(RenderPipelines.GUI_TEXTURED, tex, -4, -4, 8f, 8f, 8, 8, 8, 8, 64, 64);
            graphics.blit(RenderPipelines.GUI_TEXTURED, tex, -4, -4, 40f, 8f, 8, 8, 8, 8, 64, 64);
            rendered = true;
        } catch (Exception ignored) {
            // Draw the fallback after restoring the pose below.
        } finally {
            graphics.pose().popMatrix();
        }
        if (!rendered) drawPlayerHeadFallback(graphics, x, y, size, rotationDegrees, fallbackColor);
    }

    private static void drawPlayerHeadFallback(GuiGraphicsExtractor graphics, int x, int y, int size,
                                               float rotationDegrees, int color) {
        graphics.pose().pushMatrix();
        try {
            graphics.pose().translate(x, y);
            graphics.pose().rotate((float) Math.toRadians(rotationDegrees));
            graphics.pose().scale(size, size);
            graphics.pose().translate(-0.5f, -0.5f);
            graphics.fill(0, 0, 1, 1, color);
        } finally {
            graphics.pose().popMatrix();
        }
    }
}
