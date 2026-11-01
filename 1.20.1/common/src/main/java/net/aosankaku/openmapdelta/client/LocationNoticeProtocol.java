// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.client;

import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Exact, bounded wire forms used to confirm location notices over vanilla chat. */
public final class LocationNoticeProtocol {
    private static final Pattern ACK = Pattern.compile(
            "\\[Odyssey Map ACK:([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})]");
    private static final Pattern EMOTE = Pattern.compile("@([A-Za-z0-9_]{1,16}) (.+)");

    public record Emote(String recipientName, LocationNotice notice) {}

    private LocationNoticeProtocol() {}

    public static String ack(UUID noticeId) {
        if (noticeId == null) throw new IllegalArgumentException("Notice ID is required");
        return "[Odyssey Map ACK:" + noticeId + "]";
    }

    public static Optional<UUID> decodeAck(String text) {
        if (text == null || text.length() != 54) return Optional.empty();
        Matcher matcher = ACK.matcher(text);
        if (!matcher.matches()) return Optional.empty();
        try {
            UUID id = UUID.fromString(matcher.group(1));
            return id.toString().equals(matcher.group(1)) ? Optional.of(id) : Optional.empty();
        } catch (IllegalArgumentException invalid) {
            return Optional.empty();
        }
    }

    public static Optional<String> emoteBody(String playerName, LocationNotice notice) {
        if (!LocationNotice.validPlayerName(playerName) || notice == null) return Optional.empty();
        String payload = notice.encode();
        if (payload.length() > LocationNotice.MAX_MESSAGE_LENGTH
                || LocationNotice.decode(payload).filter(notice::equals).isEmpty()) return Optional.empty();
        String body = "@" + playerName + " " + payload;
        return PlayerCommandPayload.validText(body, PlayerCommandPayload.MAX_COMMAND_LENGTH - 4)
                ? Optional.of(body) : Optional.empty();
    }

    public static Optional<Emote> decodeEmote(String text) {
        if (text == null || !PlayerCommandPayload.validText(text, PlayerCommandPayload.MAX_COMMAND_LENGTH - 4)) {
            return Optional.empty();
        }
        Matcher matcher = EMOTE.matcher(text);
        if (!matcher.matches()) return Optional.empty();
        Optional<LocationNotice> notice = LocationNotice.decode(matcher.group(2));
        if (notice.isEmpty() || !notice.get().encode().equals(matcher.group(2))) return Optional.empty();
        return Optional.of(new Emote(matcher.group(1), notice.get()));
    }
}
