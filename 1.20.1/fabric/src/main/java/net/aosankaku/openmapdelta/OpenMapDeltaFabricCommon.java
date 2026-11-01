// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta;

import net.aosankaku.openmapdelta.network.FabricServerNetwork;
import net.fabricmc.api.ModInitializer;

/** Common entrypoint contains no Minecraft client types. */
public final class OpenMapDeltaFabricCommon implements ModInitializer {
    @Override
    public void onInitialize() { FabricServerNetwork.init(); }
}
