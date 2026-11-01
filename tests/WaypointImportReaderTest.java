// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.marker;

import java.io.IOException;
import java.io.DataOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/** Standalone regression tests: no Minecraft client or test framework is needed. */
public final class WaypointImportReaderTest {
    private static int assertions;

    public static void main(String[] args) throws Exception {
        Path game = Files.createTempDirectory("odysseymap-import-test-");
        try {
            check(WaypointImportReader.scan(game).isEmpty(), "empty installation");
            journeyMap(game);
            modernJourneyMap(game);
            xaero(game);
            check(WaypointImportReader.scan(game).size() == 8, "separate world and mod sources");
            System.out.println("Waypoint import tests passed (" + assertions + " assertions).");
        } finally {
            try (Stream<Path> files = Files.walk(game)) {
                for (Path file : files.sorted(Comparator.reverseOrder()).toList()) Files.delete(file);
            }
        }
    }

    private static void journeyMap(Path game) throws Exception {
        Path folder = game.resolve("journeymap/data/sp/My World/waypoints");
        String json = """
                {"name":"Home: 日本語", "x":-12, "y":80, "z":35,
                 "r":12, "g":34, "b":56, "enable":false,
                 "dimensions":["minecraft:overworld", "minecraft:the_nether", "minecraft:overworld"]}
                """;
        Path original = write(folder.resolve("home.json"), json);
        write(folder.resolve("legacy.json"), """
                {"name":"End", "x":0, "z":-100, "dimension":1}
                """);
        write(folder.resolve("broken.json"), "{broken");
        write(folder.resolve("fractional.json"), """
                {"name":"Invalid", "x":1.5, "z":3, "dimensions":["minecraft:overworld"]}
                """);
        write(folder.resolve("overflow.json"), """
                {"x":2147483648, "z":0, "dimension":0}
                """);
        write(folder.resolve("no_dimension.json"), "{\"x\":0,\"z\":0}");
        write(folder.resolve("bad_color.json"), "{\"x\":0,\"z\":0,\"r\":256,\"dimension\":0}");
        write(folder.resolve("null.json"), "null");
        write(folder.resolve("missing_coordinate.json"), "{\"x\":0,\"dimension\":0}");
        write(folder.resolve("empty_dimensions.json"), "{\"x\":0,\"z\":0,\"dimensions\":[]}");
        write(game.resolve("journeymap/data/mp/example.org/waypoints/custom.json"), """
                {"name":"Moon", "x":9, "y":-20, "z":10, "dimensions":["ad_astra:moon"]}
                """);
        // These are not waypoint directories and must never be imported.
        write(game.resolve("journeymap/data/sp/My World/overworld/home.json"), json);
        List<WaypointImportReader.Source> sources = WaypointImportReader.scan(game);
        WaypointImportReader.Source source = source(sources, "My World");
        check(source.modName().equals("JourneyMap"), "JourneyMap source label");
        check(source.waypoints().size() == 3, "multiple dimensions and repeated dimensions");
        check(source.skipped() == 8, "malformed JourneyMap entries counted");
        WaypointImportReader.Waypoint home = source.waypoints().stream()
                .filter(point -> point.name().startsWith("Home")).findFirst().orElseThrow();
        check(home.name().equals("Home: 日本語"), "JourneyMap Unicode name");
        check(home.x() == -12 && home.y() == 80 && home.z() == 35, "JourneyMap coordinates");
        check(home.color() == 0xFF0C2238, "JourneyMap RGB");
        check(!home.visible(), "JourneyMap hidden state");
        check(source.waypoints().stream().anyMatch(point -> point.dimension().equals("minecraft:the_nether")
                && point.x() == -2 && point.z() == 4), "legacy JourneyMap Nether coordinate scaling");
        WaypointImportReader.Waypoint legacy = source.waypoints().stream()
                .filter(point -> point.name().equals("End")).findFirst().orElseThrow();
        check(legacy.dimension().equals("minecraft:the_end"), "legacy JourneyMap dimension");
        check(legacy.y() == Integer.MIN_VALUE, "unspecified JourneyMap height");
        check(legacy.visible() && legacy.color() == 0xFFFFFFFF, "JourneyMap defaults");
        check(source(sources, "example.org").waypoints().get(0).dimension().equals("ad_astra:moon"),
                "JourneyMap custom dimension");
        check(Files.readString(original).equals(json), "source file remains unchanged");
    }

