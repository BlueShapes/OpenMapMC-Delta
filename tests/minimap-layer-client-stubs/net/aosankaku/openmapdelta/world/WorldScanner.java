package net.aosankaku.openmapdelta.world;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;

public final class WorldScanner {
    public static final List<WorldScanner> INSTANCES = new ArrayList<>();
    public final TileCache cache;
    public int resetCount, applyCount, tickCount;
    public WorldScanner(TileCache cache) { this.cache = cache; INSTANCES.add(this); }
    public void reset() { resetCount++; }
    public void applyCompleted(Minecraft mc) { applyCount++; }
    public void tick(Minecraft mc) { tickCount++; }
}
