// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.marker;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

public final class DeathPointMetadataTest {
    public static void main(String[] args) throws Exception {
        deathpointStateAndOwnerPersistAcrossReload();
        keepInventoryMotionKnowledgeSurvivesReload();
        check(WaypointListPolicy.canRemove(MarkerType.DEATH), "deathpoints can be manually deleted");
        check(MarkerType.DEATH.isWorldWaypoint() && MarkerType.DEATH.isMapSelectable(), "deathpoints support world and map interactions");
        System.out.println("Deathpoint metadata tests passed.");
    }
    private static void deathpointStateAndOwnerPersistAcrossReload() throws Exception {
        Path config = Files.createTempDirectory(Path.of(System.getProperty("codex.waypoint.test.root")), "openmapdelta-death-config-");
        System.setProperty("codex.waypoint.test.config", config.toString());
        MarkerManager.get().clearWaypoints();
        UUID owner = UUID.randomUUID();
        Marker marker = marker(MarkerType.DEATH, dimension("minecraft:overworld"), "Renamed death");
        marker.setImportedWorld("server:example.invalid");
        var data = new net.aosankaku.openmapdelta.death.DeathPointData(true, 100, true, false);
        data.setPlayerId(owner);
        data.setServerId(UUID.randomUUID());
        UUID oldCorpse = UUID.randomUUID(), trackedCorpse = UUID.randomUUID();
        data.setExistingGraves(java.util.List.of(oldCorpse)); data.followGrave(trackedCorpse);
        data.observeGraveBlock(0, 64, 0);
        data.confirmRecovery();
        marker.setDeathPoint(data);
        int originY = marker.getY();
        data.motion(marker.getX(), marker.getY(), marker.getZ()).follow(12.5, 32.5, -4.5, -0.2, 110);
        marker.setX(12); marker.setY(32); marker.setZ(-5);
        marker.setIconId(net.aosankaku.openmapdelta.death.DeathPointData.ICON);
        MarkerManager.get().addWaypointDirect(marker);
        check(MarkerStorage.save(), "deathpoint save succeeds");
        MarkerStorage.load();
        Marker loaded = MarkerManager.get().getAllWaypoints().get(0);
        check(loaded.getType() == MarkerType.DEATH && loaded.getDeathPoint().isSmart(), "death type and smart metadata survive reload");
        check(loaded.getDeathPoint().playerId().equals(owner), "death owner survives reload without shared-marker semantics");
        check(!loaded.isShared() && loaded.getImportedWorld().equals("server:example.invalid"), "death account and world scope remain local");
        check(loaded.getDeathPoint().recoveryConfirmed(), "pending owner-approach recovery survives reload");
        var motion = loaded.getDeathPoint().motion(loaded.getX(), loaded.getY(), loaded.getZ());
        check(motion.originY() == originY && motion.y() == 32.5 && motion.velocityY() == -0.2
                && loaded.getY() == 32, "original death, moving location and speed survive reload independently");
        check(motion.renderY(0.5) == 32.5, "reloaded motion renders current position without a zero-origin jump");
        check(!loaded.getDeathPoint().isNewGrave(oldCorpse) && loaded.getDeathPoint().followedGrave().equals(trackedCorpse),
                "corpse lineage and exclusion baseline survive reconnect");
        check(java.util.Arrays.equals(loaded.getDeathPoint().graveBlock(), new int[]{0, 64, 0}),
                "observed static grave anchor survives flowing items and reconnect");
        loaded.getDeathPoint().update(120);
        check(loaded.getLabel().equals("Renamed death") && loaded.getDisplayLabel().equals("Renamed death [4:59]"), "rename base persists independently of timer suffix");
        loaded.getDeathPoint().update(6100);
        check(loaded.getColor() == net.aosankaku.openmapdelta.death.DeathPointData.BLACK
                && loaded.getIconId().equals(net.aosankaku.openmapdelta.death.DeathPointData.ICON_BLACK), "expired beam and map icon both turn black");
        check(MarkerStorage.save(), "expired death state saves");
        MarkerStorage.load();
        loaded = MarkerManager.get().getAllWaypoints().get(0);
        check(loaded.getDeathPoint().shouldRemove(12100, 300), "black retention timestamp survives reload");
        loaded.getDeathPoint().setItemTimer(12000, false, false, 12100);
        MarkerStorage.save(); MarkerStorage.load();
        loaded = MarkerManager.get().getAllWaypoints().get(0);
        loaded.getDeathPoint().update(24000);
        check(loaded.getDeathPoint().hasItemTimer() && loaded.getDeathPoint().itemTimerRemainingTicks(24000) == 12000
                && loaded.getColor() == net.aosankaku.openmapdelta.death.DeathPointData.RED,
                "authoritative paused loot lifetime overrides inferred expiry and survives reconnect without offline countdown");
        loaded.getDeathPoint().setItemTimer(-1, false, false, 24000);
        MarkerStorage.save(); MarkerStorage.load();
        loaded = MarkerManager.get().getAllWaypoints().get(0);
        loaded.getDeathPoint().update(50000);
        check(loaded.getDisplayLabel().equals("Renamed death"), "indefinitely protected Corpse loot retains its renamed label without timer after reload");
        loaded.getDeathPoint().setItemTimer(0, false, true, 50000);
        MarkerStorage.save(); MarkerStorage.load();
        loaded = MarkerManager.get().getAllWaypoints().get(0);
        check(!loaded.getDeathPoint().shouldRemove(55999, 300) && loaded.getDeathPoint().shouldRemove(56000, 300),
                "confirmed loot loss retains black point from actual loss time, not original five-minute fallback");
        loaded.getDeathPoint().setItemTimer(6000, true, false, 51000);
        MarkerStorage.save(); MarkerStorage.load();
        loaded = MarkerManager.get().getAllWaypoints().get(0);
        loaded.getDeathPoint().update(51100);
        check(!loaded.getDeathPoint().isExpired() && loaded.getColor() == net.aosankaku.openmapdelta.death.DeathPointData.RED
                && !loaded.getDeathPoint().shouldRemove(60000, 300),
                "available loot revives a persisted confirmed expiry without retaining its previous deletion deadline");
        loaded.getDeathPoint().setCached(true); loaded.getDeathPoint().restore();
        MarkerStorage.save(); MarkerStorage.load();
        loaded = MarkerManager.get().getAllWaypoints().get(0);
        loaded.getDeathPoint().setCached(true);
        check(!loaded.getDeathPoint().isCached(), "explicit keepInventory restoration cannot be hidden by replay after reload");
        deleteTree(config);
    }

