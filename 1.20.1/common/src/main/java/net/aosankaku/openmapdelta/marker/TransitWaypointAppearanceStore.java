// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.marker;

import net.aosankaku.openmapdelta.platform.DataPaths;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.aosankaku.openmapdelta.platform.Services;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Persists appearance-only overrides for generated transit stations, scoped to a persistent world identity. */
public final class TransitWaypointAppearanceStore {
    private static final Logger LOG = LoggerFactory.getLogger("OpenMapDelta");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private TransitWaypointAppearanceStore() {}

    public static final class Appearance {
        private Integer color;
        private Boolean visible;
        private Boolean favorite;
        private String iconId;
        private String category;

        /** Used by Gson. */
        private Appearance() {}

        public Appearance(Integer color, Boolean visible, Boolean favorite, String iconId, String category) {
            this.color = color;
            this.visible = visible;
            this.favorite = favorite;
            this.iconId = iconId == null ? null : MarkerIconId.normalize(iconId);
            this.category = category == null || category.trim().isEmpty() ? null : category.trim();
        }

        public Integer color() { return color; }
        public Boolean visible() { return visible; }
        public Boolean favorite() { return favorite; }
        public String iconId() { return iconId; }
        public String category() { return category; }
        public boolean isEmpty() {
            return color == null && visible == null && favorite == null && iconId == null && category == null;
        }

        public void applyTo(Marker marker) {
            if (color != null) marker.setColor(color);
            if (visible != null) marker.setVisible(visible);
            if (favorite != null) marker.setFavorite(favorite);
            if (iconId != null) marker.setIconId(iconId);
            if (category != null) marker.setCategory(category);
        }
    }

    public enum Field { COLOR, VISIBLE, FAVORITE, ICON, CATEGORY }

    /** Session-only overlay mask used while a persistent world identity is still unavailable. */
    public static final class Patch {
        private final java.util.EnumSet<Field> touched = java.util.EnumSet.noneOf(Field.class);
        private Appearance value = new Appearance(null, null, null, null, null);

        public void update(Appearance appearance, Field... fields) {
            if (fields == null || fields.length == 0) return;
            Appearance source = appearance == null ? new Appearance(null, null, null, null, null) : appearance;
            Integer color = value.color;
            Boolean visible = value.visible;
            Boolean favorite = value.favorite;
            String iconId = value.iconId;
            String category = value.category;
            for (Field field : fields) {
                if (field == null) continue;
                touched.add(field);
                switch (field) {
                    case COLOR -> color = source.color;
                    case VISIBLE -> visible = source.visible;
                    case FAVORITE -> favorite = source.favorite;
                    case ICON -> iconId = source.iconId;
                    case CATEGORY -> category = source.category;
                }
            }
            value = new Appearance(color, visible, favorite, iconId, category);
        }

        public boolean isEmpty() { return touched.isEmpty(); }

        public Appearance merge(Appearance base) {
            Appearance original = base == null ? new Appearance(null, null, null, null, null) : base;
            return new Appearance(
                    touched.contains(Field.COLOR) ? value.color : original.color,
                    touched.contains(Field.VISIBLE) ? value.visible : original.visible,
                    touched.contains(Field.FAVORITE) ? value.favorite : original.favorite,
                    touched.contains(Field.ICON) ? value.iconId : original.iconId,
                    touched.contains(Field.CATEGORY) ? value.category : original.category);
        }
    }

    public static Path getPath() {
        return DataPaths.configDirectory().resolve("transit-waypoint-appearance.json");
    }

    /** Returns only the overrides belonging to this world. An unknown identity has no persistent scope. */
    public static Map<UUID, Appearance> load(Path path, String worldIdentity) {
        if (worldIdentity == null || worldIdentity.isBlank() || !Files.exists(path)) return Map.of();
        try {
            StoredData data = read(path);
            Map<String, Appearance> stored = data.worlds.get(worldIdentity);
            if (stored == null || stored.isEmpty()) return Map.of();
            Map<UUID, Appearance> result = new LinkedHashMap<>();
            for (Map.Entry<String, Appearance> entry : stored.entrySet()) {
                try {
                    if (entry.getValue() != null && !entry.getValue().isEmpty()) {
                        result.put(UUID.fromString(entry.getKey()), entry.getValue());
                    }
                } catch (IllegalArgumentException ignored) {
                    // Skip entries written by a damaged or older file.
                }
            }
            return result;
        } catch (IOException | RuntimeException e) {
            LOG.error("Failed to load transit waypoint appearance overrides", e);
            return Map.of();
        }
    }

    /** Null world identities are deliberately never written to disk. */
    public static boolean save(Path path, String worldIdentity, Map<UUID, Appearance> overrides) {
        if (worldIdentity == null || worldIdentity.isBlank()) return false;
        try {
            StoredData data = Files.exists(path) ? read(path) : new StoredData();
            Map<String, Appearance> stored = new LinkedHashMap<>();
            if (overrides != null) {
                overrides.forEach((id, appearance) -> {
                    if (id != null && appearance != null && !appearance.isEmpty()) {
                        stored.put(id.toString(), appearance);
                    }
                });
            }
            if (stored.isEmpty()) data.worlds.remove(worldIdentity);
            else data.worlds.put(worldIdentity, stored);
            if (data.worlds.isEmpty()) {
                data.worlds = new LinkedHashMap<>();
            }
            WaypointFileStore.write(path, writer -> GSON.toJson(data, writer));
            return true;
        } catch (IOException | RuntimeException e) {
            LOG.error("Failed to save transit waypoint appearance overrides", e);
            return false;
        }
    }

    private static StoredData read(Path path) throws IOException {
        try (Reader reader = Files.newBufferedReader(path)) {
            StoredData data = GSON.fromJson(reader, StoredData.class);
            if (data == null || data.worlds == null) return new StoredData();
            return data;
        }
    }

    private static final class StoredData {
        Map<String, Map<String, Appearance>> worlds = new LinkedHashMap<>();
    }
}
