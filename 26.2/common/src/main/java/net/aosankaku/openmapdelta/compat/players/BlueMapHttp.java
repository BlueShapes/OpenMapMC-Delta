// Copyright (c) 2026 AoSankaku. Licensed under Apache-2.0.
package net.aosankaku.openmapdelta.compat.players;

import net.aosankaku.openmapdelta.network.PlayerPosition;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.List;
import java.util.concurrent.*;

/** Bounded blocking request, called only from the compatibility worker. */
public final class BlueMapHttp {
    private BlueMapHttp() {}

    public static List<PlayerPosition> fetch(BlueMapEndpoint endpoint, String dimension) throws IOException {
        return new Request(endpoint, dimension).fetch();
    }

    /** Owns the actual socket so logout/configuration changes can stop blocked I/O. */
    public static final class Request {
        private final BlueMapEndpoint endpoint;
        private final String dimension;
        private final long timeoutMillis;
        private volatile boolean cancelled;
        private volatile HttpURLConnection active;
        public Request(BlueMapEndpoint endpoint, String dimension) { this(endpoint, dimension, 6000); }
        Request(BlueMapEndpoint endpoint, String dimension, long timeoutMillis) {
            this.endpoint = endpoint; this.dimension = dimension; this.timeoutMillis = timeoutMillis;
        }

        public void cancel() {
            cancelled = true;
            HttpURLConnection connection = active;
            if (connection != null) connection.disconnect();
        }

        public List<PlayerPosition> fetch() throws IOException {
            if (cancelled) throw new IOException("BlueMap request cancelled");
            HttpURLConnection connection = (HttpURLConnection) endpoint.playersUri().toURL().openConnection();
            connection.setConnectTimeout(3000); connection.setReadTimeout(3000);
            connection.setInstanceFollowRedirects(false); connection.setUseCaches(false);
            connection.setRequestProperty("Accept", "application/json");
            active = connection;
            ScheduledExecutorService deadline = Executors.newSingleThreadScheduledExecutor(task -> {
                Thread timer = new Thread(task, "OpenMap-BlueMap-deadline"); timer.setDaemon(true); return timer;
            });
            deadline.schedule(this::cancel, timeoutMillis, TimeUnit.MILLISECONDS);
            try {
                if (cancelled) throw new IOException("BlueMap request cancelled");
                if (connection.getResponseCode() != 200 || connection.getContentLengthLong() > BlueMapPlayers.MAX_BYTES)
                    throw new IOException("BlueMap endpoint unavailable or response too large");
                try (var input = connection.getInputStream()) {
                    byte[] bytes = input.readNBytes(BlueMapPlayers.MAX_BYTES + 1);
                    if (cancelled || bytes.length > BlueMapPlayers.MAX_BYTES) throw new IOException("BlueMap request stopped or too large");
                    try { return BlueMapPlayers.decode(bytes, dimension); }
                    catch (IllegalArgumentException malformed) { throw new IOException("Invalid BlueMap response", malformed); }
                }
            } finally {
                active = null; connection.disconnect(); deadline.shutdownNow();
            }
        }
    }
}
