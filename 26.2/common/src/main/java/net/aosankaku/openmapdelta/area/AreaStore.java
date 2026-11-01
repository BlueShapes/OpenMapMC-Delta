// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.area;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import net.aosankaku.openmapdelta.marker.WaypointFileStore;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Strict bounded v1 JSON store. Identity/schema failures preserve the source for recovery. */
public final class AreaStore {
    public static final String FILE_NAME = "openmapdelta-areas.json";
    public static final int MAX_FILE_BYTES = 8 * 1024 * 1024;
    public static final int VERSION = 1;
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().serializeNulls().create();
    private static final Set<String> ROOT_KEYS = Set.of("version", "identity", "revision", "areas");
    private static final Set<String> AREA_KEYS = Set.of("id", "dimension", "name", "kind", "scope", "ownerId",
            "ownerName", "parentId", "revision", "geometry", "label", "style");
    private static final Set<String> GEOMETRY_KEYS = Set.of("shape", "paths", "polygons");
    private static final Set<String> POLYGON_KEYS = Set.of("outer", "holes");
    private static final Set<String> POINT_KEYS = Set.of("x", "z");
    private static final Set<String> STYLE_KEYS = Set.of("color", "visible", "labels", "border", "fill", "minBpp", "maxBpp", "priority");

    public record LoadResult(boolean exists, AreaDocument document, IOException failure) {
        public boolean valid() { return failure == null && document != null; }
    }

    private AreaStore() {}

    public static LoadResult load(Path path, String expectedIdentity) {
        Path file;
        try {
            file = checkedPath(path);
            validateIdentity(expectedIdentity);
            if (Files.notExists(file, LinkOption.NOFOLLOW_LINKS)) return new LoadResult(false, AreaDocument.empty(), null);
            if (!Files.exists(file, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(file)
                    || !Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
                throw new IOException("Area store path cannot be read as a regular file");
            }
            long size = Files.size(file);
            if (size > MAX_FILE_BYTES) throw new IOException("Area store exceeds its size limit");
            byte[] bytes;
            try (InputStream input = Files.newInputStream(file, LinkOption.NOFOLLOW_LINKS)) {
                bytes = input.readNBytes(MAX_FILE_BYTES + 1);
            }
            if (bytes.length > MAX_FILE_BYTES) throw new IOException("Area store exceeds its size limit");
            String json;
            try {
                json = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
            } catch (CharacterCodingException malformed) {
                throw new IOException("Invalid UTF-8 in area store", malformed);
            }
            return new LoadResult(true, parseDocument(json, expectedIdentity), null);
        } catch (IOException failure) {
            return new LoadResult(path != null && Files.exists(path, LinkOption.NOFOLLOW_LINKS), null, failure);
        } catch (RuntimeException malformed) {
            return new LoadResult(path != null && Files.exists(path, LinkOption.NOFOLLOW_LINKS), null,
                    new IOException("Malformed area store", malformed));
        }
    }

    public static void save(Path path, String identity, AreaDocument document) throws IOException {
        Path file = checkedPath(path);
        validateIdentity(identity);
        if (document == null) throw new IOException("Area document is required");

        LoadResult previous = load(file, identity);
        if (!previous.valid()) {
            if (previous.exists()) throw new IOException("Existing area data is unreadable or belongs to another world; it was preserved", previous.failure());
            // Missing files have a valid empty result. An inaccessible path must still fail closed.
            if (previous.failure() != null) throw new IOException("Area store cannot be inspected", previous.failure());
        }
        String json = serializeDocument(identity, document);
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_FILE_BYTES) throw new IOException("Area store exceeds its size limit");
        WaypointFileStore.write(file, writer -> writer.write(json));
    }

