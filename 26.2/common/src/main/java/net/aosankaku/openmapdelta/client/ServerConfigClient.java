// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.client;

import net.aosankaku.openmapdelta.server.ServerConfigRequest;
import net.aosankaku.openmapdelta.server.ServerConfigSnapshot;
import net.aosankaku.openmapdelta.server.ServerConfigValues;
import net.minecraft.client.Minecraft;

import java.util.function.Consumer;

/** Connection-bound client state for administrator-editable server settings. */
public final class ServerConfigClient {
    private static final long MIN_SEND_INTERVAL_NANOS = 10L * 50_000_000L;
    private static final long REQUEST_TIMEOUT_NANOS = 100L * 50_000_000L;

    private static Consumer<ServerConfigRequest> sender = ignored -> {};
    private static Object connection;
    private static ServerConfigSnapshot snapshot;
    private static ServerConfigValues inFlightValues;
    private static ServerConfigValues queuedValues;
    private static boolean localDraftPending;
    private static long inFlightSinceNanos;
    private static long inFlightRevision;
    private static long lastSendNanos;
    private static long nextRetryNanos;
    private static long retryDelayNanos;
    private static String feedbackKey;
    private static long stateVersion;
    private static long sessionVersion;

    private ServerConfigClient() {}

    /** Keep the platform transport while clearing session-bound state on logout. */
    public static synchronized void configureSender(Consumer<ServerConfigRequest> configuredSender) {
        sender = configuredSender == null ? ignored -> {} : configuredSender;
    }

    /** Accept only packets belonging to the currently active Minecraft connection. */
    public static synchronized void apply(Object packetConnection, Object currentConnection,
                                          ServerConfigSnapshot incoming) {
        if (packetConnection == null || packetConnection != currentConnection || incoming == null) return;
        if (connection != null && connection != currentConnection) clearSession();
        connection = currentConnection;

        if (snapshot != null && incoming.revision() < snapshot.revision()) return;

        boolean changedRevision = snapshot != null && incoming.revision() > snapshot.revision();
        boolean hadPendingWrite = inFlightValues != null || queuedValues != null || localDraftPending;
        ServerConfigValues completedValues = inFlightValues;
        long completedRevision = inFlightRevision;
        if (incoming.result() == ServerConfigSnapshot.Result.NONE && changedRevision && hadPendingWrite) {
            // Another administrator changed the server while this client had a draft.
            queuedValues = null;
            inFlightValues = null;
            localDraftPending = false;
            resetRetry();
            feedbackKey = "gui.odysseymap.server_config.status.conflict";
        } else if (incoming.result() != ServerConfigSnapshot.Result.NONE) {
            inFlightValues = null;
            inFlightRevision = 0L;
            switch (incoming.result()) {
                case SAVED -> {
                    feedbackKey = "gui.odysseymap.server_config.status.saved";
                    retryDelayNanos = 0L;
                    nextRetryNanos = 0L;
                }
                case DENIED -> {
                    queuedValues = null;
                    localDraftPending = false;
                    inFlightRevision = 0L;
                    resetRetry();
                    feedbackKey = "gui.odysseymap.server_config.status.permission";
                }
                case CONFLICT -> {
                    queuedValues = null;
                    localDraftPending = false;
                    inFlightRevision = 0L;
                    resetRetry();
                    feedbackKey = "gui.odysseymap.server_config.status.conflict";
                }
                case INVALID -> {
                    queuedValues = null;
                    localDraftPending = false;
                    inFlightRevision = 0L;
                    resetRetry();
                    feedbackKey = "gui.odysseymap.server_config.status.invalid";
                }
                case STORAGE_ERROR -> {
                    queuedValues = null;
                    localDraftPending = false;
                    inFlightRevision = 0L;
                    resetRetry();
                    feedbackKey = "gui.odysseymap.server_config.status.storage_error";
                }
                case RATE_LIMITED -> {
                    if (incoming.revision() != completedRevision) {
                        queuedValues = null;
                        localDraftPending = false;
                        inFlightRevision = 0L;
                        resetRetry();
                        feedbackKey = "gui.odysseymap.server_config.status.conflict";
                    } else {
                        if (queuedValues == null) queuedValues = completedValues;
                        localDraftPending = false;
                        retryDelayNanos = retryDelayNanos == 0L
                                ? 1_000_000_000L : Math.min(5_000_000_000L, retryDelayNanos * 2L);
                        nextRetryNanos = System.nanoTime() + retryDelayNanos;
                        feedbackKey = "gui.odysseymap.server_config.status.rate_limited";
                    }
                }
                case NONE -> { }
            }
        } else if (!incoming.administrator()) {
            queuedValues = null;
            inFlightValues = null;
            inFlightRevision = 0L;
            resetRetry();
            feedbackKey = "gui.odysseymap.server_config.status.permission";
        } else if (feedbackKey == null || feedbackKey.equals("gui.odysseymap.server_config.status.permission")) {
            feedbackKey = "gui.odysseymap.server_config.status.ready";
        }

        snapshot = incoming;
        if (!incoming.administrator()) localDraftPending = false;
        if (queuedValues != null && queuedValues.equals(incoming.values())) queuedValues = null;
        stateVersion++;
        flushIfReady(System.nanoTime());
    }

