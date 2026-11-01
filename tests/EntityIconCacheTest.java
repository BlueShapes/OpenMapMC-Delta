// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.marker;

import java.util.ArrayList;
import java.util.List;

public final class EntityIconCacheTest {
    private static int assertions;

    public static void main(String[] args) {
        cachesByCompleteTypeAndAppearanceKey();
        evictsLeastRecentlyUsedAndDisposesResources();
        boundsPendingCapturesAndCaptureRate();
        appliesRetryCooldownAtTheBoundary();
        clearRejectsStaleCompletionAndCancelsOldPending();
        duplicateCompletionDoesNotDisposeCachedResource();
        nullCompletionReleasesPendingReservation();
        disposerFailureDoesNotInterruptCleanup();
        refreshKeepsOldResourceUntilSuccessfulReplacement();
        System.out.println("Entity icon cache tests passed (" + assertions + " assertions).");
    }

    private static void cachesByCompleteTypeAndAppearanceKey() {
        List<Resource> disposed = new ArrayList<>();
        EntityIconCache<Key, Resource> cache = cache(4, 4, 0, 10, disposed);
        Key salmon = new Key("minecraft:salmon", "adult");
        Key modSalmon = new Key("example:salmon", "adult");
        Key patternedSalmon = new Key("minecraft:salmon", "patterned");

        EntityIconCache.Ticket<Key> first = cache.reserve(salmon, 0);
        EntityIconCache.Ticket<Key> second = cache.reserve(modSalmon, 1);
        EntityIconCache.Ticket<Key> third = cache.reserve(patternedSalmon, 2);
        Resource firstIcon = new Resource("vanilla salmon");
        Resource secondIcon = new Resource("mod salmon");
        Resource thirdIcon = new Resource("patterned salmon");
        check(first != null && second != null && third != null, "namespace and appearance keys reserve independently");
        check(cache.complete(first, firstIcon), "first icon is accepted");
        check(cache.complete(second, secondIcon), "second icon is accepted");
        check(cache.complete(third, thirdIcon), "third icon is accepted");
        check(cache.get(salmon) == firstIcon, "lookup reuses the exact cached icon");
        check(cache.get(modSalmon) == secondIcon, "namespace remains part of the cache key");
        check(cache.get(patternedSalmon) == thirdIcon, "appearance remains part of the cache key");
        check(disposed.isEmpty(), "cache hits do not dispose resources");
    }

    private static void evictsLeastRecentlyUsedAndDisposesResources() {
        List<Resource> disposed = new ArrayList<>();
        EntityIconCache<String, Resource> cache = cache(2, 2, 0, 10, disposed);
        Resource first = complete(cache, "first", 0);
        Resource second = complete(cache, "second", 1);
        check(cache.get("first") == first, "lookup updates LRU order");
        Resource third = complete(cache, "third", 2);

        check(cache.size() == 2, "ready cache stays within its capacity");
        check(cache.get("first") == first, "recently accessed entry survives eviction");
        check(cache.get("second") == null, "least recently used entry is evicted");
        check(disposed.size() == 1 && disposed.get(0) == second, "eviction disposes its resource once");
        cache.clear();
        check(cache.size() == 0, "clear empties ready entries");
        check(disposed.size() == 3 && disposed.contains(first) && disposed.contains(third),
                "clear disposes each remaining resource");
    }

    private static void boundsPendingCapturesAndCaptureRate() {
        List<Resource> disposed = new ArrayList<>();
        EntityIconCache<String, Resource> cache = cache(2, 2, 10, 10, disposed);
        EntityIconCache.Ticket<String> first = cache.reserve("first", 0);
        check(first != null, "first request is allowed at monotonic time zero");
        check(cache.reserve("first", 10) == null, "duplicate in-flight request is suppressed");
        check(cache.reserve("second", 5) == null, "capture interval rate-limits distinct keys");
        EntityIconCache.Ticket<String> second = cache.reserve("second", 10);
        check(second != null, "a request is allowed at the capture interval boundary");
        check(cache.reserve("third", 20) == null, "pending capture count has a hard limit");
        check(cache.pendingCount() == 2, "pending count reflects only admitted captures");
        cache.fail(first, 20);
        check(cache.pendingCount() == 1, "failed capture releases its pending slot");
    }

    private static void appliesRetryCooldownAtTheBoundary() {
        List<Resource> disposed = new ArrayList<>();
        EntityIconCache<String, Resource> cache = cache(2, 2, 0, 20, disposed);
        EntityIconCache.Ticket<String> failed = cache.reserve("key", 100);
        cache.fail(failed, 100);

        check(cache.reserve("key", 119) == null, "failed key remains cooled down before retry time");
        EntityIconCache.Ticket<String> retried = cache.reserve("key", 120);
        check(retried != null, "retry becomes available at the cooldown boundary");
        check(!cache.fail(failed, 121), "stale failure cannot clear a newer reservation");
        check(cache.pendingCount() == 1, "newer reservation remains pending after stale failure");
        check(cache.complete(retried, new Resource("recovered")), "retry can populate the cache");
    }

    private static void clearRejectsStaleCompletionAndCancelsOldPending() {
        List<Resource> disposed = new ArrayList<>();
        EntityIconCache<String, Resource> cache = cache(2, 2, 100, 20, disposed);
        EntityIconCache.Ticket<String> stale = cache.reserve("same", 0);
        cache.clear();
        check(cache.size() == 0 && cache.pendingCount() == 0, "clear empties the cache and cancels reservations");
        Resource staleResult = new Resource("stale result");
        check(!cache.complete(stale, staleResult), "completion from before clear is rejected");
        check(disposed.size() == 1 && disposed.get(0) == staleResult, "rejected result is disposed immediately");
        check(!cache.fail(stale, 100), "rejected stale ticket remains retired and cannot alter state");
        EntityIconCache.Ticket<String> fresh = cache.reserve("same", 1);
        check(fresh != null, "clear resets the rate budget and allows a fresh request");
        check(cache.complete(fresh, new Resource("fresh result")), "fresh completion is retained");
    }

