package net.minecraft.network.chat;
public class Component implements FormattedText {
    protected String value;
    public boolean buttonGlyph;
    protected Component(String value) { this.value=value; }
    public static MutableComponent empty() { return literal(""); }
    public static MutableComponent literal(String text) { return new MutableComponent(text); }
    public static MutableComponent translatable(String key,Object... args) {
        StringBuilder text=new StringBuilder(key);
        for (Object arg:args) text.append("[").append(arg).append("]");
        MutableComponent result=literal(text.toString());
        for (Object arg:args) if(arg instanceof Component component) result.buttonGlyph|=component.buttonGlyph;
        return result;
    }
    public String getString() { return value; }
    public boolean hasGlyph() { return buttonGlyph; }
    @Override public String toString() { return value; }
}
