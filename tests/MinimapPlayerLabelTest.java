// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.marker;

import net.aosankaku.openmapdelta.config.OpenMapDeltaConfig;
import net.aosankaku.openmapdelta.config.OpenMapDeltaConfig.MinimapPlayerLabelMode;

public final class MinimapPlayerLabelTest {
    private static int assertions;

    public static void main(String[] args) {
        circleBounds();
        squareBounds();
        worldCoordinatesUseTheTerrainFootprint();
        textModes();
        System.out.println("Minimap player label tests passed (" + assertions + " assertions).");
    }

    private static void circleBounds() {
        check(!MinimapPlayerLabel.isOutside(0, 0, 100, true), "circle center is inside");
        check(!MinimapPlayerLabel.isOutside(50, 0, 100, true), "circle circumference is inside");
        check(!MinimapPlayerLabel.isOutside(-50, 0, 100, true), "negative boundary coordinate is inside");
        check(MinimapPlayerLabel.isOutside(50.001, 0, 100, true), "point beyond circle circumference is outside");
        check(MinimapPlayerLabel.isOutside(-40, -31, 100, true), "negative map-relative coordinates use radial bounds");
        check(!MinimapPlayerLabel.isOutside(30, 40, 100, true), "3-4-5 point on circle remains inside");
        check(MinimapPlayerLabel.isOutside(40, 40, 100, true), "diagonal point is outside circular terrain outline");
        check(MinimapPlayerLabel.isOutside(51, 0, 100, true), "point outside the real map remains outside after edge clamping");
    }

    private static void squareBounds() {
        check(!MinimapPlayerLabel.isOutside(50, -50, 100, false), "square corners are inside");
        check(!MinimapPlayerLabel.isOutside(-50, 0, 100, false), "square negative boundary coordinate is inside");
        check(MinimapPlayerLabel.isOutside(50.001, 0, 100, false), "point beyond square edge is outside");
        check(MinimapPlayerLabel.isOutside(0, -50.001, 100, false), "negative point beyond square edge is outside");
        check(!MinimapPlayerLabel.isOutside(0, 49, 100, false), "square uses independent axis bounds");
        check(!MinimapPlayerLabel.isOutside(40, 40, 100, false), "the same diagonal point is inside the square terrain outline");
        check(OpenMapDeltaConfig.MINIMAP_MARKER_EDGE_OFFSET.get() > 0
                        && MinimapPlayerLabel.isOutside(51, 0, 100, false),
                "positive edge offset does not widen square terrain bounds");
    }

    private static void worldCoordinatesUseTheTerrainFootprint() {
        check(!MinimapPlayerLabel.isOutsideWorld(0, 0, 60, 60, 1, 128, false),
                "world-space diagonal offset stays inside the rotated square terrain footprint");
        check(MinimapPlayerLabel.isOutsideWorld(0, 0, 80, 0, 1, 128, false),
                "world-space offset beyond a square side is outside");
        check(!MinimapPlayerLabel.isOutsideWorld(0, 0, 120, 120, 2, 128, false),
                "block-per-pixel stride converts world offsets to minimap pixels");
        check(MinimapPlayerLabel.isOutsideWorld(0, 0, 162, 0, 2, 128, false),
                "stride conversion preserves the outside boundary");
        check(!MinimapPlayerLabel.isOutsideWorld(100, -100, 36, -164, 1, 128, false),
                "negative world coordinates use relative square bounds");
        check(!MinimapPlayerLabel.isOutsideWorld(0, 0, -64, 64, 1, 128, false),
                "exact negative world-space square boundary is inside");
        check(MinimapPlayerLabel.isOutsideWorld(0, 0, -65, 0, 1, 128, false),
                "negative world-space point beyond the square boundary is outside");
    }

    private static void textModes() {
        check(MinimapPlayerLabel.text(MinimapPlayerLabelMode.NAME_ONLY, "Aoki", 148.99).equals("Aoki"),
                "name-only mode returns the player name");
        check(MinimapPlayerLabel.text(MinimapPlayerLabelMode.DISTANCE_ONLY, "Aoki", 148.99).equals("148m"),
                "distance-only mode truncates to the existing integer meter format");
        check(MinimapPlayerLabel.text(MinimapPlayerLabelMode.NAME_AND_DISTANCE, "Aoki", 148.99).equals("Aoki 148m"),
                "combined mode shows the name and the existing integer meter distance");
        check(MinimapPlayerLabel.text(MinimapPlayerLabelMode.NAME_AND_DISTANCE, "Aoki", 0).equals("Aoki 0m"),
                "combined mode retains a zero distance");
        check(MinimapPlayerLabel.text(MinimapPlayerLabelMode.NAME_AND_DISTANCE, null, 12).equals("12m"),
                "combined mode tolerates a missing name without rendering null or a leading space");
        check(MinimapPlayerLabel.text(MinimapPlayerLabelMode.NONE, "Aoki", 148.99).isEmpty(),
                "none mode suppresses text");
        check(MinimapPlayerLabel.text(MinimapPlayerLabelMode.NAME_ONLY, "Aoki", 12).equals("Aoki")
                        && MinimapPlayerLabel.text(MinimapPlayerLabelMode.DISTANCE_ONLY, "Aoki", 12).equals("12m"),
                "inside and outside choices can select their text modes independently");
    }

    private static void check(boolean condition, String description) {
        assertions++;
        if (!condition) throw new AssertionError(description);
    }
}
