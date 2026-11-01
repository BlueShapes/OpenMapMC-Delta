// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.server;

import net.aosankaku.openmapdelta.platform.DataPaths;
import net.aosankaku.openmapdelta.shared.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import com.google.gson.JsonParser;
import java.nio.file.*;
import java.util.*;
import net.aosankaku.openmapdelta.server.ServerConfigSnapshot.Result;

/** Exercises the real service, persistence, codecs, and shared creation policy with only Minecraft API stubs. */
public final class ServerConfigTest {
    private static int checks;
    private static void check(boolean value, String message) { checks++; if (!value) throw new AssertionError(message); }
    private static void rejects(Runnable operation) {
        checks++;
        try { operation.run(); } catch (IllegalArgumentException expected) { return; }
        throw new AssertionError("Invalid input accepted");
    }
    public static void main(String[] args) throws Exception {
        codecs();
        DataPaths.directory = Files.createTempDirectory("openmap-config-tests-");
        defaultsAndPersistence();
        permissionsAndLiveChanges();
        sharedLimit();
        malformedAndRollback();
        System.out.println("ServerConfigTest: " + checks + " checks passed (codecs, permission changes, conflicts, rollback, live cave/URL/waypoint limits).");
    }

    private static void codecs() {
        for (int count : new int[]{0, 1, 4096}) for (boolean cave : new boolean[]{false, true}) {
            for (boolean web : new boolean[]{false, true}) {
                var values = new ServerConfigValues(cave, web, "https://map.example/地図", count);
                var request = new ServerConfigRequest(3, values);
                byte[] encoded = request.encode();
                check(request.equals(ServerConfigRequest.decode(encoded)), "Request roundtrip");
                for (int size = 0; size < encoded.length; size++) {
                    final byte[] truncated = Arrays.copyOf(encoded, size);
                    rejects(() -> ServerConfigRequest.decode(truncated));
                }
                rejects(() -> ServerConfigRequest.decode(Arrays.copyOf(encoded, encoded.length + 1)));
                for (Result result : Result.values()) {
                    var snapshot = new ServerConfigSnapshot(4, true, values, result);
                    check(snapshot.equals(ServerConfigSnapshot.decode(snapshot.encode())), "Snapshot roundtrip");
                }
            }
        }
        var empty = new ServerConfigSnapshot(1, false, null, Result.DENIED);
        check(empty.equals(ServerConfigSnapshot.decode(empty.encode())), "Unauthorized response roundtrip");
        check(empty.encode().length == 11, "Unauthorized response must not contain settings");
        rejects(() -> new ServerConfigValues(true, false, "", -1));
        rejects(() -> new ServerConfigValues(true, false, "", 4097));
        rejects(() -> new ServerConfigValues(true, true, "", 4096));
        for (String url : new String[]{"javascript:alert(1)", "https://user:secret@host/", "https://map.example/\n", "file:///tmp/map", " ", "https://map.example/" + "a".repeat(2048)}) {
            rejects(() -> new ServerConfigValues(true, false, url, 4096));
        }
        rejects(() -> new ServerConfigRequest(-1, new ServerConfigValues(true, false, "", 0)));
        rejects(() -> new ServerConfigSnapshot(0, false, new ServerConfigValues(true, false, "", 0), Result.NONE));
        rejects(() -> ServerConfigRequest.decode(new byte[ServerConfigRequest.MAX_BYTES + 1]));
        rejects(() -> ServerConfigSnapshot.decode(new byte[ServerConfigSnapshot.MAX_BYTES + 1]));
        var valid = new ServerConfigRequest(0, new ServerConfigValues(true, false, "", 0)).encode();
        valid[9] = 2;
        rejects(() -> ServerConfigRequest.decode(valid));
        byte[] badResult = empty.encode(); badResult[10] = 127;
        rejects(() -> ServerConfigSnapshot.decode(badResult));
        byte[] snapshotBytes = new ServerConfigSnapshot(1, true,
                new ServerConfigValues(true, false, "", 4096), Result.NONE).encode();
        for (int size = 0; size < snapshotBytes.length; size++) {
            final byte[] truncated = Arrays.copyOf(snapshotBytes, size);
            rejects(() -> ServerConfigSnapshot.decode(truncated));
        }
        rejects(() -> ServerConfigSnapshot.decode(Arrays.copyOf(snapshotBytes, snapshotBytes.length + 1)));
        byte[] badUtf8 = new ServerConfigRequest(1, new ServerConfigValues(true, false, "https://map.example/", 0)).encode();
        badUtf8[15] = (byte) 0xff;
        rejects(() -> ServerConfigRequest.decode(badUtf8));
    }

