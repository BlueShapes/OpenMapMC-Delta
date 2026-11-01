// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.navigation;

import net.aosankaku.openmapdelta.marker.MarkerType;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

/** World markers for explicitly selected players and coordinate destinations. */
public final class NavigationWorldSelection {
    private NavigationWorldSelection() {}

    public static MarkerType snapshotType(MarkerType sourceType) {
        return sourceType == MarkerType.PLAYER || sourceType == MarkerType.NAVIGATION_POINT
                ? MarkerType.NAVIGATION_POINT : MarkerType.NAVIGATION;
    }

    public static <T> List<T> merge(Collection<T> visible, List<NavigationState.Target> targets,
                                   String dimension, Function<T, UUID> id,
                                   Function<T, NavigationState.Position> position,
                                   Function<T, MarkerType> type,
                                   Function<NavigationState.Target, T> fallback) {
        // A visible PLAYER must not replace its same-ID, world-renderable navigation snapshot.
        List<T> worldMarkers = visible.stream().filter(marker -> type.apply(marker).isWorldWaypoint()).toList();
        List<NavigationState.Target> worldTargets = targets.stream().filter(target -> {
            T marker = fallback.apply(target);
            return marker != null && type.apply(marker).isWorldWaypoint();
        }).toList();
        return NavigationMapSelection.merge(worldMarkers, worldTargets, dimension, id, position, fallback);
    }
}
