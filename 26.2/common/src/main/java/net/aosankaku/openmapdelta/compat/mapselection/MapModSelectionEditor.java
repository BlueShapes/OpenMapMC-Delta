// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.compat.mapselection;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Applies one provider selection to config files and live settings as a single transaction. */
public final class MapModSelectionEditor {
    private static final Pattern VERSION_PREFIX = Pattern.compile("^(\\d+)\\.(\\d+)\\.(\\d+)");
    private static final int MAX_JOURNEY_WORLD_DIRECTORIES = 4096;
    private static final int MAX_JOURNEY_CONFIG_DOCUMENTS = 256;
    private static final long MAX_JOURNEY_CONFIG_BYTES = 16L * 1024 * 1024;

    private MapModSelectionEditor() {}

    /** Disables other installed HUD providers and atomically acknowledges the selected one. */
    public static void apply(Path configDir, String selectedId, Map<String, String> installedVersions) throws IOException {
        if (selectedId == null || !MapModSelectionModel.PROVIDER_IDS.contains(selectedId)) {
            throw new IllegalArgumentException("A selected installed provider is required");
        }
        if (installedVersions == null || !installedVersions.containsKey(selectedId)) {
            throw new IllegalArgumentException("Selected provider is not installed: " + selectedId);
        }
        applyTransaction(configDir, selectedId, installedVersions, true);
    }

    /** Records an explicit keep-current choice without changing provider settings. */
    public static void acknowledge(Path configDir, Map<String, String> installedVersions) throws IOException {
        applyTransaction(configDir, null, installedVersions, false);
    }

    private static void applyTransaction(Path configDir, String selectedId,
                                         Map<String, String> installedVersions,
                                         boolean editProviders) throws IOException {
        if (configDir == null || installedVersions == null) throw new IllegalArgumentException("Config path and versions are required");
        String fingerprint = MapModSelectionModel.fingerprint(installedVersions);
        Path configRoot = configDir.toAbsolutePath().normalize();
        if (!Files.isDirectory(configRoot)) throw new IOException("Minecraft config directory is missing: " + configRoot);
        Path gameRoot = configRoot.getParent();
        if (gameRoot == null) throw new IOException("Minecraft config directory has no game directory parent");

        MapModSelectionState oldState;
        try {
            oldState = MapModSelectionState.read(configRoot);
        } catch (MapModSelectionState.InvalidStateException malformedState) {
            oldState = null;
        }
        Map<String, Boolean> restoreFlags = new LinkedHashMap<>(oldState == null ? Map.of() : oldState.restoreFlags());
        Transaction transaction = new Transaction();

        if (editProviders) {
            for (String providerId : MapModSelectionModel.PROVIDER_IDS.stream().sorted().toList()) {
                if (!installedVersions.containsKey(providerId)) continue;
                List<ProviderFlag> flags = switch (providerId) {
                    case MapModSelectionModel.ODYSSEY_MAP -> odysseyFlags(configRoot, gameRoot);
                    case MapModSelectionModel.JOURNEY_MAP -> journeyMapFlags(configRoot, gameRoot);
                    case MapModSelectionModel.XAERO_MINIMAP -> xaeroFlags(configRoot, gameRoot,
                            installedVersions.get(providerId));
                    default -> throw new IllegalStateException("Unexpected map provider " + providerId);
                };
                boolean selected = providerId.equals(selectedId);
                for (ProviderFlag flag : flags) {
                    Boolean configuredValue = flag.document().readBoolean(flag.key());
                    Boolean oldValue = flag.access() == null
                            ? (configuredValue == null ? flag.defaultValue() : configuredValue)
                            : flag.access().get();
                    if (oldValue == null) throw new IOException("Provider returned an unavailable boolean setting: " + flag.key());
                    Boolean target = null;
                    boolean wasSnapshotted = restoreFlags.containsKey(flag.snapshotKey());
                    if (flag.primary()) {
                        target = selected;
                        if (selected) restoreFlags.remove(flag.snapshotKey());
                        else if (!wasSnapshotted && oldValue && flag.snapshotOnDisable()) {
                            restoreFlags.put(flag.snapshotKey(), true);
                        }
                    } else if (flag.selectionOnly()) {
                        if (selected) target = true;
                    } else if (!selected) {
                        target = false;
                        if (!wasSnapshotted && oldValue) restoreFlags.put(flag.snapshotKey(), true);
                    } else if (wasSnapshotted) {
                        target = restoreFlags.remove(flag.snapshotKey());
                    }
                    if (target != null) {
                        if (!target.equals(configuredValue)) transaction.patch(flag, target);
                        if (flag.access() != null && target != oldValue) transaction.mutate(flag.access(), oldValue, target);
                    }
                }
            }
        }

        MapModSelectionState nextState = new MapModSelectionState(selectedId, fingerprint, restoreFlags);
        transaction.commit(configRoot, MapModSelectionState.path(configRoot), nextState.toBytes());
    }

