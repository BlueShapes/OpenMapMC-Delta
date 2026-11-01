// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.compat.players;

import com.mojang.authlib.GameProfile;
import journeymap.common.util.PlayerRadarManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.OnlinePlayer;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.Connection;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.aosankaku.openmapdelta.config.OpenMapDeltaConfig;
import net.aosankaku.openmapdelta.network.ClientServerState;
import net.aosankaku.openmapdelta.network.PlayerPosition;
import net.aosankaku.openmapdelta.platform.DataPaths;
import xaero.common.XaeroMinimapSession;
import xaero.hud.minimap.player.tracker.synced.ClientSyncedTrackedPlayerManager;
import xaero.hud.minimap.player.tracker.synced.SyncedTrackedPlayer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/** Headless integration coverage for the client controller and its source precedence. */
public final class ExternalPlayerClientTest {
    private static final UUID SELF = id("controller-self");
    private static final UUID JOURNEY = id("controller-journey");
    private static final UUID XAERO = id("controller-xaero");
    private static final UUID BLUE = id("controller-blue");
    private static final UUID CURRENT_BLUE = id("controller-current-blue");
    private static final String OVERWORLD = "minecraft:overworld";
    private static final String NETHER = "minecraft:the_nether";

    public static void main(String[] args) throws Exception {
        ExternalPlayerClient.configuration();
        ExternalPlayerClient.configuration().save("old.example", OVERWORLD,
                new BlueMapEndpoint("https://old.example/", "old-map"));
        ExternalPlayerClient.configuration().save("new.example", NETHER,
                new BlueMapEndpoint("https://new.example/", "new-map"));

        loadedSourcesPollAndNativeSnapshotRemainsAuthoritative();
        disabledLogoutLevelAndReconnectClearOldExternalPlayers();
        delayedBlueMapResultsAreRejectedAfterSessionAndConfigChanges();
        ExternalPlayerClient.reset();
        ClientServerState.reset();
        System.out.println("External player client integration tests passed.");
    }

    private static void loadedSourcesPollAndNativeSnapshotRemainsAuthoritative() {
        cleanState();
        Level level = level(OVERWORLD);
        Player self = player(SELF, "SelfPlayer", level, 0, 64, 0);
        Player journey = player(JOURNEY, "JourneyFriend", level, 320, 70, -32);
        PlayerRadarManager.setPlayers(Map.of(JOURNEY, journey));
        XaeroMinimapSession.setCurrentSession(xaeroSession(new SyncedTrackedPlayer(XAERO, 640.5, 72, -4.5,
                ResourceKey.create(OVERWORLD))));
        Minecraft mc = minecraft(level, self, "unconfigured.example", new Object(), List.of(self, journey,
                player(XAERO, "XaeroFriend", level, 0, 64, 0)));

        ExternalPlayerClient.tick(mc);
        List<PlayerPosition> external = ExternalPlayerClient.players();
        equal(2, external.size(), "JourneyMap and Xaero stores are polled without BlueMap configuration");
        check(external.stream().anyMatch(p -> p.id().equals(JOURNEY) && p.x() == 320 && p.z() == -32),
                "JourneyMap current-level player is published");
        check(external.stream().anyMatch(p -> p.id().equals(XAERO) && p.x() == 640 && p.z() == -5),
                "Xaero remote position is floored and published");

        ClientServerState.apply(false, List.of());
        equal(List.of(), ExternalPlayerClient.players(), "an accepted empty native snapshot overrides external sources");
        check(!ClientServerState.isCaveAllowed(), "external player handling preserves native cave=false policy");
        ClientServerState.reset();
    }

    private static void disabledLogoutLevelAndReconnectClearOldExternalPlayers() throws InterruptedException {
        cleanState();
        Level overworld = level(OVERWORLD);
        Player self = player(SELF, "SelfPlayer", overworld, 0, 64, 0);
        Player journey = player(JOURNEY, "JourneyFriend", overworld, 320, 70, -32);
        Minecraft mc = minecraft(overworld, self, "clear.example", new Object(), List.of(self, journey));

        PlayerRadarManager.setPlayers(Map.of(JOURNEY, journey));
        ExternalPlayerClient.tick(mc);
        equal(1, ExternalPlayerClient.players().size(), "initial session has an external player");

        OpenMapDeltaConfig.EXTERNAL_PLAYERS_ENABLED.set(false);
        ExternalPlayerClient.tick(mc);
        OpenMapDeltaConfig.EXTERNAL_PLAYERS_ENABLED.set(true);
        equal(List.of(), ExternalPlayerClient.players(), "disabling clears prior source data");

        PlayerRadarManager.setPlayers(Map.of(JOURNEY, journey));
        ExternalPlayerClient.tick(mc);
        equal(1, ExternalPlayerClient.players().size(), "re-enabled session can poll again");
        mc.level = null;
        ExternalPlayerClient.tick(mc);
        mc.level = overworld;
        equal(List.of(), ExternalPlayerClient.players(), "logout clears the previous session");

        PlayerRadarManager.setPlayers(Map.of(JOURNEY, journey));
        ExternalPlayerClient.tick(mc);
        equal(1, ExternalPlayerClient.players().size(), "current session can repopulate");
        PlayerRadarManager.setPlayers(Map.of());
        Level nether = level(NETHER);
        Player netherSelf = player(SELF, "SelfPlayer", nether, 0, 64, 0);
        Player netherJourney = player(JOURNEY, "JourneyFriend", nether, 128, 70, 128);
        mc.level = nether;
        mc.player = netherSelf;
        mc.getConnection().getOnlinePlayers().clear();
        mc.getConnection().getOnlinePlayers().add(new OnlinePlayer(netherSelf.getGameProfile()));
        mc.getConnection().getOnlinePlayers().add(new OnlinePlayer(netherJourney.getGameProfile()));
        ExternalPlayerClient.tick(mc);
        equal(List.of(), ExternalPlayerClient.players(), "level identity change clears previous positions");

        PlayerRadarManager.setPlayers(Map.of(JOURNEY, netherJourney));
        awaitInstalledPlayer(mc, JOURNEY);
        equal(1, ExternalPlayerClient.players().size(), "new level can repopulate from current map API");
        PlayerRadarManager.setPlayers(Map.of());
        mc.getConnection().setConnection(new Connection());
        ExternalPlayerClient.tick(mc);
        equal(List.of(), ExternalPlayerClient.players(), "reconnect identity change clears previous positions");
    }

