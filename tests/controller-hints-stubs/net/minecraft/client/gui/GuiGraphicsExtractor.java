package net.minecraft.client.gui;
import net.minecraft.util.FormattedCharSequence;
public final class GuiGraphicsExtractor {
    public int rows, width;
    public boolean glyphs=true;
    public void fill(int x,int y,int right,int bottom,int color) { width=right-x; }
    public void text(Font font,FormattedCharSequence sequence,int x,int y,int color,boolean shadow) {
        rows++; glyphs &= sequence.glyph();
    }
}
