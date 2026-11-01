// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.platform;

import net.aosankaku.openmapdelta.world.Tile;
import net.aosankaku.openmapdelta.world.TileStorage;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.AccessDeniedException;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclEntryPermission;
import java.nio.file.attribute.AclEntryType;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.UserPrincipal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/** Standalone migration regressions; exercises real tile files without launching Minecraft. */
public final class DataDirectoryMigrationTest {
    private static int assertions;
    private static final Path TEST_ROOT = Path.of(System.getProperty("map.migration.test.root", "build/data-directory-migration-tests"));
    private static final byte[] MARKER = "OpenMap Delta legacy data migration v1\n".getBytes(StandardCharsets.UTF_8);

    private DataDirectoryMigrationTest() {}

    public static void main(String[] args) throws Exception {
        if (args.length == 3 && "recheck".equals(args[0])) {
            DataDirectoryMigration.ensureMigrated(Path.of(args[1]), Path.of(args[2]));
            return;
        }
        Files.createDirectories(TEST_ROOT);
        Path root = Files.createTempDirectory(TEST_ROOT, "openmapdelta-data-migration-");
        try {
            separatesConfigAndGameRootsAndKeepsTileFormats(root.resolve("move"));
            existingFilesWinAndMissingFilesMerge(root.resolve("merge"));
            retriesAfterAnObstructionWithoutPublishingMarker(root.resolve("retry"));
            fallsBackWhenWholeDirectoryMoveFails(root.resolve("move-fallback"));
            markerPreventsDeletedSettingsAndTilesFromReturning(root.resolve("marker"));
            rejectsOverlappingAndSymbolicLinkRoots(root.resolve("unsafe"));
            rejectsJunctionEscapeWhenAvailable(root.resolve("junction"));
            inaccessibleLegacyRootCanRetry(root.resolve("inaccessible"));
            supportsNormalNestedMinecraftConfigRoot(root.resolve("nested"));
            System.out.println("Data directory migration tests passed (" + assertions + " assertions).");
        } finally {
            deleteTree(root);
        }
    }

    private static void separatesConfigAndGameRootsAndKeepsTileFormats(Path root) throws Exception {
        Path configDir = root.resolve("settings");
        Path gameDir = root.resolve("game");
        Path legacyConfig = configDir.resolve("odysseymap");
        Path legacyCache = legacyConfig.resolve("cache");
        Path legacyPrefs = legacyConfig.resolve("shared-waypoint-preferences/world/player.json");
        byte[] preferences = "{\"favorite\":true,\"visible\":false}\n".getBytes(StandardCharsets.UTF_8);
        byte[] sessionKey = "test-only\n".getBytes(StandardCharsets.US_ASCII);
        Path sessionKeyFile = legacyConfig.resolve("webmap-session.key");
        Files.createDirectories(legacyPrefs.getParent());
        Files.write(legacyPrefs, preferences);
        Files.createDirectories(legacyConfig);
        Files.write(sessionKeyFile, sessionKey);
        FileTime originalTime = FileTime.fromMillis(1_704_067_200_000L);
        Files.setLastModifiedTime(sessionKeyFile, originalTime);

        saveTile(legacyCache, "singleplayer:/saves/negative", "minecraft:overworld", -128, 256, 0xFF123456, 72);
        saveTile(legacyCache, "singleplayer:/saves/negative", "minecraft:overworld#cave:-20", -256, -128, 0xFFABCDEF, -32);
        saveTile(legacyCache, "server:stable-id-2", "minecraft:the_nether", 128, -256, 0xFF778899, 51);

        DataDirectoryMigration.ensureMigrated(configDir, gameDir);

        Path newConfig = configDir.resolve("openmapdelta");
        Path newCache = gameDir.resolve("openmapdelta");
        check(Arrays.equals(preferences, Files.readAllBytes(newConfig.resolve("shared-waypoint-preferences/world/player.json"))),
                "nested shared waypoint preferences retain their bytes");
        check(Arrays.equals(sessionKey, Files.readAllBytes(newConfig.resolve("webmap-session.key"))),
                "web map session key bytes survive migration exactly");
        equal(originalTime, Files.getLastModifiedTime(newConfig.resolve("webmap-session.key")),
                "config file modification time is preserved");
        check(!Files.exists(newConfig.resolve("cache"), LinkOption.NOFOLLOW_LINKS),
                "legacy cache is excluded from the new config directory");
        check(!Files.exists(legacyCache, LinkOption.NOFOLLOW_LINKS),
                "cache directory is moved as a whole when the destination is absent");
        check(Files.exists(legacyPrefs), "legacy settings remain as a source backup");
        check(Arrays.equals(MARKER, Files.readAllBytes(newConfig.resolve(".legacy-data-migration-v1"))),
                "completion marker has validated versioned content");
        assertTile(newCache, "singleplayer:/saves/negative", "minecraft:overworld", -128, 256, 0xFF123456, 72);
        assertTile(newCache, "singleplayer:/saves/negative", "minecraft:overworld#cave:-20", -256, -128, 0xFFABCDEF, -32);
        assertTile(newCache, "server:stable-id-2", "minecraft:the_nether", 128, -256, 0xFF778899, 51);
    }

