package net.minecraft.client.gui;

import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;

public final class GuiGraphics implements RecordingSurface {
    private final List<Draw> draws = new ArrayList<>();
    public int backgrounds;
    public void background(boolean blurry) {
        backgrounds++;
        if (blurry) for (Draw draw : draws) draw.blurred = true;
        draws.add(new Draw("background", false));
    }
    public void fill(int x1, int y1, int x2, int y2, int color) { add(x2 - x1 > 100 ? "panel" : "fill"); }
    public void renderOutline(int x, int y, int width, int height, int color) { add(width > 100 ? "panel-outline" : "swatch-outline"); }
    public void drawCenteredString(Font font, net.minecraft.network.chat.Component text, int x, int y, int color) { add("text:" + text.getString()); }
    public void drawString(Font font, FormattedCharSequence text, int x, int y, int color, boolean shadow) { add("text:" + text.getString()); }
    public void drawString(Font font, net.minecraft.network.chat.Component text, int x, int y, int color, boolean shadow) { add("text:" + text.getString()); }
    public void editBox(String value, int x, int y, int width, int height) { add("editbox:" + value); }
    public void button(String text, int x, int y, int width, int height) { add("button:" + text); }
    private void add(String value) { draws.add(new Draw(value, false)); }
    @Override public List<Draw> draws() { return draws; }
    @Override public int backgrounds() { return backgrounds; }
}
