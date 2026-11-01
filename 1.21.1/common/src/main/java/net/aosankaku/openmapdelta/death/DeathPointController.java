// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.death;

import net.aosankaku.openmapdelta.config.OpenMapDeltaConfig;
import net.aosankaku.openmapdelta.marker.Marker;
import net.aosankaku.openmapdelta.marker.MarkerManager;
import net.aosankaku.openmapdelta.marker.MarkerStorage;
import net.aosankaku.openmapdelta.marker.MarkerType;
import net.aosankaku.openmapdelta.platform.Services;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Client-thread death snapshots and conservative recovery fallback for servers without Odyssey. */
public final class DeathPointController {
    private static final DeathPointController INSTANCE = new DeathPointController();
    private record Snapshot(List<DeathPointPolicy.Item> items, int experienceLevel, java.util.Map<java.util.UUID, Integer> nearbyItems, java.util.Set<java.util.UUID> nearbyGraves) {}
    private Snapshot lastAlive;
    private record PendingDeath(ResourceKey<Level> dimension, BlockPos position, long tick, Snapshot inventory, boolean ruleUnknown) {}
    private PendingDeath pendingDeath;
    private int retainedInventoryChecks;
    private final java.util.Map<java.util.UUID, Integer> stableNearScans = new java.util.HashMap<>();
    private boolean wasDead;
    private Object connection;
    private int scanTicks;
    private boolean motionDirty;
    private DeathPointController() {}
    public static DeathPointController get() { return INSTANCE; }

    public void tick(Minecraft mc) {
        if (mc.player == null || mc.level == null) return;
        Object currentConnection = mc.getConnection() == null ? null : mc.getConnection().getConnection();
        if (connection != currentConnection) { lastAlive = null; pendingDeath = null; retainedInventoryChecks = 0; stableNearScans.clear(); wasDead = false; connection = currentConnection; }
        boolean dead = mc.player.isDeadOrDying();
        if (dead && !wasDead) createLocal(mc);
        if (!dead) {
            Snapshot current = snapshot(mc);
            if (wasDead || pendingDeath != null) inferRetainedInventory(mc, current);
            lastAlive = current;
        }
        wasDead = dead;
        for (DeathPointUpdate update : DeathPointInbox.drain()) applyReport(mc, update);
        boolean save = false;
        boolean scan = ++scanTicks % 20 == 0;
        long now = mc.level.getGameTime();
        for (Marker marker : List.copyOf(MarkerManager.get().getAllWaypoints())) {
            DeathPointData data = marker.getDeathPoint();
            if (data == null || !DeathPointPolicy.visibleToOwner(data.playerId(), mc.player.getUUID())
                    || !Objects.equals(marker.getImportedWorld(), MarkerManager.get().currentWorld())) continue;
            if (!OpenMapDeltaConfig.effectiveSmartDeathpoints() && data.isSmart()) { data.makeBasic(); save = true; }
            save |= data.update(now);
            if (!OpenMapDeltaConfig.effectiveSmartDeathpoints() || !data.isSmart()) continue;
            motionDirty |= updateMotion(mc, marker, now, DeathPointInbox.supported(currentConnection));
            if (!data.isCached() && DeathPointPolicy.finishConfirmedRecovery(data.recoveryConfirmed(), !dead,
                    marker.getDimension().equals(mc.level.dimension()), mc.player.distanceToSqr(marker.getX() + 0.5,
                    marker.getY() + 0.5, marker.getZ() + 0.5), now - data.deathTick())) {
                chat(mc, "odysseymap.death.recovered", marker.getLabel());
                MarkerManager.get().removeDeathPoint(marker.getId());
                continue;
            }
            // Observe actual drops while the player is still at the death location, including a hidden keep-inventory cache.
            if (scan && !DeathPointInbox.supported(currentConnection)) save |= observeRecovery(mc, marker, now);
            if (data.isCached()) continue;
            if (data.shouldRemove(now, OpenMapDeltaConfig.SMART_DEATHPOINT_BLACK_RETENTION_SECONDS.get())) {
                chat(mc, "odysseymap.death.removed", marker.getLabel());
                MarkerManager.get().removeDeathPoint(marker.getId());
                continue;
            }
            // Server tracking owns recovery whenever its capability is available.
        }
        if (scan && motionDirty) { save = true; motionDirty = false; }
        if (save) MarkerStorage.save();
    }

