package net.minecraft.client.gui;
import net.minecraft.client.gui.components.toasts.ToastManager;
import net.minecraft.client.gui.screens.Screen;
public class Gui {
    public Screen screen;
    private final ToastManager toasts = new ToastManager();
    public ToastManager toastManager() { return toasts; }
}
