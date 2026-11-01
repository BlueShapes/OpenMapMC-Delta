// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.marker;

import com.mojang.blaze3d.platform.NativeImage;

import java.nio.ByteBuffer;

/** Converts one asynchronous RGBA GPU readback into a small, tightly-cropped icon. */
final class EntityIconPixels {
    private EntityIconPixels() {}

    /**
     * The renderer readback is bottom-up premultiplied RGBA. The returned NativeImage uses top-left
     * ARGB pixels and straight alpha; ownership passes to the caller. Fully transparent captures
     * and malformed readbacks return null.
     */
    static NativeImage crop(ByteBuffer rgba, int width, int height, int padding) {
        if (rgba == null || width <= 0 || height <= 0 || padding < 0) return null;
        long required = (long) width * height * 4L;
        if (required > Integer.MAX_VALUE || rgba.remaining() < required) return null;

        int start = rgba.position();
        int left = width, top = height, right = -1, bottom = -1;
        for (int y = 0; y < height; y++) {
            int sourceY = height - 1 - y;
            for (int x = 0; x < width; x++) {
                int alpha = rgba.get(start + (sourceY * width + x) * 4 + 3) & 0xFF;
                if (alpha == 0) continue;
                left = Math.min(left, x);
                top = Math.min(top, y);
                right = Math.max(right, x);
                bottom = Math.max(bottom, y);
            }
        }
        if (right < left || bottom < top) return null;

        left = Math.max(0, left - padding);
        top = Math.max(0, top - padding);
        right = Math.min(width - 1, right + padding);
        bottom = Math.min(height - 1, bottom + padding);
        int cropWidth = right - left + 1;
        int cropHeight = bottom - top + 1;
        NativeImage image = new NativeImage(cropWidth, cropHeight, false);
        boolean complete = false;
        try {
            for (int y = top; y <= bottom; y++) {
                int sourceY = height - 1 - y;
                for (int x = left; x <= right; x++) {
                    int offset = start + (sourceY * width + x) * 4;
                    int red = rgba.get(offset) & 0xFF;
                    int green = rgba.get(offset + 1) & 0xFF;
                    int blue = rgba.get(offset + 2) & 0xFF;
                    int alpha = rgba.get(offset + 3) & 0xFF;
                    if (alpha == 0) {
                        image.setPixel(x - left, y - top, 0);
                    } else {
                        red = Math.min(255, (red * 255 + alpha / 2) / alpha);
                        green = Math.min(255, (green * 255 + alpha / 2) / alpha);
                        blue = Math.min(255, (blue * 255 + alpha / 2) / alpha);
                        image.setPixel(x - left, y - top, alpha << 24 | red << 16 | green << 8 | blue);
                    }
                }
            }
            complete = true;
            return image;
        } finally {
            if (!complete) image.close();
        }
    }
}
