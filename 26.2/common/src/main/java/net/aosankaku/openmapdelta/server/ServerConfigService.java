// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.server;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.function.Consumer;
import net.aosankaku.openmapdelta.server.ServerConfigSnapshot.Result;

/** Loader-independent authorization and lifecycle. Invoke only on the server game thread. */
public final class ServerConfigService {
    public static final int MUTATION_RATE_TICKS = 10;
    private record Stamp(long revision, boolean administrator) {}
    private static final class State {
        final Map<UUID, Stamp> sent = new HashMap<>();
        final Map<UUID, Long> lastRequest = new HashMap<>();
        long lastTrimTick = Long.MIN_VALUE;
    }
    private static final Map<MinecraftServer, State> STATES = new WeakHashMap<>();
    private ServerConfigService() {}

    public static boolean canManage(MinecraftServer server, ServerPlayer player) {
        return server != null && player != null && (server.isSingleplayerOwner(player.nameAndId())
                || player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER));
    }

    public static void syncIfChanged(MinecraftServer server, ServerPlayer player,
                                     Consumer<ServerConfigSnapshot> sender) {
        State state = state(server);
        if (state.lastTrimTick != server.getTickCount()) {
            var connected = new java.util.HashSet<UUID>();
            server.getPlayerList().getPlayers().forEach(p -> connected.add(p.getUUID()));
            state.sent.keySet().retainAll(connected);
            state.lastRequest.keySet().retainAll(connected);
            state.lastTrimTick = server.getTickCount();
        }
        ServerConfigSnapshot snapshot = snapshot(server, player, Result.NONE);
        Stamp stamp = new Stamp(snapshot.revision(), snapshot.administrator());
        if (stamp.equals(state.sent.get(player.getUUID()))) return;
        sender.accept(snapshot);
        state.sent.put(player.getUUID(), stamp);
    }

    public static void handle(MinecraftServer server, ServerPlayer player, byte[] encoded,
                              Consumer<ServerConfigSnapshot> respond, Runnable broadcast) {
        if (server == null || player == null) return;
        // Authority is checked for every mutation, including while a former admin screen is open.
        if (!canManage(server, player)) {
            respond.accept(snapshot(server, player, Result.DENIED));
            return;
        }
        State state = state(server);
        long tick = server.getTickCount();
        Long previous = state.lastRequest.get(player.getUUID());
        if (previous != null && tick - previous < MUTATION_RATE_TICKS) {
            respond.accept(snapshot(server, player, Result.RATE_LIMITED));
            return;
        }
        state.lastRequest.put(player.getUUID(), tick);
        ServerConfigRequest request;
        try { request = ServerConfigRequest.decode(encoded); }
        catch (IllegalArgumentException invalid) {
            respond.accept(snapshot(server, player, Result.INVALID));
            return;
        }
        var oldUrl = ServerConfig.webMapUrl();
        long oldRevision = ServerConfig.revision();
        Result result = ServerConfig.update(request.revision(), request.values());
        ServerConfigSnapshot response = snapshot(server, player, result);
        respond.accept(response);
        state.sent.put(player.getUUID(), new Stamp(response.revision(), response.administrator()));
        if (result == Result.SAVED && ServerConfig.revision() != oldRevision) {
            // Existing clients also receive the established cave-policy envelope immediately.
            broadcast.run();
            if (!oldUrl.equals(ServerConfig.webMapUrl()) && ServerConfig.webMapUrl().isPresent()) {
                server.getPlayerList().getPlayers().forEach(ServerWebMapNotice::announce);
            }
        }
    }

    private static ServerConfigSnapshot snapshot(MinecraftServer server, ServerPlayer player, Result result) {
        boolean administrator = canManage(server, player) && ServerConfig.editable();
        return new ServerConfigSnapshot(ServerConfig.revision(), administrator,
                administrator ? ServerConfig.values() : null, result);
    }
    public static void playerJoined(MinecraftServer server, ServerPlayer player) {
        State state = state(server);
        state.sent.remove(player.getUUID());
        state.lastRequest.remove(player.getUUID());
    }
    public static void reset(MinecraftServer server) { STATES.remove(server); }
    private static State state(MinecraftServer server) { return STATES.computeIfAbsent(server, ignored -> new State()); }
}
