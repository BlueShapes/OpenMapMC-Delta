// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.gui;

import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;

/** Grouping and hit targets independent of Minecraft rendering and visibility. */
public final class WaypointListModel {
    public static final int ROW_HEIGHT = 16, STAR_WIDTH = 12, EYE_WIDTH = 14, DELETE_WIDTH = 16;
    public record Category(String name, boolean stations) {
        public Category {
            name = name == null ? "" : name.trim();
            if (!name.isEmpty()) stations = false;
        }
    }
    public record Row<T>(Category category, T marker) {
        public boolean isHeader() { return marker == null; }
    }
    public enum Action { NONE, SELECT, EDIT, FAVORITE, VISIBILITY, DELETE, TOGGLE_CATEGORY }
    private WaypointListModel() {}

    public static <T> List<Row<T>> rows(Collection<T> markers, Function<T, Category> category,
                                        Comparator<T> order, Predicate<T> search) {
        return rows(markers, category, order, search, Set.of());
    }

    public static <T> List<Row<T>> rows(Collection<T> markers, Function<T, Category> category,
                                        Comparator<T> order, Predicate<T> search, Set<Category> collapsed) {
        Map<Category, List<T>> groups = new TreeMap<>(Comparator
                .comparing(Category::name, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(Category::name).thenComparing(Category::stations));
        for (T marker : markers) {
            if (search.test(marker)) groups.computeIfAbsent(category.apply(marker), ignored -> new ArrayList<>()).add(marker);
        }
        List<Row<T>> rows = new ArrayList<>();
        groups.forEach((key, members) -> {
            rows.add(new Row<>(key, null));
            if (collapsed.contains(key)) return;
            members.sort(order);
            members.forEach(marker -> rows.add(new Row<>(key, marker)));
        });
        return List.copyOf(rows);
    }
    /** Bulk actions include the whole category, even when a search hides some of its rows. */
    public static <T> List<T> members(Collection<T> markers, Function<T, Category> category, Category selected) {
        return markers.stream().filter(marker -> category.apply(marker).equals(selected)).toList();
    }
    public static Action actionAt(double x, int width, int button, boolean header, boolean deletable) {
        if (x < 0 || x >= width) return Action.NONE;
        if (button == 1) return header ? Action.NONE : Action.EDIT;
        if (button != 0) return Action.NONE;
        if (header) return x >= 16 && x < 30 ? Action.VISIBILITY : Action.TOGGLE_CATEGORY;
        if (x >= width - DELETE_WIDTH) return deletable ? Action.DELETE : Action.NONE;
        if (x >= 2 && x < 2 + STAR_WIDTH) return Action.FAVORITE;
        if (x >= 2 + STAR_WIDTH && x < 2 + STAR_WIDTH + EYE_WIDTH) return Action.VISIBILITY;
        return Action.SELECT;
    }
    /** Cursor snap targets correspond to the controls drawn for each row. */
    public static List<Double> targetXs(int width, boolean header, boolean deletable) {
        if (width < 64) return List.of();
        if (header) return List.of(23.0, Math.max(34.0, (34 + width) / 2.0));
        List<Double> targets = new ArrayList<>(List.of(2 + STAR_WIDTH / 2.0,
                2 + STAR_WIDTH + EYE_WIDTH / 2.0, (40 + width - DELETE_WIDTH) / 2.0));
        if (deletable) targets.add(width - DELETE_WIDTH / 2.0);
        return List.copyOf(targets);
    }
    public static int sidebarWidth(int screenWidth) {
        return Math.max(0, Math.min(Math.min(260, Math.max(160, screenWidth / 2)), screenWidth - 72));
    }
}
