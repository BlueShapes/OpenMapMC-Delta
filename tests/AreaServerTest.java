package net.aosankaku.openmapdelta.area;

import net.aosankaku.openmapdelta.marker.WaypointFileStore;
import net.aosankaku.openmapdelta.world.ServerWorldIdentity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/** Runtime regressions for server authorization, persistence, and rate-limit feedback. */
public final class AreaServerTest {
    private static int assertions;

    private AreaServerTest() {}

    public static void main(String[] args) throws Exception {
        String selected = args.length == 0 ? "all" : args[0];
        switch (selected) {
            case "all" -> {
                testParentAssignmentAuthorization();
                testLoadedPersonalStoreDisabled();
                testLoadedZeroUuidStoreDisabled();
                testRateLimitedMutationReceivesFeedback();
                testRateLimitedMalformedBodyReceivesFeedback();
                testFailedDiskWriteDoesNotPublishCandidate();
            }
            case "parent-auth" -> testParentAssignmentAuthorization();
            case "personal-store" -> testLoadedPersonalStoreDisabled();
            case "zero-uuid-store" -> testLoadedZeroUuidStoreDisabled();
            case "rate-limit" -> testRateLimitedMutationReceivesFeedback();
            case "rate-limit-malformed" -> testRateLimitedMalformedBodyReceivesFeedback();
            case "disk-first" -> testFailedDiskWriteDoesNotPublishCandidate();
            default -> throw new IllegalArgumentException("Unknown AreaServerTest case: " + selected);
        }
        System.out.println("PASS AreaServerTest(" + selected + "): " + assertions + " assertions");
    }

    private static void testParentAssignmentAuthorization() {
        UUID world = UUID.randomUUID();
        UUID parentOwner = UUID.randomUUID(), childOwner = UUID.randomUUID();
        var country = area(UUID.randomUUID(), "Country", MapArea.Kind.COUNTRY, parentOwner, null);
        var base = new AreaDocument(0, List.of(country));
        var newChild = area(UUID.randomUUID(), "Region", MapArea.Kind.REGION, childOwner, country.id());
        var create = request(world, 0, newChild);

        var foreignCreate = AreaTransactions.apply(base, world, create, childOwner, "child-owner", false, ignored -> true);
        check(!foreignCreate.success() && foreignCreate.document().equals(base)
                        && foreignCreate.messageKey().endsWith(".denied"),
                "a creator cannot attach a new child to another owner's country");
        var parentOwnerCreate = AreaTransactions.apply(base, world, create, parentOwner, "parent-owner", false, ignored -> true);
        check(parentOwnerCreate.success(), "the owner of the new parent may create its child");
        var administratorCreate = AreaTransactions.apply(base, world, create, childOwner, "administrator", true, ignored -> true);
        check(administratorCreate.success(), "an administrator may create a child under another owner's country");

        var otherCountry = area(UUID.randomUUID(), "Other country", MapArea.Kind.COUNTRY, parentOwner, null);
        var existingChild = area(UUID.randomUUID(), "Existing region", MapArea.Kind.REGION, childOwner, country.id());
        var hierarchy = new AreaDocument(4, List.of(country, otherCountry, existingChild));
        var reparented = existingChild.withParent(otherCountry.id());
        var reparentRequest = request(world, 4, reparented);
        var foreignReparent = AreaTransactions.apply(hierarchy, world, reparentRequest,
                childOwner, "child-owner", false, ignored -> true);
        check(!foreignReparent.success() && foreignReparent.document().equals(hierarchy)
                        && foreignReparent.messageKey().endsWith(".denied"),
                "a child owner cannot reparent into another owner's country");

        var ownedHierarchy = new AreaDocument(4, List.of(country.withOwner(childOwner, "child-owner"),
                otherCountry.withOwner(childOwner, "child-owner"), existingChild));
        var parentOwnerReparent = AreaTransactions.apply(ownedHierarchy, world, reparentRequest,
                childOwner, "child-owner", false, ignored -> true);
        check(parentOwnerReparent.success(), "an owner of both child and destination parent may reparent");
        var administratorReparent = AreaTransactions.apply(hierarchy, world, reparentRequest,
                UUID.randomUUID(), "administrator", true, ignored -> true);
        check(administratorReparent.success(), "an administrator may reparent across owners");

        var unchangedParent = existingChild.withName("Renamed region");
        var renameRequest = request(world, 4, unchangedParent);
        var childRename = AreaTransactions.apply(hierarchy, world, renameRequest,
                childOwner, "child-owner", false, ignored -> true);
        check(childRename.success(), "an existing foreign-owned parent may remain during a child edit");
    }

