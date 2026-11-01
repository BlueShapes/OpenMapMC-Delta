// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.client;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class PlayerNoticesTest {
    private static int checks;

    private static void check(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        UUID noticeId = UUID.fromString("00f42b3b-d3ca-49b4-99f0-5acaaac9ff29");
        UUID tracked = UUID.fromString("08e0ebca-0adf-4d24-9f65-933a2a8b495d");
        UUID recipient = UUID.fromString("6da3bc55-e3bc-4e3d-8d6e-071f625bd1f7");
        String original = "[Odyssey Map:" + noticeId + "] minecraft:overworld X:-10 Y:64 Z:25";
        check(new LocationNotice(noticeId, "minecraft:overworld", -10, 64, 25).encode().equals(original),
                "old five-argument constructor and static wire unchanged");
        check(new LocationNotice(noticeId, "minecraft:overworld", -10, 64, 25, 15).encode().equals(original),
                "old six-argument constructor and explicit default duration unchanged");
        check(LocationNotice.decode(original).orElseThrow().trackedPlayerId() == null,
                "old payload has no tracking target");
        check(LocationNotice.decode(original + " D:60s").orElseThrow().durationSeconds() == 60,
                "old selected-duration payload remains compatible");
        check(new LocationNotice(noticeId, "minecraft:overworld", -10, 64, 25, 15, null, "").encode().equals(original),
                "empty message preserves the old wire form");
        String userMessage = "とりあえず集合 😀 /tp @a; %%";
        LocationNotice customized = new LocationNotice(noticeId, "minecraft:overworld", -10, 64, 25, 60,
                tracked, userMessage);
        check(LocationNotice.decode(customized.encode()).orElseThrow().equals(customized),
                "Unicode custom message and tracked notice round trip");
        check(customized.encode().endsWith(" P:" + tracked + " M:" + userMessage),
                "message is appended after the legacy tracking suffix");
        UUID nextNoticeId = UUID.randomUUID();
        LocationNotice freshSend = customized.withId(nextNoticeId);
        check(freshSend.id().equals(nextNoticeId) && freshSend.dimension().equals(customized.dimension())
                        && freshSend.x() == customized.x() && freshSend.y() == customized.y()
                        && freshSend.z() == customized.z() && freshSend.durationSeconds() == customized.durationSeconds()
                        && freshSend.trackedPlayerId().equals(customized.trackedPlayerId())
                        && freshSend.message().equals(customized.message()),
                "each send can use a fresh receipt ID while preserving the preview payload");
        check(!freshSend.encode().equals(customized.encode()), "a fresh notice ID prevents outbox deduplication");
        check(LocationNotice.decode(original + " M:").isEmpty(), "empty explicit message suffix is noncanonical");
        for (String invalidMessage : new String[]{null, "x\ny", "x\ry", "x\u0000y", "x\u0085y",
                "x\u2028y", "x\u2029y", "x\u00a7cy",
                "\ud800", "\udc00", "\ud800x", "x".repeat(221)}) {
            check(!LocationNotice.validMessage(invalidMessage), "unsafe or overlong custom message rejected");
        }
        check(LocationNotice.validMessage("日本語 and 中文 😀"), "plain multilingual message is allowed");
        for (int duration : new int[]{5, 15, 30, 600}) {
            LocationNotice notice = new LocationNotice(noticeId, "minecraft:overworld", -10, 64, 25, duration, tracked);
            LocationNotice decoded = LocationNotice.decode(notice.encode()).orElseThrow();
            check(decoded.equals(notice), "tracked notice round trip at duration " + duration);
            check(decoded.id().equals(noticeId) && decoded.trackedPlayerId().equals(tracked),
                    "notice identity remains distinct from tracking target " + duration);
            check(notice.encode().endsWith(" P:" + tracked), "bounded canonical suffix " + duration);
            check(notice.encode().length() <= LocationNotice.MAX_MESSAGE_LENGTH, "tracked payload is bounded " + duration);
        }
        String followed = original + " P:" + tracked;
        for (String invalid : new String[]{
                original + " P:", original + " P:0", original + " P:00000000-0000-0000-0000-1",
                original + " P:" + tracked.toString().toUpperCase(),
                followed + " P:" + recipient, followed + " D:30s", followed + " ", followed + "\n",
                followed + "\r", followed + "; tp @a", followed + " /op @a",
                original + " P:" + tracked + " D:5s",
                original + " P:" + tracked.toString().replace('-', ' '),
                original + " P:" + tracked.toString().replace("08e0", "zzzz"),
                original + " P:" + "@a".repeat(18)}) {
            check(LocationNotice.decode(invalid).isEmpty(), "malformed UUID, ordering, or injection rejected");
        }
        check(LocationNotice.decode(new LocationNotice(noticeId, "example:" + "x".repeat(170),
                0, 0, 0, 600, tracked).encode()).isEmpty(), "tracking never raises 220-character notice limit");
        var outbox = new LocationNoticeOutbox(1);
        var notice = new LocationNotice(noticeId, "minecraft:overworld", 1, 2, 3, 30, tracked);
        check(outbox.enqueue(List.of(recipient), notice), "tracking notice queues");
        check(outbox.isReady(0), "outbox cadence starts ready");
        var pending = outbox.takeReady(0);
        check(pending.recipient().equals(recipient), "tracking UUID never changes actual whisper recipient");
        check(pending.notice().trackedPlayerId().equals(tracked), "outbox retains tracking target");
        check(pending.notice().durationSeconds() == 30, "outbox retains duration");
        outbox.sent(0);
        check(!outbox.isReady(1_099), "outbox readiness respects elapsed-time cadence");
        check(outbox.enqueue(List.of(recipient), notice), "rate-limit fixture queues second notification");
        check(outbox.takeReady(1_100) == null, "tracking does not bypass server-tick rate limit");
        outbox.observeServerTime(40);
        check(outbox.takeReady(1_100).notice().equals(notice), "rate limit releases unchanged tracking payload");
        outbox.clear();
        check(outbox.takeReady(20_000) == null, "logout clear discards tracking payloads");

        check(LocationNoticeProtocol.ack(noticeId).equals("[Odyssey Map ACK:" + noticeId + "]"),
                "acknowledgement has one canonical wire form");
        check(LocationNoticeProtocol.decodeAck(LocationNoticeProtocol.ack(noticeId)).orElseThrow().equals(noticeId),
                "canonical acknowledgement decodes");
        for (String invalid : new String[]{null, "prefix " + LocationNoticeProtocol.ack(noticeId),
                LocationNoticeProtocol.ack(noticeId) + " suffix", LocationNoticeProtocol.ack(noticeId).toUpperCase(),
                "[Odyssey Map ACK:00000000-0000-0000-0000-00000000000g]"}) {
            check(LocationNoticeProtocol.decodeAck(invalid).isEmpty(), "spoofed or malformed acknowledgement rejected");
        }
        String emoteBody = LocationNoticeProtocol.emoteBody("Alice", notice).orElseThrow();
        check(emoteBody.equals("@Alice " + notice.encode()), "fallback emote body is addressed and bounded");
        check(LocationNoticeProtocol.decodeEmote(emoteBody).orElseThrow()
                .equals(new LocationNoticeProtocol.Emote("Alice", notice)), "exact emote body decodes");
        check(PlayerCommandPayload.emote("Alice", notice).orElseThrow().equals("me " + emoteBody),
                "emote uses validated vanilla command payload");
        for (String invalid : new String[]{"prefix " + emoteBody, emoteBody + " trailing",
                "@Alice " + notice.encode() + "\n", "@a;op " + notice.encode(), "@Alice  " + notice.encode()}) {
            check(LocationNoticeProtocol.decodeEmote(invalid).isEmpty(), "emote parser rejects substring and injection forms");
        }
        check(LocationNoticeProtocol.emoteBody("@a", notice).isEmpty(), "emote recipient name is validated");
        String customEmote = LocationNoticeProtocol.emoteBody("Alice", customized).orElseThrow();
        check(LocationNoticeProtocol.decodeEmote(customEmote).orElseThrow().notice().equals(customized),
                "fallback /me carries the selected message intact");
        check(PlayerCommandPayload.emote("Alice\nop", notice).isEmpty(), "emote command rejects injected target");
        var overlongNotice = new LocationNotice(noticeId, "example:" + "x".repeat(210), 1, 2, 3);
        check(LocationNoticeProtocol.emoteBody("Alice", overlongNotice).isEmpty(), "emote rejects overlong notice payload");
        check(!PlayerCommandPayload.validMessage("x".repeat(221)), "private-message length limit remains unchanged");

        deliveryReceipts(noticeId, notice);
        busyReceiverDeadline(notice);
        emoteFallbacks(noticeId, notice);
        supportingPeerBehavior();
        supportingPeerRetentionAndReset();
        supportingPeerCacheBound();
        deliveryFairness(noticeId, notice);
        deliveryBoundsAndReset(noticeId, notice);

        for (String command : new String[]{"tp", "teleport"}) {
            check(PlayerCommandPayload.teleport(command, "Player_123").orElseThrow().equals(command + " Player_123"),
                    "native player teleport uses a validated literal name");
        }
        for (String name : new String[]{"@a", "@p", "Alice Bob", "Alice\nop", "Alice\rop",
                "Alice;op", "Alice/op", "Alice\u2028Bob", "", "a".repeat(17), null}) {
            check(PlayerCommandPayload.teleport("tp", name).isEmpty(), "unsafe teleport target rejected");
            check(PlayerCommandPayload.privateMessage("w", name, "hello").isEmpty(), "unsafe message target rejected");
        }
        for (String command : new String[]{"/tp", "tp @a", "execute", "w", null}) {
            check(PlayerCommandPayload.teleport(command, "Alice").isEmpty(), "teleport command cannot be injected");
        }
        for (String command : new String[]{"w", "msg", "tell"}) {
            String body = "日本語 中文 हिन्दी /op @a; this is message text 😀";
            String payload = PlayerCommandPayload.privateMessage(command, "Alice", body).orElseThrow();
            check(payload.equals(command + " Alice " + body), "Unicode and command-looking text stay inside whisper body");
            String maximum = PlayerCommandPayload.privateMessage(command, "A".repeat(16), "x".repeat(220)).orElseThrow();
            check(maximum.length() <= 256, "maximum command stays within native packet limit");
            check(PlayerCommandPayload.privateMessage(command, "Alice", followed).isPresent(),
                    "wire notice remains valid as private-message body");
        }
        for (String body : new String[]{null, "", " ", "\u2003", "x".repeat(221), "a\nb", "a\rb", "a\r\nb",
                "a\tb", "a\u0000b", "a\u007fb", "a\u0085b", "a\u2028b", "a\u2029b",
                "a\u00a7cb", "\ud800", "\udc00", "\ud800x"}) {
            check(!PlayerCommandPayload.validMessage(body), "invalid, multiline, control, or overlong body rejected");
            check(PlayerCommandPayload.privateMessage("w", "Alice", body).isEmpty(), "invalid body cannot form a command");
        }
        for (String command : new String[]{"/w", "w @a", "tp", "execute", "w\nop", null}) {
            check(PlayerCommandPayload.privateMessage(command, "Alice", "hello").isEmpty(), "message root whitelist");
        }
        System.out.println("Player notice and command safety: " + checks + " checks passed.");
    }

    private static void deliveryReceipts(UUID noticeId, LocationNotice notice) {
        UUID alice = id(101);
        UUID bob = id(102);
        LocationNoticeDeliveryQueue queue = new LocationNoticeDeliveryQueue(2);
        check(queue.enqueue(alice, notice) && queue.enqueue(bob, notice), "notices queue independently per recipient");
        LocationNoticeDeliveryQueue.Action first = queue.takeReady(0);
        check(first.recipient().equals(alice) && first.kind() == LocationNoticeDeliveryQueue.Kind.WHISPER,
                "first recipient starts with a private whisper");
        check(!queue.acknowledge(alice, noticeId), "receipt before the whisper was sent is ignored");
        queue.update(60_000);
        check(queue.drainResults().isEmpty(), "unsent actions never start a receipt timeout");
        queue.sent(first, 0, null);
        queue.observeServerTime(40);
        LocationNoticeDeliveryQueue.Action second = queue.takeReady(1_100);
        check(second.recipient().equals(bob), "one recipient does not block another recipient's queued send");
        queue.sent(second, 1_100, null);
        check(!queue.acknowledge(id(103), noticeId), "unknown sender UUID cannot acknowledge another recipient's notice");
        check(queue.acknowledge(alice, noticeId), "actual recipient receipt succeeds");
        check(!queue.acknowledge(alice, noticeId), "duplicate receipt is stale after success");
        List<LocationNoticeDeliveryQueue.Result> results = queue.drainResults();
        check(results.size() == 1 && results.get(0).recipient().equals(alice)
                        && results.get(0).success() && results.get(0).kind() == LocationNoticeDeliveryQueue.Kind.WHISPER,
                "success result is specific to the acknowledged recipient");
        check(queue.acknowledge(bob, noticeId), "another recipient can still complete independently");
        check(queue.drainResults().get(0).recipient().equals(bob), "second result remains independent");
    }

    private static void busyReceiverDeadline(LocationNotice notice) {
        UUID recipient = id(150);
        LocationNoticeDeliveryQueue queue = new LocationNoticeDeliveryQueue();
        queue.enqueue(recipient, notice);
        LocationNoticeDeliveryQueue.Action whisper = queue.takeReady(0);
        queue.sent(whisper, 0, null);
        // Eight senders sharing the 2-second command cadence can occupy the first 14 seconds.
        queue.update(14_000);
        check(queue.drainResults().isEmpty(), "busy mod receiver is not declared unsupported after 14 seconds");
        check(queue.acknowledge(recipient, notice.id()), "receipt arriving at 14 seconds still confirms private delivery");
        check(queue.drainResults().get(0).success(), "delayed mod receipt reports success");
        queue.observeServerTime(40);
        check(queue.takeReady(1_100) == null, "delayed receipt prevented a public fallback");
    }

    private static void emoteFallbacks(UUID noticeId, LocationNotice notice) {
        UUID alice = id(201);
        UUID bob = id(202);
        LocationNoticeDeliveryQueue queue = new LocationNoticeDeliveryQueue(2);
        queue.enqueue(alice, notice);
        queue.enqueue(bob, notice);
        LocationNoticeDeliveryQueue.Action whisperAlice = queue.takeReady(0);
        queue.sent(whisperAlice, 0, null);
        queue.observeServerTime(40);
        LocationNoticeDeliveryQueue.Action whisperBob = queue.takeReady(1_100);
        queue.sent(whisperBob, 1_100, null);
        queue.update(60_000);
        check(queue.drainResults().isEmpty(), "private receipt timeout queues fallback instead of final failure");
        queue.update(61_100);
        queue.observeServerTime(80);
        LocationNoticeDeliveryQueue.Action emoteAlice = queue.takeReady(62_200);
        check(emoteAlice.recipient().equals(alice) && emoteAlice.kind() == LocationNoticeDeliveryQueue.Kind.EMOTE,
                "private receipt timeout independently schedules an emote fallback");
        String aliceBody = LocationNoticeProtocol.emoteBody("Alice", notice).orElseThrow();
        queue.sent(emoteAlice, 62_200, aliceBody);
        queue.observeServerTime(120);
        LocationNoticeDeliveryQueue.Action emoteBob = queue.takeReady(63_300);
        check(emoteBob.recipient().equals(bob) && emoteBob.kind() == LocationNoticeDeliveryQueue.Kind.EMOTE,
                "second recipient fallback continues after first recipient fallback");
        String bobBody = LocationNoticeProtocol.emoteBody("Bob", notice).orElseThrow();
        queue.sent(emoteBob, 63_300, bobBody);
        check(!queue.confirmEmote(bobBody + " forged"), "non-exact own emote echo is ignored");
        queue.update(74_200);
        List<LocationNoticeDeliveryQueue.Result> failed = queue.drainResults();
        check(failed.size() == 1 && failed.get(0).recipient().equals(alice)
                        && !failed.get(0).success() && failed.get(0).kind() == LocationNoticeDeliveryQueue.Kind.EMOTE,
                "only the fallback whose receipt expired is reported as failed");
        check(queue.confirmEmote(bobBody), "exact fallback echo confirms the remaining recipient");
        check(queue.drainResults().get(0).recipient().equals(bob), "confirmed fallback reports its own recipient");

        LocationNoticeDeliveryQueue late = new LocationNoticeDeliveryQueue();
        late.enqueue(alice, notice);
        LocationNoticeDeliveryQueue.Action whisper = late.takeReady(0);
        late.sent(whisper, 0, null);
        late.update(60_000);
        check(late.acknowledge(alice, noticeId), "late private receipt can cancel a queued fallback");
        late.observeServerTime(40);
        check(late.takeReady(1_100) == null, "late receipt removes stale fallback action");

        LocationNoticeDeliveryQueue immediateFallback = new LocationNoticeDeliveryQueue();
        immediateFallback.enqueue(alice, notice);
        LocationNoticeDeliveryQueue.Action rejectedWhisper = immediateFallback.takeReady(0);
        immediateFallback.rejected(rejectedWhisper);
        LocationNoticeDeliveryQueue.Action fallback = immediateFallback.takeReady(0);
        check(fallback.kind() == LocationNoticeDeliveryQueue.Kind.EMOTE,
                "rejected whisper queues immediate fallback without consuming cadence");
        String exactBody = LocationNoticeProtocol.emoteBody("Alice", notice).orElseThrow();
        immediateFallback.sent(fallback, 0, exactBody);
        check(!immediateFallback.confirmEmote("@Alice " + notice.encode() + " extra"),
                "near-matching emote cannot confirm delivery");
        check(immediateFallback.confirmEmote(exactBody), "exact own emote echo confirms delivery");
        check(immediateFallback.drainResults().get(0).success(), "confirmed fallback records success");
    }

    private static void supportingPeerBehavior() {
        UUID alice = id(601);
        UUID bob = id(602);
        LocationNotice knownNotice = notice(603);
        LocationNotice aliceNotice = notice(604);
        LocationNotice bobNotice = notice(605);
        LocationNoticeDeliveryQueue queue = new LocationNoticeDeliveryQueue(3);
        queue.enqueue(alice, knownNotice);
        queue.enqueue(alice, aliceNotice);
        queue.enqueue(bob, bobNotice);
        LocationNoticeDeliveryQueue.Action known = queue.takeReady(0);
        queue.sent(known, 0, null);
        queue.observeServerTime(40);
        LocationNoticeDeliveryQueue.Action alicePending = queue.takeReady(1_100);
        queue.sent(alicePending, 1_100, null);
        queue.observeServerTime(80);
        LocationNoticeDeliveryQueue.Action bobPending = queue.takeReady(2_200);
        queue.sent(bobPending, 2_200, null);
        check(!queue.acknowledge(id(699), knownNotice.id()), "spoof sender cannot establish supporting-peer status");
        check(queue.acknowledge(alice, knownNotice.id()), "verified receipt establishes supporting-peer status");
        queue.drainResults();
        queue.update(61_099);
        check(queue.drainResults().isEmpty(), "whisper fallback waits for the full 60-second deadline");
        queue.update(61_100);
        List<LocationNoticeDeliveryQueue.Result> aliceResult = queue.drainResults();
        check(aliceResult.size() == 1 && aliceResult.get(0).recipient().equals(alice)
                        && !aliceResult.get(0).success() && aliceResult.get(0).kind() == LocationNoticeDeliveryQueue.Kind.WHISPER,
                "support learned from one notice makes an outstanding same-peer notice fail privately");
        queue.update(62_200);
        queue.observeServerTime(120);
        LocationNoticeDeliveryQueue.Action bobFallback = queue.takeReady(62_200);
        check(bobFallback.recipient().equals(bob) && bobFallback.kind() == LocationNoticeDeliveryQueue.Kind.EMOTE,
                "unknown recipient still receives public fallback while supported peer does not");
        String bobBody = LocationNoticeProtocol.emoteBody("Bob", bobNotice).orElseThrow();
        queue.sent(bobFallback, 62_200, bobBody);
        queue.update(74_200);
        List<LocationNoticeDeliveryQueue.Result> bobResult = queue.drainResults();
        check(bobResult.size() == 1 && bobResult.get(0).recipient().equals(bob)
                        && !bobResult.get(0).success() && bobResult.get(0).kind() == LocationNoticeDeliveryQueue.Kind.EMOTE,
                "unknown peer fallback retains its own 12-second echo timeout");

        LocationNoticeDeliveryQueue pendingFallback = new LocationNoticeDeliveryQueue(2);
        LocationNotice first = notice(610);
        LocationNotice second = notice(611);
        pendingFallback.enqueue(alice, first);
        pendingFallback.enqueue(alice, second);
        LocationNoticeDeliveryQueue.Action firstAction = pendingFallback.takeReady(0);
        pendingFallback.sent(firstAction, 0, null);
        pendingFallback.observeServerTime(40);
        LocationNoticeDeliveryQueue.Action secondAction = pendingFallback.takeReady(1_100);
        pendingFallback.sent(secondAction, 1_100, null);
        pendingFallback.update(60_000);
        check(pendingFallback.acknowledge(alice, second.id()),
                "verified receipt for notice B can arrive while notice A fallback is queued");
        List<LocationNoticeDeliveryQueue.Result> cancelled = pendingFallback.drainResults();
        check(cancelled.size() == 2 && cancelled.stream().anyMatch(result -> result.success() && result.notice().id().equals(second.id()))
                        && cancelled.stream().anyMatch(result -> !result.success() && result.kind() == LocationNoticeDeliveryQueue.Kind.WHISPER
                        && result.notice().id().equals(first.id())),
                "notice B receipt cancels notice A's unsent fallback and fails it privately");
        pendingFallback.observeServerTime(80);
        check(pendingFallback.takeReady(2_200) == null, "cancelled notice A fallback is removed from the action queue");

        LocationNoticeDeliveryQueue rejectedFallback = new LocationNoticeDeliveryQueue(2);
        rejectedFallback.enqueue(alice, first);
        rejectedFallback.enqueue(alice, second);
        LocationNoticeDeliveryQueue.Action rejected = rejectedFallback.takeReady(0);
        rejectedFallback.rejected(rejected);
        LocationNoticeDeliveryQueue.Action privateAction = rejectedFallback.takeReady(0);
        check(privateAction.kind() == LocationNoticeDeliveryQueue.Kind.WHISPER,
                "remaining private notice can establish the peer's capability");
        rejectedFallback.sent(privateAction, 0, null);
        check(rejectedFallback.acknowledge(alice, second.id()), "another notice can establish support after immediate fallback");
        List<LocationNoticeDeliveryQueue.Result> transition = rejectedFallback.drainResults();
        check(transition.size() == 2 && transition.stream().anyMatch(result -> result.notice().id().equals(first.id())
                        && !result.success() && result.kind() == LocationNoticeDeliveryQueue.Kind.WHISPER)
                        && transition.stream().anyMatch(result -> result.notice().id().equals(second.id()) && result.success()),
                "verified receipt cancels any unsent fallback for the newly known peer");
        rejectedFallback.observeServerTime(40);
        check(rejectedFallback.takeReady(1_100) == null, "capability transition removes leased public fallback action");

        LocationNoticeDeliveryQueue knownRejection = new LocationNoticeDeliveryQueue();
        knownRejection.enqueue(alice, first);
        LocationNoticeDeliveryQueue.Action initial = knownRejection.takeReady(0);
        knownRejection.sent(initial, 0, null);
        check(knownRejection.acknowledge(alice, first.id()), "fixture establishes that peer supports private notices");
        knownRejection.drainResults();
        LocationNotice later = notice(612);
        knownRejection.enqueue(alice, later);
        knownRejection.observeServerTime(40);
        LocationNoticeDeliveryQueue.Action privateRejected = knownRejection.takeReady(1_100);
        knownRejection.rejected(privateRejected);
        List<LocationNoticeDeliveryQueue.Result> privateFailure = knownRejection.drainResults();
        check(privateFailure.size() == 1 && !privateFailure.get(0).success()
                        && privateFailure.get(0).kind() == LocationNoticeDeliveryQueue.Kind.WHISPER,
                "known peer's rejected private command reports an error without public fallback");
    }

    private static void supportingPeerRetentionAndReset() {
        UUID alice = id(701);
        LocationNoticeDeliveryQueue retained = new LocationNoticeDeliveryQueue();
        LocationNotice supportNotice = notice(702);
        retained.enqueue(alice, supportNotice);
        LocationNoticeDeliveryQueue.Action support = retained.takeReady(0);
        retained.sent(support, 0, null);
        check(retained.acknowledge(alice, supportNotice.id()), "verified recipient enters support cache");
        retained.drainResults();
        retained.retainSupportingPeers(Set.of(alice));
        LocationNotice stillOnlineNotice = notice(703);
        retained.enqueue(alice, stillOnlineNotice);
        retained.observeServerTime(40);
        LocationNoticeDeliveryQueue.Action stillOnline = retained.takeReady(1_100);
        retained.sent(stillOnline, 1_100, null);
        retained.update(61_100);
        List<LocationNoticeDeliveryQueue.Result> retainedResult = retained.drainResults();
        check(retainedResult.size() == 1 && !retainedResult.get(0).success()
                        && retainedResult.get(0).kind() == LocationNoticeDeliveryQueue.Kind.WHISPER,
                "online supporting peer remains cached");

        retained.retainSupportingPeers(Set.of(id(799)));
        LocationNotice offlineNotice = notice(704);
        retained.enqueue(alice, offlineNotice);
        retained.observeServerTime(80);
        LocationNoticeDeliveryQueue.Action nowOffline = retained.takeReady(2_200);
        retained.sent(nowOffline, 2_200, null);
        retained.update(62_200);
        retained.observeServerTime(120);
        check(retained.takeReady(62_200).kind() == LocationNoticeDeliveryQueue.Kind.EMOTE,
                "offline supporting peer is removed and falls back after the deadline");

        LocationNoticeDeliveryQueue reset = new LocationNoticeDeliveryQueue();
        LocationNotice beforeReset = notice(705);
        reset.enqueue(alice, beforeReset);
        LocationNoticeDeliveryQueue.Action beforeResetAction = reset.takeReady(0);
        reset.sent(beforeResetAction, 0, null);
        check(reset.acknowledge(alice, beforeReset.id()), "reset fixture caches a verified peer");
        reset.clear();
        LocationNotice afterReset = notice(706);
        reset.enqueue(alice, afterReset);
        LocationNoticeDeliveryQueue.Action afterResetAction = reset.takeReady(0);
        reset.sent(afterResetAction, 0, null);
        reset.update(60_000);
        reset.observeServerTime(40);
        check(reset.takeReady(61_100).kind() == LocationNoticeDeliveryQueue.Kind.EMOTE,
                "session reset clears the positive support cache");
    }

    private static void supportingPeerCacheBound() {
        LocationNoticeDeliveryQueue queue = new LocationNoticeDeliveryQueue();
        UUID oldestPeer = new UUID(1, 1);
        for (int i = 1; i <= 4_097; i++) {
            UUID recipient = new UUID(1, i);
            LocationNotice notice = new LocationNotice(new UUID(2, i), "minecraft:overworld", i, 64, 10);
            check(queue.enqueue(recipient, notice), "support-cache bound fixture queues each recipient");
            if (i > 1) queue.observeServerTime((i - 1L) * 40);
            long nowMs = (i - 1L) * 1_100;
            LocationNoticeDeliveryQueue.Action action = queue.takeReady(nowMs);
            check(action != null && action.kind() == LocationNoticeDeliveryQueue.Kind.WHISPER,
                    "support-cache bound fixture sends a private notice");
            queue.sent(action, nowMs, null);
            check(queue.acknowledge(recipient, notice.id()), "verified receipt enters bounded support cache");
            queue.drainResults();
        }
        LocationNotice later = new LocationNotice(new UUID(3, 1), "minecraft:overworld", 1, 64, 10);
        check(queue.enqueue(oldestPeer, later), "evicted peer can receive a later notice");
        queue.observeServerTime(4_097L * 40);
        long nowMs = 4_097L * 1_100;
        LocationNoticeDeliveryQueue.Action action = queue.takeReady(nowMs);
        queue.sent(action, nowMs, null);
        queue.update(nowMs + 60_000);
        queue.observeServerTime(4_098L * 40);
        LocationNoticeDeliveryQueue.Action fallback = queue.takeReady(nowMs + 61_100);
        check(fallback != null && fallback.kind() == LocationNoticeDeliveryQueue.Kind.EMOTE,
                "support cache evicts its oldest UUID at the 4096-peer bound");
    }

    private static void deliveryFairness(UUID noticeId, LocationNotice notice) {
        LocationNoticeDeliveryQueue queue = new LocationNoticeDeliveryQueue();
        for (int i = 0; i < 5; i++) queue.queueAcknowledgement(id(300 + i), noticeId);
        check(queue.queueAcknowledgement(id(300), noticeId), "duplicate queued receipt is deduplicated idempotently");
        UUID regularRecipient = id(400);
        queue.enqueue(regularRecipient, notice);
        for (int i = 0; i < 3; i++) {
            if (i > 0) queue.observeServerTime(i * 40L);
            LocationNoticeDeliveryQueue.Action ack = queue.takeReady(i * 1_100L);
            check(ack.kind() == LocationNoticeDeliveryQueue.Kind.ACK, "receipts have priority before fairness threshold");
            queue.sent(ack, i * 1_100L, null);
            if (i == 0) check(queue.takeReady(1_100) == null,
                    "all command kinds share the server-tick cadence");
        }
        queue.observeServerTime(120);
        LocationNoticeDeliveryQueue.Action regular = queue.takeReady(3_300);
        check(regular.kind() == LocationNoticeDeliveryQueue.Kind.WHISPER
                        && regular.recipient().equals(regularRecipient),
                "regular notice gets a turn after three prioritized receipts");
        queue.sent(regular, 3_300, null);
        queue.observeServerTime(160);
        check(queue.takeReady(4_400).kind() == LocationNoticeDeliveryQueue.Kind.ACK,
                "receipt priority resumes after regular work receives a turn");
    }

    private static void deliveryBoundsAndReset(UUID noticeId, LocationNotice notice) {
        LocationNoticeDeliveryQueue queue = new LocationNoticeDeliveryQueue(1);
        UUID alice = id(501);
        check(queue.enqueue(alice, notice), "capacity accepts first recipient");
        LocationNoticeDeliveryQueue.Action action = queue.takeReady(0);
        queue.sent(action, 0, null);
        check(!queue.enqueue(id(502), notice), "inflight recipient still consumes capacity");
        check(queue.acknowledge(alice, noticeId), "success releases recipient capacity");
        queue.drainResults();
        check(queue.enqueue(id(502), notice), "completed recipient frees capacity");
        queue.observeServerTime(40);
        LocationNoticeDeliveryQueue.Action oldAction = queue.takeReady(1_100);
        queue.sent(oldAction, 1_100, null);
        check(queue.acknowledge(id(502), noticeId), "second delivery completes before session reset");
        queue.drainResults();
        check(queue.enqueue(id(502), notice), "same recipient and notice key can be queued again after completion");
        queue.observeServerTime(80);
        LocationNoticeDeliveryQueue.Action newAction = queue.takeReady(2_200);
        queue.fail(oldAction);
        check(queue.drainResults().isEmpty(), "stale action cannot fail a new delivery with the same key");
        queue.sent(newAction, 2_200, null);
        check(queue.acknowledge(id(502), noticeId), "new delivery remains valid after stale action");
        queue.drainResults();
        queue.clear();
        check(queue.drainResults().isEmpty(), "session reset discards old results");
        check(!queue.acknowledge(id(502), noticeId), "session reset discards inflight notices");
        queue.fail(oldAction);
        check(queue.drainResults().isEmpty(), "stale action cannot mutate state after session reset");
        check(queue.enqueue(alice, notice), "session reset frees all recipient capacity");
        check(queue.takeReady(0) != null, "session reset clears command cadence");

        LocationNoticeDeliveryQueue acknowledgements = new LocationNoticeDeliveryQueue();
        for (int i = 0; i < 1_024; i++) {
            check(acknowledgements.queueAcknowledgement(id(1_000 + i), noticeId), "receipt queue stays within its cap");
        }
        check(!acknowledgements.queueAcknowledgement(id(3_000), noticeId), "receipt queue enforces its 1024 cap");
        check(acknowledgements.queueAcknowledgement(id(1_000), noticeId), "deduplicated receipt does not consume another slot");
    }

    private static UUID id(long leastSignificantBits) {
        return new UUID(0, leastSignificantBits);
    }

    private static LocationNotice notice(long id) {
        return new LocationNotice(new UUID(0, id), "minecraft:overworld", (int) id, 64, 10);
    }
}
