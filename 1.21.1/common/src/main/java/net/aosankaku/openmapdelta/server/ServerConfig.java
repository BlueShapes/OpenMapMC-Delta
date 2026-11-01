// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.server;

import net.aosankaku.openmapdelta.platform.DataPaths;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.aosankaku.openmapdelta.OpenMapDeltaCommon;
import net.aosankaku.openmapdelta.marker.WaypointFileStore;
import net.aosankaku.openmapdelta.web.WebMapUrls;
import java.net.URI;
import java.util.Optional;
import java.nio.file.Files;
import java.nio.file.Path;

/** Server-owned policy; never reads or overwrites the client's map settings. */
public final class ServerConfig {
    private static volatile boolean allowCaveMap = true;
    private static volatile URI sharedWebMapUrl;
    private static volatile java.util.Set<String> graveBlockIds = java.util.Set.of();
    private static volatile ServerConfigValues values = new ServerConfigValues(true, false, "", 4096);
    private static JsonObject document;
    private static long revision;

    private ServerConfig() {}

    public static boolean isCaveAllowed() { return allowCaveMap; }
    public static Optional<URI> webMapUrl() { return Optional.ofNullable(sharedWebMapUrl); }
    /** Additional grave blocks; datapacks can use either the legacy or current grave tag. */
    public static java.util.Set<String> graveBlockIds() { return graveBlockIds; }
    public static Path path() { return DataPaths.configDirectory().resolve("server.json"); }
    public static int maxSharedWaypoints() { return values.maxSharedWaypoints(); }
    public static synchronized boolean editable() { return document != null; }
    public static synchronized long revision() { return revision; }
    public static synchronized ServerConfigValues values() { return values; }

    /** Write first, then publish. Unknown keys and grave configuration are retained. */
    public static synchronized ServerConfigSnapshot.Result update(long expectedRevision, ServerConfigValues next) {
        if (document == null) return ServerConfigSnapshot.Result.STORAGE_ERROR;
        if (expectedRevision != revision) return ServerConfigSnapshot.Result.CONFLICT;
        if (next.equals(values)) return ServerConfigSnapshot.Result.SAVED;
        JsonObject candidate = document.deepCopy();
        candidate.addProperty("allowCaveMap", next.allowCaveMap());
        candidate.addProperty("webMapEnabled", next.webMapEnabled());
        candidate.addProperty("webMapUrl", next.webMapUrl());
        candidate.addProperty("maxSharedWaypoints", next.maxSharedWaypoints());
        try {
            WaypointFileStore.write(path(), writer -> new GsonBuilder().setPrettyPrinting().create().toJson(candidate, writer));
        } catch (Exception failure) {
            OpenMapDeltaCommon.LOGGER.error("Cannot save server policy {}", path(), failure);
            return ServerConfigSnapshot.Result.STORAGE_ERROR;
        }
        document = candidate;
        values = next;
        allowCaveMap = next.allowCaveMap();
        sharedWebMapUrl = WebMapUrls.shared(next.webMapEnabled(), next.webMapUrl()).orElse(null);
        revision++;
        return ServerConfigSnapshot.Result.SAVED;
    }

    public static synchronized void load() {
        DataPaths.ensureMigrated();
        Path path = path();
        allowCaveMap = true;
        sharedWebMapUrl = null;
        graveBlockIds = java.util.Set.of();
        document = null;
        values = new ServerConfigValues(true, false, "", 4096);
        revision++;
        try {
            if (!Files.exists(path)) {
                Files.createDirectories(path.getParent());
                JsonObject defaults = new JsonObject();
                defaults.addProperty("allowCaveMap", true);
                defaults.addProperty("webMapEnabled", false);
                defaults.addProperty("webMapUrl", "");
                defaults.addProperty("maxSharedWaypoints", 4096);
                defaults.add("graveBlockIds", new com.google.gson.JsonArray());
                WaypointFileStore.write(path, writer -> new GsonBuilder().setPrettyPrinting().create().toJson(defaults, writer));
                document = defaults;
                return;
            }
            try (var reader = Files.newBufferedReader(path)) {
                JsonObject config = JsonParser.parseReader(reader).getAsJsonObject();
                if (config.has("allowCaveMap")) {
                    var value = config.get("allowCaveMap");
                    if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean()) {
                        throw new IllegalArgumentException("allowCaveMap must be a boolean");
                    }
                    allowCaveMap = value.getAsBoolean();
                }
                loadWebMapNotice(config);
                int maximum = 4096;
                if (config.has("maxSharedWaypoints")) {
                    var limit = config.get("maxSharedWaypoints");
                    if (!limit.isJsonPrimitive() || !limit.getAsJsonPrimitive().isNumber()) {
                        throw new IllegalArgumentException("maxSharedWaypoints must be an integer");
                    }
                    maximum = limit.getAsBigDecimal().intValueExact();
                }
                // Disabled announcements may keep a valid URL for later re-enabling.
                String configuredUrl = "";
                if (config.has("webMapUrl") && config.get("webMapUrl").isJsonPrimitive()
                        && config.get("webMapUrl").getAsJsonPrimitive().isString()) {
                    configuredUrl = WebMapUrls.shared(true, config.get("webMapUrl").getAsString())
                            .map(URI::toString).orElse("");
                }
                values = new ServerConfigValues(allowCaveMap, sharedWebMapUrl != null, configuredUrl, maximum);
                if (config.has("graveBlockIds")) {
                    var ids = new java.util.HashSet<String>();
                    var values = config.getAsJsonArray("graveBlockIds");
                    if (values.size() > 256) throw new IllegalArgumentException("Too many grave block IDs");
                    for (var value : values) {
                        String id = value.getAsString();
                        if (id.length() > 192 || !id.matches("[a-z0-9_.-]+:[a-z0-9_./-]+"))
                            throw new IllegalArgumentException("Invalid grave block ID");
                        ids.add(id);
                    }
                    graveBlockIds = java.util.Set.copyOf(ids);
                }
                document = config;
            }
        } catch (Exception failure) {
            // A malformed server policy must not accidentally enable forbidden cave maps.
            allowCaveMap = false;
            sharedWebMapUrl = null;
            document = null;
            OpenMapDeltaCommon.LOGGER.error("Cannot read server policy {}; cave maps are disabled", path, failure);
        }
    }

    private static void loadWebMapNotice(JsonObject config) {
        try {
            var enabled = config.get("webMapEnabled");
            if (enabled == null) return;
            if (!enabled.isJsonPrimitive() || !enabled.getAsJsonPrimitive().isBoolean()) {
                throw new IllegalArgumentException("webMapEnabled must be a boolean");
            }
            if (!enabled.getAsBoolean()) return;
            var url = config.get("webMapUrl");
            if (url == null || !url.isJsonPrimitive() || !url.getAsJsonPrimitive().isString()) {
                throw new IllegalArgumentException("webMapUrl must be a string");
            }
            sharedWebMapUrl = WebMapUrls.shared(true, url.getAsString()).orElseThrow(
                    () -> new IllegalArgumentException("webMapUrl must be a public HTTP(S) URL"));
        } catch (RuntimeException invalid) {
            // Do not echo URLs or affect the independently configured cave-map policy.
            sharedWebMapUrl = null;
            OpenMapDeltaCommon.LOGGER.warn("Server web map URL announcement disabled: invalid webMapEnabled/webMapUrl");
        }
    }
}
