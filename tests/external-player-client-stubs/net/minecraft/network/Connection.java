package net.minecraft.network;

public final class Connection {
    private final Object identity;
    public Connection() { this(new Object()); }
    public Connection(Object identity) { this.identity = identity; }
    public Object identity() { return identity; }
}
