package net.minecraft.world.level;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.chunk.LevelChunk;

public final class Level {
    public static final Object OVERWORLD = new Object();
    public static final Object NETHER = new Object();
    public static final Object END = new Object();
    private final Object dimension;
    private final DimensionType type;
    private LevelChunk chunk;

    public Level(Object dimension, boolean ceiling, int logicalHeight) {
        this.dimension = dimension; type = new DimensionType(ceiling, logicalHeight);
    }
    public Object dimension() { return dimension; }
    public DimensionType dimensionType() { return type; }
    public void setChunk(LevelChunk chunk) { this.chunk = chunk; }
    public LevelChunk getChunk(int x, int z) { return chunk; }
    public BiomeHolder getBiome(BlockPos pos) { return new BiomeHolder(); }

    public static final class DimensionType {
        private final boolean ceiling;
        private final int logicalHeight;
        public DimensionType(boolean ceiling, int logicalHeight) { this.ceiling = ceiling; this.logicalHeight = logicalHeight; }
        public boolean hasCeiling() { return ceiling; }
        public int logicalHeight() { return logicalHeight; }
    }
    public static final class BiomeHolder {
        public Biome value() { return new Biome(); }
    }
    public static final class Biome {
        public int getGrassColor(int x, int z) { return 0x55AA33; }
    }
}
