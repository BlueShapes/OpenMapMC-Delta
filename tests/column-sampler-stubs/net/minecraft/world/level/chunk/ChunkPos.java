package net.minecraft.world.level.chunk;

public final class ChunkPos {
    public final int x, z;
    public ChunkPos(int x, int z) { this.x = x; this.z = z; }
    public int x() { return x; }
    public int z() { return z; }
}
