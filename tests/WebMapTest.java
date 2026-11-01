// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.web;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Map;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;

/** Exercises the real HTTP boundary without launching Minecraft. */
public final class WebMapTest {
    private static int checks;
    private static final String PASSWORD = "fixture-only";
    private static void publicMapUrls() throws Exception {
        check(WebMapUrls.local(new InetSocketAddress("127.0.0.1", 8901)).toString()
                .equals("http://127.0.0.1:8901/"), "Loopback announcement URL is invalid");
        check(WebMapUrls.local(new InetSocketAddress("0.0.0.0", 8901)).toString()
                .equals("http://localhost:8901/"), "Wildcard IPv4 bind address leaked into the link");
        check(WebMapUrls.local(new InetSocketAddress("::", 8901)).toString()
                .equals("http://localhost:8901/"), "Wildcard IPv6 bind address leaked into the link");
        URI ipv6 = WebMapUrls.local(new InetSocketAddress("::1", 8901));
        check(ipv6.getHost().startsWith("[") && ipv6.getPort() == 8901,
                "IPv6 listener is not represented by a bracketed URL");
        boolean unresolvedRejected = false;
        try { WebMapUrls.local(InetSocketAddress.createUnresolved("not-a-listener.example", 8901)); }
        catch (IllegalArgumentException expected) { unresolvedRejected = true; }
        check(unresolvedRejected, "Unresolved non-listener address was announced");
        check(WebMapUrls.shared(false, "https://map.example/").isEmpty(), "Disabled server map was announced");
        check(WebMapUrls.shared(true, null).isEmpty() && WebMapUrls.shared(true, "").isEmpty(),
                "Missing server map URL was announced");
        String publicUrl = "https://map.example:8443/map/?dimension=overworld#center";
        check(WebMapUrls.shared(true, publicUrl).orElseThrow().toString().equals(publicUrl),
                "Reverse proxy path, port, query or fragment changed");
        check(WebMapUrls.shared(true, "HTTPS://map.example/map%2Fname").orElseThrow().toString()
                .equals("https://map.example/map%2Fname"), "Scheme normalization changed an encoded URL path");
        check(WebMapUrls.shared(true, "http://[::1]:8901/").isPresent(), "Public IPv6 URL rejected");
        for (String invalid : new String[] {"javascript:alert(1)", "file:///private", "ftp://map.example/",
                "https:///missing-host", "https://user:password@map.example/", "https://map.example:0/",
                "https://map.example:65536/", "https://map.example/\nspoof", "https://map.example/\rspoof",
                "https://map.example/\u2028spoof", "https://map.example/\u2029spoof",
                "https://map.example/" + "x".repeat(2048)}) {
            check(WebMapUrls.shared(true, invalid).isEmpty(), "Invalid or private URL was announced");
        }
    }
    private static void check(boolean result, String message) {
        checks++; if (!result) throw new AssertionError(message);
    }
    public static void main(String[] args) throws Exception {
        if (args.length > 0 && args[0].equals("--serve")) {
            try (WebMapServer server = server(Integer.parseInt(args[1]))) {
                System.out.println("FIXTURE_PORT=" + server.port());
                new java.io.BufferedReader(new java.io.InputStreamReader(System.in)).readLine();
            }
            return;
        }
        publicMapUrls();
        byte[] secret = new byte[32]; Arrays.fill(secret, (byte) 42);
        WebMapAuth auth = new WebMapAuth(PASSWORD, secret);
        check(auth.checkPassword(PASSWORD), "Correct password rejected");
        check(!auth.checkPassword("wrong"), "Wrong password accepted");
        String token = auth.newSession();
        check(auth.verify(token), "Session cannot be verified");
        check(new WebMapAuth(PASSWORD, secret).verify(token), "Session expired across a restart");
        check(!new WebMapAuth("changed", secret).verify(token), "Password change did not revoke session");
        check(!auth.verify(token.substring(0, 2) + "!" + token.substring(3)), "Malformed nonce accepted");
        char changed = token.charAt(44) == 'A' ? 'B' : 'A';
        check(!auth.verify(token.substring(0, 44) + changed + token.substring(45)), "Forged signature accepted");
        check(!auth.verify(null) && !auth.verify("x".repeat(5000)), "Oversized session accepted");

        var configDirectory = Files.createTempDirectory("odyssey-web-test");
        try {
            check(WebMapSettings.load(configDirectory).port == 8901, "Default port changed");
            check(WebMapSettings.load(configDirectory).bindAddress.equals("127.0.0.1"), "Default bind is public");
            byte[] generatedSecret = WebMapSettings.sessionSecret(configDirectory);
            check(Arrays.equals(generatedSecret, WebMapSettings.sessionSecret(configDirectory)), "Session key was not retained");
            Files.writeString(configDirectory.resolve("webmap.json"), "{\"requirePassword\":true,\"password\":\"\"}");
            boolean closed = false;
            try { WebMapSettings.load(configDirectory); } catch (java.io.IOException expected) { closed = true; }
            check(closed, "Empty required password did not fail closed");
        } finally {
            for (var file : java.util.List.of("webmap.json", "webmap-session.key")) Files.deleteIfExists(configDirectory.resolve(file));
            Files.delete(configDirectory);
        }
        int[] pixels = {0xff0000ff, 0xff00ff00, 0xffff0000, 0};
        var image = ImageIO.read(new java.io.ByteArrayInputStream(WebMapPng.encode(pixels, 2, 2)));
        check(image.getRGB(0, 0) == 0xffff0000 && image.getRGB(1, 0) == 0xff00ff00
                && image.getRGB(0, 1) == 0xff0000ff && image.getRGB(1, 1) == 0, "PNG pixel color/orientation mismatch");

        BufferedImage skin = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        skin.setRGB(8, 8, 0xffff0000);
        skin.setRGB(40, 8, 0x800000ff);
        skin.setRGB(9, 8, 0xff00ff00);
        WebMapImagePixels face = WebMapImagePixels.fromArgb(skin).minecraftFace();
        check(face != null && face.width() == 8 && face.height() == 8, "Player face is not an 8x8 crop");
        var facePng = ImageIO.read(new java.io.ByteArrayInputStream(WebMapPng.encode(face.pixels(), face.width(), face.height())));
        check(facePng.getRGB(0, 0) == 0xff7f0080, "Skin hat alpha or PNG color-channel order is wrong");
        check(facePng.getRGB(1, 0) == 0xff00ff00, "Skin face UV crop shifted or transparent hat obscured the base");
        BufferedImage createAsset = new BufferedImage(1024, 1024, BufferedImage.TYPE_INT_ARGB);
        createAsset.setRGB(512, 512, 0xffd89a31);
        WebMapImagePixels scaledIcon = WebMapImagePixels.fromArgb(createAsset).textureRegion(0, 0, 1024, 1024, 1024, 1024);
        check(scaledIcon != null && scaledIcon.width() == 128 && scaledIcon.height() == 128,
                "Large bundled station icon was not bounded to 128x128");
        String opaqueIconKey = WebMapIconCache.opaqueKey("item:minecraft:minecart");
        check(opaqueIconKey.matches("[a-f0-9]{64}") && !opaqueIconKey.contains("minecart"),
                "Icon URL key is not opaque");
        WebMapIconCache refreshCache = new WebMapIconCache();
        refreshCache.beginPublish(4);
        int[] oldSkin = new int[64]; Arrays.fill(oldSkin, 0xff0000ff);
        var oldImage = refreshCache.get("skin:shared", 1, 1,
                () -> new WebMapImagePixels(8, 8, oldSkin));
        refreshCache.beginPublish(4);
        var sameSkin = refreshCache.get("skin:shared", 1, 1,
                () -> new WebMapImagePixels(8, 8, oldSkin));
        check(oldImage != null && sameSkin.revision() == oldImage.revision(),
                "Refreshing an unchanged GPU skin needlessly changed its revision");
        refreshCache.beginPublish(4);
        int[] downloadedSkin = new int[64]; Arrays.fill(downloadedSkin, 0xffff0000);
        var newImage = refreshCache.get("skin:shared", 1, 1,
                () -> new WebMapImagePixels(8, 8, downloadedSkin));
        check(newImage != null && newImage.revision() > oldImage.revision()
                        && newImage.copyPixels()[0] == downloadedSkin[0],
                "Refreshed in-place GPU skin did not publish its downloaded pixels");

        HttpClient client = HttpClient.newHttpClient();
        try (WebMapServer server = server(0)) {
            URI root = URI.create("http://127.0.0.1:" + server.port());
            check(server.url().equals(root.resolve("/")), "Announced URL does not use the actual listening port");
            var denied = get(client, root.resolve("/api/state"), null);
            check(denied.statusCode() == 401 && !denied.body().contains("Alice"), "State leaked without authentication");
            check(get(client, root.resolve("/api/tile?key=0,0"), null).statusCode() == 401, "Terrain leaked without authentication");
            String knownIconKey = WebMapIconCache.opaqueKey("fixture:player:alice");
            check(get(client, root.resolve("/api/icon?key=" + knownIconKey), null).statusCode() == 401,
                    "Player skin icon leaked without authentication");
            var asset = get(client, root, null);
            check(asset.statusCode() == 200 && asset.headers().firstValue("Content-Security-Policy").orElse("").contains("script-src 'self'"), "Static UI or CSP missing");
            check(asset.headers().firstValue("X-Content-Type-Options").orElse("").equals("nosniff"), "Security header missing");
            var badOrigin = post(client, root.resolve("/login"), "password=" + PASSWORD, "http://evil.example");
            check(badOrigin.statusCode() == 403, "Cross-site login accepted");
            check(post(client, root.resolve("/login"), "password=wrong", root.toString()).statusCode() == 401, "Wrong login accepted");
            var login = post(client, root.resolve("/login"), "password=" + PASSWORD, root.toString());
            String cookieHeader = login.headers().firstValue("Set-Cookie").orElseThrow();
            String cookie = cookieHeader.split(";", 2)[0];
            check(login.statusCode() == 200 && cookieHeader.contains("HttpOnly") && cookieHeader.contains("SameSite=Strict"), "Persistent session cookie missing protections");
            check(get(client, root.resolve("/api/state"), cookie).body().contains("Alice"), "Authenticated map unavailable");
            var icon = getBytes(client, root.resolve("/api/icon?key=" + knownIconKey + "&v=11"), cookie);
            check(icon.statusCode() == 200, "Published skin icon unavailable");
            var iconPng = ImageIO.read(new java.io.ByteArrayInputStream(icon.body()));
            check(iconPng.getWidth() == 8 && iconPng.getHeight() == 8 && iconPng.getRGB(0, 0) == 0xffff0000,
                    "Published icon dimensions or channels changed");
            check(get(client, root.resolve("/api/icon?key=" + "0".repeat(64)), cookie).statusCode() == 404,
                    "Unknown icon key exposed an asset");
            check(get(client, root.resolve("/api/icon?key=../../secret"), cookie).statusCode() == 400,
                    "Icon key was treated as a filesystem path");
            check(get(client, root.resolve("/api/icon?key=" + knownIconKey + "&v=12"), cookie).statusCode() == 409,
                    "Stale icon revision was served");
            check(get(client, root.resolve("/api/state?x=12000&z=-14000"), cookie).statusCode() == 200
                    && server.viewCenters().get(0).x() == 12000 && server.viewCenters().get(0).z() == -14000, "Panned viewport was ignored");
            check(get(client, root.resolve("/api/state?x=-9000&z=5000&viewer=second"), cookie).statusCode() == 200
                    && server.viewCenters().size() == 2, "One browser overwrote another viewport");
            check(get(client, root.resolve("/api/state?x=30000000&z=0"), cookie).statusCode() == 400, "Out-of-bounds viewport accepted");
            check(get(client, root.resolve("/api/state?x=NaN&z=0"), cookie).statusCode() == 400, "Invalid viewport accepted");
            check(get(client, root.resolve("/api/state"), cookie + "corrupted").statusCode() == 401, "Corrupted session accepted");
            check(get(client, root.resolve("/api/tile?key=../../secret"), cookie).statusCode() == 400, "Traversal tile accepted");
            check(get(client, root.resolve("/api/tile?key=128,0"), cookie).statusCode() == 404, "Unknown tile accepted");
            check(get(client, root.resolve("/api/tile?key=0,0&v=99"), cookie).statusCode() == 409, "Old map version accepted");
            check(get(client, root.resolve("/api/tile?key=0,0&v=7"), cookie).statusCode() == 200, "Known tile unavailable");
            try (var slow1 = slowRequest(server.port()); var slow2 = slowRequest(server.port())) {
                check(requestClosed(slow1) && requestClosed(slow2),
                        "Incomplete requests did not time out");
                check(get(client, root.resolve("/api/state"), cookie).statusCode() == 200,
                        "Incomplete requests kept the workers occupied");
            }
            check(post(client, root.resolve("/login"), "password=" + "a".repeat(5000), root.toString()).statusCode() == 413, "Oversized body accepted");
            for (int i = 0; i < 10; i++) post(client, root.resolve("/login"), "password=wrong", root.toString());
            check(post(client, root.resolve("/login"), "password=" + PASSWORD, root.toString()).statusCode() == 429, "Login rate limit missing");
        }
        try (WebMapServer alternate = new WebMapServer(new InetSocketAddress("127.0.0.2", 0),
                false, "", new byte[32])) {
            alternate.start();
            check(get(client, alternate.url(), null).statusCode() == 200,
                    "Announced alternate-loopback URL was rejected");
            check(hostStatus(alternate.url(), "evil.example:" + alternate.port()) == 403,
                    "Alternate-loopback listener accepted an unrelated Host");
            check(hostStatus(alternate.url(), "127.0.0.2:" + (alternate.port() + 1)) == 403,
                    "Alternate-loopback listener accepted the wrong Host port");
        }
        System.out.println("Web map: " + checks + " checks passed");
    }

