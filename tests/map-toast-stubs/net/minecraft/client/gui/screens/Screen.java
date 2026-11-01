package net.minecraft.client.gui.screens;
import net.minecraft.client.gui.GuiGraphicsExtractor;
public class Screen {
    public void extractRenderStateWithTooltipAndSubtitles(GuiGraphicsExtractor graphics, int x, int y, float tick) {
        graphics.nextStratum();
        graphics.record("background");
        graphics.nextStratum();
        graphics.record("screen");
    }
}
