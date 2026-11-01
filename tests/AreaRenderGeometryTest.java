package net.aosankaku.openmapdelta.area;

import java.util.List;

/** Pixel-space clipping regression tests for fullscreen area fills. */
public final class AreaRenderGeometryTest {
    private static int assertions;

    private AreaRenderGeometryTest() {}

    public static void main(String[] args) {
        ringAndHoleClipAtViewport();
        disconnectedIslandsRemainSeparate();
        overlappingPartsAreUnioned();
        extremeWorldCoordinatesRemainBoundedByClip();
        nonPolygonGeometryDoesNotProduceFillSpans();
        System.out.println("PASS AreaRenderGeometryTest: " + assertions + " assertions");
    }

    private static void ringAndHoleClipAtViewport() {
        var polygon = new MapArea.Polygon(rectangle(0, 0, 10, 10),
                List.of(rectangle(3, 3, 7, 7)));
        var geometry = MapArea.Geometry.polygon(List.of(polygon));
        var spans = AreaRenderGeometry.scanlines(geometry,
                new AreaRenderGeometry.Projection(0, 0, 20, 20, 5, 5, 1),
                new AreaRenderGeometry.Clip(6, 6, 8, 8));

        equal(8L, spans.stream().map(AreaRenderGeometry.Span::y).distinct().count(),
                "only clipped scanlines are emitted");
        var rowBeforeHole = spans.stream().filter(span -> span.y() == 7).toList();
        equal(1, rowBeforeHole.size(), "solid part before hole is filled as one run");
        equal(new AreaRenderGeometry.Span(7, 6, 14), rowBeforeHole.get(0),
                "outer shape is clipped to the visible columns");

        var rowThroughHole = spans.stream().filter(span -> span.y() == 8).toList();
        equal(List.of(new AreaRenderGeometry.Span(8, 6, 8),
                        new AreaRenderGeometry.Span(8, 12, 14)),
                rowThroughHole, "hole leaves a gap in the filled row");
        var rowBelowClip = AreaRenderGeometry.scanlines(geometry,
                new AreaRenderGeometry.Projection(0, 0, 20, 20, 5, 5, 1),
                new AreaRenderGeometry.Clip(0, 0, 20, 5));
        check(rowBelowClip.stream().noneMatch(span -> span.y() >= 5),
                "rows outside the clip are never returned");
    }

    private static void disconnectedIslandsRemainSeparate() {
        var geometry = MapArea.Geometry.polygon(List.of(
                new MapArea.Polygon(rectangle(-8, -2, -4, 2), List.of()),
                new MapArea.Polygon(rectangle(4, -2, 8, 2), List.of())));
        var spans = AreaRenderGeometry.scanlines(geometry,
                new AreaRenderGeometry.Projection(0, 0, 40, 24, 0, 0, 1),
                new AreaRenderGeometry.Clip(0, 0, 40, 24));
        check(spans.stream().anyMatch(span -> span.y() == 10 && span.xStart() < 16),
                "western island is inside the viewport");
        check(spans.stream().anyMatch(span -> span.y() == 10 && span.xStart() >= 24),
                "eastern island is inside the viewport");
        check(spans.stream().noneMatch(span -> span.y() == 10 && span.xStart() <= 20 && span.xEnd() >= 20),
                "uncovered space between islands remains empty");
    }

    private static void overlappingPartsAreUnioned() {
        var geometry = MapArea.Geometry.polygon(List.of(
                new MapArea.Polygon(rectangle(-5, -4, 2, 4), List.of()),
                new MapArea.Polygon(rectangle(-2, -4, 5, 4), List.of())));
        var spans = AreaRenderGeometry.scanlines(geometry,
                new AreaRenderGeometry.Projection(0, 0, 24, 16, 0, 0, 1),
                new AreaRenderGeometry.Clip(0, 0, 24, 16));
        equal(List.of(new AreaRenderGeometry.Span(8, 7, 17)),
                spans.stream().filter(span -> span.y() == 8).toList(),
                "overlapping polygon parts render as one continuous surface");
    }

    private static void extremeWorldCoordinatesRemainBoundedByClip() {
        var worldSized = MapArea.Geometry.polygon(List.of(new MapArea.Polygon(
                rectangle(-30_000_000, -30_000_000, 30_000_000, 30_000_000), List.of())));
        var clip = new AreaRenderGeometry.Clip(2, 3, 11, 7);
        var spans = AreaRenderGeometry.scanlines(worldSized,
                new AreaRenderGeometry.Projection(0, 0, 20, 14, 0, 0, 1), clip);
        equal(clip.height(), spans.size(), "large world geometry is bounded by viewport rows");
        check(spans.stream().allMatch(span -> span.y() >= clip.y()
                        && span.y() < clip.y() + clip.height()
                        && span.xStart() >= clip.x()
                        && span.xEnd() <= clip.x() + clip.width()),
                "every output span stays within the clip bounds");
    }

    private static void nonPolygonGeometryDoesNotProduceFillSpans() {
        equal(List.of(), AreaRenderGeometry.scanlines(MapArea.Geometry.label(),
                        new AreaRenderGeometry.Projection(0, 0, 32, 24, 0, 0, 1),
                        new AreaRenderGeometry.Clip(0, 0, 32, 24)),
                "a label has no polygon fill");
        equal(List.of(), AreaRenderGeometry.scanlines(MapArea.Geometry.line(List.of(
                                List.of(new MapArea.Point(0, 0), new MapArea.Point(10, 10)))),
                        new AreaRenderGeometry.Projection(0, 0, 32, 24, 0, 0, 1),
                        new AreaRenderGeometry.Clip(0, 0, 32, 24)),
                "a river path is not filled like a polygon");
    }

    private static List<MapArea.Point> rectangle(double left, double top, double right, double bottom) {
        return List.of(new MapArea.Point(left, top), new MapArea.Point(right, top),
                new MapArea.Point(right, bottom), new MapArea.Point(left, bottom));
    }

    private static void check(boolean condition, String description) {
        assertions++;
        if (!condition) throw new AssertionError(description);
    }

    private static void equal(Object expected, Object actual, String description) {
        assertions++;
        if (!expected.equals(actual)) {
            throw new AssertionError(description + ": expected=" + expected + ", actual=" + actual);
        }
    }
}
