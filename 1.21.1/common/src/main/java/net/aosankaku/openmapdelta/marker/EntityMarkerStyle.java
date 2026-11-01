// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.marker;

/** Size and vertical-opacity policy for live entity markers on 2D maps. */
public final class EntityMarkerStyle {
    private static final int ALPHA_STEP = 51;
    private static final int HEIGHT_STEP = 4;
    private static final int MAX_ICON_SIZE = 10;

    private EntityMarkerStyle() {}

    /** Returns a symmetric, four-block stepped opacity, keeping distant floors visible. */
    public static int alpha(double viewerY, int entityY) {
        if (entityY == Integer.MIN_VALUE || !Double.isFinite(viewerY)) return 255;
        double heightDifference = Math.abs(viewerY - entityY);
        int fadedSteps = (int) Math.min(4, Math.floor(heightDifference / HEIGHT_STEP));
        return 255 - fadedSteps * ALPHA_STEP;
    }

    public static int minimapSize(double waypointScale) {
        return clampIconSize(Math.round(8.0 * waypointScale));
    }

    public static int fullscreenSize(double blocksPerPixel) {
        return clampIconSize(Math.round(8.0 / Math.sqrt(Math.max(0.5, blocksPerPixel))));
    }

    private static int clampIconSize(long size) {
        return (int) Math.max(4, Math.min(MAX_ICON_SIZE, size));
    }
}
