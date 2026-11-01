// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.shared;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.aosankaku.openmapdelta.OpenMapDeltaCommon;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Set;
import java.util.UUID;

/** Headless contract test for successful shared waypoint mutation audit events. */
public final class SharedWaypointCreatedEventTest {
    private static int checks;

    private SharedWaypointCreatedEventTest() {}

    public static void main(String[] args) throws Exception {
        Path base = Files.createTempDirectory(Path.of(System.getProperty("java.io.tmpdir")), "shared-waypoint-event-");
        successfulApply(base.resolve("apply"));
        successfulApplyEncoded(base.resolve("encoded"));
        failedCreationDoesNotEmit(base.resolve("failed-save"));
        revisionOverflowDoesNotEmit(base.resolve("revision-overflow"));
        fullStoreDoesNotEmit(base.resolve("full"));
        mutationAndRejectedPathsDoNotEmit(base.resolve("mutation"));
        successfulMutationEvents(base.resolve("successful-mutations"));
        mutationActorEscaping(base.resolve("mutation-actor-escaping"));
        mutationStoreFailuresRollback(base.resolve("failed-mutations"));
        mutationRevisionOverflowDoesNotEmit(base.resolve("mutation-revision-overflow"));
        initializeAndSnapshotDoNotEmit(base.resolve("snapshot"));
        System.out.println("SharedWaypointCreatedEventTest passed " + checks + " checks.");
    }

    private static void successfulApply(Path directory) throws Exception {
        MinecraftServer server = server(directory);
        ServerPlayer actor = new ServerPlayer();
        actor.setName("Alice");
        var request = SharedWaypointRequest.create("minecraft:overworld", 123, 64, -456,
                "拠点", 0xff00ff, "", "");
        int[] observedAtInfo = {0};
        OpenMapDeltaCommon.LOGGER.infoObserver = () -> {
            observedAtInfo[0]++;
            check(SharedWaypointServer.waypointCount(server) == 1,
                    "in-memory waypoint state is published before INFO emission");
            var stored = SharedWaypointStore.load(server.directory.resolve("data").resolve(SharedWaypointServer.FILE_NAME));
            check(stored.valid() && stored.stored().revision() == 1 && stored.stored().waypoints().size() == 1,
                    "atomic store commit is visible before INFO emission");
            var snapshots = new ArrayList<SharedWaypointSnapshot>();
            SharedWaypointServer.sendSnapshot(server, actor, true, false, (player, snapshot) -> snapshots.add(snapshot));
            check(snapshots.size() == 1 && snapshots.get(0).revision() == 1,
                    "published revision is visible before INFO emission");
        };
        boolean changed;
        try {
            changed = SharedWaypointServer.apply(server, actor, request, false, true, ignored -> true).changed();
        } finally {
            OpenMapDeltaCommon.LOGGER.infoObserver = null;
        }
        check(changed, "apply CREATE commits");
        check(observedAtInfo[0] == 1, "commit observer saw exactly one event emission");
        SharedWaypoint saved = loadOnlyWaypoint(server);
        onlyEvent(saved);
        String payload = eventPayload();
        OpenMapDeltaCommon.InfoMessage message = OpenMapDeltaCommon.LOGGER.infos.get(0);
        printUtf8("captured INFO: " + message.template().replace("{}", payload));
    }

    private static void successfulApplyEncoded(Path directory) throws Exception {
        clearEvents();
        MinecraftServer server = server(directory);
        ServerPlayer actor = new ServerPlayer();
        String separator = Character.toString(0x2028) + Character.toString(0x2029);
        String creator = "A\"\\" + separator;
        String label = "quoted \" label \\" + separator;
        actor.setName(creator);
        var request = SharedWaypointRequest.create("minecraft:the_nether", -12, 96, 34,
                label, 0x123456, "", "");
        check(SharedWaypointServer.applyEncoded(server, actor, request.encode(), false, true, ignored -> true).changed(),
                "applyEncoded CREATE commits");
        SharedWaypoint saved = loadOnlyWaypoint(server);
        JsonObject event = onlyEvent(saved);
        String payload = eventPayload();
        check(payload.contains("\\"), "Gson escapes quote and backslash characters in the JSON string");
        check(payload.contains("\\" + "u2028") && payload.contains("\\" + "u2029"),
                "Gson escapes U+2028 and U+2029 in the JSON string");
        check(event.get("name").getAsString().equals(label)
                        && event.get("creator_name").getAsString().equals(creator),
                "escaped text round-trips exactly from saved waypoint to event");
    }

