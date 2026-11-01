// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.web;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import java.net.URI;

/** Ordinary clickable chat components, including English fallback for unmodded clients. */
public final class WebMapChat {
    private WebMapChat() {}

    public static Component local(URI uri) {
        return message("web.odysseymap.local_available", "Local web map: %s", uri);
    }

    public static Component shared(URI uri) {
        return message("web.odysseymap.server_available", "Server web map: %s", uri);
    }

    private static Component message(String key, String fallback, URI uri) {
        Component link = Component.literal(uri.toASCIIString()).withStyle(style -> style
                .withColor(ChatFormatting.AQUA).withUnderlined(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, uri.toASCIIString())));
        return Component.translatableWithFallback(key, fallback, link);
    }
}
