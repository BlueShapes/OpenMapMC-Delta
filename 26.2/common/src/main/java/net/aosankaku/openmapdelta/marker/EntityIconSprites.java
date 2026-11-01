// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.marker;

import com.mojang.blaze3d.platform.NativeImage;
import net.aosankaku.openmapdelta.OpenMapDeltaCommon;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.fish.Cod;
import net.minecraft.world.entity.animal.fish.Pufferfish;
import net.minecraft.world.entity.animal.fish.Salmon;
import net.minecraft.world.entity.animal.fish.TropicalFish;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.WeakHashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

/** Resolves live entity marker imagery from the client-side renderer and owns its bounded texture cache. */
public final class EntityIconSprites {
    private static final int MAX_INDEXED_ENTITIES = 1_024;
    private static final double MAX_DISTANCE_SQUARED = 64.0 * 64.0;
    private static final long UNKNOWN_VARIANT_TICKS = 20L * 10L;
    private static final long RESOURCE_GENERATION_UNSET = Long.MIN_VALUE;
    private static final AtomicLong NEXT_TEXTURE_ID = new AtomicLong();
    private static final EntityIconSprites INSTANCE = new EntityIconSprites(
            EntityIconCapture::capture, System::nanoTime);

    private final Capture capture;
    private final LongSupplier clock;
    private final EntityIconCache<EntityIconKey, Sprite> cache = new EntityIconCache<>(
            128, 2, TimeUnit.MILLISECONDS.toNanos(100), TimeUnit.SECONDS.toNanos(5), this::retire);
    private final Map<UUID, Entity> indexedEntities = new HashMap<>();
    private final List<RetiredSprite> retired = new ArrayList<>();
    private final Map<EntityIconCache.Ticket<EntityIconKey>, CaptureResult> deliveredCaptures =
            Collections.synchronizedMap(new WeakHashMap<>());
    private ClientLevel trackedLevel;
    private long trackedResourceGeneration = RESOURCE_GENERATION_UNSET;
    private long lifecycleEpoch;
    private long tickCount;
    private boolean hadUncachedSinceTick;
    private boolean allowRefreshThisTick;
    private Minecraft testMinecraft;

    EntityIconSprites(Capture capture, LongSupplier clock) {
        this.capture = capture;
        this.clock = clock;
    }

    /** Returns a cropped live-icon texture for a fish or catalog-missing entity, when it is ready. */
    public static MarkerIconCatalog.Texture resolve(Marker marker) {
        return INSTANCE.resolveInternal(Minecraft.getInstance(), marker);
    }

    /** Refreshes the bounded loaded-entity index and retires textures whose deferred blits have completed. */
    public static void tick(Minecraft mc) { INSTANCE.tickInternal(mc); }

    /** Invalidates pending captures and schedules cached texture release after queued GUI work drains. */
    public static void clear(Minecraft mc) { INSTANCE.clearInternal(mc); }

    MarkerIconCatalog.Texture resolveInternal(Minecraft mc, Marker marker) {
        if (marker == null || !synchronizeBeforeResolve(mc)) return null;
        String requestedType = mobTypeId(marker.getIconId());
        if (requestedType == null) return null;
        Entity entity = indexedEntities.get(marker.getId());
        if (entity == null || !requestedType.equals(entityTypeId(entity))) return null;

        long resourceGeneration = MarkerIconCatalog.resourceGeneration();
        EntityIconKey key = keyFor(mc.level, entity, requestedType, resourceGeneration);
        if (key == null) return null;
        Sprite cached = cache.get(key);
        EntityIconCache.Ticket<EntityIconKey> ticket;
        if (cached != null) {
            if (key.entityId() != null && allowRefreshThisTick
                    && mc.level.getGameTime() - cached.capturedGameTime() >= UNKNOWN_VARIANT_TICKS) {
                ticket = cache.reserveRefresh(key, clock.getAsLong());
                if (ticket != null) beginCapture(mc, entity, resourceGeneration, ticket);
            }
            return cached.texture();
        }

        long nowNanos = clock.getAsLong();
        if (!cache.isCoolingDown(key, nowNanos)) hadUncachedSinceTick = true;
        ticket = cache.reserve(key, nowNanos);
        if (ticket == null) return null;
        beginCapture(mc, entity, resourceGeneration, ticket);
        return null;
    }

