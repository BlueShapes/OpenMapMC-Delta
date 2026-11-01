// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.world;

import net.aosankaku.openmapdelta.config.MinimapLayerMode;

public final class MinimapLayersTest {
    private static final int SURFACE = CaveLayerSteps.SURFACE;
    private static int assertions;

    public static void main(String[] args) {
        check(resolve(MinimapLayerMode.SURFACE, true, 65, -32, 8) == SURFACE, "surface stays fixed");
        check(resolve(MinimapLayerMode.PLAYER_Y, true, 65.9, -32, 8) == 64, "auto follows the containing player band");
        check(resolve(MinimapLayerMode.PLAYER_Y, true, 71.99, -32, 8) == 64, "movement within a band stays stable");
        check(resolve(MinimapLayerMode.PLAYER_Y, true, 72, -32, 8) == 72, "crossing a boundary updates auto mode");
        check(resolve(MinimapLayerMode.PLAYER_Y, true, -0.01, 64, 8) == -8, "negative player Y uses floor before snapping");
        check(resolve(MinimapLayerMode.MANUAL, true, 120, -25, 8) == -32, "manual Y is independent of player Y");
        check(resolve(MinimapLayerMode.MANUAL, true, 120, -25, 16) == -32, "layer-width changes resnap manual Y");
        check(resolve(MinimapLayerMode.MANUAL, true, 120, 9999, 8) == 312, "manual Y clamps to the dimension");
        for (MinimapLayerMode mode : MinimapLayerMode.values()) {
            check(resolve(mode, false, 65, 64, 8) == SURFACE, "server policy forces surface: " + mode);
        }
        check(resolve(MinimapLayerMode.PLAYER_Y, true, Double.NaN, 64, 8) == SURFACE, "missing player Y uses surface");
        check(resolve(MinimapLayerMode.PLAYER_Y, true, Double.POSITIVE_INFINITY, 64, 8) == SURFACE, "non-finite player Y uses surface");
        check(MinimapLayers.resolve(MinimapLayerMode.PLAYER_Y, true, 5, 64, 0, 127, 8) == 0, "Nether bounds replace Overworld bounds");
        check(MinimapLayers.resolve(MinimapLayerMode.PLAYER_Y, true, 70, 64, -60, 70, 8) == 68, "bands anchor to custom minimum Y");
        check(MinimapLayers.resolve(MinimapLayerMode.MANUAL, true, 0, 64, 10, 9, 8) == SURFACE, "invalid bounds use surface");
        skyAwareResolver();
        slots();
        System.out.println("Minimap layer selection tests passed (" + assertions + " assertions).");
    }

    private static void skyAwareResolver() {
        try {
            java.lang.reflect.Method resolve = MinimapLayers.class.getMethod("resolve",
                    MinimapLayerMode.class, boolean.class, double.class, int.class,
                    int.class, int.class, int.class, boolean.class);
            equal(SURFACE, resolve.invoke(null, MinimapLayerMode.PLAYER_Y, true, 65.9, -32, -64, 319, 8, true),
                    "open sky selects surface in auto mode");
            equal(64, resolve.invoke(null, MinimapLayerMode.PLAYER_Y, true, 65.9, -32, -64, 319, 8, false),
                    "roofed auto mode retains the player's cave band");
            equal(-32, resolve.invoke(null, MinimapLayerMode.MANUAL, true, 65.9, -32, -64, 319, 8, true),
                    "open sky does not override manual mode");
            equal(SURFACE, resolve.invoke(null, MinimapLayerMode.SURFACE, true, 65.9, -32, -64, 319, 8, true),
                    "surface mode remains unchanged");
        } catch (ReflectiveOperationException error) {
            throw new AssertionError("sky-aware MinimapLayers.resolve overload is missing", error);
        }
    }

    private static void equal(Object expected, Object actual, String message) {
        assertions++;
        if (!expected.equals(actual)) throw new AssertionError(message + ": expected " + expected + ", got " + actual);
    }

    private static int resolve(MinimapLayerMode mode, boolean allowed, double y, int manual, int step) {
        return MinimapLayers.resolve(mode, allowed, y, manual, -64, 319, step);
    }

    private static void slots() {
        same(MinimapLayers.assign(SURFACE, SURFACE, 0, 8), 0, 8, "two different bands use two slots");
        same(MinimapLayers.assign(0, 8, 8, 0), 0, 8, "swapping consumers preserves both cache owners");
        same(MinimapLayers.assign(0, 8, 8, 8), SURFACE, 8, "equal bands share the existing cache");
        same(MinimapLayers.assign(0, 8, SURFACE, 0), 0, SURFACE, "mini-only cave does not need a fullscreen cave");
        same(MinimapLayers.assign(0, 8, SURFACE, SURFACE), SURFACE, SURFACE, "surface modes release cave scans");
        same(MinimapLayers.assign(0, 8, 16, 8), 16, 8, "auto crossing preserves the unchanged fullscreen cache");
        java.util.Random random = new java.util.Random(20261017);
        int first = SURFACE, second = SURFACE;
        for (int i = 0; i < 1000; i++) {
            int fullscreen = random.nextInt(5) == 0 ? SURFACE : random.nextInt(8) * 8;
            int minimap = random.nextInt(5) == 0 ? SURFACE : random.nextInt(8) * 8;
            int[] next = MinimapLayers.assign(first, second, fullscreen, minimap);
            check(next[0] == SURFACE || next[1] == SURFACE || next[0] != next[1], "never create duplicate writers for a band");
            check(fullscreen == SURFACE || next[0] == fullscreen || next[1] == fullscreen, "fullscreen retains its requested band");
            check(minimap == SURFACE || next[0] == minimap || next[1] == minimap, "minimap retains its requested band");
            if (first != SURFACE && (first == fullscreen || first == minimap)) check(next[0] == first, "preserve first slot ownership");
            if (second != SURFACE && second != first && (second == fullscreen || second == minimap)) check(next[1] == second, "preserve second slot ownership");
            if (next[0] != SURFACE) first = next[0];
            if (next[1] != SURFACE) second = next[1];
        }
    }

    private static void same(int[] actual, int first, int second, String message) {
        check(actual.length == 2 && actual[0] == first && actual[1] == second, message);
    }

    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }
}
