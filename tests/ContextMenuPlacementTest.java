// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.gui;

public final class ContextMenuPlacementTest {
    private static int assertions;

    public static void main(String[] args) {
        checkPosition(100, 80, 120, 100, 640, 360, 110, 90);
        checkPosition(630, 350, 120, 100, 640, 360, 500, 240);
        checkPosition(2, 2, 120, 100, 640, 360, 12, 12);
        checkPosition(630, 2, 120, 100, 640, 360, 500, 12);
        checkPosition(2, 350, 120, 100, 640, 360, 12, 240);
        // An oversized horizontal popup can still leave the anchor visible vertically.
        var constrained = ContextMenuPlacement.place(90, 40, 172, 90, 180, 160);
        check(constrained.y() == 50, "constrained popup preserves vertical gap");
        check(ContextMenuPlacement.availableHeight(100, 80, 120, 640, 160) == 152,
                "short wide screen preserves readable rows when horizontal space is available");
        check(ContextMenuPlacement.availableHeight(160, 80, 240, 320, 160) == 66,
                "vertical compression is reserved for horizontally constrained popups");
        var compactRows = ContextMenuPlacement.rows(8, 76);
        check(compactRows.padding() == 2 && compactRows.rowHeight() == 9 && compactRows.height() == 76,
                "compact padding preserves font height at the boundary");
        for (int count = 2; count <= 12; count++) {
            for (int available = count * 9; available <= 400; available++) {
                var rows = ContextMenuPlacement.rows(count, available);
                check(rows.rowHeight() >= 9, "readable rows whenever the font fits");
                check(rows.height() <= available && rows.padding() >= 0 && rows.padding() <= 4,
                        "row geometry fits the available screen height");
            }
        }
        for (int y = 0; y < 240; y++) {
            int height = ContextMenuPlacement.availableHeight(y, 240);
            var p = ContextMenuPlacement.place(160, y, 240, height, 320, 240);
            check(p.y() >= 4 && p.y() + height <= 236, "compressed popup fits a narrow screen");
            check(y + 5 < p.y() || y - 5 >= p.y() + height, "narrow screen keeps the click circle visible");
        }
        for (int x = 0; x < 640; x += 7) {
            for (int y = 0; y < 360; y += 7) {
                var p = ContextMenuPlacement.place(x, y, 140, 170, 640, 360);
                check(p.x() >= 4 && p.x() + 140 <= 636, "horizontal screen bounds");
                check(p.y() >= 4 && p.y() + 170 <= 356, "vertical screen bounds");
                check(x + 5 < p.x() || x - 5 >= p.x() + 140
                        || y + 5 < p.y() || y - 5 >= p.y() + 170,
                        "click circle remains outside a popup when space permits");
            }
        }
        var tiny = ContextMenuPlacement.place(0, 0, 10, 10, 1, 1);
        check(tiny.x() == 0 && tiny.y() == 0, "tiny screen has nonnegative placement");
        var extreme = ContextMenuPlacement.place(Integer.MAX_VALUE, Integer.MIN_VALUE, 120, 100, 640, 360);
        check(extreme.x() == 4 && extreme.y() == 256, "extreme anchors do not overflow");
        System.out.println("Context menu placement tests passed (" + assertions + " assertions).");
    }

    private static void checkPosition(int x, int y, int w, int h, int sw, int sh, int expectedX, int expectedY) {
        var p = ContextMenuPlacement.place(x, y, w, h, sw, sh);
        check(p.x() == expectedX && p.y() == expectedY, "popup flips toward available space");
    }

    private static void check(boolean success, String message) {
        if (!success) throw new AssertionError(message);
        assertions++;
    }
}
