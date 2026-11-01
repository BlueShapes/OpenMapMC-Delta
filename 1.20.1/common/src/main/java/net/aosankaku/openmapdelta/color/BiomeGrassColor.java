// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.color;

/** Biome RGB to texture pixels: ABGR before 26.1, ARGB in newer Minecraft versions. */
public final class BiomeGrassColor {
    private BiomeGrassColor() {}

    public static boolean allowsDimension(String id) {
        return !"minecraft:the_nether".equals(id) && !"minecraft:the_end".equals(id);
    }

    public static int pixel(int rgb, int brightness, boolean leaves, boolean abgr) {
        double factor = Math.max(0, Math.min(255, brightness)) / 255.0 * (leaves ? 0.75 : 1.0);
        int red = (int) (((rgb >>> 16) & 255) * factor);
        int green = (int) (((rgb >>> 8) & 255) * factor);
        int blue = (int) ((rgb & 255) * factor);
        return 0xFF000000 | (abgr ? (blue << 16) | (green << 8) | red : (red << 16) | (green << 8) | blue);
    }
}
