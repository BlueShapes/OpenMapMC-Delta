// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.marker;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.fish.Cod;
import net.minecraft.world.entity.animal.fish.Salmon;
import net.minecraft.world.entity.animal.fish.TropicalFish;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

public final class EntityIconSpritesTest {
    private static int assertions;

    public static void main(String[] args) {
        capturesUnknownEntityOnceAndUsesCachedTexture();
        keysVanillaFishByAppearance();
        doesNotMergeModFishSubclasses();
        onlyIndexesNearestBoundedLoadedEntities();
        rejectsCacheHitsFromAWorldChangedBetweenTicks();
        failureUsesCooldownAndNullCaptureDoesNotStick();
        uploadAndCleanupFailuresReleasePendingSlot();
        unknownEntitiesKeepIconsAcrossCaptureRateWindow();
        coolingDownUnsupportedEntityDoesNotBlockRefreshes();
        refreshRetainsOldTextureUntilReplacementIsSafeToRetire();
        clearAndResourceReloadRejectStaleCapture();
        retiresTexturesAfterQueuedGuiDrawsCanFinish();
        System.out.println("Entity icon sprite tests passed (" + assertions + " assertions).");
    }

    private static void capturesUnknownEntityOnceAndUsesCachedTexture() {
        Harness h = new Harness();
        Entity first = living("example:crystal_moth", 1, 0);
        Entity second = entity("example:crystal_moth", 2, 2);
        h.world(first, second);
        h.manager.tickForTesting(h.mc);

        Marker marker = marker(first, "example:crystal_moth");
        check(h.manager.indexedCountForTesting() == 2, "test entities are indexed");
        check(h.manager.resolveForTesting(marker) == null, "first unknown lookup uses fallback while capture is pending");
        check(h.capture.requests.size() == 1 && h.capture.requests.get(0).preferHead,
                "unknown living model capture requests a safe head before full-body fallback; requests="
                        + h.capture.requests.size() + (h.capture.requests.isEmpty() ? "" : ", preferHead=" + h.capture.requests.get(0).preferHead));
        check(h.manager.resolveForTesting(marker) == null, "same marker does not enqueue duplicate work");
        NativeImage captured = image(8, 12);
        h.capture.requests.get(0).complete(captured);
        h.mc.flushTasks();
        h.capture.requests.get(0).complete(captured);
        NativeImage duplicate = image(5, 5);
        h.capture.requests.get(0).complete(duplicate);
        check(!captured.closed, "a repeated callback does not close an image already owned by a texture");
        check(duplicate.closed, "an independent duplicate callback result is closed without upload");
        h.clock.addAndGet(100_000_000L);

        Marker same = marker(first, "example:crystal_moth");
        Marker other = marker(second, "example:crystal_moth");
        Marker mismatchedType = marker(first, "example:different_mob");
        Marker notIndexed = marker(UUID.randomUUID(), "example:crystal_moth");
        MarkerIconCatalog.Texture texture = h.manager.resolveForTesting(same);
        Identifier sprite = texture(texture);
        check(sprite != null, "completed capture resolves to a texture");
        check(texture.width() == 8 && texture.height() == 12,
                "transparent bounds preserve an entity icon's source aspect ratio");
        check(sprite.equals(texture(h.manager.resolveForTesting(same))), "same entity reuses its texture");
        check(h.manager.resolveForTesting(other) == null, "unknown entities with different UUIDs get separate appearance entries");
        check(h.capture.requests.size() == 2, "second unknown entity starts a separate bounded capture");
        check(h.manager.resolveForTesting(mismatchedType) == null, "marker type must match indexed entity type");
        check(h.manager.resolveForTesting(notIndexed) == null, "unloaded entities remain on fallback");
    }

