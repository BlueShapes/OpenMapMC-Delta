// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.world;

import java.util.ArrayList;
import java.util.List;
import net.aosankaku.openmapdelta.config.MinimapLayerMode;
import net.aosankaku.openmapdelta.config.OpenMapDeltaConfig;
import net.aosankaku.openmapdelta.network.ClientServerState;
import net.aosankaku.openmapdelta.render.MinimapTexture;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;

/** Exercises the real client layer lifecycle with lightweight world/cache collaborators. */
public final class MinimapLayerClientTest {
    private static final List<String> failures = new ArrayList<>();
    private static int assertions;
    private static Minecraft minecraft;

    public static void main(String[] args) {
        minecraft = new Minecraft();
        Minecraft.setInstance(minecraft);
        OpenMapDeltaClient.init();

        initializationCreatesFixedCacheOwners();
        independentBandsAndEqualBandDeduplication();
        automaticCrossingAndOwnerSwap();
        manualSelectionTracksDimensionBounds();
        autoSelectionUsesLoadedColumnGeometry();
        skyTransitionsPreserveOwnershipAndFullscreenSelection();
        manualAndCeilingDimensionsKeepTheirPolicies();
        unavailableHeightmapsNeverForceChunkLoads();
        serverPolicyIsImmediateAndPreservesSetting();
        logoutAndExplicitResetClearContexts();
        repeatedCrossingsDoNotAllocateAndInvalidationReachesBoth();

        if (!failures.isEmpty()) {
            System.err.println("Minimap layer client checks failed: " + failures.size() + "/" + assertions);
            for (int i = 0; i < Math.min(12, failures.size()); i++) System.err.println("  - " + failures.get(i));
            throw new AssertionError("minimap layer client checks failed");
        }
        System.out.println("Minimap layer client checks passed (" + assertions + " assertions).");
    }

    private static void initializationCreatesFixedCacheOwners() {
        equal(3, TileCache.INSTANCES.size(), "one surface cache and two cave caches created at init");
        equal(1, TileCache.siblingCount, "second cave cache is a single sibling");
        equal(3, WorldScanner.INSTANCES.size(), "one scanner per cache is created at init");
        equal(1, MinimapTexture.INSTANCES.size(), "one minimap texture is created at init");
        same(OpenMapDeltaClient.getTileCache(), MinimapTexture.INSTANCES.get(0).constructorCache,
                "texture initialization keeps the surface cache as its constructor dependency");
    }

    private static void independentBandsAndEqualBandDeduplication() {
        openWorld(-64, 384, 16, MinimapLayerMode.MANUAL, 80, true, 16);
        equal(16, OpenMapDeltaClient.getSelectedMapLayer(), "fullscreen manual band");
        equal(80, OpenMapDeltaClient.getSelectedMinimapLayer(), "minimap manual band is independent");
        TileCache fullscreen = OpenMapDeltaClient.getActiveTileCache();
        TileCache minimap = OpenMapDeltaClient.getMinimapTileCache();
        check(fullscreen != minimap, "distinct bands use distinct cache owners");
        equal(16, fullscreen.getLayer(), "fullscreen cache owner stays on its layer");
        equal(80, minimap.getLayer(), "minimap cache owner stays on its layer");

        int before = caveScannerTicks();
        OpenMapDeltaConfig.MINIMAP_CAVE_Y.set(16);
        OpenMapDeltaClient.tick(minecraft);
        equal(16, OpenMapDeltaClient.getSelectedMinimapLayer(), "equal requested bands resolve identically");
        same(OpenMapDeltaClient.getActiveTileCache(), OpenMapDeltaClient.getMinimapTileCache(),
                "equal bands share one cache owner");
        equal(1, caveScannerTicks() - before, "equal bands scan the cave context once per tick");
    }

