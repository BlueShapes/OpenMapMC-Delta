// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.compat;

/** A client-synchronized waypoint at the center of Create station blocks or MTR platforms. */
public record TransitStation(Source source, Object dimension, String waypointIdentity, String name, Position position) {
    public enum Source { CREATE, MTR }
    public record Position(int x, int y, int z) implements Comparable<Position> {
        @Override public int compareTo(Position other) {
            int xOrder = Integer.compare(x, other.x);
            int zOrder = Integer.compare(z, other.z);
            return xOrder != 0 ? xOrder : zOrder != 0 ? zOrder : Integer.compare(y, other.y);
        }
    }
}