    private static List<ProviderFlag> odysseyFlags(Path configRoot, Path gameRoot) throws IOException {
        Path file = configRoot.resolve("openmapdelta/config.json");
        var document = MapModSelectionFiles.document(configRoot, file, false);
        Object mapEnabled = staticField("net.aosankaku.openmapdelta.config.OpenMapDeltaConfig", "MAP_MOD_ENABLED");
        Object hudEnabled = staticField("net.aosankaku.openmapdelta.config.OpenMapDeltaConfig", "ENABLED");
        return List.of(
                noSnapshotFlag(MapModSelectionModel.ODYSSEY_MAP, gameRoot, file, "mapModEnabled", true,
                        configValueAccess(mapEnabled, "MAP_MOD_ENABLED"), configRoot, document),
                selectionOnlyFlag(MapModSelectionModel.ODYSSEY_MAP, gameRoot, file, "enabled",
                        configValueAccess(hudEnabled, "ENABLED"), configRoot, document));
    }

    private static List<ProviderFlag> journeyMapFlags(Path configRoot, Path gameRoot) throws IOException {
        Object client = invokeStatic("journeymap.client.JourneymapClient", "getInstance");
        if (client == null) throw new IOException("JourneyMap client is not initialized");
        Object core = invoke(client, "getCoreProperties");
        Object mini1 = invoke(client, "getMiniMapProperties1");
        Object mini2 = invoke(client, "getMiniMapProperties2");
        Object waypoint = invoke(client, "getWaypointProperties");
        Object web = invoke(client, "getWebMapProperties");

        Path allowedRoot = gameRoot.resolve("journeymap");
        Path standardConfigDirectory = asPath(staticField("journeymap.client.io.FileHandler", "StandardConfigDirectory"));
        if (!standardConfigDirectory.toAbsolutePath().normalize().startsWith(allowedRoot.toAbsolutePath().normalize())
                || !Files.isDirectory(standardConfigDirectory)) {
            throw new IOException("JourneyMap standard config directory is outside its config root");
        }
        List<ProviderFlag> flags = new ArrayList<>();
        Map<Path, MapModSelectionFiles.Document> documents = new HashMap<>();
        flags.addAll(journeyFlagsForProperty(gameRoot, allowedRoot, standardConfigDirectory, core,
                "mappingEnabled", "journeymap.core.config", true, documents));
        flags.addAll(journeyFlagsForProperty(gameRoot, allowedRoot, standardConfigDirectory, mini1,
                "enabled", "journeymap.minimap.config", true, documents));
        flags.addAll(journeyFlagsForProperty(gameRoot, allowedRoot, standardConfigDirectory, mini2,
                "enabled", "journeymap.minimap2.config", false, documents));
        flags.addAll(journeyFlagsForProperty(gameRoot, allowedRoot, standardConfigDirectory, waypoint,
                "beaconEnabled", "journeymap.waypoint.config", false, documents));
        flags.addAll(journeyFlagsForProperty(gameRoot, allowedRoot, standardConfigDirectory, web,
                "enabled", "journeymap.webmap.config", false, documents));
        addOptionalJourneyFlag(flags, MapModSelectionModel.JOURNEY_MAP, gameRoot, allowedRoot,
                standardConfigDirectory, waypoint, "renderWaypointsWorld", "journeymap.waypoint.config", documents);
        return flags;
    }

    private static List<ProviderFlag> journeyFlagsForProperty(Path gameRoot, Path journeyRoot,
                                                               Path standardConfigDirectory,
                                                               Object properties, String fieldName,
                                                               String expectedFileName, boolean primary,
                                                               Map<Path, MapModSelectionFiles.Document> documents) throws IOException {
        Object field = readField(properties, fieldName, false);
        List<ProviderFlag> flags = new ArrayList<>();
        flags.add(journeyFlag(gameRoot, journeyRoot, standardConfigDirectory, properties,
                field, fieldName, primary, true, documents));
        String fileName = configFileName(properties, expectedFileName);
        Path globalFile = standardConfigDirectory.resolve(fileName);
        Path currentFile = asPath(invoke(properties, "getFile")).toAbsolutePath().normalize();
        if (!globalFile.toAbsolutePath().normalize().equals(currentFile)) {
            flags.add(journeyOfflineFlag(gameRoot, journeyRoot, globalFile, field, fieldName,
                    primary, documents));
        }
        for (Path worldFile : journeyWorldConfigFiles(journeyRoot, fileName)) {
            if (!worldFile.toAbsolutePath().normalize().equals(currentFile)) {
                flags.add(journeyOfflineFlag(gameRoot, journeyRoot, worldFile, field, fieldName,
                        primary, documents));
            }
        }
        return flags;
    }

