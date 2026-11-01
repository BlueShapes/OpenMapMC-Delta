// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta;

import net.aosankaku.openmapdelta.network.NeoForgeNetwork;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;

/** Server-safe bootstrap; client handlers are constructed only on the client distribution. */
@Mod(OpenMapDeltaCommon.MOD_ID)
public final class OpenMapDelta {
    public OpenMapDelta(IEventBus modEventBus) {
        NeoForgeNetwork.init(modEventBus);
        if (FMLEnvironment.dist == Dist.CLIENT) {
            new OpenMapDeltaNeoForgeClient(modEventBus);
        }
    }
}
