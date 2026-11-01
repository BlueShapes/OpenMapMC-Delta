package net.minecraft.locale;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;
public final class Language {
    public static Language getInstance() { return new Language(); }
    public FormattedCharSequence getVisualOrder(FormattedText text) {
        return new FormattedCharSequence(text.getString(), text.hasGlyph());
    }
}
