package net.minecraft.client.gui;
public class Font {
    public final int lineHeight = 9;
    public int width(String text) { return text.codePointCount(0, text.length()) * 6; }
}
