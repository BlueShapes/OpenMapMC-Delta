package net.minecraft.world.level;

import net.aosankaku.openmapdelta.mixin.BiomeManagerAccessor;
import net.minecraft.world.level.dimension.DimensionType;

public final class Level {
    private final int minY, height;
    private final Object dimension = new Object();
    private final Object biomeManager = new BiomeManager();
    private final ChunkSource chunkSource = new ChunkSource();
    private final DimensionType dimensionType = new DimensionType();
    private int skyBrightness;
    public Level(int minY, int height) { this.minY = minY; this.height = height; }
    public int getMinBuildHeight() { return minY; }
    public int getMaxBuildHeight() { return minY + height; }
    public int getMinY() { return minY; }
    public int getHeight() { return height; }
    public ChunkSource getChunkSource() { return chunkSource; }
    public DimensionType dimensionType() { return dimensionType; }
    public void setHasCeiling(boolean hasCeiling) { dimensionType.setHasCeiling(hasCeiling); }
    public int getSkyBrightness() { return skyBrightness; }
    public void setSkyBrightness(int skyBrightness) { this.skyBrightness = skyBrightness; }
    public Object dimension() { return dimension; }
    public Object getBiomeManager() { return biomeManager; }
    public static final class BiomeManager implements BiomeManagerAccessor {
        public long odysseymap$getBiomeZoomSeed() { return 0x5A17L; }
    }
}
