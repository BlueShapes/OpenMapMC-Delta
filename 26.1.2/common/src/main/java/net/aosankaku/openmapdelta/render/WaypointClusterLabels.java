// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.render;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

/** Selects the labels shown for a waypoint cluster without changing their source order. */
public final class WaypointClusterLabels {
    public static final int MAX_NAMES = 5;

    private WaypointClusterLabels() {}

    /**
     * Selects up to five emphasized entries first, fills remaining slots in source order, then
     * returns the selected entries in source order. Indexes are used so equal values still count
     * as distinct cluster members.
     */
    public static <T> Selection<T> select(List<T> alreadyOrdered, Predicate<? super T> emphasized) {
        Objects.requireNonNull(alreadyOrdered, "alreadyOrdered");
        Objects.requireNonNull(emphasized, "emphasized");

        boolean[] selected = new boolean[alreadyOrdered.size()];
        int selectedCount = 0;
        for (int i = 0; i < alreadyOrdered.size() && selectedCount < MAX_NAMES; i++) {
            if (emphasized.test(alreadyOrdered.get(i))) {
                selected[i] = true;
                selectedCount++;
            }
        }
        for (int i = 0; i < alreadyOrdered.size() && selectedCount < MAX_NAMES; i++) {
            if (!selected[i]) {
                selected[i] = true;
                selectedCount++;
            }
        }

        List<T> names = new ArrayList<>(selectedCount);
        for (int i = 0; i < selected.length; i++) {
            if (selected[i]) names.add(alreadyOrdered.get(i));
        }
        return new Selection<>(names, alreadyOrdered.size() - selectedCount);
    }

    public record Selection<T>(List<T> names, int remaining) {
        public Selection {
            names = List.copyOf(Objects.requireNonNull(names, "names"));
            if (remaining < 0) throw new IllegalArgumentException("remaining must be nonnegative");
        }
    }
}
