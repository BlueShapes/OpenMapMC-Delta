// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.client;

import net.aosankaku.openmapdelta.network.LocationContextRequest;
import net.aosankaku.openmapdelta.network.LocationContextResponse;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public final class LocationContextClientTest {
    private static int checks;

    private static void check(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        Object connection = new Object();
        Object replacedConnection = new Object();
        AtomicReference<LocationContextRequest> sent = new AtomicReference<>();
        AtomicInteger sends = new AtomicInteger();
        LocationContextClient.configureSender(request -> {
            sent.set(request);
            sends.incrementAndGet();
            return true;
        });

        UUID token = LocationContextClient.request(connection, "minecraft:overworld", 10, 64, -20);
        check(token != null && token.equals(sent.get().requestId()), "request token is tied to outgoing request");
        check(sent.get().x() == 10 && sent.get().y() == 64 && sent.get().z() == -20,
                "request keeps the exact coordinate");
        check(LocationContextClient.request(connection, "minecraft:overworld", 10, 64, -20).equals(token)
                        && sends.get() == 1,
                "identical pending request is not sent twice");

        LocationContextResponse valid = new LocationContextResponse(token, "minecraft:overworld", 10, 64, -20,
                true, true, "minecraft:plains", List.of("minecraft:village_plains"));
        LocationContextClient.apply(replacedConnection, connection, valid);
        check(LocationContextClient.response(token) == null, "packet from replaced connection is ignored");
        LocationContextClient.apply(connection, connection,
                new LocationContextResponse(UUID.randomUUID(), valid.dimension(), 10, 64, -20,
                        true, true, valid.biomeId(), valid.structureIds()));
        check(LocationContextClient.response(token) == null, "response with another request token is ignored");
        LocationContextClient.apply(connection, connection,
                new LocationContextResponse(token, valid.dimension(), 11, 64, -20,
                        true, true, valid.biomeId(), valid.structureIds()));
        check(LocationContextClient.response(token) == null, "response for another point is ignored");
        LocationContextClient.apply(connection, connection, valid);
        check(LocationContextClient.response(token).equals(valid), "matching context response is cached");
        check(LocationContextClient.response(UUID.randomUUID()) == null, "cache requires its request token");

        LocationContextClient.reset();
        check(LocationContextClient.response(token) == null, "reset clears accepted context");
        UUID afterReset = LocationContextClient.request(connection, "minecraft:overworld", 10, 64, -20);
        check(afterReset != null && sends.get() == 2, "reset retains the configured optional sender");
        LocationContextClient.reset();
        LocationContextClient.configureSender(request -> false);
        check(LocationContextClient.request(connection, "minecraft:overworld", 10, 64, -20) == null,
                "unavailable optional channel produces no request token");
        LocationContextClient.configureSender(null);
        check(LocationContextClient.request(connection, "minecraft:overworld", 10, 64, -20) == null,
                "missing channel sender is safe");
        check(LocationContextClient.request(connection, "bad dimension", 10, 64, -20) == null,
                "invalid dimension is rejected locally");
        LocationContextClient.reset();
        System.out.println("Location context client bridge: " + checks + " checks passed.");
    }
}