    private static void automaticCrossingAndOwnerSwap() {
        openWorld(-64, 384, 16, MinimapLayerMode.PLAYER_Y, 0, true, 16);
        equal(16, OpenMapDeltaClient.getSelectedMinimapLayer(), "player-y mode starts on the player's band");
        TileCache fullscreenOwner = OpenMapDeltaClient.getActiveTileCache();
        TileCache originalMinimapOwner = OpenMapDeltaClient.getMinimapTileCache();
        check(fullscreenOwner == originalMinimapOwner, "same player/fullscreen band shares an owner");

        minecraft.player.setY(41.0);
        OpenMapDeltaClient.tick(minecraft);
        equal(16, OpenMapDeltaClient.getSelectedMapLayer(), "automatic minimap crossing leaves fullscreen selection");
        equal(40, OpenMapDeltaClient.getSelectedMinimapLayer(), "automatic minimap crossing enters the next band");
        same(fullscreenOwner, OpenMapDeltaClient.getActiveTileCache(), "fullscreen cache owner survives minimap crossing");
        check(OpenMapDeltaClient.getMinimapTileCache() != fullscreenOwner, "crossed minimap band gets its own cache owner");
        originalMinimapOwner = OpenMapDeltaClient.getMinimapTileCache();

        OpenMapDeltaConfig.MINIMAP_LAYER_MODE.set(MinimapLayerMode.MANUAL);
        OpenMapDeltaConfig.MINIMAP_CAVE_Y.set(16);
        OpenMapDeltaClient.setSelectedMapLayer(40);
        OpenMapDeltaClient.tick(minecraft);
        equal(40, OpenMapDeltaClient.getSelectedMapLayer(), "fullscreen selection can move to the old minimap band");
        equal(16, OpenMapDeltaClient.getSelectedMinimapLayer(), "minimap can select the old fullscreen band");
        same(originalMinimapOwner, OpenMapDeltaClient.getActiveTileCache(), "swapped fullscreen band keeps its cache owner");
        same(fullscreenOwner, OpenMapDeltaClient.getMinimapTileCache(), "swapped minimap band keeps its cache owner");
        equal(3, TileCache.INSTANCES.size(), "swapping bands does not create a cache");
    }

    private static void manualSelectionTracksDimensionBounds() {
        openWorld(-64, 384, 20, MinimapLayerMode.MANUAL, 50_000, true, 50_000);
        equal(312, OpenMapDeltaClient.getSelectedMinimapLayer(), "manual minimap Y clamps to the last partial band");
        equal(312, OpenMapDeltaClient.getSelectedMapLayer(), "fullscreen Y clamps to the last partial band");

        minecraft.level = new Level(0, 128);
        minecraft.player.setY(32);
        OpenMapDeltaConfig.MINIMAP_CAVE_Y.set(-50_000);
        OpenMapDeltaClient.tick(minecraft);
        equal(0, OpenMapDeltaClient.getSelectedMinimapLayer(), "manual minimap Y follows the new dimension minimum");
        equal(120, OpenMapDeltaClient.getSelectedMapLayer(), "fullscreen Y requantizes to new dimension maximum");
    }

    private static void autoSelectionUsesLoadedColumnGeometry() {
        openWorld(-64, 384, 65, MinimapLayerMode.PLAYER_Y, 24, true, OpenMapDeltaClient.SURFACE_LAYER);
        Level level = minecraft.level;
        level.getChunkSource().queries = 0;
        minecraft.player.setEyeY(66.5);
        level.setSkyBrightness(15);
        LevelChunk chunk = loadedColumn(level, new LevelChunk(65, true));
        OpenMapDeltaClient.tick(minecraft);
        equal(OpenMapDeltaClient.SURFACE_LAYER, OpenMapDeltaClient.getSelectedMinimapLayer(),
                "open column selects surface even at night with sky brightness 15");
        equal(1, level.getChunkSource().queries, "auto mode checks the player's current loaded column");
        equal(0, level.getChunkSource().lastChunkX, "column lookup uses the player's chunk X");
        equal(-2, level.getChunkSource().lastChunkZ, "column lookup uses the player's chunk Z");
        equal(false, level.getChunkSource().lastGenerate, "column lookup never requests chunk generation");
        equal("FULL", level.getChunkSource().lastStatus.toString(), "column lookup accepts only full chunks");
        equal(1, chunk.heightReads, "open-column decision reads the primed world-surface heightmap");
        equal(12, chunk.lastHeightX, "heightmap lookup uses the player's local chunk X");
        equal(12, chunk.lastHeightZ, "heightmap lookup uses the player's local chunk Z");

        chunk.setWorldSurfaceY(66);
        OpenMapDeltaClient.tick(minecraft);
        equal(64, OpenMapDeltaClient.getSelectedMinimapLayer(),
                "a non-air block reaching the eye block obstructs surface selection");
        equal(15, level.getSkyBrightness(), "sky brightness remains 15 beneath a roof");

        chunk.setWorldSurfaceY(67);
        OpenMapDeltaClient.tick(minecraft);
        equal(64, OpenMapDeltaClient.getSelectedMinimapLayer(),
                "a higher glass or solid roof remains a cave even when sky brightness is 15");
    }

