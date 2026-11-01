// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.death;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import net.aosankaku.openmapdelta.marker.Marker;
import net.aosankaku.openmapdelta.marker.WaypointFileStore;
import net.aosankaku.openmapdelta.platform.DataPaths;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Deletion receipts contain no timer or inventory and prevent a late server report undoing a deletion. */
final class DeathPointTombstones {
    private static final class Receipt {
        String world;
        UUID playerId;
        UUID serverId;
        String dimension;
        int x, y, z;
        long tick;
        Receipt(String world, UUID playerId, UUID serverId, String dimension, int x, int y, int z, long tick) {
            this.world = world; this.playerId = playerId; this.serverId = serverId; this.dimension = dimension;
            this.x = x; this.y = y; this.z = z; this.tick = tick;
        }
        String world() { return world; }
        UUID playerId() { return playerId; }
        UUID serverId() { return serverId; }
        String dimension() { return dimension; }
        int x() { return x; }
        int y() { return y; }
        int z() { return z; }
        long tick() { return tick; }
    }
    private static final Gson GSON = new Gson();
    private static final List<Receipt> RECEIPTS = new ArrayList<>();
    private static boolean loaded;
    private static Path path() { return DataPaths.configDirectory().resolve("deathpoint-deletions.json"); }
    private static void load() {
        if (loaded) return;
        loaded = true;
        if (!Files.exists(path())) return;
        try (var reader = Files.newBufferedReader(path())) {
            List<Receipt> entries = GSON.fromJson(reader, new TypeToken<List<Receipt>>() {}.getType());
            if (entries != null) for (Receipt receipt : entries) {
                if (receipt != null && receipt.world != null && receipt.dimension != null && receipt.tick >= 0) RECEIPTS.add(receipt);
            }
        } catch (Exception failure) {
            org.slf4j.LoggerFactory.getLogger("OpenMapDelta").error("Failed to read deathpoint deletion receipts", failure);
        }
    }
    static void add(Marker marker) {
        load();
        DeathPointData data = marker.getDeathPoint();
        if (data == null) return;
        DeathPointMotion origin = data.motion(marker.getX(), marker.getY(), marker.getZ());
        RECEIPTS.add(new Receipt(marker.getImportedWorld(), data.playerId(), data.serverId(), marker.getDimension().identifier().toString(),
                origin.originX(), origin.originY(), origin.originZ(), data.deathTick()));
        try { WaypointFileStore.write(path(), writer -> GSON.toJson(RECEIPTS, writer)); }
        catch (Exception failure) { org.slf4j.LoggerFactory.getLogger("OpenMapDelta").error("Failed to save deathpoint deletion receipt", failure); }
    }
    static boolean contains(String world, UUID playerId, DeathPointUpdate report) {
        return contains(world, playerId, report, -1);
    }
    static boolean contains(String world, UUID playerId, DeathPointUpdate report, long liveDeathTick) {
        load();
        for (Receipt receipt : RECEIPTS) {
            if (!Objects.equals(world, receipt.world())) continue;
            if (report.deathId().equals(receipt.serverId())) return true;
            if (receipt.serverId() != null || !Objects.equals(receipt.playerId(), playerId)) continue;
            if (liveDeathTick >= 0 && Math.abs(liveDeathTick - report.gameTime()) < Math.abs(receipt.tick() - report.gameTime())) continue;
            if (receipt.dimension().equals(report.dimension()) && Math.abs(receipt.tick() - report.gameTime()) <= 40
                    && Math.abs(receipt.x() - report.x()) <= 2 && Math.abs(receipt.y() - report.y()) <= 2
                    && Math.abs(receipt.z() - report.z()) <= 2) return true;
        }
        return false;
    }
}