    private static void failedCreationDoesNotEmit(Path directory) throws Exception {
        Files.createDirectories(directory);
        Files.writeString(directory.resolve("data"), "blocks data directory creation");
        MinecraftServer server = server(directory);
        clearEvents();
        var result = SharedWaypointServer.apply(server, new ServerPlayer(),
                SharedWaypointRequest.create("minecraft:overworld", 1, 64, 2, "failed", 0, "", ""),
                false, true, ignored -> true);
        check(!result.changed() && result.feedback().messageKey().equals("odysseymap.shared.storage_error"),
                "failed atomic store write returns storage error");
        check(SharedWaypointServer.waypointCount(server) == 0, "failed create leaves in-memory state unchanged");
        check(OpenMapDeltaCommon.LOGGER.infos.isEmpty(), "failed create emits no event");
    }

    private static void fullStoreDoesNotEmit(Path directory) throws Exception {
        MinecraftServer server = server(directory);
        UUID owner = UUID.randomUUID();
        var full = new ArrayList<SharedWaypoint>(SharedWaypointSnapshot.MAX_WAYPOINTS);
        for (int index = 0; index < SharedWaypointSnapshot.MAX_WAYPOINTS; index++) {
            full.add(new SharedWaypoint(UUID.randomUUID(), owner, "owner", "minecraft:overworld",
                    index, 64, 0, "full-" + index, 0, "", ""));
        }
        SharedWaypointStore.save(directory.resolve("data").resolve(SharedWaypointServer.FILE_NAME),
                new SharedWaypointStore.Stored(1, full));
        clearEvents();
        var result = SharedWaypointServer.apply(server, new ServerPlayer(),
                SharedWaypointRequest.create("minecraft:overworld", 1, 64, 2, "full", 0, "", ""),
                false, true, ignored -> true);
        check(!result.changed() && result.feedback().messageKey().equals("odysseymap.shared.full"),
                "full shared waypoint cap rejects new create");
        check(OpenMapDeltaCommon.LOGGER.infos.isEmpty(), "full-cap create emits no event");
    }

    private static void revisionOverflowDoesNotEmit(Path directory) throws Exception {
        MinecraftServer server = server(directory);
        Path store = directory.resolve("data").resolve(SharedWaypointServer.FILE_NAME);
        SharedWaypointStore.save(store, new SharedWaypointStore.Stored(Long.MAX_VALUE, java.util.List.of()));
        clearEvents();
        var result = SharedWaypointServer.apply(server, new ServerPlayer(),
                SharedWaypointRequest.create("minecraft:overworld", 1, 64, 2, "overflow", 0, "", ""),
                false, true, ignored -> true);
        check(!result.changed() && result.feedback().messageKey().equals("odysseymap.shared.storage_error"),
                "revision overflow fails closed as storage error");
        check(OpenMapDeltaCommon.LOGGER.infos.isEmpty(), "revision overflow emits no event");
    }

