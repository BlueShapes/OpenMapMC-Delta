// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.marker;

import net.aosankaku.openmapdelta.util.RenderUtil;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;


/** Draws the same selected icon on each 2D map surface. */
public final class MarkerIconRenderer {
    private MarkerIconRenderer() {}

    public static boolean hasIcon(Marker marker) { return MarkerIconCatalog.find(marker.getIconId()) != null; }

    public static void draw(GuiGraphics graphics, Marker marker, int x, int y, int size, boolean hovered) {
        draw(graphics, marker, x, y, size, hovered, marker.getColor());
    }

    public static void draw(GuiGraphics graphics, Marker marker, int x, int y, int size,
                            boolean hovered, int displayColor) {
        int left = x - size / 2;
        int top = y - size / 2;
        if (hovered) {
            int outlineColor = MarkerHoverStyle.outlineColor(displayColor);
            graphics.renderOutline(left - 2, top - 2, size + 4, size + 4, outlineColor ^ 0x00FFFFFF);
            graphics.renderOutline(left - 1, top - 1, size + 2, size + 2, outlineColor);
        }
        MarkerIconCatalog.Entry entry = MarkerIconCatalog.find(marker.getIconId());
        if (entry == null) {
            graphics.fill(left, top, left + size, top + size, displayColor | 0xFF000000);
            return;
        }
        drawEntry(graphics, entry, x, y, size, displayColor);
    }

    public static void drawEntry(GuiGraphics graphics, MarkerIconCatalog.Entry entry, int x, int y, int size) {
        drawEntry(graphics, entry, x, y, size, 0xFFFFFFFF);
    }

    /** Draws a live entity icon with a temporary opacity multiplier. */
    public static void drawEntity(GuiGraphics graphics, Marker marker, int x, int y, int size, int alpha) {
        MarkerIconCatalog.Entry entry = MarkerIconCatalog.find(marker.getIconId());
        MarkerIconCatalog.Texture texture = entry == null || !entry.item().isEmpty()
                ? null : MarkerIconCatalog.resolveTexture(entry);
        int opacity = Math.max(0, Math.min(255, alpha));
        if (texture == null) {
            int color = marker.getColor();
            int colorAlpha = ((color >>> 24) * opacity + 127) / 255;
            graphics.fill(x - size / 2, y - size / 2, x - size / 2 + size, y - size / 2 + size,
                    (color & 0x00FFFFFF) | colorAlpha << 24);
            return;
        }

        float[] previousColor = RenderSystem.getShaderColor().clone();
        graphics.flush();
        boolean pushed = false;
        try {
            RenderSystem.enableBlend();
            RenderSystem.setShaderColor(previousColor[0], previousColor[1], previousColor[2],
                    previousColor[3] * opacity / 255.0F);
            graphics.pose().pushPose();
            pushed = true;
            graphics.pose().translate(x - size / 2, y - size / 2, 0);
            graphics.pose().scale(size / (float) texture.width(), size / (float) texture.height(), 1);
            graphics.blit(texture.location(), 0, 0, (float) texture.u(), (float) texture.v(),
                    texture.width(), texture.height(), texture.textureWidth(), texture.textureHeight());
        } finally {
            try {
                if (pushed) graphics.pose().popPose();
            } finally {
                try {
                    graphics.flush();
                } finally {
                    RenderSystem.setShaderColor(previousColor[0], previousColor[1], previousColor[2], previousColor[3]);
                }
            }
        }
    }

    private static void drawEntry(GuiGraphics graphics, MarkerIconCatalog.Entry entry, int x, int y,
                                  int size, int fallbackColor) {
        if (entry == null) { RenderUtil.drawMarkerDot(graphics, x, y, fallbackColor, size); return; }
        MarkerIconCatalog.Texture texture = entry.item().isEmpty() ? MarkerIconCatalog.resolveTexture(entry) : null;
        if (entry.item().isEmpty() && texture == null) {
            RenderUtil.drawMarkerDot(graphics, x, y, fallbackColor, size);
            return;
        }
        graphics.pose().pushPose();
        try {
            graphics.pose().translate(x - size / 2, y - size / 2, 0);
            if (!entry.item().isEmpty()) {
                graphics.pose().scale(size / 16.0F, size / 16.0F, 1);
                graphics.renderItem(entry.item(), 0, 0);
            } else {
                graphics.pose().scale(size / (float) texture.width(), size / (float) texture.height(), 1);
                graphics.blit(texture.location(), 0, 0, (float) texture.u(), (float) texture.v(),
                        texture.width(), texture.height(), texture.textureWidth(), texture.textureHeight());
            }
        } finally { graphics.pose().popPose(); }
    }
}
