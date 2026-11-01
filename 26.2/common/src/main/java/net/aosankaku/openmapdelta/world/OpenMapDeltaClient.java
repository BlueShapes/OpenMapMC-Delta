// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.world;

import net.aosankaku.openmapdelta.config.BlockOverrideConfig;
import net.aosankaku.openmapdelta.config.MinimapLayerMode;
import net.aosankaku.openmapdelta.config.OpenMapDeltaConfig;
import net.aosankaku.openmapdelta.mixin.BiomeManagerAccessor;
import net.aosankaku.openmapdelta.network.ClientServerState;
import net.aosankaku.openmapdelta.network.ClientWorldIdentityState;
import net.aosankaku.openmapdelta.marker.MarkerManager;
import net.aosankaku.openmapdelta.marker.MarkerStorage;
import net.aosankaku.openmapdelta.render.FullscreenMapRenderer;
import net.aosankaku.openmapdelta.render.MinimapTexture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;

import java.nio.file.Path;

public final class OpenMapDeltaClient {
    public static final int SURFACE_LAYER = Integer.MIN_VALUE;
    private static int selectedMapLayer = SURFACE_LAYER;
    private static int selectedCaveStep = 8;
    private static int selectedMinimapLayer = SURFACE_LAYER;
    private static final CaveContext[] caveContexts = new CaveContext[2];
    private static TileCache tileCache;
    private static WorldScanner scanner;
    private static MinimapTexture minimapTexture;
    private static ColumnSampler columnSampler;
    private static Object unknownWorldLevel;
    private static String unknownSessionKey;
    private static long unknownSessionCounter;
    private static boolean sessionActive;

    private static final class CaveContext {
        final TileCache cache;
        final WorldScanner scanner;
        int layer = SURFACE_LAYER;
        boolean active;
        CaveContext(TileCache cache) { this.cache = cache; scanner = new WorldScanner(cache); }
    }

    private OpenMapDeltaClient() {}

    public static void init() {
        columnSampler = new ColumnSampler();
        tileCache = new TileCache(columnSampler);
        scanner = new WorldScanner(tileCache);
        caveContexts[0] = new CaveContext(new TileCache(columnSampler, 0));
        caveContexts[1] = new CaveContext(caveContexts[0].cache.createSibling(0));
        minimapTexture = new MinimapTexture(tileCache);
        BlockOverrideConfig.reload();
        MarkerStorage.load();
    }

    /** Called before scanning/rendering work; disk results are polled every client tick. */
    public static boolean tick(Minecraft mc) {
        if (tileCache == null) return false;
        if (mc.level == null) {
            boolean ended = sessionActive;
            if (ended) {
                tileCache.clear();
                clearCaveSelection();
                scanner.reset();
                minimapTexture.clear();
                sessionActive = false;
            }
            ChunkMapGeneration.close();
            unknownWorldLevel = null;
            unknownSessionKey = null;
            tileCache.tick();
            return ended;
        }

        // Requantize against new dimension bounds or a live layer-size setting change.
        if (selectedMapLayer != SURFACE_LAYER) setSelectedMapLayer(selectedMapLayer);
        MinimapLayerMode minimapLayerMode = OpenMapDeltaConfig.MINIMAP_LAYER_MODE.get();
        boolean caveAllowed = ClientServerState.isCaveAllowed();
        boolean openSky = minimapLayerMode == MinimapLayerMode.PLAYER_Y
                && caveAllowed && hasOpenSkyColumn(mc);
        selectedMinimapLayer = MinimapLayers.resolve(minimapLayerMode,
                caveAllowed, mc.player == null ? Double.NaN : mc.player.getY(),
                OpenMapDeltaConfig.MINIMAP_CAVE_Y.get(), mc.level.getMinY(),
                mc.level.getMinY() + mc.level.getHeight() - 1, OpenMapDeltaConfig.CAVE_LAYER_STEP.get(), openSky);
        reconcileCaveLayers();
        String persistentIdentity = persistentWorldIdentity(mc);
        String sessionKey;
        if (persistentIdentity != null) {
            sessionKey = persistentIdentity;
            unknownWorldLevel = null;
            unknownSessionKey = null;
        } else {
            if (unknownWorldLevel != mc.level || unknownSessionKey == null) {
                unknownWorldLevel = mc.level;
                unknownSessionKey = "memory-session:" + (++unknownSessionCounter);
            }
            sessionKey = unknownSessionKey;
        }

        boolean changed = tileCache.beginSession(sessionKey, persistentIdentity, mc.level.dimension());
        sessionActive = true;
        if (changed) {
            scanner.reset();
            minimapTexture.clear();
        }
        if (mc.player != null) {
            BlockPos pos = mc.player.blockPosition();
            tileCache.preloadNear(mc.level.dimension(), pos.getX(), pos.getZ());
        }
        tileCache.tick();
        scanner.applyCompleted(mc);
        for (CaveContext context : caveContexts) {
            if (!context.active) continue;
            if (context.cache.beginSession(sessionKey, persistentIdentity, mc.level.dimension())) context.scanner.reset();
            if (mc.player != null) {
                BlockPos pos = mc.player.blockPosition();
                context.cache.preloadNear(mc.level.dimension(), pos.getX(), pos.getZ());
            }
            context.cache.tick();
            context.scanner.applyCompleted(mc);
            context.scanner.tick(mc);
        }
        return changed;
    }

