package net.aosankaku.openmapdelta.marker;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Captures the real 26.2 map icon renderer, including non-square source crops. */
public final class EntityIconAspectTest {
    private static int checks;
    public static void main(String[] args) {
        var wide = new GuiGraphicsExtractor();
        MarkerIconRenderer.drawEntity(wide, new Marker("mob:minecraft:cat", 0xFFFFFFFF), 20, 30, 12, 153);
        check(wide.lastDrawWidth == 12 && wide.lastDrawHeight == 8, "wide head retains 6:4 aspect ratio");
        check(wide.lastLeft == 14 && wide.lastTop == 26, "wide head centered within marker");
        check(wide.lastAlpha == 153 && wide.pose().depth == 0, "wide head alpha and pose retained");
        var tall = new GuiGraphicsExtractor();
        MarkerIconRenderer.drawEntity(tall, new Marker("mob:minecraft:villager", 0xFFFFFFFF), 20, 30, 10, 51);
        check(tall.lastDrawWidth == 8 && tall.lastDrawHeight == 10, "tall head retains 8:10 aspect ratio");
        check(tall.lastLeft == 16 && tall.lastTop == 25, "tall head centered within marker");
        var ordinary = new GuiGraphicsExtractor();
        MarkerIconRenderer.drawEntry(ordinary, MarkerIconCatalog.find("mob:minecraft:cat"), 20, 30, 12);
        check(ordinary.lastDrawWidth == 12 && ordinary.lastDrawHeight == 8, "picker icons retain aspect too");
        var generated = new GuiGraphicsExtractor();
        MarkerIconRenderer.drawEntity(generated, new Marker("mob:example:generated", 0xFFFFFFFF), 20, 30, 12, 102);
        check(generated.blitCount == 1 && generated.fillCount == 0, "unknown mod entity uses generated sprite");
        check(generated.lastDrawWidth == 12 && generated.lastDrawHeight == 6 && generated.lastAlpha == 102,
                "generated image retains aspect and height opacity");
        var fish = new GuiGraphicsExtractor();
        MarkerIconRenderer.drawEntity(fish, new Marker("mob:minecraft:cod", 0xFFFFFFFF), 20, 30, 12, 255);
        check(fish.lastDrawWidth == 12 && fish.lastDrawHeight == 6, "generated fish replaces its square fallback");
        System.out.println("Entity icon aspect checks passed (" + checks + ").");
    }
    private static void check(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }
}
