// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.shared;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/** Focused executable checks for the bounded shared protocol, authorization rule, and world store. */
public final class SharedWaypointProtocolStoreTest {
    private static int checks;

    public static void main(String[] args) throws Exception {
        protocolRoundTripsAndRejectsMalformedInput();
        authorizationUsesAuthenticatedIdentity();
        storePersistsAndFailsClosedOnMalformedFiles();
        System.out.println("SharedWaypointProtocolStoreTest: " + checks + " checks passed");
    }

    private static void protocolRoundTripsAndRejectsMalformedInput() throws Exception {
        var create = SharedWaypointRequest.create("minecraft:overworld", -24, 70, 31,
                "上南駅🚆", 0xFF44AA, "item:minecraft:minecart", "Create線");
        var decoded = SharedWaypointRequest.decode(create.encode());
        check(decoded.operation() == SharedWaypointRequest.Operation.CREATE && decoded.label().equals("上南駅🚆")
                && decoded.category().equals("Create線"), "create request round trips full Unicode");

        UUID id = UUID.randomUUID();
        check(SharedWaypointRequest.decode(SharedWaypointRequest.update(id, "minecraft:the_nether", 1, 40, 2,
                "更新", 0xFFFFFF, "", "").encode()).id().equals(id), "update request carries its target id");
        check(SharedWaypointRequest.decode(SharedWaypointRequest.delete(id).encode()).operation()
                == SharedWaypointRequest.Operation.DELETE, "delete request round trips");
        rejects(() -> SharedWaypointRequest.decode(Arrays.copyOf(create.encode(), create.encode().length + 1)),
                "request rejects trailing bytes");
        byte[] badOperation = create.encode(); badOperation[1] = (byte) 99;
        rejects(() -> SharedWaypointRequest.decode(badOperation), "request rejects unknown operations");
        rejects(() -> SharedWaypointRequest.decode(new byte[SharedWaypointRequest.MAX_BYTES + 1]), "request rejects oversized packets");
        rejects(() -> SharedWaypointRequest.create("minecraft:overworld", 0, 64, 0, "\uD800", 0, "", ""),
                "request rejects malformed UTF-16");
        rejectsChecked(() -> SharedWaypointRequest.decode(malformedUtf8Request()), "request rejects malformed UTF-8");

        UUID owner = UUID.randomUUID(), world = UUID.randomUUID();
        var point = new SharedWaypoint(id, owner, "AoSankaku", "minecraft:overworld", 12, 64, 34,
                "駅🚆", 0xFF00FF, "", "共有");
        var snapshot = new SharedWaypointSnapshot(world, 8, true, false, 0, 1, List.of(point));
        check(SharedWaypointSnapshot.decode(snapshot.encode()).waypoints().equals(List.of(point)), "snapshot round trips");
        rejects(() -> SharedWaypointSnapshot.decode(Arrays.copyOf(snapshot.encode(), snapshot.encode().length + 1)),
                "snapshot rejects trailing bytes");
        rejects(() -> new SharedWaypointSnapshot(world, 0, true, false, 64, 65, List.of()), "snapshot bounds page count");
        check(SharedWaypointFeedback.decode(SharedWaypointFeedback.failure("odysseymap.shared.denied").encode())
                .messageKey().equals("odysseymap.shared.denied"), "known feedback key round trips");
        rejects(() -> new SharedWaypointFeedback(false, "minecraft:op.granted"), "feedback rejects arbitrary translation keys");
    }

    private static byte[] malformedUtf8Request() throws IOException {
        var bytes = new ByteArrayOutputStream();
        var out = new DataOutputStream(bytes);
        out.writeByte(1); out.writeByte(0); // Version, CREATE.
        string(out, "minecraft:overworld");
        out.writeInt(0); out.writeInt(64); out.writeInt(0);
        out.writeShort(1); out.writeByte(0xC3); // A truncated multi-byte UTF-8 sequence.
        out.writeInt(0xFFFFFF);
        out.writeShort(0); out.writeShort(0);
        return bytes.toByteArray();
    }

