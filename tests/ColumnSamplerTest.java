// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.world;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.aosankaku.openmapdelta.config.BlockOverrideConfig;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.material.MapColor;

/** Exercises the real live and detached-snapshot column samplers without starting Minecraft. */
public final class ColumnSamplerTest {
    private static final int SURFACE = Integer.MIN_VALUE;
    private static final List<String> failures = new ArrayList<>();
    private static int assertions;

    public static void main(String[] args) throws Exception {
        netherRoofAndAboveRoofBuild();
        caveBandAboveNeighborSurfaceUsesSurface();
        mixedEndChunkAirColumnRemainsUnknown();
        minimumYNonBedrockSurfaceRemainsVisible();
        ceilingWithoutRoofKeepsExposedFloor();
        adjacentLavaSurface();
        negativeBuildHeightCeiling();
        isolatedPocketIsIgnored();
        missingCavityOrFloorIsUnknown();
        overworldAndEndKeepHighestSurface();
        caveSamplingIsUnchanged();
        deepCaveFindsFloorBelowSixtyFourBlocks();
        floorlessCaveRemainsUnknown();
        nullAndNoneCaveColorsUseStoneFallback();
        nullAndNoneCaveFloorsAtOrdinaryHeightUseStone();
        grassTintUsesNativeTextureChannelOrder();
        blockOverrideStillApplies();
        snapshotStaysDetachedAfterLiveEdit();

        if (!failures.isEmpty()) {
            System.err.println("Column sampler regression checks failed: " + failures.size() + "/" + assertions);
            for (int i = 0; i < Math.min(16, failures.size()); i++) System.err.println("  - " + failures.get(i));
            throw new AssertionError("column sampler regression checks failed");
        }
        System.out.println("Column sampler checks passed (" + assertions + " assertions).");
    }

    private static void netherRoofAndAboveRoofBuild() throws Exception {
        Fixture fixture = new Fixture(Level.NETHER, true, 0, 256, 128);
        fixture.fill(123, 127, Blocks.BEDROCK.defaultBlockState());
        fixture.set(64, Blocks.NETHERRACK.defaultBlockState());
        fixture.setAt(200, Blocks.STONE.defaultBlockState());

        ColumnSampler.Sample live = fixture.sampler.sampleSurface(fixture.level, fixture.x, fixture.z);
        sample(Blocks.NETHERRACK.defaultBlockState(), 64, live, "Nether surface below roof");
        parity(fixture, SURFACE, "Nether surface live/prepared parity");
    }

    private static void caveBandAboveNeighborSurfaceUsesSurface() throws Exception {
        Fixture fixture = new Fixture(Level.OVERWORLD, false, -64, 384, 384);
        fixture.set(65, Blocks.DIRT.defaultBlockState());
        fixture.set(70, Blocks.STONE.defaultBlockState());

        ColumnSampler.Sample live = fixture.sampler.sampleCave(fixture.level, fixture.x, fixture.z, 80);
        sample(Blocks.STONE.defaultBlockState(), 70, live, "cave band above neighboring surface falls back to terrain");
        parity(fixture, 80, "above-surface cave live/prepared parity");

        ChunkMapSnapshot snapshot = new ChunkMapSnapshot(fixture.chunk);
        ChunkColumnScan.Prepared prepared = ChunkColumnScan.prepare(snapshot, 80);
        fixture.set(70, Blocks.SOUL_SOIL.defaultBlockState());
        sample(Blocks.SOUL_SOIL.defaultBlockState(), 70,
                fixture.sampler.sampleCave(fixture.level, fixture.x, fixture.z, 80),
                "live above-surface fallback follows the current terrain");
        sample(Blocks.STONE.defaultBlockState(), 70,
                fixture.sampler.finish(fixture.level, snapshot, prepared, fixture.x, fixture.z, 80),
                "prepared above-surface fallback uses detached terrain states");
    }

    private static void mixedEndChunkAirColumnRemainsUnknown() throws Exception {
        Fixture fixture = new Fixture(Level.END, false, -64, 384, 384);
        fixture.setAt(fixture.x + 1, 32, fixture.z, Blocks.STONE.defaultBlockState());

        unknown(fixture.sampler.sampleCave(fixture.level, fixture.x, fixture.z, 80),
                "air column in a loaded End chunk stays unknown above its empty surface");
        ChunkMapSnapshot snapshot = new ChunkMapSnapshot(fixture.chunk);
        ChunkColumnScan.Prepared prepared = ChunkColumnScan.prepare(snapshot, 80);
        unknown(fixture.sampler.finish(fixture.level, snapshot, prepared, fixture.x, fixture.z, 80),
                "prepared air column in a loaded End chunk stays unknown");
    }

