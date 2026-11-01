// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.compat.mapselection;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.aosankaku.openmapdelta.config.OpenMapDeltaConfig;
import journeymap.client.JourneymapClient;
import journeymap.client.io.FileHandler;
import xaero.minimap.XaeroMinimap;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Focused state/config transaction tests; the provider stubs mirror reflected runtime APIs. */
public final class MapModSelectionTest {
    private static int assertions;
    private static final Path TEST_ROOT = Path.of(System.getProperty("map.selection.test.root", "build/map-selection-test-fixtures"));

    private MapModSelectionTest() {}

    public static void main(String[] args) throws Exception {
        fingerprintAndPromptPolicy();
        documentShapeAndValidation();
        acknowledgeAndMalformedStateRecovery();
        journeyMapDisablesWorldOverridesAndRestoresThem();
        failedCacheMutationRollsBackEverything();
        malformedProviderConfigFailsBeforeWrites();
        invalidUtf8AndUnsafeJourneyPathsFailClosed();
        modernXaeroUsesOfficialProfileEntryPointAndPath();
        legacyXaeroRestoresCachedWaypointSettings();
        System.out.println("MapModSelectionTest passed " + assertions + " assertions.");
    }

    private static void fingerprintAndPromptPolicy() {
        Map<String, String> two = new LinkedHashMap<>();
        two.put(MapModSelectionModel.JOURNEY_MAP, "6.0.7");
        two.put(MapModSelectionModel.ODYSSEY_MAP, "1.2.3");
        Map<String, String> reversed = new LinkedHashMap<>();
        reversed.put(MapModSelectionModel.ODYSSEY_MAP, "1.2.3");
        reversed.put(MapModSelectionModel.JOURNEY_MAP, "6.0.7");
        equal(MapModSelectionModel.fingerprint(two), MapModSelectionModel.fingerprint(reversed), "order invariant fingerprint");
        check(MapModSelectionModel.requiresPrompt(null, two), "initial coinstallation prompts");
        check(!MapModSelectionModel.requiresPrompt(null, Map.of(MapModSelectionModel.ODYSSEY_MAP, "1.2.3")),
                "initial single provider does not prompt");
        MapModSelectionState acknowledged = new MapModSelectionState(MapModSelectionModel.ODYSSEY_MAP,
                MapModSelectionModel.fingerprint(two), Map.of());
        check(!MapModSelectionModel.requiresPrompt(acknowledged, reversed), "unchanged set does not prompt");
        check(MapModSelectionModel.requiresPrompt(acknowledged,
                Map.of(MapModSelectionModel.ODYSSEY_MAP, "1.2.3")), "removal prompts even when only Odyssey remains");
        check(MapModSelectionModel.requiresPrompt(acknowledged,
                Map.of(MapModSelectionModel.ODYSSEY_MAP, "1.2.4", MapModSelectionModel.JOURNEY_MAP, "6.0.7")),
                "provider version change prompts");
        check(MapModSelectionModel.requiresPrompt(acknowledged,
                Map.of(MapModSelectionModel.ODYSSEY_MAP, "1.2.3", MapModSelectionModel.JOURNEY_MAP, "6.0.7",
                        MapModSelectionModel.XAERO_MINIMAP, "26.6.0")), "provider addition prompts");
    }

