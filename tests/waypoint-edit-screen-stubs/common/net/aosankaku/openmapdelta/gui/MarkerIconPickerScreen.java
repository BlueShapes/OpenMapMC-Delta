package net.aosankaku.openmapdelta.gui;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.function.Consumer;

public final class MarkerIconPickerScreen extends Screen {
    public MarkerIconPickerScreen(Screen parent, String selected, Consumer<String> callback) { super(Component.literal("fixture icon picker")); }
}
