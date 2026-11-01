// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.client;

import net.aosankaku.openmapdelta.compat.players.ExternalPlayerClient;

import com.mojang.authlib.GameProfile;
import net.aosankaku.openmapdelta.gui.FullscreenMapScreen;
import net.aosankaku.openmapdelta.marker.MarkerManager;
import net.aosankaku.openmapdelta.network.ClientServerState;
import net.aosankaku.openmapdelta.network.PlayerPosition;
import net.aosankaku.openmapdelta.util.DimensionUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.PlayerChatMessage;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.player.Player;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Client-only location delivery through vanilla chat with receipts and public emote fallback. */
public final class LocationSharing {
    private static final long DEDUPLICATE_MS = LocationNotice.MAX_DURATION_SECONDS * 1_000L + 60_000L;
    private static final LocationNoticeDeliveryQueue OUTBOX = new LocationNoticeDeliveryQueue();
    private static final Map<Receipt, Long> RECEIVED = new LinkedHashMap<>();
    private static final Map<UUID, String> RECIPIENT_NAMES = new LinkedHashMap<>();
    private record Receipt(UUID sender, UUID notice) {}

    private LocationSharing() {}

    private static String availableCommand(Minecraft mc, String... candidates) {
        if (mc == null || mc.player == null || mc.level == null || mc.getConnection() == null) return null;
        // The server supplies a command tree filtered by the player's permissions.
        for (String candidate : candidates) {
            if (mc.getConnection().getCommands().getRoot().getChild(candidate) != null) return candidate;
        }
        return null;
    }

    public static boolean canTeleport(Minecraft mc) {
        return availableCommand(mc, "tp", "teleport") != null;
    }

    public static boolean canShare(Minecraft mc) {
        return mc != null && mc.player != null && mc.level != null && mc.getConnection() != null;
    }

    public static boolean canMessage(Minecraft mc) {
        return availableCommand(mc, "w", "msg", "tell") != null;
    }

    /** Resolve the UUID again at send time; names stored in markers are not command targets. */
    public static String onlinePlayerName(Minecraft mc, UUID playerId) {
        if (mc == null || mc.player == null || mc.level == null || mc.getConnection() == null
                || playerId == null) return null;
        var info = mc.getConnection().getPlayerInfo(playerId);
        String name = info == null ? null : info.getProfile().name();
        return LocationNotice.validPlayerName(name) ? name : null;
    }

    public static boolean teleportToPlayer(Minecraft mc, UUID playerId) {
        String command = availableCommand(mc, "tp", "teleport");
        var payload = PlayerCommandPayload.teleport(command, onlinePlayerName(mc, playerId));
        if (payload.isEmpty()) return false;
        // This is a native server command, including its current permission and dimension checks.
        mc.getConnection().sendCommand(payload.get());
        return true;
    }

    public static boolean sendPrivateMessage(Minecraft mc, UUID playerId, String message) {
        String command = availableCommand(mc, "w", "msg", "tell");
        var payload = PlayerCommandPayload.privateMessage(command, onlinePlayerName(mc, playerId), message);
        if (payload.isEmpty()) return false;
        mc.getConnection().sendCommand(payload.get());
        return true;
    }

    /** Nearby tracked entities are fresher than the periodic optional-server snapshot. */
    public static Optional<PlayerPosition> knownPlayerPosition(Minecraft mc, UUID playerId) {
        if (mc == null || mc.player == null || mc.level == null || mc.getConnection() == null
                || playerId == null) return Optional.empty();
        if (playerId.equals(mc.player.getUUID())) return position(mc.player, mc.level.dimension());
        for (Player player : mc.level.players()) {
            if (playerId.equals(player.getUUID())) return position(player, mc.level.dimension());
        }
        return ExternalPlayerClient.players().stream().filter(player -> player.id().equals(playerId)).findFirst();
    }

    private static Optional<PlayerPosition> position(Player player, ResourceKey<Level> dimension) {
        if (!Double.isFinite(player.getX()) || !Double.isFinite(player.getY()) || !Double.isFinite(player.getZ())) {
            return Optional.empty();
        }
        try {
            return Optional.of(new PlayerPosition(player.getUUID(), player.getGameProfile().name(),
                    DimensionUtil.dimensionId(dimension), player.getBlockX(), player.getBlockY(), player.getBlockZ()));
        } catch (IllegalArgumentException invalid) {
            return Optional.empty();
        }
    }

