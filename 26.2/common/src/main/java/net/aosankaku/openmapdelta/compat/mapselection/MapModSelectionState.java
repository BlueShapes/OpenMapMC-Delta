// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.compat.mapselection;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Persisted acknowledgement and the provider flags temporarily disabled by it. */
public record MapModSelectionState(String selectedId, String fingerprint,
                                   Map<String, Boolean> restoreFlags) {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().serializeNulls().create();
    private static final int FORMAT_VERSION = 1;
    static final int MAX_STATE_BYTES = 131_072;

    public MapModSelectionState {
        if (fingerprint == null || !fingerprint.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("Invalid provider fingerprint");
        }
        if (selectedId != null && !MapModSelectionModel.PROVIDER_IDS.contains(selectedId)) {
            throw new IllegalArgumentException("Unknown selected map provider: " + selectedId);
        }
        restoreFlags = Collections.unmodifiableMap(new LinkedHashMap<>(restoreFlags == null ? Map.of() : restoreFlags));
    }

    public static Path path(Path configDir) {
        return configDir.resolve("openmapdelta").resolve("map-mod-selection.json");
    }

    /** Returns null when no choice has yet been acknowledged. */
    public static MapModSelectionState read(Path configDir) throws IOException {
        Path file = path(configDir);
        if (!Files.exists(file, LinkOption.NOFOLLOW_LINKS)) return null;
        MapModSelectionFiles.validatePath(configDir, file);
        byte[] bytes = MapModSelectionFiles.readBounded(file, MAX_STATE_BYTES);
        try {
            String json;
            try {
                json = MapModSelectionFiles.decodeUtf8(bytes, file);
            } catch (IOException e) {
                throw new InvalidStateException("Map selection state is not valid UTF-8", e);
            }
            JsonElement parsed = GSON.fromJson(json, JsonElement.class);
            if (parsed == null || !parsed.isJsonObject()) throw invalid("Map selection state must be a JSON object");
            JsonObject object = parsed.getAsJsonObject();
            if (!object.has("version") || !object.get("version").isJsonPrimitive()
                    || !object.getAsJsonPrimitive("version").isNumber()
                    || !Integer.toString(FORMAT_VERSION).equals(object.get("version").getAsString())) {
                throw invalid("Unsupported map selection state version");
            }
            String selected = null;
            if (!object.has("selectedId")) throw invalid("Missing selectedId in map selection state");
            if (!object.get("selectedId").isJsonNull()) {
                if (!object.get("selectedId").isJsonPrimitive() || !object.getAsJsonPrimitive("selectedId").isString())
                    throw invalid("Invalid selectedId in map selection state");
                selected = object.get("selectedId").getAsString();
            }
            if (!object.has("fingerprint") || !object.get("fingerprint").isJsonPrimitive()
                    || !object.getAsJsonPrimitive("fingerprint").isString()) {
                throw invalid("Missing provider fingerprint in map selection state");
            }
            Map<String, Boolean> restore = new LinkedHashMap<>();
            JsonElement restoreElement = object.get("restoreFlags");
            if (restoreElement != null) {
                if (!restoreElement.isJsonObject()) throw invalid("Invalid restoreFlags in map selection state");
                for (Map.Entry<String, JsonElement> entry : restoreElement.getAsJsonObject().entrySet()) {
                    JsonElement value = entry.getValue();
                    if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean()) {
                        throw invalid("Invalid restore flag in map selection state");
                    }
                    if (entry.getKey().length() > 4096) throw invalid("Restore flag path exceeds size limit");
                    restore.put(entry.getKey(), value.getAsBoolean());
                }
            }
            return new MapModSelectionState(selected, object.get("fingerprint").getAsString(), restore);
        } catch (InvalidStateException e) {
            throw e;
        } catch (JsonParseException | IllegalArgumentException e) {
            throw new InvalidStateException("Invalid map selection state", e);
        }
    }

    private static InvalidStateException invalid(String message) {
        return new InvalidStateException(message);
    }

    static final class InvalidStateException extends IOException {
        InvalidStateException(String message) { super(message); }
        InvalidStateException(String message, Throwable cause) { super(message, cause); }
    }

    byte[] toBytes() {
        JsonObject object = new JsonObject();
        object.addProperty("version", FORMAT_VERSION);
        if (selectedId == null) object.add("selectedId", com.google.gson.JsonNull.INSTANCE);
        else object.addProperty("selectedId", selectedId);
        object.addProperty("fingerprint", fingerprint);
        JsonObject restore = new JsonObject();
        for (Map.Entry<String, Boolean> entry : restoreFlags.entrySet()) {
            restore.addProperty(entry.getKey(), entry.getValue());
        }
        object.add("restoreFlags", restore);
        return GSON.toJson(object).getBytes(StandardCharsets.UTF_8);
    }
}
