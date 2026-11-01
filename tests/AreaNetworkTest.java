package net.aosankaku.openmapdelta.area;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/** Unit checks for bounded shared-area transport, server authorization and atomic client snapshots. */
public final class AreaNetworkTest {
    private static int assertions;

    private AreaNetworkTest() {}

    public static void main(String[] args) {
        requestCodecIsBoundedAndStrict();
        snapshotsAssembleAtomicallyAndRejectMixedPages();
        serverTransactionsAuthorizeEveryAffectedArea();
        clientRejectsOldConnectionAndRevisions();
        clientKeepsMutationPendingAcrossSnapshots();
        clientDoesNotConsumeFeedbackForAnotherWorld();
        System.out.println("PASS AreaNetworkTest: " + assertions + " assertions");
    }

    private static void requestCodecIsBoundedAndStrict() {
        UUID world = UUID.randomUUID(), actor = UUID.randomUUID();
        var forged = polygon(UUID.randomUUID(), MapArea.Scope.SHARED, UUID.randomUUID(), "forged", null, "共有駅🚉");
        var request = new AreaWire.Request(UUID.randomUUID(), world, 7, AreaWire.Operation.MUTATE, List.of(forged), List.of());
        var decoded = AreaWire.decodeRequest(AreaWire.encode(request));
        check(decoded.equals(request), "Unicode feature and transaction metadata round-trip");
        var triangle = new MapArea(UUID.randomUUID(), "minecraft:overworld", "Triangle", MapArea.Kind.POND,
                MapArea.Scope.SHARED, actor, "Owner", null, 0,
                MapArea.Geometry.polygon(List.of(new MapArea.Polygon(List.of(new MapArea.Point(0, 0),
                        new MapArea.Point(8, 0), new MapArea.Point(4, 8)), List.of()))),
                new MapArea.Point(4, 2), new MapArea.Style(0x445566, true, true, true, true, .25, 4096, 0));
        var triangleRequest = new AreaWire.Request(UUID.randomUUID(), world, 7, AreaWire.Operation.MUTATE,
                List.of(triangle), List.of());
        check(AreaWire.decodeRequest(AreaWire.encode(triangleRequest)).equals(triangleRequest),
                "open three-point polygon rings round-trip");
        var feedback = new AreaWire.Feedback(UUID.randomUUID(), world, false,
                "openmapdelta.area.feedback.conflict", 7);
        check(AreaWire.decodeFeedback(AreaWire.encode(feedback)).equals(feedback),
                "feedback codec round-trips safe localized keys");
        rejects(() -> new AreaWire.Feedback(UUID.randomUUID(), world, false, "client supplied command", 7),
                "feedback cannot carry arbitrary text or commands");
        rejects(() -> AreaWire.decodeRequest(Arrays.copyOf(AreaWire.encode(request), AreaWire.encode(request).length + 1)),
                "request trailing bytes are rejected");
        rejects(() -> AreaWire.decodeRequest(new byte[AreaWire.MAX_REQUEST_BYTES + 1]), "oversized request is rejected");
        rejects(() -> AreaWire.decodeRequest(Arrays.copyOf(AreaWire.encode(request), 3)), "truncated header is rejected");
        rejects(() -> new AreaWire.Request(request.requestId(), world, 7, AreaWire.Operation.SYNC, List.of(forged), List.of()),
                "snapshot request cannot smuggle mutations");
        rejects(() -> new AreaWire.Request(request.requestId(), world, 7, AreaWire.Operation.MUTATE,
                List.of(forged, forged), List.of()), "duplicate ids in a transaction are rejected");
        check(!forged.ownerId().equals(actor), "fixture carries a forged owner to test server-derived attribution");
    }