    private static void skyTransitionsPreserveOwnershipAndFullscreenSelection() {
        openWorld(-64, 384, 65, MinimapLayerMode.PLAYER_Y, 0, true, 24);
        Level level = minecraft.level;
        LevelChunk chunk = loadedColumn(level, new LevelChunk(65, true));
        OpenMapDeltaClient.tick(minecraft);
        same(OpenMapDeltaClient.getTileCache(), OpenMapDeltaClient.getMinimapTileCache(),
                "open sky routes minimap to surface cache");
        equal(24, OpenMapDeltaClient.getSelectedMapLayer(), "auto sky selection leaves fullscreen layer independent");

        chunk.setWorldSurfaceY(80);
        OpenMapDeltaClient.tick(minecraft);
        equal(64, OpenMapDeltaClient.getSelectedMinimapLayer(), "roof appearing changes auto mode to cave on the next tick");
        equal(24, OpenMapDeltaClient.getSelectedMapLayer(), "roof change does not move fullscreen layer");
        TileCache caveOwner = OpenMapDeltaClient.getMinimapTileCache();
        check(caveOwner != OpenMapDeltaClient.getTileCache(), "roofed minimap uses a cave cache owner");

        chunk.setWorldSurfaceY(65);
        OpenMapDeltaClient.tick(minecraft);
        equal(OpenMapDeltaClient.SURFACE_LAYER, OpenMapDeltaClient.getSelectedMinimapLayer(),
                "removing the roof returns auto mode to surface on the next tick");
        same(OpenMapDeltaClient.getTileCache(), OpenMapDeltaClient.getMinimapTileCache(),
                "leaving cave returns to the shared surface cache");

        chunk.setWorldSurfaceY(80);
        OpenMapDeltaClient.tick(minecraft);
        same(caveOwner, OpenMapDeltaClient.getMinimapTileCache(),
                "re-entering the same cave band preserves its existing cache owner");
        equal(3, TileCache.INSTANCES.size(), "sky transitions create no cache instances");
    }

    private static void manualAndCeilingDimensionsKeepTheirPolicies() {
        openWorld(-64, 384, 65, MinimapLayerMode.MANUAL, 24, true, OpenMapDeltaClient.SURFACE_LAYER);
        Level level = minecraft.level;
        loadedColumn(level, new LevelChunk(65, true));
        OpenMapDeltaClient.tick(minecraft);
        equal(24, OpenMapDeltaClient.getSelectedMinimapLayer(), "manual mode stays on its configured cave band outdoors");
        equal(0, level.getChunkSource().queries, "manual mode does not inspect sky exposure");

        openWorld(0, 128, 65, MinimapLayerMode.PLAYER_Y, 0, true, OpenMapDeltaClient.SURFACE_LAYER);
        level = minecraft.level;
        level.getChunkSource().queries = 0;
        level.setHasCeiling(true);
        loadedColumn(level, new LevelChunk(5, true));
        OpenMapDeltaClient.tick(minecraft);
        equal(64, OpenMapDeltaClient.getSelectedMinimapLayer(), "ceiling dimensions keep auto mode on the player band");
        equal(0, level.getChunkSource().queries, "ceiling dimensions skip sky exposure lookup");
    }