    private static void documentShapeAndValidation() throws Exception {
        Path root = tempDirectory("map-selection-doc-");
        Path file = root.resolve("settings.json");
        Files.writeString(file, "{\"bool\":true,\"string\":\"false\",\"object\":{\"value\":\"true\",\"label\":\"Keep me\"},\"unknown\":{\"x\":1}}\n");
        MapModSelectionFiles.Document document = MapModSelectionFiles.document(root, file, false);
        document.writeBoolean("bool", false);
        document.writeBoolean("string", true);
        document.writeBoolean("object", false);
        JsonObject updated = JsonParser.parseString(new String(document.updatedBytes())).getAsJsonObject();
        check(updated.get("bool").getAsJsonPrimitive().isBoolean(), "primitive boolean shape retained");
        check(updated.get("string").getAsJsonPrimitive().isString(), "string boolean shape retained");
        equal("Keep me", updated.getAsJsonObject("object").get("label").getAsString(), "object metadata retained");
        equal(1, updated.getAsJsonObject("unknown").get("x").getAsInt(), "unrelated JSON retained");
        equal(Boolean.FALSE, document.readBoolean("object"), "object value parsed");

        Path textFile = root.resolve("settings.txt");
        Files.writeString(textFile, "# keep\nminimap : true ;");
        MapModSelectionFiles.Document lineDocument = MapModSelectionFiles.document(root, textFile, true);
        lineDocument.writeBoolean("minimap", false);
        check(new String(lineDocument.updatedBytes()).contains("# keep"), "line comments retained");
        check(new String(lineDocument.updatedBytes()).contains("minimap : false ;"), "line value updated in place");
        Path modernMissing = root.resolve("modern.cfg");
        MapModSelectionFiles.Document modern = MapModSelectionFiles.document(root, modernMissing, true);
        modern.writeBoolean("display_minimap", false);
        check(new String(modern.updatedBytes()).contains("display_minimap = false"), "modern missing option uses equals syntax");
        equal(Boolean.FALSE, modern.readBoolean("display_minimap"), "modern missing option parses after write");
        Path legacyMissing = root.resolve("legacy.txt");
        MapModSelectionFiles.Document legacy = MapModSelectionFiles.document(root, legacyMissing, true, ":");
        legacy.writeBoolean("showIngameWaypoints", false);
        check(new String(legacy.updatedBytes()).contains("showIngameWaypoints:false"), "legacy missing option uses colon syntax");
        equal(Boolean.FALSE, legacy.readBoolean("showIngameWaypoints"), "legacy missing option parses after write");

        Path malformed = root.resolve("malformed.json");
        Files.writeString(malformed, "{\"flag\":42}");
        expectIOException(() -> MapModSelectionFiles.document(root, malformed, false).readBoolean("flag"),
                "numeric boolean is rejected");
        Path oversized = root.resolve("oversized.json");
        Files.write(oversized, new byte[MapModSelectionFiles.MAX_CONFIG_BYTES + 1]);
        expectIOException(() -> MapModSelectionFiles.document(root, oversized, false), "oversized config is rejected");
    }

    private static void acknowledgeAndMalformedStateRecovery() throws Exception {
        Path game = newGame();
        Path config = game.resolve("config");
        Map<String, String> one = Map.of(MapModSelectionModel.ODYSSEY_MAP, "1.2.3");
        MapModSelectionEditor.acknowledge(config, one);
        MapModSelectionState state = MapModSelectionState.read(config);
        check(!MapModSelectionModel.requiresPrompt(state, one), "acknowledgement stored");

        Map<String, Boolean> backups = Map.of("journeymap|data/mp/world/config/journeymap.minimap.config|enabled", true);
        MapModSelectionState withBackup = new MapModSelectionState(state.selectedId(), state.fingerprint(), backups);
        Files.write(MapModSelectionState.path(config), withBackup.toBytes());
        MapModSelectionEditor.acknowledge(config, Map.of(MapModSelectionModel.ODYSSEY_MAP, "1.2.4"));
        equal(backups, MapModSelectionState.read(config).restoreFlags(), "keep-current preserves restore snapshots");
        check(MapModSelectionModel.requiresPrompt(MapModSelectionState.read(config), one), "one-provider version change prompts");

        Files.writeString(MapModSelectionState.path(config), "{broken state");
        expectIOException(() -> MapModSelectionState.read(config), "malformed state is reported to controller");
        MapModSelectionEditor.acknowledge(config, one);
        equal(MapModSelectionModel.fingerprint(one), MapModSelectionState.read(config).fingerprint(),
                "explicit choice replaces only malformed state content");

        byte[] tooLarge = new byte[MapModSelectionState.MAX_STATE_BYTES + 1];
        Path statePath = MapModSelectionState.path(config);
        Files.write(statePath, tooLarge);
        expectIOException(() -> MapModSelectionEditor.acknowledge(config, one), "oversized state fails closed");
        equal((long) tooLarge.length, Files.size(statePath), "oversized state remains unchanged");
    }

