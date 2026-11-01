// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.web;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashMap;

/** Bounded game-thread image cache. Failed textures are retried after a short delay. */
public final class WebMapIconCache {
    private static final int MAX_ENTRIES = 256;
    private static final long RETRY_NANOS = java.util.concurrent.TimeUnit.SECONDS.toNanos(5);
    private final LinkedHashMap<String, Entry> entries = new LinkedHashMap<>(32, .75f, true);
    private long generation = Long.MIN_VALUE;
    private long revision;
    private int captureBudget;

    private record Entry(WebMapSnapshot.Image image, long retryAt, long refreshAt) {}
    @FunctionalInterface public interface Capture { WebMapImagePixels get() throws Exception; }

    public synchronized WebMapSnapshot.Image get(String sourceIdentity, long currentGeneration,
                                                  Capture capture) {
        return get(sourceIdentity, currentGeneration, 0, capture);
    }

    /** Cached textures can be refreshed periodically when their backing GPU object is updated in place. */
    public synchronized WebMapSnapshot.Image get(String sourceIdentity, long currentGeneration,
                                                  long refreshIntervalNanos, Capture capture) {
        if (currentGeneration != generation) {
            entries.clear();
            generation = currentGeneration;
        }
        Entry existing = entries.get(sourceIdentity);
        long now = System.nanoTime();
        if (existing != null && existing.image() != null
                && (refreshIntervalNanos <= 0 || now < existing.refreshAt())) return existing.image();
        if (existing != null && existing.image() == null && now < existing.retryAt()) return null;
        if (captureBudget <= 0) return existing == null ? null : existing.image();
        captureBudget--;
        try {
            WebMapImagePixels pixels = capture.get();
            if (pixels == null) {
                entries.put(sourceIdentity, new Entry(existing == null ? null : existing.image(),
                        now + RETRY_NANOS,
                        existing == null ? 0 : now + RETRY_NANOS));
                trim();
                return existing == null ? null : existing.image();
            }
            WebMapSnapshot.Image image = existing != null && existing.image() != null
                    && existing.image().width() == pixels.width() && existing.image().height() == pixels.height()
                    && existing.image().matches(pixels.pixels())
                    ? existing.image()
                    : new WebMapSnapshot.Image(pixels.pixels(), pixels.width(), pixels.height(), ++revision);
            entries.put(sourceIdentity, new Entry(image, 0,
                    refreshIntervalNanos <= 0 ? 0 : now + refreshIntervalNanos));
            trim();
            return image;
        } catch (Exception failure) {
            entries.put(sourceIdentity, new Entry(existing == null ? null : existing.image(), now + RETRY_NANOS,
                    existing == null ? 0 : now + RETRY_NANOS));
            trim();
            return existing == null ? null : existing.image();
        }
    }

    private void trim() {
        while (entries.size() > MAX_ENTRIES) entries.remove(entries.keySet().iterator().next());
    }

    public synchronized void clear() {
        entries.clear();
        generation = Long.MIN_VALUE;
        captureBudget = 0;
    }

    public synchronized void beginPublish(int maximumCaptures) {
        captureBudget = Math.max(0, maximumCaptures);
    }

    public synchronized boolean hasImage(String sourceIdentity, long currentGeneration) {
        return currentGeneration == generation && entries.containsKey(sourceIdentity)
                && entries.get(sourceIdentity).image() != null;
    }

    /** A content-independent URL token; raw resource names never become request paths. */
    public static String opaqueKey(String sourceIdentity) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(sourceIdentity.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

}
