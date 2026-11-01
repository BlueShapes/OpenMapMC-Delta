// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.render;

/** Shared marker, hit-test and context-pointer projection, including negative coordinates. */
public final class FullscreenMapProjection {
    private FullscreenMapProjection() {}

    public static int pixel(int panelStart, int panelLength, double pan,
                            double world, double blocksPerPixel) {
        return panelStart + panelLength / 2
                + (int) ((world - pan) / FullscreenMapZoom.safeBlocksPerPixel(blocksPerPixel));
    }

    public static int world(int panelStart, int panelLength, double pan,
                            double pixel, double blocksPerPixel) {
        return (int) Math.floor(pan + (pixel - panelStart - panelLength / 2)
                * FullscreenMapZoom.safeBlocksPerPixel(blocksPerPixel));
    }
}