    private static void modernJourneyMap(Path game) throws Exception {
        Path folder = game.resolve("journeymap/data/sp/Modern World/waypoints");
        Files.createDirectories(folder);
        Map<String, Object> waypoints = Map.of(
                "home", Map.of("name", "Modern: 日本語", "pos", Map.of("x", -81, "y", 72, "z", 160,
                        "dimension", "minecraft:overworld"), "color", 0x123456,
                        "dimensions", List.of("minecraft:overworld", "minecraft:the_nether"),
                        "settings", Map.of("enable", (byte) 0)),
                "nether", Map.of("name", "Nether", "pos", Map.of("x", 2, "y", 66, "z", -3,
                        "dimension", "minecraft:the_nether"), "color", 0xFF112233,
                        "dimensions", List.of("minecraft:overworld", "minecraft:the_nether")),
                "grouped", Map.of("name", "Grouped", "pos", Map.of("x", 1, "y", 2, "z", 3,
                        "dimension", "mod:moon"), "color", 0, "dimensions", List.of(), "groupId", "hidden"),
                "bad", Map.of("name", "Bad", "pos", Map.of("x", 0, "z", 0), "dimensions", List.of()));
        try (DataOutputStream out = new DataOutputStream(Files.newOutputStream(folder.resolve("WaypointData.dat")))) {
            out.writeByte(10);
            out.writeUTF("");
            compound(out, Map.of("waypoints", waypoints, "groups", Map.of("hidden",
                    Map.of("settings", Map.of("enable", (byte) 0)))));
        }
        write(folder.resolve("stale.json"), "{\"x\":100,\"z\":100,\"dimension\":0}");
        // A native file must not be confused with arbitrary backup .dat files.
        write(folder.resolve("backup.dat"), "not NBT");
        Path bad = game.resolve("journeymap/data/sp/Broken World/waypoints/WaypointData.dat");
        Files.createDirectories(bad.getParent());
        try (DataOutputStream out = new DataOutputStream(Files.newOutputStream(bad))) {
            out.writeByte(10);
            out.writeUTF("");
            out.writeByte(9);
            out.writeUTF("waypoints");
            out.writeByte(3);
            out.writeInt(-1);
        }
        List<WaypointImportReader.Source> sources = WaypointImportReader.scan(game);
        WaypointImportReader.Source source = source(sources, "Modern World");
        check(source.waypoints().size() == 5, "JourneyMap 6 NBT and stale JSON ignored");
        check(source.skipped() == 1, "JourneyMap 6 invalid waypoint skipped");
        check(source.waypoints().stream().anyMatch(point -> point.name().equals("Modern: 日本語")
                && point.dimension().equals("minecraft:overworld") && point.x() == -81 && point.z() == 160
                && point.y() == 72 && point.color() == 0xFF123456 && !point.visible()), "modern JourneyMap fields");
        check(source.waypoints().stream().anyMatch(point -> point.name().startsWith("Modern")
                && point.dimension().equals("minecraft:the_nether") && point.x() == -11 && point.z() == 20),
                "modern JourneyMap Overworld to Nether scaling with negative coordinates");
        check(source.waypoints().stream().anyMatch(point -> point.name().equals("Nether")
                && point.dimension().equals("minecraft:overworld") && point.x() == 16 && point.z() == -24),
                "modern JourneyMap Nether to Overworld scaling");
        check(source.waypoints().stream().anyMatch(point -> point.name().equals("Nether")
                && point.dimension().equals("minecraft:the_nether") && point.x() == 2 && point.z() == -3
                && point.color() == 0xFF112233 && point.visible()), "modern JourneyMap primary Nether coordinates");
        check(source.waypoints().stream().anyMatch(point -> point.name().equals("Grouped")
                && point.dimension().equals("mod:moon") && !point.visible() && point.color() == 0xFF000000),
                "JourneyMap 6 primary dimension fallback and hidden group");
        check(source(sources, "Broken World").waypoints().isEmpty()
                && source(sources, "Broken World").skipped() == 1, "corrupt NBT rejected safely");
    }