    private static void minimumYNonBedrockSurfaceRemainsVisible() throws Exception {
        Fixture fixture = new Fixture(Level.OVERWORLD, false, 0, 256, 256);
        fixture.set(0, Blocks.STONE.defaultBlockState());

        sample(Blocks.STONE.defaultBlockState(), 0,
                fixture.sampler.sampleCave(fixture.level, fixture.x, fixture.z, 8),
                "above-surface cave fallback keeps a non-bedrock block at minimum Y");
        parity(fixture, 8, "minimum-Y fallback live/prepared parity");
    }

    private static void ceilingWithoutRoofKeepsExposedFloor() throws Exception {
        Fixture fixture = new Fixture(Level.NETHER, true, 0, 256, 128);
        fixture.set(64, Blocks.NETHERRACK.defaultBlockState());
        sample(Blocks.NETHERRACK.defaultBlockState(), 64,
                fixture.sampler.sampleSurface(fixture.level, fixture.x, fixture.z), "open roof retains exposed floor");
        parity(fixture, SURFACE, "open roof live/prepared parity");
    }

    private static void adjacentLavaSurface() throws Exception {
        Fixture fixture = new Fixture(Level.NETHER, true, 0, 256, 128);
        fixture.fill(123, 127, Blocks.BEDROCK.defaultBlockState());
        fixture.set(30, Blocks.NETHERRACK.defaultBlockState());
        fixture.set(31, Blocks.LAVA.defaultBlockState());
        for (int y = 123; y <= 127; y++) fixture.setAt(fixture.x + 1, y, fixture.z, Blocks.BEDROCK.defaultBlockState());
        fixture.setAt(fixture.x + 1, 64, fixture.z, Blocks.NETHERRACK.defaultBlockState());

        ColumnSampler.Sample live = fixture.sampler.sampleSurface(fixture.level, fixture.x, fixture.z);
        sample(Blocks.LAVA.defaultBlockState(), 31, live, "Nether lava surface");
        parity(fixture, SURFACE, "lava surface live/prepared parity");
        sample(Blocks.NETHERRACK.defaultBlockState(), 64,
                fixture.sampler.sampleSurface(fixture.level, fixture.x + 1, fixture.z), "adjacent Nether floor column");
        parity(fixture, fixture.x + 1, fixture.z, SURFACE, "adjacent Nether floor live/prepared parity");
    }

    private static void negativeBuildHeightCeiling() throws Exception {
        Fixture fixture = new Fixture(Level.NETHER, true, -64, 256, 128);
        fixture.fill(59, 63, Blocks.BEDROCK.defaultBlockState());
        fixture.set(-32, Blocks.NETHERRACK.defaultBlockState());
        fixture.setAt(100, Blocks.STONE.defaultBlockState());
        sample(Blocks.NETHERRACK.defaultBlockState(), -32,
                fixture.sampler.sampleSurface(fixture.level, fixture.x, fixture.z), "negative-min Nether floor");
        parity(fixture, SURFACE, "negative-min Nether live/prepared parity");
    }

    private static void isolatedPocketIsIgnored() throws Exception {
        Fixture fixture = new Fixture(Level.NETHER, true, 0, 256, 128);
        fixture.fill(0, 127, Blocks.NETHERRACK.defaultBlockState());
        fixture.set(80, Blocks.AIR.defaultBlockState());

        ColumnSampler.Sample live = fixture.sampler.sampleSurface(fixture.level, fixture.x, fixture.z);
        unknown(live, "single-block pocket does not count as a cave");
        parity(fixture, SURFACE, "isolated pocket live/prepared parity");
    }