    private Snapshot snapshot(Minecraft mc) {
        List<DeathPointPolicy.Item> items = new ArrayList<>();
        var inventory = mc.player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            var stack = inventory.getItem(slot);
            if (stack.isEmpty()) continue;
            boolean decorated = stack.isEnchanted() || stack.has(DataComponents.CUSTOM_NAME) || stack.has(DataComponents.DYED_COLOR);
            items.add(new DeathPointPolicy.Item(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(), stack.getCount(),
                    stack.getTags().map(tag -> tag.location().toString()).toList(), decorated));
        }
        java.util.Map<java.util.UUID, Integer> nearby = new java.util.HashMap<>();
        for (var entity : mc.level.entitiesForRendering()) if (entity instanceof ItemEntity item
                && entity.distanceToSqr(mc.player) <= 100) nearby.put(entity.getUUID(), item.getItem().getCount());
        java.util.Set<java.util.UUID> graves = new java.util.HashSet<>();
        for (var entity : mc.level.entitiesForRendering()) if (entity.isAlive()
                && DeathPointPolicy.isGraveEntity(BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString())
                && entity.distanceToSqr(mc.player) <= 100) graves.add(entity.getUUID());
        return new Snapshot(List.copyOf(items), mc.player.experienceLevel, java.util.Map.copyOf(nearby), java.util.Set.copyOf(graves));
    }

    private void createLocal(Minecraft mc) {
        if (!OpenMapDeltaConfig.SHOW_DEATH.get()) return;
        // An authoritative death UUID avoids duplicate local guesses under network latency.
        if (DeathPointInbox.supported(connection)) return;
        BlockPos pos = mc.player.blockPosition();
        long now = mc.level.getGameTime();
        for (Marker reported : MarkerManager.get().getAllWaypoints()) {
            DeathPointData prior = reported.getDeathPoint();
            if (prior != null && prior.serverId() != null && !prior.localDeathObserved()
                    && DeathPointPolicy.visibleToOwner(prior.playerId(), mc.player.getUUID())
                    && Objects.equals(reported.getImportedWorld(), MarkerManager.get().currentWorld())
                    && reported.getDimension().equals(mc.level.dimension()) && Math.abs(prior.deathTick() - now) <= 5
                    && Math.abs(prior.motion(reported.getX(), reported.getY(), reported.getZ()).originX() - pos.getX()) <= 2
                    && Math.abs(prior.motion(reported.getX(), reported.getY(), reported.getZ()).originY() - pos.getY()) <= 2
                    && Math.abs(prior.motion(reported.getX(), reported.getY(), reported.getZ()).originZ() - pos.getZ()) <= 2) {
                prior.observeLocalDeath();
                MarkerStorage.save();
                return;
            }
        }
        boolean smart = OpenMapDeltaConfig.effectiveSmartDeathpoints();
        boolean keep = false;
        if (mc.getSingleplayerServer() != null) {
            var level = mc.getSingleplayerServer().getLevel(mc.level.dimension());
            if (level != null) keep = level.getGameRules().getBoolean(GameRules.RULE_KEEPINVENTORY);
        }
        boolean cached = smart && keep && !OpenMapDeltaConfig.SHOW_KEEP_INVENTORY_DEATHPOINTS.get();
        Snapshot inventory = lastAlive == null ? snapshot(mc) : lastAlive;
        pendingDeath = new PendingDeath(mc.level.dimension(), pos, now, inventory,
                mc.getSingleplayerServer() == null && !DeathPointInbox.supported(connection));
        retainedInventoryChecks = 0;
        if (!cached && smart && !DeathPointPolicy.shouldCreateSmart(inventory.items(), inventory.experienceLevel(),
                OpenMapDeltaConfig.SMART_DEATHPOINT_CHEAP_ITEMS.get())) return;
        DeathPointData data = new DeathPointData(smart, now, smart && !keep && !graveModPresent(), cached);
        data.setKeepInventory(keep);
        data.setPlayerId(mc.player.getUUID());
        data.observeLocalDeath();
        data.setExistingItemCounts(inventory.nearbyItems());
        data.setExistingGraves(inventory.nearbyGraves());
        MarkerManager.get().addDeathPoint(mc.level.dimension(), pos.getX(), pos.getY(), pos.getZ(), data);
    }

    private void applyReport(Minecraft mc, DeathPointUpdate report) {
        var manager = MarkerManager.get();
        Marker marker = find(report.deathId(), report.dimension(), report.x(), report.y(), report.z(), report.gameTime());
        if (DeathPointTombstones.contains(manager.currentWorld(), mc.player.getUUID(), report,
                marker == null ? -1 : marker.getDeathPoint().deathTick())) return;
        // The first full snapshot may already report a pickup or expiry; create before applying its outcome.
        if (marker == null) {
            if (!OpenMapDeltaConfig.SHOW_DEATH.get()) return;
            boolean smart = OpenMapDeltaConfig.effectiveSmartDeathpoints();
            boolean cached = smart && report.keepInventory() && !report.hasDrops() && !OpenMapDeltaConfig.SHOW_KEEP_INVENTORY_DEATHPOINTS.get();
            if (cached && manager.getAllWaypoints().stream().anyMatch(existing -> existing.getDeathPoint() != null
                    && existing.getDeathPoint().isCached() && DeathPointPolicy.visibleToOwner(existing.getDeathPoint().playerId(), mc.player.getUUID())
                    && Objects.equals(existing.getImportedWorld(), manager.currentWorld()) && existing.getDeathPoint().deathTick() > report.gameTime())) return;
            List<DeathPointPolicy.Item> items = report.inventory().stream()
                    .map(item -> new DeathPointPolicy.Item(item.id(), item.count(), item.tags(), item.valuable())).toList();
            if (!cached && smart && !DeathPointPolicy.shouldCreateSmart(items, report.experienceLevel(), OpenMapDeltaConfig.SMART_DEATHPOINT_CHEAP_ITEMS.get())) return;
            var dimension = ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(report.dimension()));
            DeathPointData data = new DeathPointData(smart, report.gameTime(), smart && (report.hasDrops() || !report.keepInventory() && !report.graveExpected()), cached);
            data.setPlayerId(mc.player.getUUID());
            marker = manager.addDeathPoint(dimension, report.x(), report.y(), report.z(), data);
        }
        DeathPointData data = marker.getDeathPoint();
        data.setKeepInventory(report.keepInventory());
        data.setServerId(report.deathId());
        if (!data.isSmart()) { MarkerStorage.save(); return; }
        if (report.position() != null) {
            var position = report.position();
            data.motion(marker.getX(), marker.getY(), marker.getZ()).follow(position.x(), position.y(), position.z(),
                    position.velocityY(), mc.level.getGameTime());
            syncPosition(marker);
        }
        switch (report.outcome()) {
            case ACTIVE -> {
                boolean cached = report.keepInventory() && !report.hasDrops() && !OpenMapDeltaConfig.SHOW_KEEP_INVENTORY_DEATHPOINTS.get();
                if (!cached && !DeathPointPolicy.shouldCreateSmart(report.inventory().stream()
                        .map(item -> new DeathPointPolicy.Item(item.id(), item.count(), item.tags(), item.valuable())).toList(),
                        report.experienceLevel(), OpenMapDeltaConfig.SMART_DEATHPOINT_CHEAP_ITEMS.get())) {
                    manager.removeDeathPoint(marker.getId());
                    return;
                }
                data.setTimerExpected(report.hasDrops() || !report.keepInventory() && !report.graveExpected());
                if (report.hasDrops()) { data.observeItems(); data.restore(); }
                if (report.graveExpected()) data.observeGrave();
                if (report.keepInventory() && !report.hasDrops() && !OpenMapDeltaConfig.SHOW_KEEP_INVENTORY_DEATHPOINTS.get()) {
                    data.setCached(true);
                    for (Marker older : List.copyOf(manager.getAllWaypoints())) if (!older.getId().equals(marker.getId())
                            && Objects.equals(older.getImportedWorld(), marker.getImportedWorld())
                            && DeathPointPolicy.replaceCached(data, older.getDeathPoint())) manager.removeDeathPoint(older.getId());
                }
            }
            case RECOVERED -> {
                data.confirmRecovery();
            }
            case COLLECTED_BY_OTHERS -> {
                if (!data.isCollectedByOthers()) chat(mc, "odysseymap.death.collected", marker.getLabel(), String.join(", ", report.collectorNames()));
                data.collectByOthers(mc.level.getGameTime());
                data.confirmRecovery();
            }
            case EXPIRED -> { if (report.itemTimer() == null) data.expire(); }
        }
        if (report.itemTimer() != null) {
            var timer = report.itemTimer();
            data.setItemTimer(timer.remainingTicks(), timer.ticking(), timer.expired(), mc.level.getGameTime());
        } else data.clearItemTimer();
        data.update(mc.level.getGameTime());
        MarkerStorage.save();
    }

    private Marker find(java.util.UUID serverId, String dimension, int x, int y, int z, long tick) {
        Marker closest = null;
        long closestTicks = Long.MAX_VALUE;
        for (Marker marker : MarkerManager.get().getAllWaypoints()) {
            DeathPointData data = marker.getDeathPoint();
            if (data == null || !DeathPointPolicy.visibleToOwner(data.playerId(), Minecraft.getInstance().player.getUUID())
                    || !Objects.equals(marker.getImportedWorld(), MarkerManager.get().currentWorld())) continue;
            if (serverId != null && serverId.equals(data.serverId())) return marker;
            if (data.serverId() != null && serverId != null) continue;
            if (marker.getDimension().location().toString().equals(dimension) && Math.abs(data.deathTick() - tick) <= 40
                    && Math.abs(data.motion(marker.getX(), marker.getY(), marker.getZ()).originX() - x) <= 2
                    && Math.abs(data.motion(marker.getX(), marker.getY(), marker.getZ()).originY() - y) <= 2
                    && Math.abs(data.motion(marker.getX(), marker.getY(), marker.getZ()).originZ() - z) <= 2
                    && Math.abs(data.deathTick() - tick) < closestTicks) { closest = marker; closestTicks = Math.abs(data.deathTick() - tick); }
        }
        return closest;
    }

    private void inferRetainedInventory(Minecraft mc, Snapshot afterRespawn) {
        PendingDeath death = pendingDeath;
        if (death == null || !death.ruleUnknown() || !OpenMapDeltaConfig.effectiveSmartDeathpoints()
                || DeathPointInbox.supported(connection)) { pendingDeath = null; return; }
        Snapshot before = death.inventory();
        // An empty zero-XP death provides no evidence about an unsynchronized server gamerule.
        if (before.items().isEmpty() && before.experienceLevel() == 0) { pendingDeath = null; return; }
        if (!before.items().equals(afterRespawn.items()) || afterRespawn.experienceLevel() < before.experienceLevel()) {
            if (++retainedInventoryChecks >= 20) { pendingDeath = null; retainedInventoryChecks = 0; }
            return;
        }
        pendingDeath = null;
        retainedInventoryChecks = 0;
        BlockPos pos = death.position();
        Marker marker = find(null, death.dimension().location().toString(), pos.getX(), pos.getY(), pos.getZ(), death.tick());
        if (marker != null && marker.getDeathPoint().observedItems()) return;
        boolean cached = !OpenMapDeltaConfig.SHOW_KEEP_INVENTORY_DEATHPOINTS.get();
        if (marker == null) {
            DeathPointData data = new DeathPointData(true, death.tick(), false, cached);
            data.setKeepInventory(true);
            data.setPlayerId(mc.player.getUUID());
            data.observeLocalDeath();
            data.setExistingItemCounts(before.nearbyItems());
            data.setExistingGraves(before.nearbyGraves());
            marker = MarkerManager.get().addDeathPoint(death.dimension(), pos.getX(), pos.getY(), pos.getZ(), data);
        } else {
            marker.getDeathPoint().setKeepInventory(true);
            marker.getDeathPoint().setTimerExpected(false);
            marker.getDeathPoint().setCached(cached);
            if (cached) for (Marker older : List.copyOf(MarkerManager.get().getAllWaypoints())) {
                if (!older.getId().equals(marker.getId()) && DeathPointPolicy.replaceCached(marker.getDeathPoint(), older.getDeathPoint())
                        && Objects.equals(older.getImportedWorld(), marker.getImportedWorld())) MarkerManager.get().removeDeathPoint(older.getId());
            }
            MarkerStorage.save();
        }
    }

    private boolean updateMotion(Minecraft mc, Marker marker, long now, boolean serverSupported) {
        DeathPointData data = marker.getDeathPoint();
        DeathPointMotion motion = data.motion(marker.getX(), marker.getY(), marker.getZ());
        if (!marker.getDimension().equals(mc.level.dimension()) || data.recoveryConfirmed() || data.isCollectedByOthers()) {
            motion.freeze(now); return false;
        }
        boolean changed = data.resetUnobservedMotion(now);
        ItemEntity followed = null;
        net.minecraft.world.entity.Entity corpse = null;
        if (!serverSupported) {
            for (var entity : mc.level.entitiesForRendering()) {
                if (entity.isAlive() && data.isNewGrave(entity.getUUID()) && DeathPointPolicy.isGraveEntity(BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString())
                        && (entity.getUUID().equals(data.followedGrave())
                        || corpse == null && entity.distanceToSqr(motion.x(), motion.y(), motion.z()) <= 64)) corpse = entity;
                if (!(entity instanceof ItemEntity item) || !item.isAlive() || item.getItem().isEmpty()) continue;
                if (item.getUUID().equals(data.followedItem())) { followed = item; continue; }
                if (followed == null && data.isDeathItem(item.getUUID(), item.getItem().getCount())
                        && item.distanceToSqr(motion.x(), motion.y(), motion.z()) <= 64) followed = item;
            }
        }
        if (followed != null) {
            changed |= data.observeDeathItem(followed.getUUID()) || !data.observedItems();
            data.followItem(followed.getUUID());
            data.observeItems(); data.restore();
            changed |= motion.follow(followed.getX(), followed.getY(), followed.getZ(), followed.getDeltaMovement().y, now);
        } else if (corpse != null) {
            changed |= !data.observedGrave() || !corpse.getUUID().equals(data.followedGrave());
            data.observeGrave(); data.followGrave(corpse.getUUID());
            changed |= motion.follow(corpse.getX(), corpse.getY(), corpse.getZ(), corpse.getDeltaMovement().y, now);
        } else if ((data.canPredictItemMotion() || data.followedGrave() != null
                || serverSupported && data.observedGrave() && motion.falling())
                && (!data.observedGrave() || data.observedItems() || data.followedGrave() != null
                || serverSupported && motion.falling())) {
            changed |= motion.tick(now, 1, new DeathPointPhysics(mc.level));
        } else motion.freeze(now);
        if (syncPosition(marker)) { stableNearScans.remove(marker.getId()); changed = true; }
        return changed;
    }

    private static boolean syncPosition(Marker marker) {
        var motion = marker.getDeathPoint().motion(marker.getX(), marker.getY(), marker.getZ());
        boolean changed = marker.getX() != motion.blockX() || marker.getY() != motion.blockY() || marker.getZ() != motion.blockZ();
        marker.setX(motion.blockX()); marker.setY(motion.blockY()); marker.setZ(motion.blockZ());
        return changed;
    }

    private boolean observeRecovery(Minecraft mc, Marker marker, long now) {
        if (!marker.getDimension().equals(mc.level.dimension())) { stableNearScans.remove(marker.getId()); return false; }
        int x = marker.getX(), y = marker.getY(), z = marker.getZ();
        if (mc.player.distanceToSqr(x + 0.5, y + 0.5, z + 0.5) > 256) { stableNearScans.remove(marker.getId()); return false; }
        for (int dx : new int[]{-8, 8}) for (int dz : new int[]{-8, 8}) if (!mc.level.hasChunkAt(new BlockPos(x + dx, y, z + dz))) { stableNearScans.remove(marker.getId()); return false; }
        int settledScans = stableNearScans.merge(marker.getId(), 1, (previous, ignored) -> Math.min(previous + 1, 2));
        boolean items = false;
        boolean changedItems = false;
        for (var entity : mc.level.entitiesForRendering()) if (entity instanceof ItemEntity item && entity.isAlive()
                && marker.getDeathPoint().isDeathItem(entity.getUUID(), item.getItem().getCount())
                && entity.distanceToSqr(x + 0.5, y + 0.5, z + 0.5) <= 64) { items = true; changedItems |= marker.getDeathPoint().observeDeathItem(entity.getUUID()); }
        boolean grave = false;
        for (var entity : mc.level.entitiesForRendering()) if (entity.isAlive()
                && marker.getDeathPoint().isNewGrave(entity.getUUID()) && DeathPointPolicy.isGraveEntity(BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString())
                && entity.distanceToSqr(x + 0.5, y + 0.5, z + 0.5) <= 64) { grave = true; break; }
        DeathPointData data = marker.getDeathPoint();
        boolean changedBlock = false, blockFound = false;
        int[] anchor = data.graveBlock();
        if (anchor != null) {
            var pos = new BlockPos(anchor[0], anchor[1], anchor[2]);
            if (!mc.level.hasChunkAt(pos)) { stableNearScans.remove(marker.getId()); return false; }
            blockFound = DeathPointPolicy.isGrave(BuiltInRegistries.BLOCK.getKey(mc.level.getBlockState(pos).getBlock()).toString());
            if (!blockFound) { data.clearGraveBlock(); changedBlock = true; }
        }
        var origin = data.motion(x, y, z);
        boolean originLoaded = true;
        for (int dx : new int[]{-3, 3}) for (int dz : new int[]{-3, 3})
            originLoaded &= mc.level.hasChunkAt(new BlockPos(origin.originX() + dx, origin.originY(), origin.originZ() + dz));
        if (!originLoaded && (data.observedGrave() || graveModPresent())) { stableNearScans.remove(marker.getId()); return changedBlock; }
        for (BlockPos center : new BlockPos[]{new BlockPos(x, y, z), new BlockPos(origin.originX(), origin.originY(), origin.originZ())}) {
            if (!mc.level.hasChunkAt(center)) continue;
            for (int dx = -3; dx <= 3 && !blockFound; dx++) for (int dy = -3; dy <= 3 && !blockFound; dy++) for (int dz = -3; dz <= 3; dz++) {
                BlockPos pos = center.offset(dx, dy, dz);
                if (mc.level.hasChunkAt(pos) && DeathPointPolicy.isGrave(BuiltInRegistries.BLOCK.getKey(mc.level.getBlockState(pos).getBlock()).toString())) {
                    changedBlock |= data.observeGraveBlock(pos.getX(), pos.getY(), pos.getZ()); blockFound = true; break;
                }
            }
        }
        grave |= blockFound;
        boolean changed = changedItems || changedBlock || items && !data.observedItems() || grave && !data.observedGrave();
        if (items) { data.observeItems(); data.restore(); }
        if (grave) data.observeGrave();
        if (!mc.player.isDeadOrDying() && DeathPointPolicy.canRecover(data, true,
                settledScans >= 2 && !data.motion(x, y, z).falling(), now - data.deathTick(), items, grave)) {
            chat(mc, "odysseymap.death.recovered", marker.getLabel());
            MarkerManager.get().removeDeathPoint(marker.getId());
        }
        return changed;
    }
    private static boolean graveModPresent() {
        for (String id : new String[]{"gravestone", "tombstone", "yigd", "universal_graves", "corpse"}) if (Services.PLATFORM.isModLoaded(id)) return true;
        return false;
    }
    private static void chat(Minecraft mc, String key, Object... arguments) {
        mc.player.displayClientMessage(Component.translatable(key, arguments), false);
    }
    public void onManualDelete(Marker marker) {
        stableNearScans.remove(marker.getId());
        if (pendingDeath != null && marker.getDeathPoint() != null && marker.getDeathPoint().deathTick() == pendingDeath.tick()
                && marker.getDimension().equals(pendingDeath.dimension())) { pendingDeath = null; retainedInventoryChecks = 0; }
        DeathPointTombstones.add(marker);
    }
    public void reset() { lastAlive = null; pendingDeath = null; retainedInventoryChecks = 0; stableNearScans.clear(); wasDead = false; connection = null; scanTicks = 0; DeathPointInbox.reset(); }
}