    private static void defaultsAndPersistence() throws Exception {
        ServerConfig.load();
        check(ServerConfig.editable() && ServerConfig.isCaveAllowed(), "Missing file defaults");
        check(ServerConfig.maxSharedWaypoints() == 4096 && ServerConfig.webMapUrl().isEmpty(), "Compatible defaults");
        Files.writeString(ServerConfig.path(), "{\"allowCaveMap\":true,\"futureSetting\":{\"value\":42},\"graveBlockIds\":[\"example:grave\"]}");
        ServerConfig.load();
        long revision = ServerConfig.revision();
        var candidate = new ServerConfigValues(false, true, "https://map.example/", 12);
        check(ServerConfig.update(revision, candidate) == Result.SAVED, "Save valid values");
        check(!ServerConfig.isCaveAllowed() && ServerConfig.maxSharedWaypoints() == 12, "Immediately applied policy");
        check(ServerConfig.webMapUrl().orElseThrow().toString().equals(candidate.webMapUrl()), "Immediately applied URL");
        var saved = JsonParser.parseString(Files.readString(ServerConfig.path())).getAsJsonObject();
        check(saved.getAsJsonObject("futureSetting").get("value").getAsInt() == 42, "Preserve unknown settings");
        check(saved.getAsJsonArray("graveBlockIds").get(0).getAsString().equals("example:grave"), "Preserve grave IDs");
        check(ServerConfig.update(revision, new ServerConfigValues(true, false, "", 4096)) == Result.CONFLICT, "Stale admin cannot overwrite");
        check(ServerConfig.values().equals(candidate), "Conflict preserves runtime state");
        ServerConfig.load();
        check(ServerConfig.values().equals(candidate), "Persistence reload");
        long unchanged = ServerConfig.revision();
        check(ServerConfig.update(unchanged, candidate) == Result.SAVED && ServerConfig.revision() == unchanged, "No-op has no revision/disk churn");
        String longUrl = "https://map.example/" + "地".repeat(2000);
        Files.writeString(ServerConfig.path(), "{\"allowCaveMap\":true,\"webMapEnabled\":true,\"webMapUrl\":\"" + longUrl + "\"}");
        ServerConfig.load();
        check(ServerConfig.editable() && ServerConfig.isCaveAllowed() && ServerConfig.webMapUrl().isPresent(), "Legacy long Unicode URL remains valid");
        var unicode = new ServerConfigSnapshot(ServerConfig.revision(), true, ServerConfig.values(), Result.NONE);
        check(unicode.equals(ServerConfigSnapshot.decode(unicode.encode())), "Maximum length Unicode URL fits bounded transport");
    }

    private static void permissionsAndLiveChanges() throws Exception {
        MinecraftServer server = new MinecraftServer();
        var admin = new ServerPlayer(); admin.operator = true;
        var user = new ServerPlayer();
        server.players.addAll(List.of(admin, user));
        List<ServerConfigSnapshot> replies = new ArrayList<>();
        int[] broadcasts = {0};
        Runnable broadcast = () -> broadcasts[0]++;
        ServerConfigService.syncIfChanged(server, user, replies::add);
        check(!replies.get(0).administrator() && replies.get(0).values() == null, "No settings leaked to ordinary player");
        ServerConfigService.syncIfChanged(server, user, replies::add);
        check(replies.size() == 1, "Unchanged state suppressed");
        var wanted = new ServerConfigValues(true, false, "", 3);
        byte[] request = new ServerConfigRequest(ServerConfig.revision(), wanted).encode();
        ServerConfigService.handle(server, user, request, replies::add, broadcast);
        check(replies.get(replies.size()-1).result() == Result.DENIED && broadcasts[0] == 0, "Server denies unauthorized mutation");
        check(!ServerConfig.values().equals(wanted), "Denied request no state mutation");
        ServerConfigService.syncIfChanged(server, admin, replies::add);
        check(replies.get(replies.size()-1).administrator(), "OP sees controls");
        admin.operator = false;
        ServerConfigService.handle(server, admin, request, replies::add, broadcast);
        check(replies.get(replies.size()-1).result() == Result.DENIED, "Demotion rechecked on mutation");
        ServerConfigService.syncIfChanged(server, admin, replies::add);
        check(!replies.get(replies.size()-1).administrator(), "Demotion reflected without reconnect");
        server.owner = admin.getUUID();
        check(ServerConfigService.canManage(server, admin), "Singleplayer/LAN owner without cheats");
        server.tick = 20;
        ServerConfigService.handle(server, admin, request, replies::add, broadcast);
        check(ServerConfig.values().equals(wanted) && broadcasts[0] == 1, "Owner save immediately broadcasts");
        ServerConfigService.handle(server, admin, request, replies::add, broadcast);
        check(replies.get(replies.size()-1).result() == Result.RATE_LIMITED, "Rate limit includes repeated request");
        server.tick += 10;
        ServerConfigService.handle(server, admin, request, replies::add, broadcast);
        check(replies.get(replies.size()-1).result() == Result.CONFLICT, "Concurrent stale edit rejected");
        server.tick += 10;
        ServerConfigService.handle(server, admin, new byte[]{99}, replies::add, broadcast);
        check(replies.get(replies.size()-1).result() == Result.INVALID, "Malformed bounded request rejected");
        server.tick += 10;
        var publicMap = new ServerConfigValues(false, true, "https://new-map.example/", 2);
        ServerConfigService.handle(server, admin, new ServerConfigRequest(ServerConfig.revision(), publicMap).encode(), replies::add, broadcast);
        check(admin.notices == 1 && user.notices == 1 && broadcasts[0] == 2, "URL change announces immediately to all players");
        server.tick += 10;
        ServerConfigService.handle(server, admin, new ServerConfigRequest(ServerConfig.revision(), publicMap).encode(), replies::add, broadcast);
        check(admin.notices == 1 && broadcasts[0] == 2, "No duplicate announcement on no-op");
        ServerConfigService.playerJoined(server, admin);
        ServerConfigService.syncIfChanged(server, admin, replies::add);
        check(replies.get(replies.size()-1).result() == Result.NONE, "Reconnect refreshes snapshot");
        ServerConfigService.reset(server);
    }

