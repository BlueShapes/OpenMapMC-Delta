// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.render;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

/** Pure geometry and view tests used by the world waypoint renderer. */
public final class WaypointRenderMath {
    public static final double LABEL_LOOK_ANGLE_DEGREES = 6.0;
    public static final double ICON_HEIGHT = 1.25;
    public static final double MIN_BILLBOARD_SCALE = 0.04;
    public static final double MAX_BILLBOARD_SCALE = 0.22;
    private static final double BILLBOARD_SCALE_PER_BLOCK = 0.0016;
    private static final double ANGLE_EPSILON_DEGREES = 1.0e-9;
    private static final int MIN_PROJECTION_DISTANCE = 24;
    private static final int MAX_PROJECTION_DISTANCE = 128;
    private static final long NOTIFY_BEACON_PERIOD_TICKS = 40L;
    private static final double DEFAULT_WAYPOINT_HIDE_DISTANCE = 5.0;
    private static final double DEFAULT_LABEL_FADE_DISTANCE = 2.5;

    private WaypointRenderMath() {}

    public static int projectionDistance(int renderDistanceChunks) {
        int configured = Math.max(1, renderDistanceChunks) * 12;
        return Math.max(MIN_PROJECTION_DISTANCE, Math.min(MAX_PROJECTION_DISTANCE, configured));
    }

    /** Smoothly pulses a notify beacon between 25% and full brightness over two seconds. */
    public static float notifyBeaconPulse(long gameTime, float partialTick) {
        double tick = Math.floorMod(gameTime, NOTIFY_BEACON_PERIOD_TICKS);
        double interpolation = Float.isFinite(partialTick)
                ? Math.max(0.0, Math.min(1.0, partialTick)) : 0.0;
        double phase = (tick + interpolation) / NOTIFY_BEACON_PERIOD_TICKS;
        double pulse = 0.625 + 0.375 * Math.cos(phase * Math.PI * 2.0);
        return (float) Math.max(0.25, Math.min(1.0, pulse));
    }

    /** Beam opacity: hidden within the configured radius and fully visible at twice that radius. */
    public static float proximityOpacity(double distance, double hideDistance) {
        if (!Double.isFinite(distance) || distance < 0.0) return 0.0F;
        double radius = validHideDistance(hideDistance);
        if (radius == 0.0) return 1.0F;
        if (distance <= radius) return 0.0F;
        double fadeEnd = radius * 2.0;
        if (distance >= fadeEnd) return 1.0F;
        double t = (distance - radius) / radius;
        return (float) (t * t * (3.0 - 2.0 * t));
    }

    /** Measures the actual 3D distance from a player's feet to a waypoint block center. */
    public static float proximityOpacity(double dx, double dy, double dz, double hideDistance) {
        if (!Double.isFinite(dx) || !Double.isFinite(dy) || !Double.isFinite(dz)) return 0.0F;
        double distance = Math.hypot(Math.hypot(dx, dy), dz);
        return proximityOpacity(distance, hideDistance);
    }

    /** Icon, text, and backdrop opacity: fade starts here and ends at half this distance. */
    public static float labelOpacity(double distance, double fadeDistance) {
        double start = Double.isFinite(fadeDistance) && fadeDistance >= 0.0 && fadeDistance <= 128.0
                ? fadeDistance : DEFAULT_LABEL_FADE_DISTANCE;
        if (!Double.isFinite(distance) || distance < 0.0) return 0.0F;
        if (start == 0.0 || distance >= start) return 1.0F;
        if (distance <= start / 2.0) return 0.0F;
        double t = 2.0 * (distance / start) - 1.0;
        return (float) (t * t * (3.0 - 2.0 * t));
    }

    /** Evaluate both fades from the same actual player-to-waypoint distance, independently of the camera. */
    public static Opacities proximityOpacities(double dx, double dy, double dz,
                                              double hideDistance, double labelFadeDistance) {
        double distance = Math.hypot(Math.hypot(dx, dy), dz);
        return new Opacities(proximityOpacity(distance, hideDistance), labelOpacity(distance, labelFadeDistance));
    }

    public record Opacities(float beam, float label) {
        public boolean visible() {
            return beam > 0.0F || label > 0.0F;
        }
    }