    private static void journeyMapDisablesWorldOverridesAndRestoresThem() throws Exception {
        Fixture fixture = journeyFixture();
        Map<String, String> installed = installedOdysseyAndJourney();
        OpenMapDeltaConfig.MAP_MOD_ENABLED.set(false);
        OpenMapDeltaConfig.ENABLED.set(false);
        MapModSelectionEditor.apply(fixture.config(), MapModSelectionModel.ODYSSEY_MAP, installed);
        check(OpenMapDeltaConfig.MAP_MOD_ENABLED.get(), "selected Odyssey master enabled");
        check(OpenMapDeltaConfig.ENABLED.get(), "selected Odyssey HUD enabled");
        check(!JourneymapClient.getInstance().getCoreProperties().mappingEnabled.get(),
                "JourneyMap live mapping disabled while Odyssey is selected");
        check(!JourneymapClient.getInstance().getMiniMapProperties1().enabled.get(),
                "JourneyMap live primary minimap disabled while Odyssey is selected");
        check(!JourneymapClient.getInstance().getMiniMapProperties2().enabled.get(),
                "JourneyMap live secondary minimap disabled while Odyssey is selected");
        check(!JourneymapClient.getInstance().getWaypointProperties().beaconEnabled.get(),
                "JourneyMap live beacon setting disabled while Odyssey is selected");
        check(!JourneymapClient.getInstance().getWaypointProperties().renderWaypointsWorld.get(),
                "JourneyMap live world waypoint setting disabled while Odyssey is selected");
        check(!JourneymapClient.getInstance().getWebMapProperties().enabled.get(),
                "JourneyMap live web map disabled while Odyssey is selected");
        for (Path settings : fixture.settingsDirectories()) {
            for (String name : journeyConfigNames()) {
                JsonObject object = readJson(settings.resolve(name));
                for (String key : journeyKeys(name)) {
                    equal(Boolean.FALSE, object.get(key).getAsBoolean(),
                            "JourneyMap override disabled at " + settings);
                    check(object.get(key).getAsJsonPrimitive().isString(),
                            "JourneyMap string setting shape retained at " + settings);
                }
                assertNoJourneyUiKeys(object, settings);
            }
        }
        MapModSelectionState disabledState = MapModSelectionState.read(fixture.config());
        check(disabledState.restoreFlags().size() >= 15, "world and global settings are snapshotted");

        MapModSelectionEditor.apply(fixture.config(), MapModSelectionModel.JOURNEY_MAP, installed);
        check(!OpenMapDeltaConfig.MAP_MOD_ENABLED.get(), "deselected Odyssey master disabled");
        check(OpenMapDeltaConfig.ENABLED.get(), "deselection leaves Odyssey HUD preference intact");
        check(JourneymapClient.getInstance().getCoreProperties().mappingEnabled.get(),
                "JourneyMap live mapping restored when selected");
        check(JourneymapClient.getInstance().getMiniMapProperties1().enabled.get(),
                "JourneyMap live primary minimap restored when selected");
        check(JourneymapClient.getInstance().getMiniMapProperties2().enabled.get(),
                "JourneyMap live secondary minimap restored when selected");
        check(JourneymapClient.getInstance().getWaypointProperties().beaconEnabled.get(),
                "JourneyMap live beacon setting restored when selected");
        check(JourneymapClient.getInstance().getWaypointProperties().renderWaypointsWorld.get(),
                "JourneyMap live world waypoint setting restored when selected");
        check(JourneymapClient.getInstance().getWebMapProperties().enabled.get(),
                "JourneyMap live web map restored when selected");
        for (Path settings : fixture.settingsDirectories()) {
            for (String name : journeyConfigNames()) {
                JsonObject object = readJson(settings.resolve(name));
                for (String key : journeyKeys(name)) {
                    equal(Boolean.TRUE, object.get(key).getAsBoolean(),
                            "JourneyMap setting restored at " + settings);
                    check(object.get(key).getAsJsonPrimitive().isString(),
                            "JourneyMap restored string setting shape retained at " + settings);
                }
                assertNoJourneyUiKeys(object, settings);
            }
        }
        equal(0, MapModSelectionState.read(fixture.config()).restoreFlags().size(), "restored snapshots are consumed");

        Fixture initiallyOff = journeyFixture();
        Path inactiveWorld = initiallyOff.settingsDirectories().get(2);
        writeJourneySetting(inactiveWorld.resolve("journeymap.core.config"), "mappingEnabled", false);
        writeJourneySetting(inactiveWorld.resolve("journeymap.minimap.config"), "enabled", false);
        writeJourneySetting(inactiveWorld.resolve("journeymap.minimap2.config"), "enabled", false);
        MapModSelectionEditor.apply(initiallyOff.config(), MapModSelectionModel.JOURNEY_MAP, installed);
        equal(Boolean.TRUE, readJson(inactiveWorld.resolve("journeymap.core.config")).get("mappingEnabled").getAsBoolean(),
                "choosing JourneyMap enables inactive-world main mapping");
        equal(Boolean.TRUE, readJson(inactiveWorld.resolve("journeymap.minimap.config")).get("enabled").getAsBoolean(),
                "choosing JourneyMap enables inactive-world primary minimap");
        equal(Boolean.FALSE, readJson(inactiveWorld.resolve("journeymap.minimap2.config")).get("enabled").getAsBoolean(),
                "choosing JourneyMap preserves inactive-world secondary preset");

        Fixture missingWebDefault = journeyFixture();
        Path offlineWeb = missingWebDefault.settingsDirectories().get(2).resolve("journeymap.webmap.config");
        JsonObject webSettings = readJson(offlineWeb);
        webSettings.remove("enabled");
        Files.writeString(offlineWeb, new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(webSettings));
        MapModSelectionEditor.apply(missingWebDefault.config(), MapModSelectionModel.ODYSSEY_MAP, installed);
        JsonObject disabledWeb = readJson(offlineWeb);
        equal(Boolean.FALSE, disabledWeb.get("enabled").getAsBoolean(),
                "missing offline web-map setting uses JourneyMap false default when deselected");
        check(!MapModSelectionState.read(missingWebDefault.config()).restoreFlags().containsKey(
                        "journeymap|data/sp/other/config/journeymap.webmap.config|enabled"),
                "JourneyMap false default is not snapshotted as an enabled preference");
        MapModSelectionEditor.apply(missingWebDefault.config(), MapModSelectionModel.JOURNEY_MAP, installed);
        equal(Boolean.FALSE, readJson(offlineWeb).get("enabled").getAsBoolean(),
                "missing offline web-map setting retains false default after provider restoration");
    }

