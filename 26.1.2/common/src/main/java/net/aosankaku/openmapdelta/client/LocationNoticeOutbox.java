// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.client;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.UUID;

/** Bounded, atomic batches with a send cadence below vanilla's chat spam threshold. */
public final class LocationNoticeOutbox {
    public static final int MAX_PENDING = 4_096;
    public static final long SEND_INTERVAL_MS = 1_100;
    public static final long SEND_INTERVAL_TICKS = 40;
    public record Pending(UUID recipient, LocationNotice notice) {}

    private final ArrayDeque<Pending> pending = new ArrayDeque<>();
    private final int capacity;
    private boolean sentAny;
    private long lastSendMs;
    private boolean hasServerTime;
    private long serverTick;
    private long lastServerSendTick;

    public LocationNoticeOutbox() { this(MAX_PENDING); }

    public LocationNoticeOutbox(int capacity) {
        if (capacity < 1 || capacity > MAX_PENDING) throw new IllegalArgumentException("Invalid queue capacity");
        this.capacity = capacity;
    }

    public boolean enqueue(Collection<UUID> recipients, LocationNotice notice) {
        var unique = new LinkedHashSet<>(recipients);
        if (notice == null || unique.contains(null) || unique.size() > capacity - pending.size()) return false;
        unique.forEach(recipient -> pending.addLast(new Pending(recipient, notice)));
        return true;
    }

    public Pending takeReady(long nowMs) {
        if (!isReady(nowMs)) return null;
        return pending.pollFirst();
    }

    /** Whether another command may be sent under the shared chat and server-tick cadence. */
    public boolean isReady(long nowMs) {
        return !sentAny || (nowMs - lastSendMs >= SEND_INTERVAL_MS && hasServerTime
                && serverTick - lastServerSendTick >= SEND_INTERVAL_TICKS);
    }

    public void observeServerTime(long tick) {
        // Dimensions can have different time origins. Wait for fresh ticks after a reset.
        if (hasServerTime && tick < serverTick && sentAny) lastServerSendTick = tick;
        serverTick = tick;
        hasServerTime = true;
    }

    public void sent(long nowMs) {
        sentAny = true;
        lastSendMs = nowMs;
        lastServerSendTick = serverTick;
    }

    public void clear() {
        pending.clear();
        sentAny = false;
        hasServerTime = false;
    }
}
