// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.client;

import net.aosankaku.openmapdelta.marker.TransientMarkerLifetime;
import java.util.UUID;
import java.util.List;

public final class ContextActionsTest {
    private static int checks;
    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        var outside = TransientMarkerLifetime.arrival(false);
        check(!outside.expired(true, 25.01, 0), "outside stays alive");
        check(outside.expired(true, 25, 0), "five-block boundary is arrival");

        var nearby = TransientMarkerLifetime.arrival(true);
        check(!nearby.expired(true, 0, 0), "created near player remains alive");
        check(!nearby.expired(true, 25, 0), "moving inside radius does not arm");
        check(!nearby.expired(true, 25.01, 0), "exit arms without deleting");
        check(nearby.expired(true, 24.99, 0), "reentry deletes");

        var dimension = TransientMarkerLifetime.arrival(true);
        check(!dimension.expired(false, 0, 0), "other dimension is outside sphere");
        check(dimension.expired(true, 0, 0), "returning after leaving dimension deletes");
        var distantDimension = TransientMarkerLifetime.arrival(false);
        check(!distantDimension.expired(false, 0, 0), "same XYZ in other dimension does not delete");
        check(distantDimension.expired(true, 0, 0), "arrival in target dimension deletes");

        var notice = TransientMarkerLifetime.notice(-50_000);
        check(!notice.expired(true, 0, -49_999), "notices ignore proximity");
        check(!notice.expired(false, 0, -35_001), "notices last until full fifteen seconds");
        check(notice.expired(false, 0, -35_000), "notice expires in another dimension at fifteen seconds");
        check(notice.expired(true, 0, 1_000_000), "late tick removes expired notice");
        var shortNotice = TransientMarkerLifetime.notice(-50_000, 5);
        check(!shortNotice.expired(false, 0, -45_001), "selected duration does not expire early at negative clock origin");
        check(shortNotice.expired(false, 0, -45_000), "selected duration expires exactly at its boundary");
        var longNotice = TransientMarkerLifetime.notice(12_000, 600);
        check(!longNotice.expired(false, 0, 611_999), "maximum duration remains active until its boundary");
        check(longNotice.expired(false, 0, 612_000), "maximum duration expires at its boundary");
        for (int invalidDuration : new int[]{0, 4, 601, Integer.MAX_VALUE}) {
            boolean rejected = false;
            try { TransientMarkerLifetime.notice(0, invalidDuration); }
            catch (IllegalArgumentException expected) { rejected = true; }
            check(rejected, "invalid lifetime duration rejected: " + invalidDuration);
        }

        UUID id = UUID.fromString("00f42b3b-d3ca-49b4-99f0-5acaaac9ff29");
        var payload = new LocationNotice(id, "example:deep/caves", -10, -64, 25);
        check(LocationNotice.decode(payload.encode()).orElseThrow().equals(payload), "payload round trip");
        check(payload.encode().equals("[Odyssey Map:" + id + "] example:deep/caves X:-10 Y:-64 Z:25"),
                "default duration preserves original wire format");
        check(LocationNotice.decode(payload.encode()).orElseThrow().durationSeconds() == 15,
                "old wire format decodes with default duration");
        for (int duration : new int[]{5, 30, 600}) {
            var selected = new LocationNotice(id, "example:deep/caves", -10, -64, 25, duration);
            check(LocationNotice.decode(selected.encode()).orElseThrow().equals(selected),
                    "selected duration round trip: " + duration);
        }
        check(new LocationNotice(id, "example:deep/caves", -10, -64, 25, 15).encode().equals(payload.encode()),
                "explicit default duration preserves old wire format");
        var limits = new LocationNotice(id, "minecraft:overworld", 29_999_999, 319, -29_999_999);
        check(LocationNotice.decode(limits.encode()).isPresent(), "in-range coordinates");
        check(LocationNotice.decode(null).isEmpty(), "null rejected");
        check(LocationNotice.decode(payload.encode() + "; tp @a 0 0 0").isEmpty(), "appended commands rejected");
        check(LocationNotice.decode("Alice whispers: " + payload.encode()).isEmpty(), "decorated chat is not a payload");
        check(LocationNotice.decode(payload.encode().replace("example:deep/caves", "example:deep caves")).isEmpty(), "invalid dimension rejected");
        check(LocationNotice.decode(payload.encode().replace("X:-10", "X:30000001")).isEmpty(), "outside world bounds rejected");
        check(LocationNotice.decode(payload.encode().replace("X:-10", "X:30000000")).isEmpty(), "world edge block center rejected");
        check(LocationNotice.decode(payload.encode().replace("Y:-64", "Y:-2147483648")).isEmpty(), "integer overflow/oversize rejected");
        check(LocationNotice.decode(payload.encode().replace(id.toString(), "00000000-0000-0000-0000-1")).isEmpty(), "noncanonical uuid rejected");
        check(LocationNotice.decode(payload.encode().replace("Y:-64", "Y:NaN")).isEmpty(), "noninteger rejected");
        check(LocationNotice.decode(payload.encode().replace("Y:-64", "Y:30000001")).isEmpty(), "unbounded Y rejected");
        for (String duration : new String[]{"0s", "4s", "601s", "-5s", "+5s", "999999999999999999999s", "5", "5ss"}) {
            String malformed = payload.encode() + " D:" + duration;
            check(LocationNotice.decode(malformed).isEmpty(), "invalid duration rejected: " + duration);
        }
        for (int invalidDuration : new int[]{0, 4, 601, Integer.MAX_VALUE}) {
            boolean rejected = false;
            try { new LocationNotice(id, "example:deep/caves", 0, 0, 0, invalidDuration); }
            catch (IllegalArgumentException expected) { rejected = true; }
            check(rejected, "invalid notice duration rejected: " + invalidDuration);
        }
        check(LocationNotice.decode(new LocationNotice(id, "example:" + "x".repeat(220), 0, 0, 0).encode()).isEmpty(), "oversize payload rejected");

