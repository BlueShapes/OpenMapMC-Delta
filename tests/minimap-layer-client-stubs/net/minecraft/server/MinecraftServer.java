package net.minecraft.server;

import java.nio.file.Path;
import net.minecraft.world.level.storage.LevelResource;

public final class MinecraftServer {
    public Path getWorldPath(LevelResource resource) { return Path.of("test-world"); }
}
