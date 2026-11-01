// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.shared;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Pure bounded page assembler; partial or malformed snapshots are never exposed to renderers. */
public final class SharedWaypointSnapshotAssembler {
    public record Complete(UUID worldId, long revision, boolean multiplayer, boolean administrator,
                           List<SharedWaypoint> waypoints) {
        public Complete { waypoints = List.copyOf(waypoints); }
    }

    private static final class Pending {
        final SharedWaypointSnapshot first;
        final List<SharedWaypoint>[] pages;
        int waypointCount;

        @SuppressWarnings("unchecked")
        Pending(SharedWaypointSnapshot first) {
            this.first = first;
            pages = (List<SharedWaypoint>[]) new List<?>[first.pageCount()];
        }

        boolean matches(SharedWaypointSnapshot snapshot) {
            return first.worldId().equals(snapshot.worldId()) && first.revision() == snapshot.revision()
                    && first.multiplayer() == snapshot.multiplayer()
                    && first.administrator() == snapshot.administrator()
                    && pages.length == snapshot.pageCount();
        }
    }

    private Pending pending;

    public boolean isOlderThanPending(SharedWaypointSnapshot snapshot) {
        return pending != null && pending.first.worldId().equals(snapshot.worldId())
                && snapshot.revision() < pending.first.revision();
    }

    /** Returns a complete immutable snapshot only after every page passes duplicate and size checks. */
    public Optional<Complete> accept(SharedWaypointSnapshot snapshot) {
        if (snapshot == null || isOlderThanPending(snapshot)) return Optional.empty();
        if (pending == null || !pending.matches(snapshot)) pending = new Pending(snapshot);
        int pageIndex = snapshot.pageIndex();
        if (pending.pages[pageIndex] == null) {
            if (pending.waypointCount + snapshot.waypoints().size() > SharedWaypointSnapshot.MAX_WAYPOINTS) {
                pending = null;
                return Optional.empty();
            }
            List<SharedWaypoint> page = List.copyOf(snapshot.waypoints());
            pending.pages[pageIndex] = page;
            pending.waypointCount += page.size();
        }
        for (List<SharedWaypoint> page : pending.pages) if (page == null) return Optional.empty();

        List<SharedWaypoint> complete = new ArrayList<>(pending.waypointCount);
        Set<UUID> ids = new HashSet<>();
        for (List<SharedWaypoint> page : pending.pages) {
            for (SharedWaypoint waypoint : page) {
                if (waypoint == null || !ids.add(waypoint.id())) {
                    pending = null;
                    return Optional.empty();
                }
                complete.add(waypoint);
            }
        }
        Pending accepted = pending;
        pending = null;
        return Optional.of(new Complete(accepted.first.worldId(), accepted.first.revision(),
                accepted.first.multiplayer(), accepted.first.administrator(), complete));
    }

    public void reset() { pending = null; }
}
