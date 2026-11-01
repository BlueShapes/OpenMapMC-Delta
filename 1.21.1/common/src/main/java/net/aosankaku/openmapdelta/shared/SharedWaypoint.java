// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.shared;

import java.util.UUID;

/** Immutable server-owned waypoint shared with players on the same saved world. */
public record SharedWaypoint(UUID id, UUID ownerId, String ownerName, String dimension,
                             int x, int y, int z, String label, int color, String iconId, String category) {
    public SharedWaypoint {
        if (id == null || ownerId == null || isZero(id) || isZero(ownerId)) throw new IllegalArgumentException("Invalid waypoint identity");
        ownerName = SharedWaypointData.validateOwnerName(ownerName);
        dimension = SharedWaypointData.validateDimension(dimension);
        SharedWaypointData.validateCoordinates(x, y, z);
        label = SharedWaypointData.validateLabel(label);
        iconId = SharedWaypointData.validateIcon(iconId);
        category = SharedWaypointData.validateCategory(category);
    }

    private static boolean isZero(UUID value) { return value.getMostSignificantBits() == 0L && value.getLeastSignificantBits() == 0L; }
}
