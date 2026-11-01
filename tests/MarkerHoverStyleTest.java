// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.marker;

public final class MarkerHoverStyleTest {
    private static int checks;

    public static void main(String[] args) {
        for (int light : new int[]{0xFFFF00, 0xFFFFFF, 0x00FFFF, 0x00FF00, 0xFFAA00, 0x888888}) {
            check(MarkerHoverStyle.outlineColor(light) == 0xFF000000, "Bright labels require a dark halo");
        }
        for (int dark : new int[]{0x000000, 0x0000FF, 0x330066, 0x222222, 0x444444, 0x550000}) {
            check(MarkerHoverStyle.outlineColor(dark) == 0xFFFFFFFF, "Dark labels require a light halo");
        }
        check(MarkerHoverStyle.outlineColor(0x00747474) == 0xFFFFFFFF, "Dark gray at the contrast crossover");
        check(MarkerHoverStyle.outlineColor(0x00777777) == 0xFF000000, "Light gray at the contrast crossover");
        for (int color : new int[]{0xFFFF00, 0xFFFFFF, 0x000000, 0x333388, 0x12AB56, 0x777777}) {
            int expected = MarkerHoverStyle.outlineColor(color);
            for (int alpha : new int[]{0, 1, 127, 255}) {
                check(MarkerHoverStyle.outlineColor((alpha << 24) | color) == expected,
                        "Stored alpha must not change opaque hover contrast");
            }
        }
        System.out.println("Marker hover contrast: " + checks + " checks passed.");
    }

    private static void check(boolean result, String message) {
        checks++;
        if (!result) throw new AssertionError(message);
    }
}
