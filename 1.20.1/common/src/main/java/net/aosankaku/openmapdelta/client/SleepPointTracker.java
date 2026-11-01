// Copyright (c) 2026 AoSankaku. OpenMapMC Delta modifications; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.client;

import net.aosankaku.openmapdelta.config.OpenMapDeltaConfig;
import net.aosankaku.openmapdelta.marker.MarkerManager;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.RespawnAnchorBlock;
import net.minecraft.world.level.block.state.properties.BedPart;

import java.util.Objects;

/** Client-only sleep tracking; an installed server mod is not required. */
public final class SleepPointTracker {
    private static final SleepPointSession SESSION = new SleepPointSession();
    private static Object connection;
    private static SleepPointSession.Point checkedPoint;
    private static boolean integratedSpawnMatches;
    private static long queryRevision;
    private static final SleepSpawnEvidence SPAWN_EVIDENCE = new SleepSpawnEvidence();

    private SleepPointTracker() {}

    private static void ensureConnection(Minecraft mc) {
        if (connection != mc.getConnection()) {
            reset();
            connection = mc.getConnection();
        }
    }

    public static void tick(Minecraft mc) {
        if (mc.player == null || mc.level == null) { reset(); return; }
        ensureConnection(mc);
        BlockPos pos = mc.player.isSleeping()
                ? mc.player.getSleepingPos().orElse(mc.player.blockPosition()) : null;
        SleepPointSession.Point point = pos == null ? null : new SleepPointSession.Point(
                mc.level.dimension().location().toString(), pos.getX(), pos.getY(), pos.getZ());
        // Keep a pending query alive briefly after waking; a new sleep invalidates its callback.
        if (point != null && !Objects.equals(point, checkedPoint)) {
            checkedPoint = point;
            integratedSpawnMatches = false;
            long revision = ++queryRevision;
            var server = mc.getSingleplayerServer();
            if (server != null) {
                var playerId = mc.player.getUUID();
                Object expectedConnection = connection;
                server.execute(() -> {
                    var player = server.getPlayerList().getPlayer(playerId);
                    boolean matches = player != null && pos.equals(player.getRespawnPosition())
                            && point.dimension().equals(player.getRespawnDimension().location().toString());
                    mc.execute(() -> {
                        if (revision == queryRevision && connection == expectedConnection
                                && mc.getConnection() == expectedConnection) integratedSpawnMatches = matches;
                    });
                });
            }
        }
        boolean matches = mc.getSingleplayerServer() != null ? integratedSpawnMatches
                : pos != null && isVanillaSpawnBed(mc, pos);
        SleepPointSession.Point recorded = SESSION.tick(point, matches,
                OpenMapDeltaConfig.RECORD_NON_SPAWN_SLEEP.get());
        if (recorded != null) {
            var dimension = ResourceKey.create(Registries.DIMENSION, new ResourceLocation(recorded.dimension()));
            MarkerManager.get().setBedPoint(dimension, recorded.x(), recorded.y(), recorded.z());
        }
        if (point == null && checkedPoint != null) {
            // Allow a later sleep at the same bed to query the current server respawn again.
            checkedPoint = null;
        }
    }

    private static boolean isVanillaSpawnBed(Minecraft mc, BlockPos pos) {
        var block = mc.level.getBlockState(pos).getBlock();
        // Modded beds may deliberately suppress respawn even when the dimension permits it.
        return block.getClass() == BedBlock.class
                && BuiltInRegistries.BLOCK.getKey(block).getNamespace().equals("minecraft")
                && BedBlock.canSetSpawn(mc.level);
    }

    /** Capture before the request is sent, so a spawn packet can precede sleeping metadata. */
    public static void onBlockInteraction(BlockPos pos, InteractionHand hand) {
        Minecraft mc = Minecraft.getInstance();
        if (!OpenMapDeltaConfig.MAP_MOD_ENABLED.get() || mc.player == null || mc.level == null) return;
        if (mc.player.isSpectator() || (mc.player.isSecondaryUseActive()
                && (!mc.player.getMainHandItem().isEmpty() || !mc.player.getOffhandItem().isEmpty()))) return;
        ensureConnection(mc);
        var state = mc.level.getBlockState(pos);
        boolean anchorAttempt = state.getBlock() instanceof RespawnAnchorBlock
                && SleepSpawnEvidence.anchorAttempt(state.getValue(RespawnAnchorBlock.CHARGE),
                hand == InteractionHand.MAIN_HAND, mc.player.getItemInHand(hand).is(Items.GLOWSTONE),
                mc.player.getOffhandItem().is(Items.GLOWSTONE), true, RespawnAnchorBlock.canSetSpawn(mc.level));
        if (state.getBlock() instanceof BedBlock && state.getValue(BedBlock.PART) == BedPart.FOOT) {
            pos = pos.relative(state.getValue(BedBlock.FACING));
        }
        var point = new SleepPointSession.Point(mc.level.dimension().location().toString(),
                pos.getX(), pos.getY(), pos.getZ());
        SPAWN_EVIDENCE.interact(point, anchorAttempt, System.nanoTime());
    }

    public static void onSystemMessage(Component message) {
        Minecraft mc = Minecraft.getInstance();
        if (!OpenMapDeltaConfig.MAP_MOD_ENABLED.get() || mc.player == null || mc.level == null) return;
        if (isSpawnConfirmation(message)) {
            ensureConnection(mc);
            // The integrated server's actual respawn point is authoritative, including mod overrides.
            if (mc.getSingleplayerServer() != null) return;
            SESSION.confirmSpawn(SPAWN_EVIDENCE.confirm(System.nanoTime()));
        }
    }

    private static boolean isSpawnConfirmation(Component message) {
        if (message.getContents() instanceof TranslatableContents translated
                && translated.getKey().equals("block.minecraft.set_spawn")) return true;
        return message.getSiblings().stream().anyMatch(SleepPointTracker::isSpawnConfirmation);
    }

    public static void reset() {
        SESSION.reset();
        connection = null;
        checkedPoint = null;
        integratedSpawnMatches = false;
        SPAWN_EVIDENCE.reset();
        queryRevision++;
    }
}
