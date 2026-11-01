// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.world;

import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.levelgen.Heightmap;

/** Captured on the client thread; all mutable palettes and height values are copied. */
public final class ChunkMapSnapshot implements ChunkColumnScan.Columns {
    private final PalettedContainer<BlockState>[] sections;
    private final int[] surfaces = new int[256];
    private final int minY, maxY, minSection, ceilingYExclusive;
    private final boolean hasCeiling;
    public final int chunkX, chunkZ;
    public final long sourceRevision;

    @SuppressWarnings("unchecked")
    public ChunkMapSnapshot(LevelChunk chunk) {
        sourceRevision = ((ChunkMapRevision) chunk).odysseymap$getMapRevision();
        chunkX = chunk.getPos().x;
        chunkZ = chunk.getPos().z;
        minY = chunk.getMinBuildHeight();
        maxY = minY + chunk.getHeight();
        minSection = Math.floorDiv(minY, 16);
        hasCeiling = chunk.getLevel().dimensionType().hasCeiling();
        ceilingYExclusive = Math.min(maxY, minY + chunk.getLevel().dimensionType().logicalHeight());
        LevelChunkSection[] live = chunk.getSections();
        sections = (PalettedContainer<BlockState>[]) new PalettedContainer<?>[live.length];
        for (int i = 0; i < live.length; i++) {
            if (live[i] != null && !live[i].hasOnlyAir()) sections[i] = live[i].getStates().copy();
        }
        for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++) {
            surfaces[x + z * 16] = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
        }
    }

    public BlockState state(int x, int y, int z) {
        int section = Math.floorDiv(y, 16) - minSection;
        if (y < minY || y >= maxY || section < 0 || section >= sections.length || sections[section] == null) {
            return Blocks.AIR.defaultBlockState();
        }
        return sections[section].get(x & 15, y & 15, z & 15);
    }
    public boolean hasCeiling() { return hasCeiling; }
    public int ceilingYExclusive() { return ceilingYExclusive; }
    public int minY() { return minY; }
    public int maxYExclusive() { return maxY; }
    public int surface(int x, int z) { return surfaces[x + z * 16]; }
    public boolean air(int x, int y, int z) { return state(x, y, z).isAir(); }
    public boolean fluid(int x, int y, int z) { return !state(x, y, z).getFluidState().isEmpty(); }
}