    public static boolean teleport(Minecraft mc, int x, int y, int z) {
        String command = availableCommand(mc, "tp", "teleport");
        if (command == null || !validNoticePosition(mc, mc.level.dimension(), x, y, z)
                || mc.level.isOutsideBuildHeight(y)) return false;
        // The server rechecks permission on execution; never move the player locally.
        mc.getConnection().sendCommand(command + " " + (x + 0.5) + " " + y + " " + (z + 0.5));
        return true;
    }

    public static int share(Minecraft mc, ResourceKey<Level> dimension, int x, int y, int z,
                            Collection<UUID> recipients) {
        return share(mc, dimension, x, y, z, LocationNotice.DEFAULT_DURATION_SECONDS, recipients);
    }

    public static int share(Minecraft mc, ResourceKey<Level> dimension, int x, int y, int z,
                            int durationSeconds, Collection<UUID> recipients) {
        return share(mc, dimension, x, y, z, durationSeconds, recipients, null);
    }

    public static int share(Minecraft mc, ResourceKey<Level> dimension, int x, int y, int z,
                            int durationSeconds, Collection<UUID> recipients, UUID trackedPlayerId) {
        return share(mc, dimension, x, y, z, durationSeconds, recipients, trackedPlayerId, "");
    }

    public static int share(Minecraft mc, ResourceKey<Level> dimension, int x, int y, int z,
                            int durationSeconds, Collection<UUID> recipients, UUID trackedPlayerId, String message) {
        if (!canShare(mc) || recipients == null) return 0;
        var latest = knownPlayerPosition(mc, trackedPlayerId);
        if (latest.isPresent()) {
            PlayerPosition player = latest.get();
            dimension = dimension(player.dimension());
            x = player.x();
            y = player.y();
            z = player.z();
        }
        LocationNotice notice;
        try {
            notice = new LocationNotice(UUID.randomUUID(), DimensionUtil.dimensionId(dimension),
                    x, y, z, durationSeconds, trackedPlayerId, message);
        } catch (IllegalArgumentException invalidDuration) {
            return 0;
        }
        return share(mc, notice, recipients);
    }

    /** Queue an already-previewed notice so the sent coordinates and message budget cannot drift. */
    public static int share(Minecraft mc, LocationNotice notice, Collection<UUID> recipients) {
        if (!canShare(mc) || recipients == null || notice == null) return 0;
        ResourceKey<Level> noticeDimension = dimension(notice);
        if (!validNoticePosition(mc, noticeDimension, notice.x(), notice.y(), notice.z())) return 0;
        // Leave room for the command and a maximum-length player name (256 character chat limit).
        if (LocationNotice.decode(notice.encode()).filter(notice::equals).isEmpty()) return 0;
        // Always notify the sender, even if an individual remote delivery cannot be queued.
        show(mc, notice, mc.player.getGameProfile().name());
        int count = 1;
        for (UUID recipient : new LinkedHashSet<>(recipients)) {
            if (recipient == null || recipient.equals(mc.player.getUUID())) continue;
            String name = onlinePlayerName(mc, recipient);
            if (name == null) {
                deliveryFailed(mc, recipient);
                continue;
            }
            if (RECIPIENT_NAMES.size() >= 4_096 && !RECIPIENT_NAMES.containsKey(recipient)) {
                RECIPIENT_NAMES.remove(RECIPIENT_NAMES.keySet().iterator().next());
            }
            RECIPIENT_NAMES.put(recipient, name);
            if (OUTBOX.enqueue(recipient, notice)) count++;
            else deliveryFailed(mc, recipient);
        }
        if (count > 1) displayChat(mc,
                Component.translatable("odysseymap.notify.queued", count).withStyle(ChatFormatting.YELLOW));
        return count;
    }

    public static void observeServerTime(long tick) {
        OUTBOX.observeServerTime(tick);
    }