    private static void mutationAndRejectedPathsDoNotEmit(Path directory) throws Exception {
        MinecraftServer server = server(directory);
        ServerPlayer owner = new ServerPlayer();
        ServerPlayer other = new ServerPlayer();
        server.players.add(owner);
        server.players.add(other);
        var create = SharedWaypointRequest.create("minecraft:overworld", 1, 64, 2, "point", 0, "", "");
        check(SharedWaypointServer.apply(server, owner, create, false, true, ignored -> true).changed(),
                "setup create commits");
        UUID id = loadOnlyWaypoint(server).id();
        clearEvents();

        var rateLimited = SharedWaypointServer.apply(server, owner, create, false, true, ignored -> true);
        check(!rateLimited.changed() && rateLimited.feedback().messageKey().equals("odysseymap.shared.rate_limited"),
                "repeated same-tick create is rate limited");
        server.tick += SharedWaypointServer.MUTATION_RATE_TICKS;
        var update = SharedWaypointRequest.update(id, "minecraft:overworld", 3, 66, 4, "edited", 0, "", "");
        var deniedUpdate = SharedWaypointServer.apply(server, other, update, false, true, ignored -> true);
        check(!deniedUpdate.changed() && deniedUpdate.feedback().messageKey().equals("odysseymap.shared.denied"),
                "non-owner update is denied");
        server.tick += SharedWaypointServer.MUTATION_RATE_TICKS;
        var deniedDelete = SharedWaypointServer.apply(server, other, SharedWaypointRequest.delete(id), false, true,
                ignored -> true);
        check(!deniedDelete.changed() && deniedDelete.feedback().messageKey().equals("odysseymap.shared.denied"),
                "non-owner delete is denied");
        server.tick += SharedWaypointServer.MUTATION_RATE_TICKS;
        var unavailable = SharedWaypointServer.apply(server, owner, create, false, false, ignored -> true);
        check(!unavailable.changed() && unavailable.feedback().messageKey().equals("odysseymap.shared.unavailable"),
                "create in non-multiplayer mode is rejected");
        var unknownDimension = SharedWaypointServer.apply(server, new ServerPlayer(), create, false, true,
                ignored -> false);
        check(!unknownDimension.changed() && unknownDimension.feedback().messageKey().equals("odysseymap.shared.invalid"),
                "unknown dimension is rejected");
        var malformed = SharedWaypointServer.applyEncoded(server, new ServerPlayer(), new byte[]{1, 99}, false, true,
                ignored -> true);
        check(!malformed.changed() && malformed.feedback().messageKey().equals("odysseymap.shared.invalid"),
                "malformed encoded create is rejected");
        server.tick += SharedWaypointServer.MUTATION_RATE_TICKS;
        var invalidUpdate = SharedWaypointServer.apply(server, owner,
                SharedWaypointRequest.update(id, "minecraft:the_nether", 3, 66, 4, "invalid dimension", 0, "", ""),
                false, true, ignored -> false);
        check(!invalidUpdate.changed() && invalidUpdate.feedback().messageKey().equals("odysseymap.shared.invalid"),
                "unknown-dimension UPDATE is rejected as invalid");
        server.tick += SharedWaypointServer.MUTATION_RATE_TICKS;
        byte[] validUpdate = SharedWaypointRequest.update(id, "minecraft:overworld", 3, 66, 4, "edited", 0, "", "").encode();
        byte[] truncatedUpdate = java.util.Arrays.copyOf(validUpdate, validUpdate.length - 1);
        var malformedMutation = SharedWaypointServer.applyEncoded(server, owner, truncatedUpdate, false, true,
                ignored -> true);
        check(!malformedMutation.changed() && malformedMutation.feedback().messageKey().equals("odysseymap.shared.invalid"),
                "malformed encoded mutation is rejected as invalid");
        server.tick += SharedWaypointServer.MUTATION_RATE_TICKS;
        var absentDelete = SharedWaypointServer.apply(server, new ServerPlayer(),
                SharedWaypointRequest.delete(UUID.randomUUID()), true, true, ignored -> true);
        check(!absentDelete.changed() && absentDelete.feedback().messageKey().equals("odysseymap.shared.invalid"),
                "DELETE for an absent waypoint is rejected as invalid");
        check(OpenMapDeltaCommon.LOGGER.infos.isEmpty(), "rate limited, denied, unavailable, and invalid requests emit no event");

        expectInvalid(() -> SharedWaypointRequest.create("minecraft:overworld", 1, 64, 2,
                "bad\nlabel", 0, "", ""));
        expectInvalid(() -> SharedWaypointRequest.create("minecraft:overworld", 1, 64, 2,
                "bad\rlabel", 0, "", ""));
        check(OpenMapDeltaCommon.LOGGER.infos.isEmpty(), "rejected CR/LF labels emit no event");
    }