    private static ProviderFlag journeyFlag(Path gameRoot, Path journeyRoot, Path standardConfigDirectory,
                                             Object properties, Object field, String fieldName,
                                             boolean primary, boolean hasLiveAccess,
                                             Map<Path, MapModSelectionFiles.Document> documents) throws IOException {
        Method getter = method(field.getClass(), "get", 0);
        Method setter = method(field.getClass(), "set", 1);
        if (getter == null || setter == null) throw new IOException("JourneyMap setting is not readable and writable: " + fieldName);
        BooleanAccess access = new BooleanAccess() {
            public Boolean get() throws IOException { return asBoolean(invokeMethod(field, getter), "JourneyMap " + fieldName); }
            public void set(boolean value) throws IOException { invokeMethod(field, setter, value); }
        };
        Path file = asPath(invoke(properties, "getFile"));
        file = validateJourneyMapConfigPath(journeyRoot, file);
        MapModSelectionFiles.Document document = journeyDocument(journeyRoot, file, documents);
        // JourneyMap persists the reflected field name; ConfigField.getKey() is a UI translation key.
        String key = fieldName;
        boolean defaultValue = configDefault(field, fieldName);
        return providerFlag(MapModSelectionModel.JOURNEY_MAP, gameRoot, file, key, primary,
                hasLiveAccess ? access : null, defaultValue, journeyRoot, document);
    }

    private static void addOptionalJourneyFlag(List<ProviderFlag> flags, String provider, Path gameRoot,
                                               Path allowedRoot, Path standardConfigDirectory,
                                               Object properties, String name, String expectedFileName,
                                               Map<Path, MapModSelectionFiles.Document> documents) throws IOException {
        if (readField(properties, name, true) != null) {
            flags.addAll(journeyFlagsForProperty(gameRoot, allowedRoot, standardConfigDirectory,
                    properties, name, expectedFileName, false, documents));
        }
    }

    private static ProviderFlag journeyOfflineFlag(Path gameRoot, Path journeyRoot, Path file,
                                                    Object field, String fieldName, boolean primary,
                                                    Map<Path, MapModSelectionFiles.Document> documents) throws IOException {
        file = validateJourneyMapConfigPath(journeyRoot, file);
        MapModSelectionFiles.Document document = journeyDocument(journeyRoot, file, documents);
        return providerFlag(MapModSelectionModel.JOURNEY_MAP, gameRoot, file,
                fieldName, primary, null, configDefault(field, fieldName),
                journeyRoot, document);
    }

    private static MapModSelectionFiles.Document journeyDocument(Path journeyRoot, Path file,
                                                                   Map<Path, MapModSelectionFiles.Document> documents) throws IOException {
        MapModSelectionFiles.Document document = documents.get(file);
        if (document == null) {
            if (documents.size() >= MAX_JOURNEY_CONFIG_DOCUMENTS) {
                throw new IOException("JourneyMap has too many config files to inspect safely");
            }
            document = MapModSelectionFiles.document(journeyRoot, file, false);
            long totalBytes = document.originalSize();
            for (MapModSelectionFiles.Document existing : documents.values()) totalBytes += existing.originalSize();
            if (totalBytes > MAX_JOURNEY_CONFIG_BYTES) {
                throw new IOException("JourneyMap config files exceed the combined size limit");
            }
            documents.put(file, document);
        }
        return document;
    }

    private static String configFileName(Object properties, String expectedFileName) throws IOException {
        Path file = asPath(invoke(properties, "getFile"));
        Path name = file.getFileName();
        if (name == null || !name.toString().equals(expectedFileName)) {
            throw new IOException("JourneyMap returned an unexpected settings filename: " + file);
        }
        return name.toString();
    }

    private static boolean configDefault(Object field, String fieldName) throws IOException {
        Object value = invoke(field, "getDefaultValue");
        return asBoolean(value, "JourneyMap default for " + fieldName);
    }

