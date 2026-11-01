package net.minecraft.client.multiplayer;

public class ClientLevel {
    private final Object dimension;
    public ClientLevel(Object dimension) { this.dimension = dimension; }
    public Object dimension() { return dimension; }
}