    /** Strict single-feature conversion for transport adapters. */
    public static JsonObject toJson(MapArea area) {
        if (area == null) throw new IllegalArgumentException("Area is required");
        JsonObject value = new JsonObject();
        value.addProperty("id", area.id().toString());
        value.addProperty("dimension", area.dimension());
        value.addProperty("name", area.name());
        value.addProperty("kind", area.kind().name());
        value.addProperty("scope", area.scope().name());
        value.addProperty("ownerId", area.ownerId().toString());
        value.addProperty("ownerName", area.ownerName());
        if (area.parentId() == null) value.add("parentId", com.google.gson.JsonNull.INSTANCE);
        else value.addProperty("parentId", area.parentId().toString());
        value.addProperty("revision", area.revision());
        value.add("geometry", geometryToJson(area.geometry()));
        value.add("label", pointToJson(area.label()));
        value.add("style", styleToJson(area.style()));
        return value;
    }

    /** Strict single-feature parser. Document-level parent validation is performed by AreaDocument. */
    public static MapArea fromJson(JsonObject value) throws IOException {
        requireKeys(value, AREA_KEYS, "area");
        UUID id = uuid(string(value, "id"), "area id");
        String dimension = string(value, "dimension");
        String name = string(value, "name");
        MapArea.Kind kind = enumValue(string(value, "kind"), MapArea.Kind.class, "area kind");
        MapArea.Scope scope = enumValue(string(value, "scope"), MapArea.Scope.class, "area scope");
        UUID ownerId = uuid(string(value, "ownerId"), "area owner");
        String ownerName = string(value, "ownerName");
        UUID parentId = null;
        if (!value.get("parentId").isJsonNull()) parentId = uuid(primitiveString(value.get("parentId"), "parentId"), "area parent");
        long revision = exactLong(value, "revision");
        MapArea.Geometry geometry = geometryFromJson(object(value, "geometry"));
        MapArea.Point label = pointFromJson(object(value, "label"));
        MapArea.Style style = styleFromJson(object(value, "style"));
        return new MapArea(id, dimension, name, kind, scope, ownerId, ownerName, parentId, revision, geometry, label, style);
    }

