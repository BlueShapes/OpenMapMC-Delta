// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.world;

import net.aosankaku.openmapdelta.color.BiomeGrassColor;
import net.aosankaku.openmapdelta.color.BlockColorRegistry;
import net.aosankaku.openmapdelta.color.ColorPalette;
import net.aosankaku.openmapdelta.config.BlockOverrideConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;

/** All world-dependent color callbacks run on the client thread, including after worker scans. */
public class ColumnSampler {
    public record Sample(int color, int height, int grassColor) {
        public Sample(int color, int height) { this(color, height, 0); }
    }
    private final BlockPos.MutableBlockPos position = new BlockPos.MutableBlockPos();
    @FunctionalInterface private interface States { BlockState get(int x, int y, int z); }

    public int sampleColumn(Level level, int x, int z) { return sampleSurface(level, x, z).color(); }

    public Sample sampleSurface(Level level, int x, int z) {
        LevelChunk chunk = level.getChunk(x >> 4, z >> 4);
        if (chunk.isEmpty()) return new Sample(0, Tile.UNKNOWN_HEIGHT);
        return surface(level, x, z, chunk.getMinBuildHeight(),
                chunk.getHeight(Heightmap.Types.WORLD_SURFACE, x, z),
                (lx, y, lz) -> chunk.getBlockState(position.set(x, y, z)), null, 0);
    }

    public Sample sampleCave(Level level, int x, int z, int layer) {
        LevelChunk chunk = level.getChunk(x >> 4, z >> 4);
        if (chunk.isEmpty()) return new Sample(0, Tile.UNKNOWN_HEIGHT);
        return cave(level, x, z, layer, chunk.getMinBuildHeight(), chunk.getMinBuildHeight() + chunk.getHeight(),
                chunk.getHeight(Heightmap.Types.WORLD_SURFACE, x, z),
                (lx, y, lz) -> chunk.getBlockState(position.set(x, y, z)), null, 0);
    }

    public Sample finish(Level level, ChunkMapSnapshot snapshot, ChunkColumnScan.Prepared prepared, int x, int z, int layer) {
        int worldX = snapshot.chunkX * 16 + x, worldZ = snapshot.chunkZ * 16 + z, i = x + z * 16;
        return layer == OpenMapDeltaClient.SURFACE_LAYER
                ? surface(level, worldX, worldZ, snapshot.minY(), snapshot.surface(x, z), snapshot::state, prepared, i)
                : cave(level, worldX, worldZ, layer, snapshot.minY(), snapshot.maxYExclusive(), snapshot.surface(x, z), snapshot::state, prepared, i);
    }

    private Sample surface(Level level, int x, int z, int min, int surface, States states,
                           ChunkColumnScan.Prepared prepared, int i) {
        if (surface < min) return new Sample(0, Tile.UNKNOWN_HEIGHT);
        boolean ceiling = DimensionStyle.hasCeiling(level);
        int y = prepared != null ? prepared.tops()[i] : ceiling
                ? ChunkColumnScan.ceilingSurface(min, min + level.dimensionType().logicalHeight() - 1,
                        columnY -> states.get(x & 15, columnY, z & 15).isAir())
                : surface;
        if (ceiling && y == ChunkColumnScan.NO_SPACE) return new Sample(0, Tile.UNKNOWN_HEIGHT);
        BlockState state = states.get(x & 15, y, z & 15);
        while (y > min && mapColor(level, state, x, y, z) == MapColor.NONE) state = states.get(x & 15, --y, z & 15);
        int depth = 0;
        BlockState original = state;
        if (y > min && !state.getFluidState().isEmpty()) {
            if (prepared != null && y == prepared.tops()[i]) depth = prepared.depths()[i];
            else {
                int below = y - 1;
                BlockState under;
                do { under = states.get(x & 15, below--, z & 15); depth++; }
                while (below > min && !under.getFluidState().isEmpty());
            }
            position.set(x, y, z);
            if (!state.isFaceSturdy(level, position, Direction.UP)) state = state.getFluidState().createLegacyBlock();
        }
        MapColor override = BlockOverrideConfig.getResolvedOverrides().get(state);
        MapColor map = override == null ? mapColor(level, state, x, y, z) : override;
        if (map == null || map == MapColor.NONE) map = MapColor.STONE;
        return color(level, x, y, z, original, map, depth, ceiling ? y : surface, override != null, false);
    }

    private Sample cave(Level level, int x, int z, int layer, int min, int max, int surface, States states,
                        ChunkColumnScan.Prepared prepared, int i) {
        int selected = Math.max(min, Math.min(max - 1, layer));
        if (selected > surface) return surface(level, x, z, min, surface, states, null, 0);
        int space = prepared == null ? ChunkColumnScan.NO_SPACE : prepared.spaces()[i];
        if (prepared == null) {
            for (int distance = 0; distance <= 8 && space == ChunkColumnScan.NO_SPACE; distance++) {
                for (int direction = -1; direction <= 1; direction += 2) {
                    int y = selected + distance * direction;
                    if (y <= min || y >= surface) continue;
                    BlockState state = states.get(x & 15, y, z & 15);
                    if (state.isAir() || !state.getFluidState().isEmpty()) { space = y; break; }
                }
            }
        }
        if (space == ChunkColumnScan.NO_SPACE) {
            BlockState wall = states.get(x & 15, selected, z & 15);
            MapColor map = mapColor(level, wall, x, selected, z);
            return color(level, x, selected, z, wall, map == null || map == MapColor.NONE ? MapColor.STONE : map, 0, selected, false, true);
        }
        int floor = prepared == null ? space - 1 : prepared.floors()[i];
        int depth = prepared == null ? 0 : prepared.depths()[i];
        BlockState state = states.get(x & 15, floor, z & 15);
        if (prepared == null && !state.getFluidState().isEmpty()) depth++;
        while (floor > min
                && (state.isAir() || !state.getFluidState().isEmpty())) {
            state = states.get(x & 15, --floor, z & 15);
            if (!state.getFluidState().isEmpty()) depth++;
        }
        if (state.isAir()) return new Sample(0, Tile.UNKNOWN_HEIGHT);
        MapColor override = BlockOverrideConfig.getResolvedOverrides().get(state);
        MapColor map = override == null ? mapColor(level, state, x, floor, z) : override;
        return color(level, x, floor, z, state, map == null || map == MapColor.NONE ? MapColor.STONE : map, depth, floor, override != null, false);
    }

    private MapColor mapColor(Level level, BlockState state, int x, int y, int z) {
        return state.getMapColor(level, position.set(x, y, z));
    }

    private Sample color(Level level, int x, int y, int z, BlockState state, MapColor map,
                         int depth, int height, boolean overridden, boolean wall) {
        MapColor.Brightness brightness = wall ? MapColor.Brightness.LOWEST : map == MapColor.WATER
                ? ColorPalette.waterBrightness(depth, x, z) : ColorPalette.terrainBrightness(0, x, z);
        position.set(x, y, z);
        int base = BlockColorRegistry.resolveRgb(level, position, state, map, brightness);
        int grass = 0;
        boolean leaves = state.is(BlockTags.LEAVES);
        if (!overridden && !level.dimension().equals(Level.NETHER) && !level.dimension().equals(Level.END)
                && (map == MapColor.GRASS || map == MapColor.PLANT || leaves)) {
            grass = BiomeGrassColor.pixel(level.getBiome(position).value().getGrassColor(x, z), brightness.modifier, leaves, true);
        }
        return new Sample(base, height, grass);
    }
}
