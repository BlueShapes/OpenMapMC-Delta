package net.minecraft.resources;

public final class Identifier {
    private final String value;
    private Identifier(String value) { this.value = value; }
    public static Identifier tryParse(String value) {
        return value != null && value.contains(":") ? new Identifier(value) : null;
    }
    @Override public String toString() { return value; }
    @Override public boolean equals(Object other) { return other instanceof Identifier id && value.equals(id.value); }
    @Override public int hashCode() { return value.hashCode(); }
}
