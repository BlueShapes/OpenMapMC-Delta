// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.marker;

public final class MarkerIconTest {
    private static int assertions;
    public static void main(String[] args) {
        check(MarkerIconId.normalize("item:minecraft:diamond").equals("item:minecraft:diamond"), "item identity survives persistence");
        check(MarkerIconId.normalize("mob:minecraft:creeper").equals("mob:minecraft:creeper"), "mob identity survives persistence");
        check(MarkerIconId.normalize("symbol:odysseymap:mtr_station").equals("symbol:odysseymap:mtr_station"), "MTR symbol identity survives persistence");
        check(MarkerIconId.normalize("symbol:odysseymap:create_station").equals("symbol:odysseymap:create_station"), "Create symbol identity survives persistence");
        check(MarkerIconId.normalize("item:some_mod:removed_item").equals("item:some_mod:removed_item"), "valid removed item IDs stay persisted for future recovery");
        String emoji = "emoji:emojiful:" + "a".repeat(64);
        check(MarkerIconId.normalize(emoji).equals(emoji), "unavailable emoji identities stay persisted for future recovery");
        for (String invalid : new String[]{"emoji:other:" + "a".repeat(64), "emoji:emojiful:" + "A".repeat(64),
                "emoji:emojiful:" + "a".repeat(63), "emoji:emojiful::smile:", "emoji:emojiful:" + "g".repeat(64)}) {
            check(MarkerIconId.normalize(invalid).isEmpty(), "malformed emoji identities fall back safely");
        }
        for (String invalid : new String[]{"", "item:../diamond", "mob:minecraft:Creeper", "minecraft:diamond", "https://example.com/icon", "item:" + "a".repeat(170)}) {
            check(MarkerIconId.normalize(invalid).isEmpty(), "invalid identities fall back safely");
        }
        check(MarkerIconId.normalize(null).isEmpty(), "old files with no icon remain readable");

        var crate = new MarkerIconSearchQuery.Metadata("Brass Crate", "create:brass_crate", "create",
                "Create Trains", java.util.List.of("forge:storage_blocks/brass"),
                java.util.List.of("Stores items", "Hold Shift for details"));
        check(MarkerIconSearchQuery.matches(MarkerIconSearchQuery.parse("@create"), crate), "@ searches mod ID");
        check(MarkerIconSearchQuery.matches(MarkerIconSearchQuery.parse("@\"Create Trains\""), crate), "quoted @ searches mod display name without spaces");
        check(MarkerIconSearchQuery.matches(MarkerIconSearchQuery.parse("&create:brass_crate"), crate), "& searches resource ID");
        check(MarkerIconSearchQuery.matches(MarkerIconSearchQuery.parse("#storage_blocks/brass"), crate), "# searches item tags");
        check(MarkerIconSearchQuery.matches(MarkerIconSearchQuery.parse("$\"Hold Shift\" $details"), crate), "$ searches tooltip terms with AND semantics");
        check(MarkerIconSearchQuery.matches(MarkerIconSearchQuery.parse("brass crate"), crate), "unprefixed words use AND semantics");
        check(MarkerIconSearchQuery.matches(MarkerIconSearchQuery.parse("diamond | \"Brass Crate\""), crate), "quoted alternatives use OR semantics");
        check(!MarkerIconSearchQuery.matches(MarkerIconSearchQuery.parse("@minecraft"), crate), "@ does not match a different namespace");
        check(!MarkerIconSearchQuery.matches(MarkerIconSearchQuery.parse("brass -@create"), crate), "leading minus excludes a matching entry");
        check(MarkerIconSearchQuery.matches(MarkerIconSearchQuery.parse("brass -@minecraft"), crate), "leading minus keeps a nonexcluded entry");
        check(MarkerIconSearchQuery.matches(MarkerIconSearchQuery.parse(""), crate), "an empty query matches all entries");
        check(!MarkerIconSearchQuery.matches(MarkerIconSearchQuery.parse("brass |"), new MarkerIconSearchQuery.Metadata(
                "Iron", "minecraft:iron_ingot", "minecraft", "Minecraft", java.util.List.of(), java.util.List.of())),
                "a trailing OR operator does not create a match-all alternative");
        String unicodeName = "İron Ingot";
        boolean[] highlighted = new boolean[unicodeName.length()];
        MarkerIconSearchQuery.markCaseInsensitiveMatches(unicodeName, "ingot", highlighted);
        check(highlighted.length == unicodeName.length(), "highlight indices keep the original Unicode string length");
        for (int i = 0; i < unicodeName.length(); i++) {
            check(highlighted[i] == (i >= 5), "highlight positions remain valid after Unicode case folding");
        }
        boolean[] overlapping = new boolean[3];
        MarkerIconSearchQuery.markCaseInsensitiveMatches("aaa", "aa", overlapping);
        check(overlapping[0] && overlapping[1] && overlapping[2], "overlapping search hits highlight their complete span");
        check(MarkerIconSizing.minimapEdgeRadius(128, 2) == 66,
                "default clamped icon centers reach outside the terrain circle");
        check(MarkerIconSizing.minimapEdgeRadius(128, -4) == 60,
                "an inward offset can restore the previous icon position");
        check(MarkerIconSizing.minimapEdgeRadius(128, 0) == 64,
                "zero offset places the icon center on the circle");
        check(MarkerIconSizing.minimapEdgeRadius(128, 8) == 72,
                "positive settings move clamped icons farther out");
        check(MarkerIconSizing.minimapEdgeRadius(20, -16) == 0,
                "inward offsets never create a negative clamping radius");
        check(MarkerIconSizing.minimapEdgeOverhang(2, 7) == 9,
                "default outer icon and halo fit in the reserved compass margin");
        check(MarkerIconSizing.minimapEdgeOverhang(8, 20) == 22,
                "maximum offset and scale reserve space for the full navigation halo");
        check(MarkerIconSizing.minimapEdgeOverhang(-16, 7) == 0,
                "fully inset icons need no extra margin");
        int previousDot = Integer.MAX_VALUE;
        int previousIcon = Integer.MAX_VALUE;
        for (int bpp = 1; bpp <= 256; bpp++) {
            int dot = MarkerIconSizing.fullscreenSize(bpp, false);
            int icon = MarkerIconSizing.fullscreenSize(bpp, true);
            check(dot <= previousDot && icon <= previousIcon, "zooming out never enlarges markers");
            check(dot >= 2 && icon >= 4, "small markers stay visible");
            previousDot = dot; previousIcon = icon;
        }
        check(MarkerIconSizing.fullscreenSize(16, false) == 2, "distant waypoint dots shrink to two GUI pixels");
        check(MarkerIconSizing.fullscreenSize(0.5, true) == 23, "fullscreen item icons support the 2x zoom scale");
        check(PlayerMarkerSizing.fullscreenSize(16, false) == 2, "distant player dots use matching small geometry");
        for (int cap : new int[]{4, 16, 64}) {
            for (double bpp : new double[]{0.5, 1, 16, 1024}) {
                check(PlayerMarkerSizing.fullscreenSize(bpp, true, cap) <= cap,
                        "player head respects the configured fullscreen size cap");
                check(PlayerMarkerSizing.fullscreenSize(bpp, false, cap) <= cap,
                        "player dot respects the configured fullscreen size cap");
                check(PlayerMarkerSizing.fullscreenSize(bpp, true, cap) >= 1
                                && PlayerMarkerSizing.fullscreenSize(bpp, false, cap) >= 1,
                        "configured size caps retain a visible player marker");
            }
        }
        for (int size : new int[]{2, 3, 5, 16}) {
            int left = 100 - size / 2;
            check(MarkerIconSizing.contains(left, left, 100, 100, size), "drawn top-left pixel is a hit");
            check(MarkerIconSizing.contains(left + size - 0.01, left + size - 0.01, 100, 100, size), "drawn bottom-right pixel is a hit");
            check(!MarkerIconSizing.contains(left + size, 100, 100, 100, size), "outside right edge is not a hit");
            check(!MarkerIconSizing.contains(left - 0.01, 100, 100, 100, size), "outside left edge is not a hit");
        }
        System.out.println("Marker icon tests passed (" + assertions + " assertions).");
    }
    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }
}
