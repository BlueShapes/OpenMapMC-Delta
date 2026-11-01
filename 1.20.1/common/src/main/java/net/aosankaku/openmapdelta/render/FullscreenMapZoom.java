// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.render;

/** Fullscreen zoom retains the configured coarse scale and allows two pixels per block. */
public final class FullscreenMapZoom {
    public static final double MIN_BLOCKS_PER_PIXEL = 0.5;
    public static final double DEFAULT_WAYPOINT_LABEL_MIN_ZOOM = 0.5;
    private static final double MAX_RELATIVE_SCALE = 18.0;

    private FullscreenMapZoom() {}

    public static double safeBlocksPerPixel(double value) {
        return Double.isFinite(value) ? Math.max(MIN_BLOCKS_PER_PIXEL, value) : 1.0;
    }

    /** Match the displayed 1 / blocks-per-pixel multiplier; the boundary itself stays visible. */
    public static boolean waypointLabelsVisible(double blocksPerPixel, double minimumZoom) {
        return 1.0 / safeBlocksPerPixel(blocksPerPixel) >= safeLabelMinimumZoom(minimumZoom);
    }

    public static double waypointLabelZoomFromSlider(double value) {
        double normalized = Double.isFinite(value) ? Math.max(0.0, Math.min(1.0, value)) : 0.25;
        return Math.round(normalized * 200.0) / 100.0;
    }

    public static double waypointLabelZoomToSlider(double minimumZoom) {
        return safeLabelMinimumZoom(minimumZoom) / 2.0;
    }

    private static double safeLabelMinimumZoom(double value) {
        return Double.isFinite(value) && value >= 0.0 && value <= 2.0
                ? value : DEFAULT_WAYPOINT_LABEL_MIN_ZOOM;
    }

    public static double blocksPerPixel(int baseScale, double relativeScale) {
        return Math.max(1, baseScale) * clampRelative(baseScale, relativeScale);
    }

    public static double adjust(int baseScale, double relativeScale, double scrollDelta) {
        int base = Math.max(1, baseScale);
        double relative = clampRelative(base, relativeScale);
        double bpp = base * relative;
        if (scrollDelta > 0.0) {
            if (relative > 1.0) return Math.max(1.0, relative - 1.0);
            double next = bpp > 1.0 ? Math.max(1.0, Math.ceil(bpp) - 1.0) : MIN_BLOCKS_PER_PIXEL;
            return next / base;
        }
        if (scrollDelta < 0.0) {
            if (relative < 1.0) {
                double next = bpp < 1.0 ? 1.0 : Math.floor(bpp) + 1.0;
                return Math.min(base, next) / base;
            }
            return Math.min(MAX_RELATIVE_SCALE, relative + 1.0);
        }
        return relative;
    }

    private static double clampRelative(int baseScale, double relativeScale) {
        double value = Double.isFinite(relativeScale) && relativeScale > 0.0 ? relativeScale : 1.0;
        return Math.max(MIN_BLOCKS_PER_PIXEL / Math.max(1, baseScale),
                Math.min(MAX_RELATIVE_SCALE, value));
    }
}
