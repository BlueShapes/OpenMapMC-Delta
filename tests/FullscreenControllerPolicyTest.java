// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.gui;

import net.aosankaku.openmapdelta.gui.FullscreenControllerPolicy.Action;

/** Semantic routing keeps popup/list selection separate from map zoom. */
public final class FullscreenControllerPolicyTest {
    private static int checks;
    public static void main(String[] args) {
        for (boolean menu : new boolean[]{false, true}) {
            for (boolean list : new boolean[]{false, true}) {
                expect("recenter", menu, list, Action.RECENTER);
                expect("toggle_waypoints", menu, list, Action.TOGGLE_WAYPOINTS);
                expect("close", menu, list, Action.CLOSE);
                expect("context", menu, list, Action.CONTEXT);
                expect("confirm", menu, list, Action.NATIVE_CLICK);
                expect("unknown", menu, list, Action.NATIVE_CLICK);
                expect(null, menu, list, Action.NATIVE_CLICK);
                expect("zoom_in", menu, list, menu ? Action.MENU_PREVIOUS : list ? Action.LIST_PREVIOUS : Action.ZOOM_IN);
                expect("zoom_out", menu, list, menu ? Action.MENU_NEXT : list ? Action.LIST_NEXT : Action.ZOOM_OUT);
            }
        }
        listRowsStayReachable();
        safePanPreservesTheWorldTransform();
        System.out.println("Fullscreen controller policy passed " + checks + " checks.");
    }

    private static void listRowsStayReachable() {
        check(FullscreenControllerPolicy.moveRow(-1, 1, 0) == -1, "empty list has no row to focus");
        check(FullscreenControllerPolicy.moveRow(-1, -1, 12) == 0, "first selection starts on the first row");
        check(FullscreenControllerPolicy.moveRow(0, -1, 12) == 0, "up on first row stays bounded");
        check(FullscreenControllerPolicy.moveRow(11, 1, 12) == 11, "down on last row stays bounded");
        check(FullscreenControllerPolicy.moveRow(4, 1, 12) == 5, "down selects one row");
        check(FullscreenControllerPolicy.moveRow(4, -1, 12) == 3, "up selects one row");
        check(FullscreenControllerPolicy.moveRow(Integer.MAX_VALUE, Integer.MAX_VALUE, 12) == 11,
                "invalid large row and step cannot overflow");
        for (int count : new int[]{1, 2, 17, 1000}) {
            for (int height : new int[]{16, 31, 32, 101, 320}) {
                int visible = ControllerNavigationLayout.visibleRows(height);
                int scroll = 0;
                for (int row = 0; row < count; row++) {
                    scroll = FullscreenControllerPolicy.revealRow(row, scroll, count, height);
                    check(row >= scroll && row < scroll + visible, "moving down keeps selected row fully visible");
                    check(scroll <= ControllerNavigationLayout.maxScroll(count, height), "scroll never reveals a blank tail");
                }
                for (int row = count - 1; row >= 0; row--) {
                    scroll = FullscreenControllerPolicy.revealRow(row, scroll, count, height);
                    check(row >= scroll && row < scroll + visible, "moving up keeps selected row fully visible");
                }
            }
        }
        check(FullscreenControllerPolicy.revealRow(4, -5, 12, 0) == 0,
                "a viewport without a full row produces a safe scroll");
    }

    private static void safePanPreservesTheWorldTransform() {
        for (double bpp : new double[]{0.25, 0.5, 1, 4, 288}) {
            for (double movement : new double[]{-12.5, -1, 0, 1, 12.5}) {
                double delta = FullscreenControllerPolicy.panWorldDelta(movement, bpp);
                check(Math.abs(delta / bpp - movement) < 1e-9,
                        "right stick GUI displacement has the same world scale as mouse dragging");
            }
        }
        for (double invalid : new double[]{Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
            check(FullscreenControllerPolicy.panWorldDelta(invalid, 1) == 0, "invalid input cannot corrupt the map center");
            check(FullscreenControllerPolicy.panWorldDelta(1, invalid) == 0, "invalid map scale cannot corrupt the center");
        }
        check(FullscreenControllerPolicy.panWorldDelta(Double.MAX_VALUE, 2) == 0,
                "overflowing input leaves the center unchanged");
        check(FullscreenControllerPolicy.panWorldDelta(12, 0) == 0, "zero world scale has no pan");
    }

    private static void expect(String input, boolean menu, boolean list, Action expected) {
        check(FullscreenControllerPolicy.resolve(input, menu, list) == expected,
                input + " uses " + expected + " in menu=" + menu + ", list=" + list);
    }
    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
}
