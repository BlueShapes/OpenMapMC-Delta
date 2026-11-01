// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.server;

import net.aosankaku.openmapdelta.web.WebMapChat;
import net.minecraft.server.level.ServerPlayer;

/** Invoked once by the loader's login event, for modded and vanilla players alike. */
public final class ServerWebMapNotice {
    private ServerWebMapNotice() {}

    public static void announce(ServerPlayer player) {
        ServerConfig.webMapUrl().ifPresent(uri -> player.sendSystemMessage(WebMapChat.shared(uri)));
    }
}
