// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.render;

import com.mojang.blaze3d.platform.NativeImage;
import net.aosankaku.openmapdelta.OpenMapDeltaCommon;
import net.aosankaku.openmapdelta.client.RuntimeClientState;
import net.aosankaku.openmapdelta.config.OpenMapDeltaConfig;
import net.aosankaku.openmapdelta.world.OpenMapDeltaClient;
import net.aosankaku.openmapdelta.world.Tile;
import net.aosankaku.openmapdelta.world.TileCache;
import net.aosankaku.openmapdelta.world.TerrainRelief;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

public class MinimapTexture {
    private static final Logger LOG = LoggerFactory.getLogger("OpenMapDelta");
    private static final Identifier TEXTURE_ID =
            Identifier.fromNamespaceAndPath(OpenMapDeltaCommon.MOD_ID, "minimap");
    private static final int MAX_FULLSCREEN_DIM = 512;

    private DynamicTexture dynamicTexture;
    private NativeImage image;
    private Identifier textureLocation;
    private TextureManager registeredTextureManager;
    private boolean textureRegistered;
    private int textureWidth;
    private int textureHeight;
    private boolean needsUpload = true;
    private boolean textureReady = false;
    private final MinimapLayerTransition layerTransition = new MinimapLayerTransition();
    private PhysicalCompositionKey lastPhysicalComposition;
    private PhysicalGeometryKey lastPhysicalGeometry;
    private boolean lastCompositionWasPhysical;

    public MinimapTexture(TileCache tileCache) {}

    public Identifier getTextureLocation() { return textureLocation; }
    public int getTextureWidth() { return textureWidth; }
    public int getTextureHeight() { return textureHeight; }
    public boolean isTextureReady() { return textureReady; }

    public void compose(Minecraft mc, int size) {
        LocalPlayer player = mc.player;
        if (player == null) return;
        compose(mc, size, size, player.getX(), player.getZ(), true, RuntimeClientState.getZoomBlocksPerPixel());
    }

    /** Composes the fixed logical minimap extent into a raster sized for its physical display area. */
    public void compose(Minecraft mc, int logicalSize, double physicalPixels) {
        LocalPlayer player = mc.player;
        if (player == null || logicalSize <= 0) return;
        int blocksPerPixel = Math.max(1, RuntimeClientState.getZoomBlocksPerPixel());
        int rasterSize = MinimapSizing.textureSize(logicalSize, blocksPerPixel, physicalPixels);
        if (rasterSize <= 0) return;

        int centerX = Mth.floor(player.getX());
        int centerZ = Mth.floor(player.getZ());
        long worldSpan = (long) logicalSize * blocksPerPixel;
        double sampleStep = worldSpan / (double) rasterSize;
        long originX = centerX - (long) (logicalSize / 2) * blocksPerPixel;
        long originZ = centerZ - (long) (logicalSize / 2) * blocksPerPixel;
        ClientLevel level = mc.level;
        if (level == null) return;

        PhysicalGeometryKey geometry = new PhysicalGeometryKey(
                logicalSize, blocksPerPixel, rasterSize, sampleStep);
        if (!lastCompositionWasPhysical || !geometry.equals(lastPhysicalGeometry)) {
            // Zoom and raster-density changes must not reuse a retained image from old sampling geometry.
            layerTransition.clear();
        }
        lastCompositionWasPhysical = true;
        lastPhysicalGeometry = geometry;

        boolean applyShapeMask = true;
        OpenMapDeltaConfig.MinimapShape shape = applyShapeMask ? OpenMapDeltaConfig.SHAPE.get() : null;
        var cache = OpenMapDeltaClient.getMinimapTileCache();
        var worldCache = OpenMapDeltaClient.getTileCache();
        int layer = OpenMapDeltaClient.getSelectedMinimapLayer();
        ensureTexture(rasterSize, rasterSize);
        if (applyShapeMask) MinimapShape.ensureMask(rasterSize);
        Map<Long, TerrainRelief> reliefViews = new HashMap<>();

        PhysicalCompositionKey current = new PhysicalCompositionKey(level, level.dimension(), cache,
                worldCache.getSamplingEpoch(), cache.getSamplingEpoch(), cache.getRevision(), layer,
                logicalSize, blocksPerPixel, rasterSize, originX, originZ, sampleStep, shape);
        if (textureReady && current.matches(lastPhysicalComposition) && !layerTransition.isRetaining()) return;

        layerTransition.begin(level, worldCache.getSamplingEpoch(), layer, rasterSize, rasterSize,
                originX, originZ, sampleStep, System.nanoTime());
        for (int py = 0; py < rasterSize; py++) {
            int wz = (int) Math.floor(originZ + py * sampleStep);
            for (int px = 0; px < rasterSize; px++) {
                int wx = (int) Math.floor(originX + px * sampleStep);
                int tileAlignedX = TileCache.alignTile(wx);
                int tileAlignedZ = TileCache.alignTile(wz);
                Tile buffer = cache.getOrCreate(level.dimension(), tileAlignedX, tileAlignedZ);
                int dataX = wx - tileAlignedX + 64;
                int dataZ = wz - tileAlignedZ + 64;
                int argb = 0;
                if (dataX >= 0 && dataZ >= 0 && dataX < Tile.SIZE && dataZ < Tile.SIZE) {
                    argb = reliefFor(reliefViews, cache, level, tileAlignedX, tileAlignedZ, buffer).getPixel(dataX, dataZ);
                }
                argb = layerTransition.pixel(px, py, argb, buffer.hasObservedPixel(dataX, dataZ));
                if (applyShapeMask) argb = MinimapShape.applyMask(argb, px, py);
                image.setPixel(px, py, argb);
                needsUpload = true;
            }
        }
        layerTransition.end();
        uploadPhysical(current);
    }

