// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.death;

/** Persistent recovery location, separate from the original coordinates used to identify a death. */
public final class DeathPointMotion {
    public interface Terrain {
        boolean loaded(double x, double y, double z, double dy);
        double collide(double x, double y, double z, double dy);
        double minimumY();
    }

    private int originX, originY, originZ;
    private double x, y, z, velocityY;
    private transient long lastTick;
    private transient boolean clockStarted;
    private transient double previousX, previousY, previousZ;

    public DeathPointMotion(int x, int y, int z) {
        originX = x; originY = y; originZ = z;
        this.x = x + 0.5; this.y = y; this.z = z + 0.5;
    }

    public int originX() { return originX; }
    public int originY() { return originY; }
    public int originZ() { return originZ; }
    public double x() { return x; }
    public double y() { return y; }
    public double z() { return z; }
    public double velocityY() { return velocityY; }
    public int blockX() { return (int) Math.floor(x); }
    public int blockY() { return (int) Math.floor(y); }
    public int blockZ() { return (int) Math.floor(z); }
    public boolean falling() { return velocityY < -0.001; }
    public double renderX(double partialTick) { return interpolate(previousX, x, partialTick); }
    public double renderY(double partialTick) { return interpolate(previousY, y, partialTick); }
    public double renderZ(double partialTick) { return interpolate(previousZ, z, partialTick); }
    private double interpolate(double previous, double current, double partialTick) {
        return clockStarted ? previous + (current - previous) * Math.max(0, Math.min(1, partialTick)) : current;
    }
    private void rememberPosition() { previousX = x; previousY = y; previousZ = z; }

    public void freeze(long tick) {
        rememberPosition(); velocityY = 0;
        lastTick = tick; clockStarted = true;
    }

    /** Correct old no-loot predictions without changing the identity of the death. */
    public boolean resetToOrigin(long tick) {
        double originCenterX = originX + 0.5, originCenterZ = originZ + 0.5;
        boolean changed = x != originCenterX || y != originY || z != originCenterZ;
        x = originCenterX; y = originY; z = originCenterZ;
        freeze(tick);
        return changed;
    }

    /** The tracked item supplies horizontal scatter, falling speed, water buoyancy and modded movement. */
    public boolean follow(double x, double y, double z, double velocityY, long tick) {
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z) || !Double.isFinite(velocityY)) return false;
        if (!clockStarted || lastTick != tick) rememberPosition();
        boolean changed = this.x != x || this.y != y || this.z != z;
        this.x = x; this.y = y; this.z = z;
        this.velocityY = Math.max(-64, Math.min(64, velocityY));
        lastTick = tick; clockStarted = true;
        return changed;
    }

    /** No chunk loading, wall-clock catch-up or movement while the world is paused. */
    public boolean tick(long tick, int maximumTicks, Terrain terrain) {
        int steps = clockStarted ? (int) Math.min(maximumTicks, Math.max(0, tick - lastTick)) : 1;
        if (steps > 0) rememberPosition();
        lastTick = tick; clockStarted = true;
        boolean moved = false;
        for (int i = 0; i < steps; i++) {
            double requested = (velocityY - 0.04);
            // Stop at the dimension's void boundary, where vanilla items are destroyed.
            if (y <= terrain.minimumY()) { velocityY = 0; break; }
            requested = Math.max(terrain.minimumY() - y, requested);
            if (!terrain.loaded(x, y, z, requested)) break;
            double actual = terrain.collide(x, y, z, requested);
            y += actual;
            moved |= actual != 0;
            velocityY = Math.abs(actual - requested) > 1.0e-7 ? 0 : requested * 0.98;
        }
        return moved;
    }
}
