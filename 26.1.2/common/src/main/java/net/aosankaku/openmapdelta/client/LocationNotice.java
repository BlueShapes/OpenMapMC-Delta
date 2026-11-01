// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.client;

import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Readable vanilla whisper payload shared by every supported Minecraft version. */
public record LocationNotice(UUID id, String dimension, int x, int y, int z, int durationSeconds,
                             UUID trackedPlayerId, String message) {
    private static final Pattern PAYLOAD = Pattern.compile(
            "\\[Odyssey Map:([0-9a-f-]{36})] ([a-z0-9_.-]+:[a-z0-9_./-]+) X:(-?[0-9]{1,8}) Y:(-?[0-9]{1,8}) Z:(-?[0-9]{1,8})(?: D:([0-9]{1,3})s)?(?: P:([0-9a-f-]{36}))?(?: M:(.*))?");
    public static final int MAX_MESSAGE_LENGTH = 220;
    public static final int DEFAULT_DURATION_SECONDS = 15;
    public static final int MIN_DURATION_SECONDS = 5;
    public static final int MAX_DURATION_SECONDS = 600;

    public LocationNotice {
        if (durationSeconds < MIN_DURATION_SECONDS || durationSeconds > MAX_DURATION_SECONDS) {
            throw new IllegalArgumentException("Notification duration must be between 5 and 600 seconds");
        }
        if (!validMessage(message)) throw new IllegalArgumentException("Invalid notification message");
    }

    /** Compatibility constructor for the pre-message wire format. */
    public LocationNotice(UUID id, String dimension, int x, int y, int z, int durationSeconds,
                          UUID trackedPlayerId) {
        this(id, dimension, x, y, z, durationSeconds, trackedPlayerId, "");
    }

    /** Compatibility constructor for fixed-coordinate notices with a selected duration. */
    public LocationNotice(UUID id, String dimension, int x, int y, int z, int durationSeconds) {
        this(id, dimension, x, y, z, durationSeconds, null);
    }

    /** Compatibility constructor for callers that use the standard notice duration. */
    public LocationNotice(UUID id, String dimension, int x, int y, int z) {
        this(id, dimension, x, y, z, DEFAULT_DURATION_SECONDS, null);
    }

    public String encode() {
        String payload = "[Odyssey Map:" + id + "] " + dimension + " X:" + x + " Y:" + y + " Z:" + z;
        if (durationSeconds != DEFAULT_DURATION_SECONDS) payload += " D:" + durationSeconds + "s";
        if (trackedPlayerId != null) payload += " P:" + trackedPlayerId;
        return message.isEmpty() ? payload : payload + " M:" + message;
    }

    /** Copy this preview with a unique receipt identity for each actual send. */
    public LocationNotice withId(UUID newId) {
        return new LocationNotice(newId, dimension, x, y, z, durationSeconds, trackedPlayerId, message);
    }

    public static Optional<LocationNotice> decode(String text) {
        if (text == null || text.length() > MAX_MESSAGE_LENGTH) return Optional.empty();
        Matcher matcher = PAYLOAD.matcher(text);
        if (!matcher.matches()) return Optional.empty();
        try {
            UUID id = canonicalUuid(matcher.group(1));
            UUID trackedPlayerId = matcher.group(7) == null ? null : canonicalUuid(matcher.group(7));
            int x = Integer.parseInt(matcher.group(3));
            int y = Integer.parseInt(matcher.group(4));
            int z = Integer.parseInt(matcher.group(5));
            int durationSeconds = matcher.group(6) == null
                    ? DEFAULT_DURATION_SECONDS : Integer.parseInt(matcher.group(6));
            String message = matcher.group(8) == null ? "" : matcher.group(8);
            if (Math.abs((long) x) >= 30_000_000 || Math.abs((long) z) >= 30_000_000
                    || Math.abs((long) y) > 30_000_000) return Optional.empty();
            if (matcher.group(8) != null && message.isEmpty() || !validMessage(message)) return Optional.empty();
            return Optional.of(new LocationNotice(id, matcher.group(2), x, y, z, durationSeconds,
                    trackedPlayerId, message));
        } catch (IllegalArgumentException invalid) {
            return Optional.empty();
        }
    }

    private static UUID canonicalUuid(String value) {
        UUID id = UUID.fromString(value);
        if (!id.toString().equals(value)) throw new IllegalArgumentException("Noncanonical UUID");
        return id;
    }

    public static boolean validPlayerName(String name) {
        return name != null && name.matches("[A-Za-z0-9_]{1,16}");
    }

    /** One-line text safe to embed in the bounded vanilla-chat protocol. */
    public static boolean validMessage(String message) {
        if (message == null || message.length() > MAX_MESSAGE_LENGTH || message.indexOf('\u00a7') >= 0) return false;
        for (int i = 0; i < message.length(); i++) {
            char current = message.charAt(i);
            if (Character.isISOControl(current) || current == '\u2028' || current == '\u2029') return false;
            if (Character.isHighSurrogate(current)) {
                if (i + 1 >= message.length() || !Character.isLowSurrogate(message.charAt(i + 1))) return false;
                i++;
            } else if (Character.isLowSurrogate(current)) {
                return false;
            }
        }
        return true;
    }
}
