// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.config;

import net.aosankaku.openmapdelta.platform.DataPaths;
import com.google.gson.*;
import net.aosankaku.openmapdelta.platform.Services;
import net.aosankaku.openmapdelta.render.WaypointClusterOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class OpenMapDeltaConfig {
    private static final Logger LOG = LoggerFactory.getLogger("OpenMapDelta");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static final ConfigValue<Boolean> MAP_MOD_ENABLED;
    public static final ConfigValue<Boolean> ENABLED;
    public static final ConfigValue<Double> MINIMAP_SCALE;
    public static final ConfigValue<ScreenPosition> POSITION;
    public static final ConfigValue<MinimapShape> SHAPE;
    public static final ConfigValue<MinimapLayerMode> MINIMAP_LAYER_MODE;
    public static final ConfigValue<Integer> MINIMAP_CAVE_Y;
    public static final ConfigValue<Integer> ZOOM_BLOCKS_PER_PIXEL;
    public static final ConfigValue<Integer> COLUMNS_PER_TICK;
    public static final ConfigValue<Integer> SCAN_INTERVAL_FRAMES;
    public static final ConfigValue<RotationMode> ROTATION_MODE;
    public static final ConfigValue<BorderStyle> BORDER_STYLE;
    public static final ConfigValue<Double> MINIMAP_PLAYER_SCALE;
    public static final ConfigValue<MinimapPlayerIcon> MINIMAP_PLAYER_ICON;
    public static final ConfigValue<MinimapPlayerIcon> MINIMAP_OTHER_PLAYER_ICON;
    public static final ConfigValue<MinimapPlayerLabelMode> MINIMAP_PLAYER_LABEL_INSIDE;
    public static final ConfigValue<MinimapPlayerLabelMode> MINIMAP_PLAYER_LABEL_OUTSIDE;
    public static final ConfigValue<Boolean> MINIMAP_ROTATE_PLAYER_HEAD;
    public static final ConfigValue<MinimapPlayerIcon> FULLSCREEN_PLAYER_ICON;
    public static final ConfigValue<Boolean> FULLSCREEN_ROTATE_PLAYER_HEAD;
    public static final ConfigValue<Double> MINIMAP_WAYPOINT_SCALE;
    public static final ConfigValue<Integer> MINIMAP_LABEL_LIMIT;
    public static final ConfigValue<Double> MINIMAP_WAYPOINT_NAME_DISTANCE;
    public static final ConfigValue<Double> MINIMAP_MARKER_EDGE_OFFSET;
    public static final ConfigValue<Double> INFO_TICK_SECONDS;
    public static final ConfigValue<Integer> INFO_SPEED_SECONDS;
    public static final ConfigValue<MinimapInfoType> INFO_TOP;
    public static final ConfigValue<MinimapInfoType> INFO_TOP_LEFT;
    public static final ConfigValue<MinimapInfoType> INFO_TOP_RIGHT;
    public static final ConfigValue<MinimapInfoType> INFO_BOTTOM;
    public static final ConfigValue<MinimapInfoType> INFO_BOTTOM_RIGHT;
    public static final ConfigValue<MinimapInfoType> INFO_BOTTOM_LEFT;
    public static final ConfigValue<Integer> BORDER_COLOR;
    public static final ConfigValue<Integer> BORDER_THICKNESS;
    public static final ConfigValue<Double> TRANSPARENCY;
    public static final ConfigValue<Boolean> SHOW_COMPASS;
    public static final ConfigValue<Boolean> SHOW_COORDINATES;
    public static final ConfigValue<Boolean> HIDE_WHEN_SCREEN_OPEN;
    public static final ConfigValue<PerformanceMode> PERFORMANCE_MODE;
    public static final ConfigValue<Boolean> SHOW_PLAYERS;
    public static final ConfigValue<Boolean> SHOW_WAYPOINTS;
    public static final ConfigValue<Boolean> SHOW_SHARED_WAYPOINTS;
    public static final ConfigValue<Double> WAYPOINT_HIDE_DISTANCE;
    public static final ConfigValue<Double> WAYPOINT_LABEL_FADE_DISTANCE;
    public static final ConfigValue<Boolean> SHOW_DEATH;
    public static final ConfigValue<Boolean> SMART_DEATHPOINTS;
    public static final ConfigValue<Boolean> SHOW_KEEP_INVENTORY_DEATHPOINTS;
    public static final ConfigValue<Double> DEATH_BEACON_MIN_OPACITY;
    public static final ConfigValue<Double> DEATH_BEACON_MAX_OPACITY;
    public static final ConfigValue<Integer> SMART_DEATHPOINT_BLACK_RETENTION_SECONDS;
    public static final ConfigValue<String> SMART_DEATHPOINT_CHEAP_ITEMS;
    public static final ConfigValue<Boolean> SHOW_BEDS;
    public static final ConfigValue<Boolean> RECORD_NON_SPAWN_SLEEP;
    public static final ConfigValue<Boolean> SHOW_PORTALS;
    public static final ConfigValue<Boolean> SHOW_STRUCTURES;
    public static final ConfigValue<Boolean> SHOW_ENTITIES;
    public static final ConfigValue<Boolean> SHOW_MARKER_DISTANCE;
    public static final ConfigValue<Boolean> STICK_MARKERS_TO_BORDER;
    public static final ConfigValue<Integer> FULLSCREEN_DEFAULT_ZOOM;
    public static final ConfigValue<Double> FULLSCREEN_WAYPOINT_LABEL_MIN_ZOOM;
    public static final ConfigValue<Double> FULLSCREEN_WAYPOINT_LABEL_SCALE;
    public static final ConfigValue<Boolean> FULLSCREEN_SHOW_GRID;
    public static final ConfigValue<Boolean> FULLSCREEN_SHOW_MTR_RAILS;
    public static final ConfigValue<Boolean> FULLSCREEN_SHOW_CREATE_RAILS;
    public static final ConfigValue<Boolean> FULLSCREEN_SHOW_ZOOM_BUTTONS;
    public static final ConfigValue<Double> FULLSCREEN_GRID_THICKNESS;
    public static final ConfigValue<Integer> PLAYER_ICON_MAX_SIZE;
    public static final ConfigValue<Boolean> BIOME_GRASS_TINT;
    public static final ConfigValue<Boolean> ASYNC_MAP_GENERATION;
    public static final ConfigValue<Boolean> SHOW_PLAYER_HEAD;
    public static final ConfigValue<Boolean> EXTERNAL_PLAYERS_ENABLED;
    public static final ConfigValue<Boolean> CREATE_STATION_WAYPOINTS;
    public static final ConfigValue<Integer> CREATE_STATION_GROUPING_RADIUS;
    public static final ConfigValue<Boolean> MTR_STATION_WAYPOINTS;
    public static final ConfigValue<Integer> CAVE_LAYER_STEP;
    public static final ConfigValue<Integer> NAVIGATION_MAX_TARGETS;
    public static final ConfigValue<Double> NAVIGATION_ARRIVAL_DISTANCE;
    public static final ConfigValue<WaypointClusterOrder.Order> WORLD_WAYPOINT_CLUSTER_LABEL_ORDER;

    // v1.1.0 new config values
    public static final ConfigValue<Boolean> MAP_FULLSCREEN_ENABLED;
    public static final ConfigValue<Boolean> MAP_SHOW_COORDINATES;
    public static final ConfigValue<Boolean> MAP_SHOW_WAYPOINTS;
    public static final ConfigValue<Boolean> MAP_SHOW_PLAYER_MARKER;
    public static final ConfigValue<Integer> MAP_MAX_WAYPOINTS_RENDERED;
    public static final ConfigValue<Boolean> MAP_SAFE_RENDER_MODE;

    private static final List<ConfigValue<?>> ALL_VALUES = new ArrayList<>();

    static {
        MAP_MOD_ENABLED = register(new ConfigValue<>("mapModEnabled", "Enable client maps, waypoints and web hosting", true));
        ENABLED = register(new ConfigValue<>("enabled", "Enable the minimap HUD", true));
        MINIMAP_SCALE = register(new ConfigValue<>("minimapScale", "Relative minimap size scale (0.25-2.0)", 0.9, v -> {
            if (!Double.isFinite(v) || v < 0.25 || v > 2.0) {
                throw new IllegalArgumentException("Minimap scale must be 0.25-2.0");
            }
        }));
        POSITION = register(new ConfigValue<>("position", "Screen corner position", ScreenPosition.TOP_RIGHT));
        SHAPE = register(new ConfigValue<>("shape", "Minimap shape", MinimapShape.CIRCLE));
        MINIMAP_LAYER_MODE = register(new ConfigValue<>("minimapLayerMode", "Minimap terrain layer: surface, player Y or manual", MinimapLayerMode.SURFACE));
        MINIMAP_CAVE_Y = register(new ConfigValue<>("minimapCaveY", "Manual minimap cave layer Y (-4096 to 4096)", 64, v -> {
            if (v < -4096 || v > 4096) throw new IllegalArgumentException("Minimap cave Y must be -4096 to 4096");
        }));
        ZOOM_BLOCKS_PER_PIXEL = register(new ConfigValue<>("zoomBlocksPerPixel", "World blocks per minimap pixel", 1, v -> {
            if (v < 1 || v > 16) throw new IllegalArgumentException("Zoom must be 1-16");
        }));
        ROTATION_MODE = register(new ConfigValue<>("rotationMode", "heading_up or north_up", RotationMode.NORTH_UP));
        BORDER_STYLE = register(new ConfigValue<>("borderStyle", "Solid or dotted minimap border", BorderStyle.SOLID));
        MINIMAP_PLAYER_SCALE = register(new ConfigValue<>("minimapPlayerScale", "Minimap player icon scale (0.25-2)", 0.6, OpenMapDeltaConfig::validateMarkerScale));
        MINIMAP_PLAYER_ICON = register(new ConfigValue<>("minimapPlayerIcon", "Minimap self player icon: vanilla_pointer, triangle or player_head", MinimapPlayerIcon.PLAYER_HEAD));
        MINIMAP_OTHER_PLAYER_ICON = register(new ConfigValue<>("minimapOtherPlayerIcon", "Minimap other player icons: vanilla_pointer, triangle or player_head", MinimapPlayerIcon.PLAYER_HEAD));
        MINIMAP_PLAYER_LABEL_INSIDE = register(new ConfigValue<>("minimapPlayerLabelInside", "Other player label for positions inside the minimap terrain outline", MinimapPlayerLabelMode.NONE));
        MINIMAP_PLAYER_LABEL_OUTSIDE = register(new ConfigValue<>("minimapPlayerLabelOutside", "Other player label for positions outside the minimap terrain outline", MinimapPlayerLabelMode.DISTANCE_ONLY));
        MINIMAP_ROTATE_PLAYER_HEAD = register(new ConfigValue<>("minimapRotatePlayerHead", "Rotate minimap player heads with their facing direction", false));
        FULLSCREEN_PLAYER_ICON = register(new ConfigValue<>("fullscreenPlayerIcon", "Fullscreen player icons: vanilla_pointer, triangle or player_head", MinimapPlayerIcon.PLAYER_HEAD));
        FULLSCREEN_ROTATE_PLAYER_HEAD = register(new ConfigValue<>("fullscreenRotatePlayerHead", "Rotate fullscreen player heads with their facing direction", false));
        MINIMAP_WAYPOINT_SCALE = register(new ConfigValue<>("minimapWaypointScale", "Minimap waypoint icon and label scale (0.25-2)", 0.65, OpenMapDeltaConfig::validateMarkerScale));
        MINIMAP_LABEL_LIMIT = register(new ConfigValue<>("minimapLabelLimit", "Nearest ordinary waypoint labels; -1 shows all (0-500)", 5, v -> {
            if (v < -1 || v > 500) throw new IllegalArgumentException("Label limit must be -1 to 500");
        }));
        MINIMAP_WAYPOINT_NAME_DISTANCE = register(new ConfigValue<>("minimapWaypointNameDistance", "Minimap waypoint name cutoff distance in meters; 0 disables (0-1000000)", 150.0, v -> {
            if (!Double.isFinite(v) || v < 0.0 || v > 1_000_000.0) throw new IllegalArgumentException("Minimap waypoint name distance must be 0-1000000");
        }));
        MINIMAP_MARKER_EDGE_OFFSET = register(new ConfigValue<>("minimapMarkerEdgeOffset", "Minimap marker offset from terrain circle radius in texture pixels; positive outward (-16 to 8)", 2.0, v -> {
            if (!Double.isFinite(v) || v < -16.0 || v > 8.0) throw new IllegalArgumentException("Minimap marker edge offset must be -16 to 8");
        }));
        INFO_TICK_SECONDS = register(new ConfigValue<>("infoTickSeconds", "Seconds per daytime tick for custom clock/seconds (0.001-3600)", 0.05, v -> {
            if (!Double.isFinite(v) || v < 0.001 || v > 3600) throw new IllegalArgumentException("Tick seconds must be 0.001-3600");
        }));
        INFO_SPEED_SECONDS = register(new ConfigValue<>("infoSpeedSeconds", "Horizontal speed averaging window in seconds (1-60)", 5, v -> {
            if (v < 1 || v > 60) throw new IllegalArgumentException("Speed window must be 1-60");
        }));
        INFO_TOP = registerInfo(MinimapInfoSlot.TOP, MinimapInfoType.BIOME_NAME);
        INFO_TOP_LEFT = registerInfo(MinimapInfoSlot.TOP_LEFT, MinimapInfoType.NONE);
        INFO_TOP_RIGHT = registerInfo(MinimapInfoSlot.TOP_RIGHT, MinimapInfoType.NONE);
        INFO_BOTTOM = registerInfo(MinimapInfoSlot.BOTTOM, MinimapInfoType.COORDINATES);
        INFO_BOTTOM_RIGHT = registerInfo(MinimapInfoSlot.BOTTOM_RIGHT, MinimapInfoType.DAYTIME_24);
        INFO_BOTTOM_LEFT = registerInfo(MinimapInfoSlot.BOTTOM_LEFT, MinimapInfoType.WORLD_DAYS);
        BORDER_COLOR = register(new ConfigValue<>("borderColor", "ARGB border color", 0xFF808080));
        BORDER_THICKNESS = register(new ConfigValue<>("borderThickness", "Border thickness in pixels", 2, v -> {
            if (v < 0 || v > 8) throw new IllegalArgumentException("Border thickness must be 0-8");
        }));
        TRANSPARENCY = register(new ConfigValue<>("transparency", "Global HUD alpha 0-1", 0.92, v -> {
            if (v < 0.1 || v > 1.0) throw new IllegalArgumentException("Transparency must be 0.1-1.0");
        }));
        SHOW_COMPASS = register(new ConfigValue<>("showCompass", "Show compass", true));
        SHOW_COORDINATES = register(new ConfigValue<>("showCoordinates", "Show coordinates", true));
        HIDE_WHEN_SCREEN_OPEN = register(new ConfigValue<>("hideWhenScreenOpen", "Hide when any screen is open", true));
        PERFORMANCE_MODE = register(new ConfigValue<>("performanceMode", "Performance level", PerformanceMode.MEDIUM));
        COLUMNS_PER_TICK = register(new ConfigValue<>("columnsPerTick", "Columns sampled per client tick", 256, v -> {
            if (v < 32 || v > 4096) throw new IllegalArgumentException("Must be 32-4096");
        }));
        SCAN_INTERVAL_FRAMES = register(new ConfigValue<>("scanIntervalFrames", "Frames between full scan passes", 1, v -> {
            if (v < 1 || v > 20) throw new IllegalArgumentException("Must be 1-20");
        }));
        SHOW_PLAYERS = register(new ConfigValue<>("showPlayers", "Show player markers", true));
        SHOW_WAYPOINTS = register(new ConfigValue<>("showWaypoints", "Show waypoint markers", true));
        SHOW_SHARED_WAYPOINTS = register(new ConfigValue<>("showSharedWaypoints", "Show shared waypoints", true));
        WAYPOINT_HIDE_DISTANCE = register(new ConfigValue<>("waypointHideDistance", "World waypoint beam hide radius in blocks; fades out from twice this distance (0-128, 0 disables)", 5.0, v -> {
            if (!Double.isFinite(v) || v < 0.0 || v > 128.0) {
                throw new IllegalArgumentException("Waypoint hide distance must be 0-128");
            }
        }));
        WAYPOINT_LABEL_FADE_DISTANCE = register(new ConfigValue<>("waypointLabelFadeDistance", "World waypoint icon and label fade start in blocks; hidden at half this distance (0-128, 0 disables)", 2.5, v -> {
            if (!Double.isFinite(v) || v < 0.0 || v > 128.0) {
                throw new IllegalArgumentException("Waypoint label fade distance must be 0-128");
            }
        }));
        SHOW_DEATH = register(new ConfigValue<>("showDeath", "Create and display deathpoints", true));
        SMART_DEATHPOINTS = register(new ConfigValue<>("smartDeathpoints", "Smart deathpoint recovery and inventory filtering; requires showDeath", true));
        SHOW_KEEP_INVENTORY_DEATHPOINTS = register(new ConfigValue<>("showKeepInventoryDeathpoints", "Create smart deathpoints when keepInventory retains items (last suppressed death is restorable)", false));
        DEATH_BEACON_MIN_OPACITY = register(new ConfigValue<>("deathBeaconMinOpacity", "Death beam minimum pulse opacity (0-1)", 0.25, OpenMapDeltaConfig::validateDeathOpacity));
        DEATH_BEACON_MAX_OPACITY = register(new ConfigValue<>("deathBeaconMaxOpacity", "Death beam maximum pulse opacity (0-1)", 1.0, OpenMapDeltaConfig::validateDeathOpacity));
        SMART_DEATHPOINT_BLACK_RETENTION_SECONDS = register(new ConfigValue<>("smartDeathpointBlackRetentionSeconds", "Seconds to retain a black smart deathpoint; 0 disables removal (0-86400)", 300, value -> {
            if (value < 0 || value > 86400) throw new IllegalArgumentException("Deathpoint retention must be 0-86400");
        }));
        SMART_DEATHPOINT_CHEAP_ITEMS = register(new ConfigValue<>("smartDeathpointCheapItems", "Comma-separated cheap item IDs or #item tags; decorated items always count as valuable", net.aosankaku.openmapdelta.death.DeathPointPolicy.DEFAULT_CHEAP_ITEMS));
        SHOW_BEDS = register(new ConfigValue<>("showBeds", "Show bed markers", true));
        RECORD_NON_SPAWN_SLEEP = register(new ConfigValue<>("recordNonSpawnSleep", "Record sleep locations that do not set spawn", false));
        SHOW_PORTALS = register(new ConfigValue<>("showPortals", "Show portal markers", true));
        SHOW_STRUCTURES = register(new ConfigValue<>("showStructures", "Show structure markers", false));
        SHOW_ENTITIES = register(new ConfigValue<>("showEntities", "Show entity markers", false));
        SHOW_MARKER_DISTANCE = register(new ConfigValue<>("showMarkerDistance", "Show distance on markers", true));
        STICK_MARKERS_TO_BORDER = register(new ConfigValue<>("stickMarkersToBorder", "Clamp markers to minimap edge", true));
        FULLSCREEN_DEFAULT_ZOOM = register(new ConfigValue<>("fullscreenDefaultZoom", "Default fullscreen zoom", 1, v -> {
            if (v < 1 || v > 18) throw new IllegalArgumentException("Must be 1-18");
        }));
        FULLSCREEN_SHOW_GRID = register(new ConfigValue<>("fullscreenShowGrid", "Show chunk borders on fullscreen map", true));
        FULLSCREEN_SHOW_MTR_RAILS = register(new ConfigValue<>("fullscreenShowMtrRails", "Show MTR train tracks on fullscreen map", true));
        FULLSCREEN_SHOW_CREATE_RAILS = register(new ConfigValue<>("fullscreenShowCreateRails", "Show Create train tracks on fullscreen map", true));
        FULLSCREEN_WAYPOINT_LABEL_MIN_ZOOM = register(new ConfigValue<>("fullscreenWaypointLabelMinZoom",
                "Hide fullscreen waypoint labels below this displayed zoom multiplier (0-2; 0 always shows labels)", 0.5, v -> {
            if (!Double.isFinite(v) || v < 0.0 || v > 2.0) {
                throw new IllegalArgumentException("Fullscreen waypoint label minimum zoom must be 0-2");
            }
        }));
        FULLSCREEN_WAYPOINT_LABEL_SCALE = register(new ConfigValue<>("fullscreenWaypointLabelScale",
                "Fullscreen waypoint label scale (0.25-2)", 0.75, v -> {
            if (!Double.isFinite(v) || v < 0.25 || v > 2.0) {
                throw new IllegalArgumentException("Fullscreen waypoint label scale must be 0.25-2");
            }
        }));
        FULLSCREEN_SHOW_ZOOM_BUTTONS = register(new ConfigValue<>("fullscreenShowZoomButtons", "Show zoom buttons on the fullscreen map", false));
        FULLSCREEN_GRID_THICKNESS = register(new ConfigValue<>("fullscreenGridThickness", "Fullscreen chunk border thickness in GUI pixels (0.25-4.0)", 0.5, v -> {
            if (!Double.isFinite(v) || v < 0.25 || v > 4.0) {
                throw new IllegalArgumentException("Fullscreen grid thickness must be 0.25-4.0");
            }
        }));
        PLAYER_ICON_MAX_SIZE = register(new ConfigValue<>("playerIconMaxSize", "Maximum fullscreen/web map player icon size in GUI/CSS pixels (4-64)", 16, v -> {
            if (v < 4 || v > 64) throw new IllegalArgumentException("playerIconMaxSize must be 4-64");
        }));
        BIOME_GRASS_TINT = register(new ConfigValue<>("biomeGrassTint", "Color grass and foliage using biome grass colors, except Nether and End", true));
        ASYNC_MAP_GENERATION = register(new ConfigValue<>("asyncMapGeneration", "Generate detached chunk map data on spare CPU cores", true));
        SHOW_PLAYER_HEAD = register(new ConfigValue<>("showPlayerHead", "Legacy fullscreen face toggle, imported when fullscreenPlayerIcon is absent", true));
        EXTERNAL_PLAYERS_ENABLED = register(new ConfigValue<>("externalPlayersEnabled", "Read published player positions from installed map mods and configured BlueMap", true));
        CREATE_STATION_WAYPOINTS = register(new ConfigValue<>("createStationWaypoints", "Automatically show valid Create and Steam n Rails stations", true));
        CREATE_STATION_GROUPING_RADIUS = register(new ConfigValue<>("createStationGroupingRadius", "Same-name Create station grouping radius in blocks (0-1000000; 0 disables grouping)", 50, v -> {
            if (v < 0 || v > 1_000_000) throw new IllegalArgumentException("Create station grouping radius must be 0-1000000");
        }));
        MTR_STATION_WAYPOINTS = register(new ConfigValue<>("mtrStationWaypoints", "Automatically show Minecraft Transit Railway stations with valid platforms", true));
        CAVE_LAYER_STEP = register(new ConfigValue<>("caveLayerStep", "Blocks per cave layer (1-64)", 8, v -> {
            if (v < 1 || v > 64) throw new IllegalArgumentException("Cave layer step must be 1-64");
        }));
        NAVIGATION_MAX_TARGETS = register(new ConfigValue<>("navigationMaxTargets", "Maximum simultaneous map destinations (1-16)", 3, v -> {
            if (v < 1 || v > 16) throw new IllegalArgumentException("Navigation limit must be 1-16");
        }));
        NAVIGATION_ARRIVAL_DISTANCE = register(new ConfigValue<>("navigationArrivalDistance", "Destination arrival radius (0-128, 0 disables automatic arrival)", 5.0, v -> {
            if (!Double.isFinite(v) || v < 0.0 || v > 128.0) throw new IllegalArgumentException("Arrival distance must be 0-128");
        }));
        WORLD_WAYPOINT_CLUSTER_LABEL_ORDER = register(new ConfigValue<>("worldWaypointClusterLabelOrder", "Order stacked world waypoint labels", WaypointClusterOrder.Order.DISTANCE_ASCENDING));

        MAP_FULLSCREEN_ENABLED = register(new ConfigValue<>("map.fullscreenEnabled", "Enable fullscreen map", true));
        MAP_SHOW_COORDINATES = register(new ConfigValue<>("map.showCoordinates", "Show coordinates on fullscreen map", true));
        MAP_SHOW_WAYPOINTS = register(new ConfigValue<>("map.showWaypoints", "Show waypoints on fullscreen map", true));
        MAP_SHOW_PLAYER_MARKER = register(new ConfigValue<>("map.showPlayerMarker", "Show player on fullscreen map", true));
        MAP_MAX_WAYPOINTS_RENDERED = register(new ConfigValue<>("map.maxWaypointsRendered", "Max waypoints rendered on map", 100, v -> {
            if (v < 10 || v > 500) throw new IllegalArgumentException("Must be 10-500");
        }));
        MAP_SAFE_RENDER_MODE = register(new ConfigValue<>("map.safeRenderMode", "Reduce rendering load (slower but stable)", false));
    }

    private static <T> ConfigValue<T> register(ConfigValue<T> val) {
        ALL_VALUES.add(val);
        return val;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void load() {
        Path path = getConfigPath();
        if (!Files.exists(path)) {
            save();
            return;
        }
        try (Reader reader = Files.newBufferedReader(path)) {
            JsonObject root = GSON.fromJson(reader, JsonObject.class);
            if (root == null) return;
            loadLegacyPlayerIcon(root);

            JsonObject mapObj = root.has("map") && root.get("map").isJsonObject()
                    ? root.getAsJsonObject("map") : null;

            for (ConfigValue val : ALL_VALUES) {
                if (isMinimapPlayerIconSetting(val) || val == MINIMAP_LAYER_MODE || val == MINIMAP_CAVE_Y) continue;
                String key = val.getKey();
                JsonElement elem = null;

                if (mapObj != null && key.startsWith("map.")) {
                    String subKey = key.substring(4);
                    if (mapObj.has(subKey)) {
                        elem = mapObj.get(subKey);
                    }
                }
                if (elem == null && root.has(key)) {
                    elem = root.get(key);
                }

                if (isMinimapSetting(val) && elem != null) {
                    loadMinimapSetting(val, elem);
                    continue;
                }
                if (val == MINIMAP_SCALE && elem != null) {
                    loadMinimapScale(elem);
                    continue;
                }
                if ((val == WAYPOINT_HIDE_DISTANCE || val == WAYPOINT_LABEL_FADE_DISTANCE) && elem != null) {
                    loadWaypointDistance(val, elem);
                    continue;
                }
                if ((val == PLAYER_ICON_MAX_SIZE || val == FULLSCREEN_DEFAULT_ZOOM || val == FULLSCREEN_WAYPOINT_LABEL_MIN_ZOOM || val == FULLSCREEN_WAYPOINT_LABEL_SCALE || val == CREATE_STATION_GROUPING_RADIUS || val == CAVE_LAYER_STEP || val == DEATH_BEACON_MIN_OPACITY || val == DEATH_BEACON_MAX_OPACITY || val == SMART_DEATHPOINT_BLACK_RETENTION_SECONDS || val == NAVIGATION_MAX_TARGETS || val == NAVIGATION_ARRIVAL_DISTANCE) && elem != null) {
                    loadLayerAndNavigationSetting(val, elem);
                    continue;
                }
                if (elem != null && elem.isJsonPrimitive()) {
                    JsonPrimitive prim = elem.getAsJsonPrimitive();
                    if (val.get() instanceof Boolean && prim.isBoolean()) {
                        val.set(prim.getAsBoolean());
                    } else if (val.get() instanceof Integer && prim.isNumber()) {
                        val.set(prim.getAsInt());
                    } else if (val.get() instanceof Double && prim.isNumber()) {
                        val.set(prim.getAsDouble());
                    } else if (val.get() instanceof String && prim.isString()) {
                        val.set(prim.getAsString());
                    } else if (val.get() instanceof Enum && prim.isString()) {
                        val.setFromObject(prim.getAsString());
                    }
                }
            }
            loadMinimapPlayerIcons(root);
            loadMinimapPlayerLabelModes(root);
            loadMinimapLayerSettings(root);
        } catch (IOException e) {
            LOG.error("Failed to load config", e);
        } finally {
            if (!SHOW_DEATH.get()) SMART_DEATHPOINTS.set(false);
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void loadLayerAndNavigationSetting(ConfigValue value, JsonElement element) {
        try {
            if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber())
                throw new IllegalArgumentException("Setting must be a number");
            double number = element.getAsDouble();
            if (!Double.isFinite(number)) throw new IllegalArgumentException("Non-finite setting");
            if (value.getDefault() instanceof Integer) {
                if (number != Math.rint(number) || number < Integer.MIN_VALUE || number > Integer.MAX_VALUE)
                    throw new IllegalArgumentException("Setting must be an integer");
                value.set((int) number);
            } else {
                value.set(number);
            }
        } catch (IllegalArgumentException e) {
            value.set(value.getDefault());
            LOG.warn("Invalid {}; using default {}", value.getKey(), value.getDefault());
        }
    }

    private static void loadMinimapLayerSettings(JsonObject root) {
        JsonObject map = root.has("map") && root.get("map").isJsonObject()
                ? root.getAsJsonObject("map") : null;
        JsonElement mode = map != null && map.has(MINIMAP_LAYER_MODE.getKey())
                ? map.get(MINIMAP_LAYER_MODE.getKey()) : root.get(MINIMAP_LAYER_MODE.getKey());
        if (mode != null) {
            try {
                if (!mode.isJsonPrimitive() || !mode.getAsJsonPrimitive().isString()) {
                    throw new IllegalArgumentException("Minimap layer mode must be a string");
                }
                String name = mode.getAsString();
                MinimapLayerMode selected = null;
                for (MinimapLayerMode candidate : MinimapLayerMode.values()) {
                    if (candidate.name().equalsIgnoreCase(name)) {
                        selected = candidate;
                        break;
                    }
                }
                if (selected == null) throw new IllegalArgumentException("Unknown minimap layer mode");
                MINIMAP_LAYER_MODE.set(selected);
            } catch (IllegalArgumentException invalid) {
                MINIMAP_LAYER_MODE.set(MINIMAP_LAYER_MODE.getDefault());
                LOG.warn("Invalid {}; using default {}", MINIMAP_LAYER_MODE.getKey(), MINIMAP_LAYER_MODE.getDefault());
            }
        }

        JsonElement caveY = map != null && map.has(MINIMAP_CAVE_Y.getKey())
                ? map.get(MINIMAP_CAVE_Y.getKey()) : root.get(MINIMAP_CAVE_Y.getKey());
        if (caveY != null) loadLayerAndNavigationSetting(MINIMAP_CAVE_Y, caveY);
    }

    private static void loadWaypointDistance(ConfigValue<?> value, JsonElement element) {
        try {
            if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
                throw new IllegalArgumentException("Waypoint fade distance must be a number");
            }
            setWaypointDistance(value, element.getAsDouble());
        } catch (IllegalArgumentException e) {
            setWaypointDistance(value, ((Number) value.getDefault()).doubleValue());
            LOG.warn("Invalid {}; using default {}", value.getKey(), value.getDefault());
        }
    }

    private static void setWaypointDistance(ConfigValue<?> value, double distance) {
        if (value == WAYPOINT_LABEL_FADE_DISTANCE) WAYPOINT_LABEL_FADE_DISTANCE.set(distance);
        else WAYPOINT_HIDE_DISTANCE.set(distance);
    }

    private static void loadMinimapScale(JsonElement element) {
        try {
            if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
                throw new IllegalArgumentException("Minimap scale must be a number");
            }
            MINIMAP_SCALE.set(element.getAsDouble());
        } catch (IllegalArgumentException e) {
            MINIMAP_SCALE.set(MINIMAP_SCALE.getDefault());
            LOG.warn("Invalid minimapScale; using default {}", MINIMAP_SCALE.getDefault());
        }
    }

    public static void save() {
        if (!SHOW_DEATH.get()) SMART_DEATHPOINTS.set(false);
        Path path = getConfigPath();
        Map<String, Object> root = new LinkedHashMap<>();
        Map<String, Object> mapSection = new LinkedHashMap<>();

        for (ConfigValue<?> val : ALL_VALUES) {
            String key = val.getKey();
            if (key.startsWith("map.")) {
                mapSection.put(key.substring(4), val.get());
            } else {
                root.put(key, val.get());
            }
        }
        if (!mapSection.isEmpty()) {
            root.put("map", mapSection);
        }

        try {
            Files.createDirectories(path.getParent());
            try (Writer writer = Files.newBufferedWriter(path)) {
                GSON.toJson(root, writer);
            }
        } catch (IOException e) {
            LOG.error("Failed to save config", e);
        }
    }

    public static void reload() {
        load();
    }

    private static Path getConfigPath() {
        return DataPaths.configDirectory().resolve("config.json");
    }

    private static void validateMarkerScale(double value) {
        if (!Double.isFinite(value) || value < 0.25 || value > 2) throw new IllegalArgumentException("Marker scale must be 0.25-2");
    }

    public static boolean effectiveSmartDeathpoints() { return SHOW_DEATH.get() && SMART_DEATHPOINTS.get(); }

    public static void setDeathpointsEnabled(boolean enabled) {
        SHOW_DEATH.set(enabled);
        if (!enabled) SMART_DEATHPOINTS.set(false);
    }

    public static void setSmartDeathpointsEnabled(boolean enabled) {
        SMART_DEATHPOINTS.set(enabled && SHOW_DEATH.get());
    }

    private static void validateDeathOpacity(double value) {
        if (!Double.isFinite(value) || value < 0 || value > 1) throw new IllegalArgumentException("Death beam opacity must be 0-1");
    }

    private static ConfigValue<MinimapInfoType> registerInfo(MinimapInfoSlot slot, MinimapInfoType defaultType) {
        return register(new ConfigValue<>("info" + switch (slot) {
            case TOP -> "Top";
            case TOP_LEFT -> "TopLeft";
            case TOP_RIGHT -> "TopRight";
            case BOTTOM -> "Bottom";
            case BOTTOM_RIGHT -> "BottomRight";
            case BOTTOM_LEFT -> "BottomLeft";
        }, "Information in " + slot.name(), defaultType, type -> {
            if (!slot.accepts(type)) throw new IllegalArgumentException("Wide information requires top or bottom slot");
        }));
    }

    public static ConfigValue<MinimapInfoType> infoSlot(MinimapInfoSlot slot) {
        return switch (slot) {
            case TOP -> INFO_TOP;
            case TOP_LEFT -> INFO_TOP_LEFT;
            case TOP_RIGHT -> INFO_TOP_RIGHT;
            case BOTTOM -> INFO_BOTTOM;
            case BOTTOM_RIGHT -> INFO_BOTTOM_RIGHT;
            case BOTTOM_LEFT -> INFO_BOTTOM_LEFT;
        };
    }

    /** Import the former fullscreen head toggle only when no explicit icon choice exists. */
    private static void loadLegacyPlayerIcon(JsonObject root) {
        JsonObject map = root.has("map") && root.get("map").isJsonObject()
                ? root.getAsJsonObject("map") : null;
        String iconKey = FULLSCREEN_PLAYER_ICON.getKey();
        if (root.has(iconKey) || map != null && map.has(iconKey)) return;
        JsonElement legacy = root.get(SHOW_PLAYER_HEAD.getKey());
        if (map != null && map.has(SHOW_PLAYER_HEAD.getKey())) legacy = map.get(SHOW_PLAYER_HEAD.getKey());
        boolean heads = legacy == null || !legacy.isJsonPrimitive()
                || !legacy.getAsJsonPrimitive().isBoolean() || legacy.getAsBoolean();
        FULLSCREEN_PLAYER_ICON.set(heads ? MinimapPlayerIcon.PLAYER_HEAD : MinimapPlayerIcon.TRIANGLE);
    }

    private static boolean isMinimapPlayerIconSetting(ConfigValue<?> value) {
        return value == MINIMAP_PLAYER_ICON || value == MINIMAP_OTHER_PLAYER_ICON;
    }

    private static void loadMinimapPlayerIcons(JsonObject root) {
        JsonObject map = root.has("map") && root.get("map").isJsonObject()
                ? root.getAsJsonObject("map") : null;

        JsonElement selfIcon = map != null && map.has(MINIMAP_PLAYER_ICON.getKey())
                ? map.get(MINIMAP_PLAYER_ICON.getKey())
                : root.has(MINIMAP_PLAYER_ICON.getKey()) ? root.get(MINIMAP_PLAYER_ICON.getKey()) : null;
        if (selfIcon != null) loadMinimapSetting(MINIMAP_PLAYER_ICON, selfIcon);

        JsonElement otherIcon = map != null && map.has(MINIMAP_OTHER_PLAYER_ICON.getKey())
                ? map.get(MINIMAP_OTHER_PLAYER_ICON.getKey())
                : root.has(MINIMAP_OTHER_PLAYER_ICON.getKey()) ? root.get(MINIMAP_OTHER_PLAYER_ICON.getKey()) : null;
        if (otherIcon != null) {
            loadMinimapSetting(MINIMAP_OTHER_PLAYER_ICON, otherIcon);
        } else {
            // Existing installations stored one shared icon choice; carry it forward for both groups.
            MINIMAP_OTHER_PLAYER_ICON.set(MINIMAP_PLAYER_ICON.get());
        }
    }

    private static void loadMinimapPlayerLabelModes(JsonObject root) {
        JsonObject map = root.has("map") && root.get("map").isJsonObject()
                ? root.getAsJsonObject("map") : null;
        JsonElement inside = map != null && map.has(MINIMAP_PLAYER_LABEL_INSIDE.getKey())
                ? map.get(MINIMAP_PLAYER_LABEL_INSIDE.getKey()) : root.get(MINIMAP_PLAYER_LABEL_INSIDE.getKey());
        JsonElement outside = map != null && map.has(MINIMAP_PLAYER_LABEL_OUTSIDE.getKey())
                ? map.get(MINIMAP_PLAYER_LABEL_OUTSIDE.getKey()) : root.get(MINIMAP_PLAYER_LABEL_OUTSIDE.getKey());
        MINIMAP_PLAYER_LABEL_INSIDE.set(MINIMAP_PLAYER_LABEL_INSIDE.getDefault());
        MINIMAP_PLAYER_LABEL_OUTSIDE.set(MINIMAP_PLAYER_LABEL_OUTSIDE.getDefault());
        if (inside != null) loadMinimapSetting(MINIMAP_PLAYER_LABEL_INSIDE, inside);
        if (outside != null) loadMinimapSetting(MINIMAP_PLAYER_LABEL_OUTSIDE, outside);
    }

    private static boolean isMinimapSetting(ConfigValue<?> value) {
        return value == BORDER_STYLE || value == MINIMAP_PLAYER_SCALE || value == MINIMAP_PLAYER_ICON
                || value == MINIMAP_OTHER_PLAYER_ICON || value == MINIMAP_PLAYER_LABEL_INSIDE
                || value == MINIMAP_PLAYER_LABEL_OUTSIDE
                || value == MINIMAP_ROTATE_PLAYER_HEAD || value == FULLSCREEN_PLAYER_ICON
                || value == FULLSCREEN_ROTATE_PLAYER_HEAD || value == MINIMAP_WAYPOINT_SCALE
                || value == MINIMAP_LABEL_LIMIT || value == MINIMAP_WAYPOINT_NAME_DISTANCE || value == MINIMAP_MARKER_EDGE_OFFSET
                || value == INFO_TICK_SECONDS || value == INFO_SPEED_SECONDS
                || value.getDefault() instanceof MinimapInfoType;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void loadMinimapSetting(ConfigValue value, JsonElement element) {
        try {
            if (!element.isJsonPrimitive()) throw new IllegalArgumentException("Expected primitive");
            JsonPrimitive primitive = element.getAsJsonPrimitive();
            Object defaultValue = value.getDefault();
            if (defaultValue instanceof Enum<?> enumValue && primitive.isString()) {
                value.set(Enum.valueOf(enumValue.getDeclaringClass(), primitive.getAsString().toUpperCase(java.util.Locale.ROOT)));
            } else if (defaultValue instanceof Boolean && primitive.isBoolean()) {
                value.set(primitive.getAsBoolean());
            } else if (defaultValue instanceof Integer && primitive.isNumber()) {
                double number = primitive.getAsDouble();
                if (!Double.isFinite(number) || number != Math.rint(number)) throw new IllegalArgumentException("Expected integer");
                value.set(Math.toIntExact(primitive.getAsLong()));
            } else if (defaultValue instanceof Double && primitive.isNumber()) {
                value.set(primitive.getAsDouble());
            } else throw new IllegalArgumentException("Unexpected value type");
        } catch (IllegalArgumentException | ArithmeticException e) {
            value.set(value.getDefault());
            LOG.warn("Invalid {}; using default {}", value.getKey(), value.getDefault());
        }
    }

    public enum BorderStyle { SOLID, DOTTED }

    public enum ScreenPosition {
        TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT
    }

    public enum MinimapShape {
        CIRCLE, SQUARE
    }

    public enum RotationMode {
        HEADING_UP, NORTH_UP
    }

    public enum MinimapPlayerIcon {
        VANILLA_POINTER, TRIANGLE, PLAYER_HEAD
    }

    public enum MinimapPlayerLabelMode {
        NAME_ONLY, DISTANCE_ONLY, NONE, NAME_AND_DISTANCE
    }

    public enum PerformanceMode {
        LOW(64, 128, 3),
        MEDIUM(128, 256, 1),
        HIGH(192, 512, 1);

        public final int radiusBlocks;
        public final int columnsPerTick;
        public final int scanInterval;

        PerformanceMode(int radiusBlocks, int columnsPerTick, int scanInterval) {
            this.radiusBlocks = radiusBlocks;
            this.columnsPerTick = columnsPerTick;
            this.scanInterval = scanInterval;
        }
    }

    public static int effectiveColumnsPerTick() {
        PerformanceMode mode = PERFORMANCE_MODE.get();
        return Math.min(COLUMNS_PER_TICK.get(), mode.columnsPerTick);
    }

    public static int effectiveScanRadius() {
        return PERFORMANCE_MODE.get().radiusBlocks;
    }

    public static int effectiveScanInterval() {
        PerformanceMode mode = PERFORMANCE_MODE.get();
        return Math.max(SCAN_INTERVAL_FRAMES.get(), mode.scanInterval);
    }
}
