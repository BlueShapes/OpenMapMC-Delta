// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.render;

import net.aosankaku.openmapdelta.OpenMapDeltaCommon;
import net.aosankaku.openmapdelta.marker.MarkerIconCatalog;
import net.aosankaku.openmapdelta.web.WebMapImagePixels;
import net.aosankaku.openmapdelta.web.WebMapTextureCapture;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

import java.util.LinkedHashMap;
import java.util.Map;

/** Uploads shared baked-item captures as see-through sprites for world waypoint overlays. */
public final class MarkerIconSpriteCache {
    private static final int MAX_SPRITES = 128;
    private static final LinkedHashMap<String, Sprite> SPRITES = new LinkedHashMap<>(32, 0.75F, true);
    private static long generation = Long.MIN_VALUE;

    private MarkerIconSpriteCache() {}

    public static void clear(Minecraft mc) { clear(mc, Long.MIN_VALUE); }

    static ResourceLocation get(Minecraft mc, MarkerIconCatalog.Entry entry) {
        if (entry == null || entry.item().isEmpty()) return null;
        long currentGeneration = MarkerIconCatalog.resourceGeneration();
        if (generation != currentGeneration) clear(mc, currentGeneration);

        Sprite cached = SPRITES.get(entry.id());
        if (cached != null) return cached.location();

        WebMapImagePixels captured = WebMapTextureCapture.markerItemPixels(mc, entry);
        if (captured == null) return null;
        NativeImage image = null;
        DynamicTexture dynamic = null;
        ResourceLocation location = null;
        try {
            int width = captured.width();
            int height = captured.height();
            int[] pixels = captured.pixels();
            image = new NativeImage(width, height, false);
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) image.setPixelRGBA(x, y, pixels[y * width + x]);
            }
            dynamic = new DynamicTexture(image);
            image = null;
            location = textureLocation(entry.id(), currentGeneration);
            mc.getTextureManager().register(location, dynamic);
            dynamic.upload();
            SPRITES.put(entry.id(), new Sprite(location, dynamic));
            trim(mc);
            return location;
        } catch (Exception unavailableTexture) {
            if (location != null) {
                try { mc.getTextureManager().release(location); }
                catch (RuntimeException releaseFailed) { if (dynamic != null) dynamic.close(); }
            } else if (dynamic != null) dynamic.close();
            else if (image != null) image.close();
            return null;
        }
    }

    private static void clear(Minecraft mc, long newGeneration) {
        for (Sprite sprite : SPRITES.values()) {
            try { mc.getTextureManager().release(sprite.location()); }
            catch (RuntimeException unavailableTexture) { sprite.texture().close(); }
        }
        SPRITES.clear();
        generation = newGeneration;
    }

    private static void trim(Minecraft mc) {
        while (SPRITES.size() > MAX_SPRITES) {
            Map.Entry<String, Sprite> eldest = SPRITES.entrySet().iterator().next();
            SPRITES.remove(eldest.getKey());
            try { mc.getTextureManager().release(eldest.getValue().location()); }
            catch (RuntimeException unavailableTexture) { eldest.getValue().texture().close(); }
        }
    }

    private static ResourceLocation textureLocation(String itemId, long generation) {
        String path = itemId.substring("item:".length()).replace(':', '/');
        return ResourceLocation.fromNamespaceAndPath(OpenMapDeltaCommon.MOD_ID, "marker_item_icons/g" + generation + "/" + path);
    }

    private record Sprite(ResourceLocation location, DynamicTexture texture) {}
}
