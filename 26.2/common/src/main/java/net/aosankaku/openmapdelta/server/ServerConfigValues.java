// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.server;

import net.aosankaku.openmapdelta.web.WebMapUrls;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.charset.CodingErrorAction;
import java.nio.ByteBuffer;

/** The explicit allowlist of non-secret server settings editable in game. */
public record ServerConfigValues(boolean allowCaveMap, boolean webMapEnabled, String webMapUrl,
                                 int maxSharedWaypoints) {
    public static final int MAX_SHARED_WAYPOINTS = 4096;
    public static final int MAX_URL_LENGTH = 2048;
    public static final int MAX_URL_BYTES = MAX_URL_LENGTH * 3;

    public ServerConfigValues {
        if (webMapUrl == null || webMapUrl.length() > MAX_URL_LENGTH
                || webMapUrl.getBytes(StandardCharsets.UTF_8).length > MAX_URL_BYTES
                || maxSharedWaypoints < 0 || maxSharedWaypoints > MAX_SHARED_WAYPOINTS) {
            throw new IllegalArgumentException("Invalid server settings");
        }
        if ((webMapEnabled || !webMapUrl.isEmpty()) && WebMapUrls.shared(true, webMapUrl).isEmpty()) {
            throw new IllegalArgumentException("Invalid public web map URL");
        }
        webMapUrl = webMapUrl.trim();
    }

    static void write(DataOutputStream out, ServerConfigValues values) throws IOException {
        out.writeBoolean(values.allowCaveMap());
        out.writeBoolean(values.webMapEnabled());
        byte[] url = values.webMapUrl().getBytes(StandardCharsets.UTF_8);
        out.writeInt(url.length);
        out.write(url);
        out.writeInt(values.maxSharedWaypoints());
    }

    static boolean readBoolean(DataInputStream in) throws IOException {
        int value = in.readUnsignedByte();
        if (value > 1) throw new IllegalArgumentException("Invalid boolean");
        return value == 1;
    }

    static ServerConfigValues read(DataInputStream in) throws IOException {
        boolean cave = readBoolean(in), web = readBoolean(in);
        int length = in.readInt();
        if (length < 0 || length > MAX_URL_BYTES) throw new IllegalArgumentException("Invalid URL length");
        byte[] bytes = new byte[length];
        in.readFully(bytes);
        String url = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
        return new ServerConfigValues(cave, web, url, in.readInt());
    }
}
