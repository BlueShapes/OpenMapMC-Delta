// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.web;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** Small read-only server; it never reads live Minecraft objects or arbitrary files. */
public final class WebMapServer implements AutoCloseable {
    private static final String COOKIE = "odyssey_session";
    private static final Map<String, String> ASSETS = Map.of(
            "/", "index.html", "/map.js", "map.js", "/map.css", "map.css");
    private final HttpServer server;
    private final ThreadPoolExecutor executor;
    private final WebMapAuth auth;
    private final boolean passwordRequired;
    private final boolean loopback;
    private final Map<String, byte[]> assets = new LinkedHashMap<>();
    private final Map<String, Window> limits = new LinkedHashMap<>();
    private volatile WebMapSnapshot snapshot = WebMapSnapshot.empty();
    public record ViewCenter(int x, int z) {}
    private record ViewDemand(ViewCenter center, long updated) {}
    private final Map<String, ViewDemand> viewDemands = new LinkedHashMap<>();
    private record Window(long start, int count) {}

    public WebMapServer(InetSocketAddress address, boolean passwordRequired,
                        String password, byte[] secret) throws IOException {
        if (passwordRequired && (password == null || password.isEmpty())) {
            throw new IllegalArgumentException("A web map password is required");
        }
        this.passwordRequired = passwordRequired;
        this.auth = new WebMapAuth(password == null ? "" : password, secret);
        this.loopback = address.getAddress().isLoopbackAddress();
        for (var entry : ASSETS.entrySet()) {
            try (var stream = WebMapServer.class.getResourceAsStream("/assets/openmapdelta/web/" + entry.getValue())) {
                if (stream == null) throw new IOException("Missing web map asset: " + entry.getValue());
                assets.put(entry.getKey(), stream.readAllBytes());
            }
        }
        // JDK HTTP defaults otherwise allow an incomplete request to occupy a worker forever.
        // Set these before its implementation is first initialized; preserve host JVM overrides.
        if (System.getProperty("sun.net.httpserver.maxReqTime") == null) {
            System.setProperty("sun.net.httpserver.maxReqTime", "10");
        }
        if (System.getProperty("sun.net.httpserver.maxRspTime") == null) {
            System.setProperty("sun.net.httpserver.maxRspTime", "10");
        }
        if (System.getProperty("sun.net.httpserver.timerMillis") == null) {
            System.setProperty("sun.net.httpserver.timerMillis", "1000");
        }
        server = HttpServer.create(address, 16);
        executor = new ThreadPoolExecutor(2, 2, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(32), runnable -> {
                    Thread thread = new Thread(runnable, "OpenMapDelta-Web");
                    thread.setDaemon(true);
                    return thread;
                }, new ThreadPoolExecutor.AbortPolicy());
        server.setExecutor(executor);
        server.createContext("/", this::handle);
    }

    public void start() { server.start(); }
    public int port() { return server.getAddress().getPort(); }
    public URI url() { return WebMapUrls.local(server.getAddress()); }
    public void publish(WebMapSnapshot snapshot) { this.snapshot = snapshot; }
    public synchronized java.util.List<ViewCenter> viewCenters() {
        long now = System.nanoTime();
        viewDemands.entrySet().removeIf(entry -> now - entry.getValue().updated() > TimeUnit.SECONDS.toNanos(15));
        return viewDemands.values().stream().map(ViewDemand::center).toList();
    }
    private synchronized void requestView(String viewer, int x, int z) {
        if (!viewDemands.containsKey(viewer) && viewDemands.size() >= 8) {
            viewDemands.remove(viewDemands.keySet().iterator().next());
        }
        viewDemands.put(viewer, new ViewDemand(new ViewCenter(x, z), System.nanoTime()));
    }

