// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.marker;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/** Shared list and deletion rules for user-editable and generated waypoint sources. */
public final class WaypointListPolicy {
    private WaypointListPolicy() {}

    public static List<Marker> list(Collection<Marker> manual, Collection<Marker> temporary,
                                    Collection<Marker> transit, ResourceKey<Level> dimension) {
        List<Marker> result = new ArrayList<>();
        addMatching(result, manual, MarkerType.WAYPOINT, dimension);
        addMatching(result, manual, MarkerType.DEATH, dimension);
        addMatching(result, manual, MarkerType.BED, dimension);
        addMatching(result, temporary, MarkerType.TEMPORARY_WAYPOINT, dimension);
        addMatching(result, transit, MarkerType.TRANSIT_STATION, dimension);
        return List.copyOf(result);
    }

    /** Generated stations and session-only notices never become deletable user waypoints. */
    public static boolean canRemove(MarkerType type) {
        return type != null && type.isEditableWaypoint();
    }

    /** Replaces the current world's bed waypoint while retaining its user-edited metadata. */
    public static Marker upsertBedForWorld(List<Marker> waypoints, String world, Marker candidate) {
        if (waypoints == null || world == null || world.isBlank() || candidate == null
                || candidate.getType() != MarkerType.BED) return null;

        Marker existingBed = null;
        for (Marker marker : List.copyOf(waypoints)) {
            if (marker == null || marker.getType() != MarkerType.BED
                    || !world.equals(marker.getImportedWorld())) continue;
            if (existingBed == null) existingBed = marker;
            else waypoints.remove(marker);
        }
        if (existingBed == null) {
            candidate.setImportedWorld(world);
            waypoints.add(candidate);
            return candidate;
        }

        existingBed.setDimension(candidate.getDimension());
        existingBed.setX(candidate.getX());
        existingBed.setY(candidate.getY());
        existingBed.setZ(candidate.getZ());
        return existingBed;
    }

    private static void addMatching(List<Marker> output, Collection<Marker> source,
                                    MarkerType expectedType, ResourceKey<Level> dimension) {
        if (source == null) return;
        for (Marker marker : source) {
            if (marker != null && marker.getType() == expectedType
                    && marker.getDimension().equals(dimension)) output.add(marker);
        }
    }
}
