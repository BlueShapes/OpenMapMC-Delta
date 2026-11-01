package net.minecraft.server.level;
import java.util.UUID;
public final class ServerPlayer {
    private final UUID id = UUID.randomUUID();
    private String name = "test-player";
    public boolean operator;
    public int notices;
    public record Profile(UUID id) {}
    public record Name(String value) { public String getString() { return value; } }
    public final class CommandSource { public boolean hasPermission(int level) { return operator && level <= 2; } }
    public UUID getUUID() { return id; }
    public Profile getGameProfile() { return new Profile(id); }
    public Profile nameAndId() { return new Profile(id); }
    public Name getName() { return new Name(name); }
    public void setName(String name) { this.name = name; }
    public CommandSource createCommandSourceStack() { return new CommandSource(); }
    public CommandSource permissions() { return new CommandSource(); }
    public void sendSystemMessage(String message) { notices++; }
}