    private static void unavailableHeightmapsNeverForceChunkLoads() {
        openWorld(-64, 384, 65, MinimapLayerMode.PLAYER_Y, 0, true, OpenMapDeltaClient.SURFACE_LAYER);
        Level level = minecraft.level;
        level.getChunkSource().setLoadedChunk(0, -2, null);
        OpenMapDeltaClient.tick(minecraft);
        equal(64, OpenMapDeltaClient.getSelectedMinimapLayer(), "unreceived current chunk is not treated as open sky");
        equal(false, level.getChunkSource().lastGenerate, "missing chunk lookup never requests a load");

        LevelChunk unprimed = loadedColumn(level, new LevelChunk(5, false));
        OpenMapDeltaClient.tick(minecraft);
        equal(64, OpenMapDeltaClient.getSelectedMinimapLayer(), "unprimed surface heightmap is not treated as open sky");
        equal(0, unprimed.heightReads, "unprimed heightmap is never read");
    }

    private static void serverPolicyIsImmediateAndPreservesSetting() {
        openWorld(-64, 384, 24, MinimapLayerMode.MANUAL, 80, true, 16);
        check(OpenMapDeltaClient.getSelectedMinimapLayer() != OpenMapDeltaClient.SURFACE_LAYER,
                "manual minimap cave band is active before server policy changes");

        ClientServerState.allowed = false;
        equal(OpenMapDeltaClient.SURFACE_LAYER, OpenMapDeltaClient.getSelectedMinimapLayer(),
                "server policy forces the minimap getter to surface immediately");
        same(OpenMapDeltaClient.getTileCache(), OpenMapDeltaClient.getMinimapTileCache(),
                "server policy routes the minimap to the surface cache immediately");
        equal(MinimapLayerMode.MANUAL, OpenMapDeltaConfig.MINIMAP_LAYER_MODE.get(),
                "server policy does not overwrite the user's saved mode");
        OpenMapDeltaClient.tick(minecraft);
        equal(OpenMapDeltaClient.SURFACE_LAYER, OpenMapDeltaClient.getSelectedMapLayer(),
                "server policy also hides fullscreen cave selection");
        ClientServerState.allowed = true;
    }

    private static void logoutAndExplicitResetClearContexts() {
        openWorld(-64, 384, 16, MinimapLayerMode.PLAYER_Y, 0, true, 16);
        minecraft.player.setY(80);
        OpenMapDeltaClient.tick(minecraft);
        TileCache surface = OpenMapDeltaClient.getTileCache();
        TileCache fullscreen = OpenMapDeltaClient.getActiveTileCache();
        TileCache minimap = OpenMapDeltaClient.getMinimapTileCache();
        MinimapTexture texture = OpenMapDeltaClient.getMinimapTexture();
        int surfaceClears = surface.clearCount, fullClears = fullscreen.clearCount, miniClears = minimap.clearCount;
        int textureClears = texture.clearCount, scannerResets = scannerResets();

        minecraft.level = null;
        OpenMapDeltaClient.tick(minecraft);
        equal(OpenMapDeltaClient.SURFACE_LAYER, OpenMapDeltaClient.getSelectedMapLayer(), "logout resets fullscreen layer");
        equal(OpenMapDeltaClient.SURFACE_LAYER, OpenMapDeltaClient.getSelectedMinimapLayer(), "logout resets minimap layer");
        same(surface, OpenMapDeltaClient.getMinimapTileCache(), "logout returns minimap to surface cache");
        check(surface.clearCount > surfaceClears && fullscreen.clearCount > fullClears && minimap.clearCount > miniClears,
                "logout clears surface and active cave contexts");
        check(texture.clearCount > textureClears, "logout clears the minimap texture");
        check(scannerResets() > scannerResets, "logout resets scanner contexts");

        openWorld(0, 256, 32, MinimapLayerMode.MANUAL, 80, true, 16);
        int cacheClears = totalCacheClears(), resetsBefore = scannerResets();
        OpenMapDeltaClient.resetSession();
        equal(OpenMapDeltaClient.SURFACE_LAYER, OpenMapDeltaClient.getSelectedMapLayer(), "explicit reset clears fullscreen layer");
        equal(OpenMapDeltaClient.SURFACE_LAYER, OpenMapDeltaClient.getSelectedMinimapLayer(), "explicit reset clears minimap layer");
        check(totalCacheClears() > cacheClears, "explicit reset clears active cache contexts");
        check(scannerResets() > resetsBefore, "explicit reset cancels scanner contexts");
    }

