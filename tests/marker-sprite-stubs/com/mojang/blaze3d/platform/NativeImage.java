package com.mojang.blaze3d.platform;

public final class NativeImage implements AutoCloseable {
    private static NativeImage lastCreated;
    private final int width;
    private final int height;
    private final int[] pixels;

    public NativeImage(int width, int height, boolean useStb) {
        this.width = width;
        this.height = height;
        this.pixels = new int[width * height];
        lastCreated = this;
    }

    public static NativeImage lastCreated() { return lastCreated; }
    public void setPixelRGBA(int x, int y, int abgr) { pixels[y * width + x] = abgr; }
    public int pixelAt(int x, int y) { return pixels[y * width + x]; }
    @Override public void close() {}
}