    private static void missingCavityOrFloorIsUnknown() throws Exception {
        Fixture solid = new Fixture(Level.NETHER, true, 0, 256, 128);
        solid.fill(0, 255, Blocks.BEDROCK.defaultBlockState());
        unknown(solid.sampler.sampleSurface(solid.level, solid.x, solid.z), "solid Nether column is unknown");
        parity(solid, SURFACE, "solid Nether live/prepared parity");

        Fixture noFloor = new Fixture(Level.NETHER, true, 0, 256, 128);
        noFloor.fill(123, 127, Blocks.BEDROCK.defaultBlockState());
        unknown(noFloor.sampler.sampleSurface(noFloor.level, noFloor.x, noFloor.z), "roof with no floor is unknown");
        parity(noFloor, SURFACE, "roof with no floor live/prepared parity");

        Fixture empty = new Fixture(Level.NETHER, true, 0, 256, 128);
        unknown(empty.sampler.sampleSurface(empty.level, empty.x, empty.z), "empty Nether column is unknown");
        parity(empty, SURFACE, "empty Nether live/prepared parity");
    }

    private static void overworldAndEndKeepHighestSurface() throws Exception {
        Fixture overworld = new Fixture(Level.OVERWORLD, false, -64, 384, 384);
        overworld.set(74, Blocks.DIRT.defaultBlockState());
        overworld.set(90, Blocks.STONE.defaultBlockState());
        sample(Blocks.STONE.defaultBlockState(), 90,
                overworld.sampler.sampleSurface(overworld.level, overworld.x, overworld.z), "Overworld highest surface");
        parity(overworld, SURFACE, "Overworld live/prepared parity");

        Fixture end = new Fixture(Level.END, false, 0, 256, 256);
        end.set(40, Blocks.OBSIDIAN.defaultBlockState());
        sample(Blocks.OBSIDIAN.defaultBlockState(), 40,
                end.sampler.sampleSurface(end.level, end.x, end.z), "End highest surface");
        parity(end, SURFACE, "End live/prepared parity");
    }

    private static void caveSamplingIsUnchanged() throws Exception {
        Fixture fixture = new Fixture(Level.NETHER, true, 0, 256, 128);
        fixture.fill(123, 127, Blocks.BEDROCK.defaultBlockState());
        fixture.set(64, Blocks.NETHERRACK.defaultBlockState());
        ColumnSampler.Sample live = fixture.sampler.sampleCave(fixture.level, fixture.x, fixture.z, 65);
        sample(Blocks.NETHERRACK.defaultBlockState(), 64, live, "Nether cave floor remains sampled");
        parity(fixture, 65, "Nether cave live/prepared parity");
    }

    private static void deepCaveFindsFloorBelowSixtyFourBlocks() throws Exception {
        Fixture fixture = new Fixture(Level.OVERWORLD, false, 0, 256, 256);
        fixture.fill(1, 199, Blocks.AIR.defaultBlockState());
        fixture.set(0, Blocks.STONE.defaultBlockState());
        fixture.set(200, Blocks.BEDROCK.defaultBlockState());
        sample(Blocks.STONE.defaultBlockState(), 0,
                fixture.sampler.sampleCave(fixture.level, fixture.x, fixture.z, 128),
                "cave floor more than 64 blocks below its air band remains visible");
        parity(fixture, 128, "deep cave floor live/prepared parity");
    }

    private static void floorlessCaveRemainsUnknown() throws Exception {
        Fixture fixture = new Fixture(Level.OVERWORLD, false, 0, 256, 256);
        fixture.fill(1, 199, Blocks.AIR.defaultBlockState());
        fixture.set(200, Blocks.BEDROCK.defaultBlockState());
        unknown(fixture.sampler.sampleCave(fixture.level, fixture.x, fixture.z, 128),
                "cave with no solid floor to minimum height remains unknown");
        parity(fixture, 128, "floorless deep cave live/prepared parity");
    }

