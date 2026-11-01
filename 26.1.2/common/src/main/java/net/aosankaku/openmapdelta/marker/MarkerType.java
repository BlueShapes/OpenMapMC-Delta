// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.marker;

public enum MarkerType {
    PLAYER(0xFF55FF55),
    WAYPOINT(0xFFFFFF55),
    TEMPORARY_WAYPOINT(0xFF55FFFF),
    LOCATION_NOTICE(0xFFFFAA00),
    NAVIGATION(0xFF55FFFF),
    NAVIGATION_POINT(0xFF55FFFF), // Session-only coordinate destination rendered as a world marker.
    DEATH(0xFFFF5555),
    BED(0xFF5555FF),
    PORTAL(0xFFAA55FF),
    STRUCTURE(0xFFFFAA00),
    ENTITY(0xFFAAAAAA),
    TRANSIT_STATION(0xFF55CFFF),
    SHARED_WAYPOINT(0xFF55FFFF),
    CUSTOM(0xFFFFFFFF);

    /** Navigation actions can target map-only destinations and ephemeral coordinate points. */
    public boolean isMapSelectable() {
        return isWorldWaypoint() || this == PLAYER || this == NAVIGATION;
    }

    public boolean isEditableWaypoint() {
        return this == WAYPOINT || this == TEMPORARY_WAYPOINT || this == BED;
    }

    public boolean isWorldWaypoint() {
        return isEditableWaypoint() || this == LOCATION_NOTICE || this == TRANSIT_STATION
                || this == SHARED_WAYPOINT || this == NAVIGATION_POINT;
    }

    public final int defaultColor;

    MarkerType(int defaultColor) {
        this.defaultColor = defaultColor;
    }
}
