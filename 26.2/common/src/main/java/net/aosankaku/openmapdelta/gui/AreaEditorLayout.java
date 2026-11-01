// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; Apache-2.0.
package net.aosankaku.openmapdelta.gui;

/** Small-screen layout policy shared by the area editor and its headless layout regression test. */
public final class AreaEditorLayout {
    public static final int MAP_VIEWPORT_MIN_WIDTH = 64;
    public static final int FOOTER_HEIGHT = 22;
    public static final int CONTROL_ROW_HEIGHT = 22;
    private static final int PANEL_MAX_WIDTH = 258;
    private static final int LIST_TOP = 96;
    private static final int PANEL_TOP = 46;

    public record Layout(int panelX, int panelWidth, int mapViewportWidth, int listY, int listHeight,
                         int controlsTop, int footerTop, int controlsBottom) {
        public int bodyRowY(int row, int scrollRows) {
            return controlsTop + row * CONTROL_ROW_HEIGHT - scrollRows * CONTROL_ROW_HEIGHT;
        }

        public int maxScrollRows(int finalRow) {
            int contentBottom = controlsTop + finalRow * CONTROL_ROW_HEIGHT + 18;
            int available = Math.max(0, controlsBottom - controlsTop);
            return Math.max(0, (int) Math.ceil(Math.max(0, contentBottom - controlsTop - available)
                    / (double) CONTROL_ROW_HEIGHT));
        }

        public boolean rowVisible(int row, int scrollRows) {
            int y = bodyRowY(row, scrollRows);
            return y >= controlsTop && y + 18 <= controlsBottom;
        }
    }

    public record Rect(int x, int y, int width, int height) {
        public boolean overlaps(Rect other) {
            return x < other.x + other.width && x + width > other.x
                    && y < other.y + other.height && y + height > other.y;
        }
    }

    public record Header(Rect settings, Rect layerSlider, Rect area, Rect zoomOut, Rect zoomIn,
                        Rect grid, Rect waypoints, Rect close, Rect title) {}

    private AreaEditorLayout() {}

    public static Layout calculate(int width, int height) {
        if (width < MAP_VIEWPORT_MIN_WIDTH + 8 || height < PANEL_TOP + FOOTER_HEIGHT) {
            throw new IllegalArgumentException("Screen is too small for the area editor");
        }
        int panelWidth = Math.min(PANEL_MAX_WIDTH, Math.max(1, width - MAP_VIEWPORT_MIN_WIDTH - 8));
        int panelX = width - panelWidth;
        int viewportWidth = panelX - 4;
        int listHeight = Math.max(36, Math.min(150, (height - LIST_TOP - 80) / 4));
        int controlsTop = LIST_TOP + listHeight + 18;
        int footerTop = height - FOOTER_HEIGHT;
        return new Layout(panelX, panelWidth, viewportWidth, LIST_TOP, listHeight,
                controlsTop, footerTop, footerTop - 4);
    }

    /** Mirrors the fullscreen map's native header positions; the area entry sits on a clear second row. */
    public static Header header(int width, int layerSliderWidth) {
        return new Header(new Rect(4, 2, 50, 18), new Rect(58, 2, layerSliderWidth, 18),
                new Rect(4, 22, 90, 18), new Rect(width - 120, 2, 18, 18),
                new Rect(width - 100, 2, 18, 18), new Rect(width - 80, 2, 38, 18),
                new Rect(width - 40, 2, 18, 18), new Rect(width - 20, 2, 18, 18),
                new Rect(width / 2 - 50, 24, 100, 12));
    }
}
