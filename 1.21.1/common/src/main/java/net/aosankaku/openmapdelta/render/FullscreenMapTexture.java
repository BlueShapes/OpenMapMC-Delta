// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.render;

import net.aosankaku.openmapdelta.OpenMapDeltaCommon;
import com.mojang.blaze3d.platform.NativeImage;
import net.aosankaku.openmapdelta.world.OpenMapDeltaClient;
import net.aosankaku.openmapdelta.world.TileCache;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Owns the cached terrain texture used only by the fullscreen map. */
public final class FullscreenMapTexture {
    private static final Logger LOG = LoggerFactory.getLogger("OpenMapDelta");
    private static final ResourceLocation TEXTURE_ID = ResourceLocation.fromNamespaceAndPath(OpenMapDeltaCommon.MOD_ID, "fullscreen_map");

    private DynamicTexture dynamicTexture;
    private NativeImage image;
    private ResourceLocation textureLocation;
    private int textureWidth;
    private int textureHeight;
    private boolean textureReady;
    private final FullscreenTerrainRaster raster = new FullscreenTerrainRaster();

    public boolean compose(Minecraft mc, ClientLevel level, FullscreenMapViewport viewport) {
        if (level == null || viewport == null) return false;
        if (!ensureTexture(mc, viewport.getTextureWidth(), viewport.getTextureHeight())) return false;

        TileCache cache = OpenMapDeltaClient.getActiveTileCache();
        if (cache == null) return false;
        if (!raster.update(viewport, cache.getTiles()) && textureReady) return true;
        int[] pixels = raster.getPixels();
        int minX = textureReady ? raster.getDirtyMinX() : 0;
        int minZ = textureReady ? raster.getDirtyMinZ() : 0;
        int maxX = textureReady ? raster.getDirtyMaxX() : textureWidth;
        int maxZ = textureReady ? raster.getDirtyMaxZ() : textureHeight;
        for (int py = minZ; py < maxZ; py++) {
            for (int px = minX; px < maxX; px++) {
                image.setPixelRGBA(px, py, pixels[py * textureWidth + px]);
            }
        }

        try {
            dynamicTexture.upload();
            textureReady = true;
            return true;
        } catch (Exception e) {
            textureReady = false;
            LOG.warn("Failed to upload fullscreen map texture", e);
            return false;
        }
    }

    private boolean ensureTexture(Minecraft mc, int width, int height) {
        if (dynamicTexture != null && textureWidth == width && textureHeight == height
                && textureLocation != null) return true;
        release(mc);
        try {
            image = new NativeImage(width, height, false);
            dynamicTexture = new DynamicTexture(image);
            mc.getTextureManager().register(TEXTURE_ID, dynamicTexture);
            textureLocation = TEXTURE_ID;
            textureWidth = width;
            textureHeight = height;
            return true;
        } catch (Exception e) {
            textureReady = false;
            LOG.warn("Failed to register fullscreen map texture", e);
            release(mc);
            return false;
        }
    }

    public void clear(Minecraft mc) {
        raster.clear();
        release(mc);
    }

    private void release(Minecraft mc) {
        if (textureLocation != null) {
            mc.getTextureManager().release(textureLocation);
        } else if (dynamicTexture != null) {
            dynamicTexture.close();
        } else if (image != null) {
            image.close();
        }
        dynamicTexture = null;
        image = null;
        textureLocation = null;
        textureWidth = 0;
        textureHeight = 0;
        textureReady = false;
    }

    public ResourceLocation getTextureLocation() { return textureReady ? textureLocation : null; }
    public int getTextureWidth() { return textureWidth; }
    public int getTextureHeight() { return textureHeight; }
    public boolean isTextureReady() { return textureReady; }
}
