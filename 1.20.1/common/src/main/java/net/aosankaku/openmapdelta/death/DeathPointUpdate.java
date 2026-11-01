// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.death;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

/** Owner-only, bounded death recovery report. This protocol has no client-to-server messages. */
public record DeathPointUpdate(UUID deathId, String dimension, int x, int y, int z, long gameTime,
        boolean keepInventory, boolean hasDrops, boolean graveExpected, Outcome outcome,
        List<String> collectorNames, List<InventoryItem> inventory, int experienceLevel, Position position, ItemTimer itemTimer) {
    public static final int MAX_BYTES = 524_288;
    public static final int MAX_INVENTORY = 128;
    private static final Gson GSON = new Gson();
    public enum Outcome { ACTIVE, RECOVERED, COLLECTED_BY_OTHERS, EXPIRED }

    /** Remaining availability of tracked loot, rather than cosmetic grave/body lifetime. */
    public record ItemTimer(long remainingTicks, boolean ticking, boolean expired) {
        public ItemTimer {
            if (remainingTicks < -1 || remainingTicks > 2L * Integer.MAX_VALUE + 1
                    || ticking && remainingTicks < 0 || expired && (remainingTicks != 0 || ticking))
                throw new IllegalArgumentException("Invalid item timer");
        }
    }

    /** Optional recovery location. Original x/y/z remain the immutable death identity. */
    public record Position(double x, double y, double z, double velocityY) {
        public Position {
            if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z) || !Double.isFinite(velocityY)
                    || Math.abs(x) > 30_000_000 || Math.abs(z) > 30_000_000 || y < -2048 || y > 4096
                    || Math.abs(velocityY) > 64) throw new IllegalArgumentException("Invalid recovery position");
        }
    }

    public DeathPointUpdate(UUID deathId, String dimension, int x, int y, int z, long gameTime,
            boolean keepInventory, boolean hasDrops, boolean graveExpected, Outcome outcome,
            List<String> collectorNames, List<InventoryItem> inventory, int experienceLevel) {
        this(deathId, dimension, x, y, z, gameTime, keepInventory, hasDrops, graveExpected, outcome,
                collectorNames, inventory, experienceLevel, null, null);
    }

    public DeathPointUpdate(UUID deathId, String dimension, int x, int y, int z, long gameTime,
            boolean keepInventory, boolean hasDrops, boolean graveExpected, Outcome outcome,
            List<String> collectorNames, List<InventoryItem> inventory, int experienceLevel, Position position) {
        this(deathId, dimension, x, y, z, gameTime, keepInventory, hasDrops, graveExpected, outcome,
                collectorNames, inventory, experienceLevel, position, null);
    }

    public DeathPointUpdate withPosition(Position position) {
        return new DeathPointUpdate(deathId, dimension, x, y, z, gameTime, keepInventory, hasDrops, graveExpected,
                outcome, collectorNames, inventory, experienceLevel, position, itemTimer);
    }

    public DeathPointUpdate withItemTimer(ItemTimer timer) {
        return new DeathPointUpdate(deathId, dimension, x, y, z, gameTime, keepInventory, hasDrops, graveExpected,
                outcome, collectorNames, inventory, experienceLevel, position, timer);
    }

    public int recoveryX() { return position == null ? x : (int) Math.floor(position.x()); }
    public int recoveryY() { return position == null ? y : (int) Math.floor(position.y()); }
    public int recoveryZ() { return position == null ? z : (int) Math.floor(position.z()); }

    public record InventoryItem(String id, int count, boolean valuable, List<String> tags) {
        public InventoryItem {
            validateId(id);
            if (count < 1 || count > 1_000_000 || tags == null || tags.size() > 16)
                throw new IllegalArgumentException("Invalid death inventory item");
            tags.forEach(DeathPointUpdate::validateId);
            tags = List.copyOf(tags);
        }
    }

    public DeathPointUpdate {
        if (deathId == null || deathId.equals(new UUID(0, 0)) || outcome == null)
            throw new IllegalArgumentException("Invalid death report identity");
        validateId(dimension);
        if (Math.abs((long) x) > 30_000_000 || Math.abs((long) z) > 30_000_000
                || y < -2048 || y > 4096 || gameTime < 0 || experienceLevel < 0 || experienceLevel > 1_000_000)
            throw new IllegalArgumentException("Invalid death report coordinates or time");
        if (collectorNames == null || collectorNames.size() > 64 || inventory == null || inventory.size() > MAX_INVENTORY)
            throw new IllegalArgumentException("Death report exceeds collection limits");
        for (String name : collectorNames) {
            if (name == null || name.isBlank() || name.length() > 64 || name.chars().anyMatch(Character::isISOControl))
                throw new IllegalArgumentException("Invalid collector name");
        }
        collectorNames = List.copyOf(collectorNames);
        inventory = List.copyOf(inventory);
    }

    private static void validateId(String id) {
        if (id == null || id.length() > 192 || !id.matches("[a-z0-9_.-]+:[a-z0-9_./-]+"))
            throw new IllegalArgumentException("Invalid registry identifier");
    }

    public byte[] encode() {
        JsonObject root = new JsonObject();
        root.addProperty("version", 1);
        root.add("update", GSON.toJsonTree(this));
        byte[] bytes = GSON.toJson(root).getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_BYTES) throw new IllegalArgumentException("Death report too large");
        return bytes;
    }

    public static DeathPointUpdate decode(byte[] bytes) {
        if (bytes == null || bytes.length == 0 || bytes.length > MAX_BYTES)
            throw new IllegalArgumentException("Invalid death report length");
        try {
            String json = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            if (root.get("version").getAsBigDecimal().intValueExact() != 1 || !root.get("update").isJsonObject())
                throw new IllegalArgumentException("Unsupported death report");
            validateJson(root.getAsJsonObject("update"));
            JsonObject value = root.getAsJsonObject("update");
            var collectors = new java.util.ArrayList<String>();
            for (var name : value.getAsJsonArray("collectorNames")) collectors.add(name.getAsString());
            var inventory = new java.util.ArrayList<InventoryItem>();
            for (var entry : value.getAsJsonArray("inventory")) {
                var item = entry.getAsJsonObject();
                var tags = new java.util.ArrayList<String>();
                for (var tag : item.getAsJsonArray("tags")) tags.add(tag.getAsString());
                inventory.add(new InventoryItem(item.get("id").getAsString(), item.get("count").getAsInt(),
                        item.get("valuable").getAsBoolean(), tags));
            }
            Position position = null;
            if (value.has("position") && !value.get("position").isJsonNull()) {
                var point = value.getAsJsonObject("position");
                for (String key : List.of("x", "y", "z", "velocityY")) requirePrimitive(point, key, "number");
                position = new Position(point.get("x").getAsDouble(), point.get("y").getAsDouble(),
                        point.get("z").getAsDouble(), point.get("velocityY").getAsDouble());
            }
            ItemTimer itemTimer = null;
            if (value.has("itemTimer") && !value.get("itemTimer").isJsonNull()) {
                var timer = value.getAsJsonObject("itemTimer");
                requirePrimitive(timer, "remainingTicks", "number");
                requirePrimitive(timer, "ticking", "boolean");
                requirePrimitive(timer, "expired", "boolean");
                itemTimer = new ItemTimer(timer.get("remainingTicks").getAsBigDecimal().longValueExact(),
                        timer.get("ticking").getAsBoolean(), timer.get("expired").getAsBoolean());
            }
            return new DeathPointUpdate(UUID.fromString(value.get("deathId").getAsString()), value.get("dimension").getAsString(),
                    value.get("x").getAsInt(), value.get("y").getAsInt(), value.get("z").getAsInt(), value.get("gameTime").getAsLong(),
                    value.get("keepInventory").getAsBoolean(), value.get("hasDrops").getAsBoolean(), value.get("graveExpected").getAsBoolean(),
                    Outcome.valueOf(value.get("outcome").getAsString()), collectors, inventory, value.get("experienceLevel").getAsInt(), position, itemTimer);
        } catch (Exception malformed) {
            throw new IllegalArgumentException("Malformed death report", malformed);
        }
    }

    private static void validateJson(JsonObject value) {
        for (String key : List.of("deathId", "dimension", "outcome")) requirePrimitive(value, key, "string");
        for (String key : List.of("x", "y", "z", "experienceLevel")) {
            requirePrimitive(value, key, "number"); value.get(key).getAsBigDecimal().intValueExact();
        }
        requirePrimitive(value, "gameTime", "number"); value.get("gameTime").getAsBigDecimal().longValueExact();
        for (String key : List.of("keepInventory", "hasDrops", "graveExpected")) requirePrimitive(value, key, "boolean");
        if (!value.has("collectorNames") || !value.get("collectorNames").isJsonArray()
                || value.getAsJsonArray("collectorNames").size() > 64 || !value.has("inventory")
                || !value.get("inventory").isJsonArray() || value.getAsJsonArray("inventory").size() > MAX_INVENTORY)
            throw new IllegalArgumentException("Invalid report collections");
        for (var name : value.getAsJsonArray("collectorNames"))
            if (!name.isJsonPrimitive() || !name.getAsJsonPrimitive().isString()) throw new IllegalArgumentException("Invalid collector name");
        for (var entry : value.getAsJsonArray("inventory")) {
            JsonObject item = entry.getAsJsonObject();
            requirePrimitive(item, "id", "string"); requirePrimitive(item, "count", "number");
            item.get("count").getAsBigDecimal().intValueExact(); requirePrimitive(item, "valuable", "boolean");
            if (!item.has("tags") || !item.get("tags").isJsonArray() || item.getAsJsonArray("tags").size() > 16)
                throw new IllegalArgumentException("Invalid item tags");
            for (var tag : item.getAsJsonArray("tags"))
                if (!tag.isJsonPrimitive() || !tag.getAsJsonPrimitive().isString()) throw new IllegalArgumentException("Invalid tag");
        }
    }

    private static void requirePrimitive(JsonObject value, String key, String type) {
        if (!value.has(key) || !value.get(key).isJsonPrimitive()) throw new IllegalArgumentException("Missing report field");
        var primitive = value.getAsJsonPrimitive(key);
        if (type.equals("string") && !primitive.isString() || type.equals("number") && !primitive.isNumber()
                || type.equals("boolean") && !primitive.isBoolean()) throw new IllegalArgumentException("Invalid report field type");
    }
}
