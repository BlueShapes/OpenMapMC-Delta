// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.gui;

/** Positions a popup away from its click anchor, flipping at the screen edges. */
public final class ContextMenuPlacement {
    private static final int GAP = 10;
    private static final int MARGIN = 4;

    private ContextMenuPlacement() {}

    public record Position(int x, int y) {}
    public record Rows(int padding, int rowHeight, int height) {}

    /** Spend only spare vertical space on padding, preserving the nine-pixel font height. */
    public static Rows rows(int count, int availableHeight) {
        int rows = Math.max(1, count);
        int available = Math.max(1, availableHeight);
        int padding = (int) Math.min(4L, Math.max(0L, ((long) available - rows * 9L) / 2));
        int rowHeight = Math.max(1, Math.min(20, (available - padding * 2) / rows));
        int height = (int) Math.min(available, padding * 2L + rows * (long) rowHeight);
        return new Rows(padding, rowHeight, height);
    }

    /** Use normal-height rows when the popup can avoid the click point horizontally. */
    public static int availableHeight(int anchorX, int anchorY, int popupWidth,
                                      int screenWidth, int screenHeight) {
        long after = (long) anchorX + GAP;
        long before = (long) anchorX - GAP - popupWidth;
        if (after >= MARGIN && after + popupWidth <= (long) screenWidth - MARGIN
                || before >= MARGIN && before + popupWidth <= (long) screenWidth - MARGIN) {
            return Math.max(1, screenHeight - 2 * MARGIN);
        }
        return availableHeight(anchorY, screenHeight);
    }

    /** Reserve enough vertical space to keep the anchor visible even on narrow screens. */
    public static int availableHeight(int anchorY, int screenHeight) {
        long screen = Math.max(0, screenHeight);
        long side = Math.max((long) anchorY - MARGIN - GAP, screen - anchorY - MARGIN - GAP);
        return (int) Math.max(1, Math.min(screen - 2 * MARGIN, side));
    }

    public static Position place(int anchorX, int anchorY, int popupWidth, int popupHeight,
                                 int screenWidth, int screenHeight) {
        return new Position(axis(anchorX, popupWidth, screenWidth),
                axis(anchorY, popupHeight, screenHeight));
    }

    private static int axis(int anchor, int popupSize, int screenSize) {
        int size = Math.max(0, popupSize);
        int screen = Math.max(0, screenSize);
        int margin = Math.min(MARGIN, Math.max(0, (screen - size) / 2));
        int maximum = Math.max(margin, screen - size - margin);
        long after = (long) anchor + GAP;
        long before = (long) anchor - GAP - size;
        if (after >= margin && after <= maximum) return (int) after;
        if (before >= margin && before <= maximum) return (int) before;
        // If neither side fits, choose the edge leaving the larger visible gap.
        long beforeSpace = (long) anchor - margin;
        long afterSpace = (long) screen - margin - anchor;
        return afterSpace >= beforeSpace ? maximum : margin;
    }
}
