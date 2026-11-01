package net.minecraft.core;

public class BlockPos {
    public static final BlockPos ZERO = new BlockPos(0, 0, 0);
    protected int x, y, z;
    public BlockPos(int x, int y, int z) { set(x, y, z); }
    public int getX() { return x; }
    public int getY() { return y; }
    public int getZ() { return z; }
    protected BlockPos set(int x, int y, int z) { this.x = x; this.y = y; this.z = z; return this; }

    public static final class MutableBlockPos extends BlockPos {
        public MutableBlockPos() { super(0, 0, 0); }
        @Override public MutableBlockPos set(int x, int y, int z) { super.set(x, y, z); return this; }
    }
}
