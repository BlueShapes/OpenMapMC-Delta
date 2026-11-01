// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.gui;

/** Map viewport geometry with or without the fullscreen screen's controls. */
public final class FullscreenMapChrome {
    private FullscreenMapChrome() {}

    public static Bounds bounds(int width, int height, int sidebarWidth, boolean hidden) {
        if (hidden) return new Bounds(0, 0, Math.max(1, width), Math.max(1, height));
        return new Bounds(4, 50, Math.max(64, width - sidebarWidth - 8), Math.max(64, height - 54));
    }

    public record Bounds(int x, int y, int width, int height) {}
}
