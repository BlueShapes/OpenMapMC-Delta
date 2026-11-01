// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.marker;

import net.aosankaku.openmapdelta.render.WaypointOwnerFace;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Queues a thin, half-pixel halo without mixing immediate rendering into GUI extraction. */
public final class MarkerLabelRenderer {
    private MarkerLabelRenderer() {}

    public static void draw(GuiGraphicsExtractor graphics, Font font, String label, int x, int y,
                            int markerColor, boolean hovered) {
        draw(graphics, font, label, x, y, markerColor, hovered, null);
    }

    public static void draw(GuiGraphicsExtractor graphics, Font font, String label, int x, int y,
                            int markerColor, boolean hovered, Marker marker) {
        int color = markerColor | 0xFF000000;
        int prefixWidth = marker != null && marker.getOwnerId() != null ? 10 : 0;
        int textX = x + prefixWidth;
        graphics.fill(x - 2, y - 2, textX + font.width(label) + 2, y + font.lineHeight + 2, 0x80000000);
        if (prefixWidth > 0) WaypointOwnerFace.draw(graphics, marker, x + 4, y + font.lineHeight / 2, 8);
        if (hovered) {
            int outlineColor = MarkerHoverStyle.outlineColor(color);
            // Unihex CJK glyphs are oversampled by two and use a 0.5-pixel shadow offset.
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    if (dx == 0 && dy == 0) continue;
                    graphics.pose().pushMatrix();
                    try {
                        graphics.pose().translate(dx * 0.5F, dy * 0.5F);
                        graphics.text(font, label, textX, y, outlineColor, false);
                    } finally {
                        graphics.pose().popMatrix();
                    }
                }
            }
        }
        // Deferred GUI text nodes retain this foreground after their overlapping halo nodes.
        graphics.text(font, label, textX, y, color, false);
    }
}
