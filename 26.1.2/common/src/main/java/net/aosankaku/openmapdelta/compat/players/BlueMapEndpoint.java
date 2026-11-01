// Copyright (c) 2026 AoSankaku. Licensed under Apache-2.0.
package net.aosankaku.openmapdelta.compat.players;

import java.net.URI;

/** Explicit user-selected website and map; never inferred from a Minecraft server address. */
public record BlueMapEndpoint(String baseUrl, String mapId, String liveDataRoot) {
    public BlueMapEndpoint(String baseUrl, String mapId) { this(baseUrl, mapId, "maps"); }
    public BlueMapEndpoint {
        if (baseUrl == null || baseUrl.length() > 2048 || mapId == null
                || !mapId.matches("[A-Za-z0-9_.-]{1,128}") || mapId.equals(".") || mapId.equals("..")) {
            throw new IllegalArgumentException("Invalid BlueMap endpoint");
        }
        if (liveDataRoot == null || liveDataRoot.length() > 256 || !liveDataRoot.matches("[A-Za-z0-9_.-]+(/[A-Za-z0-9_.-]+)*")
                || java.util.Arrays.stream(liveDataRoot.split("/")).anyMatch(part -> part.equals(".") || part.equals("..")))
            throw new IllegalArgumentException("Invalid BlueMap live data root");
        URI base;
        try { base = URI.create(baseUrl.trim()); }
        catch (RuntimeException invalid) { throw new IllegalArgumentException("Invalid BlueMap URL"); }
        if (!("https".equals(base.getScheme()) || "http".equals(base.getScheme()))
                || base.getHost() == null || base.getRawUserInfo() != null || base.getRawQuery() != null
                || base.getRawFragment() != null || base.getPort() > 65535) {
            throw new IllegalArgumentException("Expected an HTTP website URL without credentials, query or fragment");
        }
        baseUrl = base.toASCIIString();
        if (!baseUrl.endsWith("/")) baseUrl += "/";
    }

    public URI playersUri() { return URI.create(baseUrl).resolve(liveDataRoot + "/" + mapId + "/live/players.json"); }
}
