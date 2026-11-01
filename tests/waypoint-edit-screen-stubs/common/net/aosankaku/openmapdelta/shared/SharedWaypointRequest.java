package net.aosankaku.openmapdelta.shared;

public record SharedWaypointRequest(String dimension, int x, int y, int z, String name, int color, String icon, String category) {
    public static SharedWaypointRequest create(String dimension, int x, int y, int z, String name, int color, String icon, String category) {
        return new SharedWaypointRequest(dimension, x, y, z, name, color, icon, category);
    }
}
