package net.aosankaku.openmapdelta.gui;

import net.aosankaku.openmapdelta.area.AreaDocument;
import net.aosankaku.openmapdelta.area.MapArea;

import java.util.List;
import java.util.UUID;

/** Small-screen placement and resize regression tests for the fullscreen area editor. */
public final class AreaEditorTest {
    private static int assertions;
    private AreaEditorTest() {}

    public static void main(String[] args) {
        for (int[] size : List.of(new int[]{320, 240}, new int[]{640, 360}, new int[]{854, 480})) {
            checkLayout(size[0], size[1]);
        }
        resizeKeepsFooterReachable();
        separatesNamedLineBranchesWithoutLosingTheOriginal();
        System.out.println("PASS AreaEditorTest: " + assertions + " assertions");
    }

    private static void checkLayout(int width, int height) {
        var pane = AreaEditorLayout.calculate(width, height);
        check(pane.mapViewportWidth() >= AreaEditorLayout.MAP_VIEWPORT_MIN_WIDTH,
                "map remains usable at " + width + "x" + height);
        check(pane.mapViewportWidth() + 4 == pane.panelX(),
                "map input ends before the editor pane at " + width + "x" + height);
        check(pane.footerTop() >= pane.controlsTop() && pane.footerTop() + AreaEditorLayout.FOOTER_HEIGHT <= height,
                "fixed footer stays on screen at " + width + "x" + height);
        check(pane.rowVisible(0, 0), "first control row is visible at " + width + "x" + height);
        int lastRow = 13;
        int maxScroll = pane.maxScrollRows(lastRow);
        check(maxScroll >= 0 && pane.rowVisible(lastRow, maxScroll),
                "last control row is reachable by scrolling at " + width + "x" + height);

        int sliderWidth = Math.max(50, Math.min(150, width - 190));
        var header = AreaEditorLayout.header(width, sliderWidth);
        List<AreaEditorLayout.Rect> controls = List.of(header.settings(), header.layerSlider(), header.area(),
                header.zoomOut(), header.zoomIn(), header.grid(), header.waypoints(), header.close(), header.title());
        for (int i = 0; i < controls.size(); i++) {
            for (int j = i + 1; j < controls.size(); j++) {
                check(!controls.get(i).overlaps(controls.get(j)),
                        "fullscreen header controls do not overlap at " + width + "x" + height);
            }
        }
        check(header.area().y() + header.area().height() <= 46,
                "area entry remains above the map viewport at " + width + "x" + height);
    }

    private static void resizeKeepsFooterReachable() {
        var small = AreaEditorLayout.calculate(320, 240);
        var medium = AreaEditorLayout.calculate(640, 360);
        var large = AreaEditorLayout.calculate(854, 480);
        check(small.maxScrollRows(13) > medium.maxScrollRows(13), "smaller layout exposes more control rows by scrolling");
        check(medium.maxScrollRows(13) >= large.maxScrollRows(13), "larger layout reduces the control scroll distance");
        check(small.footerTop() == 218 && medium.footerTop() == 338 && large.footerTop() == 458,
                "save and cancel stay anchored to the resized screen footer");
    }

    private static void separatesNamedLineBranchesWithoutLosingTheOriginal() {
        UUID sourceId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        var firstPath = List.of(new MapArea.Point(0, 0), new MapArea.Point(10, 0));
        var detachedPath = List.of(new MapArea.Point(20, 0), new MapArea.Point(30, 0));
        var original = new MapArea(sourceId, "minecraft:overworld", "River", MapArea.Kind.RIVER,
                MapArea.Scope.PERSONAL, ownerId, "Mapper", null, 0,
                MapArea.Geometry.line(List.of(firstPath, detachedPath)), new MapArea.Point(15, 0),
                MapArea.Style.defaults(0xFF46B8FF));
        AreaDocument document = AreaDocument.empty().upsert(original);
        UUID newId = UUID.randomUUID();

        AreaDocument separated = AreaEditorOperations.separateLinePath(document, sourceId,
                new MapArea.Point(20, 0), "East Branch", newId);
        MapArea retained = separated.find(sourceId).orElseThrow();
        MapArea branch = separated.find(newId).orElseThrow();
        equal(List.of(firstPath), retained.geometry().paths(), "the source feature keeps its other path");
        equal(List.of(detachedPath), branch.geometry().paths(), "the selected path becomes a separate feature");
        equal("East Branch", branch.name(), "the new feature uses the entered name");
        equal(original.kind(), branch.kind(), "the new feature keeps the source kind");
        equal(original.scope(), branch.scope(), "the new feature keeps the selected storage scope");
        equal(original.ownerId(), branch.ownerId(), "the new feature remains under the same owner");
        equal(2, separated.areas().size(), "separation is one atomic document edit");

        expectIllegal(() -> AreaEditorOperations.separateLinePath(
                AreaDocument.empty().upsert(original.withGeometry(MapArea.Geometry.line(List.of(firstPath)))),
                sourceId, firstPath.get(0), "Only path"), "separation cannot empty the original feature");
        expectIllegal(() -> AreaEditorOperations.separateLinePath(document, sourceId,
                new MapArea.Point(99, 99), "Missing path"), "separation requires a vertex on the chosen path");
    }

    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }

    private static void equal(Object expected, Object actual, String message) {
        assertions++;
        if (!expected.equals(actual)) throw new AssertionError(message + ": expected=" + expected + ", actual=" + actual);
    }

    private static void expectIllegal(Runnable action, String message) {
        assertions++;
        try { action.run(); }
        catch (IllegalArgumentException expected) { return; }
        throw new AssertionError(message);
    }
}
