package net.aosankaku.openmapdelta.render;

import java.util.ArrayList;
import java.util.List;
import net.aosankaku.openmapdelta.world.TileCache;

public final class MinimapTexture {
    public static final List<MinimapTexture> INSTANCES = new ArrayList<>();
    public final TileCache constructorCache;
    public int clearCount;
    public MinimapTexture(TileCache cache) { constructorCache = cache; INSTANCES.add(this); }
    public void clear() { clearCount++; }
}
