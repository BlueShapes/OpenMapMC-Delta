// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.minecraft.client.gui;

public final class Font {
    public final int lineHeight;

    public Font(int lineHeight) { this.lineHeight = lineHeight; }

    public int width(String text) {
        int width = 0;
        for (int i = 0; i < text.length(); i++) width += charWidth(text.charAt(i));
        return width;
    }

    public String plainSubstrByWidth(String text, int maxWidth) {
        int width = 0;
        int end = 0;
        while (end < text.length()) {
            int next = charWidth(text.charAt(end));
            if (width + next > maxWidth) break;
            width += next;
            end++;
        }
        return text.substring(0, end);
    }

    private static int charWidth(char value) {
        return switch (value) {
            case 'i', 'l', '1' -> 2;
            case ' ' -> 4;
            case '…' -> 5;
            case 'W', 'M' -> 8;
            default -> 6;
        };
    }
}