    private static void keepInventoryMotionKnowledgeSurvivesReload() throws Exception {
        Path config = Files.createTempDirectory(Path.of(System.getProperty("codex.waypoint.test.root")), "openmapdelta-keepinventory-motion-");
        System.setProperty("codex.waypoint.test.config", config.toString());
        MarkerManager.get().clearWaypoints();
        Marker point = marker(MarkerType.DEATH, dimension("minecraft:overworld"), "Visible keeper");
        var data = new net.aosankaku.openmapdelta.death.DeathPointData(true, 100, false, false);
        data.setKeepInventory(true);
        var motion = data.motion(point.getX(), point.getY(), point.getZ());
        int originalY = motion.originY();
        motion.follow(point.getX() + 0.5, -128, point.getZ() + 0.5, -1, 200);
        point.setDeathPoint(data);
        MarkerManager.get().addWaypointDirect(point);
        check(MarkerStorage.save(), "visible keepInventory metadata saves");
        MarkerStorage.load();
        var loaded = MarkerManager.get().getAllWaypoints().get(0).getDeathPoint();
        check(loaded.keepsInventory() && !loaded.canPredictItemMotion(),
                "explicit keepInventory knowledge survives reload for visible, never-cached points");
        check(loaded.resetUnobservedMotion(201) && loaded.motion(0, 0, 0).y() == originalY,
                "reloaded no-loot point can correct an old falling prediction using its original death coordinates");
        loaded.setKeepInventory(false);
        check(MarkerStorage.save(), "authoritative gamerule correction saves");
        MarkerStorage.load();
        loaded = MarkerManager.get().getAllWaypoints().get(0).getDeathPoint();
        check(!loaded.keepsInventory() && loaded.canPredictItemMotion(),
                "an authoritative false gamerule also survives reload independently of a suppressed timer");
        deleteTree(config);
    }

    private static Marker marker(MarkerType type, ResourceKey<Level> dimension, String label) {
        return new Marker(UUID.randomUUID(), type, dimension, 1, 64, 2, label, type.defaultColor);
    }

    private static ResourceKey<Level> dimension(String id) {
        return ResourceKey.create(Registries.DIMENSION, ResourceLocation.tryParse(id));
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static void deleteTree(Path root) throws Exception {
        if (!Files.exists(root)) return;
        try (var paths = Files.walk(root)) {
            for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
        }
    }

}
