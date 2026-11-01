package net.minecraft.client.gui.components.toasts;
import net.minecraft.client.gui.GuiGraphicsExtractor;
public class ToastManager {
    public int extractions;
    public void extractRenderState(GuiGraphicsExtractor graphics) {
        extractions++;
        graphics.nextStratum();
        graphics.record("toast");
    }
}
