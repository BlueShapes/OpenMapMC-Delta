// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.navigation;

import java.util.UUID;

/** Pure guards for navigation requests originating in the client UI. */
public final class NavigationRequestPolicy {
    public enum ContextDestination { SELECTED_MARKER, MAP_COORDINATE, STALE_SELECTION }

    private NavigationRequestPolicy() {}

    public static boolean canStart(UUID destinationId, UUID playerId) {
        return destinationId != null && !destinationId.equals(playerId);
    }

    public static ContextDestination contextDestination(boolean markerWasSelected, boolean markerStillExists) {
        if (!markerWasSelected) return ContextDestination.MAP_COORDINATE;
        return markerStillExists ? ContextDestination.SELECTED_MARKER : ContextDestination.STALE_SELECTION;
    }
}
