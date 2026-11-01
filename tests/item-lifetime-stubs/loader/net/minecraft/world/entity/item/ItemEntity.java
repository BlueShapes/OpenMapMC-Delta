// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.minecraft.world.entity.item;
public final class ItemEntity {
    private int age;
    public int lifespan;
    public ItemEntity(int age, int lifespan) { this.age = age; this.lifespan = lifespan; }
    public int getAge() { return age; }
    public void setAge(int value) { age = value; }
    public void setLifetime(int value) { lifespan = value; }
}
