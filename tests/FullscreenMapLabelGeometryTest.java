// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.render;

/** Verifies scaled label drawing bounds and pointer selection without starting Minecraft. */
public final class FullscreenMapLabelGeometryTest {
    private static int assertions;

    public static void main(String[] args) {
        for (double scale : new double[]{0.25, 0.5, 0.65, 0.75, 1.0, 1.25, 1.5, 2.0}) {
            for (int width : new int[]{0, 9, 48, 101}) {
                var layout = FullscreenMapLabelGeometry.place(100, 120, 16, width, 9, scale);
                double left = layout.x() + (layout.textX() - 2) * layout.scale();
                double right = layout.x() + (layout.textX() + width + 2) * layout.scale();
                double top = layout.y() - 2 * layout.scale();
                double bottom = layout.y() + 11 * layout.scale();
                double centerX = (left + right) / 2;
                double centerY = (top + bottom) / 2;
                check(layout.contains(centerX, centerY), "scaled label center is selectable");
                check(layout.contains(left, centerY), "left background edge is included");
                check(layout.contains(centerX, top), "top background edge is included");
                check(!layout.contains(right, centerY), "right background edge is excluded");
                check(!layout.contains(centerX, bottom), "bottom background edge is excluded");
                check(!layout.contains(left - 0.01, centerY), "outside left edge is not selectable");
                near(bottom, 110, "background keeps a two-pixel gap above the icon");
                near(right - left, (width + 4) * layout.scale(), "background and hit width follow the text scale");
                check(Math.abs(centerX - 100) <= layout.scale() / 2 + 1e-5,
                        "odd text widths remain centered within half a glyph pixel");
            }
        }
        var smaller = FullscreenMapLabelGeometry.place(100, 120, 16, 100, 9, 0.75);
        check(!smaller.contains(145, smaller.y()), "shrinking text removes the old oversized hit region");
        var larger = FullscreenMapLabelGeometry.place(100, 120, 16, 100, 9, 2.0);
        check(larger.contains(180, larger.y()), "enlarged text can be selected beyond the old hit region");
        var shared = FullscreenMapLabelGeometry.place(100, 120, 16, 24 + 10, 9, 0.75);
        check(shared.contains(shared.x() + (shared.textX() + 4) * shared.scale(),
                shared.y() + 4 * shared.scale()), "the owner face prefix remains selectable at the smaller size");
        for (double invalid : new double[]{0, -1, 0.249, 2.001, Double.NaN, Double.POSITIVE_INFINITY}) {
            near(FullscreenMapLabelGeometry.safeScale(invalid), 0.75, "invalid text scale restores the smaller default");
        }
        near(FullscreenMapLabelGeometry.DEFAULT_SCALE, 0.75, "default text size is 25 percent smaller");
        System.out.println("Fullscreen label geometry tests passed (" + assertions + " assertions).");
    }

    private static void near(double actual, double expected, String message) {
        check(Math.abs(actual - expected) < 1e-5, message + ": " + actual);
    }

    private static void check(boolean value, String message) {
        assertions++;
        if (!value) throw new AssertionError(message);
    }
}
