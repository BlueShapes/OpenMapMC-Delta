// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.server;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.aosankaku.openmapdelta.OpenMapDeltaCommon;
import net.aosankaku.openmapdelta.death.DeathPointUpdate;
import net.aosankaku.openmapdelta.death.DeathPointPolicy;
import net.aosankaku.openmapdelta.death.DeathPointMotion;
import net.aosankaku.openmapdelta.death.DeathPointPhysics;
import net.aosankaku.openmapdelta.death.DeathDropLedger;
import net.aosankaku.openmapdelta.death.DeathDropLedger.Drop;
import net.aosankaku.openmapdelta.death.CorpseCompat;
import net.aosankaku.openmapdelta.death.ItemLifetimeCompat;
import net.aosankaku.openmapdelta.death.DeathLootTimer;
import net.aosankaku.openmapdelta.marker.WaypointFileStore;
import net.aosankaku.openmapdelta.platform.Services;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.storage.LevelResource;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.Objects;
import java.lang.invoke.MethodType;
import java.util.function.BiConsumer;

/** Server-thread death/drop lineage tracking; reports go only to the player whose items were lost. */
public final class DeathRecoveryServer {
    private static final int MAX_DEATHS = 256;
    private static final int MAX_DROPS = 512;
    private static final int MAX_FILE_BYTES = 16 * 1024 * 1024;
    private static final Map<MinecraftServer, State> STATES = new WeakHashMap<>();
    private static final TagKey<net.minecraft.world.level.block.Block> LEGACY_GRAVES = TagKey.create(Registries.BLOCK,
            ResourceLocation.fromNamespaceAndPath("odysseymap", "graves"));
    private static final TagKey<net.minecraft.world.level.block.Block> OPENMAP_GRAVES = TagKey.create(Registries.BLOCK,
            ResourceLocation.fromNamespaceAndPath(OpenMapDeltaCommon.MOD_ID, "graves"));
    private static final String[] GRAVE_MODS = {"gravestone", "tombstone", "yigd", "universal_graves", "corpse"};
    private record CorpseRemoval(MinecraftServer server, UUID deathId, Entity corpse) {}
    private static final ThreadLocal<ArrayDeque<CorpseRemoval>> CORPSE_REMOVALS = ThreadLocal.withInitial(ArrayDeque::new);

    private static final class Death {
        final UUID owner;
        DeathPointUpdate report;
        final DeathPointMotion motion;
        final Map<UUID, Drop> drops = new LinkedHashMap<>();
        final TreeSet<String> collectors = new TreeSet<>();
        boolean lostDrops, ownerPicked, graveSeen;
        UUID graveEntity;
        BlockPos graveBlock;
        BlockPos graveEntityPos;
        boolean hadTrackedResources;
        boolean incompleteDropTracking;
        final Map<UUID, Integer> previousDropAges = new HashMap<>();
        final Map<UUID, Integer> previousCorpseAges = new HashMap<>();
        final Set<UUID> unloadedDrops = new HashSet<>();
        final Set<UUID> destroyedDrops = new HashSet<>();
        boolean corpseUnloaded;
        final java.util.Set<UUID> existingGraves = new java.util.HashSet<>();
        boolean graveBaselineComplete = true;
        long revision;
        Death(UUID owner, DeathPointUpdate report) {
            this.owner = owner; this.report = report;
            motion = new DeathPointMotion(report.x(), report.y(), report.z());
            if (report.position() != null) {
                var point = report.position();
                motion.follow(point.x(), point.y(), point.z(), point.velocityY(), report.gameTime());
            }
        }
    }
    private static final class State {
        final Path path;
        final Map<UUID, Death> deaths = new LinkedHashMap<>();
        final Map<UUID, UUID> dyingPlayers = new HashMap<>();
        final Map<UUID, Map<UUID, Long>> sent = new HashMap<>();
        boolean dirty, validStore = true, warned;
        State(Path path) { this.path = path; }
    }
    private DeathRecoveryServer() {}

    public static void initialize(MinecraftServer server) { state(server); }

    public static void beginDeath(ServerPlayer player) {
        var level = (ServerLevel) player.level();
        final DeathPointUpdate report;
        try {
            report = snapshotDeathReport(player, level);
        } catch (IllegalArgumentException invalidReport) {
            OpenMapDeltaCommon.LOGGER.warn("Smart Deathpoints report for player {} could not be created; continuing normal death handling",
                    player.getUUID());
            return;
        }

        State state = state(level.getServer());
        UUID id = report.deathId();
        var death = new Death(player.getUUID(), report);
        var previousGraves = protectedGraveEntities(level, report);
        death.graveBaselineComplete = previousGraves.size() <= MAX_DROPS;
        previousGraves.stream().limit(MAX_DROPS).forEach(entity -> death.existingGraves.add(entity.getUUID()));
        death.graveSeen = gravePresent(level, death);
        while (state.deaths.size() >= MAX_DEATHS) {
            UUID oldest = state.deaths.entrySet().stream().filter(entry -> entry.getValue().report.outcome() != DeathPointUpdate.Outcome.ACTIVE)
                    .map(Map.Entry::getKey).findFirst().orElse(state.deaths.keySet().iterator().next());
            state.deaths.remove(oldest);
        }
        state.deaths.put(id, death);
        state.dyingPlayers.put(player.getUUID(), id);
    }

