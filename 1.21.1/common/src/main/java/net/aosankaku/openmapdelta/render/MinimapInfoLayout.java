// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.render;

import net.aosankaku.openmapdelta.config.MinimapInfoSlot;

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

    /**
     * Places one info label in physical pixels so integer backing edges and
     * transformed text origins use the same raster grid at fractional GUI scales.
     */
    public static PhysicalPlacement physicalPlacement(MinimapInfoSlot slot, boolean centerCorners,
                                                       int screenWidth, int screenHeight,
                                                       int mapX, int mapY, int mapSize,
                                                       int frameX, int frameWidth, int frameHeight,
                                                       int fontLineHeight, int compassOverhang,
                                                       int bottomCompassOverhang, int measuredTextWidth,
                                                       double guiScale, double localTextScale) {
        boolean centered = slot == MinimapInfoSlot.TOP || slot == MinimapInfoSlot.BOTTOM;
        boolean upper = slot == MinimapInfoSlot.TOP || slot == MinimapInfoSlot.TOP_LEFT
                || slot == MinimapInfoSlot.TOP_RIGHT;
        boolean leftAligned = slot == MinimapInfoSlot.TOP_LEFT || slot == MinimapInfoSlot.BOTTOM_LEFT;
        centerCorners = centerCorners && !centered;
        double scale = Double.isFinite(guiScale) && guiScale > 0 ? guiScale : 1.0;
        double textScale = Math.max(0.01, localTextScale * scale);
        double inkWidth = Math.max(0, measuredTextWidth) * textScale;
        int inkHeight = (int) Math.ceil(Math.max(1, fontLineHeight) * textScale);
        int horizontalPadding = Math.max(1, (int) Math.round(2 * scale));
        int verticalPadding = Math.max(1, (int) Math.round(scale));
        int rowHeight = inkHeight + verticalPadding * 2;
        int viewportWidth = Math.max(0, (int) Math.round(screenWidth * scale));
        int viewportHeight = Math.max(0, (int) Math.round(screenHeight * scale));
        int anchorX = centered || centerCorners ? frameX : mapX;
        int anchorWidth = centered || centerCorners ? frameWidth : mapSize;
        int physicalFrameLeft = (int) Math.round(anchorX * scale);
        int physicalFrameRight = (int) Math.round((anchorX + anchorWidth) * scale);
        int physicalFrameTop = (int) Math.round(mapY * scale);
        int physicalFrameBottom = (int) Math.round((mapY + frameHeight) * scale);

        int textX;
        int boxInset = (int) Math.round(5 * scale) - horizontalPadding;
        if (centered) {
            double center = (mapX + mapSize / 2.0) * scale;
            textX = (int) Math.round(center - inkWidth / 2.0);
        } else if (centerCorners) {
            double columnCenter = (frameX + frameWidth * (leftAligned ? 0.25 : 0.75)) * scale;
            textX = (int) Math.round(columnCenter - inkWidth / 2.0);
        } else if (leftAligned) {
            int boxLeft = physicalFrameLeft + boxInset;
            textX = boxLeft + horizontalPadding;
        } else {
            int boxRight = physicalFrameRight - boxInset;
            textX = (int) Math.round(boxRight - horizontalPadding - inkWidth);
        }

        int requestedBoxTop;
        if (centered) {
            double textTop = upper
                    ? mapY - compassOverhang - lineHeight(fontLineHeight, scale) - 3
                    : mapY + frameHeight + bottomCompassOverhang + 4;
            requestedBoxTop = (int) Math.round(textTop * scale) - verticalPadding;
        } else {
            int frameInset = (int) Math.round(5 * scale);
            requestedBoxTop = upper
                    ? physicalFrameTop + frameInset
                    : physicalFrameBottom - frameInset - rowHeight;
        }

        if (rowHeight > viewportHeight || viewportWidth < inkWidth + horizontalPadding * 2) return null;
        int maxTextX = Math.max(0, viewportWidth - (int) Math.ceil(inkWidth));
        int idealTextX = textX;
        textX = clamp(textX, 0, maxTextX);
        int textShift = textX - idealTextX;
        int boxLeft;
        int boxRight;
        if (centered || centerCorners) {
            boxLeft = (int) Math.round(textX - horizontalPadding);
            boxRight = (int) Math.round(textX + inkWidth + horizontalPadding);
        } else if (leftAligned) {
            boxLeft = physicalFrameLeft + boxInset;
            boxRight = (int) Math.round(textX + inkWidth + horizontalPadding);
        } else {
            boxLeft = (int) Math.round(textX - horizontalPadding);
            boxRight = physicalFrameRight - boxInset;
        }
        // Keep the backing padding attached to the text if viewport clamping moves it.
        if (!centerCorners && leftAligned && textShift != 0) boxLeft = clamp(boxLeft + textShift, 0, viewportWidth);
        if (!centered && !centerCorners && !leftAligned && textShift != 0) boxRight = clamp(boxRight + textShift, 0, viewportWidth);
        boxLeft = clamp(boxLeft, 0, viewportWidth);
        boxRight = clamp(boxRight, boxLeft, viewportWidth);

        int boxTop = clamp(requestedBoxTop, 0, Math.max(0, viewportHeight - rowHeight));
        int boxBottom = Math.min(viewportHeight, boxTop + rowHeight);
        int textY = boxTop + verticalPadding;
        return new PhysicalPlacement(boxLeft, boxTop, boxRight, boxBottom, textX, textY, textScale);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(value, max));
    }

    public record PhysicalPlacement(int boxLeft, int boxTop, int boxRight, int boxBottom,
                                    int textX, int textY, double textScale) {}
}