    /** Current settings are editable only after an authoritative administrator snapshot. */
    public static synchronized boolean canManage() {
        return isCurrentSession() && snapshot != null && snapshot.administrator() && snapshot.values() != null;
    }

    public static synchronized boolean hasSnapshot() {
        return isCurrentSession() && snapshot != null;
    }

    public static synchronized ServerConfigSnapshot snapshot() {
        return isCurrentSession() ? snapshot : null;
    }

    /** Most recent complete value set used to safely replace a queued text draft. */
    public static synchronized ServerConfigValues baseValues() {
        if (!canManage()) return null;
        if (queuedValues != null) return queuedValues;
        return inFlightValues != null ? inFlightValues : snapshot.values();
    }

    public static synchronized String statusTranslationKey() {
        if (!isCurrentSession() || snapshot == null) return "gui.odysseymap.server_config.status.waiting";
        if (!snapshot.administrator()) return "gui.odysseymap.server_config.status.permission";
        if (hasPendingWrite()) return "gui.odysseymap.server_config.status.pending";
        if (feedbackKey != null) return feedbackKey;
        return "gui.odysseymap.server_config.status.ready";
    }

    public static synchronized boolean hasPendingWrite() {
        return inFlightValues != null || queuedValues != null || localDraftPending;
    }

    /** Mark a debounced text edit so another administrator's update becomes a visible conflict. */
    public static synchronized void beginDraft() {
        if (!canManage()) return;
        localDraftPending = true;
        feedbackKey = "gui.odysseymap.server_config.status.pending";
    }

    /** Monotonic marker used by screens to react to results without polling packet details. */
    public static synchronized long stateVersion() { return stateVersion; }
    public static synchronized long sessionVersion() { return sessionVersion; }

    /** Queue the newest draft. Multiple edits before an acknowledgement collapse into one request. */
    public static synchronized void submit(ServerConfigValues values) {
        if (!canManage() || values == null) return;
        ServerConfigValues current = snapshot.values();
        localDraftPending = false;
        if (inFlightValues == null && values.equals(current)) {
            queuedValues = null;
            feedbackKey = "gui.odysseymap.server_config.status.ready";
            stateVersion++;
            return;
        }
        queuedValues = values.equals(inFlightValues) ? null : values;
        feedbackKey = "gui.odysseymap.server_config.status.pending";
        flushIfReady(System.nanoTime());
    }

    /** Cancel only a not-yet-sent draft, for example after an invalid text edit. */
    public static synchronized void clearQueuedEdit() {
        queuedValues = null;
        localDraftPending = false;
        feedbackKey = inFlightValues == null
                ? "gui.odysseymap.server_config.status.ready"
                : "gui.odysseymap.server_config.status.pending";
        stateVersion++;
    }

    /** Advance the request timeout and minimum save interval from a live screen tick. */
    public static synchronized void tick() {
        long now = System.nanoTime();
        if (connection != null && !isCurrentSession()) {
            clearSession();
            stateVersion++;
            return;
        }
        if (inFlightValues != null && now - inFlightSinceNanos >= REQUEST_TIMEOUT_NANOS) {
            inFlightValues = null;
            inFlightRevision = 0L;
            queuedValues = null;
            localDraftPending = false;
            resetRetry();
            feedbackKey = "gui.odysseymap.server_config.status.timeout";
            stateVersion++;
        }
        flushIfReady(now);
    }

    /** Clear all state and queued edits so they can never follow a player into another server. */
    public static synchronized void reset() {
        clearSession();
        stateVersion++;
    }

    private static void flushIfReady(long now) {
        if (queuedValues == null || inFlightValues != null || !canManage() || connection == null) return;
        if (nextRetryNanos != 0L && now < nextRetryNanos) return;
        if (now - lastSendNanos < MIN_SEND_INTERVAL_NANOS && lastSendNanos != 0L) return;

        ServerConfigValues values = queuedValues;
        queuedValues = null;
        inFlightValues = values;
        inFlightRevision = snapshot.revision();
        inFlightSinceNanos = now;
        lastSendNanos = now;
        try {
            sender.accept(new ServerConfigRequest(snapshot.revision(), values));
        } catch (RuntimeException unavailable) {
            inFlightValues = null;
            queuedValues = null;
            resetRetry();
            feedbackKey = "gui.odysseymap.server_config.status.send_error";
        }
        stateVersion++;
    }

    private static boolean isCurrentSession() {
        if (connection == null) return false;
        Minecraft mc = Minecraft.getInstance();
        return mc != null && mc.getConnection() != null && mc.getConnection().getConnection() == connection;
    }

    private static void clearSession() {
        sessionVersion++;
        connection = null;
        snapshot = null;
        queuedValues = null;
        inFlightValues = null;
        localDraftPending = false;
        inFlightSinceNanos = 0L;
        inFlightRevision = 0L;
        lastSendNanos = 0L;
        resetRetry();
        feedbackKey = null;
    }

    private static void resetRetry() {
        nextRetryNanos = 0L;
        retryDelayNanos = 0L;
    }
}