    private static DeathPointUpdate snapshotDeathReport(ServerPlayer player, ServerLevel level) {
        var inventory = new ArrayList<DeathPointUpdate.InventoryItem>();
        for (int slot = 0; slot < player.getInventory().getContainerSize() && inventory.size() < DeathPointUpdate.MAX_INVENTORY; slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.isEmpty()) continue;
            String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
            List<String> tags = stack.getTags().map(tag -> tag.location().toString()).filter(tag -> tag.length() <= 192).limit(16).toList();
            boolean decorated = stack.isEnchanted() || stack.has(DataComponents.CUSTOM_NAME)
                    || stack.has(DataComponents.DYED_COLOR);
            inventory.add(new DeathPointUpdate.InventoryItem(id, Math.min(1_000_000, stack.getCount()), decorated, tags));
        }
        var pos = player.blockPosition();
        boolean graveExpected = false;
        for (String mod : GRAVE_MODS) if (Services.PLATFORM.isModLoaded(mod)) { graveExpected = true; break; }
        return new DeathPointUpdate(UUID.randomUUID(), level.dimension().location().toString(), pos.getX(), pos.getY(), pos.getZ(),
                level.getGameTime(), level.getGameRules().getBoolean(GameRules.RULE_KEEPINVENTORY), false,
                graveExpected, DeathPointUpdate.Outcome.ACTIVE, List.of(), inventory, Math.max(0, player.experienceLevel));
    }

    public static void recordDrop(ServerPlayer owner, ItemEntity item) {
        if (item == null || item.getItem().isEmpty()) return;
        State state = state(((ServerLevel) owner.level()).getServer());
        Death death = state.deaths.get(state.dyingPlayers.get(owner.getUUID()));
        if (death == null) return;
        if (death.drops.size() >= MAX_DROPS) {
            death.hadTrackedResources = true;
            death.incompleteDropTracking = true;
            state.dirty = true;
            return;
        }
        var pos = item.blockPosition();
        death.drops.put(item.getUUID(), new Drop(item.getUUID(), item.getItem().getCount(), pos.getX(), pos.getY(), pos.getZ()));
        death.unloadedDrops.remove(item.getUUID());
        death.destroyedDrops.remove(item.getUUID());
        death.hadTrackedResources = true;
    }

    /** Called after ServerLevel accepts an entity; this also binds an optional Corpse body to its owner. */
    public static void entityAdded(ServerLevel level, Entity entity) {
        if (entity == null) return;
        State state = STATES.get(level.getServer());
        if (state == null) return;
        if (CorpseCompat.isCorpse(entity)) associateCorpse(state, level, entity);
        if (!(entity instanceof ItemEntity item)) return;
        boolean matchedTrackedDrop = false;
        for (Death death : state.deaths.values()) {
            Drop tracked = death.drops.get(item.getUUID());
            if (tracked == null) continue;
            matchedTrackedDrop = true;
            if (level(level.getServer(), death.report.dimension()) == level) {
                var pos = item.blockPosition();
                if (!item.getItem().isEmpty()) death.drops.put(item.getUUID(), new Drop(item.getUUID(),
                        Math.min(tracked.count(), item.getItem().getCount()), pos.getX(), pos.getY(), pos.getZ()));
                death.unloadedDrops.remove(item.getUUID());
                death.destroyedDrops.remove(item.getUUID());
                death.previousDropAges.remove(item.getUUID());
                state.dirty = true;
            } else {
                death.unloadedDrops.add(item.getUUID());
                death.destroyedDrops.remove(item.getUUID());
                state.dirty = true;
            }
        }
        if (matchedTrackedDrop) return;
        if (item.getItem().isEmpty()) return;
        CorpseRemoval removal = currentCorpseRemoval();
        if (removal == null || removal.server() != level.getServer() || removal.deathId() == null
                || removal.corpse().level() != level) return;
        Death death = state.deaths.get(removal.deathId());
        if (death == null || !recoverable(death.report.outcome())) return;
        death.hadTrackedResources = true;
        if (death.drops.size() >= MAX_DROPS) {
            death.incompleteDropTracking = true;
            state.dirty = true;
            return;
        }
        var pos = item.blockPosition();
        death.drops.put(item.getUUID(), new Drop(item.getUUID(), item.getItem().getCount(), pos.getX(), pos.getY(), pos.getZ()));
        death.previousDropAges.remove(item.getUUID());
        if (death.report.outcome() == DeathPointUpdate.Outcome.EXPIRED) {
            death.report = changed(death, true, DeathPointUpdate.Outcome.ACTIVE);
            death.revision++;
        } else {
            death.report = changed(death, true, death.report.outcome());
            death.revision++;
        }
        state.dirty = true;
    }

    /** Records entity removal without consuming pickup or merge lineage before their RETURN hooks run. */
    public static void entityRemoved(Entity entity, Entity.RemovalReason reason) {
        if (!(entity.level() instanceof ServerLevel level)) return;
        State state = STATES.get(level.getServer());
        if (state == null) return;
        if (entity instanceof ItemEntity item) {
            boolean matchedTrackedDrop = false;
            for (Death death : state.deaths.values()) {
                Drop tracked = death.drops.get(item.getUUID());
                if (tracked == null) continue;
                matchedTrackedDrop = true;
                var pos = item.blockPosition();
                death.drops.put(item.getUUID(), new Drop(item.getUUID(), tracked.count(), pos.getX(), pos.getY(), pos.getZ()));
                death.previousDropAges.remove(item.getUUID());
                if (reason.shouldDestroy()) {
                    death.unloadedDrops.remove(item.getUUID());
                    death.destroyedDrops.add(item.getUUID());
                } else {
                    death.destroyedDrops.remove(item.getUUID());
                    death.unloadedDrops.add(item.getUUID());
                }
                state.dirty = true;
            }
            if (matchedTrackedDrop) return;
        }
        if (!CorpseCompat.isCorpse(entity)) return;
        Death death = state.deaths.values().stream().filter(value -> entity.getUUID().equals(value.graveEntity))
                .findFirst().orElse(null);
        if (death == null) return;
        death.graveEntityPos = entity.blockPosition();
        CorpseRemoval removal = currentCorpseRemoval();
        death.corpseUnloaded = !reason.shouldDestroy() && (removal == null || removal.corpse() != entity);
        state.dirty = true;
    }

    /** Scope only synchronous Corpse.remove spills; unrelated item spawns are never adopted. */
    public static void beginCorpseRemoval(Entity corpse) {
        if (!(corpse.level() instanceof ServerLevel level) || !CorpseCompat.isCorpse(corpse)) return;
        State state = STATES.get(level.getServer());
        if (state == null) return;
        Death death = deathForCorpse(state, level, corpse);
        if (death != null) {
            death.graveEntity = corpse.getUUID();
            death.graveEntityPos = corpse.blockPosition();
            death.corpseUnloaded = false;
            var snapshot = CorpseCompat.snapshot(corpse);
            if (snapshot != null && !snapshot.empty()) death.hadTrackedResources = true;
        }
        CORPSE_REMOVALS.get().push(new CorpseRemoval(level.getServer(), death == null ? null : death.report.deathId(), corpse));
    }

    public static void endCorpseRemoval(Entity corpse) {
        var stack = CORPSE_REMOVALS.get();
        if (!stack.isEmpty() && stack.peek().corpse() == corpse) stack.pop();
        if (stack.isEmpty()) CORPSE_REMOVALS.remove();
    }

    private static CorpseRemoval currentCorpseRemoval() {
        var stack = CORPSE_REMOVALS.get();
        if (stack.isEmpty()) { CORPSE_REMOVALS.remove(); return null; }
        // An exception can skip a RETURN injection. Require the actual optional-mod remove frame before
        // accepting another spawn, then discard any stale ThreadLocal scope.
        boolean removingCorpse = corpseRemovalOnStack();
        if (!removingCorpse) { CORPSE_REMOVALS.remove(); return null; }
        return stack.peek();
    }

    private static boolean corpseRemovalOnStack() {
        String removalDescriptor = MethodType.methodType(void.class, Entity.RemovalReason.class).descriptorString();
        return StackWalker.getInstance().walk(frames -> frames.anyMatch(frame ->
                frame.getClassName().equals("de.maxhenkel.corpse.entities.CorpseEntity")
                        && frame.getDescriptor().equals(removalDescriptor)));
    }

    private static void clearStaleCorpseRemovalScope() {
        if (!CORPSE_REMOVALS.get().isEmpty() && !corpseRemovalOnStack()) CORPSE_REMOVALS.remove();
    }

    private static void associateCorpse(State state, ServerLevel level, Entity corpse) {
        Death death = deathForCorpse(state, level, corpse);
        if (death == null) return;
        death.graveEntity = corpse.getUUID();
        death.graveEntityPos = corpse.blockPosition();
        death.graveSeen = true;
        var snapshot = CorpseCompat.snapshot(corpse);
        if (snapshot != null && !snapshot.empty()
                || !death.report.keepInventory() && !death.report.inventory().isEmpty()) death.hadTrackedResources = true;
        death.report = changed(death, death.report.hasDrops(), death.report.outcome());
        death.revision++;
        state.dirty = true;
    }

    private static Death deathForCorpse(State state, ServerLevel level, Entity corpse) {
        Death bound = state.deaths.values().stream().filter(death -> corpse.getUUID().equals(death.graveEntity)
                && level(level.getServer(), death.report.dimension()) == level).findFirst().orElse(null);
        if (bound != null) return bound;
        UUID owner = CorpseCompat.owner(corpse);
        if (owner == null) return null;
        UUID dying = state.dyingPlayers.get(owner);
        Death current = dying == null ? null : state.deaths.get(dying);
        if (current != null && level(level.getServer(), current.report.dimension()) == level) return current;
        return state.deaths.values().stream().filter(death -> recoverable(death.report.outcome())
                        && (owner == null || death.owner.equals(owner))
                        && level(level.getServer(), death.report.dimension()) == level)
                .filter(death -> {
                    long elapsed = level.getGameTime() - death.report.gameTime();
                    return elapsed >= -20 && elapsed <= 40
                            && corpse.distanceToSqr(death.report.x() + 0.5, death.report.y(), death.report.z() + 0.5) <= 16 * 16;
                })
                .min(java.util.Comparator.comparingDouble(death -> corpse.distanceToSqr(
                        death.report.x() + 0.5, death.report.y(), death.report.z() + 0.5))).orElse(null);
    }

    public static void finishDeath(ServerPlayer player) {
        var level = (ServerLevel) player.level();
        State state = state(level.getServer());
        Death death = state.deaths.get(state.dyingPlayers.remove(player.getUUID()));
        if (death == null) return;
        if (!player.isDeadOrDying()) { state.deaths.remove(death.report.deathId()); return; }
        // Grave mods can cancel captured vanilla drops. Retain actual removal/unload observations,
        // but an unadded captured entity must not count as a real death drop.
        death.drops.values().removeIf(drop -> !(level.getEntity(drop.id()) instanceof ItemEntity)
                && !death.unloadedDrops.contains(drop.id()) && !death.destroyedDrops.contains(drop.id()));
        death.hadTrackedResources = !death.drops.isEmpty();
        var corpse = findGraveEntity(level, death);
        var corpseSnapshot = corpse == null ? null : CorpseCompat.snapshot(corpse);
        if (corpseSnapshot != null && !corpseSnapshot.empty()) death.hadTrackedResources = true;
        if (corpse != null) {
            death.graveEntity = corpse.getUUID();
            death.graveEntityPos = corpse.blockPosition();
            death.corpseUnloaded = false;
        }
        death.graveSeen |= gravePresent(level, death);
        death.report = changed(death, !death.drops.isEmpty(), DeathPointUpdate.Outcome.ACTIVE);
        death.revision++;
        state.dirty = true;
    }

    /** Successful amount, after inventory insertion; partial pickups do not complete a recovery. */
    public static void pickedUp(ItemEntity item, ServerPlayer player, int amount, int beforeTotal) {
        if (amount <= 0 || !(item.level() instanceof ServerLevel level)) return;
        State state = state(level.getServer());
        int tracked = state.deaths.values().stream().filter(death -> recoverable(death.report.outcome()))
                .mapToInt(death -> death.drops.containsKey(item.getUUID()) ? death.drops.get(item.getUUID()).count() : 0).sum();
        int unassigned = DeathDropLedger.attributableAmount(beforeTotal, tracked, amount);
        for (Death death : state.deaths.values()) {
            Drop drop = death.drops.get(item.getUUID());
            if (drop == null || !recoverable(death.report.outcome()) || unassigned == 0) continue;
            death.unloadedDrops.remove(item.getUUID());
            death.destroyedDrops.remove(item.getUUID());
            int picked = Math.min(unassigned, drop.count());
            unassigned -= picked;
            if (picked == drop.count()) death.drops.remove(drop.id());
            else death.drops.put(drop.id(), drop.withCount(drop.count() - picked));
            if (death.owner.equals(player.getUUID())) death.ownerPicked = true;
            else if (death.collectors.size() < 64) death.collectors.add(player.getName().getString());
            ServerLevel deathLevel = level(level.getServer(), death.report.dimension());
            if (deathLevel != level && death.drops.containsKey(item.getUUID()))
                death.unloadedDrops.add(item.getUUID());
            if (deathLevel == level)
                death.motion.follow(item.getX(), item.getY(), item.getZ(), item.getDeltaMovement().y, level.getGameTime());
            // Partial pickup names must survive a server restart before another player completes recovery.
            var pickupReport = changed(death, true, death.report.outcome()).withPosition(position(death.motion));
            if (!pickupReport.equals(death.report)) {
                death.report = pickupReport;
                death.revision++;
            }
            if (!death.incompleteDropTracking && death.unloadedDrops.isEmpty()
                    && DeathDropLedger.completelyCollected(death.drops.size(), death.lostDrops)
                    && deathLevel != null && graveAreaLoaded(deathLevel, death)
                    && !trackedGravePresent(deathLevel, death) && !gravePresent(deathLevel, death)) {
                DeathPointUpdate.Outcome outcome = death.ownerPicked ? DeathPointUpdate.Outcome.RECOVERED : DeathPointUpdate.Outcome.COLLECTED_BY_OTHERS;
                death.report = changed(death, true, outcome);
                death.revision++;
            }
            state.dirty = true;
        }
    }

    /** Preserve ownership when vanilla merges a tracked stack into another entity, including partial merges. */
    public static void merged(ItemEntity destination, ItemEntity origin, int amount, int beforeTotal) {
        if (amount <= 0 || !(origin.level() instanceof ServerLevel level)) return;
        State state = state(level.getServer());
        int tracked = state.deaths.values().stream().filter(death -> recoverable(death.report.outcome()))
                .mapToInt(death -> death.drops.containsKey(origin.getUUID()) ? death.drops.get(origin.getUUID()).count() : 0).sum();
        int unassigned = DeathDropLedger.attributableAmount(beforeTotal, tracked, amount);
        for (Death death : state.deaths.values()) {
            Drop drop = death.drops.get(origin.getUUID());
            if (drop == null || !recoverable(death.report.outcome()) || unassigned == 0) continue;
            death.unloadedDrops.remove(origin.getUUID());
            death.destroyedDrops.remove(origin.getUUID());
            death.unloadedDrops.remove(destination.getUUID());
            death.destroyedDrops.remove(destination.getUUID());
            int moved = Math.min(unassigned, drop.count());
            unassigned -= moved;
            if (moved == drop.count()) death.drops.remove(drop.id());
            else death.drops.put(drop.id(), drop.withCount(drop.count() - moved));
            Drop previous = death.drops.get(destination.getUUID());
            if (previous == null && death.drops.size() >= MAX_DROPS) {
                death.incompleteDropTracking = true;
                state.dirty = true;
                continue;
            }
            if (level(level.getServer(), death.report.dimension()) != level) {
                if (death.drops.containsKey(origin.getUUID())) death.unloadedDrops.add(origin.getUUID());
                death.unloadedDrops.add(destination.getUUID());
            }
            var pos = destination.blockPosition();
            death.drops.put(destination.getUUID(), new Drop(destination.getUUID(), moved + (previous == null ? 0 : previous.count()),
                    pos.getX(), pos.getY(), pos.getZ()));
            state.dirty = true;
        }
    }

    public static void playerJoined(MinecraftServer server, ServerPlayer player) { state(server).sent.remove(player.getUUID()); }

    /** Called once per second by the loader. Does not request or generate chunks. */
    public static void tick(MinecraftServer server, BiConsumer<ServerPlayer, DeathPointUpdate> sender) {
        clearStaleCorpseRemovalScope();
        State state = state(server);
        for (Death death : state.deaths.values()) {
            if (state.dyingPlayers.containsValue(death.report.deathId())) continue;
            ServerLevel level = level(server, death.report.dimension());
            if (level != null && recoverable(death.report.outcome())) inspect(state, death, level, server);
            ServerPlayer owner = server.getPlayerList().getPlayer(death.owner);
            if (owner == null) continue;
            var sent = state.sent.computeIfAbsent(death.owner, ignored -> new HashMap<>());
            if (!Long.valueOf(death.revision).equals(sent.get(death.report.deathId()))) {
                sender.accept(owner, death.report);
                sent.put(death.report.deathId(), death.revision);
            }
        }
        state.sent.keySet().removeIf(id -> server.getPlayerList().getPlayer(id) == null);
        for (var ownerReports : state.sent.values()) ownerReports.keySet().retainAll(state.deaths.keySet());
        if (state.dirty) save(state);
    }

    private static void inspect(State state, Death death, ServerLevel level, MinecraftServer server) {
        var report = death.report;
        BlockPos previousGraveBlock = death.graveBlock;
        BlockPos previousGraveEntityPos = death.graveEntityPos;
        boolean previousCorpseUnloaded = death.corpseUnloaded;
        ItemEntity followed = null;
        var timerParts = new ArrayList<DeathLootTimer.Resource>();
        boolean unloadedResourceChunk = false;
        for (Drop drop : List.copyOf(death.drops.values())) {
            var entity = level.getEntity(drop.id());
            if (entity instanceof ItemEntity item && !item.isRemoved()) {
                death.hadTrackedResources = true;
                if (death.unloadedDrops.remove(drop.id()) || death.destroyedDrops.remove(drop.id())) state.dirty = true;
                if (!item.getItem().isEmpty() && followed == null) followed = item;
                var pos = item.blockPosition();
                int remaining = Math.min(drop.count(), item.getItem().getCount());
                if (remaining < drop.count()) { death.lostDrops = true; state.dirty = true; }
                if (remaining == 0) {
                    death.drops.remove(drop.id());
                    death.previousDropAges.remove(drop.id());
                    continue;
                }
                int age = item.getAge();
                Integer previousAge = death.previousDropAges.put(drop.id(), age);
                long lifetime = ItemLifetimeCompat.remaining(item);
                timerParts.add(new DeathLootTimer.Resource(lifetime, lifetime >= 0 && (previousAge == null || previousAge != age)));
                if (remaining > 0 && (remaining != drop.count() || pos.getX() != drop.x() || pos.getY() != drop.y() || pos.getZ() != drop.z())) {
                    death.drops.put(drop.id(), new Drop(drop.id(), remaining, pos.getX(), pos.getY(), pos.getZ()));
                    state.dirty = true;
                }
            } else {
                if (death.unloadedDrops.contains(drop.id())) {
                    unloadedResourceChunk = true;
                } else if (death.destroyedDrops.contains(drop.id())
                        || entityChunkReady(level, new BlockPos(drop.x(), drop.y(), drop.z()))) {
                    death.drops.remove(drop.id());
                    death.unloadedDrops.remove(drop.id());
                    death.destroyedDrops.remove(drop.id());
                    death.previousDropAges.remove(drop.id());
                    death.hadTrackedResources = true;
                    death.lostDrops = true;
                    state.dirty = true;
                } else {
                    unloadedResourceChunk = true;
                }
            }
        }
        Entity corpse = death.graveEntity == null ? findGraveEntity(level, death) : level.getEntity(death.graveEntity);
        if (corpse != null && !corpse.isRemoved()) {
            if (death.graveEntity == null) { death.graveEntity = corpse.getUUID(); state.dirty = true; }
            death.graveEntityPos = corpse.blockPosition();
            death.corpseUnloaded = false;
        } else corpse = null;
        boolean corpseEmpty = false;
        boolean unknownCorpseResources = false;
        if (corpse != null && CorpseCompat.isCorpse(corpse)) {
            death.corpseUnloaded = false;
            var snapshot = CorpseCompat.snapshot(corpse);
            if (snapshot == null) {
                // The server config or an optional Corpse API is unavailable. Keep a live body recoverable indefinitely.
                unknownCorpseResources = true;
            } else if (snapshot.empty()) {
                corpseEmpty = true;
                death.previousCorpseAges.put(corpse.getUUID(), snapshot.age());
            } else {
                death.hadTrackedResources = true;
                Integer previousAge = death.previousCorpseAges.put(corpse.getUUID(), snapshot.age());
                boolean ticking = previousAge == null || previousAge != snapshot.age();
                long futureLifetime = 0;
                for (Object value : snapshot.items()) {
                    if (!(value instanceof ItemStack stack) || stack.isEmpty()) continue;
                    futureLifetime = Math.max(futureLifetime, ItemLifetimeCompat.futureLifetime(stack, level));
                }
                if (futureLifetime == 0) unknownCorpseResources = true;
                else {
                    long force = snapshot.remainingForceTicks();
                    long remaining = force < 0 ? -1 : saturatingAdd(force, futureLifetime);
                    timerParts.add(new DeathLootTimer.Resource(remaining, remaining >= 0 && ticking));
                }
            }
        }
        if (followed != null) {
            death.motion.follow(followed.getX(), followed.getY(), followed.getZ(), followed.getDeltaMovement().y, level.getGameTime());
        } else if (corpse != null) {
            death.motion.follow(corpse.getX(), corpse.getY(), corpse.getZ(), corpse.getDeltaMovement().y, level.getGameTime());
        } else if (report.keepInventory() && !report.hasDrops() && !death.graveSeen && death.drops.isEmpty()) {
            death.motion.resetToOrigin(level.getGameTime());
        } else if ((report.hasDrops() || !report.keepInventory())
                && death.drops.isEmpty() && (!death.graveSeen || death.graveEntity != null)) {
            death.motion.tick(level.getGameTime(), 20, new DeathPointPhysics(level));
        } else death.motion.freeze(level.getGameTime());
        var position = position(death.motion);
        if (!position.equals(report.position())) {
            death.report = report.withPosition(position);
            death.revision++; state.dirty = true;
            report = death.report;
        }
        boolean loaded = graveAreaLoaded(level, death);
        boolean grave = gravePresent(level, death);
        if (!Objects.equals(previousGraveBlock, death.graveBlock)
                || !Objects.equals(previousGraveEntityPos, death.graveEntityPos)
                || previousCorpseUnloaded != death.corpseUnloaded) state.dirty = true;
        if (grave && !death.graveSeen) {
            death.graveSeen = true;
            death.report = changed(death, report.hasDrops(), report.outcome());
            death.revision++; state.dirty = true;
            report = death.report;
        }
        ServerPlayer owner = server.getPlayerList().getPlayer(death.owner);
        boolean nearby = owner != null && owner.level() == level && !owner.isDeadOrDying()
                && owner.distanceToSqr(report.recoveryX() + 0.5, report.recoveryY() + 0.5, report.recoveryZ() + 0.5) <= 16 * 16;
        boolean corpseChunkUnloaded = death.graveEntity != null && corpse == null
                && (death.corpseUnloaded || death.graveEntityPos == null || !entityChunkReady(level, death.graveEntityPos));
        if (corpseChunkUnloaded) unloadedResourceChunk = true;
        boolean unsupportedGrave = unsupportedGraveEntityPresent(level, death)
                || grave && (death.graveBlock != null || corpse == null && death.graveEntity == null);
        boolean unavailable = unloadedResourceChunk || !loaded
                && (report.graveExpected() || death.graveSeen || death.graveBlock != null || death.graveEntity != null);
        DeathPointUpdate.ItemTimer itemTimer = DeathLootTimer.sample(timerParts,
                unknownCorpseResources || unsupportedGrave || death.incompleteDropTracking,
                unavailable, death.hadTrackedResources, report.itemTimer());
        if (!Objects.equals(report.itemTimer(), itemTimer)) {
            death.report = report.withItemTimer(itemTimer);
            death.revision++; state.dirty = true;
            report = death.report;
        }
        if (itemTimer != null && !itemTimer.expired() && itemTimer.remainingTicks() != 0
                && report.outcome() == DeathPointUpdate.Outcome.EXPIRED) {
            death.report = changed(death, report.hasDrops(), DeathPointUpdate.Outcome.ACTIVE);
            death.revision++; state.dirty = true;
            report = death.report;
        } else if (itemTimer != null && itemTimer.expired() && !unavailable
                && report.outcome() == DeathPointUpdate.Outcome.ACTIVE) {
            death.report = changed(death, report.hasDrops(), DeathPointUpdate.Outcome.EXPIRED);
            death.revision++; state.dirty = true;
            report = death.report;
        }
        if (DeathPointPolicy.canRecover(nearby,
                loaded && !unloadedResourceChunk && !death.motion.falling() && !death.incompleteDropTracking,
                level.getGameTime() - report.gameTime(), report.hasDrops(), !death.drops.isEmpty(), death.graveSeen,
                grave && !(corpseEmpty && death.graveBlock == null && !unsupportedGrave), report.keepInventory())) {
            var outcome = !death.ownerPicked && !death.collectors.isEmpty() && !death.lostDrops
                    ? DeathPointUpdate.Outcome.COLLECTED_BY_OTHERS : DeathPointUpdate.Outcome.RECOVERED;
            death.report = changed(death, report.hasDrops(), outcome);
            death.revision++; state.dirty = true;
        } else if (itemTimer == null && report.outcome() == DeathPointUpdate.Outcome.ACTIVE
                && (report.hasDrops() || !report.keepInventory() && !report.graveExpected())
                && level.getGameTime() - report.gameTime() >= 6000) {
            death.report = changed(death, report.hasDrops(), DeathPointUpdate.Outcome.EXPIRED);
            death.revision++; state.dirty = true;
        }
    }

    private static boolean recoverable(DeathPointUpdate.Outcome outcome) {
        return outcome == DeathPointUpdate.Outcome.ACTIVE || outcome == DeathPointUpdate.Outcome.EXPIRED;
    }

    private static boolean graveAreaLoaded(ServerLevel level, Death death) {
        var report = death.report;
        return areaLoaded(level, report.recoveryX(), report.recoveryY(), report.recoveryZ())
                && (!(death.graveSeen || report.graveExpected()) || areaLoaded(level, report.x(), report.y(), report.z()))
                && (death.graveBlock == null || entityChunkReady(level, death.graveBlock))
                && (death.graveEntity == null || death.graveEntityPos != null && entityChunkReady(level, death.graveEntityPos));
    }

    private static boolean entityChunkReady(ServerLevel level, BlockPos pos) {
        return level.hasChunkAt(pos) && level.areEntitiesLoaded(net.minecraft.world.level.ChunkPos.asLong(pos));
    }

    private static boolean areaLoaded(ServerLevel level, int x, int y, int z) {
        for (int chunkX = (x - 9) >> 4; chunkX <= (x + 9) >> 4; chunkX++)
            for (int chunkZ = (z - 9) >> 4; chunkZ <= (z + 9) >> 4; chunkZ++)
                if (!entityChunkReady(level, new BlockPos(chunkX << 4, y, chunkZ << 4))) return false;
        return true;
    }

    private static boolean gravePresent(ServerLevel level, Death death) {
        var report = death.report;
        if (death.graveBlock != null) {
            // Keep an observed grave even if its accompanying items flow away or its chunk unloads.
            if (!entityChunkReady(level, death.graveBlock) || isGraveBlock(level, death.graveBlock)) return true;
            death.graveBlock = null;
        }
        death.graveBlock = findGraveBlock(level, report.recoveryX(), report.recoveryY(), report.recoveryZ());
        if (death.graveBlock == null) death.graveBlock = findGraveBlock(level, report.x(), report.y(), report.z());
        return death.graveBlock != null || findGraveEntity(level, death) != null || trackedGravePresent(level, death)
                || unsupportedGraveEntityPresent(level, death);
    }

    private static BlockPos findGraveBlock(ServerLevel level, int x, int y, int z) {
        for (BlockPos pos : BlockPos.betweenClosed(x - 2, y - 1, z - 2, x + 2, y + 2, z + 2)) {
            if (level.hasChunkAt(pos) && isGraveBlock(level, pos)) return pos.immutable();
        }
        return null;
    }

    private static boolean isGraveBlock(ServerLevel level, BlockPos pos) {
        var block = level.getBlockState(pos);
        String id = BuiltInRegistries.BLOCK.getKey(block.getBlock()).toString();
        return block.is(LEGACY_GRAVES) || block.is(OPENMAP_GRAVES) || ServerConfig.graveBlockIds().contains(id)
                || !id.startsWith("minecraft:") && (id.contains("grave") || id.contains("tomb") || id.contains("corpse"));
    }

    private static List<Entity> graveEntities(ServerLevel level, DeathPointUpdate report) {
        var area = new AABB(report.recoveryX() - 8, report.recoveryY() - 8, report.recoveryZ() - 8,
                report.recoveryX() + 9, report.recoveryY() + 9, report.recoveryZ() + 9);
        return level.getEntitiesOfClass(Entity.class, area, entity -> !entity.isRemoved()
                && BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString().equals("corpse:corpse"));
    }

    private static List<Entity> protectedGraveEntities(ServerLevel level, DeathPointUpdate report) {
        var found = new LinkedHashMap<UUID, Entity>();
        int[][] centers = {{report.recoveryX(), report.recoveryY(), report.recoveryZ()}, {report.x(), report.y(), report.z()}};
        for (int[] center : centers) {
            var area = new AABB(center[0] - 8, center[1] - 8, center[2] - 8,
                    center[0] + 9, center[1] + 9, center[2] + 9);
            for (Entity entity : level.getEntitiesOfClass(Entity.class, area, candidate -> !candidate.isRemoved()
                    && (CorpseCompat.isCorpse(candidate)
                    || BuiltInRegistries.ENTITY_TYPE.getKey(candidate.getType()).toString().equals("corpse:corpse"))))
                found.put(entity.getUUID(), entity);
        }
        return new ArrayList<>(found.values());
    }

    private static boolean unsupportedGraveEntityPresent(ServerLevel level, Death death) {
        for (Entity entity : protectedGraveEntities(level, death.report)) {
            if (death.existingGraves.contains(entity.getUUID()) || entity.getUUID().equals(death.graveEntity)) continue;
            UUID owner = CorpseCompat.owner(entity);
            if (owner != null && !death.owner.equals(owner)) continue;
            return true;
        }
        return false;
    }

    private static Entity findGraveEntity(ServerLevel level, Death death) {
        if (!death.graveBaselineComplete) return null;
        var report = death.report;
        return protectedGraveEntities(level, report).stream().filter(entity -> !death.existingGraves.contains(entity.getUUID()))
                .filter(entity -> death.owner.equals(CorpseCompat.owner(entity)))
                .min(java.util.Comparator.comparingDouble(entity -> entity.distanceToSqr(
                        report.recoveryX() + 0.5, report.recoveryY(), report.recoveryZ() + 0.5))).orElse(null);
    }

    private static boolean trackedGravePresent(ServerLevel level, Death death) {
        Entity entity = death.graveEntity == null ? null : level.getEntity(death.graveEntity);
        return entity != null && !entity.isRemoved();
    }

    private static DeathPointUpdate changed(Death death, boolean hasDrops, DeathPointUpdate.Outcome outcome) {
        var old = death.report;
        return new DeathPointUpdate(old.deathId(), old.dimension(), old.x(), old.y(), old.z(), old.gameTime(), old.keepInventory(),
                hasDrops, old.graveExpected() || death.graveSeen, outcome, List.copyOf(death.collectors), old.inventory(),
                old.experienceLevel(), old.position(), old.itemTimer());
    }

    private static long saturatingAdd(long left, long right) {
        if (left < 0 || right < 0) return -1;
        return left > 2L * Integer.MAX_VALUE + 1 - right ? 2L * Integer.MAX_VALUE + 1 : left + right;
    }

    private static DeathPointUpdate.Position position(DeathPointMotion motion) {
        return new DeathPointUpdate.Position(Math.max(-30_000_000, Math.min(30_000_000, motion.x())),
                Math.max(-2048, Math.min(4096, motion.y())), Math.max(-30_000_000, Math.min(30_000_000, motion.z())), motion.velocityY());
    }

    private static ServerLevel level(MinecraftServer server, String dimension) {
        for (ServerLevel level : server.getAllLevels()) if (level.dimension().location().toString().equals(dimension)) return level;
        return null;
    }

    public static void stop(MinecraftServer server) {
        State state = STATES.remove(server);
        if (state != null && state.dirty) save(state);
    }

    private static State state(MinecraftServer server) {
        return STATES.computeIfAbsent(server, current -> {
            var state = new State(current.getWorldPath(LevelResource.ROOT).resolve("data/odysseymap-death-recovery.json"));
            load(state);
            return state;
        });
    }

    private static void load(State state) {
        if (Files.notExists(state.path)) return;
        try {
            byte[] bytes;
            try (var input = Files.newInputStream(state.path)) { bytes = input.readNBytes(MAX_FILE_BYTES + 1); }
            if (bytes.length > MAX_FILE_BYTES) throw new IllegalArgumentException("Death recovery store too large");
            var root = JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject();
            if (root.get("version").getAsInt() != 1 || root.getAsJsonArray("deaths").size() > MAX_DEATHS)
                throw new IllegalArgumentException("Unsupported death recovery store");
            for (var entry : root.getAsJsonArray("deaths")) {
                var json = entry.getAsJsonObject();
                var report = DeathPointUpdate.decode(Base64.getDecoder().decode(json.get("report").getAsString()));
                var death = new Death(UUID.fromString(json.get("owner").getAsString()), report);
                death.collectors.addAll(report.collectorNames());
                death.ownerPicked = json.get("ownerPicked").getAsBoolean();
                death.lostDrops = json.get("lostDrops").getAsBoolean();
                death.graveSeen = json.get("graveSeen").getAsBoolean();
                if (json.has("corpseUnloaded")) death.corpseUnloaded = json.get("corpseUnloaded").getAsBoolean();
                if (json.has("graveBlock")) {
                    var block = json.getAsJsonArray("graveBlock");
                    if (block.size() != 3) throw new IllegalArgumentException("Invalid grave block");
                    int x = block.get(0).getAsBigDecimal().intValueExact(), y = block.get(1).getAsBigDecimal().intValueExact(),
                            z = block.get(2).getAsBigDecimal().intValueExact();
                    new DeathPointUpdate.Position(x, y, z, 0);
                    death.graveBlock = new BlockPos(x, y, z);
                }
                if (json.has("graveEntity")) death.graveEntity = UUID.fromString(json.get("graveEntity").getAsString());
                if (json.has("graveEntityPos")) {
                    var block = json.getAsJsonArray("graveEntityPos");
                    if (block.size() != 3) throw new IllegalArgumentException("Invalid corpse position");
                    int x = block.get(0).getAsBigDecimal().intValueExact(), y = block.get(1).getAsBigDecimal().intValueExact(),
                            z = block.get(2).getAsBigDecimal().intValueExact();
                    new DeathPointUpdate.Position(x, y, z, 0);
                    death.graveEntityPos = new BlockPos(x, y, z);
                }
                if (json.has("hadTrackedResources")) death.hadTrackedResources = json.get("hadTrackedResources").getAsBoolean();
                if (json.has("incompleteDropTracking")) death.incompleteDropTracking = json.get("incompleteDropTracking").getAsBoolean();
                if (json.has("graveBaselineComplete")) death.graveBaselineComplete = json.get("graveBaselineComplete").getAsBoolean();
                if (json.has("existingGraves")) {
                    if (json.getAsJsonArray("existingGraves").size() > MAX_DROPS) throw new IllegalArgumentException("Too many persisted corpses");
                    for (var value : json.getAsJsonArray("existingGraves")) death.existingGraves.add(UUID.fromString(value.getAsString()));
                }
                if (json.getAsJsonArray("drops").size() > MAX_DROPS) throw new IllegalArgumentException("Too many persisted drops");
                for (var value : json.getAsJsonArray("drops")) {
                    Drop drop = Drop.fromJson(value.getAsJsonObject());
                    if (death.drops.put(drop.id(), drop) != null) throw new IllegalArgumentException("Duplicate drop");
                }
                if (json.has("unloadedDrops")) {
                    var unloaded = json.getAsJsonArray("unloadedDrops");
                    if (unloaded.size() > MAX_DROPS) throw new IllegalArgumentException("Too many unloaded drops");
                    for (var value : unloaded) {
                        UUID id = UUID.fromString(value.getAsString());
                        if (!death.drops.containsKey(id) || !death.unloadedDrops.add(id))
                            throw new IllegalArgumentException("Invalid unloaded drop");
                    }
                }
                death.hadTrackedResources |= !death.drops.isEmpty();
                if (state.deaths.put(report.deathId(), death) != null) throw new IllegalArgumentException("Duplicate death");
            }
        } catch (Exception invalid) {
            state.deaths.clear(); state.validStore = false;
            OpenMapDeltaCommon.LOGGER.warn("Cannot read death recovery state {}; attribution remains session-only and this file is preserved", state.path, invalid);
        }
    }

    private static void save(State state) {
        if (!state.validStore) { state.dirty = false; return; }
        try {
            var root = new JsonObject(); root.addProperty("version", 1);
            var entries = new JsonArray();
            for (Death death : state.deaths.values()) {
                var value = new JsonObject(); value.addProperty("owner", death.owner.toString());
                value.addProperty("report", Base64.getEncoder().encodeToString(death.report.encode()));
                value.addProperty("ownerPicked", death.ownerPicked); value.addProperty("lostDrops", death.lostDrops);
                value.addProperty("graveSeen", death.graveSeen);
                value.addProperty("corpseUnloaded", death.corpseUnloaded);
                if (death.graveBlock != null) value.add("graveBlock", new Gson().toJsonTree(new int[]{death.graveBlock.getX(), death.graveBlock.getY(), death.graveBlock.getZ()}));
                if (death.graveEntity != null) value.addProperty("graveEntity", death.graveEntity.toString());
                if (death.graveEntityPos != null) value.add("graveEntityPos", new Gson().toJsonTree(new int[]{
                        death.graveEntityPos.getX(), death.graveEntityPos.getY(), death.graveEntityPos.getZ()}));
                value.addProperty("hadTrackedResources", death.hadTrackedResources);
                value.addProperty("incompleteDropTracking", death.incompleteDropTracking);
                value.addProperty("graveBaselineComplete", death.graveBaselineComplete);
                value.add("existingGraves", new Gson().toJsonTree(death.existingGraves));
                value.add("drops", new Gson().toJsonTree(death.drops.values()));
                value.add("unloadedDrops", new Gson().toJsonTree(new java.util.TreeSet<>(death.unloadedDrops)));
                entries.add(value);
            }
            root.add("deaths", entries);
            String json = new Gson().toJson(root);
            if (json.getBytes(StandardCharsets.UTF_8).length > MAX_FILE_BYTES) throw new IllegalArgumentException("Death recovery state too large");
            WaypointFileStore.write(state.path, writer -> writer.write(json));
            state.dirty = false; state.warned = false;
        } catch (Exception failure) {
            if (!state.warned) OpenMapDeltaCommon.LOGGER.warn("Cannot persist death recovery state {}", state.path, failure);
            state.warned = true;
        }
    }
}
