// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.world;

/** Discrete cave bands anchored to the world's minimum Y; both height bounds are inclusive. */
public final class CaveLayerSteps {
    public static final int SURFACE = Integer.MIN_VALUE;

    private CaveLayerSteps() {}

    /** Total slider positions: one per cave band, followed by Surface. */
    public static int layerCount(int minY, int maxY, int step) {
        if (maxY < minY) return 1;
        long height = (long) maxY - minY + 1;
        long caves = (height + boundedStep(step) - 1) / boundedStep(step);
        return (int) Math.min(Integer.MAX_VALUE, caves + 1);
    }

    /** Converts a normalized slider value to a band's lower anchor. NaN safely selects Surface. */
    public static int fromSlider(double value, int minY, int maxY, int step) {
        int caves = layerCount(minY, maxY, step) - 1;
        if (caves == 0 || Double.isNaN(value) || value >= 1.0) return SURFACE;
        long index = Math.round(Math.max(0.0, value) * caves);
        if (index >= caves) return SURFACE;
        return (int) Math.min(maxY, (long) minY + index * boundedStep(step));
    }

    /** Snaps arbitrary cave Y values to their band before converting them to the slider scale. */
    public static double toSlider(int selected, int minY, int maxY, int step) {
        int caves = layerCount(minY, maxY, step) - 1;
        if (selected == SURFACE || caves == 0) return 1.0;
        long index = ((long) quantize(selected, minY, maxY, step) - minY) / boundedStep(step);
        return Math.min(caves - 1L, index) / (double) caves;
    }

    /** Clamps to world height and returns the lower edge of the containing cave band. */
    public static int quantize(int y, int minY, int maxY, int step) {
        if (y == SURFACE || maxY < minY) return SURFACE;
        long boundedY = Math.max((long) minY, Math.min((long) maxY, y));
        long index = (boundedY - minY) / boundedStep(step);
        return (int) ((long) minY + index * boundedStep(step));
    }

    /** Inclusive upper edge, including a partial final band at the world's maximum Y. */
    public static int bandEnd(int lower, int minY, int maxY, int step) {
        int anchor = quantize(lower, minY, maxY, step);
        if (anchor == SURFACE) return SURFACE;
        return (int) Math.min(maxY, (long) anchor + boundedStep(step) - 1);
    }

    private static int boundedStep(int step) { return Math.max(1, Math.min(64, step)); }
}
