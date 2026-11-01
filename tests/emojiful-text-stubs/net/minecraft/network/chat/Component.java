package net.minecraft.network.chat;
import net.minecraft.util.FormattedCharSequence;
public record Component(String text) {
    public static Component literal(String text) { return new Component(text); }
    public FormattedCharSequence getVisualOrderText() { return FormattedCharSequence.forward(text, Style.EMPTY); }
}
