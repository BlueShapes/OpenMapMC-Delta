// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.gui;

public final class FullscreenMapChromeTest {
    public static void main(String[] args) {
        int checks = 0;
        for (int width : new int[]{240, 854, 1920, 2560}) {
            for (int height : new int[]{135, 480, 1080, 1440}) {
                for (int sidebar : new int[]{0, 160, 320}) {
                    var hidden = FullscreenMapChrome.bounds(width, height, sidebar, true);
                    if (hidden.x() != 0 || hidden.y() != 0 || hidden.width() != width || hidden.height() != height)
                        throw new AssertionError("F1 left header/sidebar/footer margins");
                    var shown = FullscreenMapChrome.bounds(width, height, sidebar, false);
                    if (shown.x() != 4 || shown.y() != 50 || shown.width() != Math.max(64, width-sidebar-8)
                            || shown.height() != Math.max(64, height-54))
                        throw new AssertionError("Leaving F1 changed normal map layout");
                    checks += 2;
                }
            }
        }
        System.out.println("Fullscreen F1 viewport: " + checks + " checks passed.");
    }
}
