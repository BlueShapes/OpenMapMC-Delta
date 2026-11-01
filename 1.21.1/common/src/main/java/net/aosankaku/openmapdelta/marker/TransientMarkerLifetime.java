// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.marker;

/** Session-only expiry policy; distance is measured to the waypoint's block center. */
public final class TransientMarkerLifetime {
    public static final double ARRIVAL_RADIUS_SQUARED = 25.0;
    public static final int DEFAULT_NOTICE_DURATION_SECONDS = 15;
    public static final long NOTICE_DURATION_MS = DEFAULT_NOTICE_DURATION_SECONDS * 1_000L;
    private static final int MIN_NOTICE_DURATION_SECONDS = 5;
    private static final int MAX_NOTICE_DURATION_SECONDS = 600;

    private final boolean timed;
    private final long createdAtMs;
    private final long durationMs;
    private boolean armed;

    private TransientMarkerLifetime(boolean timed, long createdAtMs, long durationMs, boolean armed) {
        this.timed = timed;
        this.createdAtMs = createdAtMs;
        this.durationMs = durationMs;
        this.armed = armed;
    }

    public static TransientMarkerLifetime arrival(boolean initiallyInside) {
        return new TransientMarkerLifetime(false, 0, 0, !initiallyInside);
    }

    public static TransientMarkerLifetime notice(long nowMs) {
        return notice(nowMs, DEFAULT_NOTICE_DURATION_SECONDS);
    }

    public static TransientMarkerLifetime notice(long nowMs, int durationSeconds) {
        if (durationSeconds < MIN_NOTICE_DURATION_SECONDS || durationSeconds > MAX_NOTICE_DURATION_SECONDS) {
            throw new IllegalArgumentException("Notification duration must be between 5 and 600 seconds");
        }
        return new TransientMarkerLifetime(true, nowMs, durationSeconds * 1_000L, false);
    }

    public boolean expired(boolean sameDimension, double distanceSquared, long nowMs) {
        if (timed) return nowMs - createdAtMs >= durationMs;
        // Leaving the dimension also leaves the arrival sphere.
        if (!sameDimension || distanceSquared > ARRIVAL_RADIUS_SQUARED) {
            armed = true;
            return false;
        }
        return armed;
    }
}
