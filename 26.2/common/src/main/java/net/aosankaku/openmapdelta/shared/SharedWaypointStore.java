// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.shared;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.aosankaku.openmapdelta.marker.WaypointFileStore;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

/** Strict bounded JSON storage. Writes replace the world file atomically via the existing backup-safe store. */
public final class SharedWaypointStore {
    public static final int MAX_FILE_BYTES = 16 * 1024 * 1024;
    private static final int VERSION = 1;

    public record Stored(long revision, List<SharedWaypoint> waypoints) {
        public Stored {
            if (revision < 0 || waypoints.size() > SharedWaypointSnapshot.MAX_WAYPOINTS) throw new IllegalArgumentException("Invalid shared waypoint store");
            waypoints = List.copyOf(waypoints);
        }
    }
    public record LoadResult(boolean exists, Stored stored, IOException failure) {
        public boolean valid() { return failure == null; }
    }

    private SharedWaypointStore() {}

    public static LoadResult load(Path path) {
        if (Files.notExists(path)) return new LoadResult(false, new Stored(0, List.of()), null);
        try {
            // exists() can return false for both absence and access errors; ambiguity must fail closed.
            if (!Files.exists(path)) throw new IOException("Shared waypoint store could not be inspected");
            byte[] bytes;
            try (var input = Files.newInputStream(path)) { bytes = input.readNBytes(MAX_FILE_BYTES + 1); }
            if (bytes.length > MAX_FILE_BYTES) throw new IOException("Shared waypoint store exceeds size limit");
            String json;
            try {
                json = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
            } catch (CharacterCodingException malformed) { throw new IOException("Invalid store encoding", malformed); }
            JsonElement rootValue = JsonParser.parseString(json);
            if (!rootValue.isJsonObject()) throw new IOException("Invalid store root");
            JsonObject root = rootValue.getAsJsonObject();
            if (!root.has("version") || exactInteger(root, "version") != VERSION
                    || !root.has("revision") || !root.has("waypoints") || !root.get("waypoints").isJsonArray()) {
                throw new IOException("Unsupported shared waypoint store");
            }
            long revision;
            try { revision = root.getAsJsonPrimitive("revision").getAsBigDecimal().longValueExact(); }
            catch (RuntimeException malformed) { throw new IOException("Invalid shared waypoint revision", malformed); }
            if (revision < 0) throw new IOException("Invalid shared waypoint revision");
            JsonArray array = root.getAsJsonArray("waypoints");
            if (array.size() > SharedWaypointSnapshot.MAX_WAYPOINTS) throw new IOException("Too many shared waypoints");
            var waypoints = new ArrayList<SharedWaypoint>(array.size());
            var ids = new HashSet<UUID>();
            for (JsonElement item : array) {
                if (!item.isJsonObject()) throw new IOException("Invalid shared waypoint entry");
                JsonObject value = item.getAsJsonObject();
                SharedWaypoint waypoint = new SharedWaypoint(
                        UUID.fromString(string(value, "id")), UUID.fromString(string(value, "ownerId")),
                        string(value, "ownerName"), string(value, "dimension"), integer(value, "x"),
                        integer(value, "y"), integer(value, "z"), string(value, "label"), integer(value, "color"),
                        string(value, "iconId"), string(value, "category"));
                if (!ids.add(waypoint.id())) throw new IOException("Duplicate shared waypoint id");
                waypoints.add(waypoint);
            }
            return new LoadResult(true, new Stored(revision, waypoints), null);
        } catch (IOException failure) {
            return new LoadResult(true, null, failure);
        } catch (RuntimeException malformed) {
            return new LoadResult(true, null, new IOException("Malformed shared waypoint store", malformed));
        }
    }

    public static void save(Path path, Stored stored) throws IOException {
        if (stored == null || stored.waypoints().size() > SharedWaypointSnapshot.MAX_WAYPOINTS) throw new IOException("Invalid shared waypoint state");
        var ids = new HashSet<UUID>();
        JsonObject root = new JsonObject();
        root.addProperty("version", VERSION);
        root.addProperty("revision", stored.revision());
        JsonArray array = new JsonArray();
        for (SharedWaypoint waypoint : stored.waypoints()) {
            if (!ids.add(waypoint.id())) throw new IOException("Duplicate shared waypoint id");
            JsonObject value = new JsonObject();
            value.addProperty("id", waypoint.id().toString());
            value.addProperty("ownerId", waypoint.ownerId().toString());
            value.addProperty("ownerName", waypoint.ownerName());
            value.addProperty("dimension", waypoint.dimension());
            value.addProperty("x", waypoint.x()); value.addProperty("y", waypoint.y()); value.addProperty("z", waypoint.z());
            value.addProperty("label", waypoint.label()); value.addProperty("color", waypoint.color());
            value.addProperty("iconId", waypoint.iconId()); value.addProperty("category", waypoint.category());
            array.add(value);
        }
        root.add("waypoints", array);
        String json = new com.google.gson.GsonBuilder().disableHtmlEscaping().create().toJson(root);
        if (json.getBytes(StandardCharsets.UTF_8).length > MAX_FILE_BYTES) throw new IOException("Shared waypoint store exceeds size limit");
        WaypointFileStore.write(path, writer -> writer.write(json));
    }

    private static String string(JsonObject object, String key) throws IOException {
        if (!object.has(key) || !object.get(key).isJsonPrimitive() || !object.getAsJsonPrimitive(key).isString()) throw new IOException("Invalid shared waypoint field");
        return object.get(key).getAsString();
    }
    private static int integer(JsonObject object, String key) throws IOException {
        if (!object.has(key) || !object.get(key).isJsonPrimitive() || !object.getAsJsonPrimitive(key).isNumber()) throw new IOException("Invalid shared waypoint number");
        return exactInteger(object, key);
    }
    private static int exactInteger(JsonObject object, String key) throws IOException {
        if (!object.has(key) || !object.get(key).isJsonPrimitive() || !object.getAsJsonPrimitive(key).isNumber()) throw new IOException("Invalid shared waypoint number");
        try { return object.getAsJsonPrimitive(key).getAsBigDecimal().intValueExact(); }
        catch (RuntimeException malformed) { throw new IOException("Invalid shared waypoint number", malformed); }
    }
}
