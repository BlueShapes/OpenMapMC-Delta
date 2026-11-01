// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.server;

import net.aosankaku.openmapdelta.platform.DataPaths;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.aosankaku.openmapdelta.OpenMapDeltaCommon;
import net.aosankaku.openmapdelta.platform.Services;
import net.aosankaku.openmapdelta.web.WebMapUrls;
import java.net.URI;
import java.util.Optional;
import java.nio.file.Files;
import java.nio.file.Path;

/** Server-owned policy; never reads or overwrites the client's map settings. */
public final class ServerConfig {
    private static volatile boolean allowCaveMap = true;
    private static volatile URI sharedWebMapUrl;

    private ServerConfig() {}

    public static boolean isCaveAllowed() { return allowCaveMap; }
    public static Optional<URI> webMapUrl() { return Optional.ofNullable(sharedWebMapUrl); }
    public static Path path() { return DataPaths.configDirectory().resolve("server.json"); }

    public static void load() {
        DataPaths.ensureMigrated();
        Path path = path();
        allowCaveMap = true;
        sharedWebMapUrl = null;
        try {
            if (!Files.exists(path)) {
                Files.createDirectories(path.getParent());
                JsonObject defaults = new JsonObject();
                defaults.addProperty("allowCaveMap", true);
                defaults.addProperty("webMapEnabled", false);
                defaults.addProperty("webMapUrl", "");
                Files.writeString(path, new GsonBuilder().setPrettyPrinting().create().toJson(defaults) + "\n");
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
            }
        } catch (Exception failure) {
            // A malformed server policy must not accidentally enable forbidden cave maps.
            allowCaveMap = false;
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
