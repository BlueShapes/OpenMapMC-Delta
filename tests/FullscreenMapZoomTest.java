// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.render;

/** Zoom limits and marker/context geometry need no Minecraft startup. */
public final class FullscreenMapZoomTest {
    private static int assertions;

    public static void main(String[] args) {
        double minimumBpp = Double.parseDouble(System.getProperty("fullscreen.test.minBpp", "0.5"));
        for (int base = 1; base <= 16; base++) {
            double relative = 4;
            for (int step = 0; step < 40; step++) relative = FullscreenMapZoom.adjust(base, relative, 1);
            near(FullscreenMapZoom.blocksPerPixel(base, relative), minimumBpp, "all base scales reach the version's maximum zoom");
            near(FullscreenMapZoom.adjust(base, relative, 1), relative, "maximum zoom is bounded");
            relative = FullscreenMapZoom.adjust(base, relative, -1);
            near(FullscreenMapZoom.blocksPerPixel(base, relative), minimumBpp * 2, "zoom out visits the adjacent detail step");
            for (int step = 0; step < 40; step++) relative = FullscreenMapZoom.adjust(base, relative, -1);
            near(relative, 18, "expanded coarse zoom limit is reachable");
            near(FullscreenMapZoom.adjust(base, relative, -1), 18, "expanded upper zoom limit is bounded");
            near(FullscreenMapZoom.adjust(base, 3, 1), 2, "legacy zoom-in step stays unchanged");
            near(FullscreenMapZoom.adjust(base, 3, -1), 4, "legacy zoom-out step stays unchanged");
        }
        near(FullscreenMapZoom.adjust(2, 1, 0), 1, "zero scroll is inert");
        near(FullscreenMapZoom.adjust(2, 1, Double.NaN), 1, "invalid scroll is inert");
        near(FullscreenMapZoom.blocksPerPixel(0, Double.NaN), 1, "invalid scales are safe");
        near(FullscreenMapZoom.blocksPerPixel(2, 100), 36, "relative zoom is bounded at x18");
        near(FullscreenMapZoom.safeBlocksPerPixel(Double.POSITIVE_INFINITY), 1, "nonfinite geometry is safe");

        waypointLabelVisibility();

        for (double bpp : new double[] {minimumBpp, 0.5, 1, 2, 3, 16}) {
            for (int world : new int[] {-1200, -17, 0, 17, 1200}) {
                int marker = FullscreenMapProjection.pixel(7, 731, -17.25, world, bpp);
                int clickedWorld = FullscreenMapProjection.world(7, 731, -17.25, marker, bpp);
                check(Math.abs(clickedWorld - world) <= Math.ceil(bpp), "inverse projection stays within a drawn pixel");
            }
        }
        check(FullscreenMapProjection.pixel(7, 731, -17.25, -17.25, 0.5) == 372,
                "odd panel uses the same integer center as marker rendering");
        check(FullscreenMapProjection.world(7, 731, -17.25, 371, 0.5) == -18,
                "world selection floors negative fractional block coordinates");

        var east = FullscreenMapMarkerGeometry.place(10, 20, 100, 80, 200, 60, 10, false);
        check(east.edge() && east.x() == 102 && east.y() == 60, "east edge keeps the horizontal ray");
        var north = FullscreenMapMarkerGeometry.place(10, 20, 100, 80, 60, -100, 10, false);
        check(north.edge() && north.x() == 60 && north.y() == 27, "north edge keeps the vertical ray");
        var corner = FullscreenMapMarkerGeometry.place(10, 20, 100, 80, 200, -100, 10, false);
        check(corner.edge() && corner.x() > 60 && corner.y() == 27, "corner edge follows the center-to-marker ray");
        var inside = FullscreenMapMarkerGeometry.place(10, 20, 100, 80, 109, 99, 10, false);
        check(!inside.edge() && inside.x() == 109 && inside.y() == 99,
                "inside bottom-right pixel remains inside the half-open rectangle");
        var exclusive = FullscreenMapMarkerGeometry.place(10, 20, 100, 80, 110, 99, 10, false);
        check(exclusive.edge() && exclusive.x() < 110, "exclusive right edge is clamped inside");
        var negative = FullscreenMapMarkerGeometry.place(-100, -50, 200, 100, -500, -500, 8, false);
        check(negative.edge() && negative.x() >= -100 && negative.x() < 100
                && negative.y() >= -50 && negative.y() < 50, "negative map origins stay in bounds");
        var tiny = FullscreenMapMarkerGeometry.place(-4, -3, 3, 2, 100, 100, 32, true);
        check(tiny.edge() && tiny.x() >= -4 && tiny.x() < -1 && tiny.y() >= -3 && tiny.y() < -1,
                "tiny rectangles collapse safely when the icon and halo cannot fit");
        var halo = FullscreenMapMarkerGeometry.place(0, 0, 100, 100, 1000, 50, 10, true);
        check(halo.edge() && halo.x() == 91, "navigation halo is inset from the map boundary");
        System.out.println("Fullscreen zoom and projection tests passed (" + assertions + " assertions).");
    }