    private static void nullAndNoneCaveColorsUseStoneFallback() throws Exception {
        BlockState nullColor = new BlockState("null-map cave block", null, false, false, true);
        BlockState noneColor = new BlockState("none-map cave block", MapColor.NONE, false, false, true);

        Fixture nullWall = new Fixture(Level.OVERWORLD, false, 0, 256, 256);
        nullWall.fill(0, 199, Blocks.BEDROCK.defaultBlockState());
        nullWall.set(128, nullColor);
        sample(Blocks.STONE.defaultBlockState(), 128,
                nullWall.sampler.sampleCave(nullWall.level, nullWall.x, nullWall.z, 128),
                "null-map cave wall uses stone color");
        parity(nullWall, 128, "null-map cave wall live/prepared parity");

        Fixture noneWall = new Fixture(Level.OVERWORLD, false, 0, 256, 256);
        noneWall.fill(0, 199, Blocks.BEDROCK.defaultBlockState());
        noneWall.set(128, noneColor);
        sample(Blocks.STONE.defaultBlockState(), 128,
                noneWall.sampler.sampleCave(noneWall.level, noneWall.x, noneWall.z, 128),
                "NONE-map cave wall uses stone color");
        parity(noneWall, 128, "NONE-map cave wall live/prepared parity");

        Fixture nullFloor = floorFixture(nullColor);
        sample(Blocks.STONE.defaultBlockState(), 0,
                nullFloor.sampler.sampleCave(nullFloor.level, nullFloor.x, nullFloor.z, 128),
                "null-map cave floor uses stone color");
        parity(nullFloor, 128, "null-map cave floor live/prepared parity");

        Fixture noneFloor = floorFixture(noneColor);
        sample(Blocks.STONE.defaultBlockState(), 0,
                noneFloor.sampler.sampleCave(noneFloor.level, noneFloor.x, noneFloor.z, 128),
                "NONE-map cave floor uses stone color");
        parity(noneFloor, 128, "NONE-map cave floor live/prepared parity");
    }

    private static void nullAndNoneCaveFloorsAtOrdinaryHeightUseStone() throws Exception {
        BlockState nullColor = new BlockState("null-map cave floor", null, false, false, true);
        BlockState noneColor = new BlockState("none-map cave floor", MapColor.NONE, false, false, true);

        Fixture nullFloor = ordinaryHeightFloorFixture(nullColor);
        sample(Blocks.STONE.defaultBlockState(), 20,
                nullFloor.sampler.sampleCave(nullFloor.level, nullFloor.x, nullFloor.z, 40),
                "null-map cave floor at ordinary Y uses stone without descending through it");
        parity(nullFloor, 40, "null-map ordinary cave floor live/prepared parity");

        Fixture noneFloor = ordinaryHeightFloorFixture(noneColor);
        sample(Blocks.STONE.defaultBlockState(), 20,
                noneFloor.sampler.sampleCave(noneFloor.level, noneFloor.x, noneFloor.z, 40),
                "NONE-map cave floor at ordinary Y uses stone without descending through it");
        parity(noneFloor, 40, "NONE-map ordinary cave floor live/prepared parity");
    }

    private static Fixture ordinaryHeightFloorFixture(BlockState floor) {
        Fixture fixture = new Fixture(Level.OVERWORLD, false, 0, 256, 256);
        fixture.fill(21, 99, Blocks.AIR.defaultBlockState());
        fixture.set(20, floor);
        fixture.set(100, Blocks.BEDROCK.defaultBlockState());
        return fixture;
    }

    private static Fixture floorFixture(BlockState floor) {
        Fixture fixture = new Fixture(Level.OVERWORLD, false, 0, 256, 256);
        fixture.fill(1, 199, Blocks.AIR.defaultBlockState());
        fixture.set(0, floor);
        fixture.set(200, Blocks.BEDROCK.defaultBlockState());
        return fixture;
    }

    private static void grassTintUsesNativeTextureChannelOrder() throws Exception {
        Fixture fixture = new Fixture(Level.OVERWORLD, false, 0, 128, 128);
        fixture.set(32, Blocks.GRASS_BLOCK.defaultBlockState());
        ColumnSampler.Sample live = fixture.sampler.sampleSurface(fixture.level, fixture.x, fixture.z);
        int expectedGrass = Boolean.getBoolean("sampler.test.argb") ? 0xFF49922C : 0xFF2C9249;
        equal(expectedGrass, live.grassColor(), "grass tint uses version-native texture channel order");
        parity(fixture, SURFACE, "grass tint live/prepared parity");
    }

