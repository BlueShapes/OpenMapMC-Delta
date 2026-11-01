// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.marker;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;

/** Minecraft text formatting for the query editor and searchable icon tooltips. */
public final class MarkerIconSearchText {
    private MarkerIconSearchText() {}

    public static FormattedCharSequence syntax(String query) {
        MutableComponent formatted = Component.empty();
        int cursor = 0;
        while (cursor < query.length()) {
            char c = query.charAt(cursor);
            if (Character.isWhitespace(c)) {
                int end = cursor + 1;
                while (end < query.length() && Character.isWhitespace(query.charAt(end))) end++;
                formatted.append(Component.literal(query.substring(cursor, end)));
                cursor = end;
                continue;
            }
            if (c == '|') {
                formatted.append(Component.literal("|").withStyle(ChatFormatting.GRAY));
                cursor++;
                continue;
            }
            int end = tokenEnd(query, cursor);
            String token = query.substring(cursor, end);
            boolean excluded = token.startsWith("-") && token.length() > 1;
            if (excluded) {
                formatted.append(Component.literal("-").withStyle(ChatFormatting.RED));
                token = token.substring(1);
            }
            ChatFormatting color = switch (token.isEmpty() ? '\0' : token.charAt(0)) {
                case '@' -> ChatFormatting.AQUA;
                case '&' -> ChatFormatting.GREEN;
                case '#' -> ChatFormatting.YELLOW;
                case '$' -> ChatFormatting.LIGHT_PURPLE;
                default -> ChatFormatting.WHITE;
            };
            formatted.append(Component.literal(token).withStyle(color));
            cursor = end;
        }
        return formatted.getVisualOrderText();
    }

    private static int tokenEnd(String text, int start) {
        boolean quoted = false;
        for (int i = start; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '"' && (i == start || text.charAt(i - 1) != '\\')) quoted = !quoted;
            else if (!quoted && (Character.isWhitespace(c) || c == '|')) return i;
        }
        return text.length();
    }

    public static Component highlight(String value, String queryText) {
        if (value == null || value.isEmpty()) return Component.empty();
        List<String> terms = MarkerIconSearchQuery.parse(queryText).positiveTerms().stream()
                .map(MarkerIconSearchQuery.Term::value).filter(term -> !term.isEmpty()).distinct().toList();
        boolean[] highlighted = new boolean[value.length()];
        for (String term : terms) {
            MarkerIconSearchQuery.markCaseInsensitiveMatches(value, term, highlighted);
        }
        MutableComponent result = Component.empty();
        for (int start = 0; start < value.length();) {
            boolean match = highlighted[start];
            int end = start + 1;
            while (end < value.length() && highlighted[end] == match) end++;
            MutableComponent part = Component.literal(value.substring(start, end));
            if (match) part.setStyle(Style.EMPTY.withColor(ChatFormatting.YELLOW).withBold(true));
            result.append(part);
            start = end;
        }
        return result;
    }

    public static Component tooltip(MarkerIconCatalog.Entry entry, String query) {
        MarkerIconSearchQuery.Metadata data = entry.metadata();
        List<Component> lines = new ArrayList<>();
        lines.add(highlight(entry.label().getString(), query));
        if (!data.modName().isBlank() || !data.modId().isBlank()) {
            String mod = data.modName().isBlank() ? data.modId() : data.modName() + " (" + data.modId() + ")";
            lines.add(Component.translatable("gui.odysseymap.icon_mod").append(": ").withStyle(ChatFormatting.GRAY).append(highlight(mod, query)));
        }
        if (!data.id().isBlank()) {
            lines.add(Component.translatable("gui.odysseymap.icon_id").append(": ").withStyle(ChatFormatting.GRAY).append(highlight(data.id(), query)));
        }
        if (!data.tags().isEmpty()) {
            String tags = String.join(", ", data.tags());
            lines.add(Component.translatable("gui.odysseymap.icon_tags").append(": ").withStyle(ChatFormatting.GRAY).append(highlight(tags, query)));
        }
        int shown = Math.min(3, data.tooltip().size());
        for (int i = 0; i < shown; i++) {
            String text = data.tooltip().get(i);
            if (text.length() > 96) text = text.substring(0, 93) + "...";
            lines.add(highlight(text, query));
        }
        MutableComponent combined = Component.empty();
        for (int i = 0; i < lines.size(); i++) {
            if (i > 0) combined.append("\n");
            combined.append(lines.get(i));
        }
        return combined;
    }
}