    private static void failedCacheMutationRollsBackEverything() throws Exception {
        Fixture fixture = journeyFixture();
        Map<String, String> installed = installedOdysseyAndJourney();
        Map<Path, byte[]> before = capture(fixture.game());
        JourneymapClient.getInstance().getMiniMapProperties1().enabled.failNextFalse();
        expectIOException(() -> MapModSelectionEditor.apply(fixture.config(), MapModSelectionModel.ODYSSEY_MAP, installed),
                "runtime setter failure aborts choice");
        equal(before.keySet(), capture(fixture.game()).keySet(), "rollback preserves file set");
        for (Map.Entry<Path, byte[]> entry : before.entrySet()) {
            check(java.util.Arrays.equals(entry.getValue(), Files.readAllBytes(entry.getKey())),
                    "rollback restores bytes " + entry.getKey());
        }
        check(JourneymapClient.getInstance().getCoreProperties().mappingEnabled.get(), "rollback restores first cache mutation");
        check(JourneymapClient.getInstance().getMiniMapProperties1().enabled.get(), "rollback restores failed cache mutation");
        check(MapModSelectionState.read(fixture.config()) == null, "failed transaction is not acknowledged");
    }

    private static void malformedProviderConfigFailsBeforeWrites() throws Exception {
        Fixture fixture = journeyFixture();
        Path activeWeb = fixture.activeWorld().resolve("journeymap.webmap.config");
        Files.writeString(activeWeb, "{\"enabled\":42,\"keep\":true}");
        Map<Path, byte[]> before = capture(fixture.game());
        expectIOException(() -> MapModSelectionEditor.apply(fixture.config(), MapModSelectionModel.ODYSSEY_MAP,
                installedOdysseyAndJourney()), "malformed provider boolean rejects entire preflight");
        for (Map.Entry<Path, byte[]> entry : before.entrySet()) {
            check(java.util.Arrays.equals(entry.getValue(), Files.readAllBytes(entry.getKey())),
                    "preflight failure preserves provider bytes " + entry.getKey());
        }
        check(MapModSelectionState.read(fixture.config()) == null, "preflight failure is not acknowledged");
    }

