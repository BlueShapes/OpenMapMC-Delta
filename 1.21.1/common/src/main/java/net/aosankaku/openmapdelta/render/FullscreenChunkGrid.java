// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.render;

/** World-aligned chunk boundary geometry, independent of cached terrain sampling. */
public final class FullscreenChunkGrid {
    private static final int CHUNK_SIZE = 16;
    private static final double DETAIL_ZOOM_THRESHOLD = 0.15;
    private static final double MIN_GROUP_SPACING = CHUNK_SIZE * DETAIL_ZOOM_THRESHOLD;
    public static final int MAX_LINES_PER_AXIS = 4096;

    private FullscreenChunkGrid() {}

    /** Keeps every chunk at 0.15x or closer, then groups power-of-two chunks to retain grid density. */
    public static Layout layout(double blocksPerPixel, int maximumPanelLength, double configuredThickness) {
        double bpp = FullscreenMapZoom.safeBlocksPerPixel(blocksPerPixel);
        double minimumSpacing = 1.0 / bpp >= DETAIL_ZOOM_THRESHOLD ? 0.0 : MIN_GROUP_SPACING;
        minimumSpacing = Math.max(minimumSpacing,
                Math.max(0, maximumPanelLength) / (double) (MAX_LINES_PER_AXIS - 1));
        long worldSpacing = CHUNK_SIZE;
        while (worldSpacing / (double) bpp < minimumSpacing) worldSpacing *= 2;
        double spacing = worldSpacing / (double) bpp;
        double width = Double.isFinite(configuredThickness) && configuredThickness > 0.0
                ? Math.max(0.25, Math.min(4.0, configuredThickness)) : 0.5;
        // Thin dense lines without allowing the configured width to fill their gaps.
        float thickness = (float) (Math.min(width, spacing / 4.0) * Math.min(1.0, spacing / 4.0));
        if (thickness > spacing / 4.0) thickness = Math.nextDown(thickness);
        return new Layout(bpp, worldSpacing, spacing, thickness);
    }

    public static double lineSpacing(double blocksPerPixel) {
        return layout(blocksPerPixel, 0, 0.5).lineSpacing();
    }

    public static double firstLineOffset(int panelLength, double pan, double blocksPerPixel) {
        return layout(blocksPerPixel, 0, 0.5).firstLineOffset(panelLength, pan);
    }

    /** Both axes share this layout, including extra grouping for unusually large panels. */
    public record Layout(double blocksPerPixel, long worldSpacing, double lineSpacing, float lineThickness) {
        public double firstLineOffset(int panelLength, double pan) {
            if (panelLength <= 0 || !Double.isFinite(pan)) return Double.NaN;
            double worldStart = pan - (panelLength / 2) * (double) blocksPerPixel;
            // A remainder avoids overflowing a rounded world boundary near Double.MAX_VALUE.
            double remainder = Math.IEEEremainder(worldStart, worldSpacing);
            double offset = (remainder <= 0.0 ? -remainder : worldSpacing - remainder) / blocksPerPixel;
            return offset >= lineSpacing ? 0.0 : offset;
        }

        public int lineCount(int panelLength, double firstOffset) {
            if (panelLength <= 0 || !Double.isFinite(firstOffset) || firstOffset < 0.0
                    || firstOffset >= panelLength) return 0;
            return Math.min(MAX_LINES_PER_AXIS, (int) Math.ceil((panelLength - firstOffset) / lineSpacing));
        }
    }
}