    private static void waypointLabelVisibility() {
        check(FullscreenMapZoom.waypointLabelsVisible(2.0, 0.5), "labels stay visible at exactly 0.5x");
        check(!FullscreenMapZoom.waypointLabelsVisible(Math.nextUp(2.0), 0.5), "labels hide just below 0.5x");
        check(FullscreenMapZoom.waypointLabelsVisible(0.5, 0.5), "zooming in keeps labels visible");
        check(!FullscreenMapZoom.waypointLabelsVisible(3.0, 0.5), "0.33x hides waypoint labels by default");
        check(FullscreenMapZoom.waypointLabelsVisible(3.0, 0.3), "lowered threshold reveals labels at 0.33x");
        check(!FullscreenMapZoom.waypointLabelsVisible(1.0, 1.5), "raised threshold hides labels at 1x");
        check(FullscreenMapZoom.waypointLabelsVisible(0.5, 2.0), "maximum threshold includes 2x boundary");
        for (int base = 1; base <= 16; base++) {
            double bpp = FullscreenMapZoom.blocksPerPixel(base, 18);
            check(FullscreenMapZoom.waypointLabelsVisible(bpp, 0.0), "zero threshold permits labels at every coarse zoom");
        }
        check(!FullscreenMapZoom.waypointLabelsVisible(FullscreenMapZoom.blocksPerPixel(4, 1), 0.5),
                "policy uses the displayed multiplier including minimap base scale, not relative zoom alone");
        for (double invalid : new double[]{Double.NaN, Double.POSITIVE_INFINITY, -1, 2.01}) {
            check(!FullscreenMapZoom.waypointLabelsVisible(3.0, invalid), "invalid threshold safely uses 0.5x default");
            near(FullscreenMapZoom.waypointLabelZoomToSlider(invalid), 0.25, "invalid threshold restores default slider position");
        }
        near(FullscreenMapZoom.waypointLabelZoomFromSlider(Double.NaN), 0.5, "invalid slider input selects default");
        near(FullscreenMapZoom.waypointLabelZoomFromSlider(-1), 0, "slider clamps at always-show end");
        near(FullscreenMapZoom.waypointLabelZoomFromSlider(2), 2, "slider clamps at 2x end");
        for (int hundredths = 0; hundredths <= 200; hundredths++) {
            double threshold = hundredths / 100.0;
            near(FullscreenMapZoom.waypointLabelZoomFromSlider(FullscreenMapZoom.waypointLabelZoomToSlider(threshold)),
                    threshold, "every slider hundredth roundtrips when settings reopen");
        }
        check(FullscreenMapZoom.waypointLabelZoomFromSlider(0.25 + 1.0 / 142.0) > 0.5,
                "a native 150px slider keyboard step changes the setting");
    }

    private static void near(double actual, double expected, String message) {
        check(Math.abs(actual - expected) < 1e-9, message + ": " + actual);
    }

    private static void check(boolean value, String message) {
        assertions++;
        if (!value) throw new AssertionError(message);
    }
}