    private static void snapshotsAssembleAtomicallyAndRejectMixedPages() {
        UUID world = UUID.randomUUID();
        var first = polygon(UUID.randomUUID(), MapArea.Scope.SHARED, UUID.randomUUID(), "Owner", null, "First");
        var second = polygon(UUID.randomUUID(), MapArea.Scope.SHARED, UUID.randomUUID(), "Owner", null, "Second");
        var assembler = new AreaSnapshotAssembler();
        check(assembler.accept(new AreaWire.Snapshot(world, 5, 0, 2, false, true, List.of(first))).isEmpty(),
                "an incomplete snapshot exposes no partial features");
        check(assembler.accept(new AreaWire.Snapshot(world, 5, 0, 2, false, true, List.of(first))).isEmpty(),
                "a duplicate page is ignored without completing the snapshot");
        var complete = assembler.accept(new AreaWire.Snapshot(world, 5, 1, 2, false, true, List.of(second))).orElseThrow();
        check(complete.equals(List.of(first, second)), "complete pages replace state in order");
        check(assembler.accept(new AreaWire.Snapshot(world, 6, 0, 2, false, true, List.of(first))).isEmpty(),
                "a different revision restarts assembly without partial state");
        rejects(() -> assembler.accept(new AreaWire.Snapshot(UUID.randomUUID(), 6, 2, 2, false, true, List.of(second))),
                "malformed page metadata is rejected before assembly");

        var bounded = new AreaSnapshotAssembler();
        UUID largeWorld = UUID.randomUUID();
        for (int page = 0; page < 33; page++) {
            var line = lineArea(UUID.randomUUID(), page);
            var accepted = bounded.accept(new AreaWire.Snapshot(largeWorld, 1, page, 33, false, true, List.of(line)));
            check(accepted.isEmpty(), "oversized aggregate snapshots never expose a partial collection");
        }
        rejects(() -> AreaWire.snapshotPages(largeWorld, 1, false, true,
                java.util.stream.IntStream.range(0, 33).mapToObj(page -> lineArea(UUID.randomUUID(), page)).toList()),
                "server refuses snapshots beyond the aggregate point limit");
    }

    private static void serverTransactionsAuthorizeEveryAffectedArea() {
        UUID world = UUID.randomUUID(), owner = UUID.randomUUID(), attacker = UUID.randomUUID();
        var original = polygon(UUID.randomUUID(), MapArea.Scope.SHARED, owner, "owner", null, "Country");
        var document = new AreaDocument(3, List.of(original));

        var changed = original.withOwner(attacker, "Forged").withName("Changed");
        var update = new AreaWire.Request(UUID.randomUUID(), world, 3, AreaWire.Operation.MUTATE, List.of(changed), List.of());
        var ownerUpdate = AreaTransactions.apply(document, world, update, owner, "owner", false, ignored -> true);
        check(ownerUpdate.success() && ownerUpdate.changed() && ownerUpdate.document().revision() == 4,
                "the authenticated owner can commit and advances the revision");
        check(ownerUpdate.document().find(original.id()).orElseThrow().ownerId().equals(owner),
                "client cannot forge feature ownership on update");

        var denied = AreaTransactions.apply(document, world, update, attacker, "attacker", false, ignored -> true);
        check(!denied.success() && denied.document().equals(document), "another player cannot mutate a feature");
        var admin = AreaTransactions.apply(document, world, update, attacker, "admin", true, ignored -> true);
        check(admin.success() && admin.document().find(original.id()).orElseThrow().ownerId().equals(owner),
                "administrator can edit without changing the original owner");

        var create = new AreaWire.Request(UUID.randomUUID(), world, 3, AreaWire.Operation.MUTATE,
                List.of(polygon(UUID.randomUUID(), MapArea.Scope.SHARED, owner, "Forged", null, "New")), List.of());
        var created = AreaTransactions.apply(document, world, create, attacker, "actor", false, ignored -> true);
        check(created.success() && created.document().areas().stream().anyMatch(area -> area.ownerId().equals(attacker)
                && area.ownerName().equals("actor")), "creator identity comes from the authenticated server actor");

        var stale = AreaTransactions.apply(document, world, new AreaWire.Request(UUID.randomUUID(), world, 2,
                AreaWire.Operation.MUTATE, List.of(original), List.of()), owner, "owner", false, ignored -> true);
        check(!stale.success() && stale.messageKey().contains("conflict"), "stale global revision returns a conflict without overwriting");
        var wrongWorld = AreaTransactions.apply(document, world, new AreaWire.Request(UUID.randomUUID(), UUID.randomUUID(), 3,
                AreaWire.Operation.MUTATE, List.of(original), List.of()), owner, "owner", false, ignored -> true);
        check(!wrongWorld.success() && wrongWorld.document().equals(document), "cross-world request is rejected");

        var personal = original.withScope(MapArea.Scope.PERSONAL);
        rejects(() -> new AreaWire.Request(UUID.randomUUID(), world, 3,
                AreaWire.Operation.MUTATE, List.of(personal), List.of()),
                "private areas cannot enter the server snapshot");

        var country = polygon(UUID.randomUUID(), MapArea.Scope.SHARED, owner, "owner", null, "Land");
        var child = polygon(UUID.randomUUID(), MapArea.Scope.SHARED, owner, "owner", country.id(), "Region");
        var hierarchy = new AreaDocument(0, List.of(country, child));
        var delete = new AreaWire.Request(UUID.randomUUID(), world, 0, AreaWire.Operation.MUTATE,
                List.of(), List.of(country.id()));
        var parentDelete = AreaTransactions.apply(hierarchy, world, delete, owner, "owner", false, ignored -> true);
        check(parentDelete.success() && parentDelete.document().find(child.id()).orElseThrow().parentId() == null,
                "parent deletion atomically reparents authorized children");
        var childOwnedByOther = child.withOwner(attacker, "other");
        var protectedHierarchy = new AreaDocument(0, List.of(country, childOwnedByOther));
        var deniedCascade = AreaTransactions.apply(protectedHierarchy, world, delete, owner, "owner", false, ignored -> true);
        check(!deniedCascade.success() && deniedCascade.document().equals(protectedHierarchy),
                "cannot delete a parent while silently rewriting another owner's child");
    }

