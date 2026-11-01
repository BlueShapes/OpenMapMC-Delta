package net.aosankaku.openmapdelta.web;

import net.aosankaku.openmapdelta.marker.MarkerIconCatalog;
import net.minecraft.client.Minecraft;

public final class WebMapTextureCapture {
    private static int captureCount;

    private WebMapTextureCapture() {}

    public static WebMapImagePixels markerItemPixels(Minecraft minecraft, MarkerIconCatalog.Entry entry) {
        captureCount++;
        return new WebMapImagePixels(3, 1, new int[]{
                0xFF0000FF, // opaque red in ABGR
                0xFFFF0000, // opaque blue in ABGR
                0x8042A61D  // translucent, asymmetric RGB channels in ABGR
        });
    }

    public static int captureCount() { return captureCount; }
}
