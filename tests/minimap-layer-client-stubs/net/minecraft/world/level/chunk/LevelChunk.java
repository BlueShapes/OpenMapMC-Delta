package net.minecraft.world.level.chunk;

import net.minecraft.world.level.levelgen.Heightmap;

public final class LevelChunk {
    private int worldSurfaceY;
    private boolean worldSurfacePrimed;
    public int heightReads;
    public int lastHeightX, lastHeightZ;

    public LevelChunk(int worldSurfaceY, boolean worldSurfacePrimed) {
        this.worldSurfaceY = worldSurfaceY;
        this.worldSurfacePrimed = worldSurfacePrimed;
    }

    public boolean hasPrimedHeightmap(Heightmap.Types type) {
        return type == Heightmap.Types.WORLD_SURFACE && worldSurfacePrimed;
    }

    public int getHeight(Heightmap.Types type, int localX, int localZ) {
        if (type != Heightmap.Types.WORLD_SURFACE) throw new IllegalArgumentException("Unexpected heightmap type");
        heightReads++;
        lastHeightX = localX;
        lastHeightZ = localZ;
        return worldSurfaceY;
    }

    public void setWorldSurfaceY(int worldSurfaceY) { this.worldSurfaceY = worldSurfaceY; }
    public void setWorldSurfacePrimed(boolean worldSurfacePrimed) { this.worldSurfacePrimed = worldSurfacePrimed; }
}
