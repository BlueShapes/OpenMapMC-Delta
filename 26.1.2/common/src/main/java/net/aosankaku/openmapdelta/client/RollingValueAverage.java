// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.client;

import java.util.ArrayDeque;

/** A bounded time-window average for client measurements such as FPS. */
public final class RollingValueAverage {
    private static final long MAX_SAMPLE_AGE_NANOS = 60_000_000_000L;
    private record Sample(long timeNanos, double value) {}

    private final ArrayDeque<Sample> samples = new ArrayDeque<>();

    public void add(long timeNanos, double value) {
        if (!Double.isFinite(value)) return;
        Sample last = samples.peekLast();
        if (last != null && timeNanos < last.timeNanos()) reset();
        samples.addLast(new Sample(timeNanos, value));
        prune(timeNanos - MAX_SAMPLE_AGE_NANOS);
    }

    public double average(long nowNanos, long windowNanos) {
        if (windowNanos <= 0L) return Double.NaN;
        prune(nowNanos - windowNanos);
        if (samples.isEmpty()) return Double.NaN;
        double total = 0.0;
        for (Sample sample : samples) total += sample.value();
        return total / samples.size();
    }

    public void reset() {
        samples.clear();
    }

    private void prune(long cutoffNanos) {
        while (!samples.isEmpty() && samples.peekFirst().timeNanos() < cutoffNanos) samples.removeFirst();
    }
}