    /** Scales only an ARGB color's alpha channel, keeping its RGB channels unchanged. */
    public static int scaleAlpha(int argb, float opacity) {
        double boundedOpacity = Float.isFinite(opacity) ? Math.max(0.0, Math.min(1.0, opacity)) : 0.0;
        int alpha = (argb >>> 24) & 0xFF;
        int scaledAlpha = (int) Math.round(alpha * boundedOpacity);
        return (scaledAlpha << 24) | (argb & 0x00FFFFFF);
    }

    /** Combines the local proximity fade with a notify beam's animation without affecting its marker. */
    public static float beaconOpacity(float proximityOpacity, float notifyPulse) {
        double proximity = Float.isFinite(proximityOpacity)
                ? Math.max(0.0, Math.min(1.0, proximityOpacity)) : 0.0;
        double pulse = Float.isFinite(notifyPulse)
                ? Math.max(0.0, Math.min(1.0, notifyPulse)) : 0.0;
        return (float) (proximity * pulse);
    }

    private static double validHideDistance(double hideDistance) {
        return Double.isFinite(hideDistance) && hideDistance >= 0.0
                ? hideDistance : DEFAULT_WAYPOINT_HIDE_DISTANCE;
    }

    public static double projectedDistance(double worldDistance, double maximumDistance) {
        if (!Double.isFinite(worldDistance) || worldDistance <= 0.0) return 0.0;
        return Math.min(worldDistance, Math.max(0.0, maximumDistance));
    }

    /** Scales a distant target vector into the camera's renderable range. */
    public static double projectionScale(double worldDistance, double maximumDistance) {
        if (!Double.isFinite(worldDistance) || worldDistance <= 0.0) return 1.0;
        return Math.min(1.0, Math.max(0.0, maximumDistance) / worldDistance);
    }

    /**
     * Projects a waypoint and returns the camera-relative center of its displayed icon.
     * The candidate vector points to that center, while distance remains the original base distance.
     */
    public static <T> ViewCandidate<T> projectCandidate(T value, double baseDx, double baseDy,
                                                         double baseDz, double maximumDistance) {
        double distance = Math.sqrt(baseDx * baseDx + baseDy * baseDy + baseDz * baseDz);
        double scale = projectionScale(distance, maximumDistance);
        return new ViewCandidate<>(value, baseDx * scale,
                baseDy * scale + ICON_HEIGHT, baseDz * scale, distance);
    }

    /** Returns a scale that keeps projected waypoint text legible at the clamped render distance. */
    public static double billboardScale(double displayDistance) {
        double scale = Math.max(0.0, displayDistance) * BILLBOARD_SCALE_PER_BLOCK;
        return Math.max(MIN_BILLBOARD_SCALE, Math.min(MAX_BILLBOARD_SCALE, scale));
    }

    public static boolean isInFront(double dx, double dy, double dz, double yawDegrees,
                                    double pitchDegrees) {
        double yaw = Math.toRadians(yawDegrees);
        double pitch = Math.toRadians(pitchDegrees);
        double cosPitch = Math.cos(pitch);
        double lookX = -Math.sin(yaw) * cosPitch;
        double lookY = -Math.sin(pitch);
        double lookZ = Math.cos(yaw) * cosPitch;
        return dx * lookX + dy * lookY + dz * lookZ > 0.0;
    }

    /** Keeps front-facing targets, then ranks aimed-at waypoints before nearby incidental ones. */
    public static <T> List<ViewCandidate<T>> prioritizeCandidates(
            Collection<ViewCandidate<T>> candidates, double yawDegrees, double pitchDegrees, int limit) {
        return prioritize(candidates, yawDegrees, pitchDegrees, limit, false, 0.0, 0.0);
    }

    /**
     * Selects candidates whose icon center or drawn beam span is in front of the camera.
     * Ordering continues to use the icon center so crosshair-focused labels keep priority.
     * Beam endpoint heights are camera-relative Y coordinates from the rendered beam geometry.
     */
    public static <T> List<ViewCandidate<T>> prioritizeBeamCandidates(
            Collection<ViewCandidate<T>> candidates, double yawDegrees, double pitchDegrees, int limit,
            double beamBottomDy, double beamTopDy) {
        return prioritize(candidates, yawDegrees, pitchDegrees, limit, true, beamBottomDy, beamTopDy);
    }

