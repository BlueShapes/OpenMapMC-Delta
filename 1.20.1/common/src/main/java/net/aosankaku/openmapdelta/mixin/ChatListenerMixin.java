// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.mixin;

import com.mojang.authlib.GameProfile;
import net.aosankaku.openmapdelta.client.LocationSharing;
import net.minecraft.client.GuiMessageTag;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.ChatListener;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;
import net.minecraft.network.chat.PlayerChatMessage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.time.Instant;

@Mixin(ChatListener.class)
public abstract class ChatListenerMixin {
    @Shadow
    private void narrateChatMessage(ChatType.Bound type, Component content) {
        throw new AssertionError();
    }

    // Intercept display only after vanilla has accepted the signed player message.
    @Redirect(method = "showMessageToPlayer", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/components/ChatComponent;addMessage"
                    + "(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;"
                    + "Lnet/minecraft/client/GuiMessageTag;)V"), require = 2)
    private void odysseymap$hideProtocol(ChatComponent chat, Component rendered,
                                        MessageSignature signature, GuiMessageTag tag,
                                        ChatType.Bound type, PlayerChatMessage message,
                                        Component decorated, GameProfile profile,
                                        boolean onlySecure, Instant receivedAt) {
        if (!LocationSharing.isProtocolMessage(type, message, profile)) {
            chat.addMessage(rendered, signature, tag);
        }
    }

    @Redirect(method = "showMessageToPlayer", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/multiplayer/chat/ChatListener;narrateChatMessage"
                    + "(Lnet/minecraft/network/chat/ChatType$Bound;Lnet/minecraft/network/chat/Component;)V"),
            require = 2)
    private void odysseymap$hideProtocolNarration(ChatListener listener, ChatType.Bound spokenType,
                                                Component spokenContent, ChatType.Bound type,
                                                PlayerChatMessage message, Component decorated,
                                                GameProfile profile, boolean onlySecure, Instant receivedAt) {
        if (!LocationSharing.isProtocolMessage(type, message, profile)) {
            narrateChatMessage(spokenType, spokenContent);
        }
    }

    @Inject(method = "showMessageToPlayer", at = @At("RETURN"))
    private void odysseymap$receiveNotice(ChatType.Bound type, PlayerChatMessage message,
                                          Component decorated, GameProfile profile,
                                          boolean onlySecure, Instant receivedAt,
                                          CallbackInfoReturnable<Boolean> callback) {
        if (Boolean.TRUE.equals(callback.getReturnValue())) {
            LocationSharing.receive(type, message, profile);
        }
    }
}
