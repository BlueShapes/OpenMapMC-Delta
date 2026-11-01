// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.marker;

public final class PlayerMarkerSizing {
    private static final int FULLSCREEN_HEAD_SIZE = 16;
    private static final int MIN_FULLSCREEN_HEAD_SIZE = 4;
    private static final int FULLSCREEN_DOT_SIZE = 6;

    private PlayerMarkerSizing() {}

    /** Returns the centered fullscreen marker's width in GUI pixels. */
    public static int fullscreenSize(double blocksPerPixel, boolean showHead) {
        return fullscreenSize(blocksPerPixel, showHead, FULLSCREEN_HEAD_SIZE);
    }

    /** Returns the centered fullscreen marker size without exceeding the configured cap. */
    public static int fullscreenSize(double blocksPerPixel, boolean showHead, int maximumSize) {
        int cap = Math.max(1, maximumSize);
        if (!showHead) return Math.min(cap, MarkerIconSizing.fullscreenSize(blocksPerPixel, false));
        double bpp = Math.max(0.5, blocksPerPixel);
        int minimum = Math.min(MIN_FULLSCREEN_HEAD_SIZE, cap);
        return Math.min(cap, Math.max(minimum, Math.round(FULLSCREEN_HEAD_SIZE / (float) bpp)));
    }

    /** Returns an even square extent that contains a centered glyph at every allowed rotation. */
    public static int footprintSize(int glyphSize, boolean canRotate) {
        double half = Math.max(0, glyphSize) / 2.0;
        return (int) (2 * Math.ceil(canRotate ? half * Math.sqrt(2.0) : half));
    }
}
