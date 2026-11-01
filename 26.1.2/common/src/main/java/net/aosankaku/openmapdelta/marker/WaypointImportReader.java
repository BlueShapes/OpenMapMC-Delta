// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.marker;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonNull;
import com.google.gson.JsonPrimitive;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;

/** Reads other minimaps' waypoint files without modifying them or loading their mods. */
public final class WaypointImportReader {
    private static final int UNKNOWN_Y = Integer.MIN_VALUE;
    private static final int[] XAERO_COLORS = {
            0x000000, 0x0000AA, 0x00AA00, 0x00AAAA,
            0xAA0000, 0xAA00AA, 0xFFAA00, 0xAAAAAA,
            0x555555, 0x5555FF, 0x55FF55, 0x55FFFF,
            0xFF0000, 0xFF55FF, 0xFFFF55, 0xFFFFFF,
            0xFF00FF, 0x9AC0CD, 0xBFFF00, 0xFF69B4, 0x8B4513
    };

    public record Waypoint(String dimension, int x, int y, int z,
                           String name, int color, boolean visible) {}

    public record Source(String modName, Path directory, List<Waypoint> waypoints, int skipped) {
        public Source {
            waypoints = List.copyOf(waypoints);
        }
    }

    private WaypointImportReader() {}

    public static List<Source> scan(Path gameDirectory) throws IOException {
        List<Source> sources = new ArrayList<>();
        Path journeyMap = gameDirectory.resolve("journeymap/data");
        for (String mode : List.of("sp", "mp")) {
            for (Path world : directories(journeyMap.resolve(mode))) {
                readJourneyMap(world, sources);
            }
        }
        for (Path root : List.of(gameDirectory.resolve("xaero/minimap"),
                gameDirectory.resolve("XaeroWaypoints"),
                gameDirectory.resolve("config/XaeroWaypoints"),
                gameDirectory.resolve("mods/XaeroWaypoints"))) {
            for (Path world : directories(root)) {
                readXaero(world, sources);
            }
        }
        return List.copyOf(sources);
    }

    private static List<Path> directories(Path root) throws IOException {
        if (!Files.isDirectory(root) || Files.isSymbolicLink(root)) return List.of();
        try (Stream<Path> paths = Files.list(root)) {
            return paths.filter(path -> Files.isDirectory(path) && !Files.isSymbolicLink(path))
                    .sorted().toList();
        }
    }

    private static void readJourneyMap(Path world, List<Source> sources) throws IOException {
        Path directory = world.resolve("waypoints");
        if (!Files.isDirectory(directory) || Files.isSymbolicLink(directory)) return;
        List<Waypoint> points = new ArrayList<>();
        int skipped = 0;
        Path nativeFile = directory.resolve("WaypointData.dat");
        if (Files.isRegularFile(nativeFile) && !Files.isSymbolicLink(nativeFile)) {
            // JourneyMap 6 replaces individual JSON files with one uncompressed NBT file.
            // Prefer the current data over obsolete JSON files/backups left during upgrades.
            try (DataInputStream input = new DataInputStream(Files.newInputStream(nativeFile))) {
                if (Files.size(nativeFile) > 16 * 1024 * 1024 || input.readUnsignedByte() != 10) {
                    throw new IOException("Invalid JourneyMap NBT root");
                }
                input.readUTF();
                JsonObject root = readNbt(input, 10, 0).getAsJsonObject();
                JsonObject waypoints = root.getAsJsonObject("waypoints");
                if (waypoints == null) throw new IOException("Missing JourneyMap waypoints");
                for (var entry : waypoints.entrySet()) {
                    try {
                        JsonObject waypoint = entry.getValue().getAsJsonObject();
                        List<Waypoint> parsed = parseJourneyMap(waypoint);
                        boolean groupVisible = groupVisible(root, waypoint);
                        for (Waypoint point : parsed) {
                            points.add(new Waypoint(point.dimension(), point.x(), point.y(), point.z(),
                                    point.name(), point.color(), point.visible() && groupVisible));
                        }
                    } catch (RuntimeException e) {
                        skipped++;
                    }
                }
            } catch (IOException | RuntimeException e) {
                skipped++;
            }
            sources.add(new Source("JourneyMap", world, points, skipped));
            return;
        }
        // Read only the waypoint directory; map tile caches can contain millions of files.
        try (Stream<Path> paths = Files.list(directory)) {
            for (Path file : paths.filter(path -> Files.isRegularFile(path)
                    && !Files.isSymbolicLink(path)
                    && path.getFileName().toString().endsWith(".json")).sorted().toList()) {
                try (Reader reader = Files.newBufferedReader(file)) {
                    points.addAll(parseJourneyMap(JsonParser.parseReader(reader).getAsJsonObject()));
                } catch (IOException | RuntimeException e) {
                    skipped++;
                }
            }
        }
        if (!points.isEmpty() || skipped != 0) {
            sources.add(new Source("JourneyMap", world, points, skipped));
        }
    }

