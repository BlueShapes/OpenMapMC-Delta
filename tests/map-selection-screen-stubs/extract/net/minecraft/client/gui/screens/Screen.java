package net.minecraft.client.gui.screens;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Renderable;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public class Screen {
    protected final Component title;
    protected int width = 800, height = 600;
    protected final Font font = new Font();
    protected final Minecraft minecraft = new Minecraft();
    private final List<Renderable> renderables = new ArrayList<>();
    private final List<Button> buttons = new ArrayList<>();

    public Screen(Component title) { this.title = title; }
    protected void init() {}
    public void initForTest() { renderables.clear(); buttons.clear(); init(); }
    public void resizeForTest(int width, int height) { this.width=width; this.height=height; initForTest(); }
    protected void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) { graphics.background(true); }
    protected <T extends Button> T addRenderableWidget(T button) {
        buttons.add(button);
        renderables.add((graphics, mouseX, mouseY, partialTick) -> {
            if (button instanceof EditBox editBox) graphics.editBox(editBox.getValue(), editBox.getX(), editBox.getY(), editBox.getWidth(), editBox.getHeight());
            else graphics.button(button.message(), button.getX(), button.getY(), button.getWidth(), button.getHeight());
        });
        return button;
    }
    protected void addRenderableOnly(Renderable renderable) { renderables.add(renderable); }
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        for (Renderable renderable : List.copyOf(renderables)) renderable.render(graphics, mouseX, mouseY, partialTick);
    }
    public final void extractRenderStateWithTooltipAndSubtitles(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        extractBackground(graphics, mouseX, mouseY, partialTick);
        extractRenderState(graphics, mouseX, mouseY, partialTick);
    }
    public void onClose() {}
    public List<Button> buttonsForTest() { return List.copyOf(buttons); }
    public List<EditBox> editBoxesForTest() { return buttons.stream().filter(EditBox.class::isInstance).map(EditBox.class::cast).toList(); }
    public int renderableCountForTest() { return renderables.size(); }
    public static final class Minecraft { public final Gui gui = new Gui(); public void setScreen(Screen screen) { gui.setScreen(screen); } }
    public static final class Gui {
        public Screen current;
        public final Hud hud = new Hud();
        public void setScreen(Screen screen) { current=screen; }
        public Chat getChat() { return new Chat(); }
    }
    public static final class Hud { public Chat getChat() { return new Chat(); } }
    public static final class Chat { public void addClientSystemMessage(Component message) {} }
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) { return false; }
    public boolean isPauseScreen() { return true; }
}