    private static int hostStatus(URI uri, String host) throws Exception {
        try (java.net.Socket socket = new java.net.Socket(uri.getHost(), uri.getPort())) {
            socket.setSoTimeout(5000);
            socket.getOutputStream().write(("GET / HTTP/1.1\r\nHost: " + host
                    + "\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
            socket.getOutputStream().flush();
            var reader = new java.io.BufferedReader(new java.io.InputStreamReader(
                    socket.getInputStream(), StandardCharsets.US_ASCII));
            return Integer.parseInt(reader.readLine().split(" ")[1]);
        }
    }

    private static HttpResponse<String> get(HttpClient client, URI uri, String cookie) throws Exception {
        var request = HttpRequest.newBuilder(uri).timeout(java.time.Duration.ofSeconds(5)).GET();
        if (cookie != null) request.header("Cookie", cookie);
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }
    private static HttpResponse<byte[]> getBytes(HttpClient client, URI uri, String cookie) throws Exception {
        var request = HttpRequest.newBuilder(uri).timeout(java.time.Duration.ofSeconds(5)).GET();
        if (cookie != null) request.header("Cookie", cookie);
        return client.send(request.build(), HttpResponse.BodyHandlers.ofByteArray());
    }
    private static java.net.Socket slowRequest(int port) throws Exception {
        java.net.Socket socket = new java.net.Socket("127.0.0.1", port);
        socket.setSoTimeout(15000);
        socket.getOutputStream().write(("POST /login HTTP/1.1\r\nHost: 127.0.0.1:" + port
                + "\r\nContent-Type: application/x-www-form-urlencoded\r\nContent-Length: 100\r\n\r\npassword=x")
                .getBytes(StandardCharsets.US_ASCII));
        socket.getOutputStream().flush();
        return socket;
    }
    private static boolean requestClosed(java.net.Socket socket) throws Exception {
        try { return socket.getInputStream().read() == -1; }
        catch (java.net.SocketException reset) { return true; }
    }
    private static HttpResponse<String> post(HttpClient client, URI uri, String body, String origin) throws Exception {
        return client.send(HttpRequest.newBuilder(uri).timeout(java.time.Duration.ofSeconds(5))
                .header("Origin", origin).header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }
    private static WebMapServer server(int port) throws Exception {
        WebMapServer server = new WebMapServer(new InetSocketAddress("127.0.0.1", port), true, PASSWORD, new byte[32]);
        int[] pixels = new int[128 * 128]; Arrays.fill(pixels, 0xff367e44);
        String aliceKey = WebMapIconCache.opaqueKey("fixture:player:alice");
        String createKey = WebMapIconCache.opaqueKey("fixture:symbol:create_station");
        String mtrKey = WebMapIconCache.opaqueKey("fixture:symbol:mtr_station");
        String state = "{\"active\":true,\"dimension\":\"minecraft:overworld\",\"layer\":null,\"x\":0,\"y\":64,\"z\":0,\"revision\":1,\"tiles\":[{\"key\":\"0,0\",\"x\":-64,\"z\":-64,\"revision\":7}],\"players\":[{\"id\":\"alice\",\"name\":\"Alice\",\"dimension\":\"minecraft:overworld\",\"x\":10,\"y\":68,\"z\":20,\"self\":false,\"iconKey\":\"" + aliceKey + "\",\"iconRevision\":11},{\"id\":\"bob\",\"name\":\"Bob\",\"dimension\":\"minecraft:overworld\",\"x\":0,\"y\":64,\"z\":0,\"self\":true}],\"markers\":[{\"id\":\"xss\",\"name\":\"<img src=x onerror=alert(1)>\",\"x\":-20,\"y\":70,\"z\":30,\"color\":16711935,\"type\":\"WAYPOINT\",\"iconId\":\"symbol:odysseymap:create_station\",\"iconKey\":\"" + createKey + "\",\"iconRevision\":12},{\"id\":\"mtr\",\"name\":\"MTR\",\"x\":-40,\"y\":70,\"z\":90,\"color\":16776960,\"type\":\"WAYPOINT\",\"iconId\":\"symbol:odysseymap:mtr_station\",\"iconKey\":\"" + mtrKey + "\",\"iconRevision\":13},{\"id\":\"missing\",\"name\":\"Missing\",\"x\":-30,\"y\":70,\"z\":-30,\"color\":16711935,\"type\":\"WAYPOINT\",\"iconId\":\"item:missing:removed\"}],\"locale\":{}}";
        int[] red = new int[8 * 8]; Arrays.fill(red, 0xff0000ff);
        int[] brass = new int[16 * 16]; Arrays.fill(brass, 0xff00a5ff);
        int[] train = new int[16 * 16]; Arrays.fill(train, 0xff20d0e0);
        server.publish(new WebMapSnapshot(state.getBytes(StandardCharsets.UTF_8), "{}".getBytes(StandardCharsets.UTF_8),
                Map.of("0,0", new WebMapSnapshot.Image(pixels, 7)),
                Map.of(aliceKey, new WebMapSnapshot.Image(red, 8, 8, 11),
                        createKey, new WebMapSnapshot.Image(brass, 16, 16, 12),
                        mtrKey, new WebMapSnapshot.Image(train, 16, 16, 13))));
        server.start(); return server;
    }
}
