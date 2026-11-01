// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.client;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Bounded per-recipient whisper delivery, receipt, and emote-fallback state machine. */
public final class LocationNoticeDeliveryQueue {
    public enum Kind { WHISPER, EMOTE, ACK }

    public record Action(UUID recipient, LocationNotice notice, UUID noticeId, Kind kind) {}
    public record Result(UUID recipient, LocationNotice notice, boolean success, Kind kind) {}

    private static final int MAX_CAPACITY = 4_096;
    private static final int MAX_ACKS = 1_024;
    private static final int MAX_SUPPORTING_PEERS = 4_096;
    private static final long WHISPER_RECEIPT_TIMEOUT_MS = 60_000;
    private static final long EMOTE_RECEIPT_TIMEOUT_MS = 12_000;

    private record DeliveryKey(UUID recipient, UUID noticeId) {}
    private record AckKey(UUID sender, UUID noticeId) {}

    private static final class Delivery {
        final DeliveryKey key;
        final LocationNotice notice;
        Action activeAction;
        Action queuedAction;
        boolean awaitingReceipt;
        boolean whisperSent;
        long sentAtMs;
        Kind sentKind;
        String emoteBody;

        Delivery(DeliveryKey key, LocationNotice notice) {
            this.key = key;
            this.notice = notice;
        }
    }

    private final int capacity;
    private final LocationNoticeOutbox cadence = new LocationNoticeOutbox(1);
    private final Map<DeliveryKey, Delivery> deliveries = new HashMap<>();
    private final Map<AckKey, Action> acknowledgements = new HashMap<>();
    private final LinkedHashSet<UUID> supportingPeers = new LinkedHashSet<>();
    private final ArrayDeque<Action> regularQueue = new ArrayDeque<>();
    private final ArrayDeque<Action> acknowledgementQueue = new ArrayDeque<>();
    private final ArrayList<Result> results = new ArrayList<>();
    private int consecutiveAcks;

    public LocationNoticeDeliveryQueue() { this(MAX_CAPACITY); }

    public LocationNoticeDeliveryQueue(int capacity) {
        if (capacity < 1 || capacity > MAX_CAPACITY) throw new IllegalArgumentException("Invalid queue capacity");
        this.capacity = capacity;
    }

    public boolean enqueue(UUID recipient, LocationNotice notice) {
        if (recipient == null || notice == null) return false;
        DeliveryKey key = new DeliveryKey(recipient, notice.id());
        Delivery existing = deliveries.get(key);
        if (existing != null) return existing.notice.equals(notice);
        if (deliveries.size() >= capacity) return false;
        Delivery delivery = new Delivery(key, notice);
        deliveries.put(key, delivery);
        queue(delivery, Kind.WHISPER);
        return true;
    }

    public boolean queueAcknowledgement(UUID recipient, UUID noticeId) {
        if (recipient == null || noticeId == null) return false;
        AckKey key = new AckKey(recipient, noticeId);
        if (acknowledgements.containsKey(key)) return true;
        if (acknowledgements.size() >= MAX_ACKS) return false;
        Action action = new Action(recipient, null, noticeId, Kind.ACK);
        acknowledgements.put(key, action);
        acknowledgementQueue.addLast(action);
        return true;
    }

    public Action takeReady(long nowMs) {
        if (!cadence.isReady(nowMs)) return null;
        boolean hasAcks = discardStaleAcks();
        boolean hasRegular = discardStaleRegular();
        if (!hasAcks && !hasRegular) return null;
        if (hasAcks && (!hasRegular || consecutiveAcks < 3)) {
            Action action = acknowledgementQueue.pollFirst();
            consecutiveAcks++;
            return action;
        }
        Action action = regularQueue.pollFirst();
        Delivery delivery = deliveries.get(key(action));
        if (delivery != null && delivery.queuedAction == action) delivery.queuedAction = null;
        consecutiveAcks = 0;
        return action;
    }

    public void sent(Action action, long nowMs, String emoteBody) {
        if (action == null) return;
        if (action.kind() == Kind.ACK) {
            if (!removeAckAction(action)) return;
            cadence.sent(nowMs);
            return;
        }
        Delivery delivery = currentDelivery(action);
        if (delivery == null || delivery.queuedAction == action || delivery.awaitingReceipt) return;
        delivery.awaitingReceipt = true;
        delivery.sentAtMs = nowMs;
        delivery.sentKind = action.kind();
        delivery.emoteBody = action.kind() == Kind.EMOTE ? emoteBody : null;
        if (action.kind() == Kind.WHISPER) delivery.whisperSent = true;
        cadence.sent(nowMs);
    }

    public void rejected(Action action) {
        if (action == null) return;
        if (action.kind() == Kind.ACK) {
            removeAckAction(action);
            return;
        }
        Delivery delivery = currentDelivery(action);
        if (delivery == null || delivery.awaitingReceipt) return;
        if (action.kind() == Kind.WHISPER && supportingPeers.contains(delivery.key.recipient())) {
            finish(delivery, false, Kind.WHISPER);
        } else if (action.kind() == Kind.WHISPER) queue(delivery, Kind.EMOTE);
        else finish(delivery, false, Kind.EMOTE);
    }