    private static List<Path> journeyWorldConfigFiles(Path journeyRoot, String fileName) throws IOException {
        List<Path> files = new ArrayList<>();
        int worldsSeen = 0;
        for (String type : List.of("sp", "mp")) {
            Path typeDirectory = journeyRoot.resolve("data").resolve(type);
            if (!Files.exists(typeDirectory, LinkOption.NOFOLLOW_LINKS)) continue;
            if (Files.isSymbolicLink(typeDirectory) || !Files.isDirectory(typeDirectory, LinkOption.NOFOLLOW_LINKS)) {
                throw new IOException("JourneyMap world config directory is not a safe directory: " + typeDirectory);
            }
            try (var worlds = Files.newDirectoryStream(typeDirectory)) {
                for (Path world : worlds) {
                    if (++worldsSeen > MAX_JOURNEY_WORLD_DIRECTORIES) {
                        throw new IOException("JourneyMap has too many world config directories to inspect safely");
                    }
                    if (Files.isSymbolicLink(world)) throw new IOException("JourneyMap world directory is a symlink: " + world);
                    if (!Files.isDirectory(world, LinkOption.NOFOLLOW_LINKS)) continue;
                    Path configDirectory = world.resolve("config");
                    if (!Files.exists(configDirectory, LinkOption.NOFOLLOW_LINKS)) continue;
                    if (Files.isSymbolicLink(configDirectory)
                            || !Files.isDirectory(configDirectory, LinkOption.NOFOLLOW_LINKS)) {
                        throw new IOException("JourneyMap world config path is not a safe directory: " + configDirectory);
                    }
                    Path file = configDirectory.resolve(fileName);
                    if (Files.exists(file, LinkOption.NOFOLLOW_LINKS)) {
                        files.add(validateJourneyMapConfigPath(journeyRoot, file));
                    }
                }
            }
        }
        return files;
    }

    private static List<ProviderFlag> xaeroFlags(Path configRoot, Path gameRoot, String version) throws IOException {
        if (version == null) throw new IOException("Xaero Minimap version is unknown");
        return compareVersion(version, 25, 3, 2) >= 0
                ? modernXaeroFlags(configRoot, gameRoot)
                : legacyXaeroFlags(configRoot, gameRoot);
    }

    private static List<ProviderFlag> modernXaeroFlags(Path configRoot, Path gameRoot) throws IOException {
        String base = "xaero.minimap.XaeroMinimap";
        Object instance = staticField(base, "instance");
        Object hudConfigs = invoke(instance, "getHudConfigs");
        Object manager = invoke(hudConfigs, "getClientConfigManager");
        Object profile = invoke(manager, "getCurrentProfile");
        if (profile == null) throw new IOException("Xaero Minimap has no active client profile");
        Object profileId = invoke(profile, "getId");
        if (!(profileId instanceof String id) || !id.matches("[A-Za-z0-9._-]{1,128}")) {
            throw new IOException("Xaero Minimap returned an unsafe profile ID");
        }
        Path root = asPath(invoke(instance, "getConfigSubFolder"));
        Path allowedRoot = configRoot.resolve("xaero/minimap");
        if (!root.toAbsolutePath().normalize().startsWith(allowedRoot.toAbsolutePath().normalize())) {
            throw new IOException("Xaero Minimap config directory is outside its allowlisted root");
        }
        Path file = root.resolve("profiles").resolve(id + ".cfg");
        file = MapModSelectionFiles.validatePath(allowedRoot, file);
        MapModSelectionFiles.Document document = MapModSelectionFiles.document(allowedRoot, file, true);
        Class<?> options = loadClass("xaero.hud.minimap.common.config.option.MinimapProfiledConfigOptions");
        List<ProviderFlag> flags = new ArrayList<>();
        flags.add(xaeroProfileFlag(MapModSelectionModel.XAERO_MINIMAP, gameRoot, allowedRoot,
                file, document, profile, options, "DISPLAY_MINIMAP", true, "display_minimap"));
        flags.add(xaeroProfileFlag(MapModSelectionModel.XAERO_MINIMAP, gameRoot, allowedRoot,
                file, document, profile, options, "WAYPOINTS_IN_WORLD", false, "waypoints_in_world"));
        flags.add(xaeroProfileFlag(MapModSelectionModel.XAERO_MINIMAP, gameRoot, allowedRoot,
                file, document, profile, options, "WAYPOINTS_ON_MINIMAP", false, "waypoints_on_minimap"));
        addOptionalXaeroProfileFlag(flags, gameRoot, allowedRoot, file, document, profile, options,
                "TRACKED_PLAYERS_IN_WORLD", "tracked_players_in_world");
        return flags;
    }

    private static ProviderFlag xaeroProfileFlag(String provider, Path gameRoot, Path allowedRoot,
                                                  Path file, MapModSelectionFiles.Document document,
                                                  Object profile, Class<?> options, String optionName,
                                                  boolean primary, String configKey) throws IOException {
        Object option = staticField(options, optionName);
        String id = String.valueOf(invoke(option, "getId"));
        if (!id.equals(configKey)) throw new IOException("Unexpected Xaero setting ID for " + optionName + ": " + id);
        Method getter = method(profile.getClass(), "get", 1);
        Method setter = method(profile.getClass(), "set", 2);
        if (getter == null || setter == null) throw new IOException("Xaero profile does not expose setting accessors");
        BooleanAccess access = new BooleanAccess() {
            public Boolean get() throws IOException { return asBoolean(invokeMethod(profile, getter, option), "Xaero " + configKey); }
            public void set(boolean value) throws IOException { invokeMethod(profile, setter, option, value); }
        };
        return flag(provider, gameRoot, file, configKey, primary, access, allowedRoot, document, true);
    }

