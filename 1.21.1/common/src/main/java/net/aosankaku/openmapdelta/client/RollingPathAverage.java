// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.client;

import java.util.ArrayDeque;

/** Horizontal distance travelled per second, retaining stationary intervals in its denominator. */
public final class RollingPathAverage {
    private static final long MAX_SAMPLE_AGE_NANOS = 60_000_000_000L;
    private record Segment(long startNanos, long endNanos, double distance) {}

    private final ArrayDeque<Segment> segments = new ArrayDeque<>();
    private boolean hasLastTime;
    private long lastTimeNanos;

    /** Adds distance travelled since the preceding sample. The first sample seeds the window. */
    public void add(long timeNanos, double distance) {
        if (!Double.isFinite(distance) || distance < 0.0) distance = 0.0;
        if (hasLastTime && timeNanos < lastTimeNanos) reset();
        if (hasLastTime && timeNanos > lastTimeNanos) {
            segments.addLast(new Segment(lastTimeNanos, timeNanos, distance));
        }
        lastTimeNanos = timeNanos;
        hasLastTime = true;
        prune(timeNanos - MAX_SAMPLE_AGE_NANOS);
    }

    public double blocksPerSecond(long nowNanos, long windowNanos) {
        if (windowNanos <= 0L || segments.isEmpty()) return 0.0;
        long cutoff = nowNanos - windowNanos;
        prune(cutoff);
        if (segments.isEmpty()) return 0.0;

        long start = Math.max(cutoff, segments.peekFirst().startNanos());
        long elapsed = nowNanos - start;
        if (elapsed <= 0L) return 0.0;
        double distance = 0.0;
        for (Segment segment : segments) {
            long overlapStart = Math.max(start, segment.startNanos());
            long overlapEnd = Math.min(nowNanos, segment.endNanos());
            long duration = segment.endNanos() - segment.startNanos();
            if (overlapEnd > overlapStart && duration > 0L) {
                distance += segment.distance() * (overlapEnd - overlapStart) / duration;
            }
        }
        return distance * 1_000_000_000.0 / elapsed;
    }

    public void reset() {
        segments.clear();
        hasLastTime = false;
        lastTimeNanos = 0L;
    }

    private void prune(long cutoffNanos) {
        while (!segments.isEmpty() && segments.peekFirst().endNanos() <= cutoffNanos) segments.removeFirst();
    }
}
