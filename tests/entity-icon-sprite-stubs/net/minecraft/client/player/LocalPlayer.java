package net.minecraft.client.player;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import java.util.UUID;

public final class LocalPlayer extends Player {
    public LocalPlayer(UUID uuid, double distance) { super(uuid, new EntityType("minecraft:player"), distance); }
}
