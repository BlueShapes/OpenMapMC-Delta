package net.minecraft.world.item;

public final class ItemStack {
    private final boolean empty;
    public ItemStack(boolean empty) { this.empty = empty; }
    public boolean isEmpty() { return empty; }
}
