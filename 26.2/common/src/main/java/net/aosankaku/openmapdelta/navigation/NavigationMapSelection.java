// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.navigation;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

/** Prefer an original visible marker at the destination, otherwise draw one session fallback per ID. */
public final class NavigationMapSelection {
    private NavigationMapSelection() {}

    /** A tracked player destination does not replace the local player's own map marker. */
    public static boolean suppressesLocalHead(net.aosankaku.openmapdelta.marker.MarkerType type, UUID trackedPlayerId) {
        return type.isWorldWaypoint() && !(type == net.aosankaku.openmapdelta.marker.MarkerType.NAVIGATION_POINT
                && trackedPlayerId != null);
    }

    public static boolean localHeadVisible(boolean configured, boolean navigating,
                                            boolean waypointOverlaps, boolean insideViewport) {
        return insideViewport && (navigating || configured && !waypointOverlaps);
    }

    public static <T> List<T> merge(Collection<T> visible, List<NavigationState.Target> targets,
                                    String dimension, Function<T, UUID> id,
                                    Function<T, NavigationState.Position> position,
                                    Function<NavigationState.Target, T> fallback) {
        Map<UUID, NavigationState.Target> active = new LinkedHashMap<>();
        for (NavigationState.Target target : targets) active.put(target.id(), target);
        Map<UUID, T> selected = new LinkedHashMap<>();
        for (T marker : visible) {
            NavigationState.Target target = active.get(id.apply(marker));
            if (target == null || target.position().equals(position.apply(marker))) {
                selected.putIfAbsent(id.apply(marker), marker);
            }
        }
        for (NavigationState.Target target : targets) {
            if (target.position().dimension().equals(dimension) && !selected.containsKey(target.id())) {
                T marker = fallback.apply(target);
                if (marker != null) selected.put(target.id(), marker);
            }
        }
        return List.copyOf(selected.values());
    }
}
