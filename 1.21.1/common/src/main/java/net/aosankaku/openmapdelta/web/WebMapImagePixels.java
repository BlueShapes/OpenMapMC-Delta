// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.web;

import java.awt.image.BufferedImage;
import java.util.Objects;

/** Immutable ABGR pixel copies used to publish client textures without sharing live game objects. */
public final class WebMapImagePixels {
    private final int width;
    private final int height;
    private final int[] abgr;

    public WebMapImagePixels(int width, int height, int[] abgr) {
        if (width <= 0 || height <= 0 || width > 512 || height > 512 || abgr.length != width * height) {
            throw new IllegalArgumentException("Invalid image dimensions");
        }
        this.width = width;
        this.height = height;
        this.abgr = abgr.clone();
    }

    public static WebMapImagePixels fromArgb(BufferedImage image) {
        Objects.requireNonNull(image);
        int sourceWidth = image.getWidth(), sourceHeight = image.getHeight();
        if (sourceWidth <= 0 || sourceHeight <= 0 || sourceWidth > 8192 || sourceHeight > 8192
                || (long) sourceWidth * sourceHeight > 16_777_216L) throw new IllegalArgumentException("Invalid image dimensions");
        double scale = Math.min(1.0, 128.0 / Math.max(sourceWidth, sourceHeight));
        int width = Math.max(1, (int) Math.round(sourceWidth * scale));
        int height = Math.max(1, (int) Math.round(sourceHeight * scale));
        int[] pixels = new int[width * height];
        for (int y = 0; y < height; y++) {
            int sy = Math.min(sourceHeight - 1, (int) ((y + .5) * sourceHeight / height));
            for (int x = 0; x < width; x++) {
                int sx = Math.min(sourceWidth - 1, (int) ((x + .5) * sourceWidth / width));
                pixels[y * width + x] = argbToAbgr(image.getRGB(sx, sy));
            }
        }
        return new WebMapImagePixels(width, height, pixels);
    }

    /** NativeImage stores each pixel in ABGR byte order, matching WebMapPng. */
    public static WebMapImagePixels fromNativeAbgr(int width, int height, int[] pixels) {
        if (width <= 0 || height <= 0 || pixels.length != width * height) throw new IllegalArgumentException("Invalid image dimensions");
        double scale = Math.min(1.0, 128.0 / Math.max(width, height));
        int outWidth = Math.max(1, (int) Math.round(width * scale));
        int outHeight = Math.max(1, (int) Math.round(height * scale));
        if (outWidth == width && outHeight == height) return new WebMapImagePixels(width, height, pixels);
        int[] scaled = new int[outWidth * outHeight];
        for (int y = 0; y < outHeight; y++) {
            int sy = Math.min(height - 1, (int) ((y + .5) * height / outHeight));
            for (int x = 0; x < outWidth; x++) {
                int sx = Math.min(width - 1, (int) ((x + .5) * width / outWidth));
                scaled[y * outWidth + x] = pixels[sy * width + sx];
            }
        }
        return new WebMapImagePixels(outWidth, outHeight, scaled);
    }

    public static int argbToAbgr(int argb) {
        return (argb & 0xff00ff00) | ((argb & 0x000000ff) << 16) | ((argb >>> 16) & 0x000000ff);
    }

    public int width() { return width; }
    public int height() { return height; }
    public int[] pixels() { return abgr.clone(); }

    public WebMapImagePixels crop(int x, int y, int cropWidth, int cropHeight) {
        if (x < 0 || y < 0 || cropWidth <= 0 || cropHeight <= 0
                || x + cropWidth > width || y + cropHeight > height) return null;
        int[] result = new int[cropWidth * cropHeight];
        for (int row = 0; row < cropHeight; row++) {
            System.arraycopy(abgr, (y + row) * width + x, result, row * cropWidth, cropWidth);
        }
        return new WebMapImagePixels(cropWidth, cropHeight, result);
    }

    public WebMapImagePixels textureRegion(int u, int v, int regionWidth, int regionHeight,
                                           int declaredWidth, int declaredHeight) {
        if (u < 0 || v < 0 || regionWidth <= 0 || regionHeight <= 0
                || declaredWidth <= 0 || declaredHeight <= 0
                || (long) u + regionWidth > declaredWidth || (long) v + regionHeight > declaredHeight) return null;
        int left = (int) Math.floor((double) u * width / declaredWidth);
        int top = (int) Math.floor((double) v * height / declaredHeight);
        int right = (int) Math.ceil((double) (u + regionWidth) * width / declaredWidth);
        int bottom = (int) Math.ceil((double) (v + regionHeight) * height / declaredHeight);
        return crop(left, top, right - left, bottom - top);
    }

    /** Crops Minecraft's front-face UVs and composites the hat layer over the base pixels. */
    public WebMapImagePixels minecraftFace() {
        if (width < 48 || height < 16) return null;
        int[] face = new int[64];
        for (int y = 0; y < 8; y++) {
            for (int x = 0; x < 8; x++) {
                int base = abgr[(y + 8) * width + x + 8];
                int hat = abgr[(y + 8) * width + x + 40];
                face[y * 8 + x] = over(hat, base);
            }
        }
        return new WebMapImagePixels(8, 8, face);
    }

    private static int over(int source, int destination) {
        int sourceAlpha = source >>> 24;
        if (sourceAlpha == 0) return destination;
        if (sourceAlpha == 255) return source;
        int destinationAlpha = destination >>> 24;
        int inverse = 255 - sourceAlpha;
        int outAlpha = sourceAlpha + (destinationAlpha * inverse + 127) / 255;
        int red = channel(source, destination, 0, sourceAlpha, destinationAlpha, inverse, outAlpha);
        int green = channel(source, destination, 8, sourceAlpha, destinationAlpha, inverse, outAlpha);
        int blue = channel(source, destination, 16, sourceAlpha, destinationAlpha, inverse, outAlpha);
        return (outAlpha << 24) | (blue << 16) | (green << 8) | red;
    }

    private static int channel(int source, int destination, int shift, int sourceAlpha,
                               int destinationAlpha, int inverse, int outAlpha) {
        int sourceChannel = (source >>> shift) & 255;
        int destinationChannel = (destination >>> shift) & 255;
        int numerator = sourceChannel * sourceAlpha + (destinationChannel * destinationAlpha * inverse + 127) / 255;
        return Math.min(255, (numerator + outAlpha / 2) / outAlpha);
    }
}