    private static void existingFilesWinAndMissingFilesMerge(Path root) throws Exception {
        Path configDir = root.resolve("config");
        Path gameDir = root.resolve("game");
        Path legacyConfig = configDir.resolve("odysseymap");
        Path newConfig = configDir.resolve("openmapdelta");
        Files.createDirectories(legacyConfig.resolve("nested"));
        Files.createDirectories(newConfig.resolve("nested"));
        byte[] oldCollision = "legacy settings".getBytes(StandardCharsets.UTF_8);
        byte[] newCollision = "current settings".getBytes(StandardCharsets.UTF_8);
        Files.write(legacyConfig.resolve("nested/collision.json"), oldCollision);
        Files.write(newConfig.resolve("nested/collision.json"), newCollision);
        byte[] missing = new byte[]{0, 1, 2, (byte) 0xff, 9};
        Files.write(legacyConfig.resolve("nested/missing.bin"), missing);

        Path legacyCache = legacyConfig.resolve("cache");
        Path newCache = gameDir.resolve("openmapdelta");
        Files.createDirectories(newCache);
        saveTile(legacyCache, "same-world", "minecraft:overworld", -128, 0, 0xFF111111, 30);
        saveTile(legacyCache, "same-world", "minecraft:overworld", 128, 0, 0xFF222222, 31);
        saveTile(newCache, "same-world", "minecraft:overworld", -128, 0, 0xFFEEEEEE, 80);

        DataDirectoryMigration.ensureMigrated(configDir, gameDir);

        check(Arrays.equals(newCollision, Files.readAllBytes(newConfig.resolve("nested/collision.json"))),
                "existing config destination wins collisions");
        check(Arrays.equals(missing, Files.readAllBytes(newConfig.resolve("nested/missing.bin"))),
                "missing config files are copied byte-for-byte");
        check(Arrays.equals(oldCollision, Files.readAllBytes(legacyConfig.resolve("nested/collision.json"))),
                "legacy config source remains intact");
        assertTile(newCache, "same-world", "minecraft:overworld", -128, 0, 0xFFEEEEEE, 80);
        assertTile(newCache, "same-world", "minecraft:overworld", 128, 0, 0xFF222222, 31);
        check(Files.exists(legacyCache.resolve("nested")) || Files.exists(legacyCache),
                "cache merge keeps its old tree as a backup");
    }

    private static void retriesAfterAnObstructionWithoutPublishingMarker(Path root) throws Exception {
        Path configDir = root.resolve("config");
        Path gameDir = root.resolve("game");
        Path legacyConfig = configDir.resolve("odysseymap");
        Path legacyPrefs = legacyConfig.resolve("nested/preferences.json");
        Files.createDirectories(legacyPrefs.getParent());
        byte[] preferences = "{\"retry\":true}".getBytes(StandardCharsets.UTF_8);
        Files.write(legacyPrefs, preferences);

        Path legacyCache = legacyConfig.resolve("cache");
        Files.createDirectories(legacyCache.resolve("blocked"));
        Files.writeString(legacyCache.resolve("blocked/tile.bin"), "legacy tile");
        Path newCache = gameDir.resolve("openmapdelta");
        Files.createDirectories(newCache);
        Files.writeString(newCache.resolve("blocked"), "destination obstruction");

        expectIOException(() -> DataDirectoryMigration.ensureMigrated(configDir, gameDir),
                "cache obstruction aborts the overall migration");
        Path newConfig = configDir.resolve("openmapdelta");
        check(!Files.exists(newConfig.resolve(".legacy-data-migration-v1"), LinkOption.NOFOLLOW_LINKS),
                "failed migration never publishes a completion marker");
        check(Arrays.equals(preferences, Files.readAllBytes(newConfig.resolve("nested/preferences.json"))),
                "completed config copies remain available for retry");
        check(Files.exists(legacyPrefs), "failed migration preserves legacy config source");
        check(Files.exists(legacyCache.resolve("blocked/tile.bin")), "failed merge preserves the cache source");

        Files.delete(newCache.resolve("blocked"));
        DataDirectoryMigration.ensureMigrated(configDir, gameDir);
        check(Files.exists(newConfig.resolve(".legacy-data-migration-v1")),
                "retry publishes its marker only after the obstruction is removed");
        check(Files.readString(newCache.resolve("blocked/tile.bin")).equals("legacy tile"),
                "retry imports remaining cache files");
    }

