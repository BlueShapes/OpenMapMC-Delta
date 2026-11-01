// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.render;

import net.aosankaku.openmapdelta.compat.TransitRail;

import java.util.ArrayList;
import java.util.List;

/** Standalone clipping/projection checks; requires no Minecraft startup. */
public final class FullscreenRailGeometryTest {
    private static int assertions;

    public static void main(String[] args) {
        clipsCrossingSegmentsFromOutsideTheViewport();
        alignsOddViewportsAndTracksNegativeCoordinates();
        retainsCurvePointsAndFollowsPanAndZoom();
        filtersSourcesAndRejectsInvalidGeometry();
        boundsVisibleSegmentWork();
        System.out.println("Fullscreen rail geometry tests passed (" + assertions + " assertions).");
    }

    private static void clipsCrossingSegmentsFromOutsideTheViewport() {
        TransitRail rail = rail(TransitRail.Source.MTR, point(-100, 0), point(100, 0));
        var segments = layout(List.of(rail), 50, 60, 100, 80, 0, 0, 1, true, false);
        check(segments.size() == 1, "a line crossing the viewport is retained when both endpoints are outside");
        near(segments.get(0).x1(), 48, "left end clips with the two-pixel stroke allowance");
        near(segments.get(0).x2(), 152, "right end clips with the two-pixel stroke allowance");
        near(segments.get(0).y1(), 100, "crossing line preserves its Y");
        check(segments.get(0).source() == TransitRail.Source.MTR, "clipped segment retains its rail source");
    }

    private static void alignsOddViewportsAndTracksNegativeCoordinates() {
        TransitRail rail = rail(TransitRail.Source.CREATE, point(-17.25, -31.5), point(-15.25, -31.5));
        var segments = layout(List.of(rail), 11, 19, 65, 49, -17.25, -31.5, 2, false, true);
        check(segments.size() == 1, "negative and fractional world coordinates remain drawable");
        near(segments.get(0).x1(), 43, "odd viewport integer center matches marker projection");
        near(segments.get(0).x2(), 44, "two blocks span one screen pixel at two blocks per pixel");
        near(segments.get(0).y1(), 43, "pan point aligns with the integer viewport center");
    }

    private static void retainsCurvePointsAndFollowsPanAndZoom() {
        List<TransitRail.Point> curve = List.of(point(0, 0), point(4, 8), point(8, 0));
        var centered = layout(List.of(rail(TransitRail.Source.CREATE, curve)), 0, 0, 101, 101, 4, 4, 2, false, true);
        check(centered.size() == 2, "a curved polyline keeps both sampled segments");
        near(centered.get(0).x1(), 48, "curve starts from exact fractional projection");
        near(centered.get(0).y2(), 52, "curve midpoint is preserved instead of replaced with a chord");
        var zoomed = layout(List.of(rail(TransitRail.Source.CREATE, curve)), 0, 0, 101, 101, 4, 4, 1, false, true);
        near(zoomed.get(0).x1(), 46, "zoom changes line position according to map scale");
        near(zoomed.get(0).y2(), 54, "zoom changes curved sample position with terrain");
    }

    private static void filtersSourcesAndRejectsInvalidGeometry() {
        var both = List.of(rail(TransitRail.Source.MTR, point(0, 0), point(10, 0)),
                rail(TransitRail.Source.CREATE, point(0, 1), point(10, 1)));
        check(layout(both, 0, 0, 100, 100, 0, 0, 1, false, false).isEmpty(), "disabled sources emit no geometry");
        check(layout(both, 0, 0, 100, 100, 0, 0, 1, true, false).size() == 1, "MTR can be toggled independently");
        check(layout(both, 0, 0, 100, 100, 0, 0, 1, false, true).size() == 1, "Create can be toggled independently");
        check(layout(List.of(rail(TransitRail.Source.MTR, point(1.0e300, 0), point(1.0e300 + 1.0e285, 0))),
                0, 0, 100, 100, 0, 0, 1, true, true).isEmpty(), "unbounded projection cannot reach GUI transforms");
        check(layout(List.of(rail(TransitRail.Source.MTR, point(0, 0), point(1, 1))),
                0, 0, 100, 100, Double.NaN, 0, 1, true, true).isEmpty(), "nonfinite pan produces no geometry");
    }

    private static void boundsVisibleSegmentWork() {
        ArrayList<TransitRail> many = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            ArrayList<TransitRail.Point> points = new ArrayList<>();
            for (int p = 0; p < 1024; p++) points.add(point(p, i));
            many.add(rail(TransitRail.Source.MTR, points));
        }
        var segments = layout(many, 0, 0, 2000, 2000, 0, 0, 1, true, false);
        check(segments.size() == FullscreenRailGeometry.MAX_VISIBLE_SEGMENTS, "dense snapshots have a fixed GUI segment budget");
        for (var segment : segments) {
            check(segment.x1() >= -2 && segment.x1() <= 2002 && segment.x2() >= -2 && segment.x2() <= 2002,
                    "clipping keeps rendered coordinates inside the bounded viewport");
        }
    }

    private static TransitRail rail(TransitRail.Source source, TransitRail.Point... points) {
        return rail(source, List.of(points));
    }

    private static TransitRail rail(TransitRail.Source source, List<TransitRail.Point> points) {
        return new TransitRail(source, "minecraft:overworld", points);
    }

    private static TransitRail.Point point(double x, double z) { return new TransitRail.Point(x, 64, z); }

    private static List<FullscreenRailGeometry.Segment> layout(List<TransitRail> rails, int x, int y, int width, int height,
                                                                double panX, double panZ, double bpp,
                                                                boolean showMtr, boolean showCreate) {
        return FullscreenRailGeometry.visibleSegments(rails,
                new FullscreenRailGeometry.Viewport(x, y, width, height, panX, panZ, bpp), showMtr, showCreate);
    }

    private static void near(double actual, double expected, String description) {
        check(Math.abs(actual - expected) < 1.0e-9, description + " (expected " + expected + ", got " + actual + ")");
    }

    private static void check(boolean result, String description) {
        assertions++;
        if (!result) throw new AssertionError(description);
    }
}