    private static void repeatedCrossingsDoNotAllocateAndInvalidationReachesBoth() {
        openWorld(-64, 384, 0, MinimapLayerMode.PLAYER_Y, 0, true, -32);
        int cacheCount = TileCache.INSTANCES.size();
        int scannerCount = WorldScanner.INSTANCES.size();
        int siblings = TileCache.siblingCount;
        for (int i = 0; i < 100; i++) {
            minecraft.player.setY((i & 1) == 0 ? 200 : -63);
            OpenMapDeltaClient.tick(minecraft);
        }
        equal(cacheCount, TileCache.INSTANCES.size(), "repeated band crossings allocate no new caches");
        equal(scannerCount, WorldScanner.INSTANCES.size(), "repeated band crossings allocate no new scanners");
        equal(siblings, TileCache.siblingCount, "repeated band crossings create no cache siblings");

        TileCache surface = OpenMapDeltaClient.getTileCache();
        TileCache fullscreen = OpenMapDeltaClient.getActiveTileCache();
        TileCache minimap = OpenMapDeltaClient.getMinimapTileCache();
        check(fullscreen != minimap, "invalidation scenario has two active cave bands");
        int surfaceInvalidations = surface.invalidateCount;
        int fullInvalidations = fullscreen.invalidateCount;
        int miniInvalidations = minimap.invalidateCount;
        OpenMapDeltaClient.invalidateArea(minecraft.level, 123, -456);
        equal(surfaceInvalidations + 1, surface.invalidateCount, "terrain change invalidates surface context");
        equal(fullInvalidations + 1, fullscreen.invalidateCount, "terrain change invalidates fullscreen cave context");
        equal(miniInvalidations + 1, minimap.invalidateCount, "terrain change invalidates minimap cave context");
    }

    private static void openWorld(int minY, int height, double playerY, MinimapLayerMode mode,
                                  int manualMinimapY, boolean allowed, int fullscreenY) {
        OpenMapDeltaClient.resetSession();
        minecraft.level = new Level(minY, height);
        minecraft.player = new Minecraft.Player(playerY);
        ClientServerState.allowed = allowed;
        OpenMapDeltaConfig.MINIMAP_LAYER_MODE.set(mode);
        OpenMapDeltaConfig.MINIMAP_CAVE_Y.set(manualMinimapY);
        OpenMapDeltaConfig.CAVE_LAYER_STEP.set(8);
        if (fullscreenY != OpenMapDeltaClient.SURFACE_LAYER) OpenMapDeltaClient.setSelectedMapLayer(fullscreenY);
        OpenMapDeltaClient.tick(minecraft);
    }

    private static LevelChunk loadedColumn(Level level, LevelChunk chunk) {
        level.getChunkSource().setLoadedChunk(0, -2, chunk);
        return chunk;
    }

    private static int caveScannerTicks() {
        int count = 0;
        for (int i = 1; i < WorldScanner.INSTANCES.size(); i++) count += WorldScanner.INSTANCES.get(i).tickCount;
        return count;
    }

    private static int scannerResets() {
        int count = 0;
        for (WorldScanner scanner : WorldScanner.INSTANCES) count += scanner.resetCount;
        return count;
    }

    private static int totalCacheClears() {
        int count = 0;
        for (TileCache cache : TileCache.INSTANCES) count += cache.clearCount;
        return count;
    }

    private static void same(Object expected, Object actual, String message) {
        assertions++;
        if (expected != actual) failures.add(message + ": object identity differs");
    }

    private static void equal(Object expected, Object actual, String message) {
        assertions++;
        if (!expected.equals(actual)) failures.add(message + ": expected " + expected + ", got " + actual);
    }

    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) failures.add(message);
    }
}