    private static void duplicateCompletionDoesNotDisposeCachedResource() {
        List<Resource> disposed = new ArrayList<>();
        EntityIconCache<String, Resource> cache = cache(1, 1, 0, 10, disposed);
        EntityIconCache.Ticket<String> ticket = cache.reserve("key", 0);
        Resource icon = new Resource("icon");

        check(cache.complete(ticket, icon), "first completion succeeds");
        check(!cache.complete(ticket, icon), "duplicate completion is rejected");
        check(cache.get("key") == icon, "duplicate completion leaves cached resource intact");
        check(disposed.isEmpty(), "duplicate completion does not dispose the already cached resource");
        Resource unexpected = new Resource("unexpected duplicate");
        check(!cache.complete(ticket, unexpected), "different late duplicate is rejected");
        check(disposed.size() == 1 && disposed.get(0) == unexpected, "different rejected resource is disposed");

        complete(cache, "second-key", 1);
        check(disposed.contains(icon), "eviction disposes the original icon");
        int disposalCount = disposed.size();
        check(!cache.complete(ticket, icon), "duplicate after eviction remains rejected");
        check(disposed.size() == disposalCount, "duplicate after eviction does not dispose the icon twice");
    }

    private static void nullCompletionReleasesPendingReservation() {
        List<Resource> disposed = new ArrayList<>();
        EntityIconCache<String, Resource> cache = cache(1, 1, 0, 100, disposed);
        EntityIconCache.Ticket<String> unavailable = cache.reserve("key", 0);

        check(!cache.complete(unavailable, null), "null capture result is not cached");
        check(cache.pendingCount() == 0, "null completion releases its pending slot");
        check(cache.reserve("key", 1) != null, "null completion does not leave a sticky retry cooldown");
        check(disposed.isEmpty(), "null completion has no resource to dispose");
    }

    private static void disposerFailureDoesNotInterruptCleanup() {
        List<Resource> attempted = new ArrayList<>();
        EntityIconCache<String, Resource> cache = new EntityIconCache<>(2, 2, 0, 10, resource -> {
            attempted.add(resource);
            if (resource.name.equals("broken close")) throw new IllegalStateException("synthetic disposal failure");
        });
        Resource broken = complete(cache, "broken", 0);
        Resource healthy = complete(cache, "healthy", 1);

        cache.clear();
        check(attempted.contains(broken), "failing disposer was attempted");
        check(attempted.contains(healthy), "one disposer failure does not skip remaining resources");
        check(cache.size() == 0 && cache.pendingCount() == 0, "cleanup completes despite disposer failure");
    }

    private static void refreshKeepsOldResourceUntilSuccessfulReplacement() {
        List<Resource> disposed = new ArrayList<>();
        EntityIconCache<String, Resource> cache = cache(2, 1, 10, 20, disposed);
        Resource original = complete(cache, "key", 0);

        EntityIconCache.Ticket<String> refresh = cache.reserveRefresh("key", 10);
        check(refresh != null, "an existing cache entry can reserve a refresh");
        check(cache.get("key") == original, "the old icon remains available while refresh is pending");
        check(cache.reserveRefresh("key", 20) == null, "duplicate refresh is suppressed");
        check(cache.reserve("other", 20) == null, "refresh consumes a bounded pending slot");
        check(cache.fail(refresh, 20), "failed refresh retires its reservation");
        check(cache.get("key") == original, "failed refresh leaves the old icon cached");
        check(disposed.isEmpty(), "failed refresh does not dispose the old icon");
        check(cache.reserveRefresh("key", 39) == null, "failed refresh observes retry cooldown");

        EntityIconCache.Ticket<String> retry = cache.reserveRefresh("key", 40);
        Resource replacement = new Resource("replacement");
        check(retry != null && cache.complete(retry, replacement), "successful retry replaces the old icon");
        check(cache.get("key") == replacement, "replacement becomes the ready cache value");
        check(disposed.size() == 1 && disposed.get(0) == original,
                "replacing an icon disposes the prior texture exactly once");
        check(!cache.complete(refresh, new Resource("stale refresh")), "stale refresh completion is rejected");
        check(disposed.size() == 2 && disposed.get(1).name.equals("stale refresh"),
                "a stale independent refresh result is disposed");
        cache.clear();
        check(disposed.size() == 3 && disposed.get(2) == replacement,
                "clear disposes the replacement once after refresh");
    }

    private static <K> EntityIconCache<K, Resource> cache(int capacity, int maxPending,
                                                           long captureInterval, long retry,
                                                           List<Resource> disposed) {
        return new EntityIconCache<>(capacity, maxPending, captureInterval, retry, disposed::add);
    }

    private static Resource complete(EntityIconCache<String, Resource> cache, String key, long now) {
        EntityIconCache.Ticket<String> ticket = cache.reserve(key, now);
        check(ticket != null, key + " request is admitted");
        Resource resource = new Resource(key);
        check(cache.complete(ticket, resource), key + " completion is accepted");
        return resource;
    }

    private static void check(boolean condition, String description) {
        assertions++;
        if (!condition) throw new AssertionError(description);
    }

    private record Key(String type, String appearance) {}
    private static final class Resource {
        private final String name;
        private Resource(String name) { this.name = name; }
    }
}