    private void handle(HttpExchange exchange) throws IOException {
        try (exchange) {
          try {
            securityHeaders(exchange);
            if (!validHost(exchange)) { reply(exchange, 403, "text/plain", "Invalid host"); return; }
            String path = exchange.getRequestURI().getPath();
            if (!allow(exchange, "requests", 2000)) { reply(exchange, 429, "text/plain", "Try again later"); return; }
            if (path.equals("/login")) { login(exchange); return; }
            if (path.equals("/logout")) {
                if (!exchange.getRequestMethod().equals("POST") || !sameOrigin(exchange)) {
                    reply(exchange, 403, "text/plain", "Forbidden"); return;
                }
                exchange.getResponseHeaders().set("Set-Cookie", COOKIE + "=; HttpOnly; SameSite=Strict; Path=/; Max-Age=0");
                reply(exchange, 200, "application/json", "{}"); return;
            }
            if (!exchange.getRequestMethod().equals("GET")) { reply(exchange, 405, "text/plain", "Method not allowed"); return; }
            if (assets.containsKey(path)) {
                String type = path.endsWith(".js") ? "text/javascript" : path.endsWith(".css") ? "text/css" : "text/html";
                reply(exchange, 200, type, assets.get(path)); return;
            }
            WebMapSnapshot view = snapshot;
            if (path.equals("/locale.json")) { reply(exchange, 200, "application/json", view.locale()); return; }
            String token = sessionCookie(exchange);
            if (passwordRequired && !auth.verify(token)) {
                reply(exchange, 401, "application/json", "{\"authenticationRequired\":true}"); return;
            }
            // Renew browser persistence, without assigning any expiry to the signed session itself.
            if (passwordRequired) setSessionCookie(exchange, token);
            if (path.equals("/api/state")) {
                Map<String, String> query = parameters(exchange.getRequestURI().getRawQuery());
                if (query.containsKey("x") && query.containsKey("z")) {
                    int x = Integer.parseInt(query.get("x")), z = Integer.parseInt(query.get("z"));
                    if (Math.abs((long) x) >= 30_000_000 || Math.abs((long) z) >= 30_000_000) {
                        reply(exchange, 400, "text/plain", "Outside map bounds"); return;
                    }
                    String viewer = query.getOrDefault("viewer", "default");
                    if (!viewer.matches("[a-zA-Z0-9-]{1,40}")) {
                        reply(exchange, 400, "text/plain", "Invalid viewer"); return;
                    }
                    requestView(viewer, x, z);
                }
                reply(exchange, 200, "application/json", view.state()); return;
            }
            if (path.equals("/api/tile")) {
                Map<String, String> query = parameters(exchange.getRequestURI().getRawQuery());
                String key = query.get("key");
                if (key == null || !key.matches("-?[0-9]{1,11},-?[0-9]{1,11}")) {
                    reply(exchange, 400, "text/plain", "Invalid tile"); return;
                }
                WebMapSnapshot.Image tile = view.tiles().get(key);
                if (tile == null) { reply(exchange, 404, "text/plain", "Unknown tile"); return; }
                if (query.containsKey("v") && !query.get("v").equals(Long.toString(tile.revision()))) {
                    reply(exchange, 409, "text/plain", "Map changed"); return;
                }
                reply(exchange, 200, "image/png", tile.png()); return;
            }
            if (path.equals("/api/icon")) {
                Map<String, String> query = parameters(exchange.getRequestURI().getRawQuery());
                String key = query.get("key");
                if (key == null || !key.matches("[a-f0-9]{64}")) {
                    reply(exchange, 400, "text/plain", "Invalid icon"); return;
                }
                WebMapSnapshot.Image icon = view.icons().get(key);
                if (icon == null) { reply(exchange, 404, "text/plain", "Unknown icon"); return; }
                if (query.containsKey("v") && !query.get("v").equals(Long.toString(icon.revision()))) {
                    reply(exchange, 409, "text/plain", "Icon changed"); return;
                }
                reply(exchange, 200, "image/png", icon.png()); return;
            }
            reply(exchange, 404, "text/plain", "Not found");
          } catch (IllegalArgumentException e) {
            // Invalid encodings and untrusted query values never expose exception details.
            if (exchange.getResponseCode() == -1) reply(exchange, 400, "text/plain", "Invalid request");
          }
        }
    }

    private void login(HttpExchange exchange) throws IOException {
        if (!exchange.getRequestMethod().equals("POST") || !sameOrigin(exchange)) {
            reply(exchange, 403, "text/plain", "Forbidden"); return;
        }
        if (!allow(exchange, "login", 10)) { reply(exchange, 429, "text/plain", "Try again later"); return; }
        String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
        if (contentType == null || !contentType.startsWith("application/x-www-form-urlencoded")) {
            reply(exchange, 415, "text/plain", "Unsupported content type"); return;
        }
        byte[] body = exchange.getRequestBody().readNBytes(4097);
        if (body.length > 4096) { reply(exchange, 413, "text/plain", "Request too large"); return; }
        String candidate = parameters(new String(body, StandardCharsets.UTF_8)).get("password");
        if (passwordRequired && !auth.checkPassword(candidate)) {
            reply(exchange, 401, "application/json", "{\"error\":\"authentication\"}"); return;
        }
        if (passwordRequired) setSessionCookie(exchange, auth.newSession());
        reply(exchange, 200, "application/json", "{}");
    }