    private static void testLoadedPersonalStoreDisabled() throws Exception {
        withWorld("personal", worldRoot -> {
            UUID worldId = UUID.randomUUID();
            var personal = area(UUID.randomUUID(), "Private", MapArea.Kind.LAND, UUID.randomUUID(), null)
                    .withScope(MapArea.Scope.PERSONAL);
            assertInvalidStoreDisabled(worldRoot, worldId, new AreaDocument(1, List.of(personal)),
                    "a store containing personal records");
        });
    }

    private static void testLoadedZeroUuidStoreDisabled() throws Exception {
        withWorld("zero-uuid", worldRoot -> {
            UUID worldId = UUID.randomUUID();
            var invalid = area(UUID.randomUUID(), "Zero owner", MapArea.Kind.LAND, new UUID(0, 0), null);
            assertInvalidStoreDisabled(worldRoot, worldId, new AreaDocument(1, List.of(invalid)),
                    "a store containing a UUID that cannot be represented on the wire");
        });
    }

    private static void assertInvalidStoreDisabled(Path worldRoot, UUID worldId, AreaDocument invalidDocument,
                                                   String description) throws IOException {
        Path file = worldRoot.resolve("data").resolve(AreaStore.FILE_NAME);
        AreaStore.save(file, worldId.toString(), invalidDocument);
        byte[] sourceBytes = Files.readAllBytes(file);
        MinecraftServer server = server(worldRoot, worldId);
        try {
            AreaServer.initialize(server);
            var sync = new AreaWire.Request(UUID.randomUUID(), worldId, 0,
                    AreaWire.Operation.SYNC, List.of(), List.of());
            var syncResult = AreaServer.applyEncoded(server, player(), AreaWire.encode(sync), false, ignored -> true);
            var snapshots = new ArrayList<AreaWire.Snapshot>();
            AreaServer.sendSnapshot(server, player(), false, (recipient, snapshot) -> snapshots.add(snapshot));
            check(syncResult != null && !syncResult.feedback().success() && syncResult.syncRequested(),
                    description + " disables shared synchronization");
            check(snapshots.size() == 1 && !snapshots.get(0).available() && snapshots.get(0).areas().isEmpty(),
                    "invalid shared storage yields a safe unavailable snapshot without throwing");
            check(Arrays.equals(sourceBytes, Files.readAllBytes(file)),
                    "invalid source bytes are left untouched for recovery");
        } finally {
            AreaServer.stop(server);
        }
    }

