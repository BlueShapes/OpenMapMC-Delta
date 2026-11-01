package net.minecraft.resources;

public final class Identifier {
    private final String value;
    private Identifier(String value) { this.value = value; }
    public static Identifier parse(String value) { return new Identifier(value); }
    @Override public String toString() { return value; }
}