    private static void addOptionalXaeroProfileFlag(List<ProviderFlag> flags, Path gameRoot,
                                                     Path allowedRoot, Path file,
                                                     MapModSelectionFiles.Document document,
                                                     Object profile, Class<?> options,
                                                     String optionName, String configKey) throws IOException {
        try {
            flags.add(xaeroProfileFlag(MapModSelectionModel.XAERO_MINIMAP, gameRoot, allowedRoot,
                    file, document, profile, options, optionName, false, configKey));
        } catch (IOException e) {
            if (!e.getMessage().contains("No such field")) throw e;
        }
    }

    private static List<ProviderFlag> legacyXaeroFlags(Path configRoot, Path gameRoot) throws IOException {
        Object instance = staticField("xaero.minimap.XaeroMinimap", "instance");
        Object settings = invoke(instance, "getSettings");
        Path file = configRoot.resolve("xaerominimap.txt");
        MapModSelectionFiles.Document document = MapModSelectionFiles.document(configRoot, file, true, ":");
        Object module = staticField("xaero.hud.minimap.BuiltInHudModules", "MINIMAP");
        Method isActive = method(module.getClass(), "isActive", 0);
        Method setActive = method(module.getClass(), "setActive", 1);
        if (isActive == null || setActive == null) throw new IOException("Xaero minimap HUD module cannot be controlled");
        BooleanAccess minimap = new BooleanAccess() {
            public Boolean get() throws IOException { return asBoolean(invokeMethod(module, isActive), "Xaero minimap"); }
            public void set(boolean value) throws IOException { invokeMethod(module, setActive, value); }
        };
        List<ProviderFlag> flags = new ArrayList<>();
        flags.add(flag(MapModSelectionModel.XAERO_MINIMAP, gameRoot, file, "minimap", true,
                minimap, configRoot, document, true));
        flags.add(legacyXaeroSettingFlag(gameRoot, configRoot, file, document, settings,
                "showWaypoints", false));
        flags.add(legacyXaeroSettingFlag(gameRoot, configRoot, file, document, settings,
                "showIngameWaypoints", false));
        addOptionalLegacyXaeroFlag(flags, gameRoot, configRoot, file, document, settings,
                "displayTrackedPlayersInWorld");
        return flags;
    }

    private static ProviderFlag legacyXaeroSettingFlag(Path gameRoot, Path configRoot, Path file,
                                                        MapModSelectionFiles.Document document,
                                                        Object settings, String fieldName,
                                                        boolean primary) throws IOException {
        Field field = findField(settings.getClass(), fieldName);
        if (field == null) throw new IOException("Xaero legacy setting is unavailable: " + fieldName);
        field.setAccessible(true);
        BooleanAccess access = new BooleanAccess() {
            public Boolean get() throws IOException {
                try { return field.getBoolean(settings); }
                catch (ReflectiveOperationException e) { throw new IOException("Cannot read Xaero " + fieldName, e); }
            }
            public void set(boolean value) throws IOException {
                try { field.setBoolean(settings, value); }
                catch (ReflectiveOperationException e) { throw new IOException("Cannot set Xaero " + fieldName, e); }
            }
        };
        return flag(MapModSelectionModel.XAERO_MINIMAP, gameRoot, file, fieldName, primary,
                access, configRoot, document, true);
    }

    private static void addOptionalLegacyXaeroFlag(List<ProviderFlag> flags, Path gameRoot,
                                                    Path configRoot, Path file,
                                                    MapModSelectionFiles.Document document,
                                                    Object settings, String fieldName) throws IOException {
        if (findField(settings.getClass(), fieldName) != null) {
            flags.add(legacyXaeroSettingFlag(gameRoot, configRoot, file, document, settings, fieldName, false));
        }
    }

    private static ProviderFlag flag(String provider, Path gameRoot, Path file, String key,
                                     boolean primary, BooleanAccess access, Path allowedRoot,
                                     MapModSelectionFiles.Document document, boolean lineConfig) throws IOException {
        return providerFlag(provider, gameRoot, file, key, primary, access, null, allowedRoot, document);
    }