    public static void tick(Minecraft mc) {
        if (!canShare(mc)) return;
        long now = MarkerManager.monotonicMillis();
        RECEIVED.values().removeIf(received -> now - received >= DEDUPLICATE_MS);
        OUTBOX.retainSupportingPeers(mc.getConnection().getOnlinePlayers().stream()
                .map(info -> info.getProfile().id()).collect(java.util.stream.Collectors.toSet()));
        OUTBOX.update(now);
        reportResults(mc);
        // Invalid recipients or unavailable commands must not abort the remaining deliveries.
        for (int attempts = 0; attempts < 32; attempts++) {
            LocationNoticeDeliveryQueue.Action action = OUTBOX.takeReady(now);
            if (action == null) break;
            String name = onlinePlayerName(mc, action.recipient());
            LocationNotice notice = action.notice();
            if (name == null || notice != null
                    && !validNoticePosition(mc, dimension(notice), notice.x(), notice.y(), notice.z())) {
                OUTBOX.fail(action);
                continue;
            }
            Optional<String> payload = switch (action.kind()) {
                case ACK -> PlayerCommandPayload.privateMessage(availableCommand(mc, "msg", "tell", "w"),
                        name, LocationNoticeProtocol.ack(action.noticeId()));
                case WHISPER -> PlayerCommandPayload.privateMessage(availableCommand(mc, "msg", "tell", "w"),
                        name, notice.encode());
                case EMOTE -> availableCommand(mc, "me") == null
                        ? Optional.empty() : PlayerCommandPayload.emote(name, notice);
            };
            if (payload.isEmpty()) {
                OUTBOX.rejected(action);
                continue;
            }
            try {
                mc.getConnection().sendCommand(payload.get());
                OUTBOX.sent(action, now, action.kind() == LocationNoticeDeliveryQueue.Kind.EMOTE
                        ? payload.get().substring(3) : null);
                break;
            } catch (RuntimeException sendFailed) {
                OUTBOX.rejected(action);
            }
        }
        reportResults(mc);
    }

    private static String recipientName(Minecraft mc, UUID recipient) {
        String online = onlinePlayerName(mc, recipient);
        return online != null ? online : RECIPIENT_NAMES.getOrDefault(recipient, recipient.toString());
    }

    private static void deliveryFailed(Minecraft mc, UUID recipient) {
        displayChat(mc, Component.translatable("odysseymap.notify.recipient_failed",
                recipientName(mc, recipient)).withStyle(ChatFormatting.RED));
    }

    private static void reportResults(Minecraft mc) {
        for (LocationNoticeDeliveryQueue.Result result : OUTBOX.drainResults()) {
            if (!result.success()) {
                deliveryFailed(mc, result.recipient());
                continue;
            }
            LocationNotice notice = result.notice();
            Component message = notice.message().isEmpty()
                    ? Component.translatable("odysseymap.notify.sent_to",
                            recipientName(mc, result.recipient()), notice.dimension(), notice.x(), notice.y(), notice.z())
                    : Component.translatable("odysseymap.notify.sent_to_message", recipientName(mc, result.recipient()),
                            notice.message(), notice.dimension(), notice.x(), notice.y(), notice.z());
            displayChat(mc, copyableCoordinates(message, notice.x(), notice.y(), notice.z()));
        }
    }

    /** Called only after vanilla's secure-chat, block, and filter checks accept a player message. */
    public static void receive(ChatType.Bound type, PlayerChatMessage message, GameProfile profile) {
        Minecraft mc = Minecraft.getInstance();
        if (!acceptedPlayerMessage(mc, message, profile)) return;
        String body = message.signedContent();
        if (isType(mc, type, ChatType.EMOTE_COMMAND) && message.sender().equals(mc.player.getUUID())) {
            OUTBOX.confirmEmote(body);
            return;
        }
        if (isType(mc, type, ChatType.MSG_COMMAND_INCOMING)) {
            Optional<UUID> receipt = LocationNoticeProtocol.decodeAck(body);
            if (receipt.isPresent()) {
                OUTBOX.acknowledge(message.sender(), receipt.get());
                return;
            }
            LocationNotice.decode(body).ifPresent(notice -> {
                if (receiveNotice(mc, notice, message.sender(), profile.name())) {
                    OUTBOX.queueAcknowledgement(message.sender(), notice.id());
                }
            });
        } else if (isType(mc, type, ChatType.EMOTE_COMMAND)) {
            LocationNoticeProtocol.decodeEmote(body)
                    .filter(emote -> emote.recipientName().equals(mc.player.getGameProfile().name()))
                    .ifPresent(emote -> receiveNotice(mc, emote.notice(), message.sender(), profile.name()));
        }
    }

