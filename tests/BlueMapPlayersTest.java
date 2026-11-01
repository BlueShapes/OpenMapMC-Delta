// Copyright (c) 2026 AoSankaku. Licensed under Apache-2.0.
package net.aosankaku.openmapdelta.compat.players;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.*;

public final class BlueMapPlayersTest {
    private static int checks;
    private static final String DIM = "minecraft:overworld";
    private static final UUID ID = UUID.fromString("12345678-1234-1234-1234-123456789abc");
    private static final String PLAYER = "{\"uuid\":\"" + ID + "\",\"name\":\"Remote\",\"foreign\":false,\"position\":{\"x\":-1.2,\"y\":64.9,\"z\":12000}}";
    private static byte[] bytes(String s) { return s.getBytes(StandardCharsets.UTF_8); }
    private static void check(boolean value, String message) { checks++; if (!value) throw new AssertionError(message); }
    private static void rejects(Runnable action) { checks++; try { action.run(); } catch (IllegalArgumentException expected) { return; } throw new AssertionError("Expected rejection"); }
    public static void main(String[] args) throws Exception {
        var endpoint = new BlueMapEndpoint("https://example.org/map/", "world");
        check(endpoint.playersUri().toString().equals("https://example.org/map/maps/world/live/players.json"), "Subpath preserved");
        check(new BlueMapEndpoint("https://example.org/map", "world").equals(endpoint), "Trailing slash normalized");
        check(new BlueMapEndpoint("https://example.org/map", "world", "data/live").playersUri().toString().equals("https://example.org/map/data/live/world/live/players.json"), "Custom same-site liveDataRoot");
        rejects(() -> new BlueMapEndpoint("https://example.org/map", "world", "../other"));
        for (String url : new String[]{"file:///test", "https://name:password@example.org/", "https://example.org/?token=secret", "https://example.org/#world", "https:///missing", "ftp://example.org/"}) {
            rejects(() -> new BlueMapEndpoint(url, "world"));
        }
        for (String map : new String[]{"..", ".", "../other", "world/live", "", "a?x", "a%2Fb"}) rejects(() -> new BlueMapEndpoint("https://example.org", map));
        var players = BlueMapPlayers.decode(bytes("{\"players\":[" + PLAYER + "]}"), DIM);
        check(players.size() == 1 && players.get(0).id().equals(ID), "Public remote player decoded");
        check(players.get(0).x() == -2 && players.get(0).y() == 64 && players.get(0).z() == 12000, "Block coordinates floor negative values");
        check(players.get(0).dimension().equals(DIM), "Dimension is explicitly bound to map");
        check(BlueMapPlayers.decode(bytes("{\"players\":[" + PLAYER.replace("false", "true") + "]}"), DIM).isEmpty(), "Foreign player never assigned wrong dimension");
        check(BlueMapPlayers.decode(bytes("{\"players\":[]}"), DIM).isEmpty(), "Empty removes prior snapshot");
        rejects(() -> BlueMapPlayers.decode(bytes("{}"), DIM));
        rejects(() -> BlueMapPlayers.decode(bytes("[]"), DIM));
        rejects(() -> BlueMapPlayers.decode(bytes("{\"players\":null}"), DIM));
        rejects(() -> BlueMapPlayers.decode(bytes("{\"players\":[]} trailing"), DIM));
        rejects(() -> BlueMapPlayers.decode(new byte[BlueMapPlayers.MAX_BYTES + 1], DIM));
        rejects(() -> BlueMapPlayers.decode(bytes("{\"players\":[" + PLAYER + "," + PLAYER + "]}"), DIM));
        for (String bad : new String[]{PLAYER.replace("-1.2", "30000000"), PLAYER.replace("-1.2", "\"NaN\""), PLAYER.replace("Remote", "bad name"), PLAYER.replace(ID.toString(), "garbage"), PLAYER.replace("false", "\"false\""), PLAYER.replace("\"foreign\":false,", "")}) {
            rejects(() -> BlueMapPlayers.decode(bytes("{\"players\":[" + bad + "]}"), DIM));
        }
        Path directory = Files.createTempDirectory("openmap-bluemap-test-");
        Path configFile = directory.resolve("remote-players.json");
        BlueMapConfig config = BlueMapConfig.load(configFile);
        check(config.get("one:25565", DIM).isEmpty() && !Files.exists(configFile), "No automatic configuration or requests");
        config.save("one:25565", DIM, endpoint);
        check(BlueMapConfig.load(configFile).get("one:25565", DIM).orElseThrow().equals(endpoint), "Persist explicit server/map binding");
        check(config.get("two:25565", DIM).isEmpty() && config.get("one:25565", "minecraft:the_nether").isEmpty(), "Separate servers and dimensions");
        config.save("one:25565", DIM, null);
        check(BlueMapConfig.load(configFile).get("one:25565", DIM).isEmpty(), "Remove binding");
        Files.writeString(configFile, "{broken");
        boolean malformed = false;
        try { BlueMapConfig.load(configFile); } catch (java.io.IOException expected) { malformed = true; }
        check(malformed && Files.readString(configFile).equals("{broken"), "Corrupt configuration preserved");

        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        var serverWorkers = Executors.newCachedThreadPool(task -> { var t = new Thread(task); t.setDaemon(true); return t; });
        server.setExecutor(serverWorkers);
        CountDownLatch cancelStarted = new CountDownLatch(1), releaseCancel = new CountDownLatch(1);
        server.createContext("/maps/world/live/players.json", exchange -> {
            byte[] response = bytes("{\"players\":[" + PLAYER + "]}");
            exchange.sendResponseHeaders(200, response.length);
            try (var out = exchange.getResponseBody()) { out.write(response); }
        });
        server.createContext("/maps/redirect/live/players.json", exchange -> { exchange.getResponseHeaders().set("Location", "/maps/world/live/players.json"); exchange.sendResponseHeaders(302, -1); exchange.close(); });
        server.createContext("/maps/large/live/players.json", exchange -> { exchange.sendResponseHeaders(200, BlueMapPlayers.MAX_BYTES + 1); exchange.close(); });
        server.createContext("/maps/drip/live/players.json", exchange -> {
            exchange.sendResponseHeaders(200, 0);
            try (var out = exchange.getResponseBody()) {
                for (int i = 0; i < 100; i++) { out.write(' '); out.flush(); try { Thread.sleep(40); } catch (InterruptedException stop) { break; } }
            } catch (java.io.IOException cancelled) { /* Client deadline closed the socket. */ }
        });
        server.createContext("/maps/cancel/live/players.json", exchange -> {
            exchange.sendResponseHeaders(200, 0);
            try (var out = exchange.getResponseBody()) {
                out.write('{'); out.flush(); cancelStarted.countDown();
                try { releaseCancel.await(3, TimeUnit.SECONDS); } catch (InterruptedException stop) { Thread.currentThread().interrupt(); }
            } catch (java.io.IOException cancelled) { /* Expected client cancellation. */ }
        });
        server.start();
        try {
            String url = "http://127.0.0.1:" + server.getAddress().getPort();
            check(BlueMapHttp.fetch(new BlueMapEndpoint(url, "world"), DIM).size() == 1, "Fetch real endpoint without credentials");
            for (String map : new String[]{"redirect", "large", "missing"}) {
                boolean failed = false;
                try { BlueMapHttp.fetch(new BlueMapEndpoint(url, map), DIM); } catch (java.io.IOException expected) { failed = true; }
                check(failed, "Reject redirect/oversized/non-success HTTP response");
            }
            var deadline = new BlueMapHttp.Request(new BlueMapEndpoint(url, "drip"), DIM, 200);
            long start = System.nanoTime();
            boolean timedOut = false;
            try { deadline.fetch(); } catch (java.io.IOException expected) { timedOut = true; }
            check(timedOut && System.nanoTime() - start < 2_000_000_000L, "Total deadline aborts a response that keeps dripping below the read timeout");
            var cancel = new BlueMapHttp.Request(new BlueMapEndpoint(url, "cancel"), DIM);
            var requestWorker = Executors.newSingleThreadExecutor();
            try {
                Future<?> result = requestWorker.submit(() -> { try { cancel.fetch(); throw new AssertionError("Cancelled request accepted"); } catch (java.io.IOException expected) { } });
                check(cancelStarted.await(2, TimeUnit.SECONDS), "Cancellation test entered body read");
                long cancelStart = System.nanoTime();
                cancel.cancel(); result.get(2, TimeUnit.SECONDS);
                check(System.nanoTime() - cancelStart < 1_000_000_000L, "Cancellation returns promptly and closes the active response without blocking the client thread");
            } finally { releaseCancel.countDown(); requestWorker.shutdownNow(); }
            var cancelledBeforeStart = new BlueMapHttp.Request(new BlueMapEndpoint(url, "world"), DIM);
            cancelledBeforeStart.cancel();
            boolean cancelled = false;
            try { cancelledBeforeStart.fetch(); } catch (java.io.IOException expected) { cancelled = true; }
            check(cancelled, "Cancellation before worker start prevents a request");
        } finally { releaseCancel.countDown(); server.stop(0); serverWorkers.shutdownNow(); }
        System.out.println("BlueMap players: " + checks + " checks passed");
    }
}
