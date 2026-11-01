// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta;

import net.aosankaku.openmapdelta.network.ForgeNetwork;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;

/** Server-safe bootstrap; client handlers are constructed only on the client distribution. */
@Mod(OpenMapDeltaCommon.MOD_ID)
public final class OpenMapDelta {
    public OpenMapDelta() {
        ForgeNetwork.init();
        if (FMLEnvironment.dist == Dist.CLIENT) {
            new OpenMapDeltaForgeClient(FMLJavaModLoadingContext.get().getModEventBus());
        }
    }
}
