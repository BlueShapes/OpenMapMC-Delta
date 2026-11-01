// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.render;

/** Rectangle placement shared by fullscreen edge markers, pointer hits, and context anchors. */
public final class FullscreenMapMarkerGeometry {
    public record Position(int x, int y, boolean edge) {}

    private FullscreenMapMarkerGeometry() {}

    public static Position place(int rectX, int rectY, int width, int height,
                                 int projectedX, int projectedY, int iconSize, boolean navigationHalo) {
        if (width <= 0 || height <= 0) return new Position(rectX, rectY, false);
        long rightExclusive = (long) rectX + width;
        long bottomExclusive = (long) rectY + height;
        boolean inside = projectedX >= rectX && projectedX < rightExclusive
                && projectedY >= rectY && projectedY < bottomExclusive;
        if (inside) return new Position(projectedX, projectedY, false);

        int size = Math.max(1, iconSize);
        int iconAndHoverInset = size / 2 + 2;
        int haloInset = navigationHalo ? (size + 6) / 2 : 0;
        int inset = Math.max(iconAndHoverInset, haloInset);
        int minX = (int) Math.min(rightExclusive - 1, (long) rectX + inset);
        int maxX = (int) Math.max(rectX, rightExclusive - 1 - inset);
        int minY = (int) Math.min(bottomExclusive - 1, (long) rectY + inset);
        int maxY = (int) Math.max(rectY, bottomExclusive - 1 - inset);

        int centerX = rectX + width / 2;
        int centerY = rectY + height / 2;
        if (minX > maxX) minX = maxX = clamp(centerX, rectX, (int) rightExclusive - 1);
        if (minY > maxY) minY = maxY = clamp(centerY, rectY, (int) bottomExclusive - 1);

        double dx = (double) projectedX - centerX;
        double dy = (double) projectedY - centerY;
        double scale = 1.0;
        if (dx > 0.0) scale = Math.min(scale, (maxX - centerX) / dx);
        else if (dx < 0.0) scale = Math.min(scale, (minX - centerX) / dx);
        if (dy > 0.0) scale = Math.min(scale, (maxY - centerY) / dy);
        else if (dy < 0.0) scale = Math.min(scale, (minY - centerY) / dy);
        scale = Math.max(0.0, Math.min(1.0, scale));
        int x = clamp((int) Math.round(centerX + dx * scale), minX, maxX);
        int y = clamp((int) Math.round(centerY + dy * scale), minY, maxY);
        return new Position(x, y, true);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
