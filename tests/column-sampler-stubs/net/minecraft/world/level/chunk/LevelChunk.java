package net.minecraft.world.level.chunk;

import net.aosankaku.openmapdelta.world.ChunkMapRevision;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

public final class LevelChunk implements ChunkMapRevision {
    private final Level level;
    private final int minY, height;
    private final ChunkPos pos;
    private final LevelChunkSection[] sections;
    private boolean empty;
    private long revision;

    public LevelChunk(Level level, int minY, int height, int chunkX, int chunkZ) {
        this.level = level; this.minY = minY; this.height = height; pos = new ChunkPos(chunkX, chunkZ);
        sections = new LevelChunkSection[height / 16];
        for (int i = 0; i < sections.length; i++) sections[i] = new LevelChunkSection();
    }
    public Level getLevel() { return level; }
    public ChunkPos getPos() { return pos; }
    public int getMinBuildHeight() { return minY; }
    public int getMinY() { return minY; }
    public int getHeight() { return height; }
    public LevelChunkSection[] getSections() { return sections; }
    public boolean isEmpty() { return empty; }
    public void setEmpty(boolean empty) { this.empty = empty; }
    public long odysseymap$getMapRevision() { return revision; }
    public int getHeight(Heightmap.Types type, int x, int z) {
        for (int y = minY + height - 1; y >= minY; y--) if (!getBlockState(x, y, z).isAir()) return y;
        return minY - 1;
    }
    public BlockState getBlockState(net.minecraft.core.BlockPos pos) { return getBlockState(pos.getX(), pos.getY(), pos.getZ()); }
    public BlockState getBlockState(int x, int y, int z) {
        if (y < minY || y >= minY + height) return Blocks.AIR.defaultBlockState();
        return sections[(y - minY) >> 4].getStates().get(x, y, z);
    }
    public void setBlockState(int x, int y, int z, BlockState state) {
        if (y < minY || y >= minY + height) throw new IllegalArgumentException("Y outside chunk: " + y);
        sections[(y - minY) >> 4].getStates().set(x, y, z, state);
        revision++;
    }
}
