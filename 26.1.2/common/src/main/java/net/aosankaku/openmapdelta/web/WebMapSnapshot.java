// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.web;

import java.util.Map;

/** Published by the game thread. HTTP threads only read these immutable copies. */
public record WebMapSnapshot(byte[] state, byte[] locale, Map<String, Image> tiles,
                             Map<String, Image> icons) {
    public WebMapSnapshot {
        state = state.clone();
        locale = locale.clone();
        tiles = Map.copyOf(tiles);
        icons = Map.copyOf(icons);
    }
    public WebMapSnapshot(byte[] state, byte[] locale, Map<String, Image> tiles) {
        this(state, locale, tiles, Map.of());
    }
    public static WebMapSnapshot empty() {
        return new WebMapSnapshot("{\"active\":false}".getBytes(java.nio.charset.StandardCharsets.UTF_8),
                "{}".getBytes(java.nio.charset.StandardCharsets.UTF_8), Map.of(), Map.of());
    }

    public static final class Image {
        private final int[] pixels;
        private final int width;
        private final int height;
        private final long revision;
        private byte[] png;
        public Image(int[] pixels) { this(pixels, 128, 128, 0); }
        public Image(int[] pixels, long revision) { this(pixels, 128, 128, revision); }
        public Image(int[] pixels, int width, int height, long revision) {
            if (width <= 0 || height <= 0 || width > 512 || height > 512
                    || pixels.length != width * height) throw new IllegalArgumentException("Invalid image size");
            this.pixels = pixels.clone(); this.width = width; this.height = height; this.revision = revision;
        }
        public boolean matches(int[] other) { return java.util.Arrays.equals(pixels, other); }
        public int[] copyPixels() { return pixels.clone(); }
        public long revision() { return revision; }
        public int width() { return width; }
        public int height() { return height; }
        public synchronized byte[] png() throws java.io.IOException {
            if (png == null) png = WebMapPng.encode(pixels, width, height);
            return png;
        }
    }
}
