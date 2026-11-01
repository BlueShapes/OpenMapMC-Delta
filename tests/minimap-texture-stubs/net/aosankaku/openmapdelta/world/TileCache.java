package net.aosankaku.openmapdelta.world;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class TileCache {
    private final Map<Key, Tile> tiles = new HashMap<>();
    private long samplingEpoch;
    private int getOrCreateCount;

    public static int alignTile(int blockCoord) {
        return Math.floorDiv(blockCoord + 64, Tile.SIZE) * Tile.SIZE;
    }

    public Tile getOrCreate(Object dimension, int alignedX, int alignedZ) {
        getOrCreateCount++;
        return tiles.computeIfAbsent(new Key(dimension, alignedX, alignedZ), ignored -> new Tile());
    }

    public Tile get(Object dimension, int alignedX, int alignedZ) {
        return tiles.get(new Key(dimension, alignedX, alignedZ));
    }

    public boolean remove(Object dimension, int alignedX, int alignedZ) {
        return tiles.remove(new Key(dimension, alignedX, alignedZ)) != null;
    }

    /** Snapshot keyed like the real fullscreen tile cache for the texture harness. */
    public Map<Long, Tile> getTiles() {
        Map<Long, Tile> snapshot = new HashMap<>();
        for (Map.Entry<Key, Tile> entry : tiles.entrySet()) {
            Key key = entry.getKey();
            snapshot.put(((long) key.x() << Integer.SIZE) | (key.z() & 0xFFFFFFFFL), entry.getValue());
        }
        return snapshot;
    }

    public void clear() { tiles.clear(); samplingEpoch++; }
    public long getSamplingEpoch() { return samplingEpoch; }
    public long getRevision() { return tiles.values().stream().mapToLong(Tile::getImageRevision).sum(); }
    public int getOrCreateCount() { return getOrCreateCount; }
    public Collection<Tile> tiles() { return tiles.values(); }

    private record Key(Object dimension, int x, int z) {}
}
