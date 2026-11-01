// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.render;

/** Short-lived display pixels only; never feeds retained terrain back into a sampled tile. */
public final class MinimapLayerTransition {
    private static final int SURFACE = Integer.MIN_VALUE;
    private static final long HOLD_NANOS = 1_000_000_000L, FADE_NANOS = 200_000_000L;
    private Object level;
    private long sessionEpoch;
    private int layer, width, height;
    private double stride;
    private long originX, originZ;
    private int[] pixels, retained;
    private int retainedWidth, retainedHeight, retainedAlpha;
    private double retainedStride;
    private long retainedX, retainedZ, retainedSince;
    private boolean hasFrame, usedRetention;

    public void begin(Object level, long sessionEpoch, int layer, int width, int height,
                      long originX, long originZ, double stride, long now) {
        boolean sameSession = hasFrame && this.level == level && this.sessionEpoch == sessionEpoch;
        if (!sameSession || layer == SURFACE || this.stride != stride
                || this.width != width || this.height != height) {
            retained = null;
        } else if (this.layer != layer) {
            boolean continuing = retained != null;
            retained = pixels.clone();
            retainedWidth = this.width;
            retainedHeight = this.height;
            retainedStride = this.stride;
            retainedX = this.originX;
            retainedZ = this.originZ;
            if (!continuing) retainedSince = now;
        }
        if (retained != null) {
            long elapsed = Math.max(0L, now - retainedSince);
            if (elapsed >= HOLD_NANOS + FADE_NANOS) retained = null;
            else retainedAlpha = elapsed <= HOLD_NANOS ? 255
                    : (int) ((HOLD_NANOS + FADE_NANOS - elapsed) * 255 / FADE_NANOS);
        }
        this.level = level;
        this.sessionEpoch = sessionEpoch;
        this.layer = layer;
        this.width = width;
        this.height = height;
        this.originX = originX;
        this.originZ = originZ;
        this.stride = stride;
        if (pixels == null || pixels.length != width * height) pixels = new int[width * height];
        usedRetention = false;
    }

    public int pixel(int x, int z, int color, boolean observed) {
        int sourceColor = color;
        if (!observed && retained != null) {
            double dx = originX + x * stride - retainedX;
            double dz = originZ + z * stride - retainedZ;
            long oldX = (long) Math.floor(dx / retainedStride + 0.5);
            long oldZ = (long) Math.floor(dz / retainedStride + 0.5);
            if (oldX >= 0 && oldZ >= 0 && oldX < retainedWidth && oldZ < retainedHeight) {
                int previous = retained[(int) oldX + (int) oldZ * retainedWidth];
                int alpha = (previous >>> 24) * retainedAlpha / 255;
                if (alpha != 0) {
                    sourceColor = previous;
                    color = (previous & 0xFFFFFF) | (alpha << 24);
                    usedRetention = true;
                }
            }
        }
        // Keep the unfaded source so crossing again does not multiply the fade or extend its age.
        pixels[x + z * width] = sourceColor;
        return color;
    }

    public void end() {
        hasFrame = true;
        if (!usedRetention) retained = null;
    }

    public void clear() {
        level = null;
        pixels = null;
        retained = null;
        hasFrame = false;
    }

    public boolean isRetaining() { return retained != null; }
}