    private static void testRateLimitedMutationReceivesFeedback() throws Exception {
        withWorld("rate-limit", worldRoot -> {
            UUID worldId = UUID.randomUUID();
            MinecraftServer server = server(worldRoot, worldId);
            ServerPlayer actor = player();
            server.getPlayerList().getPlayers().add(actor);
            try {
                AreaServer.initialize(server);
                var sync = new AreaWire.Request(UUID.randomUUID(), worldId, 0,
                        AreaWire.Operation.SYNC, List.of(), List.of());
                var syncResult = AreaServer.applyEncoded(server, actor, AreaWire.encode(sync), false, ignored -> true);
                check(syncResult != null && syncResult.feedback().success() && syncResult.syncRequested(),
                        "the first sync request is answered");
                var repeatedSync = new AreaWire.Request(UUID.randomUUID(), worldId, 0,
                        AreaWire.Operation.SYNC, List.of(), List.of());
                var syncLimited = AreaServer.applyEncoded(server, actor, AreaWire.encode(repeatedSync), false, ignored -> true);
                check(syncLimited != null && syncLimited.feedback().requestId().equals(repeatedSync.requestId())
                                && syncLimited.feedback().messageKey().endsWith(".rate_limited") && !syncLimited.syncRequested(),
                        "a throttled sync receives correlated feedback without triggering another snapshot");

                UUID areaId = UUID.randomUUID();
                UUID requestId = UUID.randomUUID();
                var created = new AreaWire.Request(requestId, worldId, 0, AreaWire.Operation.MUTATE,
                        List.of(area(areaId, "New shared area", MapArea.Kind.REGION, actor.getUUID(), null)), List.of());
                var createResult = AreaServer.applyEncoded(server, actor, AreaWire.encode(created), false, ignored -> true);
                check(createResult != null && createResult.changed() && createResult.feedback().success(),
                        "an initial save is not throttled by its preceding sync");

                UUID limitedId = UUID.randomUUID();
                var update = new AreaWire.Request(limitedId, worldId, 1, AreaWire.Operation.MUTATE,
                        List.of(area(areaId, "Updated too soon", MapArea.Kind.REGION, actor.getUUID(), null)), List.of());
                var limited = AreaServer.applyEncoded(server, actor, AreaWire.encode(update), false, ignored -> true);
                check(limited != null && limited.feedback().requestId().equals(limitedId)
                                && limited.feedback().messageKey().endsWith(".rate_limited"),
                        "a rate-limited save receives correlated feedback instead of being silently discarded");
                check(AreaServer.areaCount(server) == 1,
                        "a rejected rate-limited update leaves the last saved state in memory");
            } finally {
                AreaServer.stop(server);
            }
        });
    }

    private static void testFailedDiskWriteDoesNotPublishCandidate() throws Exception {
        withWorld("disk-first", worldRoot -> {
            UUID worldId = UUID.randomUUID();
            MinecraftServer server = server(worldRoot, worldId);
            ServerPlayer actor = player();
            try {
                AreaServer.initialize(server);
                WaypointFileStore.failNextWrite = true;
                var create = request(worldId, 0,
                        area(UUID.randomUUID(), "Must not appear", MapArea.Kind.REGION, actor.getUUID(), null));
                var result = AreaServer.applyEncoded(server, actor, AreaWire.encode(create), false, ignored -> true);
                check(result != null && !result.feedback().success()
                                && result.feedback().messageKey().endsWith(".storage_error"),
                        "a failed disk write returns a storage error");
                check(!result.changed() && AreaServer.areaCount(server) == 0,
                        "a failed disk write does not publish the candidate in memory");
                var snapshots = new ArrayList<AreaWire.Snapshot>();
                AreaServer.sendSnapshot(server, actor, false, (recipient, snapshot) -> snapshots.add(snapshot));
                check(snapshots.size() == 1 && snapshots.get(0).revision() == 0 && snapshots.get(0).areas().isEmpty(),
                        "snapshots retain the previous revision and document after a failed write");
            } finally {
                WaypointFileStore.failNextWrite = false;
                AreaServer.stop(server);
            }
        });
    }

