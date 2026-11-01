// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.world;

import net.aosankaku.openmapdelta.network.ClientWorldIdentityState;
import net.aosankaku.openmapdelta.network.WorldIdentityMessage;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.UUID;

public final class WorldIdentityTest {
    private static int checks;
    private static final UUID A = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final UUID B = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");

    public static void main(String[] args) throws Exception {
        check(CacheWorldIdentity.normalizeServerAddress("example.com").equals("example.com:25565"), "default port has a canonical form");
        sameAddress("example.com", "example.com:25565");
        sameAddress(" EXAMPLE.com. ", "example.com:25565");
        sameAddress("example.com:025565", "example.com");
        sameAddress("[2001:DB8::1]", "[2001:db8::1]:25565");
        sameAddress("例え.テスト", "xn--r8jz45g.xn--zckzah:25565");
        check(!CacheWorldIdentity.multiplayer("example.com:25566", 42, null).equals(CacheWorldIdentity.multiplayer("example.com", 42, null)), "different ports remain separate servers");
        check(!CacheWorldIdentity.multiplayer("alias.example.com", 42, null).equals(CacheWorldIdentity.multiplayer("example.com", 42, null)), "different DNS names are not merged through resolution");
        for (String invalid : new String[]{"", "  ", "example.com:0", "example.com:65536", "example.com:abc", "../world", "https://example.com", "example.com/path", "example.com\\world", "[example.com]", "[bad:host]"}) {
            check(CacheWorldIdentity.normalizeServerAddress(invalid).isEmpty(), "malformed server addresses do not create a persistent identity");
            check(CacheWorldIdentity.multiplayer(invalid, 42, A) == null, "malformed server remains memory-only");
        }
        String seedA = CacheWorldIdentity.multiplayer("example.com", 123, null);
        String seedB = CacheWorldIdentity.multiplayer("example.com", 456, null);
        check(!seedA.equals(seedB), "different vanilla seed hashes isolate worlds at one address");
        check(!seedA.equals("multiplayer:example.com"), "legacy address-only identity is never reused");
        check(CacheWorldIdentity.multiplayer("example.com", 0, null) != null, "a zero seed hash is legitimate");
        String uuidA = CacheWorldIdentity.multiplayer("example.com", 123, A);
        String uuidB = CacheWorldIdentity.multiplayer("example.com", 123, B);
        check(!uuidA.equals(uuidB), "same-seed server worlds with different UUIDs stay separate");
        check(!uuidA.equals(CacheWorldIdentity.multiplayer("example.com", 456, A)), "changed seed isolates a world even if its UUID file was retained");
        check(!uuidA.equals(seedA), "late authoritative identity cannot overwrite the provisional seed cache");
        check(CacheWorldIdentity.multiplayer("example.com", 123, null, true) == null,
                "a UUID-capable server cannot persist into another world's seed-only cache while identity is pending");
        check(seedA.equals(CacheWorldIdentity.multiplayer("example.com", 123, null, false)),
                "unsupported servers retain persistent seed-based caching");
        check(uuidB.equals(CacheWorldIdentity.multiplayer("example.com", 123, B, true)),
                "an authoritative UUID enables its separate cache after the wait");

        Object connection = new String("same server");
        Object reconnect = new String("same server");
        check(!ClientWorldIdentityState.requiresIdentity(connection), "unmodified servers do not wait for an unavailable UUID");
        ClientWorldIdentityState.markExpected(connection);
        check(ClientWorldIdentityState.requiresIdentity(connection) && ClientWorldIdentityState.get(connection).isEmpty(),
                "login support notice makes pending UUID sampling memory-only");
        check(!ClientWorldIdentityState.requiresIdentity(reconnect), "equal-looking connections do not inherit login support");
        ClientWorldIdentityState.markExpected(reconnect);
        ClientWorldIdentityState.markExpected(connection);
        check(ClientWorldIdentityState.requiresIdentity(reconnect), "a delayed old login notice cannot erase the new connection's pending identity");
        ClientWorldIdentityState.apply(connection, A);
        check(ClientWorldIdentityState.get(connection).orElseThrow().equals(A), "world UUID is applied to its connection");
        check(ClientWorldIdentityState.get(reconnect).isEmpty(), "equal-looking connection tokens never share a stale UUID");
        ClientWorldIdentityState.apply(reconnect, B);
        check(ClientWorldIdentityState.get(reconnect).orElseThrow().equals(B), "a reconnect can select another world");
        check(ClientWorldIdentityState.get(connection).isEmpty(), "previous connection no longer resolves");
        ClientWorldIdentityState.apply(connection, reconnect, A);
        check(ClientWorldIdentityState.get(reconnect).orElseThrow().equals(B), "late packet from a closed connection cannot erase the current world's UUID");
        ClientWorldIdentityState.markExpected(connection);
        check(ClientWorldIdentityState.get(reconnect).orElseThrow().equals(B), "a delayed old login notice cannot erase the new world's UUID");
        ClientWorldIdentityState.reset();
        check(ClientWorldIdentityState.get(reconnect).isEmpty(), "disconnect clears negotiated identity");
        check(!ClientWorldIdentityState.requiresIdentity(connection) && !ClientWorldIdentityState.requiresIdentity(reconnect),
                "disconnect clears per-connection support notices");
        ClientWorldIdentityState.configureSupportProbe(token -> token == reconnect);
        check(ClientWorldIdentityState.requiresIdentity(reconnect) && !ClientWorldIdentityState.requiresIdentity(connection),
                "negotiated loader capabilities require UUID only for their actual connection");
        ClientWorldIdentityState.reset();
        check(ClientWorldIdentityState.requiresIdentity(reconnect), "disconnect preserves the loader's capability probe");
        ClientWorldIdentityState.configureSupportProbe(token -> false);
        byte[] message = new WorldIdentityMessage(A).encode();
        check(message.length == 17, "world UUID wire data is fixed and bounded");
        check(WorldIdentityMessage.decode(message).equals(new WorldIdentityMessage(A)), "world UUID round-trips without changing old snapshot codecs");
        reject(() -> WorldIdentityMessage.decode(Arrays.copyOf(message, 16)), "truncated UUID is rejected");
        reject(() -> WorldIdentityMessage.decode(Arrays.copyOf(message, 18)), "trailing UUID data is rejected");
        byte[] wrongVersion = message.clone(); wrongVersion[0] = 2;
        reject(() -> WorldIdentityMessage.decode(wrongVersion), "unknown UUID protocol is rejected");
        byte[] zero = new byte[17]; zero[0] = 1;
        reject(() -> WorldIdentityMessage.decode(zero), "a zero UUID is not an authoritative world identity");

        Path temporary = Files.createTempDirectory("odysseymap-world-id-test-").toAbsolutePath().normalize();
        try {
            Path idFile = temporary.resolve("world/data/odysseymap-world-id");
            UUID created = WorldIdentityFile.loadOrCreate(idFile).orElseThrow();
            byte[] storedId = Files.readAllBytes(idFile);
            check(WorldIdentityFile.loadOrCreate(idFile).orElseThrow().equals(created), "server restart reuses the stored UUID");
            check(Arrays.equals(storedId, Files.readAllBytes(idFile)), "reading a world never rewrites its identity file");
            try (var files = Files.list(idFile.getParent())) {
                check(files.count() == 1, "completed UUID publication leaves no temporary file");
            }
            Path concurrentFile = temporary.resolve("concurrent/data/odysseymap-world-id");
            var readers = java.util.concurrent.Executors.newFixedThreadPool(6);
            try {
                var futures = new java.util.ArrayList<java.util.concurrent.Future<java.util.Optional<UUID>>>();
                for (int i = 0; i < 12; i++) futures.add(readers.submit(() -> WorldIdentityFile.loadOrCreate(concurrentFile)));
                UUID winner = futures.get(0).get().orElseThrow();
                for (var future : futures) check(future.get().orElseThrow().equals(winner), "concurrent identity readers see one complete published UUID");
            } finally {
                readers.shutdownNow();
                if (!readers.awaitTermination(2, java.util.concurrent.TimeUnit.SECONDS)) throw new AssertionError("identity test workers did not stop");
            }
            Files.delete(idFile);
            check(!WorldIdentityFile.loadOrCreate(idFile).orElseThrow().equals(created), "a recreated world generates a new UUID");
            Files.writeString(idFile, "broken-world-identity", StandardCharsets.US_ASCII);
            check(WorldIdentityFile.loadOrCreate(idFile).isEmpty(), "corrupt identity falls back without overwriting data");
            check(Files.readString(idFile).equals("broken-world-identity"), "corrupt identity is preserved for diagnosis");
            Files.write(idFile, new byte[129]);
            check(WorldIdentityFile.loadOrCreate(idFile).isEmpty() && Files.size(idFile) == 129, "oversized identity is bounded and preserved");
            check(!CacheWorldIdentity.singleplayer(temporary.resolve("world"), 123, A).equals(CacheWorldIdentity.singleplayer(temporary.resolve("other-world"), 123, A)), "different local save folders remain independent");
            check(!CacheWorldIdentity.singleplayer(temporary.resolve("world"), 123, A).equals(CacheWorldIdentity.singleplayer(temporary.resolve("world"), 123, B)), "a replaced local world at the same path stays separate");

            Path cache = temporary.resolve("cache");
            Tile tile = new Tile();
            tile.setPixel(0, 0, 0xFF123456, 70);
            var legacy = new TileStorage(cache, "multiplayer:example.com", "minecraft:overworld");
            legacy.saveMerged(0, 0, tile.snapshot());
            byte[] legacyBytes = Files.readAllBytes(legacy.fileFor(0, 0));
            var seedStorage = new TileStorage(cache, seedA, "minecraft:overworld");
            seedStorage.saveMerged(0, 0, tile.snapshot());
            byte[] seedBytes = Files.readAllBytes(seedStorage.fileFor(0, 0));
            var storageA = new TileStorage(cache, uuidA, "minecraft:overworld");
            storageA.saveMerged(0, 0, tile.snapshot());
            byte[] bytesA = Files.readAllBytes(storageA.fileFor(0, 0));
            var storageB = new TileStorage(cache, uuidB, "minecraft:overworld");
            check(storageB.load(0, 0).isEmpty(), "new world cannot load another world's tiles");
            tile.setPixel(0, 0, 0xFFABCDEF, 90);
            storageB.saveMerged(0, 0, tile.snapshot());
            check(Arrays.equals(bytesA, Files.readAllBytes(storageA.fileFor(0, 0))), "saving new world preserves old world's cache bytes");
            check(Arrays.equals(legacyBytes, Files.readAllBytes(legacy.fileFor(0, 0))), "legacy cache remains untouched during new writes");
            check(Arrays.equals(seedBytes, Files.readAllBytes(seedStorage.fileFor(0, 0))),
                    "same-seed world replacement with server support preserves the previously unsupported world's cache");
            var sameServer = new TileStorage(cache, CacheWorldIdentity.multiplayer("EXAMPLE.COM:25565", 123, A), "minecraft:overworld");
            check(sameServer.fileFor(0, 0).equals(storageA.fileFor(0, 0)) && sameServer.load(0, 0).isPresent(), "default-port spelling reuses the exact saved cache");
        } finally {
            try (var paths = Files.walk(temporary)) {
                for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
            }
        }
        System.out.println("World identity: " + checks + " checks passed");
    }

    private static void sameAddress(String a, String b) {
        check(CacheWorldIdentity.multiplayer(a, 42, A).equals(CacheWorldIdentity.multiplayer(b, 42, A)), "equivalent server spelling selects one world cache");
    }
    private static void reject(Runnable action, String message) {
        boolean rejected = false;
        try { action.run(); } catch (IllegalArgumentException expected) { rejected = true; }
        check(rejected, message);
    }
    private static void check(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }
}