    private static void compound(DataOutputStream out, Map<?, ?> fields) throws IOException {
        for (var entry : fields.entrySet()) {
            Object value = entry.getValue();
            out.writeByte(value instanceof Map ? 10 : value instanceof List ? 9
                    : value instanceof Byte ? 1 : value instanceof Integer ? 3 : 8);
            out.writeUTF((String) entry.getKey());
            if (value instanceof Map<?, ?> map) compound(out, map);
            else if (value instanceof List<?> list) {
                out.writeByte(8);
                out.writeInt(list.size());
                for (Object item : list) out.writeUTF((String) item);
            } else if (value instanceof Byte number) out.writeByte(number);
            else if (value instanceof Integer number) out.writeInt(number);
            else out.writeUTF((String) value);
        }
        out.writeByte(0);
    }

    private static void xaero(Path game) throws Exception {
        Path folder = game.resolve("xaero/minimap/Multiplayer_example.org");
        write(folder.resolve("dim%0/mw$default_1.txt"), """
                # Xaero's Minimap Waypoints
                sets:gui.xaero_default:More
                waypoint:Home§§base:H:-20:~:44:12:true:0:gui.xaero_default:false:0:false
                waypoint:Spawn:S:0:64:0:15:false
                waypoint:Bad:B:bad:64:0:0:false
                waypoint:Bad color:B:0:64:0:99:false
                waypoint:Bad flag:B:0:64:0:0:maybe
                waypoint:Missing:M:0
                """);
        write(folder.resolve("dim%-1/waypoints.txt"), "waypoint:Portal:P:2:-5:3:6:false\n");
        write(folder.resolve("dim%mod$moon%crater/waypoints.txt"), "waypoint:Moon:M:1:2:3:16:false\n");
        write(folder.resolve("dim%mod$name,/waypoints.txt"), "waypoint:Dot:D:1:2:3:20:false\n");
        write(folder.resolve("settings.txt"), "waypoint:Not a waypoint:N:1:2:3:0:false\n");
        write(game.resolve("XaeroWaypoints/Old World/dim%1/waypoints.txt"),
                "waypoint:End:E:0:90:0:0:false\n");
        write(game.resolve("config/XaeroWaypoints/Config World/dim%0/waypoints.txt"),
                "waypoint:Old:O:0:70:0:1:false\n");
        write(game.resolve("mods/XaeroWaypoints/Mods World/dim%0/waypoints.txt"),
                "waypoint:Old:O:0:70:0:2:false\n");
        List<WaypointImportReader.Source> sources = WaypointImportReader.scan(game);
        WaypointImportReader.Source source = source(sources, "Multiplayer_example.org");
        check(source.modName().equals("Xaero's Minimap"), "Xaero source label");
        check(source.waypoints().size() == 5, "Xaero dimensions and subworlds");
        check(source.skipped() == 4, "malformed Xaero entries counted");
        WaypointImportReader.Waypoint home = source.waypoints().stream()
                .filter(point -> point.name().equals("Home:base")).findFirst().orElseThrow();
        check(home.dimension().equals("minecraft:overworld"), "Xaero overworld");
        check(home.x() == -20 && home.z() == 44, "Xaero coordinates");
        check(home.y() == Integer.MIN_VALUE, "Xaero unspecified height");
        check(!home.visible(), "Xaero disabled flag");
        check(home.color() == 0xFFFF0000, "Xaero palette red");
        check(source.waypoints().stream().anyMatch(point -> point.dimension().equals("minecraft:the_nether")
                && point.y() == -5 && point.color() == 0xFFFFAA00), "Xaero Nether and gold");
        check(source.waypoints().stream().anyMatch(point -> point.dimension().equals("mod:moon/crater")
                && point.color() == 0xFFFF00FF), "Xaero custom path and extra palette");
        check(source.waypoints().stream().anyMatch(point -> point.dimension().equals("mod:name.")
                && point.color() == 0xFF8B4513), "Xaero trailing dot escape and brown");
        check(source(sources, "Old World").waypoints().get(0).dimension().equals("minecraft:the_end"),
                "legacy Xaero End");
        check(source(sources, "Config World").waypoints().size() == 1, "legacy config root");
        check(source(sources, "Mods World").waypoints().size() == 1, "legacy mods root");
    }

    private static WaypointImportReader.Source source(List<WaypointImportReader.Source> sources, String name) {
        return sources.stream().filter(source -> source.directory().getFileName().toString().equals(name))
                .findFirst().orElseThrow();
    }

    private static Path write(Path file, String text) throws IOException {
        Files.createDirectories(file.getParent());
        return Files.writeString(file, text);
    }

    private static void check(boolean result, String message) {
        assertions++;
        if (!result) throw new AssertionError(message);
    }
}