    private static void successfulMutationEvents(Path directory) throws Exception {
        MinecraftServer server = server(directory);
        ServerPlayer owner = new ServerPlayer();
        owner.setName("Alice");
        ServerPlayer admin = new ServerPlayer();
        admin.setName("Admin");
        server.players.addAll(java.util.List.of(owner, admin));

        check(SharedWaypointServer.apply(server, owner,
                SharedWaypointRequest.create("minecraft:overworld", 10, 64, 20, "original", 0, "", ""),
                false, true, ignored -> true).changed(), "mutation fixture create commits");
        SharedWaypoint original = loadOnlyWaypoint(server);
        onlyEvent(original); // Guard the existing CREATE schema and owner fields.

        clearEvents();
        var ownerUpdate = SharedWaypointRequest.update(original.id(), "minecraft:overworld",
                11, 65, 21, "owner edit", 0, "", "");
        SharedWaypoint afterOwnerUpdate = new SharedWaypoint(original.id(), original.ownerId(), original.ownerName(),
                ownerUpdate.dimension(), ownerUpdate.x(), ownerUpdate.y(), ownerUpdate.z(), ownerUpdate.label(),
                ownerUpdate.color(), ownerUpdate.iconId(), ownerUpdate.category());
        server.tick += SharedWaypointServer.MUTATION_RATE_TICKS;
        int[] ownerUpdateObserved = observeCommittedStateAtInfo(server, owner, 2, afterOwnerUpdate);
        boolean ownerUpdateChanged;
        try {
            ownerUpdateChanged = SharedWaypointServer.apply(server, owner, ownerUpdate, false, true, ignored -> true).changed();
        } finally {
            OpenMapDeltaCommon.LOGGER.infoObserver = null;
        }
        check(ownerUpdateChanged && ownerUpdateObserved[0] == 1, "owner UPDATE commits and emits once after commit");
        onlyMutationEvent(afterOwnerUpdate, "shared_waypoint_updated", owner);
        check(SharedWaypointServer.apply(server, owner, ownerUpdate, false, true, ignored -> true).feedback()
                        .messageKey().equals("odysseymap.shared.rate_limited")
                        && OpenMapDeltaCommon.LOGGER.infos.size() == 1,
                "same-tick UPDATE retry is rate limited without a duplicate event");

        clearEvents();
        var adminUpdate = SharedWaypointRequest.update(original.id(), "minecraft:the_end",
                101, 70, -202, "admin edit", 0, "", "");
        SharedWaypoint afterAdminUpdate = new SharedWaypoint(original.id(), original.ownerId(), original.ownerName(),
                adminUpdate.dimension(), adminUpdate.x(), adminUpdate.y(), adminUpdate.z(), adminUpdate.label(),
                adminUpdate.color(), adminUpdate.iconId(), adminUpdate.category());
        server.tick += SharedWaypointServer.MUTATION_RATE_TICKS;
        int[] adminUpdateObserved = observeCommittedStateAtInfo(server, admin, 3, afterAdminUpdate);
        boolean adminUpdateChanged;
        try {
            adminUpdateChanged = SharedWaypointServer.applyEncoded(server, admin, adminUpdate.encode(), true, true,
                    ignored -> true).changed();
        } finally {
            OpenMapDeltaCommon.LOGGER.infoObserver = null;
        }
        check(adminUpdateChanged && adminUpdateObserved[0] == 1, "admin applyEncoded UPDATE commits and emits once");
        onlyMutationEvent(afterAdminUpdate, "shared_waypoint_updated", admin);
        printCapturedEvent("UPDATE");
        check(SharedWaypointServer.applyEncoded(server, admin, adminUpdate.encode(), true, true, ignored -> true)
                        .feedback().messageKey().equals("odysseymap.shared.rate_limited")
                        && OpenMapDeltaCommon.LOGGER.infos.size() == 1,
                "same-tick applyEncoded UPDATE retry does not duplicate its event");

        clearEvents();
        server.tick += SharedWaypointServer.MUTATION_RATE_TICKS;
        int[] ownerDeleteObserved = observeCommittedStateAtInfo(server, owner, 4, null);
        boolean ownerDeleteChanged;
        try {
            ownerDeleteChanged = SharedWaypointServer.apply(server, owner,
                    SharedWaypointRequest.delete(original.id()), false, true, ignored -> true).changed();
        } finally {
            OpenMapDeltaCommon.LOGGER.infoObserver = null;
        }
        check(ownerDeleteChanged && ownerDeleteObserved[0] == 1, "owner DELETE commits and emits once after removal");
        var ownerDeleteRetry = SharedWaypointServer.apply(server, owner,
                SharedWaypointRequest.delete(original.id()), false, true, ignored -> true);
        check(ownerDeleteRetry.feedback().messageKey().equals("odysseymap.shared.rate_limited")
                        && OpenMapDeltaCommon.LOGGER.infos.size() == 1,
                "same-tick apply DELETE retry does not duplicate its event");
        onlyMutationEvent(afterAdminUpdate, "shared_waypoint_deleted", owner);

        clearEvents();
        server.tick += SharedWaypointServer.MUTATION_RATE_TICKS;
        check(SharedWaypointServer.apply(server, owner,
                SharedWaypointRequest.create("minecraft:overworld", 7, 68, 9, "second point", 0, "", ""),
                false, true, ignored -> true).changed(), "second delete fixture create commits");
        SharedWaypoint second = loadOnlyWaypoint(server);
        onlyEvent(second);

        clearEvents();
        var encodedDelete = SharedWaypointRequest.delete(second.id()).encode();
        server.tick += SharedWaypointServer.MUTATION_RATE_TICKS;
        int[] adminDeleteObserved = observeCommittedStateAtInfo(server, admin, 6, null);
        boolean adminDeleteChanged;
        try {
            adminDeleteChanged = SharedWaypointServer.applyEncoded(server, admin, encodedDelete, true, true,
                    ignored -> true).changed();
        } finally {
            OpenMapDeltaCommon.LOGGER.infoObserver = null;
        }
        check(adminDeleteChanged && adminDeleteObserved[0] == 1, "admin applyEncoded DELETE commits and emits once");
        onlyMutationEvent(second, "shared_waypoint_deleted", admin);
        printCapturedEvent("DELETE");
        check(SharedWaypointServer.applyEncoded(server, admin, encodedDelete, true, true, ignored -> true)
                        .feedback().messageKey().equals("odysseymap.shared.rate_limited")
                        && OpenMapDeltaCommon.LOGGER.infos.size() == 1,
                "same-tick applyEncoded DELETE retry does not duplicate its event");
    }

