// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.client;

import net.aosankaku.openmapdelta.network.LocationContextRequest;
import net.aosankaku.openmapdelta.network.LocationContextResponse;

import java.util.UUID;
import java.util.function.Function;

/** Connection-bound client bridge for optional biome and loaded-structure context. */
public final class LocationContextClient {
    private record Binding(UUID requestId, Object connection, String dimension, int x, int y, int z) {}

    private static volatile Function<LocationContextRequest, Boolean> sender = ignored -> false;
    private static Binding pending;
    private static LocationContextResponse accepted;

    private LocationContextClient() {}

    public static void configureSender(Function<LocationContextRequest, Boolean> configuredSender) {
        sender = configuredSender == null ? ignored -> false : configuredSender;
    }

    /** Send at most one request at a time and bind its answer to the originating connection and point. */
    public static synchronized UUID request(Object connection, String dimension, int x, int y, int z) {
        if (connection == null) return null;
        if (pending != null && pending.connection() == connection && pending.dimension().equals(dimension)
                && pending.x() == x && pending.y() == y && pending.z() == z) return pending.requestId();
        UUID requestId = UUID.randomUUID();
        LocationContextRequest request;
        try {
            request = new LocationContextRequest(requestId, dimension, x, y, z);
        } catch (IllegalArgumentException invalid) {
            return null;
        }
        pending = new Binding(requestId, connection, dimension, x, y, z);
        accepted = null;
        boolean sent;
        try {
            sent = Boolean.TRUE.equals(sender.apply(request));
        } catch (RuntimeException unavailable) {
            sent = false;
        }
        if (!sent && pending != null && pending.requestId().equals(requestId)) pending = null;
        return sent ? requestId : null;
    }

    /** Accept only the response for the exact live connection, request token, and coordinates. */
    public static synchronized void apply(Object packetConnection, Object currentConnection,
                                          LocationContextResponse response) {
        Binding binding = pending;
        if (binding == null || response == null || packetConnection == null
                || packetConnection != currentConnection || packetConnection != binding.connection()
                || !binding.requestId().equals(response.requestId())
                || !binding.dimension().equals(response.dimension()) || binding.x() != response.x()
                || binding.y() != response.y() || binding.z() != response.z()) return;
        accepted = response;
        pending = null;
    }

    public static synchronized LocationContextResponse response(UUID requestId) {
        return accepted != null && accepted.requestId().equals(requestId) ? accepted : null;
    }

    /** Drop connection state while retaining the loader's optional-channel sender. */
    public static synchronized void reset() {
        pending = null;
        accepted = null;
    }
}
