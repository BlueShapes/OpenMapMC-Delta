// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.render;

import net.aosankaku.openmapdelta.marker.Marker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.component.ResolvableProfile;

import java.util.LinkedHashMap;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/** Bounded, non-blocking cache for the face that prefixes a shared waypoint name. */
public final class WaypointOwnerFace {
    private static final int MAX_CACHE_SIZE = 512;
    private static final int MAX_CONCURRENT_LOOKUPS = 8;
    private static final long LOOKUP_TIMEOUT_MILLIS = 15_000L;
    private static final long FAILURE_RETRY_MILLIS = 60_000L;
    private static final LinkedHashMap<UUID, Identifier> TEXTURES = new LinkedHashMap<>(32, .75f, true);
    private static final LinkedHashMap<UUID, Boolean> RESOLVED = new LinkedHashMap<>(32, .75f, true);
    private static final LinkedHashMap<UUID, Long> RETRY_AFTER = new LinkedHashMap<>(32, .75f, true);
    private static final ConcurrentHashMap<UUID, Long> PENDING = new ConcurrentHashMap<>();
    private static final AtomicLong REQUEST_IDS = new AtomicLong();
    private static volatile long epoch;

    private WaypointOwnerFace() {}

    public static Identifier texture(UUID ownerId, String ownerName) {
        if (ownerId == null) return null;
        Minecraft mc = Minecraft.getInstance();
        var connection = mc.getConnection();
        var info = connection == null ? null : connection.getPlayerInfo(ownerId);
        var onlineSkin = info == null ? null : info.getSkin();
        Identifier online = onlineSkin == null || onlineSkin.body() == null ? null : onlineSkin.body().texturePath();
        if (online != null) {
            cache(ownerId, online, true);
            return online;
        }
        Identifier cached;
        synchronized (TEXTURES) {
            cached = TEXTURES.get(ownerId);
            if (cached == null) {
                var fallback = DefaultPlayerSkin.get(ownerId);
                cached = fallback == null || fallback.body() == null ? null : fallback.body().texturePath();
                if (cached != null) {
                    TEXTURES.put(ownerId, cached);
                    trimTextures();
                }
            }
        }
        request(mc, ownerId);
        return cached;
    }

    /** Coordinates are the center of the eight-pixel face, matching RenderUtil.drawPlayerHead. */
    public static void draw(GuiGraphicsExtractor graphics, Marker marker, int x, int y, int size) {
        if (marker == null || marker.getOwnerId() == null || size <= 0) return;
        Identifier location = texture(marker.getOwnerId(), marker.getOwnerName());
        if (location == null) return;
        float scale = size / 8.0F;
        graphics.pose().pushMatrix();
        graphics.pose().translate(x, y);
        graphics.pose().scale(scale, scale);
        graphics.blit(RenderPipelines.GUI_TEXTURED, location, -4, -4, 8.0F, 8.0F, 8, 8, 64, 64);
        graphics.blit(RenderPipelines.GUI_TEXTURED, location, -4, -4, 40.0F, 8.0F, 8, 8, 64, 64);
        graphics.pose().popMatrix();
    }

    public static void clear() {
        epoch++;
        synchronized (TEXTURES) { TEXTURES.clear(); }
        synchronized (RESOLVED) { RESOLVED.clear(); }
        synchronized (RETRY_AFTER) { RETRY_AFTER.clear(); }
        synchronized (PENDING) { PENDING.clear(); }
    }

    private static void request(Minecraft mc, UUID ownerId) {
        synchronized (RESOLVED) { if (RESOLVED.containsKey(ownerId)) return; }
        long now = System.currentTimeMillis();
        synchronized (RETRY_AFTER) { if (RETRY_AFTER.getOrDefault(ownerId, 0L) > now) return; }
        long requestId = REQUEST_IDS.incrementAndGet();
        synchronized (PENDING) {
            if (PENDING.size() >= MAX_CONCURRENT_LOOKUPS || PENDING.putIfAbsent(ownerId, requestId) != null) return;
        }
        long requestEpoch = epoch;
        scheduleTimeout(ownerId, requestId, requestEpoch);
        try {
            ResolvableProfile.createUnresolved(ownerId)
                    .resolveProfile(mc.services().profileResolver())
                    .thenCompose(mc.getSkinManager()::get)
                    .whenComplete((skin, failure) -> {
                        Optional<?> resolved = failure == null && skin != null ? skin : Optional.empty();
                        Identifier texture = null;
                        if (resolved.isPresent() && resolved.get() instanceof net.minecraft.world.entity.player.PlayerSkin playerSkin
                                && playerSkin.body() != null) {
                            texture = playerSkin.body().texturePath();
                        }
                        if (requestEpoch == epoch && texture != null) cache(ownerId, texture, true);
                        complete(ownerId, requestId, requestEpoch, texture != null);
                    });
        } catch (RuntimeException failure) {
            complete(ownerId, requestId, requestEpoch, false);
        }
    }

    private static void scheduleTimeout(UUID ownerId, long requestId, long requestEpoch) {
        CompletableFuture.delayedExecutor(LOOKUP_TIMEOUT_MILLIS, java.util.concurrent.TimeUnit.MILLISECONDS)
                .execute(() -> complete(ownerId, requestId, requestEpoch, false));
    }

    private static void complete(UUID ownerId, long requestId, long requestEpoch, boolean success) {
        if (!PENDING.remove(ownerId, requestId) || requestEpoch != epoch) return;
        if (success) {
            synchronized (RETRY_AFTER) { RETRY_AFTER.remove(ownerId); }
            return;
        }
        synchronized (RETRY_AFTER) {
            RETRY_AFTER.put(ownerId, System.currentTimeMillis() + FAILURE_RETRY_MILLIS);
            trim(RETRY_AFTER);
        }
    }

    private static void cache(UUID id, Identifier texture, boolean resolved) {
        synchronized (TEXTURES) {
            TEXTURES.put(id, texture);
            trimTextures();
        }
        if (resolved) {
            synchronized (RESOLVED) {
                RESOLVED.put(id, Boolean.TRUE);
                trim(RESOLVED);
            }
        }
    }

    private static <V> void trim(LinkedHashMap<UUID, V> map) {
        while (map.size() > MAX_CACHE_SIZE) map.remove(map.keySet().iterator().next());
    }

    private static void trimTextures() {
        while (TEXTURES.size() > MAX_CACHE_SIZE) {
            UUID evicted = TEXTURES.keySet().iterator().next();
            TEXTURES.remove(evicted);
            synchronized (RESOLVED) { RESOLVED.remove(evicted); }
        }
    }
}