    private void beginCapture(Minecraft mc, Entity entity, long resourceGeneration,
                              EntityIconCache.Ticket<EntityIconKey> ticket) {
        ClientLevel requestLevel = mc.level;
        long requestEpoch = lifecycleEpoch;
        long requestGameTime = requestLevel.getGameTime();
        boolean preferHead = entity instanceof LivingEntity;
        try {
            capture.capture(mc, entity, preferHead, image -> onCapture(mc, requestLevel, requestEpoch,
                    resourceGeneration, requestGameTime, ticket, image));
        } catch (Throwable failure) {
            cache.fail(ticket, clock.getAsLong());
        }
    }

    void tickInternal(Minecraft mc) {
        allowRefreshThisTick = !hadUncachedSinceTick;
        hadUncachedSinceTick = false;
        tickCount++;
        drainRetired();
        if (mc == null || mc.level == null || mc.player == null) {
            if (trackedLevel != null || !indexedEntities.isEmpty()) clearInternal(mc);
            return;
        }

        long resourceGeneration = MarkerIconCatalog.resourceGeneration();
        if (trackedLevel != mc.level || trackedResourceGeneration != resourceGeneration) {
            clearResources();
            trackedLevel = mc.level;
            trackedResourceGeneration = resourceGeneration;
        }
        rebuildIndex(mc.level, mc.player);
    }

    void clearInternal(Minecraft mc) {
        clearResources();
        trackedLevel = null;
        trackedResourceGeneration = RESOURCE_GENERATION_UNSET;
    }

    private void clearResources() {
        lifecycleEpoch++;
        indexedEntities.clear();
        cache.clear();
        hadUncachedSinceTick = false;
        allowRefreshThisTick = false;
    }

    private boolean synchronizeBeforeResolve(Minecraft mc) {
        if (mc == null || mc.level == null || mc.player == null) {
            if (trackedLevel != null || trackedResourceGeneration != RESOURCE_GENERATION_UNSET
                    || !indexedEntities.isEmpty()) {
                clearResources();
            }
            trackedLevel = null;
            trackedResourceGeneration = RESOURCE_GENERATION_UNSET;
            return false;
        }

        long resourceGeneration = MarkerIconCatalog.resourceGeneration();
        if (trackedLevel != mc.level || trackedResourceGeneration != resourceGeneration) {
            clearResources();
            trackedLevel = mc.level;
            trackedResourceGeneration = resourceGeneration;
            return false;
        }
        return true;
    }

    private void rebuildIndex(ClientLevel level, LocalPlayer self) {
        PriorityQueue<EntityDistance> nearest = new PriorityQueue<>(
                Comparator.comparingDouble(EntityDistance::distanceSquared).reversed());
        for (Entity entity : level.entitiesForRendering()) {
            if (entity == self || entity instanceof Player) continue;
            double distanceSquared = self.distanceToSqr(entity);
            if (!Double.isFinite(distanceSquared) || distanceSquared > MAX_DISTANCE_SQUARED) continue;

            EntityDistance candidate = new EntityDistance(entity, distanceSquared);
            if (nearest.size() < MAX_INDEXED_ENTITIES) {
                nearest.add(candidate);
            } else if (distanceSquared < nearest.peek().distanceSquared()) {
                nearest.remove();
                nearest.add(candidate);
            }
        }

        indexedEntities.clear();
        for (EntityDistance candidate : nearest) indexedEntities.put(candidate.entity().getUUID(), candidate.entity());
    }

