// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.marker;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class WaypointMetadataTest {
    private WaypointMetadataTest() {}

    public static void main(String[] args) throws Exception {
        bedWaypointsPersistAndUseTheNormalDisplayAndEditPaths();
        bedReplacementIsScopedAndPreservesEdits();
        categoriesNormalizeAndPersistBackwardsCompatibly();
        hiddenWaypointSourcesRemainListedAndGeneratedStationsCannotBeRemoved();
        transitAppearancePersistenceIsWorldScopedAndMergeIsFieldAware();
        System.out.println("Waypoint metadata tests passed.");
    }

    private static void bedWaypointsPersistAndUseTheNormalDisplayAndEditPaths() throws Exception {
        Path testRoot = Path.of(System.getProperty("codex.waypoint.test.root", "build/waypoint-metadata-tests"));
        Files.createDirectories(testRoot);
        Path config = Files.createTempDirectory(testRoot, "openmapdelta-bed-config-");
        System.setProperty("codex.waypoint.test.config", config.toString());
        MarkerManager.get().clearWaypoints();
        Marker bed = new Marker(UUID.randomUUID(), MarkerType.BED, dimension("minecraft:overworld"),
                18, 72, -30, "Last slept here", MarkerType.BED.defaultColor);
        bed.setImportedWorld("server:example.org");
        bed.setVisible(false);
        bed.setFavorite(true);
        bed.setCategory("Home");
        check(bed.getIconId().equals("item:minecraft:red_bed"), "bed gets a distinct default icon");
        check(MarkerType.BED.isWorldWaypoint(), "bed participates in world icons and beacons");
        check(MarkerType.BED.isEditableWaypoint(), "bed uses normal waypoint settings");
        MarkerManager.get().addWaypointDirect(bed);
        check(MarkerStorage.save(), "bed save succeeds");
        MarkerManager.get().clearWaypoints();
        MarkerStorage.load();
        Marker loaded = MarkerManager.get().getAllWaypoints().get(0);
        check(loaded.getType() == MarkerType.BED, "bed type survives reconnect/restart");
        check(loaded.getId().equals(bed.getId()) && loaded.getY() == 72, "bed identity and exact height persist");
        check(loaded.getImportedWorld().equals("server:example.org"), "bed retains its server scope");
        check(!loaded.isVisible() && loaded.isFavorite() && loaded.getCategory().equals("Home"),
                "bed visibility and appearance persist");
        check(loaded.getIconId().equals("item:minecraft:red_bed"), "bed icon survives reload");
        check(WaypointListPolicy.list(List.of(loaded), List.of(), List.of(), loaded.getDimension())
                .equals(List.of(loaded)), "hidden bed remains available for editing");
        check(WaypointListPolicy.canRemove(MarkerType.BED), "bed can be deleted");
        loaded.setIconId("");
        check(MarkerStorage.save(), "cleared bed icon save succeeds");
        MarkerManager.get().clearWaypoints();
        MarkerStorage.load();
        check(MarkerManager.get().getAllWaypoints().get(0).getIconId().isEmpty(),
                "explicitly clearing the bed icon survives reload");
        MarkerManager.get().clearWaypoints();
        deleteTree(config);
        deleteTree(config.resolveSibling("game"));
    }

    private static void categoriesNormalizeAndPersistBackwardsCompatibly() throws Exception {
        Path testRoot = Path.of(System.getProperty("codex.waypoint.test.root", "build/waypoint-metadata-tests"));
        Files.createDirectories(testRoot);
        Path config = Files.createTempDirectory(testRoot, "openmapdelta-waypoint-config-");
        System.setProperty("codex.waypoint.test.config", config.toString());
        ResourceKey<Level> dimension = dimension("minecraft:overworld");
        Marker marker = new Marker(UUID.randomUUID(), MarkerType.WAYPOINT, dimension,
                10, 64, 20, "Mine", MarkerType.WAYPOINT.defaultColor);
        marker.setCategory("  Work  ");
        check(marker.getCategory().equals("Work"), "category is trimmed on assignment");
        marker.setCategory("   ");
        check(marker.getCategory().isEmpty(), "blank categories normalize to the default category");
        marker.setCategory(" Work ");
        MarkerManager.get().addWaypointDirect(marker);
        check(MarkerStorage.save(), "manual waypoint save succeeds");
        MarkerManager.get().clearWaypoints();
        MarkerStorage.load();
        Marker loaded = MarkerManager.get().getAllWaypoints().get(0);
        check(loaded.getCategory().equals("Work"), "category survives marker JSON roundtrip");

        String legacy = "[{\"id\":\"00000000-0000-0000-0000-000000000010\","
                + "\"dimension\":\"minecraft:overworld\",\"x\":1,\"z\":2,"
                + "\"label\":\"Legacy\",\"color\":-1}]";
        Files.writeString(MarkerStorageTestPaths.waypoints(config), legacy);
        MarkerStorage.load();
        check(MarkerManager.get().getAllWaypoints().size() == 1, "legacy waypoint loads");
        check(MarkerManager.get().getAllWaypoints().get(0).getCategory().isEmpty(),
                "missing legacy category defaults to empty");
        deleteTree(config);
        deleteTree(config.resolveSibling("game"));
    }

    private static void bedReplacementIsScopedAndPreservesEdits() {
        String worldA = "server:example.org";
        ResourceKey<Level> overworld = dimension("minecraft:overworld");
        ResourceKey<Level> nether = dimension("minecraft:the_nether");
        Marker existingBed = marker(MarkerType.BED, overworld, "My bedroom");
        existingBed.setImportedWorld(worldA);
        existingBed.setColor(0xFF123456);
        existingBed.setVisible(false);
        existingBed.setFavorite(true);
        existingBed.setIconId("item:minecraft:blue_bed");
        existingBed.setCategory("Home");
        Marker duplicateBed = marker(MarkerType.BED, nether, "Duplicate");
        duplicateBed.setImportedWorld(worldA);
        Marker otherWorldBed = marker(MarkerType.BED, overworld, "Other server");
        otherWorldBed.setImportedWorld("server:other.example");
        Marker ordinary = marker(MarkerType.WAYPOINT, overworld, "Keep waypoint");
        List<Marker> waypoints = new ArrayList<>(List.of(existingBed, duplicateBed, otherWorldBed, ordinary));

        Marker candidate = new Marker(UUID.randomUUID(), MarkerType.BED, nether,
                -42, 19, 73, "New sleep point", MarkerType.BED.defaultColor);
        Marker recorded = WaypointListPolicy.upsertBedForWorld(waypoints, worldA, candidate);
        check(recorded == existingBed, "re-recording reuses this world's existing bed marker");
        check(waypoints.size() == 3 && waypoints.contains(otherWorldBed) && waypoints.contains(ordinary),
                "replacement removes only duplicate beds scoped to the current world");
        check(existingBed.getDimension().equals(nether) && existingBed.getX() == -42
                        && existingBed.getY() == 19 && existingBed.getZ() == 73,
                "replacement moves the saved bed across dimensions with exact height");
        check(existingBed.getLabel().equals("My bedroom") && existingBed.getColor() == 0xFF123456
                        && !existingBed.isVisible() && existingBed.isFavorite()
                        && existingBed.getIconId().equals("item:minecraft:blue_bed")
                        && existingBed.getCategory().equals("Home"),
                "replacement retains the existing marker's edits");

        int sizeBeforeUnknownWorld = waypoints.size();
        check(WaypointListPolicy.upsertBedForWorld(waypoints, null, candidate) == null
                        && waypoints.size() == sizeBeforeUnknownWorld,
                "unknown world scope cannot mutate saved bed markers");
    }

    private static void hiddenWaypointSourcesRemainListedAndGeneratedStationsCannotBeRemoved() {
        ResourceKey<Level> overworld = dimension("minecraft:overworld");
        ResourceKey<Level> nether = dimension("minecraft:the_nether");
        Marker manual = marker(MarkerType.WAYPOINT, overworld, "Manual");
        Marker temporary = marker(MarkerType.TEMPORARY_WAYPOINT, overworld, "Temporary");
        Marker station = marker(MarkerType.TRANSIT_STATION, overworld, "Station");
        Marker otherDimension = marker(MarkerType.WAYPOINT, nether, "Other");
        Marker notice = marker(MarkerType.LOCATION_NOTICE, overworld, "Notice");
        manual.setVisible(false);
        temporary.setVisible(false);
        station.setVisible(false);
        List<Marker> listed = WaypointListPolicy.list(List.of(manual, otherDimension), List.of(temporary, notice),
                List.of(station), overworld);
        check(listed.equals(List.of(manual, temporary, station)),
                "hidden manual, temporary, and transit sources remain listed in the selected dimension");
        check(!WaypointListPolicy.canRemove(MarkerType.TRANSIT_STATION), "generated stations cannot be removed");
        check(WaypointListPolicy.canRemove(MarkerType.WAYPOINT), "manual waypoints remain deletable");
        check(WaypointListPolicy.canRemove(MarkerType.TEMPORARY_WAYPOINT), "temporary waypoints remain deletable");
    }

    private static void transitAppearancePersistenceIsWorldScopedAndMergeIsFieldAware() throws Exception {
        Path testRoot = Path.of(System.getProperty("codex.waypoint.test.root", "build/waypoint-metadata-tests"));
        Path file = Files.createTempDirectory(testRoot, "openmapdelta-station-appearance-").resolve("appearance.json");
        UUID stationId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        TransitWaypointAppearanceStore.Appearance saved = new TransitWaypointAppearanceStore.Appearance(
                0xFF123456, false, true, "symbol:odysseymap:custom_station", "Work");
        Map<UUID, TransitWaypointAppearanceStore.Appearance> worldA = new LinkedHashMap<>();
        worldA.put(stationId, saved);
        check(TransitWaypointAppearanceStore.save(file, "world-A", worldA), "world A appearance saves");
        TransitWaypointAppearanceStore.Appearance loaded = TransitWaypointAppearanceStore.load(file, "world-A").get(stationId);
        check(loaded != null && loaded.color() == 0xFF123456 && Boolean.FALSE.equals(loaded.visible())
                        && Boolean.TRUE.equals(loaded.favorite()) && loaded.iconId().equals("symbol:odysseymap:custom_station")
                        && loaded.category().equals("Work"),
                "color, visibility, favorite, icon, and category survive station preference reload");
        check(TransitWaypointAppearanceStore.load(file, "world-B").isEmpty(),
                "a different persistent world cannot see station preferences");

        TransitWaypointAppearanceStore.Patch favoriteOnly = new TransitWaypointAppearanceStore.Patch();
        favoriteOnly.update(new TransitWaypointAppearanceStore.Appearance(null, null, true, null, null),
                TransitWaypointAppearanceStore.Field.FAVORITE);
        TransitWaypointAppearanceStore.Appearance merged = favoriteOnly.merge(loaded);
        check(merged.color().equals(loaded.color()) && merged.visible().equals(loaded.visible())
                        && merged.iconId().equals(loaded.iconId()) && merged.category().equals(loaded.category())
                        && Boolean.TRUE.equals(merged.favorite()),
                "an identity-late favorite change preserves fields loaded from disk");

        TransitWaypointAppearanceStore.Patch resetCategoryAndIcon = new TransitWaypointAppearanceStore.Patch();
        resetCategoryAndIcon.update(new TransitWaypointAppearanceStore.Appearance(null, null, null, "", ""),
                TransitWaypointAppearanceStore.Field.CATEGORY, TransitWaypointAppearanceStore.Field.ICON);
        TransitWaypointAppearanceStore.Appearance reset = resetCategoryAndIcon.merge(loaded);
        check(reset.category() == null && "".equals(reset.iconId()),
                "explicit default resets clear category while retaining an empty icon override");

        Map<UUID, TransitWaypointAppearanceStore.Appearance> worldB = new LinkedHashMap<>();
        worldB.put(UUID.randomUUID(), new TransitWaypointAppearanceStore.Appearance(null, false, null, null, "Other"));
        check(TransitWaypointAppearanceStore.save(file, "world-B", worldB), "world B appearance saves");
        check(TransitWaypointAppearanceStore.load(file, "world-A").get(stationId).category().equals("Work"),
                "saving another world's appearance preserves world A");

        Marker station = marker(MarkerType.TRANSIT_STATION, dimension("minecraft:overworld"), "Generated source name");
        station.setIconId("symbol:odysseymap:mtr_station");
        resetCategoryAndIcon.merge(loaded).applyTo(station);
        check(station.getIconId().isEmpty(), "empty icon override clears generated default icons after cache regeneration");
        byte[] beforeUnknownSave = Files.readAllBytes(file);
        check(!TransitWaypointAppearanceStore.save(file, null, worldA), "unknown identity does not write shared station settings");
        check(java.util.Arrays.equals(beforeUnknownSave, Files.readAllBytes(file)),
                "unknown identity cannot change the stored preference file");
        String json = Files.readString(file);
        check(!json.contains("Generated source name") && !json.contains("\"x\""),
                "generated station source names and coordinates are never duplicated in the override file");
        deleteTree(file.getParent());
    }

    private static Marker marker(MarkerType type, ResourceKey<Level> dimension, String label) {
        return new Marker(UUID.randomUUID(), type, dimension, 1, 64, 2, label, type.defaultColor);
    }

    private static ResourceKey<Level> dimension(String id) {
        return ResourceKey.create(Registries.DIMENSION, ResourceLocation.tryParse(id));
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static void deleteTree(Path root) throws Exception {
        if (!Files.exists(root)) return;
        try (var paths = Files.walk(root)) {
            for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
        }
    }

    private static final class MarkerStorageTestPaths {
        private static Path waypoints(Path config) {
            return config.resolve("openmapdelta").resolve("waypoints.json");
        }
    }
}