    public void compose(Minecraft mc, int texWidth, int texHeight, double centerWorldX, double centerWorldZ,
                        boolean applyShapeMask, int blocksPerPixel) {
        if (lastCompositionWasPhysical) layerTransition.clear();
        lastCompositionWasPhysical = false;
        lastPhysicalGeometry = null;
        lastPhysicalComposition = null;
        ClientLevel level = mc.level;
        if (level == null || texWidth <= 0 || texHeight <= 0) return;

        int w = Math.min(texWidth, MAX_FULLSCREEN_DIM);
        int h = Math.min(texHeight, MAX_FULLSCREEN_DIM);
        ensureTexture(w, h);
        if (applyShapeMask) MinimapShape.ensureMask(Math.min(w, h));

        int centerX = Mth.floor(centerWorldX);
        int centerZ = Mth.floor(centerWorldZ);
        int stride = Math.max(1, blocksPerPixel);
        int halfW = w / 2;
        int halfH = h / 2;

        var cache = OpenMapDeltaClient.getMinimapTileCache();
        Map<Long, TerrainRelief> reliefViews = new HashMap<>();
        layerTransition.begin(level, OpenMapDeltaClient.getTileCache().getSamplingEpoch(),
                OpenMapDeltaClient.getSelectedMinimapLayer(), w, h,
                centerX - (long) halfW * stride, centerZ - (long) halfH * stride, stride, System.nanoTime());

        for (int py = 0; py < h; py++) {
            for (int px = 0; px < w; px++) {
                int wx = centerX + (px - halfW) * stride;
                int wz = centerZ + (py - halfH) * stride;

                int tileAlignedX = TileCache.alignTile(wx);
                int tileAlignedZ = TileCache.alignTile(wz);
                Tile buffer = cache.getOrCreate(level.dimension(), tileAlignedX, tileAlignedZ);
                int dataX = wx - tileAlignedX + 64;
                int dataZ = wz - tileAlignedZ + 64;

                int argb = 0x00000000;
                if (dataX >= 0 && dataZ >= 0 && dataX < Tile.SIZE && dataZ < Tile.SIZE) {
                    argb = reliefFor(reliefViews, cache, level, tileAlignedX, tileAlignedZ, buffer).getPixel(dataX, dataZ);
                }
                argb = layerTransition.pixel(px, py, argb, buffer.hasObservedPixel(dataX, dataZ));
                if (applyShapeMask) argb = MinimapShape.applyMask(argb, px, py);
                image.setPixel(px, py, argb);
                needsUpload = true;
            }
        }

        layerTransition.end();
        if (needsUpload) {
            if (!textureRegistered) {
                textureReady = false;
                return;
            }
            try {
                dynamicTexture.upload();
                needsUpload = false;
                textureReady = true;
            } catch (Exception e) {
                LOG.warn("Failed to upload minimap texture", e);
                textureReady = false;
            }
        }
    }

    private static TerrainRelief reliefFor(Map<Long, TerrainRelief> views, TileCache cache, ClientLevel level,
                                           int alignedX, int alignedZ, Tile center) {
        long key = ((long) alignedX << Integer.SIZE) | (alignedZ & 0xFFFFFFFFL);
        return views.computeIfAbsent(key, ignored -> new TerrainRelief(center,
                getNeighbor(cache, level, alignedX, alignedZ, 0, -Tile.SIZE),
                getNeighbor(cache, level, alignedX, alignedZ, Tile.SIZE, 0),
                getNeighbor(cache, level, alignedX, alignedZ, 0, Tile.SIZE),
                getNeighbor(cache, level, alignedX, alignedZ, -Tile.SIZE, 0)));
    }