    private static void delayedBlueMapResultsAreRejectedAfterSessionAndConfigChanges() throws Exception {
        cleanState();
        ExternalPlayerClient.configuration().save("old.example", OVERWORLD,
                new BlueMapEndpoint("https://old.example/", "old-map"));
        ExternalPlayerClient.configuration().save("new.example", NETHER,
                new BlueMapEndpoint("https://new.example/", "new-map"));
        BlueMapHttp.Plan oldPlan = BlueMapHttp.hold(List.of(position(BLUE, "BlueFriend", OVERWORLD, 900, 70, 900)));
        BlueMapHttp.Plan nextPlan = BlueMapHttp.immediate(List.of(
                position(CURRENT_BLUE, "NewBlueFriend", NETHER, 1200, 70, -1200)));
        BlueMapHttp.enqueue(oldPlan);
        BlueMapHttp.enqueue(nextPlan);

        Level oldLevel = level(OVERWORLD);
        Player self = player(SELF, "SelfPlayer", oldLevel, 0, 64, 0);
        Minecraft oldMc = minecraft(oldLevel, self, "old.example", new Object(), List.of(self));
        ExternalPlayerClient.tick(oldMc);
        check(oldPlan.awaitStarted(), "old BlueMap request starts on worker");

        Level newLevel = level(NETHER);
        Player newSelf = player(SELF, "SelfPlayer", newLevel, 0, 64, 0);
        Minecraft newMc = minecraft(newLevel, newSelf, "new.example", new Object(), List.of(newSelf));
        ExternalPlayerClient.tick(newMc);
        check(nextPlan.awaitStarted(), "new configuration/session request starts independently");

        oldPlan.release();
        check(oldPlan.awaitFinished(), "held old response completes after session/config replacement");
        nextPlan.release();
        awaitBlueMapPublication(newMc, CURRENT_BLUE);
        check(ExternalPlayerClient.players().stream().anyMatch(p -> p.id().equals(CURRENT_BLUE)
                        && p.dimension().equals(NETHER)),
                "a successful BlueMap response for the current configuration is published");
        check(ExternalPlayerClient.players().stream().noneMatch(p -> p.id().equals(BLUE)),
                "late BlueMap data from the prior session/config never enters current state");
    }

    private static void awaitInstalledPlayer(Minecraft mc, UUID id) throws InterruptedException {
        long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (System.nanoTime() < until) {
            ExternalPlayerClient.tick(mc);
            if (ExternalPlayerClient.players().stream().anyMatch(player -> player.id().equals(id))) return;
            Thread.sleep(10);
        }
        throw new AssertionError("installed player store was not polled again");
    }

    private static void awaitBlueMapPublication(Minecraft mc, UUID expectedPlayer) throws InterruptedException {
        long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (System.nanoTime() < until) {
            ExternalPlayerClient.tick(mc);
            if (ExternalPlayerClient.players().stream().anyMatch(player -> player.id().equals(expectedPlayer))) return;
            Thread.sleep(10);
        }
        throw new AssertionError("current BlueMap response was not published in time");
    }

    private static void cleanState() {
        ExternalPlayerClient.reset();
        ClientServerState.reset();
        OpenMapDeltaConfig.MAP_MOD_ENABLED.set(true);
        OpenMapDeltaConfig.EXTERNAL_PLAYERS_ENABLED.set(true);
        PlayerRadarManager.setPlayers(Map.of());
        XaeroMinimapSession.setCurrentSession(null);
        BlueMapHttp.clearPlans();
    }

    private static XaeroMinimapSession xaeroSession(SyncedTrackedPlayer... players) {
        ClientSyncedTrackedPlayerManager manager = new ClientSyncedTrackedPlayerManager();
        manager.setPlayers(List.of(players));
        return new XaeroMinimapSession(manager);
    }

    private static Minecraft minecraft(Level level, Player player, String server, Object session,
                                       List<Player> online) {
        Minecraft mc = new Minecraft();
        mc.level = level;
        mc.player = player;
        mc.setCurrentServer(new ServerData(server));
        mc.setConnection(new ClientPacketListener(new Connection(session), online.stream()
                .map(value -> new OnlinePlayer(value.getGameProfile())).toList()));
        return mc;
    }

    private static Level level(String dimension) { return new Level(ResourceKey.create(dimension)); }
    private static Player player(UUID id, String name, Level level, double x, double y, double z) {
        return new Player(id, name, level, x, y, z);
    }
    private static PlayerPosition position(UUID id, String name, String dimension, int x, int y, int z) {
        return new PlayerPosition(id, name, dimension, x, y, z);
    }
    private static UUID id(String value) { return UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8)); }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
    private static void equal(Object expected, Object actual, String message) {
        if (!expected.equals(actual)) throw new AssertionError(message + ": expected " + expected + ", got " + actual);
    }
}