    private static void keysVanillaFishByAppearance() {
        Harness h = new Harness();
        Salmon small = new Salmon(uuid(10), "small");
        Salmon large = new Salmon(uuid(11), "large");
        Salmon anotherSmall = new Salmon(uuid(12), "small");
        TropicalFish tropicalA = new TropicalFish(uuid(13), "kob", 2, 5);
        TropicalFish tropicalB = new TropicalFish(uuid(14), "kob", 2, 6);
        h.world(small, large, anotherSmall, tropicalA, tropicalB);
        h.manager.tickForTesting(h.mc);

        check(h.manager.resolveForTesting(marker(small, "minecraft:salmon")) == null, "first salmon appearance queues");
        h.clock.addAndGet(100_000_000L);
        check(h.manager.resolveForTesting(marker(large, "minecraft:salmon")) == null, "different salmon variant gets a distinct key");
        h.clock.addAndGet(100_000_000L);
        h.capture.requests.get(0).complete(image(8, 6));
        h.capture.requests.get(1).complete(image(10, 6));
        h.mc.flushTasks();
        check(h.manager.resolveForTesting(marker(anotherSmall, "minecraft:salmon")) != null,
                "matching salmon appearances reuse one cached key");
        h.clock.addAndGet(100_000_000L);
        check(h.manager.resolveForTesting(marker(tropicalA, "minecraft:tropical_fish")) == null,
                "tropical fish pattern and base colors form a key");
        h.clock.addAndGet(100_000_000L);
        check(h.manager.resolveForTesting(marker(tropicalB, "minecraft:tropical_fish")) == null,
                "tropical fish pattern color also forms a distinct key");
        check(h.capture.requests.size() == 4, "fish requests coalesce only when their semantic variants match");
    }

    private static void onlyIndexesNearestBoundedLoadedEntities() {
        Harness h = new Harness();
        List<Entity> entities = new ArrayList<>();
        Entity nearest = entity("example:near", 100, 1);
        Entity farthest = entity("example:far", 101, 64);
        entities.add(nearest);
        entities.add(farthest);
        for (int i = 0; i < 1_030; i++) entities.add(entity("example:mob_" + i, 1_000 + i, i % 63 + 1));
        h.world(entities.toArray(Entity[]::new));
        h.manager.tickForTesting(h.mc);

        check(h.manager.indexedCountForTesting() == 1_024, "entity index is capped at 1024 entries");
        check(h.manager.resolveForTesting(marker(nearest, "example:near")) == null,
                "nearest loaded entity is indexed");
        check(h.manager.resolveForTesting(marker(farthest, "example:far")) == null,
                "an entity omitted from the nearest 1024 falls back without capture");
        Entity outsideRange = entity("example:outside", 9_000, 65);
        h.world(outsideRange);
        h.manager.tickForTesting(h.mc);
        check(h.manager.resolveForTesting(marker(outsideRange, "example:outside")) == null,
                "entities beyond 64 blocks are not captured");
        check(h.manager.indexedCountForTesting() == 0, "index is refreshed from current world entities");
    }

    private static void doesNotMergeModFishSubclasses() {
        Harness h = new Harness();
        ModdedCod first = new ModdedCod(uuid(20));
        ModdedCod second = new ModdedCod(uuid(21));
        h.world(first, second);
        h.manager.tickForTesting(h.mc);

        h.manager.resolveForTesting(marker(first, "example:codish"));
        h.clock.addAndGet(100_000_000L);
        h.manager.resolveForTesting(marker(second, "example:codish"));

        check(h.capture.requests.size() == 2,
                "mod fish subclasses keep UUID-specific appearance keys instead of sharing vanilla fish keys");
        check(h.capture.requests.get(0).entity == first && h.capture.requests.get(1).entity == second,
                "each mod fish capture uses its own loaded entity");
        NativeImage firstImage = image(6, 4);
        NativeImage secondImage = image(9, 5);
        h.capture.requests.get(0).complete(firstImage);
        h.capture.requests.get(1).complete(secondImage);
        h.mc.flushTasks();
        check(!texture(h.manager.resolveForTesting(marker(first, "example:codish")))
                        .equals(texture(h.manager.resolveForTesting(marker(second, "example:codish")))),
                "mod fish captures keep individual textures");
    }

    private static void rejectsCacheHitsFromAWorldChangedBetweenTicks() {
        Harness h = new Harness();
        UUID sharedId = uuid(25);
        Entity oldEntity = entity("example:world_mob", 25, 1);
        Entity replacement = new Entity(sharedId, new EntityType("example:world_mob"), 1, false);
        h.world(oldEntity);
        h.manager.tickForTesting(h.mc);
        h.manager.resolveForTesting(marker(sharedId, "example:world_mob"));
        h.capture.requests.get(0).complete(image(7, 7));
        h.mc.flushTasks();
        Identifier oldTexture = texture(h.manager.resolveForTesting(marker(sharedId, "example:world_mob")));
        check(oldTexture != null, "first world has a cached entity icon");

        h.mc.level = new ClientLevel();
        h.mc.level.entities.add(replacement);
        check(h.manager.resolveForTesting(marker(replacement, "example:world_mob")) == null,
                "world change between manager ticks invalidates old cache hits before lookup");
        check(h.capture.requests.size() == 1, "resolver waits for the next tick to rebuild the loaded entity index");
    }

