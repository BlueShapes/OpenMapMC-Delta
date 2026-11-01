// Copyright (c) 2026 AoSankaku. Licensed under Apache-2.0.
package net.aosankaku.openmapdelta.compat.players;

import com.google.gson.*;
import net.aosankaku.openmapdelta.network.PlayerPosition;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** BlueMap public live-player JSON. The endpoint, rather than each row, identifies the dimension. */
public final class BlueMapPlayers {
    public static final int MAX_BYTES = 800_000;
    private BlueMapPlayers() {}

    public static List<PlayerPosition> decode(byte[] data, String dimension) {
        if (data == null || data.length > MAX_BYTES) throw new IllegalArgumentException("BlueMap response too large");
        try {
            JsonObject root = JsonParser.parseString(new String(data, StandardCharsets.UTF_8)).getAsJsonObject();
            JsonArray array = root.getAsJsonArray("players");
            if (array == null || array.size() > 4096) throw new IllegalArgumentException("Invalid BlueMap players");
            var result = new ArrayList<PlayerPosition>();
            var ids = new HashSet<UUID>();
            for (JsonElement entry : array) {
                JsonObject object = entry.getAsJsonObject();
                JsonElement foreign = object.get("foreign");
                if (foreign == null || !foreign.isJsonPrimitive() || !foreign.getAsJsonPrimitive().isBoolean())
                    throw new IllegalArgumentException("Missing BlueMap world membership");
                if (foreign.getAsBoolean()) continue;
                String rawId = string(object, "uuid");
                if (!rawId.matches("[0-9a-fA-F]{8}(-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}"))
                    throw new IllegalArgumentException("Invalid UUID");
                UUID id = UUID.fromString(rawId);
                if (!ids.add(id)) throw new IllegalArgumentException("Duplicate BlueMap player");
                JsonObject position = object.getAsJsonObject("position");
                result.add(new PlayerPosition(id, string(object, "name"), dimension,
                        coordinate(position, "x"), coordinate(position, "y"), coordinate(position, "z")));
            }
            return List.copyOf(result);
        } catch (RuntimeException invalid) {
            throw new IllegalArgumentException("Invalid BlueMap player response", invalid);
        }
    }

    private static String string(JsonObject object, String key) {
        JsonElement value = object.get(key);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString())
            throw new IllegalArgumentException("Invalid string");
        return value.getAsString();
    }

    private static int coordinate(JsonObject object, String key) {
        JsonElement value = object.get(key);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber())
            throw new IllegalArgumentException("Invalid coordinate");
        double coordinate = value.getAsDouble();
        if (!Double.isFinite(coordinate) || Math.abs(coordinate) >= 30_000_000)
            throw new IllegalArgumentException("Invalid coordinate");
        return (int) Math.floor(coordinate);
    }
}
