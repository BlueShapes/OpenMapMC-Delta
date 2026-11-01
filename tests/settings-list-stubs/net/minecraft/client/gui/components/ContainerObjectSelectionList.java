package net.minecraft.client.gui.components;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import java.util.ArrayList;
import java.util.List;

/** Minimal 1.20.1 list geometry shim; scrollbar formula is from its native implementation. */
public abstract class ContainerObjectSelectionList<E extends ContainerObjectSelectionList.Entry<E>> {
    private final List<E> entries = new ArrayList<>();
    protected int width;
    private final int screenHeight;
    private final int top;
    private final int bottom;
    private final int itemHeight;
    protected ContainerObjectSelectionList(Minecraft minecraft, int width, int screenHeight,
                                           int top, int bottom, int itemHeight) {
        this.width = width;
        this.screenHeight = screenHeight;
        this.top = top;
        this.bottom = bottom;
        this.itemHeight = itemHeight;
    }
    public int getRowWidth() { return 220; }
    public int getRowLeft() { return width / 2 - getRowWidth() / 2 + 2; }
    protected int getScrollbarPosition() { return width / 2 + 124; }
    public int testScrollbarPosition() { return getScrollbarPosition(); }
    public double getScrollAmount() { return 0; }
    public int getItemHeight() { return itemHeight; }
    public int getTop() { return top; }
    public int getBottom() { return bottom; }
    public int getScreenHeight() { return screenHeight; }
    protected int addEntry(E entry) { entries.add(entry); return entries.size() - 1; }
    public List<E> children() { return List.copyOf(entries); }
    public void setRenderBackground(boolean value) {}
    public void setRenderTopAndBottom(boolean value) {}
    public void setRenderSelection(boolean value) {}
    public void renderEntry(int index, GuiGraphics graphics) {
        // Native getRowTop includes a 4px inset; renderList reserves another 4px of row pitch.
        entries.get(index).render(graphics, index, top + 4 + index * itemHeight, getRowLeft(), getRowWidth(),
                itemHeight - 4, -1, -1, false, 0);
    }
    public abstract static class Entry<E extends Entry<E>> implements GuiEventListener {
        public abstract List<? extends GuiEventListener> children();
        public abstract List<? extends NarratableEntry> narratables();
        public abstract void render(GuiGraphics graphics, int index, int top, int left, int rowWidth,
                                    int rowHeight, int mouseX, int mouseY, boolean hovered, float partialTick);
    }
}
