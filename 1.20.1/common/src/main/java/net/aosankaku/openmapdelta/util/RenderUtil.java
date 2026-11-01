// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.util;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

public final class RenderUtil {
    private static final ResourceLocation VANILLA_PLAYER_POINTER_TEXTURE =
            new ResourceLocation("minecraft", "textures/map/map_icons.png");

    private RenderUtil() {}

    public static void drawCircleBorder(GuiGraphics graphics, int x, int y, int size, int thickness, int color) {
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
    public static void drawSolidCircleBorder(GuiGraphics graphics, int x, int y, int size, int thickness, int color) {
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
    public static void drawMarkerDot(GuiGraphics graphics, int x, int y, int color, int size) {
        graphics.fill(x - size / 2, y - size / 2, x + size / 2, y + size / 2, color);
    }

    /** Outlined north-pointing triangle; rotation follows the map's screen coordinates. */
    public static void drawPlayerArrow(GuiGraphics graphics, int x, int y, int size, float rotationDegrees) {
        graphics.pose().pushPose();
        try {
            graphics.pose().translate(x, y, 0);
            graphics.pose().mulPose(com.mojang.math.Axis.ZP.rotationDegrees(rotationDegrees));
            float scale = size / 14.0f;
            // Keep a 3:2 height-to-width ratio for a readable directional silhouette.
            graphics.pose().scale(scale * (2.0f / 3.0f), scale, 1);
            for (int row = 0; row < 14; row++) {
                int halfWidth = (row + 2) / 2;
                graphics.fill(-halfWidth, row - 7, halfWidth, row - 6, 0xFF000000);
                if (row >= 2 && row < 13) {
                    int innerHalfWidth = halfWidth - 1;
                    graphics.fill(-innerHalfWidth, row - 7, innerHalfWidth, row - 6, 0xFFFFFFFF);
                }
            }
        } finally {
            graphics.pose().popPose();
        }
    }

    /** Draws vanilla's monochrome player cursor. */
    public static void drawVanillaPlayerPointer(GuiGraphics graphics, int x, int y, int size,
                                                float rotationDegrees) {
        drawVanillaPlayerPointer(graphics, x, y, size, rotationDegrees, 0xFFFFFFFF);
    }

    /** Draws vanilla's monochrome player cursor with a per-player tint. */
    public static void drawVanillaPlayerPointer(GuiGraphics graphics, int x, int y, int size,
                                                float rotationDegrees, int color) {
        float scale = size / 8.0f;
        float[] previousColor = RenderSystem.getShaderColor().clone();
        graphics.pose().pushPose();
        try {
            graphics.flush();
            RenderSystem.enableBlend();
            RenderSystem.setShaderTexture(0, VANILLA_PLAYER_POINTER_TEXTURE);
            RenderSystem.setShaderColor(previousColor[0] * ((color >> 16 & 0xFF) / 255.0f),
                    previousColor[1] * ((color >> 8 & 0xFF) / 255.0f),
                    previousColor[2] * ((color & 0xFF) / 255.0f),
                    previousColor[3] * ((color >>> 24) / 255.0f));
            graphics.pose().translate(x, y, 0);
            graphics.pose().mulPose(com.mojang.math.Axis.ZP.rotationDegrees(rotationDegrees));
            graphics.pose().scale(scale, scale, 1);
            // The sprite has transparent padding that shifts its visible pixel-edge center by (+0.5, -0.5).
            graphics.pose().translate(-0.5f, 0.5f, 0);
            graphics.blit(VANILLA_PLAYER_POINTER_TEXTURE, -4, -4, 8, 8,
                    0.0f, 0.0f, 8, 8, 128, 128);
        } finally {
            graphics.pose().popPose();
            graphics.flush();
            RenderSystem.setShaderColor(previousColor[0], previousColor[1], previousColor[2], previousColor[3]);
        }
    }

    public static void drawPlayerHead(GuiGraphics graphics, int x, int y, int size) {
        drawPlayerHead(graphics, x, y, size, 0.0f);
    }

    public static void drawPlayerHead(GuiGraphics graphics, int x, int y, int size, float rotationDegrees) {
        LocalPlayer lp = Minecraft.getInstance().player;
        if (lp == null) { drawMarkerDot(graphics, x, y, 0xFFFFFFFF, 5); return; }
        drawPlayerHead(graphics, lp.getUUID(), lp.getGameProfile().getName(), x, y, size,
                rotationDegrees, 0xFFFFFFFF);
    }

    /** Draws the cached skin face for any player, including remote players. */
    public static void drawPlayerHead(GuiGraphics graphics, UUID playerId, String playerName,
                                      int x, int y, int size, float rotationDegrees, int fallbackColor) {
        if (playerId == null) { drawMarkerDot(graphics, x, y, fallbackColor, size); return; }
        ResourceLocation tex;
        try {
            tex = net.aosankaku.openmapdelta.render.WaypointOwnerFace.texture(playerId, playerName);
            if (tex == null) {
                drawMarkerDot(graphics, x, y, fallbackColor, size);
                return;
            }
        } catch (Exception e) {
            drawMarkerDot(graphics, x, y, fallbackColor, size);
            return;
        }
        float scale = size / 8.0f;
        boolean rendered = false;
        graphics.pose().pushPose();
        try {
            RenderSystem.enableBlend();
            RenderSystem.setShaderTexture(0, tex);
            graphics.pose().translate(x, y, 0);
            graphics.pose().mulPose(com.mojang.math.Axis.ZP.rotationDegrees(rotationDegrees));
            graphics.pose().scale(scale, scale, 1);
            graphics.blit(tex, -4, -4, 8, 8, 8f, 8f, 8, 8, 64, 64);
            graphics.blit(tex, -4, -4, 8, 8, 40f, 8f, 8, 8, 64, 64);
            rendered = true;
        } catch (Exception ignored) {
            // Draw the fallback after restoring the pose below.
        } finally {
            graphics.pose().popPose();
        }
        if (!rendered) drawMarkerDot(graphics, x, y, fallbackColor, size);
    }
}