    private static void failureUsesCooldownAndNullCaptureDoesNotStick() {
        Harness h = new Harness();
        Entity mob = entity("example:broken", 200, 1);
        h.world(mob);
        h.manager.tickForTesting(h.mc);
        Marker marker = marker(mob, "example:broken");

        h.manager.resolveForTesting(marker);
        h.capture.requests.get(0).complete(image(8, 8));
        h.mc.flushTasks();
        check(h.textureManager.registeredCount() == 1, "valid image is uploaded once");

        Harness empty = new Harness();
        Entity invisible = entity("example:invisible", 201, 1);
        empty.world(invisible);
        empty.manager.tickForTesting(empty.mc);
        empty.manager.resolveForTesting(marker(invisible, "example:invisible"));
        empty.capture.requests.get(0).complete(new NativeImage(8, 8));
        empty.mc.flushTasks();
        check(empty.textureManager.registeredCount() == 0, "fully transparent capture does not register a useless icon");
        check(empty.capture.requests.size() == 1, "empty image failure is cooled down");
        empty.clock.addAndGet(5_000_000_000L);
        empty.manager.resolveForTesting(marker(invisible, "example:invisible"));
        check(empty.capture.requests.size() == 2, "failed capture retries after cooldown expires");

        Harness missing = new Harness();
        Entity missingMob = entity("example:no_result", 202, 1);
        missing.world(missingMob);
        missing.manager.tickForTesting(missing.mc);
        missing.manager.resolveForTesting(marker(missingMob, "example:no_result"));
        missing.capture.requests.get(0).complete(null);
        missing.mc.flushTasks();
        check(missing.manager.pendingForTesting() == 0, "null result releases pending request");
    }

    private static void uploadAndCleanupFailuresReleasePendingSlot() {
        Harness h = new Harness();
        Entity mob = entity("example:upload_failure", 250, 1);
        h.world(mob);
        h.manager.tickForTesting(h.mc);
        Marker marker = marker(mob, "example:upload_failure");
        h.manager.resolveForTesting(marker);
        h.textureManager.failNextUploadAndClose = true;
        h.capture.requests.get(0).complete(image(8, 8));
        boolean cleanupEscaped = false;
        try {
            h.mc.flushTasks();
        } catch (RuntimeException expected) {
            cleanupEscaped = true;
        }

        check(!cleanupEscaped, "texture cleanup exceptions are contained after upload failure");
        check(h.manager.pendingForTesting() == 0, "upload and close failures still release the pending slot");
        h.clock.addAndGet(5_000_000_000L);
        h.textureManager.failNextUploadAndClose = false;
        h.manager.resolveForTesting(marker);
        check(h.capture.requests.size() == 2, "a failed upload can be retried after its cooldown");
    }

    private static void unknownEntitiesKeepIconsAcrossCaptureRateWindow() {
        Harness h = new Harness();
        List<Entity> entities = new ArrayList<>();
        for (int i = 0; i < 110; i++) entities.add(entity("example:unknown", 500 + i, 1 + i % 20));
        h.world(entities.toArray(Entity[]::new));
        h.manager.tickForTesting(h.mc);

        for (Entity entity : entities) {
            h.manager.resolveForTesting(marker(entity, "example:unknown"));
            FakeCapture.Request request = h.capture.requests.get(h.capture.requests.size() - 1);
            request.complete(image(6, 8));
            h.mc.flushTasks();
            h.clock.addAndGet(100_000_000L);
            h.mc.level.setGameTime(h.mc.level.getGameTime() + 2);
        }
        check(h.capture.requests.size() == 110, "all visible unknown entities can be captured across the ten-second window");

        h.mc.level.setGameTime(220);
        h.manager.tickForTesting(h.mc);
        int available = 0;
        for (Entity entity : entities) {
            if (h.manager.resolveForTesting(marker(entity, "example:unknown")) != null) available++;
        }
        check(available == 110,
                "unknown entity UUID keys remain stable when snapshots age across a ten-second bucket boundary");
        check(h.capture.requests.size() == 110, "stale-while-refresh keeps existing icons available without resetting capture keys");
    }

