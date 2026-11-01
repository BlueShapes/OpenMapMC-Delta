// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.web;

/** Checks the generation and shared-budget contract used by asynchronous GPU captures. */
public final class WebMapAsyncIconCacheTest {
    private static int checks;

    public static void main(String[] args) {
        WebMapIconCache cache = new WebMapIconCache();
        WebMapImagePixels red = new WebMapImagePixels(1, 1, new int[]{0xff0000ff});
        WebMapImagePixels blue = new WebMapImagePixels(1, 1, new int[]{0xffff0000});
        cache.activateGeneration(1);
        cache.beginPublish(1);
        check(!cache.consumeCapturePermit(0), "A stale capture consumed a permit");
        check(cache.consumeCapturePermit(1), "Current capture could not reserve its permit");
        check(!cache.consumeCapturePermit(1), "Capture budget exceeded its limit");
        int[] invoked = {0};
        check(cache.get("sync", 1, () -> { invoked[0]++; return red; }) == null && invoked[0] == 0,
                "Synchronous capture bypassed the shared asynchronous budget");
        var first = cache.put("item", 1, red);
        check(first != null && cache.peek("item", 1) == first,
                "Completed readback was not cached after spending its permit");
        check(cache.put("item", 1, red).revision() == first.revision(),
                "Identical asynchronous pixels changed the image revision");
        var changed = cache.put("item", 1, blue);
        check(changed.revision() > first.revision() && changed.copyPixels()[0] == 0xffff0000,
                "Changed readback pixels were not published");
        cache.activateGeneration(2);
        check(cache.peek("item", 2) == null && cache.peek("item", 1) == null,
                "Resource reload retained a stale image");
        check(cache.put("item", 1, red) == null && cache.peek("item", 2) == null,
                "Late readback repopulated the cache after resource reload");
        cache.beginPublish(1);
        check(cache.get("sync", 2, () -> red) != null && !cache.consumeCapturePermit(2),
                "Asynchronous capture bypassed the synchronous capture budget");
        for (int i = 0; i < 257; i++) cache.put("item:" + i, 2, red);
        check(cache.peek("item:0", 2) == null && cache.peek("item:256", 2) != null,
                "Completed asynchronous images escaped the bounded cache");
        cache.clear();
        check(cache.put("late", 2, red) == null && !cache.consumeCapturePermit(2),
                "Logout allowed an old readback or capture permit");
        System.out.println("Async icon cache: " + checks + " checks passed");
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
}
