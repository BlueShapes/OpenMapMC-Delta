package net.aosankaku.openmapdelta.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;

public final class SettingsListTest {
    public static void main(String[] args) {
        verifyResponsiveRows(320);
        verifyResponsiveRows(640);
        verifyColorFieldAndSwatchAtMinimumWidth();
        System.out.println("SettingsList geometry and accessibility checks passed.");
    }

    private static void verifyResponsiveRows(int width) {
        SettingsList list = newList(width);
        list.addHeading(Component.translatable("gui.odysseymap.tab.minimap"));
        AbstractWidget button = new AbstractWidget(0, 0, Math.max(1, list.getRowWidth() - 8), 20,
                Component.literal("setting"));
        list.addWidget(button, 0, false);

        SettingsList.Entry heading = list.children().get(0);
        check(heading.children().isEmpty(), "category heading must not receive focus");
        check(heading.narratables().isEmpty(), "category heading must not be narrated as a control");

        SettingsList.Entry setting = list.children().get(1);
        check(setting.children().size() == 1 && setting.children().get(0) == button,
                "setting entry must expose its widget to native input handling");
        check(setting.narratables().size() == 1 && setting.narratables().get(0) == button,
                "setting entry must expose its widget for narration");

        list.renderEntry(1, new GuiGraphics());
        check(button.getHeight() == 20, "standard widget height must remain 20 pixels");
        int rowTop = list.getTop() + 4 + list.getItemHeight();
        check(button.getY() >= rowTop && button.getY() + button.getHeight() <= rowTop + list.getItemHeight() - 4,
                "standard widget must fit within the native row content height");
        check(button.getWidth() == list.getRowWidth(), "setting button should fill the responsive row");
        check(button.getWidth() >= 280 && button.getWidth() <= 400,
                "setting buttons must use a readable single column within the maximum width");
        check(button.getX() + button.getWidth() < list.testScrollbarPosition(),
                "scrollbar must not overlap the setting button at GUI width " + width);
        check(list.testScrollbarPosition() + 6 <= width,
                "scrollbar must remain within the viewport at GUI width " + width);
    }

    private static void verifyColorFieldAndSwatchAtMinimumWidth() {
        SettingsList list = newList(320);
        list.addHeading(Component.translatable("gui.odysseymap.setting.border_color"));
        AbstractWidget field = new AbstractWidget(0, 0, 220, 18, Component.literal("border color"));
        list.addWidget(field, 220, true);
        GuiGraphics graphics = new GuiGraphics();
        list.renderEntry(1, graphics);

        int swatchColor = 0xFF336699;
        GuiGraphics.Fill swatch = graphics.fills.stream().filter(fill -> fill.color() == swatchColor)
                .findFirst().orElseThrow(() -> new AssertionError("border color swatch was not rendered"));
        check(swatch.left() >= field.getX() + field.getWidth() + 4, "swatch must follow the field with a gap");
        check(swatch.right() < list.testScrollbarPosition(), "swatch must not collide with the scrollbar");
        check(swatch.right() <= 320, "swatch must remain inside the minimum-width viewport");
    }

    private static SettingsList newList(int width) {
        return new SettingsList(new Minecraft(), new Font(), width, 240, 32, 202);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
