// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.shared;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class SharedWaypointSnapshotAssemblerTest {
    private static int checks;

    public static void main(String[] args) {
        SharedWaypointSnapshotAssembler assembler = new SharedWaypointSnapshotAssembler();
        UUID world = UUID.randomUUID();
        UUID owner = UUID.randomUUID();

        SharedWaypoint single = waypoint(UUID.randomUUID(), owner, "one");
        var accepted = assembler.accept(snapshot(world, 1, true, false, 0, 1, List.of(single)));
        check(accepted.isPresent() && accepted.orElseThrow().waypoints().equals(List.of(single)), "one-page snapshot is accepted");
        reject(() -> accepted.orElseThrow().waypoints().add(single), "completed data is immutable");

        List<SharedWaypoint> all = new ArrayList<>();
        for (int i = 0; i < 65; i++) all.add(waypoint(UUID.randomUUID(), owner, "point-" + i));
        check(assembler.accept(snapshot(world, 2, true, false, 1, 2, List.of(all.get(64)))).isEmpty(),
                "partial page is not exposed");
        var paged = assembler.accept(snapshot(world, 2, true, false, 0, 2, all.subList(0, 64)));
        check(paged.isPresent() && paged.orElseThrow().waypoints().size() == 65, "out-of-order pages assemble atomically");

        check(assembler.accept(snapshot(world, 3, true, false, 0, 2, all.subList(0, 64))).isEmpty(), "new partial revision waits");
        check(assembler.isOlderThanPending(snapshot(world, 2, true, false, 0, 1, List.of(single))),
                "older partial revisions are identified as stale");
        check(assembler.accept(snapshot(world, 3, true, false, 1, 2, all.subList(64, 65))).isPresent(),
                "older packet does not displace a newer partial revision");

        var metadata = assembler.accept(snapshot(world, 3, true, true, 0, 1, List.of(single)));
        check(metadata.isPresent() && metadata.orElseThrow().administrator(),
                "permission metadata can change without a data revision increment");

        UUID duplicateId = UUID.randomUUID();
        SharedWaypoint duplicateA = waypoint(duplicateId, owner, "a");
        SharedWaypoint duplicateB = waypoint(duplicateId, owner, "b");
        check(assembler.accept(snapshot(world, 4, true, false, 0, 2, List.of(duplicateA))).isEmpty(), "duplicate test starts partial");
        check(assembler.accept(snapshot(world, 4, true, false, 1, 2, List.of(duplicateB))).isEmpty(),
                "duplicate IDs across pages reject the snapshot");

        check(assembler.accept(snapshot(world, 5, true, false, 0, 2, List.of(single))).isEmpty(), "reset test starts partial");
        assembler.reset();
        check(assembler.accept(snapshot(world, 6, false, false, 0, 1, List.of())).isPresent(),
                "reset discards old connection pages and accepts a fresh session");
        System.out.println("SharedWaypointSnapshotAssemblerTest: " + checks + " checks passed");
    }

    private static SharedWaypoint waypoint(UUID id, UUID owner, String name) {
        return new SharedWaypoint(id, owner, "Steve", "minecraft:overworld", 0, 64, 0,
                name, 0xFFFFFFFF, "", "");
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

    private static void reject(Runnable action, String message) {
        checks++;
        try { action.run(); }
        catch (UnsupportedOperationException expected) { return; }
        throw new AssertionError(message);
    }
}