    private static List<Waypoint> parseJourneyMap(JsonObject json) {
        JsonObject position = json.has("pos") ? json.getAsJsonObject("pos") : json;
        int x = integer(position.get("x"));
        int y = position.has("y") ? integer(position.get("y")) : UNKNOWN_Y;
        int z = integer(position.get("z"));
        String name = json.has("name") ? json.get("name").getAsString() : "Waypoint";
        int color = 0xFF000000 | (json.has("color") ? integer(json.get("color")) & 0xFFFFFF
                : (channel(json, "r") << 16) | (channel(json, "g") << 8) | channel(json, "b"));
        boolean visible = json.has("settings") ? settingsVisible(json.getAsJsonObject("settings"))
                : !json.has("enable") || bool(json.get("enable").getAsString());
        Set<String> dimensions = new LinkedHashSet<>();
        JsonElement dimension = json.has("dimensions") ? json.get("dimensions") : json.get("dimension");
        if (dimension != null && dimension.isJsonArray()) {
            JsonArray values = dimension.getAsJsonArray();
            for (JsonElement value : values) dimensions.add(normalizeDimension(value.getAsString()));
        } else if (dimension != null && !dimension.isJsonNull()) {
            dimensions.add(normalizeDimension(dimension.getAsString()));
        }
        String primary = position.has("dimension")
                ? normalizeDimension(position.get("dimension").getAsString()) : null;
        if (dimensions.isEmpty() && primary != null) dimensions.add(primary);
        if (dimensions.isEmpty()) throw new IllegalArgumentException("Missing JourneyMap dimensions");
        List<Waypoint> points = new ArrayList<>();
        for (String dim : dimensions) {
            int targetX = x;
            int targetZ = z;
            if (!dim.equals(primary)) {
                if ("minecraft:the_nether".equals(primary)) {
                    targetX = Math.multiplyExact(x, 8);
                    targetZ = Math.multiplyExact(z, 8);
                } else if (dim.equals("minecraft:the_nether")) {
                    targetX = Math.floorDiv(x, 8);
                    targetZ = Math.floorDiv(z, 8);
                }
            }
            points.add(new Waypoint(dim, targetX, y, targetZ, name, color, visible));
        }
        return points;
    }

    private static boolean settingsVisible(JsonObject settings) {
        if (settings == null || !settings.has("enable")) return true;
        String value = settings.get("enable").getAsString();
        return value.equals("1") || (!value.equals("0") && bool(value));
    }

    private static boolean groupVisible(JsonObject root, JsonObject waypoint) {
        JsonObject groups = root.getAsJsonObject("groups");
        if (groups == null || !waypoint.has("groupId")) return true;
        JsonObject group = groups.getAsJsonObject(waypoint.get("groupId").getAsString());
        return group == null || settingsVisible(group.getAsJsonObject("settings"));
    }

    /** Minimal bounded NBT reader; keeps the importer independent of version-specific NBT APIs. */
    private static JsonElement readNbt(DataInputStream input, int type, int depth) throws IOException {
        if (depth > 64) throw new IOException("NBT nesting too deep");
        return switch (type) {
            case 1 -> new JsonPrimitive(input.readByte());
            case 2 -> new JsonPrimitive(input.readShort());
            case 3 -> new JsonPrimitive(input.readInt());
            case 4 -> new JsonPrimitive(input.readLong());
            case 5 -> new JsonPrimitive(input.readFloat());
            case 6 -> new JsonPrimitive(input.readDouble());
            case 8 -> new JsonPrimitive(input.readUTF());
            case 9 -> {
                int childType = input.readUnsignedByte();
                int size = nbtLength(input);
                JsonArray array = new JsonArray();
                for (int i = 0; i < size; i++) array.add(readNbt(input, childType, depth + 1));
                yield array;
            }
            case 10 -> {
                JsonObject object = new JsonObject();
                int childType;
                int entries = 0;
                while ((childType = input.readUnsignedByte()) != 0) {
                    if (++entries > 100_000) throw new IOException("NBT compound too large");
                    String name = input.readUTF();
                    object.add(name, readNbt(input, childType, depth + 1));
                }
                yield object;
            }
            case 7, 11, 12 -> {
                int size = nbtLength(input);
                int elementBytes = type == 7 ? 1 : type == 11 ? 4 : 8;
                input.skipNBytes((long) size * elementBytes);
                yield JsonNull.INSTANCE;
            }
            default -> throw new IOException("Unsupported NBT tag " + type);
        };
    }