    public static void resetSession() {
        ChunkMapGeneration.close();
        if (tileCache != null) tileCache.clear();
        clearCaveSelection();
        if (scanner != null) scanner.reset();
        if (minimapTexture != null) minimapTexture.clear();
        FullscreenMapRenderer.reset();
        unknownWorldLevel = null;
        unknownSessionKey = null;
        sessionActive = false;
    }

    public static void reset() {
        resetSession();
        MarkerManager.get().clearSession();
    }

    public static String persistentWorldIdentity(Minecraft mc) {
        long hashedSeed = ((BiomeManagerAccessor) mc.level.getBiomeManager()).odysseymap$getBiomeZoomSeed();
        MinecraftServer integratedServer = mc.getSingleplayerServer();
        if (integratedServer != null) {
            Path root = integratedServer.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
            var worldId = ServerWorldIdentity.get(integratedServer).orElse(null);
            return worldId == null ? null : CacheWorldIdentity.singleplayer(root, hashedSeed, worldId);
        }
        ServerData server = mc.getCurrentServer();
        if (server == null && mc.getConnection() != null) server = mc.getConnection().getServerData();
        if (server == null) return null;
        var connection = mc.getConnection();
        Object connectionToken = connection == null ? null : connection.getConnection();
        var serverWorldId = connectionToken == null ? null : ClientWorldIdentityState.get(connectionToken).orElse(null);
        boolean identityRequired = ClientWorldIdentityState.requiresIdentity(connectionToken);
        return CacheWorldIdentity.multiplayer(server.ip, hashedSeed, serverWorldId, identityRequired);
    }

    /** Surface cache retained for compatibility with integrations. */
    public static TileCache getTileCache() { return tileCache; }
    public static TileCache getActiveTileCache() { return cacheForLayer(getSelectedMapLayer()); }
    public static TileCache getMinimapTileCache() { return cacheForLayer(getSelectedMinimapLayer()); }
    public static int getSelectedMinimapLayer() {
        return ClientServerState.isCaveAllowed() ? selectedMinimapLayer : SURFACE_LAYER;
    }
    private static TileCache cacheForLayer(int layer) {
        if (layer != SURFACE_LAYER) {
            for (CaveContext context : caveContexts) {
                if (context != null && context.active && context.layer == layer) return context.cache;
            }
        }
        return tileCache;
    }
    public static int getSelectedMapLayer() { return isCaveMapSelected() ? selectedMapLayer : SURFACE_LAYER; }
    public static boolean isCaveMapSelected() {
        return selectedMapLayer != SURFACE_LAYER && ClientServerState.isCaveAllowed();
    }
    public static void setSelectedMapLayer(int layer) {
        int step = Math.max(1, Math.min(64, OpenMapDeltaConfig.CAVE_LAYER_STEP.get()));
        Minecraft mc = Minecraft.getInstance();
        if (layer != SURFACE_LAYER) {
            layer = !ClientServerState.isCaveAllowed() || mc.level == null ? SURFACE_LAYER
                    : CaveLayerSteps.quantize(layer, mc.level.getMinY(), mc.level.getMinY() + mc.level.getHeight() - 1, step);
        }
        if (layer == selectedMapLayer && (layer == SURFACE_LAYER || selectedCaveStep == step)) return;
        selectedMapLayer = layer;
        selectedCaveStep = step;
        reconcileCaveLayers();
        FullscreenMapRenderer.invalidate();
    }
    private static void reconcileCaveLayers() {
        if (caveContexts[0] == null) return;
        int[] layers = MinimapLayers.assign(caveContexts[0].layer, caveContexts[1].layer,
                getSelectedMapLayer(), getSelectedMinimapLayer());
        for (int slot = 0; slot < caveContexts.length; slot++) {
            CaveContext context = caveContexts[slot];
            if (layers[slot] == SURFACE_LAYER) {
                if (context.active) { context.scanner.reset(); context.cache.clear(); }
                context.active = false;
            } else {
                if (context.layer != layers[slot]) {
                    context.scanner.reset();
                    context.cache.setLayer(layers[slot]);
                    context.layer = layers[slot];
                }
                context.active = true;
            }
        }
    }
    private static void clearCaveSelection() {
        selectedMapLayer = SURFACE_LAYER;
        selectedMinimapLayer = SURFACE_LAYER;
        reconcileCaveLayers();
        FullscreenMapRenderer.invalidate();
    }
    public static void invalidateArea(net.minecraft.world.level.Level level, int x, int z) {
        if (tileCache != null) tileCache.invalidateArea(level, x, z, x, z);
        for (CaveContext context : caveContexts) {
            if (context != null && context.active) context.cache.invalidateArea(level, x, z, x, z);
        }
    }
    public static WorldScanner getScanner() { return scanner; }
    public static MinimapTexture getMinimapTexture() { return minimapTexture; }
    public static ColumnSampler getColumnSampler() { return columnSampler; }

    /** Only a loaded, initialized column can prove geometric sky exposure; light levels are ignored. */
    private static boolean hasOpenSkyColumn(Minecraft mc) {
        if (mc.level == null || mc.player == null || mc.level.dimensionType().hasCeiling()) return false;
        BlockPos position = mc.player.blockPosition();
        var chunk = mc.level.getChunkSource().getChunk(position.getX() >> 4, position.getZ() >> 4,
                ChunkStatus.FULL, false);
        return chunk != null && chunk.hasPrimedHeightmap(Heightmap.Types.WORLD_SURFACE)
                && chunk.getHeight(Heightmap.Types.WORLD_SURFACE, position.getX() & 15, position.getZ() & 15)
                < Math.floor(mc.player.getEyeY());
    }
}
