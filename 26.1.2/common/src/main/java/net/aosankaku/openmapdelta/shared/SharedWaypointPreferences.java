// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.shared;

import net.aosankaku.openmapdelta.platform.DataPaths;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.aosankaku.openmapdelta.marker.WaypointFileStore;
import net.aosankaku.openmapdelta.platform.Services;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Per-client visibility and favorite preferences for server-owned waypoints. */
public final class SharedWaypointPreferences {
    private static final Logger LOG = LoggerFactory.getLogger("OpenMapDelta");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final int MAX_WAYPOINTS_PER_SCOPE = 4096;
    private static final int MAX_CACHED_SCOPES = 16;
    private static final long MAX_FILE_BYTES = 4L * 1024 * 1024;
    /** Access-ordered bounded cache; all mutations are persisted before returning. */
    private static final LinkedHashMap<Scope, ScopeData> CACHE = new LinkedHashMap<>(16, 0.75f, true);

    private record Scope(UUID worldId, UUID playerId) {}

    private static final class ScopeData {
        Map<String, Preference> waypoints = new LinkedHashMap<>();
        boolean loadFailed;
    }

    private static final class Preference {
        boolean visible = true;
        boolean favorite;
    }

    private SharedWaypointPreferences() {}

    public static synchronized boolean isVisible(UUID worldId, UUID playerId, UUID waypointId) {
        Preference preference = find(worldId, playerId, waypointId);
        return preference == null || preference.visible;
    }

    public static synchronized boolean isFavorite(UUID worldId, UUID playerId, UUID waypointId) {
        Preference preference = find(worldId, playerId, waypointId);
        return preference != null && preference.favorite;
    }

    public static synchronized void setVisible(UUID worldId, UUID playerId, UUID waypointId, boolean visible) {
        ScopeData scope = getScope(worldId, playerId);
        if (scope == null || scope.loadFailed || waypointId == null) return;
        Preference preference = scope.waypoints.get(waypointId.toString());
        if (preference == null && visible) return;
        if (preference == null) preference = getOrCreate(scope, waypointId);
        if (preference == null || preference.visible == visible) return;
        preference.visible = visible;
        save(worldId, playerId, scope);
    }

    /** Batch list/category visibility changes into a single atomic replacement of this scope's file. */
    public static synchronized void setVisible(UUID worldId, UUID playerId,
                                               Collection<UUID> waypointIds, boolean visible) {
        ScopeData scope = getScope(worldId, playerId);
        if (scope == null || scope.loadFailed || waypointIds == null || waypointIds.isEmpty()) return;
        boolean changed = false;
        Set<UUID> distinct = new HashSet<>();
        for (UUID waypointId : waypointIds) {
            if (waypointId == null || !distinct.add(waypointId)) continue;
            Preference preference = scope.waypoints.get(waypointId.toString());
            if (preference == null && visible) continue;
            if (preference == null) preference = getOrCreate(scope, waypointId);
            if (preference != null && preference.visible != visible) {
                preference.visible = visible;
                changed = true;
            }
        }
        if (changed) save(worldId, playerId, scope);
    }

    public static synchronized void setFavorite(UUID worldId, UUID playerId, UUID waypointId, boolean favorite) {
        ScopeData scope = getScope(worldId, playerId);
        if (scope == null || scope.loadFailed || waypointId == null) return;
        Preference preference = scope.waypoints.get(waypointId.toString());
        if (preference == null && !favorite) return;
        if (preference == null) preference = getOrCreate(scope, waypointId);
        if (preference == null || preference.favorite == favorite) return;
        preference.favorite = favorite;
        save(worldId, playerId, scope);
    }

    /** Drop preferences for server waypoints that no longer exist in the authoritative snapshot. */
    public static synchronized void retain(UUID worldId, UUID playerId, Set<UUID> waypointIds) {
        ScopeData scope = getScope(worldId, playerId);
        if (scope == null || scope.loadFailed || waypointIds == null) return;
        boolean changed = scope.waypoints.keySet().removeIf(key -> {
            try { return !waypointIds.contains(UUID.fromString(key)); }
            catch (IllegalArgumentException malformed) { return true; }
        });
        if (changed) save(worldId, playerId, scope);
    }

    private static Preference find(UUID worldId, UUID playerId, UUID waypointId) {
        if (waypointId == null) return null;
        ScopeData scope = getScope(worldId, playerId);
        return scope == null ? null : scope.waypoints.get(waypointId.toString());
    }

    private static Preference getOrCreate(ScopeData scope, UUID waypointId) {
        String key = waypointId.toString();
        Preference preference = scope.waypoints.get(key);
        if (preference == null && scope.waypoints.size() < MAX_WAYPOINTS_PER_SCOPE) {
            preference = new Preference();
            scope.waypoints.put(key, preference);
        }
        return preference;
    }

    private static ScopeData getScope(UUID worldId, UUID playerId) {
        if (worldId == null || playerId == null) return null;
        Scope key = new Scope(worldId, playerId);
        ScopeData cached = CACHE.get(key);
        if (cached != null) return cached;
        ScopeData loaded = load(key);
        CACHE.put(key, loaded);
        while (CACHE.size() > MAX_CACHED_SCOPES) {
            Scope eldest = CACHE.keySet().iterator().next();
            CACHE.remove(eldest);
        }
        return loaded;
    }

    private static ScopeData load(Scope scope) {
        ScopeData empty = new ScopeData();
        Path path = getPath(scope);
        if (!Files.exists(path)) return empty;
        try {
            if (Files.size(path) > MAX_FILE_BYTES) throw new IOException("Preference file exceeds size limit");
            try (var reader = Files.newBufferedReader(path)) {
                ScopeData loaded = GSON.fromJson(reader, ScopeData.class);
                if (loaded == null || loaded.waypoints == null
                        || loaded.waypoints.size() > MAX_WAYPOINTS_PER_SCOPE) {
                    throw new IllegalArgumentException("Invalid or oversized preference file");
                }
                for (var entry : loaded.waypoints.entrySet()) {
                    if (!isUuid(entry.getKey()) || entry.getValue() == null) {
                        throw new IllegalArgumentException("Invalid waypoint preference entry");
                    }
                }
                return loaded;
            }
        } catch (IOException | RuntimeException e) {
            empty.loadFailed = true;
            LOG.warn("Could not load shared waypoint preferences; preserving the existing file", e);
            return empty;
        }
    }

    private static boolean isUuid(String value) {
        try { UUID.fromString(value); return true; }
        catch (IllegalArgumentException e) { return false; }
    }

    private static void save(UUID worldId, UUID playerId, ScopeData scope) {
        if (scope.loadFailed) return;
        try {
            WaypointFileStore.write(getPath(new Scope(worldId, playerId)), writer -> GSON.toJson(scope, writer));
        } catch (IOException | RuntimeException e) {
            LOG.warn("Could not save shared waypoint preferences", e);
        }
    }

    private static Path getPath(Scope scope) {
        return DataPaths.configDirectory()
                .resolve("shared-waypoint-preferences").resolve(scope.worldId().toString())
                .resolve(scope.playerId().toString() + ".json");
    }
}
