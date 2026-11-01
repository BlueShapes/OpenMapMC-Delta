// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.marker;

import net.aosankaku.openmapdelta.config.OpenMapDeltaConfig.MinimapPlayerLabelMode;

/** Geometry and text rules for other-player minimap labels. */
public final class MinimapPlayerLabel {
    private MinimapPlayerLabel() {}

    /** Classifies map-relative terrain coordinates, before any marker edge clamping or offset. */
    public static boolean isOutside(double dx, double dz, int size, boolean circular) {
        double half = size / 2.0;
        return circular
                ? dx * dx + dz * dz > half * half
                : Math.abs(dx) > half || Math.abs(dz) > half;
    }

    /** Classifies the unrotated world-space footprint covered by the minimap terrain. */
    public static boolean isOutsideWorld(double playerX, double playerZ, double markerX, double markerZ,
                                         int blocksPerPixel, int size, boolean circular) {
        if (blocksPerPixel <= 0) throw new IllegalArgumentException("Blocks per pixel must be positive");
        return isOutside((markerX - playerX) / blocksPerPixel, (markerZ - playerZ) / blocksPerPixel,
                size, circular);
    }

    public static String text(MinimapPlayerLabelMode mode, String name, double distance) {
        return switch (mode) {
            case NAME_ONLY -> name == null ? "" : name;
            case DISTANCE_ONLY -> (int) distance + "m";
            case NAME_AND_DISTANCE -> (name == null || name.isEmpty() ? "" : name + " ") + (int) distance + "m";
            case NONE -> "";
        };
    }
}
