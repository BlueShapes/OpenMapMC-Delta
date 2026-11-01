// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.marker;

import net.aosankaku.openmapdelta.render.FullscreenMapZoom;

/** Fullscreen self-head sizing and selection, based on the current cursor position. */
public final class FullscreenLocalPlayerSizing {
    private static final double MIN_NORMAL_SIZE = 4.0;

    private FullscreenLocalPlayerSizing() {}

    /** The ordinary self head is 25% smaller than a head for another player. */
    public static int normalSize(double blocksPerPixel, int maximumSize) {
        int cap = Math.max(1, maximumSize);
        int fullSize = PlayerMarkerSizing.fullscreenSize(blocksPerPixel, true, cap);
        int oneBlockSize = oneBlockSize(blocksPerPixel);
        return Math.min(cap, Math.max(oneBlockSize,
                Math.max(Math.min(cap, (int) MIN_NORMAL_SIZE), Math.round(fullSize * 0.75f))));
    }

    /** Return one of five stable size steps. Unknown cursor distance keeps the normal size. */
    public static int size(int normalSize, double blocksPerPixel, double cursorDistance) {
        int maximum = Math.max(1, normalSize);
        int minimum = Math.min(maximum, oneBlockSize(blocksPerPixel));
        if (!Double.isFinite(cursorDistance)) return maximum;

        double start = maximum * 1.5;
        double finish = maximum * 0.5;
        double progress = Math.max(0.0, Math.min(1.0, (cursorDistance - finish) / (start - finish)));
        int stage = (int) Math.ceil(progress * 4.0);
        return Math.max(minimum, (int) Math.round(minimum + (maximum - minimum) * stage / 4.0));
    }

    /** Test the same symmetric glyph envelope used by rendering, without an enlarged odd-size hit box. */
    public static boolean contains(double mouseX, double mouseY, int centerX, int centerY,
                                   int glyphSize, boolean rotateHead) {
        double half = Math.max(0, glyphSize) / 2.0;
        if (rotateHead) half *= Math.sqrt(2.0);
        return mouseX >= centerX - half && mouseX < centerX + half
                && mouseY >= centerY - half && mouseY < centerY + half;
    }

    private static int oneBlockSize(double blocksPerPixel) {
        double bpp = FullscreenMapZoom.safeBlocksPerPixel(blocksPerPixel);
        return Math.max(1, (int) Math.ceil(1.0 / bpp));
    }
}
