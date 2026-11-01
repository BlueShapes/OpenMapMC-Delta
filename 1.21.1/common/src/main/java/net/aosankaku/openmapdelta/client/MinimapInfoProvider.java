// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.client;

import net.aosankaku.openmapdelta.compat.players.ExternalPlayerClient;

import net.aosankaku.openmapdelta.config.MinimapInfoType;
import net.aosankaku.openmapdelta.config.OpenMapDeltaConfig;
import net.aosankaku.openmapdelta.marker.Marker;
import net.aosankaku.openmapdelta.marker.MarkerManager;
import net.aosankaku.openmapdelta.network.ClientServerState;
import net.aosankaku.openmapdelta.network.PlayerPosition;
import net.aosankaku.openmapdelta.util.DimensionUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.HashSet;
import java.util.Objects;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.UUID;

/** Client-side values rendered in the minimap's configurable information slots. */
public final class MinimapInfoProvider {
    private static final long FPS_WINDOW_NANOS = 5_000_000_000L;
    private static final RollingValueAverage FPS = new RollingValueAverage();
    private static final RollingPathAverage MOTION = new RollingPathAverage();
    private static final ServerTickEstimate SERVER_TPS = new ServerTickEstimate();
    private static final DateTimeFormatter REAL_TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT);

    private static long sessionStartedNanos;
    private static Player activePlayer;
    private static Level activeLevel;
    private static Object activeDimension;
    private static double lastX;
    private static double lastZ;

    private MinimapInfoProvider() {}

    /** Called on every client tick so wall-clock samples remain meaningful while a screen is open. */
    public static void tick(Minecraft mc) {
        long now = System.nanoTime();
        FPS.add(now, mc == null ? Double.NaN : mc.getFps());
        if (mc == null || mc.player == null || mc.level == null) {
            resetMotion();
            return;
        }

        Player player = mc.player;
        Level level = mc.level;
        Object dimension = level.dimension();
        if (sessionStartedNanos == 0L) sessionStartedNanos = now;
        if (player != activePlayer || level != activeLevel || !Objects.equals(dimension, activeDimension)) {
            resetMotion();
            activePlayer = player;
            activeLevel = level;
            activeDimension = dimension;
            lastX = player.getX();
            lastZ = player.getZ();
            MOTION.add(now, 0.0);
            return;
        }

        double dx = player.getX() - lastX;
        double dz = player.getZ() - lastZ;
        double distance = Math.hypot(dx, dz);
        // Large one-tick jumps are teleports; start a fresh averaging window at the destination.
        if (distance > 8.0) {
            MOTION.reset();
            MOTION.add(now, 0.0);
        } else {
            MOTION.add(now, distance);
        }
        lastX = player.getX();
        lastZ = player.getZ();
    }

    public static void reset() {
        FPS.reset();
        SERVER_TPS.reset();
        sessionStartedNanos = 0L;
        resetMotion();
    }

    /** Clears motion samples when the player, level, or dimension changes. */
    public static void resetMotion() {
        MOTION.reset();
        activePlayer = null;
        activeLevel = null;
        activeDimension = null;
        lastX = 0.0;
        lastZ = 0.0;
    }

    /** Vanilla time packets arrive about once a second and provide a conservative TPS estimate. */
    public static void observeServerTime(long serverGameTime) {
        SERVER_TPS.observe(serverGameTime, System.nanoTime());
    }

    public static String text(Minecraft mc, MinimapInfoType type) {
        if (type == null || type == MinimapInfoType.NONE) return "";
        if (type == MinimapInfoType.REAL_TIME) return value("real_time", LocalTime.now().format(REAL_TIME_FORMAT));
        if (type == MinimapInfoType.SESSION_TIME) {
            if (sessionStartedNanos == 0L) return unavailable();
            return value("session_time", MinimapInfoMath.formatDuration((System.nanoTime() - sessionStartedNanos) / 1_000_000_000L));
        }
        if (mc == null || mc.player == null || mc.level == null) return unavailable();

        Player player = mc.player;
        Level level = mc.level;
        BlockPos playerPos = player.blockPosition();
        long daytime = level.getDayTime();
        String dimensionId = DimensionUtil.dimensionId(level.dimension());
        return switch (type) {
            case NONE -> "";
            case WEATHER -> weather(level);
            case AVERAGE_FPS -> {
                double average = FPS.average(System.nanoTime(), FPS_WINDOW_NANOS);
                yield Double.isFinite(average) ? value("average_fps", Integer.toString((int) Math.round(average))) : unavailable();
            }
            case DAYTIME_12 -> value("daytime_12", MinimapInfoMath.formatClock12(daytime,
                    Component.translatable("gui.odysseymap.info_value.period."
                            + (MinimapInfoMath.clock24(daytime).hour() < 12 ? "am" : "pm")).getString()));
            case DAYTIME_24 -> value("daytime_24", MinimapInfoMath.formatClock(MinimapInfoMath.clock24(daytime)));
            case DAYTIME_CLOCK -> value("daytime_clock", MinimapInfoMath.customClock(daytime, OpenMapDeltaConfig.INFO_TICK_SECONDS.get()));
            case DAYTIME_TICKS -> value("daytime_ticks", Long.toString(MinimapInfoMath.normalizedDayTick(daytime)));
            case DAYTIME_SECONDS -> value("daytime_seconds", Long.toString((long) Math.floor(
                    MinimapInfoMath.normalizedDayTick(daytime) * OpenMapDeltaConfig.INFO_TICK_SECONDS.get())));
            case SLEEP -> sleepAllowed(level, playerPos) ? value("sleep.yes") : value("sleep.no");
            case PLAYER_COUNT -> value("player_count", Integer.toString(playerCount(mc)));
            case X -> value("coordinate.x", Integer.toString(playerPos.getX()));
            case Y -> value("coordinate.y", Integer.toString(playerPos.getY()));
            case Z -> value("coordinate.z", Integer.toString(playerPos.getZ()));
            case WORLD_DAYS -> value("world_days", Long.toString(MinimapInfoMath.worldDay(daytime)));
            case TARGET_LIGHT -> targetLight(mc, level);
            case FOOT_LIGHT -> value("foot_light", Integer.toString(level.getMaxLocalRawBrightness(playerPos)));
            case HORIZONTAL_SPEED -> value("horizontal_speed", number(horizontalSpeed(System.nanoTime())));
            case SESSION_TIME -> sessionStartedNanos == 0L ? unavailable()
                    : value("session_time", MinimapInfoMath.formatDuration((System.nanoTime() - sessionStartedNanos) / 1_000_000_000L));
            case REAL_TIME -> value("real_time", LocalTime.now().format(REAL_TIME_FORMAT));
            case CHUNK_COORDINATES -> value("chunk_coordinates", Integer.toString(playerPos.getX() >> 4), Integer.toString(playerPos.getZ() >> 4));
            case CHUNK_LOCAL -> value("chunk_local", Integer.toString(Math.floorMod(playerPos.getX(), 16)), Integer.toString(Math.floorMod(playerPos.getZ(), 16)));
            case TPS -> serverTps(mc);
            case LATENCY_TICKS -> latency(mc, level);
            case RAM -> ram();
            case FREE_SLOTS -> value("free_slots", Integer.toString(freeSlots(player)));
            case COORDINATES -> value("coordinates", Integer.toString(playerPos.getX()), Integer.toString(playerPos.getY()), Integer.toString(playerPos.getZ()));
            case DIMENSION_ID -> value("dimension_id", dimensionId);
            case DIMENSION_NAME -> value("dimension_name", dimensionName(dimensionId));
            case BIOME_ID -> value("biome_id", biomeId(level, playerPos));
            case BIOME_NAME -> value("biome_name", biomeName(level, playerPos));
            case TARGET_NAME -> targetName(mc, level);
            case TARGET_ID -> targetId(mc, level);
            case NEAREST_PLAYER -> nearestPlayer(mc, level, false);
            case NEAREST_PLAYER_COORDINATES -> nearestPlayer(mc, level, true);
            case NEAREST_WAYPOINT -> nearestWaypoint(player, level);
        };
    }

    private static String weather(Level level) {
        if (level.isThundering()) return value("weather.thunder");
        return level.isRaining() ? value("weather.rain") : value("weather.clear");
    }

    private static boolean sleepAllowed(Level level, BlockPos playerPos) {
        long dayTick = MinimapInfoMath.normalizedDayTick(level.getDayTime());
        boolean night = dayTick >= 12_542L && dayTick <= 23_459L;
        return level.dimensionType().natural() && (night || level.isThundering());
    }

    private static int playerCount(Minecraft mc) {
        if (mc.getConnection() != null) return mc.getConnection().getOnlinePlayers().size();
        return mc.level == null ? 0 : mc.level.players().size();
    }

    private static String targetLight(Minecraft mc, Level level) {
        if (!(mc.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) return unavailable();
        // Sample the block just outside the hit face, where the surface is visible to the player.
        BlockPos visibleFace = hit.getBlockPos().relative(hit.getDirection());
        return value("target_light", Integer.toString(level.getMaxLocalRawBrightness(visibleFace)));
    }

    private static String latency(Minecraft mc, Level level) {
        if (mc.getConnection() == null || mc.player == null) return unavailable();
        var info = mc.getConnection().getPlayerInfo(mc.player.getUUID());
        return info == null ? unavailable() : value("latency_ticks",
                number(info.getLatency() * level.tickRateManager().tickrate() / 1_000.0));
    }

    private static String ram() {
        Runtime runtime = Runtime.getRuntime();
        long maximum = runtime.maxMemory();
        if (maximum <= 0L) return unavailable();
        long used = runtime.totalMemory() - runtime.freeMemory();
        return value("ram", Long.toString(Math.round(100.0 * used / maximum)));
    }

    private static int freeSlots(Player player) {
        var inventory = player.getInventory();
        int free = 0;
        // Player inventory's main list contains 36 slots, including all nine hotbar slots.
        for (int slot = 0; slot < Math.min(36, inventory.getContainerSize()); slot++) {
            if (inventory.getItem(slot).isEmpty()) free++;
        }
        return free;
    }

    private static String serverTps(Minecraft mc) {
        var server = mc.getSingleplayerServer();
        if (server != null) {
            long averageTickNanos = server.getAverageTickTimeNanos();
            float targetTickRate = server.tickRateManager().tickrate();
            if (averageTickNanos > 0L && Float.isFinite(targetTickRate) && targetTickRate > 0.0F) {
                return value("tps", number(Math.min(targetTickRate, 1_000_000_000.0 / averageTickNanos)));
            }
        }
        OptionalDouble estimate = SERVER_TPS.ticksPerSecond(System.nanoTime());
        float clientTickRate = mc.level.tickRateManager().tickrate();
        return estimate.isPresent() && Float.isFinite(clientTickRate) && clientTickRate > 0.0F
                ? value("tps_estimated", number(Math.min(clientTickRate, estimate.getAsDouble()))) : unavailable();
    }

    private static double horizontalSpeed(long nowNanos) {
        long windowNanos = OpenMapDeltaConfig.INFO_SPEED_SECONDS.get() * 1_000_000_000L;
        return MOTION.blocksPerSecond(nowNanos, windowNanos);
    }

    private static String targetName(Minecraft mc, Level level) {
        BlockState state = targetBlock(mc, level);
        return state == null ? unavailable() : value("target_name", state.getBlock().getName().getString());
    }

    private static String targetId(Minecraft mc, Level level) {
        BlockState state = targetBlock(mc, level);
        if (state == null) return unavailable();
        Block block = state.getBlock();
        return value("target_id", BuiltInRegistries.BLOCK.getKey(block).toString());
    }

    private static BlockState targetBlock(Minecraft mc, Level level) {
        if (!(mc.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) return null;
        return level.getBlockState(hit.getBlockPos());
    }

    private static String biomeId(Level level, BlockPos position) {
        var key = level.getBiome(position).unwrapKey().orElse(null);
        return key == null ? "unknown" : key.location().toString();
    }

    private static String biomeName(Level level, BlockPos position) {
        String id = biomeId(level, position);
        String key = translationKey("biome", id);
        return value("biome_name", translatedOrHumanized(key, id));
    }

    private static String dimensionName(String id) {
        if (id.startsWith("minecraft:")) {
            String vanillaNameKey = "gui.odysseymap.info_value.dimension." + id.substring("minecraft:".length());
            if (Language.getInstance().has(vanillaNameKey)) return Component.translatable(vanillaNameKey).getString();
        }
        return translatedOrHumanized(translationKey("dimension", id), id);
    }

    private static String translationKey(String category, String id) {
        int separator = id.indexOf(':');
        if (separator < 0) return category + ".minecraft." + id;
        return category + "." + id.substring(0, separator) + "." + id.substring(separator + 1).replace('/', '.');
    }

    private static String translatedOrHumanized(String key, String id) {
        if (Language.getInstance().has(key)) return Component.translatable(key).getString();
        int separator = id.indexOf(':');
        String path = separator < 0 ? id : id.substring(separator + 1);
        String readable = path.replace('/', ' ').replace('_', ' ');
        if (readable.isEmpty()) return id;
        return Character.toUpperCase(readable.charAt(0)) + readable.substring(1);
    }

    private static String nearestPlayer(Minecraft mc, Level level, boolean includeCoordinates) {
        Player self = mc.player;
        if (self == null) return unavailable();
        Vec3 origin = self.position();
        Candidate nearest = null;
        Set<UUID> loadedPlayers = new HashSet<>();
        for (Player player : level.players()) {
            if (player == self) continue;
            loadedPlayers.add(player.getUUID());
            BlockPos position = player.blockPosition();
            Candidate candidate = new Candidate(player.getName().getString(), position.getX(), position.getY(), position.getZ(),
                    distanceSquared(origin, position.getX(), position.getY(), position.getZ()));
            if (nearest == null || candidate.distanceSquared() < nearest.distanceSquared()) nearest = candidate;
        }
        String dimension = DimensionUtil.dimensionId(level.dimension());
        for (PlayerPosition position : ExternalPlayerClient.players()) {
            if (position.id().equals(self.getUUID()) || loadedPlayers.contains(position.id())
                    || !dimension.equals(position.dimension())) continue;
            Candidate candidate = new Candidate(position.name(), position.x(), position.y(), position.z(),
                    distanceSquared(origin, position.x(), position.y(), position.z()));
            if (nearest == null || candidate.distanceSquared() < nearest.distanceSquared()) nearest = candidate;
        }
        if (nearest == null) return unavailable();
        if (!includeCoordinates) return value("nearest_player", nearest.name());
        return value("nearest_player_coordinates", nearest.name(), Integer.toString(nearest.x()),
                Integer.toString(nearest.y()), Integer.toString(nearest.z()));
    }

    private static String nearestWaypoint(Player player, Level level) {
        Vec3 origin = player.position();
        Marker nearest = null;
        double nearestDistance = Double.POSITIVE_INFINITY;
        for (Marker marker : MarkerManager.get().getVisibleMarkers(level.dimension())) {
            if (!marker.getType().isWorldWaypoint()) continue;
            double dx = origin.x - (marker.getX() + 0.5);
            double dz = origin.z - (marker.getZ() + 0.5);
            double dy = marker.hasKnownY() ? origin.y - marker.getY() : 0.0;
            double distance = dx * dx + dy * dy + dz * dz;
            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearest = marker;
            }
        }
        if (nearest == null) return unavailable();
        return value("nearest_waypoint", nearest.getLabel(), Integer.toString(nearest.getX()),
                nearest.hasKnownY() ? Integer.toString(nearest.getY()) : "?", Integer.toString(nearest.getZ()));
    }

    private static double distanceSquared(Vec3 origin, int x, int y, int z) {
        double dx = origin.x - (x + 0.5);
        double dy = origin.y - y;
        double dz = origin.z - (z + 0.5);
        return dx * dx + dy * dy + dz * dz;
    }

    private static String number(double value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }

    private static String value(String key, String... arguments) {
        return Component.translatable("gui.odysseymap.info_value." + key, (Object[]) arguments).getString();
    }

    private static String unavailable() {
        return value("unavailable");
    }

    private record Candidate(String name, int x, int y, int z, double distanceSquared) {}
}
