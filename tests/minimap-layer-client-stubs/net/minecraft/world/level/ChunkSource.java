package net.minecraft.world.level;

import net.minecraft.world.level.chunk.LevelChunk;

/** Minimal loaded-client-chunk fixture; the boolean records whether a caller could request generation. */
public final class ChunkSource {
    private int chunkX, chunkZ;
    private LevelChunk chunk;
    public int queries, lastChunkX, lastChunkZ;
    public boolean lastGenerate;
    public Object lastStatus;

    public void setLoadedChunk(int chunkX, int chunkZ, LevelChunk chunk) {
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
        this.chunk = chunk;
    }

    public LevelChunk getChunk(int chunkX, int chunkZ,
                               net.minecraft.world.level.chunk.ChunkStatus status, boolean generate) {
        return getChunkInternal(chunkX, chunkZ, status, generate);
    }

    public LevelChunk getChunk(int chunkX, int chunkZ,
                               net.minecraft.world.level.chunk.status.ChunkStatus status, boolean generate) {
        return getChunkInternal(chunkX, chunkZ, status, generate);
    }

    private LevelChunk getChunkInternal(int chunkX, int chunkZ, Object status, boolean generate) {
        queries++;
        lastChunkX = chunkX;
        lastChunkZ = chunkZ;
        lastStatus = status;
        lastGenerate = generate;
        return this.chunkX == chunkX && this.chunkZ == chunkZ ? chunk : null;
    }
}
