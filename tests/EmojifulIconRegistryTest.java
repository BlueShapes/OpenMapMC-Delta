// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.compat;

import com.hrznstudio.emojiful.Constants;
import com.hrznstudio.emojiful.api.Emoji;
import com.hrznstudio.emojiful.api.EmojiFromTwitmoji;
import net.aosankaku.openmapdelta.marker.MarkerIconId;
import net.aosankaku.openmapdelta.marker.MarkerIconSearchQuery;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public final class EmojifulIconRegistryTest {
    private static int assertions;

    public static void main(String[] args) {
        AtomicBoolean present = new AtomicBoolean(false);
        AtomicLong clock = new AtomicLong();
        AtomicInteger reads = new AtomicInteger();
        List<Object> live = new ArrayList<>();
        EmojifulIconRegistry registry = new EmojifulIconRegistry(present::get,
                () -> { reads.incrementAndGet(); return live; }, clock::get);
        check(registry.snapshot().isEmpty() && reads.get() == 0, "absent mod does not inspect its API");
        present.set(true);
        check(registry.snapshot().isEmpty(), "background initialization may start empty");
        Emoji smile = new Emoji("smile", ":smile:", ":happy:");
        smile.texts = List.of(":)");
        live.add(smile);
        check(registry.snapshot().isEmpty(), "metadata refresh has a one-second limit");
        clock.addAndGet(1001);
        var icon = registry.snapshot().get(0);
        String id = icon.id();
        check(id.equals("emoji:emojiful:2fd64fd0ae04becd01e138285edfa3769d4c61b05bedb8e50a757359a0eb5a43"),
                "persistent identity is the SHA-256 of the canonical UTF-8 alias");
        check(MarkerIconId.normalize(id).equals(id), "emoji identity survives serialization");
        check(icon.aliases().equals(List.of(":smile:", ":happy:")) && icon.texts().equals(List.of(":)")),
                "aliases and alternate text remain searchable");
        check(smile.loadRequests == 0, "enumeration never starts image downloads");
        int currentReads = reads.get();
        for (int i = 0; i < 20; i++) { registry.snapshot(); registry.find(id); registry.revision(); }
        check(reads.get() == currentReads, "many markers share a bounded metadata refresh");
        check(registry.resolveFrame(icon).equals("emojiful:frame_0"), "selected frame is resolved lazily");
        smile.frame = "emojiful:frame_1";
        check(registry.resolveFrame(icon).equals(smile.frame), "animated frame changes are not cached in metadata");
        for (Object placeholder : List.of(Emoji.loading_texture, Emoji.noSignal_texture, Emoji.error_texture)) {
            check(registry.isPlaceholderFrame(placeholder), "loading and failed frames use colored fallback");
        }
        check(registry.isPlaceholderFrame(null) && !registry.isPlaceholderFrame(smile.frame), "only ready frames render");
        smile.failFrame = true;
        check(registry.resolveFrame(icon) == null, "one failing download does not escape the bridge");
        smile.failFrame = false;

        long revision = registry.revision();
        Emoji replacement = new EmojiFromTwitmoji("twemojis_smile", ":smile:");
        live.set(0, replacement);
        clock.addAndGet(1001);
        check(registry.find(id).handle() == replacement, "same-size recipe replacement updates the live handle");
        check(registry.revision() > revision, "a replaced live handle invalidates picker and web caches");
        check(registry.find(id).id().equals(id), "upstream name changes keep the selected alias identity");
        present.set(false);
        check(registry.find(id) == null, "uninstall hides the unavailable image");
        check(MarkerIconId.normalize(id).equals(id), "uninstall does not erase a saved choice");
        present.set(true);
        clock.addAndGet(1001);
        check(registry.find(id) != null, "reinstall restores the saved choice");
        live.clear();
        clock.addAndGet(1001);
        check(registry.find(id) == null, "a removed world recipe no longer resolves");

        Emoji twemoji = new Emoji("twemojis_station", ":station:");
        Emoji custom = new Emoji("custom_station", ":station:");
        Emoji world = new Emoji("z_world_station", ":station:");
        world.worldBased = true;
        live.addAll(List.of(twemoji, custom, world));
        clock.addAndGet(1001);
        check(registry.snapshot().size() == 1 && registry.snapshot().get(0).handle() == world,
                "world custom emoji wins a duplicate alias deterministically");
        live.remove(world);
        clock.addAndGet(1001);
        check(registry.snapshot().get(0).handle() == custom, "custom emoji wins over a standard alias");
        live.clear();
        Emoji unicode = new Emoji("custom_駅", "", ":駅🚉:");
        live.add(unicode);
        live.add(new Object());
        clock.addAndGet(1001);
        check(registry.snapshot().size() == 1, "malformed entries do not hide valid Unicode aliases");
        check(MarkerIconId.normalize(registry.snapshot().get(0).id()).equals(registry.snapshot().get(0).id()),
                "Unicode aliases fit the persistent resource key");
        var retainedSnapshot = registry.snapshot();
        live.clear();
        registry.invalidate();
        check(registry.snapshot().isEmpty(), "resource or world invalidation bypasses the refresh throttle");
        check(retainedSnapshot.size() == 1, "published metadata stays immutable after a catalog refresh");
        var changing = new ArrayList<Object>(List.of(unicode)) {
            @Override public java.util.Iterator<Object> iterator() { throw new java.util.ConcurrentModificationException(); }
        };
        var concurrent = new EmojifulIconRegistry(() -> true, () -> changing, clock::get);
        check(concurrent.snapshot().size() == 1, "background catalog mutation can recover through a stable array copy");

        check(registry.isUploaded(new Emoji.DownloadImageData(true)), "custom public upload flag is supported");
        check(!registry.isUploaded(new Emoji.DownloadImageData(false)), "custom pending image uses fallback");
        check(registry.isUploaded(new EmojiFromTwitmoji.DownloadImageData(true)), "Twemoji private upload flag is supported");
        check(!registry.isUploaded(new EmojiFromTwitmoji.DownloadImageData(false)),
                "private subclass flag takes precedence over an uploaded base flag");
        check(!registry.isUploaded(new UnrelatedTexture()) && !registry.isUploaded(null),
                "arbitrary texture flags are never treated as Emojiful images");

        Constants.EMOJI_LIST.add(unicode);
        var reflected = EmojifulIconRegistry.forEmojiful(() -> true, clock::get);
        check(reflected.snapshot().size() == 1 && reflected.snapshot().get(0).handle() == unicode,
                "production reflection reads the authoritative live catalog");
        Constants.EMOJI_LIST.clear();
        var broken = new EmojifulIconRegistry(() -> true, () -> { throw new NoClassDefFoundError("optional API gone"); }, clock::get);
        check(broken.snapshot().isEmpty(), "an incompatible optional API falls back safely");
        var metadata = new MarkerIconSearchQuery.Metadata("Smile happy", id, "emojiful", "Emojiful",
                List.of(), List.of(":smile:", ":happy:", ":)"));
        check(MarkerIconSearchQuery.matches(MarkerIconSearchQuery.parse("@emojiful"), metadata), "mod search includes emoji entries");
        check(MarkerIconSearchQuery.matches(MarkerIconSearchQuery.parse("$happy"), metadata), "alias search includes emoji entries");
        System.out.println("Emojiful icon registry tests passed (" + assertions + " assertions).");
    }

    public static final class UnrelatedTexture { public boolean textureUploaded = true; }
    private static void check(boolean value, String message) {
        assertions++;
        if (!value) throw new AssertionError(message);
    }
}
