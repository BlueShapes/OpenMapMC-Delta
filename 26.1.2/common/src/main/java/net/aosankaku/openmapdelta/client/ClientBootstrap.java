// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.client;

import net.aosankaku.openmapdelta.config.BlockOverrideConfig;
import net.aosankaku.openmapdelta.config.OpenMapDeltaConfig;
import net.aosankaku.openmapdelta.compat.ControllerSupport;
import net.aosankaku.openmapdelta.world.OpenMapDeltaClient;
import net.aosankaku.openmapdelta.platform.DataPaths;

/** Loaded only from the loader's client initialization path. */
public final class ClientBootstrap {
    private ClientBootstrap() {}

    public static void init() {
        DataPaths.ensureMigrated();
        OpenMapDeltaConfig.load();
        MapModSelectionController.init();
        BlockOverrideConfig.reload();
        OpenMapDeltaClient.init();
        ControllerSupport.init();
    }
}
