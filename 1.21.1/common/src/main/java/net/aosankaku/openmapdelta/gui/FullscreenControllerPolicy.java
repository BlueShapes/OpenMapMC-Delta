// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.gui;

/** Pure action and cursor-navigation policy shared by fullscreen controller integration. */
public final class FullscreenControllerPolicy {
    public enum Action {
        RECENTER, TOGGLE_WAYPOINTS, CLOSE, CONTEXT, NATIVE_CLICK, ZOOM_IN, ZOOM_OUT,
        MENU_PREVIOUS, MENU_NEXT, LIST_PREVIOUS, LIST_NEXT
    }

    private FullscreenControllerPolicy() {}

    public static Action resolve(String action, boolean menuOpen, boolean listFocused) {
        if (action == null) return Action.NATIVE_CLICK;
        return switch (action) {
            case "recenter" -> Action.RECENTER;
            case "toggle_waypoints" -> Action.TOGGLE_WAYPOINTS;
            case "close" -> Action.CLOSE;
            case "context" -> Action.CONTEXT;
            case "zoom_in" -> menuOpen ? Action.MENU_PREVIOUS
                    : listFocused ? Action.LIST_PREVIOUS : Action.ZOOM_IN;
            case "zoom_out" -> menuOpen ? Action.MENU_NEXT
                    : listFocused ? Action.LIST_NEXT : Action.ZOOM_OUT;
            default -> Action.NATIVE_CLICK;
        };
    }

    public static int moveRow(int current, int delta, int count) {
        if (count <= 0) return -1;
        long requested = (long) Math.max(0, current) + delta;
        return (int) Math.max(0L, Math.min((long) count - 1L, requested));
    }

    public static int revealRow(int row, int scroll, int total, int height) {
        int visibleRows = ControllerNavigationLayout.visibleRows(height);
        if (total <= 0 || visibleRows <= 0) return 0;
        int boundedRow = Math.max(0, Math.min(row, total - 1));
        int boundedScroll = ControllerNavigationLayout.clampScroll(scroll, total, height);
        long pageEnd = (long) boundedScroll + visibleRows;
        if (boundedRow < boundedScroll) boundedScroll = boundedRow;
        else if (boundedRow >= pageEnd) boundedScroll = boundedRow - visibleRows + 1;
        return ControllerNavigationLayout.clampScroll(boundedScroll, total, height);
    }

    public static double panWorldDelta(double guiDelta, double blocksPerPixel) {
        if (!Double.isFinite(guiDelta) || !Double.isFinite(blocksPerPixel) || blocksPerPixel <= 0) return 0;
        double worldDelta = guiDelta * blocksPerPixel;
        return Double.isFinite(worldDelta) ? worldDelta : 0;
    }
}
