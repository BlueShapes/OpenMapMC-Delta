// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.marker;

/** Chooses a legible hover halo independently of font and GUI rendering APIs. */
public final class MarkerHoverStyle {
    private MarkerHoverStyle() {}

    public static int outlineColor(int markerColor) {
        double luminance = 0.2126 * linear((markerColor >>> 16) & 0xFF)
                + 0.7152 * linear((markerColor >>> 8) & 0xFF)
                + 0.0722 * linear(markerColor & 0xFF);
        double blackContrast = (luminance + 0.05) / 0.05;
        double whiteContrast = 1.05 / (luminance + 0.05);
        return blackContrast >= whiteContrast ? 0xFF000000 : 0xFFFFFFFF;
    }

    private static double linear(int component) {
        double value = component / 255.0;
        return value <= 0.04045 ? value / 12.92 : Math.pow((value + 0.055) / 1.055, 2.4);
    }
}
