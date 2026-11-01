// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.marker;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;
import java.util.function.Consumer;

/** A bounded, render-thread-friendly cache policy for asynchronously captured entity icons. */
public final class EntityIconCache<K, V> {
    private final int capacity;
    private final int maxPending;
    private final long captureIntervalNanos;
    private final long retryNanos;
    private final Consumer<V> disposer;
    private final LinkedHashMap<K, V> ready = new LinkedHashMap<>(16, 0.75F, true);
    private final LinkedHashMap<K, Ticket<K>> readyOwners = new LinkedHashMap<>();
    private final LinkedHashMap<K, Ticket<K>> pending = new LinkedHashMap<>();
    private final LinkedHashMap<K, Long> retryAfter = new LinkedHashMap<>();
    private final WeakHashMap<Ticket<K>, Boolean> retiredTickets = new WeakHashMap<>();
    private final WeakHashMap<Ticket<K>, IdentityHashMap<V, Boolean>> disposedResources = new WeakHashMap<>();
    private long epoch;
    private long nextTicketId;
    private long lastCaptureNanos;
    private boolean hasCaptured;

    public EntityIconCache(int capacity, int maxPending, long captureIntervalNanos,
                           long retryNanos, Consumer<V> disposer) {
        if (capacity <= 0) throw new IllegalArgumentException("capacity must be positive");
        if (maxPending <= 0) throw new IllegalArgumentException("maxPending must be positive");
        if (captureIntervalNanos < 0) throw new IllegalArgumentException("captureIntervalNanos must not be negative");
        if (retryNanos < 0) throw new IllegalArgumentException("retryNanos must not be negative");
        this.capacity = capacity;
        this.maxPending = maxPending;
        this.captureIntervalNanos = captureIntervalNanos;
        this.retryNanos = retryNanos;
        this.disposer = Objects.requireNonNull(disposer, "disposer");
    }

    public synchronized V get(K key) {
        return ready.get(Objects.requireNonNull(key, "key"));
    }

    /** Reserves one capture. A null result means the caller should keep drawing its fallback. */
    public synchronized Ticket<K> reserve(K key, long nowNanos) {
        Objects.requireNonNull(key, "key");
        if (ready.containsKey(key) || pending.containsKey(key) || pending.size() >= maxPending) return null;
        return reserveAfterChecks(key, nowNanos);
    }

    /** Reserves a refresh while leaving the currently ready value available to callers. */
    public synchronized Ticket<K> reserveRefresh(K key, long nowNanos) {
        Objects.requireNonNull(key, "key");
        if (!ready.containsKey(key) || pending.containsKey(key) || pending.size() >= maxPending) return null;
        return reserveAfterChecks(key, nowNanos);
    }

    private Ticket<K> reserveAfterChecks(K key, long nowNanos) {
        if (isCoolingDown(key, nowNanos)) return null;
        if (hasCaptured && nowNanos - lastCaptureNanos < captureIntervalNanos) return null;

        Ticket<K> ticket = new Ticket<>(key, epoch, ++nextTicketId);
        pending.put(key, ticket);
        lastCaptureNanos = nowNanos;
        hasCaptured = true;
        return ticket;
    }

    /** Accepts a completed image, disposing late or duplicate results owned by this cache. */
    public synchronized boolean complete(Ticket<K> ticket, V value) {
        Objects.requireNonNull(ticket, "ticket");
        if (value == null) {
            retirePending(ticket);
            return false;
        }
        if (retiredTickets.containsKey(ticket)) {
            disposeUnlessCached(ticket, value);
            return false;
        }
        Ticket<K> active = pending.get(ticket.key());
        if (ticket.epoch() != epoch || active != ticket) {
            retireTickets(ticket);
            disposeUnlessCached(ticket, value);
            return false;
        }

        pending.remove(ticket.key());
        retireTickets(ticket);
        retryAfter.remove(ticket.key());
        V replaced = ready.get(ticket.key());
        Ticket<K> replacedOwner = readyOwners.get(ticket.key());
        ready.put(ticket.key(), value);
        readyOwners.put(ticket.key(), ticket);
        if (replaced != null && replaced != value) disposeOnce(replacedOwner, replaced);
        trimReady();
        return true;
    }

