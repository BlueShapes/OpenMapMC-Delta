package net.aosankaku.openmapdelta.marker;

public final class MarkerIconCatalog {
    private MarkerIconCatalog() {}

    public static long resourceGeneration() { return 1L; }

    public record Entry(String id, Item item) {}

    public static final class Item {
        public boolean isEmpty() { return false; }
    }
}