    private static void testRateLimitedMalformedBodyReceivesFeedback() throws Exception {
        withWorld("rate-limit-malformed", worldRoot -> {
            UUID worldId = UUID.randomUUID();
            MinecraftServer server = server(worldRoot, worldId);
            ServerPlayer actor = player();
            server.getPlayerList().getPlayers().add(actor);
            try {
                AreaServer.initialize(server);
                var first = request(worldId, 0,
                        area(UUID.randomUUID(), "First", MapArea.Kind.REGION, actor.getUUID(), null));
                var accepted = AreaServer.applyEncoded(server, actor, AreaWire.encode(first), false, ignored -> true);
                check(accepted != null && accepted.changed(), "first request occupies mutation bucket");

                UUID limitedId = UUID.randomUUID();
                var expensive = new AreaWire.Request(limitedId, worldId, 1, AreaWire.Operation.MUTATE,
                        List.of(largeArea(UUID.randomUUID(), actor.getUUID(), 512)), List.of());
                byte[] completeBody = AreaWire.encode(expensive);
                byte[] malformedBody = Arrays.copyOf(completeBody, completeBody.length + 1);
                malformedBody[malformedBody.length - 1] = 1;
                var limited = AreaServer.applyEncoded(server, actor, malformedBody, false, ignored -> true);
                check(limited != null && limited.feedback().requestId().equals(limitedId),
                        "a throttled packet with a fixed header receives correlated feedback before body decoding");
                check(limited.feedback().messageKey().endsWith(".rate_limited"),
                        "the bounded-header response identifies a rate-limited request");

                server.setTickCount(AreaServer.MUTATION_RATE_TICKS);
                byte[] malformedHeader = malformedBody.clone();
                malformedHeader[0] ^= 0x7f;
                check(AreaServer.applyEncoded(server, actor, malformedHeader, false, ignored -> true) == null,
                        "a packet without a valid fixed header is dropped");
            } finally {
                AreaServer.stop(server);
            }
        });
    }

    private static MinecraftServer server(Path root, UUID worldId) {
        MinecraftServer server = new MinecraftServer(root);
        ServerWorldIdentity.set(server, worldId);
        return server;
    }

    private static ServerPlayer player() { return new ServerPlayer(UUID.randomUUID(), "Area tester"); }

    private static AreaWire.Request request(UUID world, long revision, MapArea area) {
        return new AreaWire.Request(UUID.randomUUID(), world, revision, AreaWire.Operation.MUTATE, List.of(area), List.of());
    }

    private static MapArea area(UUID id, String name, MapArea.Kind kind, UUID owner, UUID parent) {
        var ring = List.of(new MapArea.Point(0, 0), new MapArea.Point(12, 0), new MapArea.Point(12, 12));
        return new MapArea(id, "minecraft:overworld", name, kind, MapArea.Scope.SHARED, owner, "Owner", parent, 0,
                MapArea.Geometry.polygon(List.of(new MapArea.Polygon(ring, List.of()))),
                new MapArea.Point(6, 6), new MapArea.Style(0x445566, true, true, true, false, .25, 4096, 0));
    }

    private static MapArea largeArea(UUID id, UUID owner, int points) {
        var ring = new ArrayList<MapArea.Point>(points);
        for (int i = 0; i < points; i++) {
            double angle = 2 * Math.PI * i / points;
            ring.add(new MapArea.Point(1000 * Math.cos(angle), 1000 * Math.sin(angle)));
        }
        return new MapArea(id, "minecraft:overworld", "Large", MapArea.Kind.REGION, MapArea.Scope.SHARED,
                owner, "Owner", null, 0,
                MapArea.Geometry.polygon(List.of(new MapArea.Polygon(ring, List.of()))),
                new MapArea.Point(0, 0), new MapArea.Style(0x445566, true, true, true, false, .25, 4096, 0));
    }

    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }

    private static void withWorld(String name, WorldTest test) throws Exception {
        Path testRoot = Path.of(System.getProperty("areaTestRoot", ".task-work/areas-20261028/server-worlds"));
        Files.createDirectories(testRoot);
        Path worldRoot = Files.createTempDirectory(testRoot, name + "-");
        try {
            test.run(worldRoot);
        } finally {
            try (var paths = Files.walk(worldRoot)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
            }
        }
    }

    @FunctionalInterface
    private interface WorldTest { void run(Path worldRoot) throws Exception; }
}
