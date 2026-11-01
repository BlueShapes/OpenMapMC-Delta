package net.minecraft.client.gui.components;

import net.minecraft.network.chat.Component;

public final class Tooltip {
    private final Component message;

    private Tooltip(Component message) { this.message = message; }
    public static Tooltip create(Component message) { return new Tooltip(message); }
    public Component message() { return message; }
}
