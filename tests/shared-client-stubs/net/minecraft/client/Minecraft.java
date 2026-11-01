package net.minecraft.client;

import net.minecraft.network.chat.Component;

import java.util.UUID;

public final class Minecraft {
    private static final Minecraft INSTANCE = new Minecraft();
    public FakePlayer player;
    public final FakeGui gui = new FakeGui();
    private Minecraft() {}
    public static Minecraft getInstance() { return INSTANCE; }

    public static final class FakePlayer {
        private final UUID id;
        private Component lastMessage;
        public FakePlayer(UUID id) { this.id = id; }
        public UUID getUUID() { return id; }
        public void displayClientMessage(Component message, boolean actionBar) { lastMessage = message; }
        public Component lastMessage() { return lastMessage; }
    }

    public static final class FakeGui {
        private final FakeChat chat = new FakeChat();
        public final FakeHud hud = new FakeHud(chat);
        public FakeChat getChat() { return chat; }
    }

    public static final class FakeHud {
        private final FakeChat chat;
        private FakeHud(FakeChat chat) { this.chat = chat; }
        public FakeChat getChat() { return chat; }
    }

    public static final class FakeChat {
        private Component lastMessage;
        public void addClientSystemMessage(Component message) { lastMessage = message; }
        public Component lastMessage() { return lastMessage; }
    }
}
