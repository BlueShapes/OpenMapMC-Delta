package net.minecraft.util;
import net.minecraft.network.chat.Style;
@FunctionalInterface public interface FormattedCharSequence {
    boolean accept(FormattedCharSink sink);
    static FormattedCharSequence forward(String text, Style style) {
        return sink -> {
            for (int i = 0; i < text.length();) {
                int cp = text.codePointAt(i);
                if (!sink.accept(i, style, cp)) return false;
                i += Character.charCount(cp);
            }
            return true;
        };
    }
}
