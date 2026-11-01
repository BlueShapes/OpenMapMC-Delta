// Copyright (c) 2026 AoSankaku. OpenMapMC Delta modifications; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.render;

import com.mojang.blaze3d.platform.NativeImage;
import net.aosankaku.openmapdelta.marker.MarkerIconCatalog;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;

/** Headless regression test for the captured ABGR to NativeImage upload boundary. */
public final class MarkerIconSpriteCacheTest {
    private static final int RED_ABGR = 0xFF0000FF;
    private static final int BLUE_ABGR = 0xFFFF0000;
    private static final int TRANSLUCENT_ABGR = 0x8042A61D;

    private MarkerIconSpriteCacheTest() {}

    public static void main(String[] args) {
        Minecraft minecraft = new Minecraft();
        MarkerIconCatalog.Entry entry = new MarkerIconCatalog.Entry("item:test:marker", new MarkerIconCatalog.Item());

        ResourceLocation location = MarkerIconSpriteCache.get(minecraft, entry);

        if (location == null) throw new AssertionError("marker sprite was not created");
        if (net.aosankaku.openmapdelta.web.WebMapTextureCapture.captureCount() != 1) {
            throw new AssertionError("captured marker pixels should be requested once");
        }
        NativeImage image = NativeImage.lastCreated();
        assertPixel(image, 0, RED_ABGR);
        assertPixel(image, 1, BLUE_ABGR);
        assertPixel(image, 2, TRANSLUCENT_ABGR);

        if (minecraft.getTextureManager().registerCount() != 1) {
            throw new AssertionError("sprite texture should be registered exactly once");
        }
        Map<ResourceLocation, DynamicTexture> textures = minecraft.getTextureManager().registeredTextures();
        DynamicTexture texture = textures.get(location);
        if (texture == null) throw new AssertionError("returned sprite was not registered");
        if (texture.image() != image) throw new AssertionError("registered texture should own the captured image");
        if (texture.uploadCount() != 1) throw new AssertionError("registered sprite texture should be uploaded");

        System.out.println("MarkerIconSpriteCacheTest passed");
    }

    private static void assertPixel(NativeImage image, int x, int expected) {
        int actual = image.pixelAt(x, 0);
        if (actual != expected) {
            throw new AssertionError("pixel " + x + " changed: expected ABGR 0x"
                    + Integer.toHexString(expected) + ", got 0x" + Integer.toHexString(actual));
        }
    }
}
