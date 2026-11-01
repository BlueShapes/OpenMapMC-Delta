package net.aosankaku.openmapdelta.render;
import net.minecraft.client.gui.GuiGraphicsExtractor;
public class MinimapHudRenderer {
    public static boolean visible = true;
    public static int calls;
    public static GuiGraphicsExtractor lastGraphics;
    public static void render(GuiGraphicsExtractor graphics, float tick, int width, int height) {
        calls++;
        lastGraphics = graphics;
        if (tick != 0.25f || width != 960 || height != 540)
            throw new AssertionError("Interpolation or GUI size lost");
        if (visible) {
            graphics.record("terrain");
            graphics.record("markers");
            graphics.record("info");
        }
    }
}
