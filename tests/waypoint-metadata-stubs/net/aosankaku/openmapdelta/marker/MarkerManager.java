// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.marker;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class MarkerManager {
    private static final MarkerManager INSTANCE = new MarkerManager();
    private final List<Marker> waypoints = new ArrayList<>();
    private MarkerManager() {}
    public static MarkerManager get() { return INSTANCE; }
    public List<Marker> getAllWaypoints() { return Collections.unmodifiableList(waypoints); }
    public void clearWaypoints() { waypoints.clear(); }
    public void addWaypointDirect(Marker marker) { waypoints.add(marker); }
}