    private static void invalidUtf8AndUnsafeJourneyPathsFailClosed() throws Exception {
        Fixture badUtf8 = journeyFixture();
        Path activeCore = badUtf8.activeWorld().resolve("journeymap.core.config");
        Files.write(activeCore, new byte[]{'{', (byte) 0xff, '}'});
        Map<Path, byte[]> beforeUtf8 = capture(badUtf8.game());
        expectIOException(() -> MapModSelectionEditor.apply(badUtf8.config(), MapModSelectionModel.ODYSSEY_MAP,
                installedOdysseyAndJourney()), "invalid UTF-8 provider config rejects preflight");
        check(java.util.Arrays.equals(beforeUtf8.get(activeCore), Files.readAllBytes(activeCore)),
                "invalid UTF-8 bytes are preserved");

        Path game = newGame();
        Path config = game.resolve("config");
        Path global = game.resolve("journeymap/config/6.0");
        Path outside = game.resolve("outside/settings");
        Files.createDirectories(global);
        Files.createDirectories(outside);
        FileHandler.StandardConfigDirectory = global;
        new JourneymapClient(outside);
        Files.writeString(config.resolve("openmapdelta/config.json"), "{\"mapModEnabled\":false,\"enabled\":false}");
        Map<Path, byte[]> beforePath = capture(game);
        expectIOException(() -> MapModSelectionEditor.apply(config, MapModSelectionModel.ODYSSEY_MAP,
                installedOdysseyAndJourney()), "JourneyMap live config path outside allowlist rejected");
        check(java.util.Arrays.equals(beforePath.get(config.resolve("openmapdelta/config.json")),
                Files.readAllBytes(config.resolve("openmapdelta/config.json"))), "unsafe path leaves OpenMap config unchanged");
        check(MapModSelectionState.read(config) == null, "unsafe path is not acknowledged");
    }

    private static void modernXaeroUsesOfficialProfileEntryPointAndPath() throws Exception {
        Path game = newGame();
        Path config = game.resolve("config");
        Path xaeroRoot = config.resolve("xaero/minimap");
        Path profiles = xaeroRoot.resolve("profiles");
        Files.createDirectories(profiles);
        XaeroMinimap minimap = new XaeroMinimap(xaeroRoot);
        Path profile = profiles.resolve("default.cfg");
        Files.writeString(profile, "# keep\ndisplay_minimap = false\nwaypoints_in_world = true\nwaypoints_on_minimap = true\ntracked_players_in_world = true\n");
        Map<String, String> installed = Map.of(MapModSelectionModel.XAERO_MINIMAP, "26.6.0");
        MapModSelectionEditor.apply(config, MapModSelectionModel.XAERO_MINIMAP, installed);
        check(minimap.getHudConfigs().getClientConfigManager().getCurrentProfile().value("display_minimap"),
                "modern Xaero primary minimap enabled");
        check(Files.readString(profile).contains("display_minimap = true"), "modern Xaero profile file patched");
        check(Files.readString(profile).contains("# keep"), "modern Xaero profile comments preserved");
        equal(MapModSelectionModel.XAERO_MINIMAP, MapModSelectionState.read(config).selectedId(),
                "modern Xaero selection acknowledged");
    }

    private static void legacyXaeroRestoresCachedWaypointSettings() throws Exception {
        Path game = newGame();
        Path config = game.resolve("config");
        Path file = config.resolve("xaerominimap.txt");
        Files.writeString(file, "# user preference\nminimap:false\nshowWaypoints:true\ndisplayTrackedPlayersInWorld:true\n");
        XaeroMinimap legacyMinimap = new XaeroMinimap(config.resolve("xaero/minimap"));
        var module = xaero.hud.minimap.BuiltInHudModules.MINIMAP;
        module.setActive(false);
        Map<String, String> installed = Map.of(
                MapModSelectionModel.XAERO_MINIMAP, "25.2.10",
                MapModSelectionModel.ODYSSEY_MAP, "1.2.3");
        MapModSelectionEditor.apply(config, MapModSelectionModel.XAERO_MINIMAP, installed);
        check(module.isActive(), "legacy Xaero minimap module enabled");
        check(Files.readString(file).contains("minimap:true"), "legacy Xaero text config updated");

        MapModSelectionEditor.apply(config, MapModSelectionModel.ODYSSEY_MAP, installed);
        check(!module.isActive(), "legacy Xaero minimap disabled while Odyssey selected");
        check(!legacyMinimap.getSettings().showWaypoints(), "legacy waypoints disabled in cache");
        check(!legacyMinimap.getSettings().showIngameWaypoints(), "legacy world waypoints disabled in cache");
        check(!legacyMinimap.getSettings().displayTrackedPlayersInWorld(), "legacy tracked players disabled in cache");
        check(Files.readString(file).contains("showIngameWaypoints:false"), "missing legacy setting inserted with colon syntax");

        MapModSelectionEditor.apply(config, MapModSelectionModel.XAERO_MINIMAP, installed);
        check(module.isActive(), "legacy Xaero minimap restored");
        check(legacyMinimap.getSettings().showWaypoints(), "legacy waypoint preference restored");
        check(legacyMinimap.getSettings().showIngameWaypoints(), "legacy world waypoint preference restored");
        check(legacyMinimap.getSettings().displayTrackedPlayersInWorld(), "legacy tracked-player preference restored");
        check(Files.readString(file).contains("showIngameWaypoints:true"), "missing legacy setting restored through native syntax");
        check(Files.readString(file).contains("# user preference"), "legacy Xaero comments preserved");
    }

