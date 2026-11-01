// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.gui;

import net.aosankaku.openmapdelta.marker.Marker;
import net.aosankaku.openmapdelta.marker.MarkerStorage;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class MarkerEditPopup {
    private MarkerEditPopup() {}

    public static void applyLabel(Marker marker, String newLabel) {
        marker.setLabel(newLabel);
        MarkerStorage.save();
    }
}