    private static ProviderFlag providerFlag(String provider, Path gameRoot, Path file, String key,
                                              boolean primary, BooleanAccess access, Boolean defaultValue,
                                              Path allowedRoot,
                                              MapModSelectionFiles.Document document) throws IOException {
        if (!document.path().equals(MapModSelectionFiles.validatePath(allowedRoot, file))) {
            throw new IOException("Provider config changed during preflight");
        }
        String relative = gameRoot.toAbsolutePath().normalize().relativize(file.toAbsolutePath().normalize())
                .toString().replace('\\', '/');
        String snapshotKey = provider + "|" + relative + "|" + key;
        return new ProviderFlag(provider, file, key, primary, false, true, access, defaultValue, document, snapshotKey);
    }

    private static BooleanAccess configValueAccess(Object configValue, String name) throws IOException {
        if (configValue == null) throw new IOException("OpenMapDelta " + name + " is unavailable");
        Method getter = method(configValue.getClass(), "get", 0);
        Method setter = method(configValue.getClass(), "set", 1);
        if (getter == null || setter == null) throw new IOException("OpenMapDelta " + name + " cannot be accessed");
        return new BooleanAccess() {
            public Boolean get() throws IOException { return asBoolean(invokeMethod(configValue, getter), "OpenMapDelta " + name); }
            public void set(boolean value) throws IOException { invokeMethod(configValue, setter, value); }
        };
    }

    private static ProviderFlag selectionOnlyFlag(String provider, Path gameRoot, Path file, String key,
                                                   BooleanAccess access, Path allowedRoot,
                                                   MapModSelectionFiles.Document document) throws IOException {
        ProviderFlag flag = providerFlag(provider, gameRoot, file, key, false, access, null, allowedRoot, document);
        return new ProviderFlag(flag.provider(), flag.path(), flag.key(), flag.primary(), true,
                flag.snapshotOnDisable(), flag.access(), flag.defaultValue(), flag.document(), flag.snapshotKey());
    }

    private static ProviderFlag noSnapshotFlag(String provider, Path gameRoot, Path file, String key,
                                                boolean primary, BooleanAccess access, Path allowedRoot,
                                                MapModSelectionFiles.Document document) throws IOException {
        ProviderFlag flag = flag(provider, gameRoot, file, key, primary, access, allowedRoot, document, false);
        return new ProviderFlag(flag.provider(), flag.path(), flag.key(), flag.primary(), flag.selectionOnly(),
                false, flag.access(), flag.defaultValue(), flag.document(), flag.snapshotKey());
    }

    private static Path validateJourneyMapConfigPath(Path journeyRoot, Path target) throws IOException {
        Path root = journeyRoot.toAbsolutePath().normalize();
        Path file = target.toAbsolutePath().normalize();
        if (!file.startsWith(root)) throw new IOException("JourneyMap config is outside its allowlisted root");
        Path relative = root.relativize(file);
        List<String> parts = new ArrayList<>();
        for (Path part : relative) parts.add(part.toString());
        boolean globalConfig = (parts.size() == 2 || parts.size() == 3)
                && parts.get(0).equals("config");
        boolean activeWorldConfig = parts.size() == 5 && parts.get(0).equals("data")
                && (parts.get(1).equals("sp") || parts.get(1).equals("mp"))
                && !parts.get(2).isBlank() && parts.get(3).equals("config");
        if ((!globalConfig && !activeWorldConfig)
                || !parts.get(parts.size() - 1).toLowerCase(Locale.ROOT).endsWith(".config")) {
            throw new IOException("JourneyMap returned a config path outside its native settings locations: " + file);
        }
        return MapModSelectionFiles.validatePath(root, file);
    }

    private interface BooleanAccess {
        Boolean get() throws IOException;
        void set(boolean value) throws IOException;
    }

    private record ProviderFlag(String provider, Path path, String key, boolean primary, boolean selectionOnly,
                                boolean snapshotOnDisable,
                                BooleanAccess access, Boolean defaultValue,
                                MapModSelectionFiles.Document document, String snapshotKey) {}

    private static final class Transaction {
        private final Map<Path, MapModSelectionFiles.Document> documents = new LinkedHashMap<>();
        private final List<CacheMutation> mutations = new ArrayList<>();

        void patch(ProviderFlag flag, boolean value) throws IOException {
            MapModSelectionFiles.Document current = documents.get(flag.path());
            if (current == null) {
                current = flag.document();
                documents.put(flag.path(), current);
            }
            current.writeBoolean(flag.key(), value);
        }

        void mutate(BooleanAccess access, boolean oldValue, boolean nextValue) {
            mutations.add(new CacheMutation(access, oldValue, nextValue));
        }

