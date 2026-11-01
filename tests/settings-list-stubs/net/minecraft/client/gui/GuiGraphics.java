package net.minecraft.client.gui;

import net.minecraft.network.chat.Component;
import java.util.ArrayList;
import java.util.List;

public class GuiGraphics {
    public record Fill(int left, int top, int right, int bottom, int color) {}
    public final List<Fill> fills = new ArrayList<>();
    public final List<Fill> outlines = new ArrayList<>();
    public void fill(int left, int top, int right, int bottom, int color) {
        fills.add(new Fill(left, top, right, bottom, color));
    }
    public void drawCenteredString(Font font, Component text, int x, int y, int color) {}
    public void renderOutline(int left, int top, int width, int height, int color) {
        outlines.add(new Fill(left, top, left + width, top + height, color));
    }
}