    private void onCapture(Minecraft mc, ClientLevel requestLevel, long requestEpoch, long resourceGeneration,
                           long requestGameTime,
                           EntityIconCache.Ticket<EntityIconKey> ticket, NativeImage image) {
        synchronized (deliveredCaptures) {
            if (deliveredCaptures.containsKey(ticket)) {
                CaptureResult original = deliveredCaptures.get(ticket);
                NativeImage originalImage = original == null ? null : original.image();
                if (image != null && image != originalImage) closeImage(image);
                return;
            }
            deliveredCaptures.put(ticket, image == null
                    ? CaptureResult.EMPTY : new CaptureResult(image));
        }
        try {
            mc.execute(() -> completeOnClientThread(mc, requestLevel, requestEpoch,
                    resourceGeneration, requestGameTime, ticket, image));
        } catch (Throwable schedulingFailure) {
            cache.complete(ticket, null);
            closeImage(image);
        }
    }

    private void completeOnClientThread(Minecraft mc, ClientLevel requestLevel, long requestEpoch,
                                        long resourceGeneration, long requestGameTime,
                                        EntityIconCache.Ticket<EntityIconKey> ticket, NativeImage image) {
        if (image == null) {
            if (isCurrent(mc, requestLevel, requestEpoch, resourceGeneration)) cache.fail(ticket, clock.getAsLong());
            else cache.complete(ticket, null);
            return;
        }
        if (!isCurrent(mc, requestLevel, requestEpoch, resourceGeneration)) {
            cache.complete(ticket, null);
            closeImage(image);
            return;
        }

        int[] bounds;
        try {
            bounds = alphaBounds(image);
        } catch (Throwable failure) {
            cache.fail(ticket, clock.getAsLong());
            closeImage(image);
            return;
        }
        if (bounds == null) {
            cache.fail(ticket, clock.getAsLong());
            closeImage(image);
            return;
        }

        Identifier location = Identifier.fromNamespaceAndPath(OpenMapDeltaCommon.MOD_ID,
                "entity_icons/" + resourceGeneration + "/" + NEXT_TEXTURE_ID.incrementAndGet());
        int textureWidth = image.getWidth();
        int textureHeight = image.getHeight();
        DynamicTexture texture = null;
        boolean registered = false;
        try {
            texture = new DynamicTexture(() -> "Odyssey Map entity icon", image);
            mc.getTextureManager().register(location, texture);
            registered = true;
            texture.upload();
            Sprite sprite = new Sprite(location, bounds[0], bounds[1], bounds[2], bounds[3],
                    textureWidth, textureHeight, requestGameTime, texture, mc.getTextureManager());
            if (!cache.complete(ticket, sprite)) return;
        } catch (Throwable failure) {
            cache.fail(ticket, clock.getAsLong());
            if (registered) {
                try {
                    mc.getTextureManager().release(location);
                    return;
                } catch (Throwable cleanupFailure) {
                    closeTexture(texture);
                }
            } else if (texture != null) {
                closeTexture(texture);
            } else {
                closeImage(image);
            }
        }
    }

    private boolean isCurrent(Minecraft mc, ClientLevel requestLevel, long requestEpoch, long resourceGeneration) {
        return lifecycleEpoch == requestEpoch && mc.level == requestLevel
                && trackedLevel == requestLevel
                && trackedResourceGeneration == resourceGeneration
                && MarkerIconCatalog.resourceGeneration() == resourceGeneration;
    }

