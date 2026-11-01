package net.minecraft.client.gui.components;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.client.input.MouseButtonEvent;

public abstract class AbstractWidget {
    protected int x, y, width, height;
    protected boolean active = true, visible = true;

    protected AbstractWidget(int x, int y, int width, int height, Component message) {
        this.x = x; this.y = y; this.width = width; this.height = height;
    }
    public int getX() { return x; }
    public int getY() { return y; }
    public int getWidth() { return width; }
    public int getHeight() { return height; }
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) { return false; }
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double delta) { return false; }
    protected abstract void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick);
    protected abstract void updateWidgetNarration(NarrationElementOutput output);
    protected void defaultButtonNarrationText(NarrationElementOutput output) { }
}
