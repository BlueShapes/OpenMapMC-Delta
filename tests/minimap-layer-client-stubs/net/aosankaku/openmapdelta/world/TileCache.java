package net.aosankaku.openmapdelta.world;

import java.util.ArrayList;
import java.util.List;

public class TileCache {
    public static final List<TileCache> INSTANCES = new ArrayList<>();
    public static int siblingCount;
    private int layer;
    private String sessionKey;
    private Object dimension;
    public int clearCount, setLayerCount, preloadCount, tickCount, invalidateCount;
    public TileCache(ColumnSampler sampler) { this(sampler, Integer.MIN_VALUE); }
    public TileCache(ColumnSampler sampler, int layer) { this.layer = layer; INSTANCES.add(this); }
    public TileCache createSibling(int layer) { siblingCount++; return new TileCache(null, layer); }
    public int getLayer() { return layer; }
    public void setLayer(int layer) { this.layer = layer; setLayerCount++; }
    public void clear() { clearCount++; }
    public boolean beginSession(String key, String identity, Object dimension) {
        boolean changed = !key.equals(sessionKey) || this.dimension != dimension;
        sessionKey = key; this.dimension = dimension; return changed;
    }
    public void preloadNear(Object dimension, int x, int z) { preloadCount++; }
    public void tick() { tickCount++; }
    public void invalidateArea(Object level, int x1, int z1, int x2, int z2) { invalidateCount++; }
}