    /** Pure predicate used only at vanilla's accepted-message display/narration call sites. */
    public static boolean isProtocolMessage(ChatType.Bound type, PlayerChatMessage message, GameProfile profile) {
        Minecraft mc = Minecraft.getInstance();
        if (!acceptedPlayerMessage(mc, message, profile)) return false;
        String body = message.signedContent();
        boolean own = message.sender().equals(mc.player.getUUID());
        if (isType(mc, type, ChatType.MSG_COMMAND_INCOMING)
                || own && isType(mc, type, ChatType.MSG_COMMAND_OUTGOING)) {
            if (LocationNoticeProtocol.decodeAck(body).isPresent()) return true;
            return LocationNotice.decode(body).filter(notice ->
                    validNoticePosition(mc, dimension(notice), notice.x(), notice.y(), notice.z())).isPresent();
        }
        if (!isType(mc, type, ChatType.EMOTE_COMMAND)) return false;
        return LocationNoticeProtocol.decodeEmote(body)
                .filter(emote -> own || emote.recipientName().equals(mc.player.getGameProfile().name()))
                .filter(emote -> validNoticePosition(mc, dimension(emote.notice()), emote.notice().x(),
                        emote.notice().y(), emote.notice().z())).isPresent();
    }

    private static boolean acceptedPlayerMessage(Minecraft mc, PlayerChatMessage message, GameProfile profile) {
        return canShare(mc) && message.filterMask().isEmpty() && message.sender().equals(profile.id())
                && LocationNotice.validPlayerName(profile.name())
                && profile.name().equals(onlinePlayerName(mc, message.sender()));
    }

    private static boolean isType(Minecraft mc, ChatType.Bound type, ResourceKey<ChatType> expected) {
        return type.chatType().is(expected);
    }

    private static boolean receiveNotice(Minecraft mc, LocationNotice notice, UUID senderId, String senderName) {
        if (!validNoticePosition(mc, dimension(notice), notice.x(), notice.y(), notice.z())) return false;
        Receipt receipt = new Receipt(senderId, notice.id());
        if (RECEIVED.containsKey(receipt)) return true;
        if (RECEIVED.size() >= 1_024) RECEIVED.remove(RECEIVED.keySet().iterator().next());
        RECEIVED.put(receipt, MarkerManager.monotonicMillis());
        show(mc, notice, senderName);
        return true;
    }

    private static ResourceKey<Level> dimension(LocationNotice notice) {
        return dimension(notice.dimension());
    }

    private static ResourceKey<Level> dimension(String id) {
        var identifier = Identifier.tryParse(id);
        return identifier == null ? null : ResourceKey.create(Registries.DIMENSION, identifier);
    }

    private static boolean validNoticePosition(Minecraft mc, ResourceKey<Level> dimension, int x, int y, int z) {
        if (mc.level == null || mc.getConnection() == null || dimension == null
                || !mc.getConnection().levels().contains(dimension)) return false;
        return LocationNotice.validCoordinates(x, y, z);
    }

    private static void addNoticeMarker(LocationNotice notice, String sender) {
        MarkerManager.get().addLocationNotice(dimension(notice), notice.x(), notice.y(), notice.z(),
                sender, notice.durationSeconds(), notice.trackedPlayerId());
    }

    private static void show(Minecraft mc, LocationNotice notice, String sender) {
        ResourceKey<Level> dimension = dimension(notice);
        addNoticeMarker(notice, sender);
        Component message = notice.message().isEmpty()
                ? Component.translatable("odysseymap.notify.received",
                        sender, notice.dimension(), notice.x(), notice.y(), notice.z(), notice.durationSeconds())
                : Component.translatable("odysseymap.notify.received_message", sender, notice.message(),
                        notice.dimension(), notice.x(), notice.y(), notice.z(), notice.durationSeconds());
        displayChat(mc, copyableCoordinates(message, notice.x(), notice.y(), notice.z()));
        if (mc.gui.screen() instanceof FullscreenMapScreen screen) {
            screen.focusLocation(dimension, notice.x(), notice.z());
        }
    }

    private static void displayChat(Minecraft mc, Component message) {
        mc.gui.hud.getChat().addClientSystemMessage(message);
    }

    private static Component copyableCoordinates(Component message, int x, int y, int z) {
        String coordinates = x + " " + y + " " + z;
        return message.copy().withStyle(style -> style.withColor(ChatFormatting.YELLOW)
                .withClickEvent(new ClickEvent.CopyToClipboard(coordinates))
                .withHoverEvent(new HoverEvent.ShowText(Component.translatable("odysseymap.notify.copy_coordinates"))));
    }

    public static void reset() {
        OUTBOX.clear();
        RECEIVED.clear();
        RECIPIENT_NAMES.clear();
        LocationContextClient.reset();
    }
}
