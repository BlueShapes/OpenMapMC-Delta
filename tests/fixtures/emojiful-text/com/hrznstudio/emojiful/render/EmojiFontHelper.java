package com.hrznstudio.emojiful.render;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.FormattedCharSink;
/** The optional display API only; actual glyph contracts are tested in Emojiful. */
public final class EmojiFontHelper {
    public static int calls;
    public static boolean fail;
    public static FormattedCharSequence outlineSequence(FormattedCharSequence source) {
        calls++;
        if (fail) throw new IllegalStateException("Optional API unavailable");
        return new Outline(source);
    }
    public record Outline(FormattedCharSequence source) implements FormattedCharSequence {
        @Override public boolean accept(FormattedCharSink sink) { return source.accept(sink); }
    }
}
