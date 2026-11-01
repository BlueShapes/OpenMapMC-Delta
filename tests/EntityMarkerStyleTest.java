// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.marker;

public final class EntityMarkerStyleTest {
    private static int assertions;

    public static void main(String[] args) {
        alphaIsSteppedAndSymmetric();
        alphaHandlesUnknownHeights();
        iconSizesStaySmallAndPositive();
        System.out.println("Entity marker style tests passed (" + assertions + " assertions).");
    }

    private static void alphaIsSteppedAndSymmetric() {
        check(EntityMarkerStyle.alpha(64, 67) == 255, "less than four blocks of height difference is fully opaque");
        check(EntityMarkerStyle.alpha(64, 68) == 204, "four block height difference fades to eighty percent");
        check(EntityMarkerStyle.alpha(64, 71) == 204, "height difference within a band keeps its alpha");
        check(EntityMarkerStyle.alpha(64, 72) == 153, "eight block height difference fades to sixty percent");
        check(EntityMarkerStyle.alpha(64, 76) == 102, "twelve block height difference fades to forty percent");
        check(EntityMarkerStyle.alpha(64, 80) == 51, "sixteen block height difference retains twenty percent alpha");
        check(EntityMarkerStyle.alpha(64, 128) == 51, "large height differences retain the minimum visible alpha");
        check(EntityMarkerStyle.alpha(64, 60) == EntityMarkerStyle.alpha(64, 68),
                "height fading is symmetric above and below the player");
    }

    private static void alphaHandlesUnknownHeights() {
        check(EntityMarkerStyle.alpha(64, Integer.MIN_VALUE) == 255,
                "unknown entity height does not create a spurious fade");
        check(EntityMarkerStyle.alpha(Double.NaN, 64) == 255, "non-finite player height does not create a spurious fade");
        check(EntityMarkerStyle.alpha(Double.POSITIVE_INFINITY, 64) == 255,
                "infinite player height does not create a spurious fade");
    }

    private static void iconSizesStaySmallAndPositive() {
        int minimapSize = EntityMarkerStyle.minimapSize(0.65);
        check(minimapSize == 5, "minimap mob icons use a compact size below ordinary ten-pixel markers");
        check(EntityMarkerStyle.minimapSize(0.0) == 4, "tiny minimap scale remains legible");
        check(EntityMarkerStyle.minimapSize(20.0) == 10, "large waypoint scales cannot make mob icons oversized");
        check(EntityMarkerStyle.fullscreenSize(1.0) == 8, "fullscreen entity icons remain below player icons");
        check(EntityMarkerStyle.fullscreenSize(16.0) == 4, "fullscreen zoom-out clamps entity icons to a compact minimum");
        check(EntityMarkerStyle.fullscreenSize(0.5) == 10, "fullscreen zoom-in clamps entity icons to a compact maximum");
    }

    private static void check(boolean condition, String description) {
        assertions++;
        if (!condition) throw new AssertionError(description);
    }
}