    private static void markerPreventsDeletedSettingsAndTilesFromReturning(Path root) throws Exception {
        Path configDir = root.resolve("config");
        Path gameDir = root.resolve("game");
        Path legacyConfig = configDir.resolve("odysseymap");
        Path newConfig = configDir.resolve("openmapdelta");
        Path legacyValue = legacyConfig.resolve("config.json");
        Files.createDirectories(legacyConfig);
        Files.writeString(legacyValue, "{\"keep\":true}");

        Path legacyCache = legacyConfig.resolve("cache");
        Path newCache = gameDir.resolve("openmapdelta");
        Files.createDirectories(newCache);
        saveTile(legacyCache, "marker-world", "minecraft:overworld", -256, 128, 0xFF334455, 64);
        saveTile(newCache, "existing-world", "minecraft:overworld", 0, 0, 0xFF998877, 80);
        DataDirectoryMigration.ensureMigrated(configDir, gameDir);

        Path migratedConfig = newConfig.resolve("config.json");
        Path migratedTile = new TileStorage(newCache, "marker-world", "minecraft:overworld").fileFor(-256, 128);
        Files.delete(migratedConfig);
        Files.delete(migratedTile);
        runFreshProcessToRecheck(configDir, gameDir);

        check(!Files.exists(migratedConfig), "valid marker prevents a deleted config from being restored");
        check(!Files.exists(migratedTile), "valid marker prevents a deleted merged tile from being restored");
        check(Files.exists(legacyValue) && Files.exists(legacyCache), "marker check leaves old backups untouched");
    }

    private static void fallsBackWhenWholeDirectoryMoveFails(Path root) throws Exception {
        Path configDir = root.resolve("config");
        Path gameDir = root.resolve("game");
        Path legacyCache = configDir.resolve("odysseymap/cache");
        Path oldTileBytes = legacyCache.resolve("world/dimension/negative.tile");
        Files.createDirectories(oldTileBytes.getParent());
        byte[] bytes = new byte[]{7, 6, 5, 0, (byte) 0xfe};
        Files.write(oldTileBytes, bytes);
        boolean[] moveAttempted = {false};

        DataDirectoryMigration.ensureMigrated(configDir, gameDir, (source, destination) -> {
            moveAttempted[0] = true;
            throw new IOException("injected cross-filesystem move failure");
        });

        check(moveAttempted[0], "whole-directory move was attempted");
        check(Arrays.equals(bytes, Files.readAllBytes(gameDir.resolve("openmapdelta/world/dimension/negative.tile"))),
                "copy fallback publishes cache files without replacement");
        check(Files.exists(oldTileBytes), "copy fallback keeps the original cache tree as a backup");
        check(Files.exists(configDir.resolve("openmapdelta/.legacy-data-migration-v1")),
                "successful copy fallback can publish the completion marker");
    }

    private static void rejectsOverlappingAndSymbolicLinkRoots(Path root) throws Exception {
        Path sameRoot = root.resolve("same");
        expectIOException(() -> DataDirectoryMigration.ensureMigrated(sameRoot, sameRoot),
                "overlapping app config and cache destinations are rejected");
        check(!Files.exists(sameRoot.resolve("openmapdelta/.legacy-data-migration-v1")),
                "overlap rejection does not mark migration complete");

        Path invalidConfig = root.resolve("invalid-config");
        Path invalidGame = root.resolve("invalid-game");
        Files.createDirectories(invalidGame);
        Files.writeString(invalidGame.resolve("openmapdelta"), "not a directory");
        expectIOException(() -> DataDirectoryMigration.ensureMigrated(invalidConfig, invalidGame),
                "invalid cache destination fails even without legacy cache files");

        Path configDir = root.resolve("unsafe-config");
        Path gameDir = root.resolve("unsafe-game");
        Path legacyConfig = configDir.resolve("odysseymap");
        Files.createDirectories(legacyConfig);
        Path outside = root.resolve("outside.json");
        Files.writeString(outside, "sensitive outside bytes");
        try {
            Files.createSymbolicLink(legacyConfig.resolve("external.json"), outside);
        } catch (UnsupportedOperationException | IOException | SecurityException unavailable) {
            return;
        }
        expectIOException(() -> DataDirectoryMigration.ensureMigrated(configDir, gameDir),
                "symbolic links in legacy settings are rejected");
        check(!Files.exists(configDir.resolve("openmapdelta/.legacy-data-migration-v1")),
                "symbolic link rejection prevents marker publication");
        check(Files.readString(outside).equals("sensitive outside bytes"),
                "symbolic link target is never read or modified");
    }

