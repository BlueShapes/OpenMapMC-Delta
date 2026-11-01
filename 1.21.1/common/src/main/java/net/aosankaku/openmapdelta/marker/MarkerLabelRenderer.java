// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.marker;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.network.chat.Component;
import net.aosankaku.openmapdelta.render.WaypointOwnerFace;

/** Uses each glyph's own outline offset, including half-pixel CJK glyphs. */
public final class MarkerLabelRenderer {
    private MarkerLabelRenderer() {}

    public static void draw(GuiGraphics graphics, Font font, String label, int x, int y,
                            int markerColor, boolean hovered) {
        draw(graphics, font, label, x, y, markerColor, hovered, null);
    }

    public static void draw(GuiGraphics graphics, Font font, String label, int x, int y,
                            int markerColor, boolean hovered, Marker marker) {
        int color = markerColor | 0xFF000000;
        int prefixWidth = marker != null && marker.getOwnerId() != null ? 10 : 0;
        int textX = x + prefixWidth;
        graphics.fill(x - 2, y - 2, textX + font.width(label) + 2, y + font.lineHeight + 2, 0x80000000);
        if (prefixWidth > 0) WaypointOwnerFace.draw(graphics, marker, x + 4, y + font.lineHeight / 2, 8);
        if (!hovered) {
            graphics.drawString(font, label, textX, y, color, false);
            return;
        }
        graphics.flush();
        font.drawInBatch8xOutline(Component.literal(label).getVisualOrderText(), textX, y, color,
                MarkerHoverStyle.outlineColor(color), graphics.pose().last().pose(),
                graphics.bufferSource(), LightTexture.FULL_BRIGHT);
        graphics.flush();
    }
}
