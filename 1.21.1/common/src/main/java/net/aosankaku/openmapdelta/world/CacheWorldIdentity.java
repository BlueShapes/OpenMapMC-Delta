// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.world;

import java.net.IDN;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.file.Path;
import java.util.Locale;
import java.util.UUID;

/** Builds versioned, stable cache identities without resolving server names. */
public final class CacheWorldIdentity {
    private static final int DEFAULT_PORT = 25565;

    private CacheWorldIdentity() {}

    /** Returns a lowercase host and explicit port, or an empty string for malformed input. */
    public static String normalizeServerAddress(String address) {
        if (address == null) return "";
        String value = address.trim();
        if (value.isEmpty()) return "";

        String host;
        String portText = null;
        if (value.charAt(0) == '[') {
            int closingBracket = value.indexOf(']');
            if (closingBracket < 0) return "";
            host = value.substring(1, closingBracket);
            String remainder = value.substring(closingBracket + 1);
            if (!remainder.isEmpty()) {
                if (remainder.charAt(0) != ':' || remainder.length() == 1) return "";
                portText = remainder.substring(1);
            }
            host = normalizeIpv6(host);
            if (host.isEmpty()) return "";
            int port = parsePort(portText);
            return port < 0 ? "" : "[" + host + "]:" + port;
        }

        int firstColon = value.indexOf(':');
        int lastColon = value.lastIndexOf(':');
        if (firstColon >= 0 && firstColon != lastColon) {
            // An unbracketed multi-colon address is treated as a bare IPv6 literal.
            host = normalizeIpv6(value);
            if (host.isEmpty()) return "";
            return "[" + host + "]:" + DEFAULT_PORT;
        }
        if (lastColon >= 0) {
            host = value.substring(0, lastColon);
            portText = value.substring(lastColon + 1);
            if (host.isEmpty() || portText.isEmpty()) return "";
        } else {
            host = value;
        }

        // DNS names are canonicalized as ASCII without any DNS lookup.
        if (host.endsWith(".")) host = host.substring(0, host.length() - 1);
        if (host.isEmpty() || host.endsWith(".") || host.indexOf('%') >= 0
                || host.indexOf('/') >= 0 || host.indexOf('\\') >= 0) return "";
        try {
            String ascii = IDN.toASCII(host, IDN.USE_STD3_ASCII_RULES).toLowerCase(Locale.ROOT);
            if (ascii.isEmpty() || ascii.length() > 253) return "";
            int port = parsePort(portText);
            return port < 0 ? "" : ascii + ":" + port;
        } catch (IllegalArgumentException invalidName) {
            return "";
        }
    }

    public static String multiplayer(String address, long hashedSeed, UUID serverWorldId) {
        return multiplayer(address, hashedSeed, serverWorldId, false);
    }

    public static String multiplayer(String address, long hashedSeed, UUID serverWorldId, boolean identityRequired) {
        if (identityRequired && serverWorldId == null) return null;
        String endpoint = normalizeServerAddress(address);
        if (endpoint.isEmpty()) return null;
        return "world-v2:multiplayer:" + endpoint + ":seed:" + Long.toUnsignedString(hashedSeed, 16)
                + ":id:" + (serverWorldId == null ? "none" : serverWorldId);
    }

    public static String singleplayer(Path savePath, long hashedSeed, UUID serverWorldId) {
        if (savePath == null) return null;
        Path normalized = savePath.toAbsolutePath().normalize();
        return "world-v2:singleplayer:" + normalized + ":seed:" + Long.toUnsignedString(hashedSeed, 16)
                + ":id:" + (serverWorldId == null ? "none" : serverWorldId);
    }

    private static int parsePort(String text) {
        if (text == null) return DEFAULT_PORT;
        if (text.isEmpty()) return -1;
        int firstSignificantDigit = 0;
        while (firstSignificantDigit < text.length() && text.charAt(firstSignificantDigit) == '0') {
            firstSignificantDigit++;
        }
        if (text.length() - firstSignificantDigit > 5 || firstSignificantDigit == text.length()) return -1;
        int port = 0;
        for (int i = firstSignificantDigit; i < text.length(); i++) {
            char character = text.charAt(i);
            if (character < '0' || character > '9') return -1;
            port = port * 10 + character - '0';
        }
        return port >= 1 && port <= 65535 ? port : -1;
    }

    private static String normalizeIpv6(String literal) {
        if (literal.isEmpty() || literal.indexOf(':') < 0) return "";
        for (int i = 0; i < literal.length(); i++) {
            char character = literal.charAt(i);
            if (!(character == ':' || character == '.'
                    || character >= '0' && character <= '9'
                    || character >= 'a' && character <= 'f'
                    || character >= 'A' && character <= 'F')) return "";
        }
        try {
            InetAddress parsed = InetAddress.getByName(literal);
            if (!(parsed instanceof Inet6Address)) return "";
            return parsed.getHostAddress().toLowerCase(Locale.ROOT);
        } catch (UnknownHostException invalidAddress) {
            return "";
        }
    }
}
