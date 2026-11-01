package com.mojang.authlib;

import java.util.UUID;

/** Provides both legacy and record-style accessor names used across the version roots. */
public final class GameProfile {
    private final UUID id;
    private final String name;
    public GameProfile(UUID id, String name) { this.id = id; this.name = name; }
    public UUID getId() { return id; }
    public String getName() { return name; }
    public UUID id() { return id; }
    public String name() { return name; }
}
