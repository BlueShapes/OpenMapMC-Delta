// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.minecraft.world.item;
import net.minecraft.world.level.Level;
public final class ItemStack {
    private final int lifespan;
    public ItemStack(int lifespan) { this.lifespan = lifespan; }
    public int getEntityLifespan(Level level) { return lifespan; }
}
