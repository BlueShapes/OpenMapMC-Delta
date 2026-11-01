// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.render;

public final class MinimapSizing {
    public static final int TEXTURE_SIZE = 128;
    private static final int MAX_TEXTURE_SIZE = 2048;
    public static final int MIN_TEXT_PIXEL_HEIGHT = 16;

    private MinimapSizing() {}

    /** Chooses a high-resolution raster without changing the logical map's world coverage. */
    public static int textureSize(int logicalSize, int blocksPerPixel, double physicalPixels) {
        if (logicalSize <= 0) return 0;
        long worldSpan = (long) logicalSize * Math.max(1, blocksPerPixel);
        int maxByWorldSpan = (int) Math.max(2, Math.min(MAX_TEXTURE_SIZE, worldSpan));
        if (maxByWorldSpan > 2) maxByWorldSpan &= ~1;
        int maxByPhysicalSize;
        if (!Double.isFinite(physicalPixels) || physicalPixels <= 0) {
            maxByPhysicalSize = Math.min(TEXTURE_SIZE, MAX_TEXTURE_SIZE);
        } else if (physicalPixels >= MAX_TEXTURE_SIZE) {
            maxByPhysicalSize = MAX_TEXTURE_SIZE;
        } else {
            int requested = (int) Math.ceil(physicalPixels);
            maxByPhysicalSize = Math.max(2, (requested + 1) & ~1);
        }
        return Math.min(maxByWorldSpan, maxByPhysicalSize);
    }

    public static int displaySize(int width, int height, double scale) {
        if (width <= 0 || height <= 0) return 0;
        if (!Double.isFinite(scale) || scale < 0.25 || scale > 2.0) scale = 1.0;
        return (int) Math.floor(Math.min(width, height) / 3.0 * scale);
    }

    public static int renderExtent(int borderThickness, boolean circular, boolean headingUp) {
        int borderPadding = borderThickness + (circular && borderThickness > 0 ? 1 : 0);
        int rotatedTextureExtent = headingUp && !circular
                ? (int) Math.ceil(TEXTURE_SIZE * Math.sqrt(2.0)) : TEXTURE_SIZE;
        return Math.max(rotatedTextureExtent, TEXTURE_SIZE + 2 * borderPadding);
    }

    public static int margin(int width, int height, int displaySize) {
        return margin(width, height, displaySize, 0);
    }

    public static int margin(int width, int height, int displaySize, double overhang) {
        int desiredMargin = Math.max(8, (int) Math.ceil(overhang));
        return Math.min(desiredMargin, Math.max(0, Math.min(width, height) - displaySize));
    }

    /** Returns the local text scale needed to retain a minimum physical pixel height. */
    public static double textScale(double requestedScale, double parentScale, double guiScale, int fontLineHeight) {
        double requested = Double.isFinite(requestedScale) && requestedScale > 0 ? requestedScale : 1.0;
        double parent = Double.isFinite(parentScale) && parentScale > 0 ? parentScale : 1.0;
        double gui = Double.isFinite(guiScale) && guiScale > 0 ? guiScale : 1.0;
        int lineHeight = Math.max(1, fontLineHeight);
        return Math.max(requested, MIN_TEXT_PIXEL_HEIGHT / (parent * gui * lineHeight));
    }
}
