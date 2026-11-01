// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.render;

/** Immutable sampling and drawing geometry for the fullscreen map's cached terrain. */
public final class FullscreenMapViewport {
    public static final int MAX_TEXTURE_DIMENSION = 4096;
    public static final int OVERSCAN_PIXELS = 32;
    public static final int RECENTER_MARGIN_PIXELS = 16;
    public static final int TARGET_VISIBLE_PIXELS = MAX_TEXTURE_DIMENSION - OVERSCAN_PIXELS * 2;

    private final int panelWidth;
    private final int panelHeight;
    private final double blocksPerPixel;
    private final double guiScale;
    private final int sampleScale;
    private final double sampleStride;
    private final int textureWidth;
    private final int textureHeight;
    private final int originX;
    private final int originZ;

    private FullscreenMapViewport(int panelWidth, int panelHeight, double blocksPerPixel, double guiScale,
                                  int sampleScale, double sampleStride, int textureWidth, int textureHeight,
                                  int originX, int originZ) {
        this.panelWidth = panelWidth;
        this.panelHeight = panelHeight;
        this.blocksPerPixel = blocksPerPixel;
        this.guiScale = guiScale;
        this.sampleScale = sampleScale;
        this.sampleStride = sampleStride;
        this.textureWidth = textureWidth;
        this.textureHeight = textureHeight;
        this.originX = originX;
        this.originZ = originZ;
    }

    public static FullscreenMapViewport forView(int panelWidth, int panelHeight, double blocksPerPixel,
                                                double panX, double panZ) {
        return forView(panelWidth, panelHeight, blocksPerPixel, panX, panZ, 1.0);
    }

    public static FullscreenMapViewport forView(int panelWidth, int panelHeight, double blocksPerPixel,
                                                double panX, double panZ, double requestedGuiScale) {
        if (panelWidth <= 0 || panelHeight <= 0) {
            throw new IllegalArgumentException("Panel dimensions must be positive");
        }
        double bpp = FullscreenMapZoom.safeBlocksPerPixel(blocksPerPixel);
        double guiScale = normalizeGuiScale(requestedGuiScale);
        double maxPanelDimension = Math.max(panelWidth, panelHeight);
        double pixelsPerGui = Math.min(guiScale, TARGET_VISIBLE_PIXELS / maxPanelDimension);
        double stride = Math.max(1.0, bpp / pixelsPerGui);
        stride = snapNearInteger(stride);
        int scale = boundedCeil(guiScale / pixelsPerGui);
        int width = textureExtent(panelWidth, bpp, stride);
        int height = textureExtent(panelHeight, bpp, stride);
        int originX = snappedOrigin(panX, stride, width / 2);
        int originZ = snappedOrigin(panZ, stride, height / 2);
        return new FullscreenMapViewport(panelWidth, panelHeight, bpp, guiScale, scale, stride,
                width, height, originX, originZ);
    }

    private static double normalizeGuiScale(double scale) {
        return Double.isFinite(scale) && scale > 0.0 ? scale : 1.0;
    }

    private static double snapNearInteger(double stride) {
        double nearest = Math.rint(stride);
        return Math.abs(stride - nearest) <= Math.ulp(stride) * 4.0 ? nearest : stride;
    }

    private static int boundedCeil(double value) {
        if (!Double.isFinite(value) || value >= Integer.MAX_VALUE) return Integer.MAX_VALUE;
        double nearest = Math.rint(value);
        if (Math.abs(value - nearest) <= Math.ulp(value) * 4.0) value = nearest;
        return Math.max(1, (int) Math.ceil(value));
    }

    private static int textureExtent(int panelDimension, double bpp, double stride) {
        int visibleTexels = boundedCeil((double) panelDimension * bpp / stride);
        return (int) Math.min(MAX_TEXTURE_DIMENSION, (long) visibleTexels + OVERSCAN_PIXELS * 2L);
    }

    private static int snappedOrigin(double pan, double stride, int centerIndex) {
        double origin;
        if (stride <= Integer.MAX_VALUE && stride == Math.rint(stride)) {
            double snappedPan = Math.floor(pan / stride) * stride;
            origin = snappedPan - centerIndex * stride;
        } else {
            origin = pan - centerIndex * stride;
        }
        if (!Double.isFinite(origin)) return origin < 0 ? Integer.MIN_VALUE : Integer.MAX_VALUE;
        if (stride != Math.rint(stride)) origin = Math.floor(origin);
        else {
            double nearest = Math.rint(origin);
            if (Math.abs(origin - nearest) <= Math.ulp(origin) * 4.0) origin = nearest;
        }
        return (int) Math.max(Integer.MIN_VALUE, Math.min(Integer.MAX_VALUE, origin));
    }

    public boolean hasSameShape(int width, int height, double bpp) {
        return hasSameShape(width, height, bpp, 1.0);
    }

    public boolean hasSameShape(int width, int height, double bpp, double requestedGuiScale) {
        return panelWidth == width && panelHeight == height
                && blocksPerPixel == FullscreenMapZoom.safeBlocksPerPixel(bpp)
                && guiScale == normalizeGuiScale(requestedGuiScale);
    }

    /** True when the whole requested panel view stays inside the cached inset. */
    public boolean containsView(double panX, double panZ) {
        double centerTextureX = (panX - originX) / sampleStride;
        double centerTextureZ = (panZ - originZ) / sampleStride;
        double texelsPerPixel = blocksPerPixel / sampleStride;
        double left = centerTextureX - panelWidth / 2 * texelsPerPixel;
        double right = centerTextureX + (panelWidth - panelWidth / 2) * texelsPerPixel;
        double top = centerTextureZ - panelHeight / 2 * texelsPerPixel;
        double bottom = centerTextureZ + (panelHeight - panelHeight / 2) * texelsPerPixel;
        return left >= RECENTER_MARGIN_PIXELS
                && top >= RECENTER_MARGIN_PIXELS
                && right <= textureWidth - RECENTER_MARGIN_PIXELS
                && bottom <= textureHeight - RECENTER_MARGIN_PIXELS;
    }

    public double drawX(int panelX, double panX, double currentBlocksPerPixel) {
        return drawX(panelX, panX, currentBlocksPerPixel, panelWidth);
    }

    public double drawX(int panelX, double panX, double currentBlocksPerPixel, int currentPanelWidth) {
        return panelX + currentPanelWidth / 2 + (originX - panX) / FullscreenMapZoom.safeBlocksPerPixel(currentBlocksPerPixel);
    }

    public double drawY(int panelY, double panZ, double currentBlocksPerPixel) {
        return drawY(panelY, panZ, currentBlocksPerPixel, panelHeight);
    }

    public double drawY(int panelY, double panZ, double currentBlocksPerPixel, int currentPanelHeight) {
        return panelY + currentPanelHeight / 2 + (originZ - panZ) / FullscreenMapZoom.safeBlocksPerPixel(currentBlocksPerPixel);
    }

    public double renderScale(double currentBlocksPerPixel) {
        return sampleStride / (double) FullscreenMapZoom.safeBlocksPerPixel(currentBlocksPerPixel);
    }

    public int getPanelWidth() { return panelWidth; }
    public int getPanelHeight() { return panelHeight; }
    public double getBlocksPerPixel() { return blocksPerPixel; }
    public int getSampleScale() { return sampleScale; }
    public double getSampleStride() { return sampleStride; }
    public int getTextureWidth() { return textureWidth; }
    public int getTextureHeight() { return textureHeight; }
    public int getOriginX() { return originX; }
    public int getOriginZ() { return originZ; }
}