    private static Tile getNeighbor(TileCache cache, ClientLevel level, int alignedX, int alignedZ,
                                    int offsetX, int offsetZ) {
        long x = (long) alignedX + offsetX;
        long z = (long) alignedZ + offsetZ;
        if (x < Integer.MIN_VALUE || x > Integer.MAX_VALUE || z < Integer.MIN_VALUE || z > Integer.MAX_VALUE) {
            return null;
        }
        return cache.get(level.dimension(), (int) x, (int) z);
    }

    private void ensureTexture(int width, int height) {
        if (dynamicTexture != null && textureWidth == width && textureHeight == height) {
            if (!textureRegistered) registerTexture();
            return;
        }
        releaseTexture();
        textureWidth = width;
        textureHeight = height;
        image = new NativeImage(width, height, false);
        dynamicTexture = new DynamicTexture(() -> "odysseymap_minimap", image);
        registerTexture();
    }

    private void registerTexture() {
        if (dynamicTexture == null) return;
        registeredTextureManager = Minecraft.getInstance().getTextureManager();
        try {
            registeredTextureManager.register(TEXTURE_ID, dynamicTexture);
            textureLocation = TEXTURE_ID;
            textureRegistered = true;
        } catch (Exception e) {
            textureLocation = null;
            textureRegistered = false;
            LOG.warn("Failed to register minimap texture", e);
            textureReady = false;
        }
    }

    private void releaseTexture() {
        if (dynamicTexture != null) {
            if (textureRegistered && registeredTextureManager != null && textureLocation != null) {
                registeredTextureManager.release(textureLocation);
            } else {
                dynamicTexture.close();
            }
        }
        dynamicTexture = null;
        textureLocation = null;
        registeredTextureManager = null;
        textureRegistered = false;
    }

    private void uploadPhysical(PhysicalCompositionKey current) {
        if (!needsUpload) return;
        if (!textureRegistered) {
            textureReady = false;
            return;
        }
        try {
            dynamicTexture.upload();
            needsUpload = false;
            textureReady = true;
            lastPhysicalComposition = current;
        } catch (Exception e) {
            LOG.warn("Failed to upload minimap texture", e);
            textureReady = false;
        }
    }

    private static final class PhysicalGeometryKey {
        private final int logicalSize, blocksPerPixel, width;
        private final double sampleStep;

        private PhysicalGeometryKey(int logicalSize, int blocksPerPixel, int width, double sampleStep) {
            this.logicalSize = logicalSize;
            this.blocksPerPixel = blocksPerPixel;
            this.width = width;
            this.sampleStep = sampleStep;
        }

        @Override public boolean equals(Object other) {
            return other instanceof PhysicalGeometryKey key && logicalSize == key.logicalSize
                    && blocksPerPixel == key.blocksPerPixel && width == key.width
                    && Double.compare(sampleStep, key.sampleStep) == 0;
        }

        @Override public int hashCode() {
            return java.util.Objects.hash(logicalSize, blocksPerPixel, width, sampleStep);
        }
    }

    private static final class PhysicalCompositionKey {
        private final Object level, dimension, cache;
        private final long worldEpoch, cacheEpoch, revision, originX, originZ;
        private final int layer, logicalSize, blocksPerPixel, width;
        private final double sampleStep;
        private final OpenMapDeltaConfig.MinimapShape shape;

        private PhysicalCompositionKey(Object level, Object dimension, Object cache,
                                       long worldEpoch, long cacheEpoch, long revision, int layer,
                                       int logicalSize, int blocksPerPixel, int width,
                                       long originX, long originZ, double sampleStep,
                                       OpenMapDeltaConfig.MinimapShape shape) {
            this.level = level;
            this.dimension = dimension;
            this.cache = cache;
            this.worldEpoch = worldEpoch;
            this.cacheEpoch = cacheEpoch;
            this.revision = revision;
            this.layer = layer;
            this.logicalSize = logicalSize;
            this.blocksPerPixel = blocksPerPixel;
            this.width = width;
            this.originX = originX;
            this.originZ = originZ;
            this.sampleStep = sampleStep;
            this.shape = shape;
        }

        private boolean matches(PhysicalCompositionKey other) {
            return other != null && level == other.level && java.util.Objects.equals(dimension, other.dimension)
                    && cache == other.cache && worldEpoch == other.worldEpoch && cacheEpoch == other.cacheEpoch
                    && revision == other.revision && layer == other.layer && logicalSize == other.logicalSize
                    && blocksPerPixel == other.blocksPerPixel && width == other.width
                    && originX == other.originX && originZ == other.originZ
                    && Double.compare(sampleStep, other.sampleStep) == 0 && shape == other.shape;
        }
    }

    public void clear() {
        layerTransition.clear();
        lastPhysicalComposition = null;
        lastPhysicalGeometry = null;
        lastCompositionWasPhysical = false;
        releaseTexture();
        image = null;
        textureWidth = 0;
        textureHeight = 0;
        textureReady = false;
    }
}