    private static <T> List<ViewCandidate<T>> prioritize(
            Collection<ViewCandidate<T>> candidates, double yawDegrees, double pitchDegrees, int limit,
            boolean includeBeamSpan, double beamBottomDy, double beamTopDy) {
        if (limit <= 0 || candidates.isEmpty()) return List.of();

        List<RankedCandidate<T>> ranked = new ArrayList<>(candidates.size());
        for (ViewCandidate<T> candidate : candidates) {
            boolean centerInFront = isInFront(candidate.dx, candidate.dy, candidate.dz, yawDegrees, pitchDegrees);
            boolean beamInFront = includeBeamSpan && isBeamInFront(candidate.dx, candidate.dz,
                    beamBottomDy, beamTopDy, yawDegrees, pitchDegrees);
            if (!centerInFront && !beamInFront) continue;
            double angle = lookAngleDegrees(candidate.dx, candidate.dy, candidate.dz,
                    yawDegrees, pitchDegrees);
            ranked.add(new RankedCandidate<>(candidate, angle, centerInFront));
        }
        ranked.sort(Comparator
                .comparing((RankedCandidate<T> candidate) -> !isFocused(candidate.angle))
                .thenComparingDouble(candidate -> isFocused(candidate.angle) ? candidate.angle : 0.0)
                .thenComparing(candidate -> !candidate.centerInFront)
                .thenComparingDouble(candidate -> candidate.candidate.distance));

        return ranked.stream().limit(limit).map(RankedCandidate::candidate).toList();
    }

    /** A vertical beam is visible if either endpoint is in front of the camera. */
    public static boolean isBeamInFront(double dx, double dz, double beamBottomDy, double beamTopDy,
                                        double yawDegrees, double pitchDegrees) {
        return isInFront(dx, beamBottomDy, dz, yawDegrees, pitchDegrees)
                || isInFront(dx, beamTopDy, dz, yawDegrees, pitchDegrees);
    }

    /** The angle between a target vector and the camera's Minecraft yaw/pitch view direction. */
    public static double lookAngleDegrees(double dx, double dy, double dz, double yawDegrees,
                                          double pitchDegrees) {
        double targetLength = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (targetLength < 1.0e-9) return 0.0;

        double yaw = Math.toRadians(yawDegrees);
        double pitch = Math.toRadians(pitchDegrees);
        double cosPitch = Math.cos(pitch);
        double lookX = -Math.sin(yaw) * cosPitch;
        double lookY = -Math.sin(pitch);
        double lookZ = Math.cos(yaw) * cosPitch;
        double dot = (dx * lookX + dy * lookY + dz * lookZ) / targetLength;
        return Math.toDegrees(Math.acos(Math.max(-1.0, Math.min(1.0, dot))));
    }

    public static boolean isLookingAt(double dx, double dy, double dz, double yawDegrees,
                                      double pitchDegrees, double maximumAngleDegrees) {
        return lookAngleDegrees(dx, dy, dz, yawDegrees, pitchDegrees)
                <= Math.max(0.0, maximumAngleDegrees) + ANGLE_EPSILON_DEGREES;
    }

    private static boolean isFocused(double angleDegrees) {
        return angleDegrees <= LABEL_LOOK_ANGLE_DEGREES + ANGLE_EPSILON_DEGREES;
    }

    /** Beacon yOffset is relative to the waypoint. Extend the beam below the dimension floor. */
    public static int beamStartOffset(int dimensionMinY, int waypointY) {
        return dimensionMinY - 64 - waypointY;
    }

    /** Includes the lower extension and reaches the dimension's upper build limit. */
    public static int beamHeight(int dimensionMinY, int dimensionMaxY) {
        return Math.max(16, dimensionMaxY - (dimensionMinY - 64));
    }

    public record ViewCandidate<T>(T value, double dx, double dy, double dz, double distance) {}

    private record RankedCandidate<T>(ViewCandidate<T> candidate, double angle, boolean centerInFront) {}
}
