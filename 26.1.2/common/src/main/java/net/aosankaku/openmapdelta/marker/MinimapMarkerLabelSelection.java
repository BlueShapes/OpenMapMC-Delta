// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.marker;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;

/** Pure selection logic for capped, ordinary waypoint labels on the minimap. */
public final class MinimapMarkerLabelSelection {
    private MinimapMarkerLabelSelection() {}

    /**
     * Selects visible ordinary labels by nearest horizontal distance. Emphasized markers are rendered
     * separately and therefore never use a slot in the ordinary label limit.
     */
    public static <T> List<T> select(Collection<Candidate<T>> candidates, int limit) {
        if (candidates.isEmpty() || limit == 0) return List.of();
        var eligible = candidates.stream()
                .filter(candidate -> candidate.visible() && candidate.eligible() && !candidate.emphasized())
                .sorted(Comparator.comparingDouble((Candidate<T> candidate) -> safeDistance(candidate.distance()))
                        .thenComparing(Candidate::stableId, Comparator.nullsLast(String::compareTo)))
                .toList();
        if (limit < 0 || eligible.size() <= limit) return eligible.stream().map(Candidate::value).toList();
        return eligible.subList(0, limit).stream().map(Candidate::value).toList();
    }

    private static double safeDistance(double distance) {
        return Double.isFinite(distance) && distance >= 0.0 ? distance : Double.POSITIVE_INFINITY;
    }

    public record Candidate<T>(T value, String stableId, double distance,
                               boolean visible, boolean eligible, boolean emphasized) {}
}