    private static void mutationStoreFailuresRollback(Path directory) throws Exception {
        MinecraftServer server = server(directory);
        ServerPlayer owner = new ServerPlayer(); owner.setName("Alice");
        ServerPlayer admin = new ServerPlayer(); admin.setName("Admin");
        var create = SharedWaypointRequest.create("minecraft:overworld", 30, 70, 40, "persisted", 0, "", "");
        check(SharedWaypointServer.apply(server, owner, create, false, true, ignored -> true).changed(),
                "rollback fixture create commits");
        SharedWaypoint original = loadOnlyWaypoint(server);
        Path store = directory.resolve("data").resolve(SharedWaypointServer.FILE_NAME);
        Path backup = store.resolveSibling("shared-waypoint-before-block.json");
        byte[] originalBytes = Files.readAllBytes(store);
        clearEvents();

        blockStore(store, backup);
        server.tick += SharedWaypointServer.MUTATION_RATE_TICKS;
        var update = SharedWaypointRequest.update(original.id(), "minecraft:overworld", 31, 71, 41,
                "must not persist", 0, "", "");
        var updateFailure = SharedWaypointServer.apply(server, owner, update, false, true, ignored -> true);
        check(!updateFailure.changed() && updateFailure.feedback().messageKey().equals("odysseymap.shared.storage_error"),
                "blocked atomic store path rejects UPDATE");
        check(Files.readAllBytes(backup).length == originalBytes.length
                        && java.util.Arrays.equals(Files.readAllBytes(backup), originalBytes),
                "failed UPDATE preserves original on-disk state");
        checkPublishedState(server, owner, 1, original, "failed UPDATE rolls back state");
        check(OpenMapDeltaCommon.LOGGER.infos.isEmpty(), "failed UPDATE emits no event");

        restoreStore(store, backup);
        blockStore(store, backup);
        server.tick += SharedWaypointServer.MUTATION_RATE_TICKS;
        var deleteFailure = SharedWaypointServer.applyEncoded(server, admin,
                SharedWaypointRequest.delete(original.id()).encode(), true, true, ignored -> true);
        check(!deleteFailure.changed() && deleteFailure.feedback().messageKey().equals("odysseymap.shared.storage_error"),
                "blocked atomic store path rejects DELETE");
        check(java.util.Arrays.equals(Files.readAllBytes(backup), originalBytes),
                "failed DELETE preserves original on-disk state");
        checkPublishedState(server, owner, 1, original, "failed DELETE rolls back state");
        check(OpenMapDeltaCommon.LOGGER.infos.isEmpty(), "failed DELETE emits no event");
        restoreStore(store, backup);
    }