    private static void string(DataOutputStream out, String value) throws IOException {
        byte[] bytes = value.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        out.writeShort(bytes.length); out.write(bytes);
    }

    private static void authorizationUsesAuthenticatedIdentity() {
        UUID owner = UUID.randomUUID(), other = UUID.randomUUID();
        var point = new SharedWaypoint(UUID.randomUUID(), owner, "owner", "minecraft:overworld",
                0, 64, 0, "station", 0xFFFFFF, "", "");
        check(SharedWaypointAccess.canManage(point, owner, false), "creator may manage their own point");
        check(!SharedWaypointAccess.canManage(point, other, false), "another non-admin cannot manage the point");
        check(SharedWaypointAccess.canManage(point, other, true), "server administrator may manage another player's point");
        check(!SharedWaypointAccess.canManage(point, null, true), "missing authenticated identity is rejected");
    }

    private static void storePersistsAndFailsClosedOnMalformedFiles() throws Exception {
        Path directory = Files.createTempDirectory("odysseymap-shared-test-");
        Path file = directory.resolve("odysseymap-shared-waypoints.json");
        var owner = UUID.randomUUID();
        var point = new SharedWaypoint(UUID.randomUUID(), owner, "AoSankaku", "minecraft:overworld",
                12, 64, 34, "上南駅🚆", 0xFF00FF, "item:minecraft:minecart", "共有");
        check(!SharedWaypointStore.load(file).exists() && SharedWaypointStore.load(file).valid(), "missing storage starts empty");
        SharedWaypointStore.save(file, new SharedWaypointStore.Stored(1, List.of(point)));
        var loaded = SharedWaypointStore.load(file);
        check(loaded.valid() && loaded.stored().revision() == 1 && loaded.stored().waypoints().equals(List.of(point)),
                "store writes and reloads shared data");

        String validJson = Files.readString(file);
        assertMalformedPreserved(file, validJson.replace("\"revision\":1", "\"revision\":1.5"), "fractional revision");
        assertMalformedPreserved(file, validJson.replace("\"x\":12", "\"x\":12.5"), "fractional coordinate");
        assertMalformedPreserved(file, validJson.replace("\"color\":16711935", "\"color\":2147483648"), "overflowing color");

        Files.writeString(file, validJson);
        Path blockedParent = directory.resolve("not-a-directory");
        Files.writeString(blockedParent, "keep");
        rejectsIo(() -> SharedWaypointStore.save(blockedParent.resolve("store.json"),
                new SharedWaypointStore.Stored(2, List.of(point))), "storage failure is reported");
        check(SharedWaypointStore.load(file).valid() && SharedWaypointStore.load(file).stored().revision() == 1,
                "failed unrelated write leaves the committed store intact");
    }

    private static void assertMalformedPreserved(Path file, String malformed, String description) throws Exception {
        Files.writeString(file, malformed);
        byte[] before = Files.readAllBytes(file);
        var result = SharedWaypointStore.load(file);
        check(!result.valid() && result.failure() != null, "store rejects " + description);
        check(Arrays.equals(before, Files.readAllBytes(file)), "store does not rewrite " + description);
    }

    private static void rejects(Runnable action, String message) {
        checks++;
        try { action.run(); } catch (IllegalArgumentException expected) { return; }
        throw new AssertionError(message);
    }

    @FunctionalInterface private interface IoAction { void run() throws IOException; }
    private static void rejectsIo(IoAction action, String message) throws IOException {
        checks++;
        try { action.run(); } catch (IOException expected) { return; }
        throw new AssertionError(message);
    }

    @FunctionalInterface private interface CheckedAction { void run() throws Exception; }
    private static void rejectsChecked(CheckedAction action, String message) throws Exception {
        checks++;
        try { action.run(); } catch (IllegalArgumentException expected) { return; }
        throw new AssertionError(message);
    }

    private static void check(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }
}
