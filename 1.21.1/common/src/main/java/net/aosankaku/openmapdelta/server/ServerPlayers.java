// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.server;

import net.aosankaku.openmapdelta.network.ClientServerState;
import net.aosankaku.openmapdelta.network.PlayerPosition;
import net.aosankaku.openmapdelta.network.ServerSnapshot;
import net.aosankaku.openmapdelta.util.DimensionUtil;
import net.minecraft.server.MinecraftServer;
import java.util.ArrayList;

public final class ServerPlayers {
    private ServerPlayers() {}

    /** Called on the server game thread once per second by a loader adapter. */
    public static ServerSnapshot snapshot(MinecraftServer server) {
        var positions = new ArrayList<PlayerPosition>();
        for (var player : server.getPlayerList().getPlayers()) {
            if (positions.size() >= ClientServerState.MAX_PLAYERS) break;
            var pos = player.blockPosition();
            try {
                positions.add(new PlayerPosition(player.getUUID(), player.getGameProfile().getName(),
                        DimensionUtil.dimensionId(player.level().dimension()), pos.getX(), pos.getY(), pos.getZ()));
            } catch (IllegalArgumentException outsideMapBounds) {
                // Some server extensions create synthetic players or allow positions beyond world bounds.
            }
        }
        return new ServerSnapshot(ServerConfig.isCaveAllowed(), positions);
    }
}
