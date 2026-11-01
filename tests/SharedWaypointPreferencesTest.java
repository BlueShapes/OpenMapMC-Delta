// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.shared;

import net.aosankaku.openmapdelta.platform.Services;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Exercises real Gson/atomic-file persistence using a temporary platform config directory. */
public final class SharedWaypointPreferencesTest {
    private static int checks;

    public static void main(String[] args) throws Exception {
        if (args.length != 2) throw new IllegalArgumentException("Expected mode and config directory");
        Path configDir = Path.of(args[1]);
        Services.setConfigDir(configDir);
        if (args[0].equals("write")) writeAndExercise(configDir);
        else if (args[0].equals("verify")) verifyReload(configDir);
        else throw new IllegalArgumentException("Unknown test mode");
        System.out.println("SharedWaypointPreferencesTest[" + args[0] + "]: " + checks + " checks passed");
    }

    private static void writeAndExercise(Path configDir) throws Exception {
        UUID world = id("world-a");
        UUID player = id("player-a");
        UUID hidden = id("hidden");
        UUID favorite = id("favorite");
        UUID overflow = id("overflow");
        SharedWaypointPreferences.setVisible(world, player, hidden, false);
        SharedWaypointPreferences.setFavorite(world, player, favorite, true);

        List<UUID> bulk = new ArrayList<>();
        for (int i = 0; i < 4094; i++) bulk.add(id("bulk-" + i));
        SharedWaypointPreferences.setVisible(world, player, bulk, false);
        Path scopeFile = scopePath(configDir, world, player);
        check(Files.isRegularFile(scopeFile), "scope is stored in a world/player-specific file");
        check(Files.size(scopeFile) < 4L * 1024 * 1024, "maximum-size scope serializes below the read limit");
        SharedWaypointPreferences.setVisible(world, player, overflow, false);
        check(SharedWaypointPreferences.isVisible(world, player, overflow), "4096-entry scope cap rejects overflow safely");

        UUID defaultsWorld = id("defaults-world");
        UUID defaultsPlayer = id("defaults-player");
        List<UUID> defaultIds = new ArrayList<>();
        for (int i = 0; i < 4096; i++) defaultIds.add(id("default-" + i));
        SharedWaypointPreferences.setVisible(defaultsWorld, defaultsPlayer, defaultIds, true);
        UUID newlyHidden = id("newly-hidden-after-defaults");
        SharedWaypointPreferences.setVisible(defaultsWorld, defaultsPlayer, newlyHidden, false);
        check(!SharedWaypointPreferences.isVisible(defaultsWorld, defaultsPlayer, newlyHidden),
                "no-op default visibility updates do not consume the scope entry limit");

        Set<UUID> retained = new LinkedHashSet<>(bulk);
        UUID removed = bulk.get(0);
        retained.remove(removed);
        retained.add(hidden);
        retained.add(favorite);
        SharedWaypointPreferences.retain(world, player, retained);
        check(SharedWaypointPreferences.isVisible(world, player, removed), "retain drops deleted waypoint preferences");
        check(!SharedWaypointPreferences.isVisible(world, player, bulk.get(1)), "retain preserves live waypoint preferences");

        UUID anotherPlayer = id("player-b");
        UUID anotherWorld = id("world-b");
        SharedWaypointPreferences.setFavorite(world, anotherPlayer, hidden, true);
        SharedWaypointPreferences.setFavorite(anotherWorld, player, hidden, true);
        check(SharedWaypointPreferences.isVisible(world, anotherPlayer, hidden), "preferences are isolated by player");
        check(SharedWaypointPreferences.isVisible(anotherWorld, player, hidden), "preferences are isolated by world");
        check(!SharedWaypointPreferences.isVisible(world, player, hidden), "hidden state remains scoped to its owner");
        check(!SharedWaypointPreferences.isFavorite(world, player, hidden), "another player's favorite does not bleed across scopes");
        check(Files.isRegularFile(scopePath(configDir, world, anotherPlayer))
                        && Files.isRegularFile(scopePath(configDir, anotherWorld, player)),
                "each player/world scope has an independent persistence file");

        // More scopes than the LRU holds force a cache eviction; the original scope must reload from disk.
        for (int i = 0; i < 20; i++) {
            SharedWaypointPreferences.setFavorite(id("lru-world-" + i), id("lru-player-" + i), id("lru-point-" + i), true);
        }
        check(SharedWaypointPreferences.isFavorite(world, player, favorite), "evicted scope reloads its durable favorite");
        check(!SharedWaypointPreferences.isVisible(world, player, hidden), "evicted scope reloads its durable visibility");

        UUID corruptWorld = id("corrupt-world");
        UUID corruptPlayer = id("corrupt-player");
        Path corruptFile = scopePath(configDir, corruptWorld, corruptPlayer);
        Files.createDirectories(corruptFile.getParent());
        Files.writeString(corruptFile, "{broken", StandardCharsets.UTF_8);
        SharedWaypointPreferences.setFavorite(corruptWorld, corruptPlayer, id("corrupt-point"), true);
        check(Files.readString(corruptFile, StandardCharsets.UTF_8).equals("{broken"),
                "corrupt preference files are preserved rather than overwritten");
    }

    private static void verifyReload(Path configDir) throws Exception {
        UUID world = id("world-a");
        UUID player = id("player-a");
        check(!SharedWaypointPreferences.isVisible(world, player, id("hidden")), "hidden state survives a process reload");
        check(SharedWaypointPreferences.isFavorite(world, player, id("favorite")), "favorite survives a process reload");
        check(SharedWaypointPreferences.isVisible(world, player, id("bulk-0")), "deleted id stays pruned after reload");
        check(!SharedWaypointPreferences.isVisible(world, player, id("bulk-1")), "remaining bulk preferences survive reload");
        check(SharedWaypointPreferences.isVisible(world, id("player-b"), id("hidden")), "other player's defaults remain independent");
        check(SharedWaypointPreferences.isVisible(id("world-b"), player, id("hidden")), "other world's defaults remain independent");
        check(SharedWaypointPreferences.isFavorite(world, id("player-b"), id("hidden")), "other player preference reloads independently");
        check(SharedWaypointPreferences.isFavorite(id("world-b"), player, id("hidden")), "other world preference reloads independently");
        check(Files.size(scopePath(configDir, world, player)) < 4L * 1024 * 1024,
                "persisted scope remains within the accepted reload size");
    }

    private static Path scopePath(Path configDir, UUID worldId, UUID playerId) {
        return configDir.resolve("openmapdelta").resolve("shared-waypoint-preferences")
                .resolve(worldId.toString()).resolve(playerId + ".json");
    }

    private static UUID id(String value) {
        return UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8));
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
}
