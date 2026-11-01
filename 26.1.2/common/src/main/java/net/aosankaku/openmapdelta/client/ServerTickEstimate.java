// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.client;

import java.util.OptionalDouble;

/** Estimates server TPS from vanilla time packets, returning no value when the sample goes stale. */
public final class ServerTickEstimate {
    public static final long STALE_AFTER_NANOS = 5_000_000_000L;

    private boolean hasPrevious;
    private long previousTick;
    private long previousTimeNanos;
    private long lastObservedNanos;
    private double ticksPerSecond = Double.NaN;

    public void observe(long serverTick, long nowNanos) {
        if (!hasPrevious || serverTick <= previousTick || nowNanos <= previousTimeNanos) {
            ticksPerSecond = Double.NaN;
            hasPrevious = true;
            previousTick = serverTick;
            previousTimeNanos = nowNanos;
            lastObservedNanos = nowNanos;
            return;
        }

        long elapsed = nowNanos - previousTimeNanos;
        long tickDelta = serverTick - previousTick;
        double estimate = tickDelta * 1_000_000_000.0 / elapsed;
        ticksPerSecond = Double.isFinite(estimate) && estimate > 0.0 && estimate <= 10_000.0
                ? estimate : Double.NaN;
        previousTick = serverTick;
        previousTimeNanos = nowNanos;
        lastObservedNanos = nowNanos;
    }

    public OptionalDouble ticksPerSecond(long nowNanos) {
        if (!Double.isFinite(ticksPerSecond) || nowNanos < lastObservedNanos
                || nowNanos - lastObservedNanos > STALE_AFTER_NANOS) return OptionalDouble.empty();
        return OptionalDouble.of(ticksPerSecond);
    }

    public void reset() {
        hasPrevious = false;
        previousTick = 0L;
        previousTimeNanos = 0L;
        lastObservedNanos = 0L;
        ticksPerSecond = Double.NaN;
    }
}
