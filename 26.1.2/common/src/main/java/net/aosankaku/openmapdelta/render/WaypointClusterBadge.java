// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.render;

/** Circle geometry shared by immediate and submitted world waypoint renderers. */
public final class WaypointClusterBadge {
    private static final int SEGMENTS = 32;
    private static final float[] X = new float[SEGMENTS + 1];
    private static final float[] Y = new float[SEGMENTS + 1];
    static {
        for (int i = 0; i <= SEGMENTS; i++) {
            double angle = -2.0 * Math.PI * i / SEGMENTS;
            X[i] = (float) Math.cos(angle); Y[i] = (float) Math.sin(angle);
        }
    }

    private WaypointClusterBadge() {}

    /** Keep all four corners of even a multi-digit count inside the dark inner disk. */
    public static float radius(int textWidth, int lineHeight) {
        return (float) Math.ceil(Math.max(10.0, Math.hypot(Math.max(0, textWidth) / 2.0,
                Math.max(0, lineHeight) / 2.0) + 3.0));
    }

    @FunctionalInterface
    public interface VertexSink { void add(float x, float y, int color); }

    /** Emits clockwise quads: black outer edge, contrasting ring, and a dark numeral backdrop. */
    public static void emit(float radius, boolean emphasized, int alphaSource, VertexSink sink) {
        float opacity = ((alphaSource >>> 24) & 0xFF) / 255.0F;
        ring(radius + 1.0F, radius, WaypointRenderMath.scaleAlpha(0xFF000000, opacity), sink);
        ring(radius, radius - 1.0F,
                WaypointRenderMath.scaleAlpha(emphasized ? 0xFFFFFF55 : 0xFFFFFFFF, opacity), sink);
        ring(radius - 1.0F, 0.0F, WaypointRenderMath.scaleAlpha(0xE6000000, opacity), sink);
    }

    private static void ring(float outer, float inner, int color, VertexSink sink) {
        for (int i = 0; i < SEGMENTS; i++) {
            sink.add(X[i] * inner, Y[i] * inner, color);
            sink.add(X[i] * outer, Y[i] * outer, color);
            sink.add(X[i + 1] * outer, Y[i + 1] * outer, color);
            sink.add(X[i + 1] * inner, Y[i + 1] * inner, color);
        }
    }
}