    public void fail(Action action) {
        if (action == null) return;
        if (action.kind() == Kind.ACK) {
            removeAckAction(action);
            return;
        }
        Delivery delivery = currentDelivery(action);
        if (delivery != null) finish(delivery, false, action.kind());
    }

    /** Accepts a receipt only from the exact UUID that was sent this notice. */
    public boolean acknowledge(UUID sender, UUID noticeId) {
        if (sender == null || noticeId == null) return false;
        Delivery delivery = deliveries.get(new DeliveryKey(sender, noticeId));
        if (delivery == null || !delivery.whisperSent) return false;
        rememberSupportingPeer(sender);
        finish(delivery, true, Kind.WHISPER);
        cancelUnsentFallbacks(sender);
        return true;
    }

    /** Confirms only an exact own /me echo for a fallback currently awaiting delivery. */
    public boolean confirmEmote(String exactSignedBody) {
        if (exactSignedBody == null) return false;
        for (Delivery delivery : deliveries.values()) {
            if (delivery.awaitingReceipt && delivery.sentKind == Kind.EMOTE
                    && exactSignedBody.equals(delivery.emoteBody)) {
                finish(delivery, true, Kind.EMOTE);
                return true;
            }
        }
        return false;
    }

    public void update(long nowMs) {
        for (Delivery delivery : List.copyOf(deliveries.values())) {
            if (!delivery.awaitingReceipt) continue;
            long timeout = delivery.sentKind == Kind.WHISPER
                    ? WHISPER_RECEIPT_TIMEOUT_MS : EMOTE_RECEIPT_TIMEOUT_MS;
            if (nowMs - delivery.sentAtMs < timeout) continue;
            if (delivery.sentKind == Kind.WHISPER) {
                if (supportingPeers.contains(delivery.key.recipient())) finish(delivery, false, Kind.WHISPER);
                else queue(delivery, Kind.EMOTE);
            } else {
                finish(delivery, false, Kind.EMOTE);
            }
        }
    }

    public List<Result> drainResults() {
        List<Result> drained = List.copyOf(results);
        results.clear();
        return drained;
    }

    public void observeServerTime(long tick) { cadence.observeServerTime(tick); }

    /** Keep cached mod-capable peers only while they remain connected. */
    public void retainSupportingPeers(Set<UUID> online) {
        if (online == null) {
            supportingPeers.clear();
            return;
        }
        supportingPeers.removeIf(peer -> !online.contains(peer));
    }

    public void clear() {
        deliveries.clear();
        acknowledgements.clear();
        supportingPeers.clear();
        regularQueue.clear();
        acknowledgementQueue.clear();
        results.clear();
        consecutiveAcks = 0;
        cadence.clear();
    }

    private void queue(Delivery delivery, Kind kind) {
        delivery.awaitingReceipt = false;
        delivery.sentKind = null;
        delivery.emoteBody = null;
        Action action = new Action(delivery.key.recipient(), delivery.notice, delivery.key.noticeId(), kind);
        delivery.activeAction = action;
        delivery.queuedAction = action;
        regularQueue.addLast(action);
    }

    private boolean discardStaleAcks() {
        while (!acknowledgementQueue.isEmpty()) {
            Action action = acknowledgementQueue.peekFirst();
            if (actionIsCurrentAck(action)) return true;
            acknowledgementQueue.removeFirst();
        }
        return false;
    }

    private boolean discardStaleRegular() {
        while (!regularQueue.isEmpty()) {
            Action action = regularQueue.peekFirst();
            Delivery delivery = deliveries.get(key(action));
            if (delivery != null && delivery.queuedAction == action) return true;
            regularQueue.removeFirst();
        }
        return false;
    }

    private Delivery currentDelivery(Action action) {
        Delivery delivery = deliveries.get(key(action));
        return delivery != null && delivery.activeAction == action ? delivery : null;
    }

    private DeliveryKey key(Action action) {
        return new DeliveryKey(action.recipient(), action.noticeId());
    }

    private boolean actionIsCurrentAck(Action action) {
        return acknowledgements.get(new AckKey(action.recipient(), action.noticeId())) == action;
    }

    private boolean removeAckAction(Action action) {
        AckKey key = new AckKey(action.recipient(), action.noticeId());
        if (acknowledgements.get(key) != action) return false;
        acknowledgements.remove(key);
        return true;
    }

    private void finish(Delivery delivery, boolean success, Kind kind) {
        if (deliveries.remove(delivery.key, delivery)) {
            delivery.queuedAction = null;
            delivery.awaitingReceipt = false;
            results.add(new Result(delivery.key.recipient(), delivery.notice, success, kind));
        }
    }

    private void rememberSupportingPeer(UUID peer) {
        if (!supportingPeers.remove(peer) && supportingPeers.size() >= MAX_SUPPORTING_PEERS) {
            supportingPeers.remove(supportingPeers.iterator().next());
        }
        supportingPeers.add(peer);
    }

    private void cancelUnsentFallbacks(UUID peer) {
        for (Delivery delivery : List.copyOf(deliveries.values())) {
            Action active = delivery.activeAction;
            if (!delivery.key.recipient().equals(peer) || delivery.awaitingReceipt
                    || active == null || active.kind() != Kind.EMOTE) continue;
            finish(delivery, false, Kind.WHISPER);
        }
    }
}
