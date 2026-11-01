package net.minecraft.client.gui;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
public class GuiGraphicsExtractor {
    public final List<String> events;
    public final GuiRenderState state;
    public final int mouseX, mouseY;
    public GuiGraphicsExtractor() { this(null, new GuiRenderState(), 0, 0); }
    public GuiGraphicsExtractor(Minecraft minecraft, GuiRenderState state, int mouseX, int mouseY) {
        this.state = state;
        this.events = state.events;
        this.mouseX = mouseX;
        this.mouseY = mouseY;
    }
    public void nextStratum() { state.stratum++; }
    public void record(String label) { events.add(label + "@" + state.stratum); }
}