    private static int nbtLength(DataInputStream input) throws IOException {
        int size = input.readInt();
        if (size < 0 || size > 100_000) throw new IOException("Invalid NBT length");
        return size;
    }

    private static int integer(JsonElement value) {
        if (value == null || value.isJsonNull()) throw new IllegalArgumentException("Missing coordinate");
        try {
            return value.getAsBigDecimal().intValueExact();
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException("Invalid integer", e);
        }
    }

    private static int channel(JsonObject json, String key) {
        int value = json.has(key) ? integer(json.get(key)) : 255;
        if (value < 0 || value > 255) throw new IllegalArgumentException("Invalid color channel");
        return value;
    }

    private static boolean bool(String value) {
        if (value.equalsIgnoreCase("true")) return true;
        if (value.equalsIgnoreCase("false")) return false;
        throw new IllegalArgumentException("Invalid boolean");
    }

    private static void readXaero(Path world, List<Source> sources) throws IOException {
        List<Waypoint> points = new ArrayList<>();
        int skipped = 0;
        try (Stream<Path> paths = Files.walk(world, 5)) {
            for (Path file : paths.filter(path -> Files.isRegularFile(path)
                    && !Files.isSymbolicLink(path)
                    && path.getFileName().toString().endsWith(".txt")).sorted().toList()) {
                String dimension = xaeroDimension(world.relativize(file));
                // Ignore config files and unrelated text outside dimension folders.
                if (dimension == null) continue;
                try (BufferedReader reader = Files.newBufferedReader(file)) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (!line.startsWith("waypoint:")) continue;
                        try {
                            points.add(parseXaero(line, dimension));
                        } catch (IllegalArgumentException e) {
                            skipped++;
                        }
                    }
                } catch (IOException e) {
                    skipped++;
                }
            }
        }
        if (!points.isEmpty() || skipped != 0) {
            sources.add(new Source("Xaero's Minimap", world, points, skipped));
        }
    }

    private static Waypoint parseXaero(String line, String dimension) {
        String[] values = line.split(":", -1);
        if (values.length < 8) throw new IllegalArgumentException("Incomplete Xaero waypoint");
        int color = Integer.parseInt(values[6]);
        if (color < 0 || color >= XAERO_COLORS.length) {
            throw new IllegalArgumentException("Invalid Xaero color");
        }
        return new Waypoint(dimension, Integer.parseInt(values[3]),
                values[4].equals("~") ? UNKNOWN_Y : Integer.parseInt(values[4]),
                Integer.parseInt(values[5]), values[1].replace("\u00a7\u00a7", ":"),
                0xFF000000 | XAERO_COLORS[color],
                !bool(values[7]));
    }

    private static String xaeroDimension(Path relativePath) {
        for (Path part : relativePath) {
            String value = part.toString();
            if (value.startsWith("dim%")) {
                try {
                    return normalizeDimension(value.substring(4).replace('$', ':')
                            .replace('%', '/').replace(',', '.'));
                } catch (IllegalArgumentException e) {
                    return null;
                }
            }
        }
        return null;
    }

    private static String normalizeDimension(String value) {
        String normalized = switch (value.toLowerCase(Locale.ROOT)) {
            case "0", "dim0", "overworld" -> "minecraft:overworld";
            case "-1", "dim-1", "nether", "the_nether" -> "minecraft:the_nether";
            case "1", "dim1", "end", "the_end" -> "minecraft:the_end";
            default -> value;
        };
        if (!normalized.matches("[a-z0-9_.-]+:[a-z0-9/._-]+")) {
            throw new IllegalArgumentException("Unknown dimension: " + value);
        }
        return normalized;
    }
}