    private int[] alphaBounds(NativeImage image) {
        int minX = image.getWidth();
        int minY = image.getHeight();
        int maxX = -1;
        int maxY = -1;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if ((image.getPixel(x, y) & 0xFF000000) == 0) continue;
                minX = Math.min(minX, x);
                minY = Math.min(minY, y);
                maxX = Math.max(maxX, x);
                maxY = Math.max(maxY, y);
            }
        }
        return maxX < minX || maxY < minY ? null
                : new int[]{minX, minY, maxX - minX + 1, maxY - minY + 1};
    }

    private EntityIconKey keyFor(ClientLevel level, Entity entity, String typeId, long resourceGeneration) {
        if ("minecraft:tropical_fish".equals(typeId) && entity instanceof TropicalFish fish) {
            return new EntityIconKey(typeId, null,
                    fish.getPattern().getSerializedName() + ":" + fish.getBaseColor().getId()
                            + ":" + fish.getPatternColor().getId(), resourceGeneration);
        }
        if ("minecraft:salmon".equals(typeId) && entity instanceof Salmon salmon) {
            return new EntityIconKey(typeId, null, salmon.getVariant().getSerializedName(), resourceGeneration);
        }
        if ("minecraft:pufferfish".equals(typeId) && entity instanceof Pufferfish pufferfish) {
            return new EntityIconKey(typeId, null, Integer.toString(pufferfish.getPuffState()), resourceGeneration);
        }
        if ("minecraft:cod".equals(typeId) && entity instanceof Cod) {
            return new EntityIconKey(typeId, null, "cod", resourceGeneration);
        }

        String appearance = entity instanceof LivingEntity living && living.isBaby() ? "baby" : "adult";
        return new EntityIconKey(typeId, entity.getUUID(), appearance, resourceGeneration);
    }

    private static String mobTypeId(String iconId) {
        if (iconId == null || !iconId.startsWith("mob:")) return null;
        return iconId.substring("mob:".length());
    }

    private static String entityTypeId(Entity entity) {
        Identifier key = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        return key == null ? null : key.toString();
    }

    private void retire(Sprite sprite) {
        retired.add(new RetiredSprite(sprite, tickCount + 2));
    }

    private void drainRetired() {
        for (int index = retired.size() - 1; index >= 0; index--) {
            RetiredSprite scheduled = retired.get(index);
            if (tickCount < scheduled.releaseAfterTick()) continue;
            retired.remove(index);
            try {
                scheduled.sprite().manager().release(scheduled.sprite().location());
            } catch (RuntimeException ignored) {
                try { scheduled.sprite().dynamicTexture().close(); } catch (RuntimeException ignoredAgain) {}
            }
        }
    }

    private static void closeImage(NativeImage image) {
        if (image == null) return;
        try { image.close(); } catch (Throwable ignored) {}
    }

    private static void closeTexture(DynamicTexture texture) {
        if (texture == null) return;
        try { texture.close(); } catch (Throwable ignored) {}
    }

    MarkerIconCatalog.Texture resolveForTesting(Marker marker) {
        return resolveInternal(testMinecraft, marker);
    }
    void tickForTesting(Minecraft mc) { testMinecraft = mc; tickInternal(mc); }
    void clearForTesting(Minecraft mc) { testMinecraft = mc; clearInternal(mc); }
    int indexedCountForTesting() { return indexedEntities.size(); }
    int pendingForTesting() { return cache.pendingCount(); }

    private record EntityIconKey(String typeId, UUID entityId, String appearance, long resourceGeneration) {}
    private record CaptureResult(NativeImage image) {
        private static final CaptureResult EMPTY = new CaptureResult(null);
    }
    private record Sprite(Identifier location, int u, int v, int width, int height,
                         int textureWidth, int textureHeight, long capturedGameTime,
                         DynamicTexture dynamicTexture, TextureManager manager) {
        private MarkerIconCatalog.Texture texture() {
            return new MarkerIconCatalog.Texture(location, u, v, width, height, textureWidth, textureHeight);
        }
    }
    private record EntityDistance(Entity entity, double distanceSquared) {}
    private record RetiredSprite(Sprite sprite, long releaseAfterTick) {}

    @FunctionalInterface
    interface Capture {
        void capture(Minecraft mc, Entity entity, boolean preferHead, Consumer<NativeImage> callback);
    }

    // Package-private seam keeps lifecycle and cache behavior testable without launching Minecraft.
    static EntityIconSprites createForTesting(Capture capture, LongSupplier clock) {
        return new EntityIconSprites(capture, clock);
    }
}
