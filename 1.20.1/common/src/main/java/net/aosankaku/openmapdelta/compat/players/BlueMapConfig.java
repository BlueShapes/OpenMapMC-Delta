// Copyright (c) 2026 AoSankaku. Licensed under Apache-2.0.
package net.aosankaku.openmapdelta.compat.players;

import com.google.gson.*;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/** Explicit per-server, per-dimension settings; malformed files are never silently replaced. */
public final class BlueMapConfig {
    private record Binding(String server, String dimension, BlueMapEndpoint endpoint) {}
    private final Path file;
    private List<Binding> bindings;
    private BlueMapConfig(Path file, List<Binding> bindings) { this.file = file; this.bindings = bindings; }

    public static BlueMapConfig load(Path file) throws IOException {
        if (!Files.exists(file)) return new BlueMapConfig(file, List.of());
        if (Files.size(file) > 262_144) throw new IOException("Remote player configuration too large");
        try {
            JsonObject root = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
            if (root.get("version").getAsInt() != 1) throw new IllegalArgumentException("Unsupported version");
            var result = new ArrayList<Binding>();
            var keys = new HashSet<String>();
            JsonArray entries = root.getAsJsonArray("bindings");
            if (entries == null || entries.size() > 256) throw new IllegalArgumentException("Invalid bindings");
            for (JsonElement entry : entries) {
                JsonObject row = entry.getAsJsonObject();
                String server = row.get("server").getAsString(), dimension = row.get("dimension").getAsString();
                validate(server, dimension);
                if (!keys.add(server + "\n" + dimension)) throw new IllegalArgumentException("Duplicate binding");
                result.add(new Binding(server, dimension, new BlueMapEndpoint(row.get("url").getAsString(), row.get("map").getAsString(),
                        row.has("liveDataRoot") ? row.get("liveDataRoot").getAsString() : "maps")));
            }
            return new BlueMapConfig(file, List.copyOf(result));
        } catch (RuntimeException invalid) { throw new IOException("Invalid remote player configuration", invalid); }
    }

    public Optional<BlueMapEndpoint> get(String server, String dimension) {
        return bindings.stream().filter(b -> b.server.equals(server) && b.dimension.equals(dimension)).map(Binding::endpoint).findFirst();
    }

    public void save(String server, String dimension, BlueMapEndpoint endpoint) throws IOException {
        validate(server, dimension);
        var next = new ArrayList<>(bindings.stream().filter(b -> !(b.server.equals(server) && b.dimension.equals(dimension))).toList());
        if (endpoint != null) next.add(new Binding(server, dimension, endpoint));
        if (next.size() > 256) throw new IOException("Too many BlueMap bindings");
        JsonObject root = new JsonObject(); root.addProperty("version", 1);
        JsonArray entries = new JsonArray(); root.add("bindings", entries);
        for (Binding binding : next) {
            JsonObject row = new JsonObject();
            row.addProperty("server", binding.server); row.addProperty("dimension", binding.dimension);
            row.addProperty("url", binding.endpoint.baseUrl()); row.addProperty("map", binding.endpoint.mapId());
            row.addProperty("liveDataRoot", binding.endpoint.liveDataRoot());
            entries.add(row);
        }
        String json = new GsonBuilder().setPrettyPrinting().create().toJson(root);
        if (json.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 262_144) throw new IOException("Remote player configuration too large");
        Files.createDirectories(file.toAbsolutePath().getParent());
        Path temporary = Files.createTempFile(file.toAbsolutePath().getParent(), "remote-players-", ".tmp");
        try {
            Files.writeString(temporary, json);
            try { Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
            catch (AtomicMoveNotSupportedException unavailable) { Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING); }
            bindings = List.copyOf(next);
        } finally { Files.deleteIfExists(temporary); }
    }

    private static void validate(String server, String dimension) {
        if (server == null || server.isBlank() || server.length() > 512 || server.chars().anyMatch(c -> c < 32)
                || dimension == null || dimension.length() > 128 || !dimension.matches("[a-z0-9_.-]+:[a-z0-9_./-]+"))
            throw new IllegalArgumentException("Invalid server or dimension binding");
    }
}
