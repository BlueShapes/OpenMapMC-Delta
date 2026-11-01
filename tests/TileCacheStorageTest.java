// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.world;

import net.aosankaku.openmapdelta.client.CacheRevisionTracker;

import java.io.IOException;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.util.zip.GZIPOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.stream.Stream;
import java.util.zip.DeflaterOutputStream;

/** Standalone cache regressions; no Minecraft startup or test framework is required. */
public final class TileCacheStorageTest {
    private static int assertions;

    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory("odysseymap-tile-cache-test-");
        try {
            grassColorRoundTripAndLegacyV2(root.resolve("grass"));
            legacyHeightUpgrade(root.resolve("legacy"));
            heightSnapshotsAndLayerIsolation(root.resolve("heights"));
            roundTripAndIdentityIsolation(root.resolve("roundtrip"));
            roundTripExactInflaterBuffer(root.resolve("inflater-boundary"));
            corruptionFallback(root.resolve("corruption"));
            malformedHeadersAndBounds(root.resolve("malformed"));
            appendedGzipMembersAreRejected(root.resolve("gzip-members"));
            immutableSnapshotsAndLateMerge();
            pendingLoadFinalSaveAndRejoin(root.resolve("pending"));
            siblingBandOwnershipSerializesFinalWrites(root.resolve("sibling-bands"));
            staleLoadsAreSessionScoped(root.resolve("stale"));
            emptyTilesAreNotWritten(root.resolve("empty"));
            failedSaveRetriesAndOldAckPreservesNewContent(root.resolve("retry"));
            failedAutosaveDoesNotLoseFinalSave(root.resolve("final-retry"));
            storedTileIndexIsValidated(root.resolve("index"));
            viewportPrefetchIsAsynchronousNearestAndDeduplicated(root.resolve("viewport"));
            viewportPanSupersedesQueuedArea(root.resolve("pan"));
            viewportAreaHandlesMissingFilesAndHugeBounds(root.resolve("huge-area"));
            viewportBatchesYieldToSavesAndResumeAfterBackpressure(root.resolve("backpressure"));
            viewportPrefetchIsSessionAndLayerScoped(root.resolve("area-isolation"));
            safeRenderInvalidationIsCoalesced();
            System.out.println("Tile cache tests passed (" + assertions + " assertions).");
        } finally {
            try (Stream<Path> files = Files.walk(root)) {
                for (Path file : files.sorted(Comparator.reverseOrder()).toList()) Files.delete(file);
            }
        }
    }

    private static void grassColorRoundTripAndLegacyV2(Path root) throws Exception {
        TileStorage storage = new TileStorage(root, "world", "dimension");
        Tile tile = new Tile();
        tile.setPixel(3, 4, 0xFF112233, 91, 0xFF445566);
        tile.setGrassTintEnabled(true);
        check(tile.getPixel(3, 4) == 0xFF445566, "grass color is selected when enabled");
        check(tile.snapshot().pixelAt(index(3, 4)) == 0xFF112233, "snapshot retains original map color");
        long content = tile.getContentRevision(), image = tile.getImageRevision();
        tile.setGrassTintEnabled(false);
        check(tile.getPixel(3, 4) == 0xFF112233, "toggle restores original without rescanning");
        check(tile.getContentRevision() == content && tile.getImageRevision() > image, "toggle invalidates display without rewriting terrain");
        storage.saveMerged(0, 0, tile.snapshot());
        Tile loaded = new Tile(); loaded.setGrassTintEnabled(true);
        loaded.mergeLoaded(storage.load(0, 0).orElseThrow());
        check(loaded.getPixel(3, 4) == 0xFF445566 && loaded.getHeight(3, 4) == 91, "v3 preserves both colors and heights");
        loaded.setPixel(3, 4, 0xFF778899, 92);
        storage.saveMerged(0, 0, loaded.snapshot());
        check(storage.load(0, 0).orElseThrow().grassPixelAt(index(3, 4)) == 0, "non-grass resample clears obsolete tint");
        Files.createDirectories(storage.fileFor(128, 0).getParent());
        try (DataOutputStream out = new DataOutputStream(new DeflaterOutputStream(Files.newOutputStream(storage.fileFor(128, 0))))) {
            out.writeInt(0x4F4D5443); out.writeInt(2); out.writeInt(128); out.writeInt(0);
            for (String id : new String[]{"world", "dimension"}) {
                byte[] bytes = id.getBytes(java.nio.charset.StandardCharsets.UTF_8);
                out.writeInt(bytes.length); out.write(bytes);
            }
            Tile.Snapshot snapshot = tile.snapshot();
            for (int color : snapshot.copyPixels()) out.writeInt(color);
            for (long word : snapshot.copyObserved()) out.writeLong(word);
            for (int height : snapshot.copyHeights()) out.writeInt(height);
        }
        Tile.Snapshot old = storage.load(128, 0).orElseThrow();
        check(old.pixelAt(index(3, 4)) == 0xFF112233 && old.heightAt(index(3, 4)) == 91 && old.grassPixelAt(index(3, 4)) == 0,
                "v2 caches remain visible with their original color");
        storage.saveMerged(128, 0, old);
        check(storage.load(128, 0).orElseThrow().heightAt(index(3, 4)) == 91, "v2 upgrades without losing old terrain");
    }

    private static void legacyHeightUpgrade(Path root) throws Exception {
        TileStorage storage = new TileStorage(root, "world", "dimension");
        Tile legacy = new Tile();
        legacy.setPixel(3, 4, 0xFF123456);
        Files.createDirectories(storage.fileFor(0, 0).getParent());
        try (DataOutputStream out = new DataOutputStream(new DeflaterOutputStream(Files.newOutputStream(storage.fileFor(0, 0))))) {
            out.writeInt(0x4F4D5443); out.writeInt(1); out.writeInt(0); out.writeInt(0);
            for (String identity : new String[]{"world", "dimension"}) {
                byte[] bytes = identity.getBytes(java.nio.charset.StandardCharsets.UTF_8);
                out.writeInt(bytes.length); out.write(bytes);
            }
            for (int color : legacy.snapshot().copyPixels()) out.writeInt(color);
            for (long mask : legacy.snapshot().copyObserved()) out.writeLong(mask);
        }
        Tile restored = new Tile();
        restored.mergeLoaded(storage.load(0, 0).orElseThrow());
        check(restored.getPixel(3, 4) == 0xFF123456, "legacy surface colors remain readable");
        check(restored.getHeight(3, 4) == Tile.UNKNOWN_HEIGHT, "legacy tiles never invent a height");
        restored.setPixel(3, 4, 0xFF123456, -23);
        storage.saveMerged(0, 0, restored.snapshot());
        Tile upgraded = new Tile();
        upgraded.mergeLoaded(storage.load(0, 0).orElseThrow());
        check(upgraded.getHeight(3, 4) == -23, "legacy tiles upgrade to v2 heights on resampling");
    }

    private static void heightSnapshotsAndLayerIsolation(Path root) throws Exception {
        Tile tile = new Tile();
        tile.setPixel(1, 2, 0xFFAA5500, 120);
        Tile.Snapshot snapshot = tile.snapshot();
        tile.setPixel(1, 2, 0xFFAA5500, 121);
        check(snapshot.heightAt(index(1, 2)) == 120, "height snapshots are immutable");
        int[] heights = snapshot.copyHeights(); heights[index(1, 2)] = 999;
        check(snapshot.heightAt(index(1, 2)) == 120, "height accessor returns a copy");
        Tile live = new Tile(); live.setPixel(1, 2, 0xFF111111, 90); live.mergeLoaded(snapshot);
        check(live.getHeight(1, 2) == 90, "late load cannot overwrite a sampled height");
        TileStorage surface = new TileStorage(root, "world", "dimension");
        TileStorage cave = new TileStorage(root, "world", "dimension#cave:-20");
        TileStorage otherCave = new TileStorage(root, "world", "dimension#cave:-19");
        cave.saveMerged(0, 0, tile.snapshot());
        check(surface.load(0, 0).isEmpty(), "cave colors cannot contaminate surface cache");
        check(otherCave.load(0, 0).isEmpty(), "different cave slices stay isolated");
        Tile reloaded = new Tile(); reloaded.mergeLoaded(cave.load(0, 0).orElseThrow());
        check(reloaded.getHeight(1, 2) == 121, "v2 stores negative-layer cache heights");
    }

    private static void roundTripAndIdentityIsolation(Path root) throws Exception {
        TileStorage overworld = new TileStorage(root, "singleplayer:C:/saves/My World", "minecraft:overworld");
        TileStorage nether = new TileStorage(root, "singleplayer:C:/saves/My World", "minecraft:the_nether");
        TileStorage otherWorld = new TileStorage(root, "singleplayer:C:/saves/Other", "minecraft:overworld");
        Tile tile = new Tile();
        tile.setPixel(2, 3, 0xFF123456);
        tile.setPixel(4, 5, 0x00000000);
        overworld.saveMerged(-256, 384, tile.snapshot());
        Tile negative = new Tile();
        negative.setPixel(127, 126, 0xFFABCDEF);
        overworld.saveMerged(512, -128, negative.snapshot());

        Tile restored = new Tile();
        restored.mergeLoaded(overworld.load(-256, 384).orElseThrow());
        check(restored.getPixel(2, 3) == 0xFF123456, "negative tile coordinates round trip");
        check(restored.getPixel(4, 5) == 0, "transparent sample round trips");
        Tile restoredPositive = new Tile();
        restoredPositive.mergeLoaded(overworld.load(512, -128).orElseThrow());
        check(restoredPositive.getPixel(127, 126) == 0xFFABCDEF, "positive/negative coordinate pair round trips");
        check(nether.load(-256, 384).isEmpty(), "dimension identity isolates stored tiles");
        check(otherWorld.load(-256, 384).isEmpty(), "world identity isolates stored tiles");
        check(!overworld.fileFor(-256, 384).toString().contains("My World"), "world names are hashed in paths");
        expectIllegalAlignment(() -> overworld.fileFor(1, 0));
    }

    private static void roundTripExactInflaterBuffer(Path root) throws Exception {
        // The v2 fixed payload is 133,144 bytes; 3,072 + 3,048 identity bytes make 17 * 8,192.
        TileStorage storage = new TileStorage(root, "w".repeat(3_072), "d".repeat(3_048));
        Tile tile = new Tile();
        tile.setPixel(1, 1, 0xFF654321);
        storage.saveMerged(-128, 128, tile.snapshot());
        Tile restored = new Tile();
        restored.mergeLoaded(storage.load(-128, 128).orElseThrow());
        check(restored.getPixel(1, 1) == 0xFF654321,
                "zlib decoder accepts payload ending on an inflater buffer boundary");
    }
    private static void corruptionFallback(Path root) throws Exception {
        TileStorage storage = new TileStorage(root, "server:example.org", "minecraft:overworld");
        Tile good = new Tile();
        good.setPixel(1, 1, 0xFF010203);
        storage.saveMerged(0, 0, good.snapshot());
        byte[] validBytes = Files.readAllBytes(storage.fileFor(0, 0));
        byte[] brokenBytes = {1, 2, 3, 4, 5};
        Files.createDirectories(storage.fileFor(128, 0).getParent());
        Files.write(storage.fileFor(128, 0), brokenBytes);

        check(storage.load(128, 0).isEmpty(), "truncated/corrupt file falls back to missing cache");
        check(Arrays.equals(Files.readAllBytes(storage.fileFor(128, 0)), brokenBytes),
                "corrupt entry is left untouched during fallback");
        check(Arrays.equals(Files.readAllBytes(storage.fileFor(0, 0)), validBytes),
                "valid neighboring tile is not changed by corrupt read");

        Tile replacement = new Tile();
        replacement.setPixel(0, 0, 0xFF445566);
        storage.saveMerged(128, 0, replacement.snapshot());
        Tile repaired = new Tile();
        repaired.mergeLoaded(storage.load(128, 0).orElseThrow());
        check(repaired.getPixel(0, 0) == 0xFF445566, "successful replacement repairs corrupt entry");
        check(storage.load(256, 0).isEmpty(), "missing file returns empty cache");
    }

    private static void malformedHeadersAndBounds(Path root) throws Exception {
        TileStorage storage = new TileStorage(root, "server:bounded.example", "minecraft:overworld");
        Files.createDirectories(storage.fileFor(0, 0).getParent());
        Files.write(storage.fileFor(0, 0), new byte[256 * 1024 + 1]);
        check(storage.load(0, 0).isEmpty(), "compressed file above input bound is rejected");

        byte[] oversizedIdentity = compressedHeader(128, 0, 32 * 1024 + 1);
        Files.write(storage.fileFor(128, 0), oversizedIdentity);
        check(storage.load(128, 0).isEmpty(), "oversized identity length is rejected before allocation");

        byte[] wrongCoordinates = compressedHeader(256, 0, 1);
        Files.write(storage.fileFor(384, 0), wrongCoordinates);
        check(storage.load(384, 0).isEmpty(), "header coordinates must match the requested tile");

        Tile tile = new Tile();
        tile.setPixel(0, 0, 0xFF102030);
        storage.saveMerged(512, 0, tile.snapshot());
        byte[] good = Files.readAllBytes(storage.fileFor(512, 0));
        byte[] trailing = Arrays.copyOf(good, good.length + 1);
        trailing[trailing.length - 1] = 0x5A;
        Files.write(storage.fileFor(512, 0), trailing);
        check(storage.load(512, 0).isEmpty(), "trailing compressed bytes are rejected");

        byte[] badChecksum = good.clone();
        badChecksum[badChecksum.length - 1] ^= 0x01;
        Files.write(storage.fileFor(512, 0), badChecksum);
        check(storage.load(512, 0).isEmpty(), "invalid zlib checksum is rejected");
    }

    private static byte[] compressedHeader(int headerX, int headerZ, int worldIdentityLength) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(new DeflaterOutputStream(bytes))) {
            out.writeInt(0x4F4D5443);
            out.writeInt(1);
            out.writeInt(headerX);
            out.writeInt(headerZ);
            out.writeInt(worldIdentityLength);
        }
        return bytes.toByteArray();
    }
    private static void appendedGzipMembersAreRejected(Path root) throws Exception {
        TileStorage storage = new TileStorage(root, "server:gzip.example", "minecraft:overworld");
        Tile tile = new Tile();
        tile.setPixel(0, 0, 0xFF123456);
        storage.saveMerged(0, 0, tile.snapshot());
        byte[] encodedTile = Files.readAllBytes(storage.fileFor(0, 0));
        ByteArrayOutputStream emptyMemberBytes = new ByteArrayOutputStream();
        try (GZIPOutputStream emptyMember = new GZIPOutputStream(emptyMemberBytes)) {
            // Closing writes a valid empty gzip member.
        }
        byte[] emptyMember = emptyMemberBytes.toByteArray();
        ByteArrayOutputStream chain = new ByteArrayOutputStream();
        chain.write(encodedTile);
        for (int i = 0; i < 10_000; i++) chain.write(emptyMember);
        Files.write(storage.fileFor(0, 0), chain.toByteArray());
        check(Files.size(storage.fileFor(0, 0)) < 256 * 1024,
                "hostile gzip member chain stays within the compressed input bound");
        check(storage.load(0, 0).isEmpty(), "appended gzip members are rejected safely");
    }
    private static void immutableSnapshotsAndLateMerge() {
        Tile source = new Tile();
        source.setPixel(1, 1, 0xFF00AA00);
        source.setPixel(2, 2, 0xFF0000AA);
        Tile.Snapshot loaded = source.snapshot();
        source.setPixel(1, 1, 0xFFFF0000);
        check(loaded.pixelAt(index(1, 1)) == 0xFF00AA00, "snapshot does not observe later live changes");
        int[] copiedPixels = loaded.copyPixels();
        copiedPixels[index(2, 2)] = 0;
        check(loaded.pixelAt(index(2, 2)) == 0xFF0000AA, "snapshot accessors return immutable copies");

        Tile current = new Tile();
        current.setPixel(1, 1, 0x00000000);
        current.setPixel(3, 3, 0xFFFF0000);
        check(current.mergeLoaded(loaded), "late load fills previously unknown pixels");
        check(current.getPixel(1, 1) == 0, "sampled transparent zero beats late disk data");
        check(current.getPixel(2, 2) == 0xFF0000AA, "late load fills an unobserved pixel");
        check(current.getPixel(3, 3) == 0xFFFF0000, "late load preserves sampled nonzero pixel");
    }

    private static void pendingLoadFinalSaveAndRejoin(Path root) throws Exception {
        TileStorage storage = new TileStorage(root, "singleplayer:world-a", "minecraft:overworld");
        Tile old = new Tile();
        old.setPixel(0, 0, 0xFF112233);
        old.setPixel(1, 0, 0xFF445566);
        storage.saveMerged(0, 0, old.snapshot());

        ManualExecutor executor = new ManualExecutor();
        WorldCache cache = new WorldCache(executor);
        cache.beginSession(storage);
        check(cache.requestLoad(0, 0), "first tile request queues a disk read");
        check(!cache.requestLoad(0, 0), "tile load is queued only once per session");
        Tile sampled = new Tile();
        sampled.setPixel(0, 0, 0);
        long sampleRevision = sampled.getContentRevision();
        cache.endSession(List.of(new WorldCache.TileWrite(0, 0, sampleRevision, sampled.snapshot())));

        long rejoinGeneration = cache.beginSession(storage);
        check(cache.requestLoad(0, 0), "rejoin queues a new-generation read");
        executor.runAll();
        List<WorldCache.LoadResult> results = cache.pollLoads();
        check(results.size() == 1 && results.get(0).generation() == rejoinGeneration,
                "old pending load cannot enter an immediate rejoin");
        Tile.Snapshot rejoined = results.get(0).snapshot();
        Tile restored = new Tile();
        restored.mergeLoaded(rejoined);
        check(restored.getPixel(0, 0) == 0, "final logout sample overrides pending-load data on disk");
        check(restored.getPixel(1, 0) == 0xFF445566, "pending-load save preserves other cached pixels");
        cache.endSession(List.of());
    }

    private static void siblingBandOwnershipSerializesFinalWrites(Path root) throws Exception {
        ManualExecutor executor = new ManualExecutor();
        WorldCache first = new WorldCache(executor), second = first.createSibling();
        TileStorage band = new TileStorage(root, "world", "minecraft:the_nether#cave:64");
        first.beginSession(band);
        Tile oldOwner = new Tile();
        oldOwner.setPixel(0, 0, 0xFF102030, 63);
        first.endSession(List.of(new WorldCache.TileWrite(0, 0, oldOwner.getContentRevision(), oldOwner.snapshot())));
        second.beginSession(new TileStorage(root, "world", "minecraft:the_nether#cave:64"));
        second.requestLoad(0, 0);
        check(executor.size() == 2, "both cave cache owners share a serial disk queue");
        executor.runAll();
        List<WorldCache.LoadResult> loads = second.pollLoads();
        check(loads.size() == 1 && loads.get(0).snapshot().pixelAt(index(0, 0)) == 0xFF102030,
                "new band owner loads after previous owner's pending final write");
        Tile newOwner = new Tile();
        newOwner.mergeLoaded(loads.get(0).snapshot());
        newOwner.setPixel(1, 0, 0xFF405060, 62);
        second.endSession(List.of(new WorldCache.TileWrite(0, 0, newOwner.getContentRevision(), newOwner.snapshot())));
        first.beginSession(band);
        first.requestLoad(0, 0);
        executor.runAll();
        Tile.Snapshot restored = first.pollLoads().get(0).snapshot();
        check(restored.pixelAt(index(0, 0)) == 0xFF102030 && restored.pixelAt(index(1, 0)) == 0xFF405060,
                "returning band owner preserves exploration from both views");
        check(second.pollLoads().isEmpty(), "ended sibling has no stale load results");
        first.endSession(List.of());
    }

    private static void staleLoadsAreSessionScoped(Path root) throws Exception {
        TileStorage first = new TileStorage(root, "server:first.example", "minecraft:overworld");
        TileStorage second = new TileStorage(root, "server:second.example", "minecraft:the_nether");
        Tile savedSecond = new Tile();
        savedSecond.setPixel(7, 9, 0xFF778899);
        second.saveMerged(0, 0, savedSecond.snapshot());

        ManualExecutor executor = new ManualExecutor();
        WorldCache cache = new WorldCache(executor);
        cache.beginSession(first);
        cache.requestLoad(0, 0);
        executor.runNext();
        cache.endSession(List.of());
        long currentGeneration = cache.beginSession(second);
        cache.requestLoad(0, 0);
        executor.runAll();
        List<WorldCache.LoadResult> results = cache.pollLoads();
        check(results.size() == 1, "different-world load discards old result");
        check(results.get(0).generation() == currentGeneration && results.get(0).storage() == second,
                "only exact active world/dimension can return a load");
        Tile loaded = new Tile();
        loaded.mergeLoaded(results.get(0).snapshot());
        check(loaded.getPixel(7, 9) == 0xFF778899, "active world's tile content is isolated");
        cache.endSession(List.of());
    }

    private static void emptyTilesAreNotWritten(Path root) throws Exception {
        TileStorage storage = new TileStorage(root, "server:empty.example", "minecraft:overworld");
        ManualExecutor executor = new ManualExecutor();
        WorldCache cache = new WorldCache(executor);
        cache.beginSession(storage);
        Tile renderOnly = new Tile();
        for (int i = 0; i < 20; i++) check(!renderOnly.hasUnsavedContent(), "empty render tile has no content revision");
        cache.save(0, 0, renderOnly.getContentRevision(), renderOnly.snapshot());
        check(executor.size() == 0, "empty render-only tiles never enqueue a disk write");
        check(storage.load(0, 0).isEmpty(), "empty render-only tiles have no cache file");
        cache.endSession(List.of());
    }

    private static void failedSaveRetriesAndOldAckPreservesNewContent(Path root) throws Exception {
        Path blockedRoot = root.resolve("blocked");
        Files.createDirectories(root);
        Files.writeString(blockedRoot, "prevent directory creation");
        TileStorage storage = new TileStorage(blockedRoot, "server:retry.example", "minecraft:overworld");
        ManualExecutor executor = new ManualExecutor();
        WorldCache cache = new WorldCache(executor);
        cache.beginSession(storage);

        Tile tile = new Tile();
        tile.setPixel(0, 0, 0xFF000011);
        long firstRevision = tile.getContentRevision();
        cache.save(0, 0, firstRevision, tile.snapshot());
        tile.setPixel(0, 0, 0xFF000022);
        long secondRevision = tile.getContentRevision();
        executor.runNext();
        WorldCache.SaveResult failed = cache.pollSaveResults().get(0);
        check(!failed.succeeded() && failed.contentRevision() == firstRevision,
                "save failure is reported for its tile revision");
        check(tile.hasUnsavedContent(), "failed autosave leaves content eligible for retry");

        tile.markSavedContent(failed.contentRevision());
        check(tile.hasUnsavedContent(), "an older acknowledgment cannot clear newer sampled content");
        Files.delete(blockedRoot);
        cache.save(0, 0, secondRevision, tile.snapshot());
        executor.runAll();
        WorldCache.SaveResult retried = cache.pollSaveResults().get(0);
        check(retried.succeeded() && retried.contentRevision() == secondRevision,
                "same unchanged content revision succeeds on retry");
        tile.markSavedContent(retried.contentRevision());
        check(!tile.hasUnsavedContent(), "successful matching acknowledgment clears saved content");
        check(new TileStorage(root.resolve("valid"), "server:retry.example", "minecraft:overworld")
                .load(0, 0).isEmpty(), "failed storage root did not create a misleading cache entry");
        cache.endSession(List.of());
    }

    private static void failedAutosaveDoesNotLoseFinalSave(Path root) throws Exception {
        Path blockedRoot = root.resolve("blocked");
        Files.createDirectories(root);
        Files.writeString(blockedRoot, "block the queued autosave");
        TileStorage storage = new TileStorage(blockedRoot, "server:final.example", "minecraft:overworld");
        ManualExecutor executor = new ManualExecutor();
        WorldCache cache = new WorldCache(executor);
        cache.beginSession(storage);
        Tile tile = new Tile();
        tile.setPixel(2, 4, 0xFFABCDEF);
        long contentRevision = tile.getContentRevision();
        Tile.Snapshot snapshot = tile.snapshot();
        cache.save(0, 0, contentRevision, snapshot);
        cache.endSession(List.of(new WorldCache.TileWrite(0, 0, contentRevision, snapshot)));

        executor.runNext();
        check(cache.pollSaveResults().isEmpty(), "save result from ended generation is ignored");
        Files.delete(blockedRoot);
        executor.runNext();

        long rejoin = cache.beginSession(storage);
        cache.requestLoad(0, 0);
        executor.runNext();
        List<WorldCache.LoadResult> loaded = cache.pollLoads();
        check(loaded.size() == 1 && loaded.get(0).generation() == rejoin,
                "rejoin reads the final snapshot after a failed queued autosave");
        Tile restored = new Tile();
        restored.mergeLoaded(loaded.get(0).snapshot());
        check(restored.getPixel(2, 4) == 0xFFABCDEF, "final logout write preserves sampled content");
        cache.endSession(List.of());
    }

    private static void storedTileIndexIsValidated(Path root) throws Exception {
        TileStorage storage = new TileStorage(root, "index-world", "dimension");
        Tile tile = new Tile();
        tile.setPixel(0, 0, 0xFF123456);
        storage.saveMerged(-128, 256, tile.snapshot());
        storage.saveMerged(128, -256, tile.snapshot());
        Files.createDirectories(storage.fileFor(0, 0).getParent());
        Path corrupt = storage.fileFor(0, 0);
        byte[] corruptBytes = {1, 2, 3};
        Files.write(corrupt, corruptBytes);
        Files.writeString(corrupt.resolveSibling("not-a-coordinate.tile.z"), "ignored");
        Files.writeString(corrupt.resolveSibling("7_8.tile.z"), "unaligned");
        check(storage.listStoredTiles().equals(List.of(
                        new TileStorage.StoredTileCoordinate(-128, 256),
                        new TileStorage.StoredTileCoordinate(0, 0),
                        new TileStorage.StoredTileCoordinate(128, -256))),
                "stored index returns only sorted, aligned signed filenames");
        check(Arrays.equals(Files.readAllBytes(corrupt), corruptBytes),
                "index discovery never opens or mutates corrupt tile data");
        expectIllegalAlignment(() -> new TileStorage.StoredTileCoordinate(7, 0));
    }

    private static void viewportPrefetchIsAsynchronousNearestAndDeduplicated(Path root) throws Exception {
        TileStorage storage = new TileStorage(root, "viewport-world", "overworld");
        for (int[] point : new int[][]{{0, 0}, {-128, 0}, {0, -128}, {0, 128}, {128, 0}, {384, 0}}) {
            Tile tile = new Tile();
            tile.setPixel(1, 1, 0xFF000000 | (point[0] + 512) << 8 | (point[1] + 512));
            storage.saveMerged(point[0], point[1], tile.snapshot());
        }

        ManualExecutor executor = new ManualExecutor();
        WorldCache cache = new WorldCache(executor);
        cache.beginSession(storage);
        check(cache.requestArea(-256, -256, 256, 256), "viewport area queues async stored-tile discovery");
        check(executor.size() == 1 && cache.pollLoads().isEmpty(),
                "area request does no disk work or empty-tile result synchronously");
        check(!cache.requestArea(-256, -256, 256, 256), "identical viewport request is coalesced");
        executor.runAll();
        check(!cache.requestLoad(0, 0), "area and direct load requests share per-session deduplication");
        List<WorldCache.LoadResult> loads = cache.pollLoads();
        check(loads.size() == 5, "viewport loads only stored files inside inclusive bounds");
        check(loads.stream().map(result -> result.alignedX() + "," + result.alignedZ()).toList()
                        .equals(List.of("0,0", "-128,0", "0,-128", "0,128", "128,0")),
                "viewport disk reads are nearest-first with deterministic coordinate ties");
        check(!cache.requestLoad(0, 0), "completed area reads remain deduplicated for the session");
        cache.endSession(List.of());
    }

    private static void viewportPanSupersedesQueuedArea(Path root) throws Exception {
        TileStorage storage = new TileStorage(root, "pan-world", "overworld");
        for (int x : new int[]{0, 1024}) {
            Tile tile = new Tile(); tile.setPixel(1, 1, 0xFF123456 + x);
            storage.saveMerged(x, 0, tile.snapshot());
        }
        ManualExecutor executor = new ManualExecutor();
        WorldCache cache = new WorldCache(executor);
        cache.beginSession(storage);
        cache.requestArea(0, 0, 0, 0);
        cache.requestArea(1024, 0, 1024, 0);
        executor.runAll();
        List<WorldCache.LoadResult> loads = cache.pollLoads();
        check(loads.size() == 1 && loads.get(0).alignedX() == 1024,
                "new viewport replaces queued, unread work from the previous pan");
        check(cache.requestLoad(0, 0), "superseded tile was not reserved or read");
        executor.runAll();
        cache.endSession(List.of());
    }

    private static void viewportAreaHandlesMissingFilesAndHugeBounds(Path root) throws Exception {
        TileStorage storage = new TileStorage(root, "huge-world", "overworld");
        Tile present = new Tile(); present.setPixel(4, 4, 0xFF556677);
        storage.saveMerged(Integer.MIN_VALUE, 0, present.snapshot());
        Path corruptPath = storage.fileFor(0, 0);
        Files.createDirectories(corruptPath.getParent());
        byte[] corruptBytes = {9, 8, 7};
        Files.write(corruptPath, corruptBytes);

        ManualExecutor executor = new ManualExecutor();
        WorldCache cache = new WorldCache(executor);
        cache.beginSession(storage);
        cache.requestArea(Integer.MIN_VALUE, Integer.MIN_VALUE,
                Integer.MAX_VALUE - (Integer.MAX_VALUE % Tile.SIZE), Integer.MAX_VALUE - (Integer.MAX_VALUE % Tile.SIZE));
        executor.runAll();
        List<WorldCache.LoadResult> loads = cache.pollLoads();
        check(loads.size() == 1 && loads.get(0).alignedX() == Integer.MIN_VALUE,
                "huge viewport filters known filenames without iterating empty coordinates");
        check(Arrays.equals(Files.readAllBytes(corruptPath), corruptBytes),
                "corrupt indexed entries remain untouched and produce no empty load result");
        cache.endSession(List.of());
    }

    private static void viewportBatchesYieldToSavesAndResumeAfterBackpressure(Path root) throws Exception {
        TileStorage storage = new TileStorage(root, "batch-world", "overworld");
        for (int i = 0; i < 48; i++) {
            Tile tile = new Tile(); tile.setPixel(0, 0, 0xFF000000 | i);
            storage.saveMerged(i * Tile.SIZE, 0, tile.snapshot());
        }
        ManualExecutor executor = new ManualExecutor();
        WorldCache cache = new WorldCache(executor);
        cache.beginSession(storage);
        cache.requestArea(0, 0, 47 * Tile.SIZE, 0);
        Tile saved = new Tile(); saved.setPixel(1, 2, 0xFFABCDEF);
        cache.save(10_240, 0, saved.getContentRevision(), saved.snapshot());

        executor.runNext();
        check(cache.pollLoads(8).size() == 8, "one worker batch completes no more than eight area reads");
        check(executor.size() == 2, "area continuation yields behind already queued saves");
        executor.runNext();
        check(cache.pollSaveResults().size() == 1 && storage.load(10_240, 0).isPresent(),
                "queued save progresses between viewport batches");
        executor.runAll();
        check(cache.pollLoads(0).isEmpty(), "zero-result polling leaves bounded completions queued");
        check(executor.size() == 0, "full result queue does not busy-loop or enqueue another batch");
        List<WorldCache.LoadResult> firstResumedBatch = cache.pollLoads(8);
        check(firstResumedBatch.size() == 8, "polling frees queue capacity and returns a bounded batch");
        check(executor.size() == 1, "polling resumes paused viewport work");
        executor.runAll();
        List<WorldCache.LoadResult> remaining = cache.pollLoads();
        check(8 + firstResumedBatch.size() + remaining.size() == 48,
                "backpressured work resumes and delivers every stored tile exactly once");
        check(cache.areaPlanBuildCountForTest() == 1,
                "all bounded batches reuse a single nearest-first plan for the viewport");

        cache.requestArea(10_240, 0, 10_240, 0);
        executor.runAll();
        check(cache.pollLoads().size() == 1, "a successful save is added to an already-built filename index");
        cache.endSession(List.of());
    }

    private static void viewportPrefetchIsSessionAndLayerScoped(Path root) throws Exception {
        TileStorage surface = new TileStorage(root, "same-world", "dimension");
        TileStorage cave = new TileStorage(root, "same-world", "dimension#cave:-20");
        Tile surfaceTile = new Tile(); surfaceTile.setPixel(0, 0, 0xFF111111);
        Tile caveTile = new Tile(); caveTile.setPixel(0, 0, 0xFF222222);
        surface.saveMerged(0, 0, surfaceTile.snapshot());
        cave.saveMerged(0, 0, caveTile.snapshot());

        ManualExecutor executor = new ManualExecutor();
        WorldCache cache = new WorldCache(executor);
        cache.beginSession(surface);
        cache.requestArea(0, 0, 0, 0);
        cache.endSession(List.of());
        long generation = cache.beginSession(cave);
        cache.requestArea(0, 0, 0, 0);
        executor.runAll();
        List<WorldCache.LoadResult> loads = cache.pollLoads();
        check(loads.size() == 1 && loads.get(0).generation() == generation
                        && loads.get(0).storage() == cave,
                "stale area tasks cannot leak across world or layer session changes");
        Tile result = new Tile(); result.mergeLoaded(loads.get(0).snapshot());
        check(result.getPixel(0, 0) == 0xFF222222, "area load uses only the active layer's stored tile");
        cache.endSession(List.of());
    }
    private static void safeRenderInvalidationIsCoalesced() {
        CacheRevisionTracker tracker = new CacheRevisionTracker();
        check(tracker.shouldInvalidate(1, true), "initial cache revision invalidates immediately");
        tracker.tick();
        check(!tracker.shouldInvalidate(2, true), "safe mode defers repeated invalidation");
        tracker.tick();
        check(!tracker.shouldInvalidate(3, true), "safe mode coalesces a second tick's cache update");
        tracker.tick();
        check(tracker.shouldInvalidate(4, true), "safe mode allows three compose attempts between invalidations");
        tracker.forceInvalidation(4, true);
        check(!tracker.shouldInvalidate(5, true), "forced session invalidation also starts safe-mode cooldown");
        check(tracker.shouldInvalidate(5, false), "disabling safe mode applies pending cache revision immediately");
    }

    private static int index(int localX, int localZ) {
        return (127 - localZ) * Tile.SIZE + (127 - localX);
    }

    private static void expectIllegalAlignment(Runnable operation) {
        try {
            operation.run();
            throw new AssertionError("expected invalid alignment rejection");
        } catch (IllegalArgumentException expected) {
            check(true, "unaligned coordinate rejected");
        }
    }

    private static void check(boolean result, String message) {
        assertions++;
        if (!result) throw new AssertionError(message);
    }

    private static final class ManualExecutor implements Executor {
        private final ArrayDeque<Runnable> tasks = new ArrayDeque<>();

        @Override
        public void execute(Runnable command) { tasks.addLast(command); }
        int size() { return tasks.size(); }
        void runNext() { tasks.removeFirst().run(); }
        void runAll() { while (!tasks.isEmpty()) runNext(); }
    }
}
