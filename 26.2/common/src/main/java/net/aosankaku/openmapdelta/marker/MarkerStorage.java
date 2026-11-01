// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.marker;

import net.aosankaku.openmapdelta.platform.DataPaths;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.aosankaku.openmapdelta.platform.Services;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class MarkerStorage {
    private static final Logger LOG = LoggerFactory.getLogger("OpenMapDelta");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type LIST_TYPE = new TypeToken<List<StoredMarker>>() {}.getType();

    private MarkerStorage() {}

    public static boolean save() {
        Path path = getPath();
        List<StoredMarker> stored = new ArrayList<>();
        for (Marker m : MarkerManager.get().getAllWaypoints()) {
            stored.add(StoredMarker.from(m));
        }
        try {
            WaypointFileStore.write(path, writer -> GSON.toJson(stored, writer));
            return true;
        } catch (IOException | RuntimeException e) {
            LOG.error("Failed to save waypoints", e);
            return false;
        }
    }

    public static void load() {
        Path path = getPath();
        MarkerManager.get().clearWaypoints();
        if (!Files.exists(path)) return;
        try (Reader reader = Files.newBufferedReader(path)) {
            List<StoredMarker> stored = GSON.fromJson(reader, LIST_TYPE);
            if (stored == null) return;
            for (StoredMarker s : stored) {
                Identifier loc = Identifier.tryParse(s.dimension);
                if (loc == null) continue;
                ResourceKey<net.minecraft.world.level.Level> dim = ResourceKey.create(
                        net.minecraft.core.registries.Registries.DIMENSION, loc);
                MarkerType type = "DEATH".equals(s.type) ? MarkerType.DEATH
                        : "BED".equals(s.type) ? MarkerType.BED : MarkerType.WAYPOINT;
                int color = s.color != 0 ? s.color : type.defaultColor;
                int y = s.y != null ? s.y : Marker.UNKNOWN_Y;
                Marker marker = new Marker(
                        s.id != null ? UUID.fromString(s.id) : UUID.randomUUID(),
                        type, dim, s.x, y, s.z,
                        s.label != null ? s.label : "Waypoint", color);
                if (s.visible != null) marker.setVisible(s.visible);
                if (s.favorite != null) marker.setFavorite(s.favorite);
                marker.setImportedWorld(s.importedWorld);
                if (s.iconId != null) marker.setIconId(s.iconId);
                marker.setCategory(s.category);
                if (type == MarkerType.DEATH) {
                    marker.setDeathPoint(s.deathPoint != null ? s.deathPoint
                            : new net.aosankaku.openmapdelta.death.DeathPointData(false, 0, false, false));
                    marker.setIconId(net.aosankaku.openmapdelta.death.DeathPointData.ICON);
                }
                MarkerManager.get().addWaypointDirect(marker);
            }
        } catch (IOException e) {
            LOG.error("Failed to load waypoints", e);
        }
    }

    private static Path getPath() {
        return DataPaths.configDirectory().resolve("waypoints.json");
    }

    private static class StoredMarker {
        String id;
        String dimension;
        int x;
        Integer y;
        int z;
        String label;
        int color;
        Boolean visible;
        Boolean favorite;
        String importedWorld;
        String iconId;
        String category;
        String type;
        net.aosankaku.openmapdelta.death.DeathPointData deathPoint;

        static StoredMarker from(Marker m) {
            StoredMarker s = new StoredMarker();
            s.id = m.getId().toString();
            s.type = m.getType() == MarkerType.DEATH ? "DEATH"
                    : m.getType() == MarkerType.BED ? "BED" : null;
            s.deathPoint = m.getDeathPoint();
            s.importedWorld = m.getImportedWorld();
            s.iconId = m.getIconId().isEmpty()
                    ? (m.getType() == MarkerType.BED ? "" : null) : m.getIconId();
            s.category = m.getCategory().isEmpty() ? null : m.getCategory();
            s.dimension = m.getDimension().identifier().toString();
            s.x = m.getX();
            s.y = m.hasKnownY() ? m.getY() : null;
            s.z = m.getZ();
            s.label = m.getLabel();
            s.color = m.getColor();
            s.visible = m.isVisible() ? null : false;  // only save if hidden (default is visible)
            s.favorite = m.isFavorite() ? true : null;  // only save if favorited
            return s;
        }
    }
}