    private static void refreshRetainsOldTextureUntilReplacementIsSafeToRetire() {
        Harness h = new Harness();
        Entity mob = entity("example:refresh", 275, 1);
        h.world(mob);
        h.manager.tickForTesting(h.mc);
        Marker marker = marker(mob, "example:refresh");
        h.manager.resolveForTesting(marker);
        h.capture.requests.get(0).complete(image(8, 8));
        h.mc.flushTasks();
        Identifier original = texture(h.manager.resolveForTesting(marker));

        h.manager.tickForTesting(h.mc);
        h.manager.tickForTesting(h.mc);
        h.mc.level.setGameTime(201);
        h.clock.addAndGet(100_000_000L);
        h.manager.tickForTesting(h.mc);
        check(original.equals(texture(h.manager.resolveForTesting(marker))),
                "an expired unknown snapshot continues drawing while refresh is pending");
        check(h.capture.requests.size() == 2, "expired unknown snapshot queues a refresh");
        h.capture.requests.get(1).complete(null);
        h.mc.flushTasks();
        check(original.equals(texture(h.manager.resolveForTesting(marker))), "refresh failure keeps the old texture usable");

        h.clock.addAndGet(5_000_000_000L);
        check(original.equals(texture(h.manager.resolveForTesting(marker))), "old texture remains usable during retry");
        FakeCapture.Request retry = h.capture.requests.get(2);
        retry.complete(image(10, 7));
        h.mc.flushTasks();
        Identifier replacement = texture(h.manager.resolveForTesting(marker));
        check(replacement != null && !replacement.equals(original), "successful refresh installs a new texture");
        check(h.textureManager.releaseCount == 0, "replaced texture waits for queued GUI draws");
        h.manager.tickForTesting(h.mc);
        check(h.textureManager.releaseCount == 0, "replaced texture survives the first safe tick");
        h.manager.tickForTesting(h.mc);
        check(h.textureManager.releaseCount == 1, "replaced texture releases after the deferred draw window");
    }

    private static void coolingDownUnsupportedEntityDoesNotBlockRefreshes() {
        Harness h = new Harness();
        Entity supported = entity("example:refreshable", 280, 1);
        Entity unsupported = entity("example:no_renderer", 281, 2);
        h.world(supported, unsupported);
        h.manager.tickForTesting(h.mc);
        Marker supportedMarker = marker(supported, "example:refreshable");
        Marker unsupportedMarker = marker(unsupported, "example:no_renderer");

        h.manager.resolveForTesting(supportedMarker);
        h.capture.requests.get(0).complete(image(8, 8));
        h.mc.flushTasks();
        h.clock.addAndGet(100_000_000L);
        h.manager.resolveForTesting(unsupportedMarker);
        h.capture.requests.get(1).complete(null);
        h.mc.flushTasks();

        for (int i = 0; i < 40; i++) {
            h.clock.addAndGet(100_000_000L);
            h.mc.level.setGameTime(h.mc.level.getGameTime() + 5);
            h.manager.tickForTesting(h.mc);
            check(h.manager.resolveForTesting(unsupportedMarker) == null,
                    "unsupported renderer stays on fallback during its retry cooldown");
        }
        check(h.mc.level.getGameTime() == 200, "successful icon has reached its refresh age");

        check(h.manager.resolveForTesting(supportedMarker) != null,
                "old supported icon remains visible while its refresh starts");
        check(h.capture.requests.size() == 3,
                "cooling down unsupported lookups cannot starve an expired supported icon refresh");
    }

