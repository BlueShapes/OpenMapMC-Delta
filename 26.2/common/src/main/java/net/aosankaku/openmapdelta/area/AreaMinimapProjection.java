// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; Apache-2.0.
package net.aosankaku.openmapdelta.area;

/** The terrain's heading transform, with complete-label containment for either minimap shape. */
public final class AreaMinimapProjection {
    public record Pixel(double x, double y) {}
    private AreaMinimapProjection() {}

    public static Pixel project(double x, double z, double centerX, double centerZ,
                                double stride, double rotationDegrees, int size) {
        if (!Double.isFinite(stride) || stride <= 0) throw new IllegalArgumentException("Invalid map stride");
        double dx = (x - centerX) / stride, dz = (z - centerZ) / stride;
        double angle = Math.toRadians(rotationDegrees);
        return new Pixel(size / 2.0 + dx * Math.cos(angle) - dz * Math.sin(angle),
                size / 2.0 + dx * Math.sin(angle) + dz * Math.cos(angle));
    }

    public static boolean fits(double x, double y, double halfWidth, double halfHeight, int size, boolean circular) {
        if (x - halfWidth < 0 || y - halfHeight < 0 || x + halfWidth > size || y + halfHeight > size) return false;
        if (!circular) return true;
        double farX = Math.abs(x - size / 2.0) + halfWidth;
        double farY = Math.abs(y - size / 2.0) + halfHeight;
        return farX * farX + farY * farY <= size * (double) size / 4;
    }
}
