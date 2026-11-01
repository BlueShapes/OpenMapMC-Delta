// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.minecraft.client;

import net.minecraft.client.gui.Font;

public final class Minecraft {
    public final Font font;
    private final Window window;

    public Minecraft(Font font, double guiScale) {
        this.font = font;
        this.window = new Window(guiScale);
    }

    public Window getWindow() { return window; }

    public static final class Window {
        private final double guiScale;
        private Window(double guiScale) { this.guiScale = guiScale; }
        public double getGuiScale() { return guiScale; }
    }
}
