package net.aosankaku.openmapdelta.marker;
import com.hrznstudio.emojiful.render.EmojiFontHelper;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import java.lang.reflect.Method;
import java.util.function.BiFunction;
import java.util.function.ToIntFunction;

public final class EmojifulMapTextTest {
    private static int assertions;
    public static void main(String[] args) throws Exception {
        verifyOutlinePasses();
        verifyUnhoveredAndFailureFallback();
        verifyFitting();
        System.out.println("Emojiful map text tests passed (" + assertions + " assertions).");
    }
    private static void verifyOutlinePasses() {
        var graphics = new GuiGraphicsExtractor();
        MarkerLabelRenderer.draw(graphics, new Font(), ":long_alias: station", 20, 30, 0xFF22AA66, true);
        check(graphics.texts.size() == 9, "eight outline passes and one foreground");
        for (int i = 0; i < 8; i++) {
            var call = graphics.texts.get(i);
            check(call.sequence() instanceof EmojiFontHelper.Outline, "halo needs the optional emoji outline display policy");
            check(call.dx() != 0 || call.dy() != 0, "each halo offset is nonzero");
            check(text(call.sequence()).equals(":long_alias: station"), "halo preserves displayed text");
        }
        var foreground = graphics.texts.get(8);
        check(!(foreground.sequence() instanceof EmojiFontHelper.Outline), "foreground keeps original emoji colors");
        check(foreground.dx() == 0 && foreground.dy() == 0, "foreground pose restored");
        check(EmojiFontHelper.calls == 1, "one optional conversion shared by all eight passes");
    }
    private static void verifyUnhoveredAndFailureFallback() throws Exception {
        var graphics = new GuiGraphicsExtractor();
        int before = EmojiFontHelper.calls;
        MarkerLabelRenderer.draw(graphics, new Font(), "station", 0, 0, 0xFF123456, false);
        check(graphics.texts.size() == 1 && EmojiFontHelper.calls == before, "plain foreground skips outline conversion");
        FormattedCharSequence original = Component.literal("station").getVisualOrderText();
        Method outline = Class.forName("net.aosankaku.openmapdelta.compat.EmojifulTextCompat")
                .getMethod("outlineSequence", FormattedCharSequence.class);
        EmojiFontHelper.fail = true;
        try { check(outline.invoke(null, original) == original, "broken optional API preserves native text"); }
        finally { EmojiFontHelper.fail = false; }
    }
    private static void verifyFitting() throws Exception {
        Method fit = Class.forName("net.aosankaku.openmapdelta.util.MapTextFitting").getMethod(
                "fit", String.class, int.class, ToIntFunction.class, BiFunction.class);
        String alias = ":very_long_alias:";
        ToIntFunction<String> width = text -> text.replace(alias, "XX").codePointCount(0, text.replace(alias, "XX").length()) * 5;
        int[] calls = {0};
        BiFunction<String, Integer, String> prefix = (source, pixels) -> {
            calls[0]++;
            check(source.equals(alias + " suffix"), "native prefix receives complete source, not a partially deleted alias");
            check(pixels == 10, "ellipsis budget is reserved before clipping");
            return alias;
        };
        String fitted = (String) fit.invoke(null, alias + " suffix", 15, width, prefix);
        check(fitted.equals(alias + "…") && calls[0] == 1, "fitting uses one native atomic prefix operation");
        check(fit.invoke(null, alias, 10, width, prefix).equals(alias), "a fitting emoji retains its raw alias");
        check(fit.invoke(null, "suffix", 4, width, prefix).equals(""), "too-small label hides without partial ellipsis");
        check(fit.invoke(null, "suffix", 5, width, (BiFunction<String, Integer, String>)(s, p) -> "").equals("…"), "ellipsis alone fits exactly");
    }
    private static String text(FormattedCharSequence value) {
        StringBuilder result = new StringBuilder();
        value.accept((index, style, cp) -> { result.appendCodePoint(cp); return true; });
        return result.toString();
    }
    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }
}