    private static void sharedLimit() throws Exception {
        MinecraftServer server = new MinecraftServer(); server.directory = Files.createTempDirectory("openmap-limit-tests-");
        var owner = new ServerPlayer(); server.players.add(owner);
        ServerConfig.update(ServerConfig.revision(), new ServerConfigValues(true, false, "", 1));
        var create = SharedWaypointRequest.create("minecraft:overworld", 1, 64, 2, "First", 0xff00ff, "", "");
        check(SharedWaypointServer.apply(server, owner, create, false, true, d -> true).changed(), "Create below configured limit");
        server.tick += 5;
        check(!SharedWaypointServer.apply(server, owner, create, false, true, d -> true).changed(), "Enforce configured limit on server");
        ServerConfig.update(ServerConfig.revision(), new ServerConfigValues(true, false, "", 0));
        check(SharedWaypointServer.waypointCount(server) == 1, "Lowering limit does not delete existing points");
        List<SharedWaypointSnapshot> snapshots = new ArrayList<>();
        SharedWaypointServer.sendSnapshot(server, owner, true, false, (p, s) -> snapshots.add(s));
        UUID id = snapshots.get(0).waypoints().get(0).id();
        server.tick += 5;
        var update = SharedWaypointRequest.update(id, "minecraft:overworld", 5, 70, 8, "Edited", 0xff00ff, "", "");
        check(SharedWaypointServer.apply(server, owner, update, false, true, d -> true).changed(), "Editing allowed above reduced limit");
        server.tick += 5;
        check(SharedWaypointServer.apply(server, owner, SharedWaypointRequest.delete(id), false, true, d -> true).changed(), "Deletion allowed with zero limit");
        server.tick += 5;
        check(!SharedWaypointServer.apply(server, owner, create, false, true, d -> true).changed(), "Zero disallows new shared waypoints");
        check(SharedWaypointStore.load(server.directory.resolve("data").resolve(SharedWaypointServer.FILE_NAME)).stored().waypoints().isEmpty(), "Existing store format maintained");
    }

    private static void malformedAndRollback() throws Exception {
        var before = ServerConfig.values(); long revision = ServerConfig.revision();
        Path original = ServerConfig.path(); Path backup = original.resolveSibling("server-backup.json");
        Files.move(original, backup); Files.createDirectory(original);
        check(ServerConfig.update(revision, new ServerConfigValues(false, false, "", 9)) == Result.STORAGE_ERROR, "Save failure reported");
        check(ServerConfig.revision() == revision && ServerConfig.values().equals(before), "Failed persistence cannot change live state");
        Files.delete(original); Files.move(backup, original);
        Files.writeString(original, "{broken"); ServerConfig.load();
        check(!ServerConfig.editable() && !ServerConfig.isCaveAllowed(), "Corrupt policy fails closed and disables editing");
        check(ServerConfig.update(ServerConfig.revision(), before) == Result.STORAGE_ERROR, "Cannot overwrite corrupt config");
        check(Files.readString(original).equals("{broken"), "Corrupt config preserved");
        Files.writeString(original, "{\"allowCaveMap\":true,\"webMapEnabled\":true,\"webMapUrl\":\"invalid\"}"); ServerConfig.load();
        check(ServerConfig.editable() && ServerConfig.isCaveAllowed() && ServerConfig.webMapUrl().isEmpty(), "Bad public URL does not disable cave policy");
        Files.writeString(original, "{\"allowCaveMap\":true,\"maxSharedWaypoints\":1.5}"); ServerConfig.load();
        check(!ServerConfig.editable() && !ServerConfig.isCaveAllowed(), "Fractional count rejected");
    }
}