        void commit(Path configRoot, Path statePath, byte[] stateBytes) throws IOException {
            List<FileUpdate> updates = new ArrayList<>();
            for (MapModSelectionFiles.Document document : documents.values()) {
                updates.add(new FileUpdate(document.path(), document.originalBytes(), document.existed(), document.updatedBytes()));
            }
            Path safeStatePath = MapModSelectionFiles.validatePath(configRoot, statePath);
            boolean stateExisted = Files.exists(safeStatePath, LinkOption.NOFOLLOW_LINKS);
            byte[] originalState = stateExisted
                    ? MapModSelectionFiles.readBounded(safeStatePath, MapModSelectionState.MAX_STATE_BYTES)
                    : new byte[0];
            if (originalState.length > MapModSelectionState.MAX_STATE_BYTES) throw new IOException("Map selection state exceeds size limit");
            if (stateBytes.length > MapModSelectionState.MAX_STATE_BYTES) throw new IOException("Map selection state exceeds size limit");
            FileUpdate stateUpdate = new FileUpdate(safeStatePath, originalState, stateExisted, stateBytes);
            List<FileUpdate> written = new ArrayList<>();
            List<CacheMutation> attempted = new ArrayList<>();
            try {
                for (FileUpdate update : updates) {
                    if (!java.util.Arrays.equals(update.original(), update.updated())) {
                        ensureUnchanged(update);
                        writeAtomic(update.path(), update.updated());
                        written.add(update);
                    }
                }
                for (CacheMutation mutation : mutations) {
                    attempted.add(mutation);
                    mutation.apply();
                }
                if (!java.util.Arrays.equals(stateUpdate.original(), stateUpdate.updated())) {
                    ensureUnchanged(stateUpdate);
                    writeAtomic(stateUpdate.path(), stateUpdate.updated());
                    written.add(stateUpdate);
                }
            } catch (IOException | RuntimeException failure) {
                IOException rollbackFailure = rollback(written, attempted);
                if (rollbackFailure != null) failure.addSuppressed(rollbackFailure);
                if (failure instanceof IOException io) throw io;
                throw new IOException("Unable to apply map provider selection", failure);
            }
        }

        private static void ensureUnchanged(FileUpdate update) throws IOException {
            boolean exists = Files.exists(update.path(), LinkOption.NOFOLLOW_LINKS);
            if (exists != update.existed()) throw new IOException("Provider config changed during selection: " + update.path());
            if (exists && !java.util.Arrays.equals(update.original(),
                    MapModSelectionFiles.readBounded(update.path(), Math.max(update.original().length, MapModSelectionState.MAX_STATE_BYTES)))) {
                throw new IOException("Provider config changed during selection: " + update.path());
            }
        }

        private static IOException rollback(List<FileUpdate> written, List<CacheMutation> attempted) {
            IOException failure = null;
            for (int i = attempted.size() - 1; i >= 0; i--) {
                try { attempted.get(i).rollback(); }
                catch (IOException e) { failure = append(failure, e); }
            }
            for (int i = written.size() - 1; i >= 0; i--) {
                FileUpdate update = written.get(i);
                try {
                    if (update.existed()) writeAtomic(update.path(), update.original());
                    else Files.deleteIfExists(update.path());
                } catch (IOException e) { failure = append(failure, e); }
            }
            return failure;
        }

        private static IOException append(IOException previous, IOException next) {
            if (previous == null) return next;
            previous.addSuppressed(next);
            return previous;
        }

        private static void writeAtomic(Path path, byte[] bytes) throws IOException {
            Path parent = path.getParent();
            if (parent == null || !Files.isDirectory(parent)) throw new IOException("Config parent directory is missing: " + parent);
            Path temporary = Files.createTempFile(parent, path.getFileName().toString(), ".tmp");
            try {
                Files.write(temporary, bytes);
                try {
                    Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                } catch (AtomicMoveNotSupportedException e) {
                    throw new IOException("Atomic config replacement is not supported for " + path, e);
                }
            } finally {
                Files.deleteIfExists(temporary);
            }
        }
    }

    private record FileUpdate(Path path, byte[] original, boolean existed, byte[] updated) {}

    private record CacheMutation(BooleanAccess access, boolean oldValue, boolean newValue) {
        void apply() throws IOException { access.set(newValue); }
        void rollback() throws IOException { access.set(oldValue); }
    }

    private static int compareVersion(String version, int major, int minor, int patch) throws IOException {
        Matcher matcher = VERSION_PREFIX.matcher(version.trim());
        if (!matcher.find()) throw new IOException("Unrecognized Xaero Minimap version: " + version);
        try {
            int[] actual = {Integer.parseInt(matcher.group(1)), Integer.parseInt(matcher.group(2)), Integer.parseInt(matcher.group(3))};
            int[] expected = {major, minor, patch};
            for (int i = 0; i < actual.length; i++) if (actual[i] != expected[i]) return Integer.compare(actual[i], expected[i]);
            return 0;
        } catch (NumberFormatException e) {
            throw new IOException("Unrecognized Xaero Minimap version: " + version, e);
        }
    }