    private static AreaDocument parseDocument(String json, String expectedIdentity) throws IOException {
        try {
            JsonElement rootValue;
            try (JsonReader reader = new JsonReader(new InputStreamReader(
                    new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)), StandardCharsets.UTF_8))) {
                reader.setLenient(false);
                rootValue = JsonParser.parseReader(reader);
                if (reader.peek() != JsonToken.END_DOCUMENT) throw new IOException("Trailing content in area store");
            }
            if (!rootValue.isJsonObject()) throw new IOException("Invalid area store root");
            JsonObject root = rootValue.getAsJsonObject();
            requireKeys(root, ROOT_KEYS, "area store");
            if (exactInt(root, "version") != VERSION) throw new IOException("Unsupported area store schema");
            if (!string(root, "identity").equals(expectedIdentity)) throw new IOException("Area store identity does not match the current world");
            long revision = exactLong(root, "revision");
            JsonArray entries = array(root, "areas");
            if (entries.size() > AreaDocument.MAX_AREAS) throw new IOException("Too many areas in store");
            ArrayList<MapArea> areas = new ArrayList<>(entries.size());
            for (JsonElement item : entries) {
                if (!item.isJsonObject()) throw new IOException("Invalid area entry");
                areas.add(fromJson(item.getAsJsonObject()));
            }
            return new AreaDocument(revision, areas);
        } catch (IOException failure) {
            throw failure;
        } catch (RuntimeException malformed) {
            throw new IOException("Malformed area store", malformed);
        }
    }

    private static String serializeDocument(String identity, AreaDocument document) {
        JsonObject root = new JsonObject();
        root.addProperty("version", VERSION);
        root.addProperty("identity", identity);
        root.addProperty("revision", document.revision());
        JsonArray areas = new JsonArray();
        for (MapArea area : document.areas()) areas.add(toJson(area));
        root.add("areas", areas);
        return GSON.toJson(root);
    }

    private static JsonObject geometryToJson(MapArea.Geometry geometry) {
        JsonObject result = new JsonObject();
        result.addProperty("shape", geometry.shape().name());
        JsonArray paths = new JsonArray();
        for (List<MapArea.Point> path : geometry.paths()) paths.add(pointsToJson(path));
        result.add("paths", paths);
        JsonArray polygons = new JsonArray();
        for (MapArea.Polygon polygon : geometry.polygons()) {
            JsonObject component = new JsonObject();
            component.add("outer", pointsToJson(polygon.outer()));
            JsonArray holes = new JsonArray();
            for (List<MapArea.Point> hole : polygon.holes()) holes.add(pointsToJson(hole));
            component.add("holes", holes);
            polygons.add(component);
        }
        result.add("polygons", polygons);
        return result;
    }

    private static MapArea.Geometry geometryFromJson(JsonObject value) throws IOException {
        requireKeys(value, GEOMETRY_KEYS, "geometry");
        MapArea.Shape shape = enumValue(string(value, "shape"), MapArea.Shape.class, "geometry shape");
        ArrayList<List<MapArea.Point>> paths = new ArrayList<>();
        JsonArray pathEntries = array(value, "paths");
        if (pathEntries.size() > AreaDocument.MAX_PARTS) throw new IOException("Too many area paths");
        for (JsonElement entry : pathEntries) paths.add(pointsFromJson(entry));
        ArrayList<MapArea.Polygon> polygons = new ArrayList<>();
        JsonArray polygonEntries = array(value, "polygons");
        if (polygonEntries.size() > AreaDocument.MAX_PARTS) throw new IOException("Too many area polygons");
        for (JsonElement entry : polygonEntries) {
            if (!entry.isJsonObject()) throw new IOException("Invalid area polygon");
            JsonObject polygon = entry.getAsJsonObject();
            requireKeys(polygon, POLYGON_KEYS, "polygon");
            List<MapArea.Point> outer = pointsFromJson(polygon.get("outer"));
            JsonArray holes = array(polygon, "holes");
            if (holes.size() > AreaDocument.MAX_PARTS) throw new IOException("Too many holes in area polygon");
            ArrayList<List<MapArea.Point>> decodedHoles = new ArrayList<>(holes.size());
            for (JsonElement hole : holes) decodedHoles.add(pointsFromJson(hole));
            polygons.add(new MapArea.Polygon(outer, decodedHoles));
        }
        return new MapArea.Geometry(shape, paths, polygons);
    }

    private static JsonArray pointsToJson(List<MapArea.Point> points) {
        JsonArray result = new JsonArray();
        for (MapArea.Point point : points) {
            JsonObject value = new JsonObject();
            value.addProperty("x", point.x());
            value.addProperty("z", point.z());
            result.add(value);
        }
        return result;
    }

    private static List<MapArea.Point> pointsFromJson(JsonElement value) throws IOException {
        if (!value.isJsonArray()) throw new IOException("Expected an array of area points");
        JsonArray points = value.getAsJsonArray();
        if (points.size() > AreaDocument.MAX_POINTS_PER_AREA) throw new IOException("Too many points in area geometry");
        ArrayList<MapArea.Point> result = new ArrayList<>(points.size());
        for (JsonElement element : points) {
            if (!element.isJsonObject()) throw new IOException("Invalid area point");
            JsonObject point = element.getAsJsonObject();
            requireKeys(point, POINT_KEYS, "point");
            result.add(new MapArea.Point(number(point, "x"), number(point, "z")));
        }
        return List.copyOf(result);
    }

    private static JsonObject styleToJson(MapArea.Style style) {
        JsonObject value = new JsonObject();
        value.addProperty("color", style.color());
        value.addProperty("visible", style.visible());
        value.addProperty("labels", style.labels());
        value.addProperty("border", style.border());
        value.addProperty("fill", style.fill());
        value.addProperty("minBpp", style.minBpp());
        value.addProperty("maxBpp", style.maxBpp());
        value.addProperty("priority", style.priority());
        return value;
    }

    private static MapArea.Style styleFromJson(JsonObject value) throws IOException {
        requireKeys(value, STYLE_KEYS, "area style");
        return new MapArea.Style(exactInt(value, "color"), bool(value, "visible"), bool(value, "labels"),
                bool(value, "border"), bool(value, "fill"), number(value, "minBpp"), number(value, "maxBpp"), exactInt(value, "priority"));
    }

    private static JsonObject pointToJson(MapArea.Point point) {
        JsonObject value = new JsonObject();
        value.addProperty("x", point.x());
        value.addProperty("z", point.z());
        return value;
    }

    private static MapArea.Point pointFromJson(JsonObject value) throws IOException {
        requireKeys(value, POINT_KEYS, "label point");
        return new MapArea.Point(number(value, "x"), number(value, "z"));
    }

    private static void requireKeys(JsonObject value, Set<String> expected, String name) throws IOException {
        if (value == null || value.size() != expected.size() || !value.keySet().equals(expected)) throw new IOException("Invalid " + name + " fields");
    }

    private static JsonObject object(JsonObject source, String key) throws IOException {
        if (!source.has(key) || !source.get(key).isJsonObject()) throw new IOException("Invalid area " + key);
        return source.getAsJsonObject(key);
    }

    private static JsonArray array(JsonObject source, String key) throws IOException {
        if (!source.has(key) || !source.get(key).isJsonArray()) throw new IOException("Invalid area " + key);
        return source.getAsJsonArray(key);
    }

    private static String string(JsonObject source, String key) throws IOException {
        if (!source.has(key)) throw new IOException("Missing area " + key);
        return primitiveString(source.get(key), key);
    }

    private static String primitiveString(JsonElement value, String field) throws IOException {
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) throw new IOException("Invalid area " + field);
        return value.getAsString();
    }

    private static int exactInt(JsonObject source, String key) throws IOException {
        long value = exactLong(source, key);
        if (value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) throw new IOException("Area integer is outside supported range");
        return (int) value;
    }

    private static long exactLong(JsonObject source, String key) throws IOException {
        JsonElement value = source.get(key);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) throw new IOException("Invalid area " + key);
        try { return value.getAsBigDecimal().longValueExact(); }
        catch (RuntimeException invalid) { throw new IOException("Invalid area " + key, invalid); }
    }

    private static double number(JsonObject source, String key) throws IOException {
        JsonElement value = source.get(key);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) throw new IOException("Invalid area " + key);
        try {
            double parsed = value.getAsDouble();
            if (!Double.isFinite(parsed)) throw new IOException("Nonfinite area " + key);
            return parsed;
        } catch (NumberFormatException invalid) {
            throw new IOException("Invalid area " + key, invalid);
        }
    }

    private static boolean bool(JsonObject source, String key) throws IOException {
        JsonElement value = source.get(key);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean()) throw new IOException("Invalid area " + key);
        return value.getAsBoolean();
    }

    private static <T extends Enum<T>> T enumValue(String value, Class<T> enumType, String field) throws IOException {
        try { return Enum.valueOf(enumType, value); }
        catch (IllegalArgumentException invalid) { throw new IOException("Invalid " + field, invalid); }
    }

    private static UUID uuid(String value, String field) throws IOException {
        try { return UUID.fromString(value); }
        catch (IllegalArgumentException invalid) { throw new IOException("Invalid " + field, invalid); }
    }

    private static Path checkedPath(Path path) throws IOException {
        if (path == null || path.getFileName() == null) throw new IOException("Invalid area store path");
        Path file = path.toAbsolutePath().normalize();
        if (file.equals(file.getRoot())) throw new IOException("Invalid area store target");
        return file;
    }

    private static void validateIdentity(String identity) throws IOException {
        if (identity == null || identity.isBlank() || identity.length() > 1024
                || identity.codePoints().anyMatch(Character::isISOControl)) throw new IOException("Invalid area store identity");
    }
}
