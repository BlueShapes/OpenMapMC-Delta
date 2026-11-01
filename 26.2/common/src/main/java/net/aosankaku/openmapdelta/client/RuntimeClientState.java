// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.client;

import net.aosankaku.openmapdelta.config.OpenMapDeltaConfig;

public final class RuntimeClientState {
    private static int zoomBlocksPerPixel = -1;

    private RuntimeClientState() {}

    public static int getZoomBlocksPerPixel() {
        if (zoomBlocksPerPixel < 1) {
            zoomBlocksPerPixel = OpenMapDeltaConfig.ZOOM_BLOCKS_PER_PIXEL.get();
        }
        return zoomBlocksPerPixel;
    }

    public static void zoomIn() {
        int z = getZoomBlocksPerPixel();
        if (z > 1) {
            zoomBlocksPerPixel = z - 1;
        }
    }

    public static void zoomOut() {
        int z = getZoomBlocksPerPixel();
        if (z < 16) {
            zoomBlocksPerPixel = z + 1;
        }
    }

    public static void resetZoom() {
        zoomBlocksPerPixel = OpenMapDeltaConfig.ZOOM_BLOCKS_PER_PIXEL.get();
    }
}
