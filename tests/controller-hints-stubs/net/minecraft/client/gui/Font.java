package net.minecraft.client.gui;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;
public final class Font {
    public FormattedText substrByWidth(FormattedText text, int width) {
        return new Clip(text.getString().substring(0, Math.min(width, text.getString().length())), text.hasGlyph());
    }
    public int width(FormattedCharSequence sequence) { return sequence.text().length(); }
    private record Clip(String getString, boolean hasGlyph) implements FormattedText {}
}
