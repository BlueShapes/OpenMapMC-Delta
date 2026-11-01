package com.mojang.blaze3d.platform;

public final class NativeImage implements AutoCloseable {
    private static NativeImage lastCreated;
    private final int width;
    private final int height;
    private final int[] pixels;
    private long writeCount;

    public NativeImage(int width, int height, boolean useStb) {
        this.width = width;
        this.height = height;
        this.pixels = new int[width * height];
        lastCreated = this;
    }

    public static NativeImage lastCreated() { return lastCreated; }
    public int getWidth() { return width; }
    public int getHeight() { return height; }
    public void setPixelRGBA(int x, int y, int argb) { pixels[y * width + x] = argb; writeCount++; }
    public void setPixel(int x, int y, int argb) { pixels[y * width + x] = argb; writeCount++; }
    public int pixelAt(int x, int y) { return pixels[y * width + x]; }
    public int[] copyPixels() { return pixels.clone(); }
    public long writeCount() { return writeCount; }
    @Override public void close() {}
}
