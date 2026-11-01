package net.minecraft.client.gui;

import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;

public final class Font {
    public List<FormattedCharSequence> split(Component component, int maxWidth) {
        String text = component.getString();
        if (text.startsWith("Your installed map mods")) {
            return List.of(new FormattedCharSequence("Your installed map mods have changed."),
                    new FormattedCharSequence("Choose one to enable."),
                    new FormattedCharSequence("Choose again in OpenMap Delta settings."));
        }
        if (text.startsWith("Choose one map mod")) {
            return List.of(new FormattedCharSequence("Choose one map mod to enable."),
                    new FormattedCharSequence("Installed mods and map data are kept."));
        }
        List<FormattedCharSequence> lines = new ArrayList<>();
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(text.length(), start + Math.max(1, maxWidth / 6));
            if (end < text.length()) {
                int space = text.lastIndexOf(' ', end);
                if (space > start) end = space;
            }
            lines.add(new FormattedCharSequence(text.substring(start, end)));
            start = end;
            while (start < text.length() && text.charAt(start) == ' ') start++;
        }
        return lines;
    }
}
