package net.aosankaku.openmapdelta.marker;

public enum MarkerType {
    WAYPOINT(0xFF55FF55), SHARED_WAYPOINT(0xFF55FFFF), TRANSIT_STATION(0xFF55CFFF), DEATH(0xFFFF0000);
    public final int defaultColor;
    MarkerType(int defaultColor) { this.defaultColor = defaultColor; }
}
