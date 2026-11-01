// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.client;

import java.util.Optional;

/** Bounded command arguments; command availability and UUID resolution stay in LocationSharing. */
public final class PlayerCommandPayload {
    public static final int MAX_COMMAND_LENGTH = 256;
    public static final int MAX_PRIVATE_MESSAGE_LENGTH = 220;

    private PlayerCommandPayload() {}

    public static Optional<String> teleport(String command, String playerName) {
        if (!("tp".equals(command) || "teleport".equals(command))
                || !LocationNotice.validPlayerName(playerName)) return Optional.empty();
        return bounded(command + " " + playerName);
    }

    public static Optional<String> privateMessage(String command, String playerName, String message) {
        if (!("w".equals(command) || "msg".equals(command) || "tell".equals(command))
                || !LocationNotice.validPlayerName(playerName) || !validMessage(message)) return Optional.empty();
        return bounded(command + " " + playerName + " " + message);
    }

    public static Optional<String> emote(String playerName, LocationNotice notice) {
        return LocationNoticeProtocol.emoteBody(playerName, notice)
                .flatMap(body -> bounded("me " + body));
    }

    public static boolean validMessage(String message) {
        return validText(message, MAX_PRIVATE_MESSAGE_LENGTH);
    }

    static boolean validText(String message, int maxLength) {
        if (message == null || message.isBlank() || maxLength < 1 || message.length() > maxLength) return false;
        for (int i = 0; i < message.length(); i++) {
            char ch = message.charAt(i);
            if (Character.isISOControl(ch) || ch == '\u00a7' || ch == '\u2028' || ch == '\u2029') return false;
            if (Character.isHighSurrogate(ch)) {
                if (i + 1 >= message.length() || !Character.isLowSurrogate(message.charAt(++i))) return false;
            } else if (Character.isLowSurrogate(ch)) {
                return false;
            }
        }
        return true;
    }

    private static Optional<String> bounded(String command) {
        return command.length() <= MAX_COMMAND_LENGTH ? Optional.of(command) : Optional.empty();
    }
}
