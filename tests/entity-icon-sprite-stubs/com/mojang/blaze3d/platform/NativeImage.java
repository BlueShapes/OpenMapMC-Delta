package com.mojang.blaze3d.platform;

public final class NativeImage implements AutoCloseable {
    private final int width;
    private final int height;
    private final int[] pixels;
    public boolean closed;

    public NativeImage(int width, int height) {
        this.width = width;
        this.height = height;
        this.pixels = new int[width * height];
    }
    public int getWidth() { return width; }
    public int getHeight() { return height; }
    public int getPixel(int x, int y) { return pixels[y * width + x]; }
    public int getPixelABGR(int x, int y) { return pixels[y * width + x]; }
    public void setPixelABGR(int x, int y, int color) { pixels[y * width + x] = color; }
    @Override public void close() { closed = true; }
}