    private static void clearAndResourceReloadRejectStaleCapture() {
        Harness worldChange = new Harness();
        Entity mob = entity("example:late", 300, 1);
        worldChange.world(mob);
        worldChange.manager.tickForTesting(worldChange.mc);
        worldChange.manager.resolveForTesting(marker(mob, "example:late"));
        FakeCapture.Request staleWorld = worldChange.capture.requests.get(0);
        worldChange.manager.clearForTesting(worldChange.mc);
        staleWorld.complete(image(9, 7));
        worldChange.mc.flushTasks();
        check(worldChange.textureManager.registeredCount() == 0, "callback after world clear is rejected before upload");
        check(staleWorld.lastImage.closed, "late world image is closed");

        Harness reload = new Harness();
        Entity reloaded = entity("example:reload", 301, 1);
        reload.world(reloaded);
        reload.manager.tickForTesting(reload.mc);
        reload.manager.resolveForTesting(marker(reloaded, "example:reload"));
        FakeCapture.Request staleGeneration = reload.capture.requests.get(0);
        MarkerIconCatalog.generation++;
        reload.manager.tickForTesting(reload.mc);
        staleGeneration.complete(image(5, 5));
        reload.mc.flushTasks();
        check(reload.textureManager.registeredCount() == 0, "callback from a stale resource generation is rejected");
        check(staleGeneration.lastImage.closed, "stale resource image is closed");
    }

    private static void retiresTexturesAfterQueuedGuiDrawsCanFinish() {
        Harness h = new Harness();
        Entity mob = entity("example:retire", 400, 1);
        h.world(mob);
        h.manager.tickForTesting(h.mc);
        h.manager.resolveForTesting(marker(mob, "example:retire"));
        h.capture.requests.get(0).complete(image(10, 6));
        h.mc.flushTasks();
        check(h.textureManager.registeredCount() == 1, "captured icon is registered");

        h.manager.clearForTesting(h.mc);
        check(h.textureManager.releaseCount == 0, "clearing does not release texture used by extracted GUI draw");
        h.manager.tickForTesting(h.mc);
        check(h.textureManager.releaseCount == 0, "texture remains alive through the first safe frame");
        h.manager.tickForTesting(h.mc);
        h.manager.tickForTesting(h.mc);
        check(h.textureManager.releaseCount == 1, "texture is eventually released after queued draws complete");
    }

    private static NativeImage image(int width, int height) {
        NativeImage image = new NativeImage(width, height);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) image.setPixelABGR(x, y, 0xFFFFFFFF);
        }
        return image;
    }

    private static Identifier texture(MarkerIconCatalog.Texture texture) {
        return texture == null ? null : texture.location();
    }

    private static Marker marker(Entity entity, String typeId) { return marker(entity.getUUID(), typeId); }

    private static Marker marker(UUID id, String typeId) { return new Marker(id, "mob:" + typeId); }

    private static Entity entity(String id, long mostBits, double distance) {
        return new Entity(uuid(mostBits), new EntityType(id), distance, false);
    }

    private static Entity living(String id, long mostBits, double distance) {
        return new LivingEntity(uuid(mostBits), new EntityType(id), distance);
    }

    private static UUID uuid(long mostBits) { return new UUID(mostBits, 1); }

    private static void check(boolean condition, String description) {
        assertions++;
        if (!condition) throw new AssertionError(description);
    }

    private static final class Harness {
        private final AtomicLong clock = new AtomicLong();
        private final TextureManager textureManager = new TextureManager();
        private final Minecraft mc = new Minecraft(textureManager);
        private final FakeCapture capture = new FakeCapture();
        private final EntityIconSprites manager = new EntityIconSprites(capture, clock::get);

        private Harness() {
            mc.player = new LocalPlayer(uuid(0), 0);
            mc.level = new ClientLevel();
        }

        private void world(Entity... entities) {
            mc.level.entities.clear();
            for (Entity entity : entities) mc.level.entities.add(entity);
        }
    }

    private static final class FakeCapture implements EntityIconSprites.Capture {
        private final List<Request> requests = new ArrayList<>();

        @Override
        public void capture(Minecraft mc, Entity entity, boolean preferHead, Consumer<NativeImage> callback) {
            requests.add(new Request(entity, preferHead, callback));
        }

        private static final class Request {
            private final Entity entity;
            private final boolean preferHead;
            private final Consumer<NativeImage> callback;
            private NativeImage lastImage;

            private Request(Entity entity, boolean preferHead, Consumer<NativeImage> callback) {
                this.entity = entity;
                this.preferHead = preferHead;
                this.callback = callback;
            }

            private void complete(NativeImage image) {
                lastImage = image;
                callback.accept(image);
            }
        }
    }

    private static final class ModdedCod extends Cod {
        private ModdedCod(UUID uuid) { super(uuid, "example:codish"); }
    }
}
