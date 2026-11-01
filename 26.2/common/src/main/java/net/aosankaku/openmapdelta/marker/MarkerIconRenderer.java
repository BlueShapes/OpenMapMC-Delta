// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.marker;

import net.aosankaku.openmapdelta.util.RenderUtil;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;

/** Draws the same selected icon on each 2D map surface. */
public final class MarkerIconRenderer {
    private MarkerIconRenderer() {}

    public static boolean hasIcon(Marker marker) { return MarkerIconCatalog.find(marker.getIconId()) != null; }

    public static void draw(GuiGraphicsExtractor graphics, Marker marker, int x, int y, int size, boolean hovered) {
        draw(graphics, marker, x, y, size, hovered, marker.getColor());
    }

    public static void draw(GuiGraphicsExtractor graphics, Marker marker, int x, int y, int size,
                            boolean hovered, int displayColor) {
        int left = x - size / 2;
        int top = y - size / 2;
        if (hovered) {
            int outlineColor = MarkerHoverStyle.outlineColor(displayColor);
            graphics.outline(left - 2, top - 2, size + 4, size + 4, outlineColor ^ 0x00FFFFFF);
            graphics.outline(left - 1, top - 1, size + 2, size + 2, outlineColor);
        }
        MarkerIconCatalog.Entry entry = MarkerIconCatalog.find(marker.getIconId());
        if (entry == null) {
            graphics.fill(left, top, left + size, top + size, displayColor | 0xFF000000);
            return;
        }
        drawEntry(graphics, entry, x, y, size, displayColor);
    }

    public static void drawEntry(GuiGraphicsExtractor graphics, MarkerIconCatalog.Entry entry, int x, int y, int size) {
        drawEntry(graphics, entry, x, y, size, 0xFFFFFFFF);
    }

    /** Draws a live entity icon with an explicit per-draw opacity multiplier. */
    public static void drawEntity(GuiGraphicsExtractor graphics, Marker marker, int x, int y, int size, int alpha) {
        MarkerIconCatalog.Entry entry = MarkerIconCatalog.find(marker.getIconId());
        MarkerIconCatalog.Texture texture = entry == null || !entry.item().isEmpty()
                ? null : MarkerIconCatalog.resolveTexture(entry);
        if (entry == null || switch (marker.getIconId()) {
            case "mob:minecraft:cod", "mob:minecraft:salmon", "mob:minecraft:pufferfish",
                    "mob:minecraft:tropical_fish" -> true;
            default -> false;
        }) {
            MarkerIconCatalog.Texture generated = EntityIconSprites.resolve(marker);
            if (generated != null) texture = generated;
        }
        int opacity = Math.max(0, Math.min(255, alpha));
        if (texture == null) {
            int color = marker.getColor();
            int colorAlpha = ((color >>> 24) * opacity + 127) / 255;
            graphics.fill(x - size / 2, y - size / 2, x - size / 2 + size, y - size / 2 + size,
                    (color & 0x00FFFFFF) | colorAlpha << 24);
            return;
        }

        drawTexture(graphics, texture, x, y, size, opacity);
    }

    private static void drawEntry(GuiGraphicsExtractor graphics, MarkerIconCatalog.Entry entry, int x, int y,
                                  int size, int fallbackColor) {
        if (entry == null) { RenderUtil.drawMarkerDot(graphics, x, y, fallbackColor, size); return; }
        MarkerIconCatalog.Texture texture = entry.item().isEmpty() ? MarkerIconCatalog.resolveTexture(entry) : null;
        if (entry.item().isEmpty() && texture == null) {
            RenderUtil.drawMarkerDot(graphics, x, y, fallbackColor, size);
            return;
        }
        if (entry.item().isEmpty()) {
            drawTexture(graphics, texture, x, y, size, 255);
            return;
        }
        graphics.pose().pushMatrix();
        try {
            graphics.pose().translate(x - size / 2, y - size / 2);
            graphics.pose().scale(size / 16.0F, size / 16.0F);
            graphics.item(entry.item(), 0, 0);
        } finally { graphics.pose().popMatrix(); }
    }

    private static void drawTexture(GuiGraphicsExtractor graphics, MarkerIconCatalog.Texture texture,
                                    int x, int y, int size, int opacity) {
        if (size <= 0 || texture.width() <= 0 || texture.height() <= 0) return;
        float scale = size / (float) Math.max(texture.width(), texture.height());
        graphics.pose().pushMatrix();
        try {
            graphics.pose().translate(x - texture.width() * scale / 2, y - texture.height() * scale / 2);
            graphics.pose().scale(scale, scale);
            graphics.blit(RenderPipelines.GUI_TEXTURED, texture.location(), 0, 0,
                    (float) texture.u(), (float) texture.v(), texture.width(), texture.height(),
                    texture.textureWidth(), texture.textureHeight(), opacity << 24 | 0x00FFFFFF);
        } finally { graphics.pose().popMatrix(); }
    }
}
