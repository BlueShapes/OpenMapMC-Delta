package net.aosankaku.openmapdelta.marker;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public final class EntityMarkerRenderFixture {
    private EntityMarkerRenderFixture() {}
    public static GuiGraphics createGraphics() { return new GuiGraphicsExtractor(); }
    public static void drawEntity(GuiGraphics graphics, Marker marker, int x, int y, int size, int alpha) {
        MarkerIconRenderer.drawEntity((GuiGraphicsExtractor) graphics, marker, x, y, size, alpha);
    }
    public static void drawOrdinary(GuiGraphics graphics, Marker marker, int x, int y, int size, boolean hovered) {
        MarkerIconRenderer.draw((GuiGraphicsExtractor) graphics, marker, x, y, size, hovered);
    }
}