    private static void supportsNormalNestedMinecraftConfigRoot(Path root) throws Exception {
        Path gameDir = root.resolve("instance");
        Path configDir = gameDir.resolve("config");
        Path legacyConfig = configDir.resolve("odysseymap");
        Files.createDirectories(legacyConfig);
        Files.writeString(legacyConfig.resolve("config.json"), "{\"nested\":true}");
        saveTile(legacyConfig.resolve("cache"), "nested-instance", "minecraft:overworld", 0, -128, 0xFF556677, 39);

        DataDirectoryMigration.ensureMigrated(configDir, gameDir);

        check(Files.exists(configDir.resolve("openmapdelta/config.json")),
                "normal game/config directory nesting migrates successfully");
        assertTile(gameDir.resolve("openmapdelta"), "nested-instance", "minecraft:overworld", 0, -128, 0xFF556677, 39);
    }

    private static void rejectsJunctionEscapeWhenAvailable(Path root) throws Exception {
        if (!isWindows()) return;
        Path configDir = root.resolve("config");
        Path gameDir = root.resolve("game");
        Path legacyConfig = configDir.resolve("odysseymap");
        Path outside = root.resolve("outside");
        Path junction = legacyConfig.resolve("external");
        Files.createDirectories(legacyConfig);
        Files.createDirectories(outside);
        Path sentinel = outside.resolve("sentinel.txt");
        Files.writeString(sentinel, "keep outside data unchanged");

        Process process;
        try {
            String command = "mklink /J \"" + junction + "\" \"" + outside + "\"";
            process = new ProcessBuilder("cmd.exe", "/c", command).redirectErrorStream(true).start();
        } catch (IOException unavailable) {
            System.out.println("Skipping Windows junction regression: " + unavailable.getMessage());
            return;
        }
        String output;
        try (var stream = process.getInputStream()) {
            output = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
        if (process.waitFor() != 0) {
            System.out.println("Skipping Windows junction regression: " + output.strip());
            return;
        }

        var attributes = Files.readAttributes(junction, java.nio.file.attribute.BasicFileAttributes.class,
                LinkOption.NOFOLLOW_LINKS);
        check(attributes.isDirectory() && attributes.isOther(),
                "Windows junction is identified as an other directory without following it");
        expectIOException(() -> DataDirectoryMigration.ensureMigrated(configDir, gameDir),
                "junctions inside owned legacy trees are rejected");
        check(!Files.exists(configDir.resolve("openmapdelta/.legacy-data-migration-v1"), LinkOption.NOFOLLOW_LINKS),
                "junction rejection prevents completion marker publication");
        check(Files.readString(sentinel).equals("keep outside data unchanged"),
                "junction target outside migration roots is never read or modified");
    }

    private static void inaccessibleLegacyRootCanRetry(Path root) throws Exception {
        if (!isWindows()) return;
        Path configDir = root.resolve("config");
        Path gameDir = root.resolve("game");
        Path legacyConfig = configDir.resolve("odysseymap");
        Files.createDirectories(legacyConfig);
        Files.writeString(legacyConfig.resolve("config.json"), "{\"restored\":true}");
        AclFileAttributeView view = Files.getFileAttributeView(legacyConfig, AclFileAttributeView.class,
                LinkOption.NOFOLLOW_LINKS);
        if (view == null) return;

        List<AclEntry> original = view.getAcl();
        UserPrincipal owner = Files.getOwner(legacyConfig, LinkOption.NOFOLLOW_LINKS);
        AclEntry denyRead = AclEntry.newBuilder().setType(AclEntryType.DENY).setPrincipal(owner)
                .setPermissions(AclEntryPermission.READ_ATTRIBUTES, AclEntryPermission.READ_DATA,
                        AclEntryPermission.EXECUTE).build();
        List<AclEntry> denied = new ArrayList<>();
        denied.add(denyRead);
        denied.addAll(original);
        try {
            view.setAcl(denied);
        } catch (IOException | UnsupportedOperationException unavailable) {
            System.out.println("Skipping Windows ACL regression: " + unavailable.getMessage());
            return;
        }

        boolean denialEffective = false;
        try {
            try {
                Files.readAttributes(legacyConfig, java.nio.file.attribute.BasicFileAttributes.class,
                        LinkOption.NOFOLLOW_LINKS);
            } catch (AccessDeniedException deniedAttributes) {
                denialEffective = true;
            }
            if (!denialEffective) {
                System.out.println("Skipping Windows ACL regression: read-attribute denial was not effective.");
                return;
            }
            expectIOException(() -> DataDirectoryMigration.ensureMigrated(configDir, gameDir),
                    "inaccessible legacy settings prevent migration success");
            check(!Files.exists(configDir.resolve("openmapdelta/.legacy-data-migration-v1"), LinkOption.NOFOLLOW_LINKS),
                    "inaccessible legacy settings do not receive a success marker");
        } finally {
            view.setAcl(original);
        }

        DataDirectoryMigration.ensureMigrated(configDir, gameDir);
        check(Files.exists(configDir.resolve("openmapdelta/.legacy-data-migration-v1")),
                "restoring access lets the same roots retry and complete migration");
        check(Files.readString(configDir.resolve("openmapdelta/config.json")).equals("{\"restored\":true}"),
                "retry imports settings after access is restored");
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }

    private static void saveTile(Path cacheRoot, String world, String dimension,
                                 int x, int z, int color, int height) throws Exception {
        Tile tile = new Tile();
        tile.setPixel(7, 11, color, height);
        new TileStorage(cacheRoot, world, dimension).saveMerged(x, z, tile.snapshot());
    }

    private static void assertTile(Path cacheRoot, String world, String dimension,
                                   int x, int z, int color, int height) throws Exception {
        Tile.Snapshot snapshot = new TileStorage(cacheRoot, world, dimension).load(x, z).orElseThrow(
                () -> new AssertionError("Migrated tile is missing: " + world + " / " + dimension + " / " + x + "," + z));
        equal(color, snapshot.pixelAt((127 - 11) * Tile.SIZE + (127 - 7)), "tile pixels survive migration");
        equal(height, snapshot.heightAt((127 - 11) * Tile.SIZE + (127 - 7)), "tile heights survive migration");
    }

    private static void runFreshProcessToRecheck(Path configDir, Path gameDir) throws Exception {
        boolean windows = System.getProperty("os.name", "").toLowerCase().contains("win");
        Path java = Path.of(System.getProperty("java.home"), "bin", windows ? "java.exe" : "java");
        Process child = new ProcessBuilder(java.toString(), "-cp", System.getProperty("java.class.path"),
                DataDirectoryMigrationTest.class.getName(), "recheck", configDir.toString(), gameDir.toString())
                .redirectErrorStream(true).start();
        String output;
        try (var stream = child.getInputStream()) { output = new String(stream.readAllBytes(), StandardCharsets.UTF_8); }
        int exitCode = child.waitFor();
        check(exitCode == 0, "fresh process accepts a valid completion marker: " + output);
    }

    private static void deleteTree(Path root) throws IOException {
        if (!Files.exists(root, LinkOption.NOFOLLOW_LINKS)) return;
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override public FileVisitResult preVisitDirectory(Path directory,
                    java.nio.file.attribute.BasicFileAttributes attrs) throws IOException {
                if (attrs.isOther()) {
                    Files.delete(directory);
                    return FileVisitResult.SKIP_SUBTREE;
                }
                return FileVisitResult.CONTINUE;
            }
            @Override public FileVisitResult visitFile(Path file, java.nio.file.attribute.BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }
            @Override public FileVisitResult postVisitDirectory(Path directory, IOException failure) throws IOException {
                if (failure != null) throw failure;
                Files.delete(directory);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static void expectIOException(IoOperation operation, String message) throws Exception {
        try {
            operation.run();
            throw new AssertionError(message);
        } catch (IOException expected) {
            check(true, message);
        }
    }

    private static void check(boolean result, String message) {
        assertions++;
        if (!result) throw new AssertionError(message);
    }

    private static void equal(Object expected, Object actual, String message) {
        check(expected.equals(actual), message + ": expected " + expected + ", got " + actual);
    }

    @FunctionalInterface
    private interface IoOperation { void run() throws Exception; }
}

