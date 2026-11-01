// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.marker;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Cached, Minecraft-independent subset of JEI's useful ingredient search syntax. */
public final class MarkerIconSearchQuery {
    public enum Field { ANY, MOD, ID, TAG, TOOLTIP }

    public record Metadata(String name, String id, String modId, String modName,
                           List<String> tags, List<String> tooltip) {
        public Metadata {
            name = safe(name);
            id = safe(id);
            modId = safe(modId);
            modName = safe(modName);
            tags = tags == null ? List.of() : List.copyOf(tags);
            tooltip = tooltip == null ? List.of() : List.copyOf(tooltip);
        }
    }

    public record Term(Field field, String value, boolean excluded) {}
    public record Query(List<List<Term>> alternatives) {
        public Query {
            alternatives = alternatives.stream().map(List::copyOf).toList();
        }
        public List<Term> positiveTerms() {
            return alternatives.stream().flatMap(List::stream).filter(term -> !term.excluded()).toList();
        }
    }

    private MarkerIconSearchQuery() {}

    public static Query parse(String source) {
        String text = source == null ? "" : source;
        List<List<Term>> alternatives = new ArrayList<>();
        List<Term> current = new ArrayList<>();
        StringBuilder token = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\\' && quoted && i + 1 < text.length()
                    && (text.charAt(i + 1) == '"' || text.charAt(i + 1) == '\\')) {
                token.append(text.charAt(++i));
            } else if (c == '"') {
                quoted = !quoted;
            } else if (!quoted && c == '|') {
                addToken(current, token);
                if (!current.isEmpty()) alternatives.add(current);
                current = new ArrayList<>();
            } else if (!quoted && Character.isWhitespace(c)) {
                addToken(current, token);
            } else {
                token.append(c);
            }
        }
        addToken(current, token);
        if (!current.isEmpty() || alternatives.isEmpty()) alternatives.add(current);
        return new Query(alternatives);
    }

    public static boolean matches(Query query, Metadata metadata) {
        if (query == null || metadata == null) return false;
        if (query.alternatives().stream().allMatch(List::isEmpty)) return true;
        for (List<Term> group : query.alternatives()) {
            boolean matches = true;
            for (Term term : group) {
                boolean found = contains(metadata, term);
                if (term.excluded() == found) {
                    matches = false;
                    break;
                }
            }
            if (matches) return true;
        }
        return false;
    }

    /** Marks literal case-insensitive matches at indices in the original UTF-16 string. */
    public static void markCaseInsensitiveMatches(String text, String term, boolean[] marked) {
        if (text == null || term == null || marked == null || marked.length != text.length() || term.isEmpty()) return;
        for (int start = 0; start <= text.length() - term.length(); start++) {
            if (!text.regionMatches(true, start, term, 0, term.length())) continue;
            for (int index = start; index < start + term.length(); index++) marked[index] = true;
        }
    }

    private static void addToken(List<Term> destination, StringBuilder raw) {
        if (raw.length() == 0) return;
        String value = raw.toString();
        raw.setLength(0);
        boolean excluded = value.startsWith("-") && value.length() > 1;
        if (excluded) value = value.substring(1);
        Field field = Field.ANY;
        if (!value.isEmpty()) {
            field = switch (value.charAt(0)) {
                case '@' -> Field.MOD;
                case '&' -> Field.ID;
                case '#' -> Field.TAG;
                case '$' -> Field.TOOLTIP;
                default -> Field.ANY;
            };
            if (field != Field.ANY) value = value.substring(1);
        }
        if (!value.isBlank()) destination.add(new Term(field, normalize(value), excluded));
    }

    private static boolean contains(Metadata metadata, Term term) {
        String needle = term.value();
        if (needle.isEmpty()) return true;
        return switch (term.field()) {
            case ANY -> lower(metadata.name()).contains(needle)
                    || lower(metadata.id()).contains(needle)
                || lower(metadata.modId()).contains(needle)
                    || lower(metadata.modName()).contains(needle);
            case MOD -> normalizedMod(metadata.modId()).contains(normalizedMod(needle))
                    || normalizedMod(metadata.modName()).contains(normalizedMod(needle));
            case ID -> lower(metadata.id()).contains(needle);
            case TAG -> metadata.tags().stream().anyMatch(value -> lower(value).contains(needle));
            case TOOLTIP -> metadata.tooltip().stream().anyMatch(value -> lower(value).contains(needle));
        };
    }

    private static String normalizedMod(String value) {
        return lower(value).replace(" ", "").replace("_", "");
    }

    private static String normalize(String value) { return lower(value); }
    private static String lower(String value) { return safe(value).toLowerCase(Locale.ROOT); }
    private static String safe(String value) { return value == null ? "" : value; }
}