    private static Fixture journeyFixture() throws Exception {
        Path game = newGame();
        Path config = game.resolve("config");
        Path standard = game.resolve("journeymap/config/6.0");
        Path current = game.resolve("journeymap/data/mp/current/config");
        Path other = game.resolve("journeymap/data/sp/other/config");
        Files.createDirectories(standard);
        Files.createDirectories(current);
        Files.createDirectories(other);
        FileHandler.StandardConfigDirectory = standard;
        for (Path directory : List.of(standard, current, other)) {
            for (String name : journeyConfigNames()) Files.writeString(directory.resolve(name), journeyJson(name));
        }
        new JourneymapClient(current);
        Files.writeString(config.resolve("openmapdelta/config.json"), "{\"mapModEnabled\":false,\"enabled\":false,\"keep\":\"yes\"}");
        OpenMapDeltaConfig.MAP_MOD_ENABLED.set(false);
        OpenMapDeltaConfig.ENABLED.set(false);
        return new Fixture(game, config, current, List.of(standard, current, other));
    }

    private static Path newGame() throws IOException {
        Path game = tempDirectory("map-selection-game-");
        Files.createDirectories(game.resolve("config/openmapdelta"));
        return game;
    }

    private static Map<String, String> installedOdysseyAndJourney() {
        return Map.of(MapModSelectionModel.ODYSSEY_MAP, "1.2.3", MapModSelectionModel.JOURNEY_MAP, "6.0.7");
    }

    private static List<String> journeyConfigNames() {
        return List.of("journeymap.core.config", "journeymap.minimap.config", "journeymap.minimap2.config",
                "journeymap.waypoint.config", "journeymap.webmap.config");
    }

    private static List<String> journeyKeys(String name) {
        if (name.equals("journeymap.core.config")) return List.of("mappingEnabled");
        if (name.equals("journeymap.waypoint.config")) return List.of("beaconEnabled", "renderWaypointsWorld");
        return List.of("enabled");
    }

    private static String journeyJson(String name) {
        StringBuilder json = new StringBuilder("{\"unrelated\":{\"keep\":1}");
        for (String key : journeyKeys(name)) json.append(",\"").append(key).append("\":\"true\"");
        return json.append('}').toString();
    }

    private static void assertNoJourneyUiKeys(JsonObject object, Path settings) {
        for (String key : object.keySet()) {
            check(!key.startsWith("jm."), "JourneyMap UI translation key is not written to " + settings + ": " + key);
        }
    }

    private static JsonObject readJson(Path file) throws IOException {
        return JsonParser.parseString(Files.readString(file)).getAsJsonObject();
    }

    private static void writeJourneySetting(Path file, String key, boolean value) throws IOException {
        JsonObject json = readJson(file);
        json.addProperty(key, Boolean.toString(value));
        Files.writeString(file, new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(json));
    }

    private static Map<Path, byte[]> capture(Path root) throws IOException {
        Map<Path, byte[]> files = new LinkedHashMap<>();
        try (var paths = Files.walk(root)) {
            for (Path path : paths.filter(Files::isRegularFile).toList()) files.put(path, Files.readAllBytes(path));
        }
        return files;
    }

    private static Path tempDirectory(String prefix) throws IOException {
        Files.createDirectories(TEST_ROOT);
        return Files.createTempDirectory(TEST_ROOT, prefix);
    }

    private static void expectIOException(ThrowingAction action, String message) throws Exception {
        try {
            action.run();
            throw new AssertionError("Expected IOException: " + message);
        } catch (IOException expected) {
            assertions++;
        }
    }

    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }

    private static void equal(Object expected, Object actual, String message) {
        assertions++;
        if (!java.util.Objects.equals(expected, actual)) {
            throw new AssertionError(message + ": expected " + expected + ", got " + actual);
        }
    }

    private record Fixture(Path game, Path config, Path activeWorld, List<Path> settingsDirectories) {}

    @FunctionalInterface
    private interface ThrowingAction { void run() throws Exception; }
}
