// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.client;

/** Coalesces cache-driven fullscreen invalidations to match the renderer's safe-mode cadence. */
public final class CacheRevisionTracker {
    private long lastRevision = Long.MIN_VALUE;
    private int cooldown;

    public void tick() {
        if (cooldown > 0) cooldown--;
    }

    public boolean shouldInvalidate(long revision, boolean safeRenderMode) {
        if (revision == lastRevision) return false;
        if (safeRenderMode && cooldown > 0) return false;
        lastRevision = revision;
        cooldown = safeRenderMode ? 3 : 0;
        return true;
    }

    public void forceInvalidation(long revision, boolean safeRenderMode) {
        lastRevision = revision;
        cooldown = safeRenderMode ? 3 : 0;
    }

    public void reset() {
        lastRevision = Long.MIN_VALUE;
        cooldown = 0;
    }
}
