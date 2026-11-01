// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.gui;

import java.util.ArrayList;
import java.util.List;

public final class ControllerNavigationLayoutTest {
    private static int assertions;

    public static void main(String[] args) {
        markerColumnsAndRows();
        contextHeaderAndClipping();
        scrollingAndRowPicking();
        visibleFilteringAndDeduplication();
        invalidAndExtremeInputs();
        System.out.println("Controller navigation layout tests passed (" + assertions + " assertions).");
    }

    private static void markerColumnsAndRows() {
        var points = ControllerNavigationLayout.markerRows(10, 20, 100, 33, 0, 4);
        check(points.size() == 6, "only fully visible marker rows get targets");
        check(points.get(0).equals(new ControllerNavigationLayout.Point(18, 28)), "favorite center");
        check(points.get(1).equals(new ControllerNavigationLayout.Point(30, 28)), "visibility center");
        check(points.get(2).equals(new ControllerNavigationLayout.Point(78, 28)), "label center after icon columns");
        check(points.get(3).equals(new ControllerNavigationLayout.Point(18, 44)), "second row center");
        var scrolled = ControllerNavigationLayout.markerRows(10, 20, 100, 32, 2, 4);
        check(scrolled.size() == 6 && scrolled.get(0).y() == 28, "scrolled list lays out viewport rows from top");
    }

    private static void contextHeaderAndClipping() {
        var points = ControllerNavigationLayout.contextRows(10, 20, 80, 51, 3, 12, 5);
        check(points.size() == 3, "only complete popup options are navigable");
        check(points.get(0).equals(new ControllerNavigationLayout.Point(50, 41)), "first option follows header");
        check(points.get(2).equals(new ControllerNavigationLayout.Point(50, 65)), "option centers advance by row height");
        check(ControllerNavigationLayout.contextRows(0, 0, 20, 30, 0, 10, 2).size() == 2,
                "exactly fitting option rows remain visible");
    }

    private static void scrollingAndRowPicking() {
        check(ControllerNavigationLayout.visibleRows(47) == 2, "partial row does not count as visible");
        check(ControllerNavigationLayout.maxScroll(8, 48) == 5, "last page stops at final full page");
        check(ControllerNavigationLayout.maxScroll(2, 48) == 0, "short list does not scroll");
        check(ControllerNavigationLayout.clampScroll(-4, 8, 48) == 0, "negative requested scroll clamps to start");
        check(ControllerNavigationLayout.clampScroll(99, 8, 48) == 5, "scroll clamps without a blank tail");
        check(ControllerNavigationLayout.rowAt(4, 4, 4, 4, 30, 33, 1, 5) == 1, "top row is picked");
        check(ControllerNavigationLayout.rowAt(4, 20, 4, 4, 30, 33, 1, 5) == 2, "row hit returns absolute index");
        check(ControllerNavigationLayout.rowAt(4, 36, 4, 4, 30, 33, 1, 5) == -1,
                "partial bottom row cannot be picked");
        check(ControllerNavigationLayout.rowAt(34, 4, 4, 4, 30, 33, 1, 5) == -1,
                "right edge is outside widget");
        check(ControllerNavigationLayout.rowAt(4, 4, 4, 4, 30, 48, 6, 8) == -1,
                "invalid scroll is rejected");
    }

    private static void visibleFilteringAndDeduplication() {
        List<ControllerNavigationLayout.Point> input = new ArrayList<>();
        input.add(new ControllerNavigationLayout.Point(1, 1));
        input.add(new ControllerNavigationLayout.Point(1, 1));
        input.add(new ControllerNavigationLayout.Point(-1, 2));
        input.add(new ControllerNavigationLayout.Point(10, 2));
        input.add(new ControllerNavigationLayout.Point(2, 10));
        input.add(new ControllerNavigationLayout.Point(Double.NaN, 3));
        input.add(new ControllerNavigationLayout.Point(3, Double.POSITIVE_INFINITY));
        input.add(null);
        var filtered = ControllerNavigationLayout.visibleUnique(input, 10, 10);
        check(filtered.equals(List.of(new ControllerNavigationLayout.Point(1, 1))),
                "visible targets are finite, in bounds, and unique in original order");
        check(ControllerNavigationLayout.visibleUnique(input, 0, 10).isEmpty(), "invalid screen is empty");
    }

    private static void invalidAndExtremeInputs() {
        check(ControllerNavigationLayout.markerRows(0, 0, 36, 32, 0, 2).isEmpty(), "empty label area is rejected");
        check(ControllerNavigationLayout.markerRows(0, 0, 80, 32, -1, 2).isEmpty(), "negative marker scroll is rejected");
        check(ControllerNavigationLayout.markerRows(0, 0, 80, 32, 1, 2).isEmpty(), "overscroll is rejected");
        check(ControllerNavigationLayout.markerRows(0, 0, 80, 15, 0, 2).isEmpty(), "partial first row is omitted");
        check(ControllerNavigationLayout.contextRows(0, 0, 0, 20, 0, 10, 1).isEmpty(), "zero popup width is rejected");
        check(ControllerNavigationLayout.contextRows(0, 0, 20, 20, -1, 10, 1).isEmpty(), "negative popup padding is rejected");
        check(ControllerNavigationLayout.visibleRows(Integer.MAX_VALUE) == Integer.MAX_VALUE / 16,
                "large heights do not overflow row count");
        check(ControllerNavigationLayout.rowAt(Double.NaN, 0, 0, 0, 20, 20, 0, 1) == -1,
                "non-finite pointer input is rejected");
        check(ControllerNavigationLayout.contextRows(Integer.MAX_VALUE, Integer.MAX_VALUE,
                Integer.MAX_VALUE, Integer.MAX_VALUE, 1, Integer.MAX_VALUE, 1).isEmpty(),
                "extreme popup coordinates do not overflow into visible rows");
    }

    private static void check(boolean success, String message) {
        if (!success) throw new AssertionError(message);
        assertions++;
    }
}
