package xaero.hud.minimap.player.tracker.synced;

import java.util.UUID;

/** Test-only approximation of Xaero's synced player record. */
public final class SyncedTrackedPlayer {
    private final UUID id;
    private final double x, y, z;
    private final Object dimensionKey;

    public SyncedTrackedPlayer(UUID id, double x, double y, double z, Object dimensionKey) {
        this.id = id;
        this.x = x;
        this.y = y;
        this.z = z;
        this.dimensionKey = dimensionKey;
    }

    public UUID getId() { return id; }
    public double getX() { return x; }
    public double getY() { return y; }
    public double getZ() { return z; }
    public Object getDimensionKey() { return dimensionKey; }
}
