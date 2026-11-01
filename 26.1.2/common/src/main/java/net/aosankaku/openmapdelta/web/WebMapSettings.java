// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.web;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.Base64;

/** Separate settings so server passwords never enter the public map snapshot. */
public final class WebMapSettings {
    public boolean enabled = true;
    public String bindAddress = "127.0.0.1";
    public int port = 8901;
    public boolean requirePassword = false;
    public String password = "";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static WebMapSettings load(Path directory) throws IOException {
        Files.createDirectories(directory);
        Path file = directory.resolve("webmap.json");
        WebMapSettings settings;
        if (Files.exists(file)) {
            try (var reader = Files.newBufferedReader(file)) {
                settings = GSON.fromJson(reader, WebMapSettings.class);
            } catch (RuntimeException e) {
                throw new IOException("Invalid web map configuration", e);
            }
            if (settings == null) throw new IOException("Empty web map configuration");
        } else {
            settings = new WebMapSettings();
            Files.writeString(file, GSON.toJson(settings), StandardCharsets.UTF_8);
        }
        if (settings.port < 1 || settings.port > 65535 || settings.bindAddress == null
                || settings.bindAddress.isBlank() || settings.bindAddress.length() > 255
                || settings.password == null || settings.password.length() > 1024) {
            throw new IOException("Invalid web map settings");
        }
        String environmentPassword = System.getenv("ODYSSEY_WEB_PASSWORD");
        if (environmentPassword != null && !environmentPassword.isEmpty()) {
            settings.password = environmentPassword;
        }
        if (settings.requirePassword && settings.password.isEmpty()) {
            throw new IOException("Web map password is required but not configured");
        }
        return settings;
    }

    public static byte[] sessionSecret(Path directory) throws IOException {
        Path file = directory.resolve("webmap-session.key");
        if (Files.exists(file)) {
            try {
                byte[] bytes = Base64.getDecoder().decode(Files.readString(file).trim());
                if (bytes.length != 32) throw new IllegalArgumentException();
                return bytes;
            } catch (IllegalArgumentException e) {
                throw new IOException("Invalid web map session key", e);
            }
        }
        byte[] secret = new byte[32];
        new SecureRandom().nextBytes(secret);
        Files.writeString(file, Base64.getEncoder().encodeToString(secret), StandardCharsets.US_ASCII);
        return secret;
    }
}
