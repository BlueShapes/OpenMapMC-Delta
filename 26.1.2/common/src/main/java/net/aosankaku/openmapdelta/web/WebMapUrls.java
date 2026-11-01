// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.web;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Optional;
import java.util.Locale;

/** Public navigation URLs only; never includes web map credentials or session keys. */
public final class WebMapUrls {
    private WebMapUrls() {}

    public static URI local(InetSocketAddress listener) {
        if (listener == null || listener.isUnresolved() || listener.getPort() < 1) {
            throw new IllegalArgumentException("A bound web map listener is required");
        }
        String host = listener.getAddress().isAnyLocalAddress() ? "localhost" : listener.getAddress().getHostAddress();
        try {
            return new URI("http", null, host, listener.getPort(), "/", null, null);
        } catch (URISyntaxException invalid) {
            throw new IllegalArgumentException("Invalid listener address", invalid);
        }
    }

    public static Optional<URI> shared(boolean enabled, String address) {
        if (!enabled || address == null || address.isBlank() || address.length() > 2_048
                || address.chars().anyMatch(ch -> Character.isISOControl(ch) || ch == 0x2028 || ch == 0x2029)) {
            return Optional.empty();
        }
        try {
            URI uri = new URI(address.trim());
            if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                    || uri.getHost() == null || uri.getRawUserInfo() != null
                    || uri.getPort() == 0 || uri.getPort() > 65_535) return Optional.empty();
            return Optional.of(new URI(uri.getScheme().toLowerCase(Locale.ROOT)
                    + uri.toString().substring(uri.getScheme().length())));
        } catch (URISyntaxException invalid) {
            return Optional.empty();
        }
    }
}
