// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.death;

import net.aosankaku.openmapdelta.marker.Marker;
import net.aosankaku.openmapdelta.marker.MarkerType;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

public final class DeathPointDeletionTest {
    public static void main(String[] args) throws Exception {
        var config = Files.createTempDirectory(Path.of(System.getProperty("codex.waypoint.test.root")), "openmapdelta-death-receipts-");
        System.setProperty("codex.waypoint.test.config", config.toString());
        UUID owner = UUID.randomUUID(), serverId = UUID.randomUUID();
        UUID legacyId = UUID.randomUUID();
        Path legacyDirectory = config.resolve("odysseymap");
        Files.createDirectories(legacyDirectory);
        Files.writeString(legacyDirectory.resolve("deathpoint-deletions.json"),
                "[{\"world\":\"world-A\",\"playerId\":\"" + owner + "\",\"serverId\":\"" + legacyId
                        + "\",\"dimension\":\"minecraft:overworld\",\"x\":1,\"y\":64,\"z\":2,\"tick\":80}]");
        check(DeathPointTombstones.contains("world-A", owner, report(legacyId, 80)),
                "legacy deletion receipts remain effective after rebranding and config migration");
        check(Files.exists(config.resolve("openmapdelta/deathpoint-deletions.json"))
                        && Files.exists(legacyDirectory.resolve("deathpoint-deletions.json")),
                "migration preserves legacy receipts and publishes the new config copy");
        Marker point = new Marker(UUID.randomUUID(), MarkerType.DEATH,
                ResourceKey.create(Registries.DIMENSION, ResourceLocation.tryParse("minecraft:overworld")), 1, 64, 2, "Deleted", DeathPointData.RED);
        point.setImportedWorld("world-A");
        var data = new DeathPointData(true, 100, true, false); data.setPlayerId(owner); data.setServerId(serverId); point.setDeathPoint(data);
        DeathPointTombstones.add(point);
        check(DeathPointTombstones.contains("world-A", owner, report(serverId, 100)), "known server death remains deleted");
        check(!DeathPointTombstones.contains("world-B", owner, report(serverId, 100)), "deletion does not affect another world");
        check(!DeathPointTombstones.contains("world-A", owner, report(UUID.randomUUID(), 110)), "different known server death at same coordinate remains allowed");
        data.setServerId(null);
        data.motion(point.getX(), point.getY(), point.getZ()).follow(1.5, 32.5, 2.5, 0, 120);
        point.setY(32);
        DeathPointTombstones.add(point);
        UUID lateId = UUID.randomUUID();
        check(DeathPointTombstones.contains("world-A", owner, report(lateId, 100)), "late first server report cannot undo local deletion");
        check(DeathPointTombstones.contains("world-A", owner, report(lateId, 100).withPosition(new DeathPointUpdate.Position(1.5, 32.5, 2.5, 0))),
                "deleting a landed point prevents a moving late report restoring it");
        check(!DeathPointTombstones.contains("world-A", UUID.randomUUID(), report(lateId, 100)), "coordinate receipt cannot suppress another account death");
        check(!DeathPointTombstones.contains("world-A", owner, report(lateId, 110), 110), "closer newly observed real death wins over deleted coordinate receipt");
        String json = Files.readString(config.resolve("openmapdelta/deathpoint-deletions.json"));
        check(!json.contains("timer") && !json.contains("Deleted"), "deletion receipts preserve no countdown or restorable marker");
        var entries = DeathPointTombstones.class.getDeclaredField("RECEIPTS"); entries.setAccessible(true); ((List<?>)entries.get(null)).clear();
        var loaded = DeathPointTombstones.class.getDeclaredField("loaded"); loaded.setAccessible(true); loaded.setBoolean(null, false);
        check(DeathPointTombstones.contains("world-A", owner, report(serverId, 100)), "deletion remains irreversible after reconnect and disk reload");
        try (var paths = Files.walk(config)) { for (var path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) Files.deleteIfExists(path); }
        System.out.println("Deathpoint deletion tests passed (11 migration, persistence and identity checks).");
    }
    private static DeathPointUpdate report(UUID id, long tick) {
        return new DeathPointUpdate(id, "minecraft:overworld", 1, 64, 2, tick, false, true, false,
                DeathPointUpdate.Outcome.ACTIVE, List.of(), List.of(), 0);
    }
    private static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
}
