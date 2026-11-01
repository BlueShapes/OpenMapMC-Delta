package net.aosankaku.openmapdelta.marker;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.UUID;

public final class MarkerManager {
    private static final MarkerManager INSTANCE = new MarkerManager();
    public static MarkerManager get() { return INSTANCE; }
    public Marker getWaypoint(UUID id) { return null; }
    public boolean saveSharedWaypoint(UUID id, ResourceKey<Level> dimension, int x, int y, int z,
                                      String name, int color, String icon, String category) { return true; }
    public Marker updateWaypoint(UUID id, String name, int color, int x, int y, int z) { return null; }
    public Marker addWaypoint(ResourceKey<Level> dimension, int x, int y, int z, String name, int color) { return null; }
    public void saveWaypointAppearance(Marker marker, String icon, String category) {}
}
