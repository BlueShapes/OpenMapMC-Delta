package xaero.common;

import xaero.hud.minimap.player.tracker.synced.ClientSyncedTrackedPlayerManager;
import xaero.hud.minimap.player.tracker.system.RenderedPlayerTrackerManager;

/** Test-only approximation of Xaero's public current-session path. */
public final class XaeroMinimapSession {
    private static XaeroMinimapSession currentSession;
    private final MinimapProcessor processor;
    private final ModMain modMain;

    public XaeroMinimapSession(ClientSyncedTrackedPlayerManager manager) {
        this(manager, null, true);
    }
    public XaeroMinimapSession(ClientSyncedTrackedPlayerManager manager, RenderedPlayerTrackerManager trackers,
                               boolean modernAccessor) {
        processor = new MinimapProcessor(manager);
        modMain = new ModMain(trackers, modernAccessor);
    }
    public static XaeroMinimapSession getCurrentSession() { return currentSession; }
    public static void setCurrentSession(XaeroMinimapSession session) { currentSession = session; }
    public MinimapProcessor getMinimapProcessor() { return processor; }
    public ModMain getModMain() { return modMain; }

    public static final class ModMain {
        private final RenderedPlayerTrackerManager trackers;
        private final boolean modernAccessor;
        public ModMain(RenderedPlayerTrackerManager trackers, boolean modernAccessor) {
            this.trackers = trackers;
            this.modernAccessor = modernAccessor;
        }
        public RenderedPlayerTrackerManager getRenderedPlayerTrackerManager() { return modernAccessor ? trackers : null; }
        public RenderedPlayerTrackerManager getPlayerTracker() { return modernAccessor ? null : trackers; }
    }

    public static final class MinimapProcessor {
        private final ClientSyncedTrackedPlayerManager manager;
        public MinimapProcessor(ClientSyncedTrackedPlayerManager manager) { this.manager = manager; }
        public ClientSyncedTrackedPlayerManager getSyncedTrackedPlayerManager() { return manager; }
    }
}