        check(LocationNotice.validPlayerName("Player_123"), "valid recipient name");
        check(LocationNotice.validPlayerName("A"), "short name");
        check(!LocationNotice.validPlayerName("Alice tp @a"), "recipient command injection rejected");
        check(!LocationNotice.validPlayerName("@a"), "selectors rejected");
        check(!LocationNotice.validPlayerName("a".repeat(17)), "oversize name rejected");
        check(!LocationNotice.validPlayerName(null), "null recipient rejected");

        UUID a = UUID.randomUUID(), b = UUID.randomUUID(), c = UUID.randomUUID();
        var outbox = new LocationNoticeOutbox(2);
        check(outbox.enqueue(List.of(a, b, b), payload), "duplicate recipients occupy one slot");
        check(!outbox.enqueue(List.of(c), payload), "queue refuses overflowing batch");
        outbox.observeServerTime(100);
        check(outbox.takeReady(-5_000).recipient().equals(a), "negative clock origin allows first send");
        outbox.sent(-5_000);
        check(outbox.takeReady(-3_901) == null, "send interval prevents spam");
        check(outbox.takeReady(500_000) == null, "stalled server ticks cannot be extrapolated from wall time");
        outbox.observeServerTime(139);
        check(outbox.takeReady(-3_900) == null, "less than forty server ticks keeps spare spam budget");
        outbox.observeServerTime(140);
        check(outbox.takeReady(-3_900).recipient().equals(b), "next recipient ready after 1100ms");
        check(outbox.takeReady(-3_900) == null, "rejected batch did not partly enqueue");
        check(outbox.enqueue(List.of(a), payload), "queue slot can be reused");
        outbox.clear();
        check(outbox.takeReady(-3_900) == null, "logout clear discards old sends");
        check(outbox.enqueue(List.of(c), payload), "new session can enqueue");
        check(outbox.takeReady(-5_000).recipient().equals(c), "clear resets send clock across sessions");
        check(!outbox.enqueue(List.of(a), null), "null payload rejected");
        check(!outbox.enqueue(java.util.Arrays.asList(a, null), payload), "null recipient batch rejected");
        outbox.observeServerTime(500);
        outbox.sent(-5_000);
        check(outbox.enqueue(List.of(a), payload), "queue for dimension reset");
        outbox.observeServerTime(10);
        check(outbox.takeReady(0) == null, "backward server time resets cadence");
        outbox.observeServerTime(49);
        check(outbox.takeReady(0) == null, "dimension reset waits full forty new ticks");
        outbox.observeServerTime(50);
        check(outbox.takeReady(0).recipient().equals(a), "dimension reset resumes after new ticks");

        var durationOutbox = new LocationNoticeOutbox(1);
        var queuedDuration = new LocationNotice(id, "example:deep/caves", 1, 2, 3, 300);
        check(durationOutbox.enqueue(List.of(c), queuedDuration), "duration notice queues");
        check(durationOutbox.takeReady(0).notice().durationSeconds() == 300,
                "outbox retains the selected duration");
        System.out.println("Context action logic: " + checks + " checks passed.");
    }
}