    private static void mutationActorEscaping(Path directory) throws Exception {
        MinecraftServer server = server(directory);
        ServerPlayer owner = new ServerPlayer(); owner.setName("Alice");
        ServerPlayer admin = new ServerPlayer();
        String separators = Character.toString(0x2028) + Character.toString(0x2029);
        String actorName = "Admin \"\\" + separators;
        admin.setName(actorName);
        var create = SharedWaypointRequest.create("minecraft:overworld", 2, 64, 3, "owned", 0, "", "");
        check(SharedWaypointServer.apply(server, owner, create, false, true, ignored -> true).changed(),
                "actor-escaping fixture create commits");
        SharedWaypoint original = loadOnlyWaypoint(server);
        clearEvents();

        var update = SharedWaypointRequest.update(original.id(), "minecraft:the_nether", 4, 70, 5,
                "escaped actor", 0, "", "");
        server.tick += SharedWaypointServer.MUTATION_RATE_TICKS;
        check(SharedWaypointServer.applyEncoded(server, admin, update.encode(), true, true, ignored -> true).changed(),
                "escaped-name admin UPDATE commits");
        SharedWaypoint updated = new SharedWaypoint(original.id(), original.ownerId(), original.ownerName(),
                update.dimension(), update.x(), update.y(), update.z(), update.label(), update.color(),
                update.iconId(), update.category());
        JsonObject event = onlyMutationEvent(updated, "shared_waypoint_updated", admin);
        String payload = eventPayload();
        check(event.get("actor_name").getAsString().equals(actorName),
                "mutation event round-trips actor quote, backslash, and line separators");
        check(payload.contains("\\\"") && payload.contains("\\\\")
                        && payload.contains("\\u2028") && payload.contains("\\u2029"),
                "mutation actor text is safely escaped in compact JSON");
    }

    private static void mutationRevisionOverflowDoesNotEmit(Path directory) throws Exception {
        MinecraftServer server = server(directory);
        ServerPlayer owner = new ServerPlayer(); owner.setName("Alice");
        SharedWaypoint saved = new SharedWaypoint(UUID.randomUUID(), owner.getUUID(), "Alice",
                "minecraft:overworld", 5, 64, 6, "overflow", 0, "", "");
        Path store = directory.resolve("data").resolve(SharedWaypointServer.FILE_NAME);
        SharedWaypointStore.save(store, new SharedWaypointStore.Stored(Long.MAX_VALUE, java.util.List.of(saved)));
        clearEvents();

        var update = SharedWaypointRequest.update(saved.id(), "minecraft:overworld", 6, 65, 7,
                "cannot commit", 0, "", "");
        var updateFailure = SharedWaypointServer.apply(server, owner, update, false, true, ignored -> true);
        check(!updateFailure.changed() && updateFailure.feedback().messageKey().equals("odysseymap.shared.storage_error"),
                "revision overflow rejects UPDATE as storage error");
        checkPublishedState(server, owner, Long.MAX_VALUE, saved, "overflow UPDATE leaves published state unchanged");
        check(OpenMapDeltaCommon.LOGGER.infos.isEmpty(), "revision overflow UPDATE emits no event");

        server.tick += SharedWaypointServer.MUTATION_RATE_TICKS;
        var deleteFailure = SharedWaypointServer.apply(server, owner, SharedWaypointRequest.delete(saved.id()),
                false, true, ignored -> true);
        check(!deleteFailure.changed() && deleteFailure.feedback().messageKey().equals("odysseymap.shared.storage_error"),
                "revision overflow rejects DELETE as storage error");
        checkPublishedState(server, owner, Long.MAX_VALUE, saved, "overflow DELETE leaves published state unchanged");
        check(OpenMapDeltaCommon.LOGGER.infos.isEmpty(), "revision overflow DELETE emits no event");
    }

