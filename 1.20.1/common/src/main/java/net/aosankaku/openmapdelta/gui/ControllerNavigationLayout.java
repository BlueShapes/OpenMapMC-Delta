// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.gui;

import java.util.ArrayList;
import java.util.List;

/** Pure screen-coordinate layout helpers for controller navigation targets. */
public final class ControllerNavigationLayout {
    public static final int ROW_HEIGHT = 16;
    public static final int LEFT_PADDING = 2;
    public static final int FAVORITE_WIDTH = 12;
    public static final int VISIBILITY_WIDTH = 12;
    public static final int ICON_WIDTH = 10;

    private ControllerNavigationLayout() {
    }

    public record Point(double x, double y) {
    }

    /**
     * Returns favorite, visibility, and label centers for fully visible marker rows.
     * Scroll is an absolute first-row index and must be a legal no-blank-tail page.
     */
    public static List<Point> markerRows(int x, int y, int width, int height, int scroll, int total) {
        int rows = visibleRows(height);
        int maximumScroll = maxScroll(total, height);
        if (width <= LEFT_PADDING + FAVORITE_WIDTH + VISIBILITY_WIDTH + ICON_WIDTH
                || rows == 0 || total <= 0 || scroll < 0 || scroll > maximumScroll) {
            return List.of();
        }

        double favoriteX = (double) x + LEFT_PADDING + FAVORITE_WIDTH / 2.0;
        double visibilityX = (double) x + LEFT_PADDING + FAVORITE_WIDTH + VISIBILITY_WIDTH / 2.0;
        double labelLeft = (double) x + LEFT_PADDING + FAVORITE_WIDTH + VISIBILITY_WIDTH + ICON_WIDTH;
        double selectX = labelLeft + ((double) width - (labelLeft - x)) / 2.0;
        List<Point> points = new ArrayList<>();
        int end = (int) Math.min((long) total, (long) scroll + rows);
        for (int row = scroll; row < end; row++) {
            double centerY = (double) y + (long) (row - scroll) * ROW_HEIGHT + ROW_HEIGHT / 2.0;
            points.add(new Point(favoriteX, centerY));
            points.add(new Point(visibilityX, centerY));
            points.add(new Point(selectX, centerY));
        }
        return List.copyOf(points);
    }

    /** Returns centers for popup options after the popup's one-row header. */
    public static List<Point> contextRows(int x, int y, int width, int height, int padding,
            int rowHeight, int optionCount) {
        if (width <= 0 || height <= 0 || padding < 0 || rowHeight <= 0 || optionCount <= 0) {
            return List.of();
        }
        List<Point> points = new ArrayList<>();
        double centerX = (double) x + width / 2.0;
        long bottom = (long) y + height;
        for (int index = 0; index < optionCount; index++) {
            long rowTop = (long) y + padding + (long) (index + 1) * rowHeight;
            long rowBottom = rowTop + rowHeight;
            if (rowTop < y || rowBottom > bottom) {
                break;
            }
            points.add(new Point(centerX, rowTop + rowHeight / 2.0));
        }
        return List.copyOf(points);
    }

    /** Filters invalid/off-screen targets and removes duplicate centers, preserving order. */
    public static List<Point> visibleUnique(List<Point> points, int screenWidth, int screenHeight) {
        if (points == null || screenWidth <= 0 || screenHeight <= 0) {
            return List.of();
        }
        List<Point> result = new ArrayList<>();
        for (Point point : points) {
            if (point == null || !Double.isFinite(point.x()) || !Double.isFinite(point.y())
                    || point.x() < 0.0 || point.x() >= screenWidth
                    || point.y() < 0.0 || point.y() >= screenHeight) {
                continue;
            }
            boolean duplicate = false;
            for (Point existing : result) {
                if (existing.x() == point.x() && existing.y() == point.y()) {
                    duplicate = true;
                    break;
                }
            }
            if (!duplicate) {
                result.add(point);
            }
        }
        return List.copyOf(result);
    }

    public static int visibleRows(int height) {
        return height <= 0 ? 0 : height / ROW_HEIGHT;
    }

    /** Maximum first-row index that shows a full page without a blank tail. */
    public static int maxScroll(int total, int height) {
        int rows = visibleRows(height);
        if (total <= 0 || rows <= 0) {
            return 0;
        }
        return Math.max(0, total - rows);
    }

    public static int clampScroll(int requested, int total, int height) {
        return Math.max(0, Math.min(requested, maxScroll(total, height)));
    }

    /** Returns the absolute row index under the mouse, or -1 if it is outside a drawn row. */
    public static int rowAt(double mouseX, double mouseY, int x, int y, int width, int height,
            int scroll, int total) {
        int rows = visibleRows(height);
        int maximumScroll = maxScroll(total, height);
        if (!Double.isFinite(mouseX) || !Double.isFinite(mouseY) || width <= 0 || rows <= 0
                || total <= 0 || scroll < 0 || scroll > maximumScroll
                || mouseX < x || mouseX >= (double) x + width
                || mouseY < y || mouseY >= (double) y + height) {
            return -1;
        }
        int localRow = (int) ((mouseY - y) / ROW_HEIGHT);
        if (localRow < 0 || localRow >= rows) {
            return -1;
        }
        long index = (long) scroll + localRow;
        return index < total ? (int) index : -1;
    }
}