    private static void clientRejectsOldConnectionAndRevisions() {
        var sends = new ArrayList<AreaWire.Request>();
        Consumer<AreaWire.Request> sender = sends::add;
        AreaClient.reset();
        AreaClient.configureSender(sender);
        Object connection = new Object(), replaced = new Object();
        var actor = UUID.randomUUID();
        AreaClient.bind(connection);
        var area = polygon(UUID.randomUUID(), MapArea.Scope.SHARED, actor, "actor", null, "Local sample");
        UUID world = UUID.randomUUID();
        var first = new AreaWire.Snapshot(world, 4, 0, 2, false, true, List.of(area));
        long beforePartial = AreaClient.revision();
        AreaClient.apply(connection, connection, first);
        check(AreaClient.areas().isEmpty() && AreaClient.revision() == beforePartial,
                "client leaves existing state unchanged while a snapshot is partial");
        AreaClient.apply(connection, connection, new AreaWire.Snapshot(world, 4, 1, 2, false, true, List.of()));
        check(AreaClient.areas().equals(List.of(area)) && AreaClient.worldId().equals(world),
                "client replaces state only after snapshot assembly completes");
        check(AreaClient.send(new AreaWire.Request(UUID.randomUUID(), world, 4,
                AreaWire.Operation.MUTATE, List.of(area), List.of())), "available client queues a shared mutation");
        long acceptedRevision = AreaClient.revision();
        AreaClient.apply(replaced, connection, new AreaWire.Snapshot(world, 8, 0, 1, true, true, List.of()));
        check(AreaClient.revision() == acceptedRevision && AreaClient.areas().equals(List.of(area)),
                "queued packets from a replaced network connection are ignored");
        AreaClient.apply(connection, connection, new AreaWire.Snapshot(world, 3, 0, 1, true, true, List.of()));
        check(AreaClient.areas().equals(List.of(area)), "older server snapshots cannot roll back accepted state");
        AreaClient.reset();
        check(!AreaClient.available() && AreaClient.areas().isEmpty() && AreaClient.worldId() == null,
                "logout clears server state but keeps the transport configurable");
        check(AreaClient.configureSender(sender), "senders can be replaced after a reset");
    }

