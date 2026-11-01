package net.minecraft.client;
import net.minecraft.client.gui.Gui;
import com.mojang.blaze3d.platform.Window;
public class Minecraft {
    public final Gui gui = new Gui();
    public final MouseHandler mouseHandler = new MouseHandler();
    public Window window = new Window();
    public Window getWindow() { return window; }
}
