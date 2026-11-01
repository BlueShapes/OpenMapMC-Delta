package net.aosankaku.openmapdelta.world;

public final class ChunkMapGeneration {
    public static int closeCount;
    private ChunkMapGeneration() {}
    public static void close() { closeCount++; }
}