    /** Retires a failed capture and suppresses repeated attempts until the cooldown expires. */
    public synchronized boolean fail(Ticket<K> ticket, long nowNanos) {
        Objects.requireNonNull(ticket, "ticket");
        if (retiredTickets.containsKey(ticket)) return false;
        Ticket<K> active = pending.get(ticket.key());
        if (ticket.epoch() != epoch || active != ticket) {
            retireTickets(ticket);
            return false;
        }

        pending.remove(ticket.key());
        retireTickets(ticket);
        retryAfter.put(ticket.key(), nowNanos + retryNanos);
        trimRetryAfter();
        return true;
    }

    /** Clears resources and invalidates reservations from the previous world or resource generation. */
    public synchronized void clear() {
        epoch++;
        for (Ticket<K> ticket : pending.values()) retireTickets(ticket);
        pending.clear();
        retryAfter.clear();
        hasCaptured = false;

        List<Map.Entry<K, V>> values = new ArrayList<>(ready.entrySet());
        ready.clear();
        for (Map.Entry<K, V> entry : values) {
            Ticket<K> owner = readyOwners.remove(entry.getKey());
            disposeOnce(owner, entry.getValue());
        }
        readyOwners.clear();
    }

    public synchronized int size() { return ready.size(); }

    public synchronized int pendingCount() { return pending.size(); }

    /** Reports whether a key is still within its retry cooldown, expiring elapsed entries. */
    public synchronized boolean isCoolingDown(K key, long nowNanos) {
        Objects.requireNonNull(key, "key");
        Long until = retryAfter.get(key);
        if (until == null) return false;
        if (nowNanos - until < 0) return true;
        retryAfter.remove(key);
        return false;
    }

    private void trimReady() {
        while (ready.size() > capacity) {
            Map.Entry<K, V> eldest = ready.entrySet().iterator().next();
            ready.remove(eldest.getKey());
            Ticket<K> owner = readyOwners.remove(eldest.getKey());
            disposeOnce(owner, eldest.getValue());
        }
    }

    private void trimRetryAfter() {
        int limit = Math.max(capacity, maxPending);
        while (retryAfter.size() > limit) {
            retryAfter.remove(retryAfter.keySet().iterator().next());
        }
    }

    private void retirePending(Ticket<K> ticket) {
        if (retiredTickets.containsKey(ticket)) return;
        if (pending.get(ticket.key()) == ticket) pending.remove(ticket.key());
        retireTickets(ticket);
    }

    private void retireTickets(Ticket<K> ticket) { retiredTickets.put(ticket, Boolean.TRUE); }

    private void disposeUnlessCached(Ticket<K> ticket, V value) {
        for (V cached : ready.values()) {
            if (cached == value) return;
        }
        disposeOnce(ticket, value);
    }

    private void disposeOnce(Ticket<K> ticket, V value) {
        if (ticket == null) {
            dispose(value);
            return;
        }
        IdentityHashMap<V, Boolean> disposed = disposedResources.computeIfAbsent(ticket, ignored -> new IdentityHashMap<>());
        if (disposed.put(value, Boolean.TRUE) == null) dispose(value);
    }

    private void dispose(V value) {
        try {
            disposer.accept(value);
        } catch (RuntimeException ignored) {
            // A broken texture/resource disposer must not prevent the rest of the cache from clearing.
        }
    }

    /** Immutable token tying a capture result to one cache reservation and resource epoch. */
    public static final class Ticket<K> {
        private final K key;
        private final long epoch;
        private final long id;

        private Ticket(K key, long epoch, long id) {
            this.key = Objects.requireNonNull(key, "key");
            this.epoch = epoch;
            this.id = id;
        }

        public K key() { return key; }
        public long epoch() { return epoch; }
        public long id() { return id; }
    }
}