    private static void initializeAndSnapshotDoNotEmit(Path directory) throws Exception {
        MinecraftServer original = server(directory);
        ServerPlayer owner = new ServerPlayer();
        check(SharedWaypointServer.apply(original, owner,
                SharedWaypointRequest.create("minecraft:overworld", 8, 70, 9, "persisted", 0, "", ""),
                false, true, ignored -> true).changed(), "snapshot setup create commits");
        clearEvents();

        MinecraftServer restored = new MinecraftServer(original.worldId);
        restored.directory = original.directory;
        SharedWaypointServer.initialize(restored);
        check(SharedWaypointServer.waypointCount(restored) == 1, "initialize loads committed waypoint from store");
        var snapshots = new ArrayList<SharedWaypointSnapshot>();
        SharedWaypointServer.sendSnapshot(restored, owner, true, false, (player, snapshot) -> snapshots.add(snapshot));
        check(snapshots.size() == 1 && snapshots.get(0).waypoints().size() == 1,
                "snapshot sends the loaded shared waypoint");
        check(OpenMapDeltaCommon.LOGGER.infos.isEmpty(), "initialize and snapshot emit no creation event");
    }

    private static JsonObject onlyEvent(SharedWaypoint saved) {
        return onlyStructuredEvent(saved, "shared_waypoint_created", null);
    }

    private static JsonObject onlyMutationEvent(SharedWaypoint saved, String expectedType, ServerPlayer actor) {
        return onlyStructuredEvent(saved, expectedType, actor);
    }

    private static JsonObject onlyStructuredEvent(SharedWaypoint saved, String expectedType, ServerPlayer actor) {
        check(OpenMapDeltaCommon.LOGGER.infos.size() == 1, "successful mutation emits exactly one INFO event");
        OpenMapDeltaCommon.InfoMessage info = OpenMapDeltaCommon.LOGGER.infos.get(0);
        check(info.template().equals("OPENMAPMC_EVENT {}") && info.arguments().length == 1
                        && info.arguments()[0] instanceof String,
                "event uses the stable logger template and one compact JSON argument");
        JsonObject event = JsonParser.parseString((String) info.arguments()[0]).getAsJsonObject();
        Set<String> expectedFields = actor == null
                ? Set.of("schema_version", "event", "waypoint_id", "creator_uuid", "creator_name",
                        "name", "dimension", "x", "y", "z")
                : Set.of("schema_version", "event", "waypoint_id", "creator_uuid", "creator_name",
                        "name", "dimension", "x", "y", "z", "actor_uuid", "actor_name");
        check(event.keySet().equals(expectedFields),
                "event contains exactly the schema fields");
        check(event.get("schema_version").getAsInt() == 1
                        && event.get("event").getAsString().equals(expectedType),
                "event includes versioned shared-waypoint mutation identity");
        check(event.get("waypoint_id").getAsString().equals(saved.id().toString())
                        && event.get("creator_uuid").getAsString().equals(saved.ownerId().toString())
                        && event.get("creator_name").getAsString().equals(saved.ownerName())
                        && event.get("name").getAsString().equals(saved.label())
                        && event.get("dimension").getAsString().equals(saved.dimension())
                        && event.get("x").getAsInt() == saved.x()
                        && event.get("y").getAsInt() == saved.y()
                        && event.get("z").getAsInt() == saved.z(),
                "event values exactly match the successfully stored waypoint");
        if (actor != null) {
            check(event.get("actor_uuid").getAsString().equals(actor.getUUID().toString())
                            && event.get("actor_name").getAsString().equals(actor.getName().getString()),
                    "event actor fields match the authenticated mutation actor");
        }
        check(event.get("x").getAsJsonPrimitive().isNumber()
                        && event.get("y").getAsJsonPrimitive().isNumber()
                        && event.get("z").getAsJsonPrimitive().isNumber(),
                "coordinates are JSON number primitives");
        String payload = (String) info.arguments()[0];
        check(!payload.contains(": ") && !payload.contains(", "), "event JSON is compact");
        check(!payload.contains("\r") && !payload.contains("\n")
                        && !payload.contains(Character.toString(0x2028))
                        && !payload.contains(Character.toString(0x2029)),
                "event payload contains no literal line separators");
        return event;
    }

