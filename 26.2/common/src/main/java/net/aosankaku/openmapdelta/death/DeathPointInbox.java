// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.death;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/** Bounded client inbox; stale packets from a previous connection cannot change deathpoints. */
public final class DeathPointInbox {
    private static final int MAX_PENDING = 256;
    private static final ArrayDeque<DeathPointUpdate> PENDING = new ArrayDeque<>();
    private static Predicate<Object> supportProbe = ignored -> false;
    private static Object acceptedConnection;
    private DeathPointInbox() {}

    public static synchronized void configureSupportProbe(Predicate<Object> probe) {
        supportProbe = probe == null ? ignored -> false : probe;
    }

    public static synchronized boolean supported(Object currentConnection) {
        return currentConnection != null && (currentConnection == acceptedConnection || supportProbe.test(currentConnection));
    }

    /** An owner-only report channel announces support before the first death, including on Fabric. */
    public static synchronized void markSupported(Object packetConnection, Object currentConnection) {
        if (packetConnection != null && packetConnection == currentConnection) acceptedConnection = currentConnection;
    }

    public static synchronized void apply(Object packetConnection, Object currentConnection, DeathPointUpdate update) {
        if (packetConnection == null || packetConnection != currentConnection || update == null) return;
        acceptedConnection = currentConnection;
        PENDING.removeIf(previous -> previous.deathId().equals(update.deathId()));
        if (PENDING.size() == MAX_PENDING) PENDING.removeFirst();
        PENDING.addLast(update);
    }

    public static synchronized List<DeathPointUpdate> drain() {
        var result = new ArrayList<>(PENDING);
        PENDING.clear();
        return List.copyOf(result);
    }

    public static synchronized void reset() { PENDING.clear(); acceptedConnection = null; }
}
