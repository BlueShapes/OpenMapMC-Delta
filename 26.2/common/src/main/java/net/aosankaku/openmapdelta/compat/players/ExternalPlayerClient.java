// Copyright (c) 2026 AoSankaku. Licensed under Apache-2.0.
package net.aosankaku.openmapdelta.compat.players;

import net.aosankaku.openmapdelta.config.OpenMapDeltaConfig;
import net.aosankaku.openmapdelta.network.ClientServerState;
import net.aosankaku.openmapdelta.network.PlayerPosition;
import net.aosankaku.openmapdelta.platform.DataPaths;
import net.aosankaku.openmapdelta.util.DimensionUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;

/** All publication and Minecraft access stay on the client thread; HTTP only runs on a bounded worker. */
public final class ExternalPlayerClient {
    private static final ExternalPlayerState STATE = new ExternalPlayerState();
    private static Object session, level;
    private static BlueMapConfig config;
    private static BlueMapEndpoint endpoint;
    private static ThreadPoolExecutor worker;
    private static Future<FetchResult> pending;
    private static BlueMapHttp.Request pendingRequest;
    private static long nextInstalled, nextHttp;
    private static String status = "unconfigured";
    private record FetchResult(List<PlayerPosition> players, long receivedAt) {}
    private ExternalPlayerClient() {}

    public static List<PlayerPosition> players() {
        // An accepted native snapshot is complete and authoritative, including player removals.
        if (ClientServerState.hasSnapshot()) return ClientServerState.players();
        if (!OpenMapDeltaConfig.MAP_MOD_ENABLED.get() || !OpenMapDeltaConfig.EXTERNAL_PLAYERS_ENABLED.get()) return List.of();
        return STATE.snapshot(session, level, System.nanoTime());
    }

    public static String serverKey(Minecraft mc) {
        return mc == null || mc.getCurrentServer() == null ? "" : mc.getCurrentServer().ip;
    }

    public static BlueMapConfig configuration() throws IOException {
        if (config == null) config = BlueMapConfig.load(DataPaths.configDirectory().resolve("remote-players.json"));
        return config;
    }

    public static String statusKey() { return "gui.odysseymap.remote_players." + status; }

    public static void settingsChanged() {
        cancelRequest();
        endpoint = null;
        STATE.replace(ExternalPlayerState.Source.BLUEMAP, session, level, List.of(), System.nanoTime());
        nextHttp = 0;
    }

    public static void tick(Minecraft mc) {
        if (mc == null || mc.level == null || mc.player == null || mc.getConnection() == null
                || !OpenMapDeltaConfig.MAP_MOD_ENABLED.get() || !OpenMapDeltaConfig.EXTERNAL_PLAYERS_ENABLED.get()) {
            reset(); status = "disabled"; return;
        }
        Object currentSession = mc.getConnection().getConnection();
        if (session != currentSession || level != mc.level) {
            reset(); session = currentSession; level = mc.level;
            STATE.beginSession(session, level);
        }
        long now = System.nanoTime();
        String dimension = DimensionUtil.dimensionId(mc.level.dimension());
        if (nextInstalled == 0 || now - nextInstalled >= 0) {
            var names = new HashMap<UUID, String>();
            for (var info : mc.getConnection().getOnlinePlayers()) names.put(info.getProfile().id(), info.getProfile().name());
            var context = new InstalledMapPlayers.Context(dimension, mc.player.getUUID(), names,
                    value -> journeyMapPosition(value, mc, dimension), ExternalPlayerClient::dimensionId);
            STATE.replace(ExternalPlayerState.Source.JOURNEYMAP, session, level, InstalledMapPlayers.readJourneyMap(context), now);
            STATE.replace(ExternalPlayerState.Source.XAERO, session, level, InstalledMapPlayers.readXaero(context), now);
            nextInstalled = now + 1_000_000_000L;
        }
        if (ClientServerState.hasSnapshot()) { cancelRequest(); status = "native"; return; }
        BlueMapEndpoint selected;
        try { selected = configuration().get(serverKey(mc), dimension).orElse(null); }
        catch (IOException invalid) { cancelRequest(); status = "config_error"; return; }
        if (!Objects.equals(selected, endpoint)) {
            settingsChanged(); endpoint = selected;
        }
        if (endpoint == null) { status = "unconfigured"; return; }
        if (pending != null && pending.isDone()) {
            try {
                FetchResult result = pending.get();
                STATE.replace(ExternalPlayerState.Source.BLUEMAP, session, level, result.players, result.receivedAt);
                status = "connected";
            } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); status = "failed"; }
            catch (ExecutionException | CancellationException failed) { status = "failed"; }
            pending = null;
            pendingRequest = null;
        }
        if (pending == null && (nextHttp == 0 || now - nextHttp >= 0)) {
            if (worker == null) worker = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
                    new ArrayBlockingQueue<>(1), task -> { Thread thread = new Thread(task, "OpenMap-BlueMap"); thread.setDaemon(true); return thread; });
            BlueMapHttp.Request request = new BlueMapHttp.Request(endpoint, dimension);
            pendingRequest = request;
            try { pending = worker.submit(() -> new FetchResult(request.fetch(), System.nanoTime())); }
            catch (RejectedExecutionException busy) { status = "failed"; }
            nextHttp = now + 3_000_000_000L;
        }
    }

    private static PlayerPosition journeyMapPosition(Object value, Minecraft mc, String dimension) {
        if (!(value instanceof Player player) || player.level() != mc.level || player.isInvisible()
                || !Double.isFinite(player.getX()) || !Double.isFinite(player.getY()) || !Double.isFinite(player.getZ())) return null;
        try { return new PlayerPosition(player.getUUID(), player.getGameProfile().name(), dimension,
                player.getBlockX(), player.getBlockY(), player.getBlockZ()); }
        catch (IllegalArgumentException invalid) { return null; }
    }

    @SuppressWarnings("unchecked")
    private static String dimensionId(Object value) {
        return value instanceof ResourceKey<?> key ? DimensionUtil.dimensionId((ResourceKey<Level>) key) : null;
    }

    private static void cancelRequest() {
        if (pendingRequest != null) { pendingRequest.cancel(); pendingRequest = null; }
        if (pending != null) { pending.cancel(true); pending = null; }
        if (worker != null) worker.purge();
    }

    public static void reset() {
        cancelRequest();
        if (worker != null) { worker.shutdownNow(); worker = null; }
        STATE.reset(); session = null; level = null; endpoint = null;
        nextInstalled = 0; nextHttp = 0; status = "unconfigured";
    }
}