    private static int[] observeCommittedStateAtInfo(MinecraftServer server, ServerPlayer actor,
                                                      long revision, SharedWaypoint expectedWaypoint) {
        int[] observed = {0};
        OpenMapDeltaCommon.LOGGER.infoObserver = () -> {
            observed[0]++;
            checkState(server, actor, revision, expectedWaypoint, "state is committed before mutation INFO");
        };
        return observed;
    }

    private static void checkState(MinecraftServer server, ServerPlayer actor, long revision,
                                   SharedWaypoint expectedWaypoint, String description) {
        var expectedWaypoints = expectedWaypoint == null ? java.util.List.<SharedWaypoint>of()
                : java.util.List.of(expectedWaypoint);
        var loaded = SharedWaypointStore.load(server.directory.resolve("data").resolve(SharedWaypointServer.FILE_NAME));
        check(loaded.valid() && loaded.stored().revision() == revision
                        && loaded.stored().waypoints().equals(expectedWaypoints),
                description + " in persisted store");
        checkPublishedState(server, actor, revision, expectedWaypoint, description);
    }

    private static void checkPublishedState(MinecraftServer server, ServerPlayer actor, long revision,
                                            SharedWaypoint expectedWaypoint, String description) {
        var expectedWaypoints = expectedWaypoint == null ? java.util.List.<SharedWaypoint>of()
                : java.util.List.of(expectedWaypoint);
        check(SharedWaypointServer.waypointCount(server) == expectedWaypoints.size(),
                description + " in in-memory count");
        var snapshots = new ArrayList<SharedWaypointSnapshot>();
        SharedWaypointServer.sendSnapshot(server, actor, true, false, (player, snapshot) -> snapshots.add(snapshot));
        check(snapshots.size() == 1 && snapshots.get(0).revision() == revision
                        && snapshots.get(0).waypoints().equals(expectedWaypoints),
                description + " in published snapshot");
    }

    private static void blockStore(Path store, Path backup) throws Exception {
        Files.move(store, backup);
        Files.createDirectory(store);
        Files.writeString(store.resolve("blocker"), "prevent replacement");
    }

    private static void restoreStore(Path store, Path backup) throws Exception {
        Files.delete(store.resolve("blocker"));
        Files.delete(store);
        Files.move(backup, store);
    }

    private static void printCapturedEvent(String operation) {
        OpenMapDeltaCommon.InfoMessage message = OpenMapDeltaCommon.LOGGER.infos.get(0);
        printUtf8("captured " + operation + " INFO: "
                + message.template().replace("{}", (String) message.arguments()[0]));
    }

    private static SharedWaypoint loadOnlyWaypoint(MinecraftServer server) throws Exception {
        var loaded = SharedWaypointStore.load(server.directory.resolve("data").resolve(SharedWaypointServer.FILE_NAME));
        check(loaded.valid() && loaded.stored().waypoints().size() == 1, "stored waypoint is readable after success");
        return loaded.stored().waypoints().get(0);
    }

    private static String eventPayload() {
        return (String) OpenMapDeltaCommon.LOGGER.infos.get(0).arguments()[0];
    }

    private static void printUtf8(String line) {
        PrintStream output = new PrintStream(new FileOutputStream(FileDescriptor.out), true, StandardCharsets.UTF_8);
        output.println(line);
        output.flush();
    }

    private static MinecraftServer server(Path directory) throws Exception {
        Files.createDirectories(directory);
        MinecraftServer server = new MinecraftServer();
        server.directory = directory;
        return server;
    }

    private static void clearEvents() { OpenMapDeltaCommon.LOGGER.clearInfo(); }
    private static void expectInvalid(Runnable action) {
        checks++;
        try { action.run(); } catch (IllegalArgumentException expected) { return; }
        throw new AssertionError("Invalid newline text was accepted");
    }
    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
}