    private static void clientKeepsMutationPendingAcrossSnapshots() {
        var sends = new ArrayList<AreaWire.Request>();
        AreaClient.reset();
        AreaClient.configureSender(sends::add);
        Object connection = new Object();
        AreaClient.bind(connection);
        UUID world = UUID.randomUUID(), actor = UUID.randomUUID();
        var area = polygon(UUID.randomUUID(), MapArea.Scope.SHARED, actor, "actor", null, "Before");
        AreaClient.apply(connection, connection, new AreaWire.Snapshot(world, 10, 0, 1, false, true, List.of(area)));

        UUID mutationId = UUID.randomUUID();
        var mutation = new AreaWire.Request(mutationId, world, 10, AreaWire.Operation.MUTATE,
                List.of(area.withName("After")), List.of());
        check(AreaClient.send(mutation), "mutation is tracked while awaiting feedback");
        AreaClient.apply(connection, connection, new AreaWire.Snapshot(world, 11, 0, 1, false, true,
                List.of(area.withName("After"))));
        var feedback = new AreaWire.Feedback(mutationId, world, true, "openmapdelta.area.feedback.saved", 11);
        AreaClient.feedback(connection, connection, feedback);
        check(feedback.equals(AreaClient.lastFeedback()),
                "a full snapshot arriving first does not discard pending mutation feedback");
    }

    private static void clientDoesNotConsumeFeedbackForAnotherWorld() {
        var sends = new ArrayList<AreaWire.Request>();
        AreaClient.reset();
        AreaClient.configureSender(sends::add);
        Object connection = new Object();
        AreaClient.bind(connection);
        UUID world = UUID.randomUUID(), actor = UUID.randomUUID();
        var area = polygon(UUID.randomUUID(), MapArea.Scope.SHARED, actor, "actor", null, "Before");
        AreaClient.apply(connection, connection, new AreaWire.Snapshot(world, 10, 0, 1, false, true, List.of(area)));

        UUID mutationId = UUID.randomUUID();
        check(AreaClient.send(new AreaWire.Request(mutationId, world, 10, AreaWire.Operation.MUTATE,
                List.of(area.withName("After")), List.of())), "mutation is pending before feedback");
        AreaClient.feedback(connection, connection, new AreaWire.Feedback(mutationId, UUID.randomUUID(), false,
                "openmapdelta.area.feedback.conflict", 10));
        var validFeedback = new AreaWire.Feedback(mutationId, world, true, "openmapdelta.area.feedback.saved", 11);
        AreaClient.feedback(connection, connection, validFeedback);
        check(validFeedback.equals(AreaClient.lastFeedback()),
                "wrong-world feedback cannot consume a pending request id");
    }

    private static MapArea polygon(UUID id, MapArea.Scope scope, UUID ownerId, String ownerName,
                                   UUID parentId, String name) {
        var ring = List.of(new MapArea.Point(0, 0), new MapArea.Point(12, 0),
                new MapArea.Point(12, 12), new MapArea.Point(0, 12));
        return new MapArea(id, "minecraft:overworld", name, MapArea.Kind.REGION, scope,
                ownerId, ownerName, parentId, 0,
                MapArea.Geometry.polygon(List.of(new MapArea.Polygon(ring, List.of()))),
                new MapArea.Point(6, 6), new MapArea.Style(0x445566, true, true, true, false, .25, 4096, 0));
    }

    private static MapArea lineArea(UUID id, int offset) {
        var points = new ArrayList<MapArea.Point>(AreaDocument.MAX_POINTS_PER_AREA);
        for (int index = 0; index < AreaDocument.MAX_POINTS_PER_AREA; index++) {
            points.add(new MapArea.Point(offset * 512.0 + index, 100.0));
        }
        return new MapArea(id, "minecraft:overworld", "Line " + offset, MapArea.Kind.RIVER,
                MapArea.Scope.SHARED, UUID.randomUUID(), "Owner", null, 0,
                MapArea.Geometry.line(List.of(points)), new MapArea.Point(0, 100),
                new MapArea.Style(0x445566, true, true, true, false, .25, 4096, 0));
    }

    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }

    private static void rejects(Runnable operation, String message) {
        assertions++;
        try { operation.run(); }
        catch (IllegalArgumentException expected) { return; }
        throw new AssertionError(message);
    }
}
