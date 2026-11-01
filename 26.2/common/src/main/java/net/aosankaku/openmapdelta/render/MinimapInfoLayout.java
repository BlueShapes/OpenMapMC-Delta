// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.render;

/** Minimum GUI-space frame prevents six information slots colliding on a tiny minimap. */
public final class MinimapInfoLayout {
    private MinimapInfoLayout() {}
    public static int width(int screenWidth, int mapSize) {
        return Math.min(Math.max(0, screenWidth - 6), Math.max(120, mapSize));
    }
    public static int height(int mapSize) { return height(mapSize, 12); }
    public static int height(int mapSize, int lineHeight) {
        return Math.max(Math.max(48, mapSize), 2 * Math.max(12, lineHeight) + 18);
    }
    public static int lineHeight(int fontLineHeight, double guiScale) {
        double scale = MinimapSizing.textScale(0.6, 1.0, guiScale, fontLineHeight);
        return Math.max(12, (int) Math.ceil(fontLineHeight * scale));
    }
    public static int topReserve(int lineHeight, int compassOverhang) {
        return Math.max(12, lineHeight) + 3 + Math.max(0, compassOverhang);
    }
    public static int bottomReserve(int lineHeight, int compassOverhang) {
        return Math.max(12, lineHeight) + 4 + Math.max(0, compassOverhang);
    }
    public static int left(int screenWidth, int mapX, int mapSize) {
        int frameWidth = width(screenWidth, mapSize);
        return Math.max(3, Math.min(mapX + (mapSize - frameWidth) / 2, screenWidth - frameWidth - 3));
    }
    public static int cornerTextWidth(int frameWidth) { return Math.max(1, frameWidth / 2 - 12); }

    /** Leave equal space on both sides of the map center, including background padding. */
    public static int centeredTextWidth(int screenWidth, int mapX, int mapSize) {
        float center = mapX + mapSize / 2.0f;
        return Math.max(1, (int) Math.floor(2 * Math.min(center - 5, screenWidth - 5 - center)));
    }

    public static float centeredTextLeft(int mapX, int mapSize, float textWidth) {
        return mapX + mapSize / 2.0f - textWidth / 2.0f;
    }
}
