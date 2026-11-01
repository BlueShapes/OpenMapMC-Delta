package net.aosankaku.openmapdelta.compat;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
public final class EmojifulTextAbsentTest {
    public static void main(String[] args) throws Exception {
        FormattedCharSequence original = Component.literal(":smile: station").getVisualOrderText();
        Object result = Class.forName("net.aosankaku.openmapdelta.compat.EmojifulTextCompat")
                .getMethod("outlineSequence", FormattedCharSequence.class).invoke(null, original);
        if (result != original) throw new AssertionError("No optional API must preserve the native sequence");
        System.out.println("Emojiful text absent-classpath test passed.");
    }
}
