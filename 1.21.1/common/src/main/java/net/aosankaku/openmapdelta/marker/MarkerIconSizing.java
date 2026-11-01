// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.marker;

/** Marker geometry shared by drawing and cursor hit testing. */
public final class MarkerIconSizing {
    private MarkerIconSizing() {}
    /** Positive offsets place clamped icon centers outside the terrain circle. */
    public static float minimapEdgeRadius(int size, double offset) {
        return Math.max(0.0f, size / 2.0f + (float) offset);
    }

    /** Reserve the larger navigation halo as well as the icon itself outside the map. */
    public static double minimapEdgeOverhang(double offset, int iconSize) {
        return Math.max(0.0, offset + (Math.max(2, iconSize) + 6) / 2 + 1);
    }

    public static int fullscreenSize(double blocksPerPixel, boolean hasIcon) {
        double bpp = Math.max(0.5, blocksPerPixel);
        return Math.max(hasIcon ? 4 : 2, (int) Math.round((hasIcon ? 16 : 5) / Math.sqrt(bpp)));
    }
    public static boolean contains(double mouseX, double mouseY, int x, int y, int size) {
        int left = x - size / 2;
        int top = y - size / 2;
        return mouseX >= left && mouseX < left + size && mouseY >= top && mouseY < top + size;
    }
}
