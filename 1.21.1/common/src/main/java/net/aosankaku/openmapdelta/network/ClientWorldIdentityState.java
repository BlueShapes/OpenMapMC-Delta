// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.network;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;

/** Keeps the server-provided world identity scoped to the connection that sent it. */
public final class ClientWorldIdentityState {
    private record State(Object connectionToken, UUID worldId) {}
    private static volatile State state;
    private static final Map<Object, Boolean> EXPECTED_CONNECTIONS = new IdentityHashMap<>();
    private static volatile Predicate<Object> supportProbe = ignored -> false;

    private ClientWorldIdentityState() {}

    public static void configureSupportProbe(Predicate<Object> probe) {
        supportProbe = probe == null ? ignored -> false : probe;
    }

    public static void markExpected(Object actualConnectionToken) {
        if (actualConnectionToken == null) return;
        synchronized (EXPECTED_CONNECTIONS) {
            EXPECTED_CONNECTIONS.put(actualConnectionToken, Boolean.TRUE);
        }
    }

    public static boolean requiresIdentity(Object actualConnectionToken) {
        if (actualConnectionToken == null) return false;
        synchronized (EXPECTED_CONNECTIONS) {
            if (EXPECTED_CONNECTIONS.containsKey(actualConnectionToken)) return true;
        }
        return supportProbe.test(actualConnectionToken);
    }

    public static void apply(Object actualConnectionToken, UUID worldId) {
        if (actualConnectionToken == null || worldId == null || isZero(worldId)) return;
        state = new State(actualConnectionToken, worldId);
    }

    /** Applies only when the queued packet still belongs to the active connection. */
    public static void apply(Object packetConnectionToken, Object currentConnectionToken, UUID worldId) {
        if (packetConnectionToken == null || packetConnectionToken != currentConnectionToken) return;
        apply(packetConnectionToken, worldId);
    }

    public static Optional<UUID> get(Object currentConnectionToken) {
        State current = state;
        return currentConnectionToken != null && current != null && current.connectionToken() == currentConnectionToken
                ? Optional.of(current.worldId()) : Optional.empty();
    }

    public static void reset() {
        state = null;
        synchronized (EXPECTED_CONNECTIONS) {
            EXPECTED_CONNECTIONS.clear();
        }
    }

    private static boolean isZero(UUID id) {
        return id.getMostSignificantBits() == 0L && id.getLeastSignificantBits() == 0L;
    }
}
