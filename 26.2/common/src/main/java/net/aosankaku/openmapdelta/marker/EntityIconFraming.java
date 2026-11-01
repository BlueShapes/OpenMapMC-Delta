// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.marker;

/** Fits projected model bounds into the transparent entity-icon canvas. Coordinates are blocks. */
final class EntityIconFraming {
    private EntityIconFraming() {}

    static Frame fit(float widthBlocks, float heightBlocks, int imageSize, float padding) {
        float width = Math.max(0.25F, widthBlocks);
        float height = Math.max(0.25F, heightBlocks);
        return fitProjectedBounds(-width / 2.0F, 0.0F, width / 2.0F, height, imageSize, padding);
    }

    static Frame fitProjectedBounds(float minX, float minY, float maxX, float maxY,
                                    int imageSize, float padding) {
        float width = maxX - minX;
        float height = maxY - minY;
        if (!Float.isFinite(width) || !Float.isFinite(height) || width <= 0.001F || height <= 0.001F
                || imageSize <= 0 || padding < 0.0F || padding * 2.0F >= imageSize) {
            return null;
        }

        float available = imageSize - padding * 2.0F;
        float scale = Math.min(available / width, available / height);
        float centerX = (minX + maxX) / 2.0F;
        float centerY = (minY + maxY) / 2.0F;
        return new Frame(scale, imageSize / 2.0F + centerX * scale,
                imageSize / 2.0F + centerY * scale, width * scale, height * scale);
    }

    record Frame(float scale, float translateX, float translateY, float widthPixels, float heightPixels) {
        /** World-up y coordinates map to smaller screen y values at the top of the icon. */
        float screenY(float worldY) { return translateY - worldY * scale; }
    }
}