    private boolean validHost(HttpExchange exchange) {
        String host = exchange.getRequestHeaders().getFirst("Host");
        if (host == null || host.length() > 300) return false;
        try {
            URI uri = URI.create("http://" + host);
            if (uri.getHost() == null || uri.getRawUserInfo() != null || !uri.getPath().isEmpty()
                    || uri.getRawQuery() != null || uri.getRawFragment() != null) return false;
            if (uri.getPort() != port()) return false;
            String name = uri.getHost();
            return !loopback || name.equalsIgnoreCase("localhost") || name.equals("127.0.0.1")
                    || name.equals("[::1]") || name.equals("::1") || name.equalsIgnoreCase(url().getHost());
        } catch (IllegalArgumentException e) { return false; }
    }

    private static boolean sameOrigin(HttpExchange exchange) {
        String origin = exchange.getRequestHeaders().getFirst("Origin");
        String fetchSite = exchange.getRequestHeaders().getFirst("Sec-Fetch-Site");
        if (fetchSite != null && !fetchSite.equals("same-origin") && !fetchSite.equals("none")) return false;
        return origin == null || origin.equalsIgnoreCase("http://" + exchange.getRequestHeaders().getFirst("Host"));
    }

    private synchronized boolean allow(HttpExchange exchange, String scope, int maximum) {
        long now = System.nanoTime();
        String key = scope + ":" + exchange.getRemoteAddress().getAddress().getHostAddress();
        limits.entrySet().removeIf(entry -> now - entry.getValue().start() > TimeUnit.MINUTES.toNanos(1));
        Window window = limits.get(key);
        if (window == null) {
            if (limits.size() >= 1024) return false;
            limits.put(key, new Window(now, 1)); return true;
        }
        if (window.count() >= maximum) return false;
        limits.put(key, new Window(window.start(), window.count() + 1)); return true;
    }

    private static Map<String, String> parameters(String encoded) {
        if (encoded == null || encoded.isEmpty()) return Map.of();
        if (encoded.length() > 4096) throw new IllegalArgumentException("Request too large");
        Map<String, String> values = new LinkedHashMap<>();
        for (String part : encoded.split("&", 16)) {
            String[] pair = part.split("=", 2);
            values.put(URLDecoder.decode(pair[0], StandardCharsets.UTF_8),
                    pair.length > 1 ? URLDecoder.decode(pair[1], StandardCharsets.UTF_8) : "");
        }
        return values;
    }

    private static String sessionCookie(HttpExchange exchange) {
        String cookies = exchange.getRequestHeaders().getFirst("Cookie");
        if (cookies == null || cookies.length() > 4096) return null;
        for (String part : cookies.split(";")) {
            String value = part.trim();
            if (value.startsWith(COOKIE + "=")) return value.substring(COOKIE.length() + 1);
        }
        return null;
    }
    private static void setSessionCookie(HttpExchange exchange, String token) {
        exchange.getResponseHeaders().set("Set-Cookie", COOKIE + "=" + token
                + "; HttpOnly; SameSite=Strict; Path=/; Max-Age=2147483647");
    }
    private static void securityHeaders(HttpExchange exchange) {
        var headers = exchange.getResponseHeaders();
        headers.set("Cache-Control", "no-store"); headers.set("X-Content-Type-Options", "nosniff");
        headers.set("Referrer-Policy", "no-referrer"); headers.set("X-Frame-Options", "DENY");
        headers.set("Content-Security-Policy", "default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' data:; base-uri 'none'; object-src 'none'; frame-ancestors 'none'; form-action 'self'");
    }
    private static void reply(HttpExchange exchange, int status, String type, String text) throws IOException {
        reply(exchange, status, type, text.getBytes(StandardCharsets.UTF_8));
    }
    private static void reply(HttpExchange exchange, int status, String type, byte[] body) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", type + (type.startsWith("text/") || type.equals("application/json") ? "; charset=utf-8" : ""));
        exchange.sendResponseHeaders(status, body.length);
        exchange.getResponseBody().write(body);
    }
    @Override public void close() { server.stop(0); executor.shutdownNow(); }
}
