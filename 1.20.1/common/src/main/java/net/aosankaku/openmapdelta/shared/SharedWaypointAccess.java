// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.shared;

import java.util.UUID;

/** Pure authorization rule shared by server transactions and focused tests. */
public final class SharedWaypointAccess {
    private SharedWaypointAccess() {}
    public static boolean canManage(SharedWaypoint waypoint, UUID authenticatedActorId, boolean administrator) {
        return waypoint != null && authenticatedActorId != null
                && (administrator || waypoint.ownerId().equals(authenticatedActorId));
    }
}