    private static Object staticField(String className, String name) throws IOException {
        return staticField(loadClass(className), name);
    }

    private static Object staticField(Class<?> type, String name) throws IOException {
        Field field = findField(type, name);
        if (field == null) throw new IOException("No such field " + type.getName() + "." + name);
        try {
            field.setAccessible(true);
            return field.get(null);
        } catch (ReflectiveOperationException | RuntimeException e) {
            throw new IOException("Cannot read " + type.getName() + "." + name, e);
        }
    }

    private static Object readField(Object target, String name, boolean optional) throws IOException {
        if (target == null) throw new IOException("Provider settings object is unavailable for " + name);
        Field field = findField(target.getClass(), name);
        if (field == null) {
            if (optional) return null;
            throw new IOException("No such field " + target.getClass().getName() + "." + name);
        }
        try {
            field.setAccessible(true);
            return field.get(target);
        } catch (ReflectiveOperationException | RuntimeException e) {
            throw new IOException("Cannot read setting " + name, e);
        }
    }

    private static Field findField(Class<?> type, String name) {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            try { return current.getDeclaredField(name); }
            catch (NoSuchFieldException ignored) {}
        }
        return null;
    }

    private static Class<?> loadClass(String name) throws IOException {
        try {
            ClassLoader loader = Thread.currentThread().getContextClassLoader();
            return Class.forName(name, true, loader == null ? MapModSelectionEditor.class.getClassLoader() : loader);
        } catch (ClassNotFoundException | LinkageError e) {
            throw new IOException("Required provider runtime class is unavailable: " + name, e);
        }
    }

    private static Object invokeStatic(String className, String methodName, Object... arguments) throws IOException {
        Class<?> type = loadClass(className);
        Method method = findCompatibleMethod(type, methodName, arguments);
        if (method == null) throw new IOException("Provider method is unavailable: " + className + "." + methodName);
        return invokeMethod(null, method, arguments);
    }

    private static Object invoke(Object target, String methodName, Object... arguments) throws IOException {
        if (target == null) throw new IOException("Provider runtime handle is unavailable for " + methodName);
        Method method = findCompatibleMethod(target.getClass(), methodName, arguments);
        if (method == null) throw new IOException("Provider method is unavailable: " + target.getClass().getName() + "." + methodName);
        return invokeMethod(target, method, arguments);
    }

    private static Method method(Class<?> type, String name, int parameterCount) {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            for (Method method : current.getDeclaredMethods()) {
                if (method.getName().equals(name) && method.getParameterCount() == parameterCount) {
                    method.setAccessible(true);
                    return method;
                }
            }
        }
        return null;
    }

    private static Method findCompatibleMethod(Class<?> type, String name, Object... arguments) {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            for (Method method : current.getDeclaredMethods()) {
                if (!method.getName().equals(name) || method.getParameterCount() != arguments.length) continue;
                Class<?>[] parameters = method.getParameterTypes();
                boolean matches = true;
                for (int i = 0; i < parameters.length; i++) {
                    if (arguments[i] == null) continue;
                    Class<?> parameter = box(parameters[i]);
                    if (!parameter.isAssignableFrom(arguments[i].getClass())) { matches = false; break; }
                }
                if (matches) {
                    method.setAccessible(true);
                    return method;
                }
            }
        }
        return null;
    }

    private static Class<?> box(Class<?> type) {
        if (!type.isPrimitive()) return type;
        if (type == boolean.class) return Boolean.class;
        if (type == int.class) return Integer.class;
        if (type == long.class) return Long.class;
        if (type == double.class) return Double.class;
        if (type == float.class) return Float.class;
        if (type == short.class) return Short.class;
        if (type == byte.class) return Byte.class;
        if (type == char.class) return Character.class;
        return type;
    }

    private static Object invokeMethod(Object target, Method method, Object... arguments) throws IOException {
        try {
            method.setAccessible(true);
            return method.invoke(target, arguments);
        } catch (IllegalAccessException | InvocationTargetException | RuntimeException e) {
            Throwable cause = e instanceof InvocationTargetException invocation && invocation.getCause() != null
                    ? invocation.getCause() : e;
            throw new IOException("Provider runtime call failed: " + method.getName(), cause);
        }
    }

    private static Boolean asBoolean(Object value, String name) throws IOException {
        if (value instanceof Boolean bool) return bool;
        throw new IOException(name + " did not expose a boolean value");
    }

    private static Path asPath(Object value) throws IOException {
        if (value instanceof Path path) return path;
        if (value instanceof java.io.File file) return file.toPath();
        throw new IOException("Map provider returned an invalid config path");
    }
}
