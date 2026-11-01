// Copyright (c) 2026 AoSankaku. OpenMapMC Delta modifications; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.client;

import java.util.Objects;

/** Correlates successful sleep with a spawn notification, regardless of packet ordering. */
public final class SleepPointSession {
    private static final int CONFIRMATION_TICKS = 20;
    public record Point(String dimension, int x, int y, int z) {}

    private long ticks;
    private long spawnTick = Long.MIN_VALUE;
    private Point spawnPoint;
    private long pendingTick;
    private Point sleeping;
    private Point pending;

    public void confirmSpawn(Point point) {
        // A notification for an already recorded sleep must not leak into the next sleep.
        if (point != null && (sleeping == null || pending != null)) {
            spawnPoint = point;
            spawnTick = ticks;
        }
    }

    /** Returns each qualifying sleep once. A skipped sleep leaves the stored marker untouched. */
    public Point tick(Point point, boolean spawnMatches, boolean includeWithoutSpawn) {
        ticks++;
        if (point != null && !Objects.equals(point, sleeping)) {
            pending = point;
            pendingTick = ticks;
        }
        sleeping = point;
        if (pending == null) return null;
        if (ticks - pendingTick > CONFIRMATION_TICKS) {
            pending = null;
            return null;
        }
        boolean confirmed = pending.equals(spawnPoint)
                && spawnTick != Long.MIN_VALUE && ticks - spawnTick <= CONFIRMATION_TICKS;
        if (!spawnMatches && !includeWithoutSpawn && !confirmed) return null;
        Point result = pending;
        pending = null;
        spawnTick = Long.MIN_VALUE;
        spawnPoint = null;
        return result;
    }

    public void reset() {
        ticks = 0;
        spawnTick = Long.MIN_VALUE;
        spawnPoint = null;
        sleeping = null;
        pending = null;
    }
}
