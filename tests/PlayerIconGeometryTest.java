// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.render;

import net.aosankaku.openmapdelta.marker.MarkerIconSizing;
import net.aosankaku.openmapdelta.marker.PlayerMarkerSizing;

/** Visible rotated face corners remain selectable and inside clamped navigation bounds. */
public final class PlayerIconGeometryTest {
    private static int checks;

    public static void main(String[] args) {
        check(PlayerMarkerSizing.footprintSize(16, false) == 16, "upright even-sized face keeps its footprint");
        check(PlayerMarkerSizing.footprintSize(5, false) == 6, "odd glyph's centered half pixels fit symmetric bounds");
        int rotatedFace = PlayerMarkerSizing.footprintSize(16, true);
        check(MarkerIconSizing.contains(100 + 11.3, 100, 100, 100, rotatedFace),
                "visible 45-degree face corner opens the player context menu");
        check(!MarkerIconSizing.contains(100 + rotatedFace / 2.0, 100, 100, 100, rotatedFace),
                "space beyond the reserved footprint remains outside selection");

        for (int glyph : new int[]{2, 3, 5, 16, 32, 64}) {
            int footprint = PlayerMarkerSizing.footprintSize(glyph, true);
            double half = glyph / 2.0 - 0.00001;
            var edge = FullscreenMapMarkerGeometry.place(20, 40, 500, 300, 900, -600, footprint, true);
            check(edge.edge(), "navigation player is clamped at map edge");
            check(edge.x() - (footprint + 6) / 2 >= 20 && edge.x() + (footprint + 6) / 2 < 520
                            && edge.y() - (footprint + 6) / 2 >= 40 && edge.y() + (footprint + 6) / 2 < 340,
                    "navigation halo and outline remain inside map bounds");
            for (int angle = 0; angle < 360; angle++) {
                double radians = Math.toRadians(angle);
                double cos = Math.cos(radians), sin = Math.sin(radians);
                for (int sx : new int[]{-1, 1}) {
                    for (int sy : new int[]{-1, 1}) {
                        double dx = half * (sx * cos - sy * sin);
                        double dy = half * (sx * sin + sy * cos);
                        check(MarkerIconSizing.contains(100 + dx, 100 + dy, 100, 100, footprint),
                                "rendered corner is selectable: " + glyph + ", " + angle);
                        check(edge.x() + dx >= 20 && edge.x() + dx < 520
                                        && edge.y() + dy >= 40 && edge.y() + dy < 340,
                                "rendered corner stays inside navigation edge: " + glyph + ", " + angle);
                    }
                }
            }
        }
        System.out.println("Player icon geometry tests passed (" + checks + " assertions).");
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
}
