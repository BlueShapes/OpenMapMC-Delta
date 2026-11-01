// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.shared;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

public final class SharedWaypointClientTest {
    private static int checks;

    public static void main(String[] args) {
        UUID owner = UUID.randomUUID();
        UUID world = UUID.randomUUID();
        Minecraft.getInstance().player = new Minecraft.FakePlayer(owner);
        AtomicInteger sent = new AtomicInteger();
        List<SharedWaypointRequest> requests = new ArrayList<>();
        SharedWaypointClient.configureSender(request -> { requests.add(request); sent.incrementAndGet(); });

        Object firstConnection = new Object();
        SharedWaypoint waypointA = waypoint(UUID.randomUUID(), owner, "A");
        SharedWaypoint waypointB = waypoint(UUID.randomUUID(), owner, "B");
        SharedWaypointClient.apply(firstConnection, new Object(), snapshot(world, 1, true, false, 0, 1, List.of(waypointA)));
        check(!SharedWaypointClient.canCreate() && SharedWaypointClient.points().isEmpty(), "mismatched connection is ignored");

        SharedWaypointClient.apply(firstConnection, firstConnection, snapshot(world, 1, true, false, 0, 2, List.of(waypointA)));
        check(!SharedWaypointClient.canCreate() && SharedWaypointClient.points().isEmpty()
                        && SharedWaypointClient.worldId() == null,
                "partial initial snapshot grants no capability or preference-pruning world id");
        SharedWaypointClient.apply(firstConnection, firstConnection, snapshot(world, 1, true, false, 1, 2, List.of(waypointB)));
        check(SharedWaypointClient.canCreate() && SharedWaypointClient.points().size() == 2, "complete snapshot applies atomically");
        check(SharedWaypointClient.canManage(owner) && !SharedWaypointClient.canManage(UUID.randomUUID()), "owner identity gates client controls");
        SharedWaypointClient.feedback(firstConnection, firstConnection,
                SharedWaypointFeedback.success("odysseymap.shared.created"));
        Minecraft.FakePlayer feedbackPlayer = Minecraft.getInstance().player;
        Component feedbackMessage = feedbackPlayer.lastMessage() != null
                ? feedbackPlayer.lastMessage() : Minecraft.getInstance().gui.getChat().lastMessage();
        check(feedbackMessage != null && feedbackMessage.toString().equals("odysseymap.shared.created"),
                "feedback is displayed through the active-version chat API");

        check(SharedWaypointClient.send(SharedWaypointRequest.create("minecraft:overworld", 0, 64, 0, "new", 0xFFFFFFFF, "", "")),
                "create request is queued during multiplayer");
        check(sent.get() == 1 && requests.get(0).operation() == SharedWaypointRequest.Operation.CREATE, "configured transport receives request");

        // Keep the last complete collection while a newer revision is being paged.
        SharedWaypointClient.apply(firstConnection, firstConnection, snapshot(world, 3, true, false, 0, 2, List.of(waypointA)));
        check(SharedWaypointClient.points().size() == 2, "partial update does not replace last complete state");
        SharedWaypointClient.apply(firstConnection, firstConnection, snapshot(world, 2, true, false, 0, 1, List.of(waypointB)));
        SharedWaypointClient.apply(firstConnection, firstConnection, snapshot(world, 3, true, false, 1, 2, List.of(waypointB)));
        check(SharedWaypointClient.points().size() == 2 && SharedWaypointClient.points().get(0).label().equals("A"),
                "older packet cannot displace a newer pending revision");

        // Permission and LAN availability can change without incrementing the data revision.
        SharedWaypointClient.apply(firstConnection, firstConnection, snapshot(world, 3, true, true, 0, 1, List.of(waypointA, waypointB)));
        check(SharedWaypointClient.canManage(UUID.randomUUID()), "same-revision administrator update is accepted");
        SharedWaypointClient.apply(firstConnection, firstConnection, snapshot(world, 3, false, true, 0, 1, List.of(waypointA, waypointB)));
        check(!SharedWaypointClient.canCreate(), "same-revision LAN state update disables creation");
        check(!SharedWaypointClient.send(SharedWaypointRequest.create("minecraft:overworld", 0, 64, 0, "new", 0xFFFFFFFF, "", "")),
                "create request is refused after LAN publication ends");

        Object secondConnection = new Object();
        SharedWaypointClient.apply(secondConnection, secondConnection, snapshot(world, 1, true, false, 0, 2, List.of(waypointA)));
        check(SharedWaypointClient.points().isEmpty() && !SharedWaypointClient.canCreate()
                        && SharedWaypointClient.worldId() == null,
                "reconnect clears old state while a partial snapshot cannot prune saved preferences");
        SharedWaypointClient.apply(secondConnection, secondConnection, snapshot(world, 1, true, false, 1, 2, List.of(waypointB)));
        check(SharedWaypointClient.worldId().equals(world) && SharedWaypointClient.points().size() == 2
                        && SharedWaypointClient.canCreate(),
                "reconnect restores world id and points only after every page arrives");
        SharedWaypointClient.apply(firstConnection, secondConnection, snapshot(world, 4, true, true, 0, 1, List.of(waypointA)));
        check(SharedWaypointClient.points().size() == 2, "stale old-connection packet cannot replace restored points");

        SharedWaypointClient.reset();
        check(!SharedWaypointClient.canCreate() && SharedWaypointClient.points().isEmpty(), "logout resets server state");
        Object thirdConnection = new Object();
        SharedWaypointClient.apply(thirdConnection, thirdConnection, snapshot(world, 0, true, false, 0, 1, List.of()));
        check(SharedWaypointClient.send(SharedWaypointRequest.create("minecraft:overworld", 0, 64, 0, "after logout", 0xFFFFFFFF, "", "")),
                "logout retains the platform sender for the next connection");
        check(sent.get() == 2, "sender remains configured across reset");

        System.out.println("SharedWaypointClientTest: " + checks + " checks passed");
    }

    private static SharedWaypoint waypoint(UUID id, UUID owner, String label) {
        return new SharedWaypoint(id, owner, "Steve", "minecraft:overworld", 0, 64, 0,
                label, 0xFFFFFFFF, "", "");
    }

    private static SharedWaypointSnapshot snapshot(UUID world, long revision, boolean multiplayer,
                                                    boolean administrator, int index, int count,
                                                    List<SharedWaypoint> points) {
        return new SharedWaypointSnapshot(world, revision, multiplayer, administrator, index, count, points);
    }

    private static void check(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }
}
