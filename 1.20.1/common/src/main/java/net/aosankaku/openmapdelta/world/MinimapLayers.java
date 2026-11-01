// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.world;

import net.aosankaku.openmapdelta.config.MinimapLayerMode;

/** Pure selection and ownership rules for the two independently selected cave views. */
public final class MinimapLayers {
    private static final int SURFACE = CaveLayerSteps.SURFACE;
    private MinimapLayers() {}

    public static int resolve(MinimapLayerMode mode, boolean allowed, double playerY, int manualY,
                              int minY, int maxY, int step) {
        return resolve(mode, allowed, playerY, manualY, minY, maxY, step, false);
    }

    public static int resolve(MinimapLayerMode mode, boolean allowed, double playerY, int manualY,
                              int minY, int maxY, int step, boolean openSky) {
        if (!allowed || mode == null || mode == MinimapLayerMode.SURFACE || maxY < minY) return SURFACE;
        if (mode == MinimapLayerMode.PLAYER_Y && (openSky || !Double.isFinite(playerY))) return SURFACE;
        int y = mode == MinimapLayerMode.PLAYER_Y
                ? (int) Math.floor(Math.max(minY, Math.min(maxY, playerY))) : manualY;
        return CaveLayerSteps.quantize(y, minY, maxY, step);
    }

    /** Keep matching cache owners, including inactive ones with pending serial disk writes. */
    public static int[] assign(int first, int second, int fullscreen, int minimap) {
        int[] wanted = {fullscreen, minimap == fullscreen ? SURFACE : minimap};
        int[] current = {first, second}, result = {SURFACE, SURFACE};
        for (int slot = 0; slot < 2; slot++) {
            for (int view = 0; view < 2; view++) {
                if (current[slot] != SURFACE && current[slot] == wanted[view]) {
                    result[slot] = current[slot];
                    wanted[view] = SURFACE;
                    break;
                }
            }
        }
        for (int layer : wanted) {
            if (layer == SURFACE) continue;
            result[result[0] == SURFACE ? 0 : 1] = layer;
        }
        return result;
    }
}
