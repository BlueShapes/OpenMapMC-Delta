package net.minecraft.server.level;

import java.util.UUID;

/** Minimal actor surface used by AreaServer. */
public final class ServerPlayer {
    private final UUID id;
    private final Name name;

    public ServerPlayer(UUID id, String name) {
        this.id = id;
        this.name = new Name(name);
    }

    public UUID getUUID() { return id; }
    public Name getName() { return name; }

    public record Name(String value) { public String getString() { return value; } }
}
