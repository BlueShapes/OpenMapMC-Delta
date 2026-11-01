package net.minecraft.client.gui.screens;

import net.minecraft.network.chat.Component;
import java.util.function.Consumer;

public final class ConfirmScreen extends Screen {
    public ConfirmScreen(Consumer<Boolean> callback, Component title, Component message, Component yes, Component no) { super(title); }
}
