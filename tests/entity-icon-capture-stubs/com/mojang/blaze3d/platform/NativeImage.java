package com.mojang.blaze3d.platform;

public final class NativeImage implements AutoCloseable {
    private final int width, height;
    private final int[] pixels;
    public NativeImage(int width, int height, boolean useStb) {
        this.width = width; this.height = height; pixels = new int[width * height];
    }
    public int getWidth() { return width; }
    public int getHeight() { return height; }
    public void setPixel(int x, int y, int color) { pixels[y * width + x] = color; }
    public int getPixel(int x, int y) { return pixels[y * width + x]; }
    @Override public void close() {}
}