    private static void blockOverrideStillApplies() throws Exception {
        Fixture fixture = new Fixture(Level.OVERWORLD, false, 0, 128, 128);
        BlockState floor = Blocks.NETHERRACK.defaultBlockState();
        fixture.set(24, floor);
        MapColor override = MapColor.TEST_OVERRIDE;
        BlockOverrideConfig.getResolvedOverrides().clear();
        BlockOverrideConfig.getResolvedOverrides().put(floor, override);
        int expected = 0xFF000000 | override.rgb;
        equal(expected, fixture.sampler.sampleSurface(fixture.level, fixture.x, fixture.z).color(),
                "block override color applies to live sample");
        parity(fixture, SURFACE, "block override live/prepared parity");
        BlockOverrideConfig.getResolvedOverrides().clear();
    }

    private static void snapshotStaysDetachedAfterLiveEdit() throws Exception {
        Fixture fixture = new Fixture(Level.NETHER, true, 0, 256, 128);
        fixture.fill(123, 127, Blocks.BEDROCK.defaultBlockState());
        fixture.set(64, Blocks.NETHERRACK.defaultBlockState());
        ChunkMapSnapshot snapshot = new ChunkMapSnapshot(fixture.chunk);
        ChunkColumnScan.Prepared prepared = ChunkColumnScan.prepare(snapshot, SURFACE);

        fixture.set(64, Blocks.SOUL_SOIL.defaultBlockState());
        ColumnSampler.Sample changedLive = fixture.sampler.sampleSurface(fixture.level, fixture.x, fixture.z);
        sample(Blocks.SOUL_SOIL.defaultBlockState(), 64, changedLive, "live edit changes live Nether sample");
        ColumnSampler.Sample detached = fixture.sampler.finish(fixture.level, snapshot, prepared, fixture.x, fixture.z, SURFACE);
        sample(Blocks.NETHERRACK.defaultBlockState(), 64, detached, "prepared snapshot retains captured Nether floor");
    }

    private static void parity(Fixture fixture, int layer, String label) throws Exception {
        parity(fixture, fixture.x, fixture.z, layer, label);
    }

    private static void parity(Fixture fixture, int worldX, int worldZ, int layer, String label) throws Exception {
        ChunkMapSnapshot snapshot = new ChunkMapSnapshot(fixture.chunk);
        ChunkColumnScan.Prepared prepared = ChunkColumnScan.prepare(snapshot, layer);
        ColumnSampler.Sample live = layer == SURFACE
                ? fixture.sampler.sampleSurface(fixture.level, worldX, worldZ)
                : fixture.sampler.sampleCave(fixture.level, worldX, worldZ, layer);
        ColumnSampler.Sample finished = fixture.sampler.finish(fixture.level, snapshot, prepared,
                worldX & 15, worldZ & 15, layer);
        equal(live.color(), finished.color(), label + " color");
        equal(live.height(), finished.height(), label + " height");
        equal(live.grassColor(), finished.grassColor(), label + " grass color");
    }

    private static void sample(BlockState state, int height, ColumnSampler.Sample sample, String message) {
        equal(0xFF000000 | state.mapColor().rgb, sample.color(), message + " color");
        equal(height, sample.height(), message + " height");
    }

    private static void unknown(ColumnSampler.Sample sample, String message) {
        equal(0, sample.color(), message + " color");
        equal(Tile.UNKNOWN_HEIGHT, sample.height(), message + " height");
    }

    private static void equal(int expected, int actual, String message) {
        assertions++;
        if (expected != actual) failures.add(message + ": expected " + hexOrDecimal(expected) + ", got " + hexOrDecimal(actual));
    }

    private static String hexOrDecimal(int value) {
        return value < 0 && value != Tile.UNKNOWN_HEIGHT ? String.format("0x%08X", value) : Integer.toString(value);
    }

    private static final class Fixture {
        final int x = 5, z = 9;
        final Level level;
        final LevelChunk chunk;
        final ColumnSampler sampler = new ColumnSampler();

        Fixture(Object dimension, boolean ceiling, int minY, int height, int logicalHeight) {
            level = new Level(dimension, ceiling, logicalHeight);
            chunk = new LevelChunk(level, minY, height, 0, 0);
            level.setChunk(chunk);
        }

        void set(int y, BlockState state) { setAt(y, state); }
        void setAt(int y, BlockState state) { chunk.setBlockState(x, y, z, state); }
        void setAt(int x, int y, int z, BlockState state) { chunk.setBlockState(x, y, z, state); }
        void fill(int fromY, int toY, BlockState state) {
            for (int y = fromY; y <= toY; y++) setAt(y, state);
        }
    }
}
