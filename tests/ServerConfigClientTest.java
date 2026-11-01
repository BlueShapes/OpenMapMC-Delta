// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.client;

import net.aosankaku.openmapdelta.server.ServerConfigRequest;
import net.aosankaku.openmapdelta.server.ServerConfigSnapshot;
import net.aosankaku.openmapdelta.server.ServerConfigValues;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.Connection;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

public final class ServerConfigClientTest {
    private static int assertions;
    private ServerConfigClientTest() {}

    public static void main(String[] args) throws Exception {
        Connection first = new Connection();
        Minecraft.getInstance().setConnection(new ClientPacketListener(first));
        List<ServerConfigRequest> sent = new ArrayList<>();
        ServerConfigClient.configureSender(sent::add);
        ServerConfigClient.reset();

        ServerConfigValues initial = values(false, false, "https://map.example/", 20);
        apply(first, 7, initial, ServerConfigSnapshot.Result.NONE);
        check(ServerConfigClient.canManage(), "raw transport Connection token matches the packet listener token");

        ServerConfigValues firstEdit = values(true, false, "https://map.example/", 20);
        ServerConfigClient.submit(firstEdit);
        check(sent.size() == 1 && sent.get(0).revision() == 7, "first edit sends the current expected revision");
        ServerConfigValues coalesced = values(true, true, "https://map.example/", 30);
        ServerConfigClient.submit(coalesced);
        check(sent.size() == 1 && ServerConfigClient.hasPendingWrite(), "later edits coalesce behind the in-flight request");
        check(ServerConfigClient.baseValues().equals(coalesced), "draft fallback uses the newest coalesced values");

        apply(first, 7, initial, ServerConfigSnapshot.Result.RATE_LIMITED);
        check(sent.size() == 1 && ServerConfigClient.hasPendingWrite(), "rate limit retains the newest queued edit");
        setLong("nextRetryNanos", 1L);
        setLong("lastSendNanos", 1L);
        ServerConfigClient.tick();
        check(sent.size() == 2 && sent.get(1).values().equals(coalesced) && sent.get(1).revision() == 7,
                "rate-limited retry resends the newest values against the unchanged revision");
        apply(first, 8, coalesced, ServerConfigSnapshot.Result.SAVED);
        check(!ServerConfigClient.hasPendingWrite() && ServerConfigClient.statusTranslationKey().endsWith("saved"),
                "successful retry clears pending status");

        ServerConfigValues savedAhead = values(false, true, "https://map.example/", 31);
        ServerConfigValues queuedAfterAck = values(true, true, "https://map.example/", 32);
        ServerConfigClient.submit(savedAhead);
        setLong("lastSendNanos", 1L);
        ServerConfigClient.tick();
        check(sent.size() == 3 && sent.get(2).revision() == 8, "a fresh edit starts at the acknowledged revision");
        ServerConfigClient.submit(queuedAfterAck);
        apply(first, 9, savedAhead, ServerConfigSnapshot.Result.SAVED);
        check(ServerConfigClient.hasPendingWrite() && ServerConfigClient.statusTranslationKey().endsWith("pending"),
                "a saved response keeps a newer coalesced edit visibly pending");
        setLong("lastSendNanos", 1L);
        ServerConfigClient.tick();
        check(sent.size() == 4 && sent.get(3).revision() == 9 && sent.get(3).values().equals(queuedAfterAck),
                "the queued edit sends after acknowledgement using the new revision");
        apply(first, 10, queuedAfterAck, ServerConfigSnapshot.Result.SAVED);
        check(!ServerConfigClient.hasPendingWrite(), "the queued acknowledgement completes the edit sequence");

        ServerConfigClient.submit(values(false, true, "https://map.example/", 33));
        setLong("lastSendNanos", 1L);
        ServerConfigClient.tick();
        check(ServerConfigClient.hasPendingWrite(), "administrator revocation setup has a request in flight");
        ServerConfigClient.apply(first, first,
                new ServerConfigSnapshot(10, false, null, ServerConfigSnapshot.Result.NONE));
        check(!ServerConfigClient.canManage() && !ServerConfigClient.hasPendingWrite(),
                "permission revocation clears queued settings and disables management");
        apply(first, 10, queuedAfterAck, ServerConfigSnapshot.Result.NONE);
        check(ServerConfigClient.canManage(), "a later authoritative administrator snapshot restores management");

        ServerConfigValues racedEdit = values(true, true, "https://other.example/", 34);
        ServerConfigClient.submit(racedEdit);
        setLong("lastSendNanos", 1L);
        ServerConfigClient.tick();
        check(sent.size() == 6 && sent.get(5).revision() == 10 && sent.get(5).values().equals(racedEdit),
                "a later edit uses the latest acknowledged revision");
        ServerConfigValues otherAdminValues = values(false, false, "https://map.example/", 25);
        apply(first, 11, otherAdminValues, ServerConfigSnapshot.Result.RATE_LIMITED);
        check(!ServerConfigClient.hasPendingWrite() && ServerConfigClient.statusTranslationKey().endsWith("conflict")
                        && ServerConfigClient.snapshot().values().equals(otherAdminValues),
                "rate limit after a revision change becomes conflict and loads authoritative values");

        ServerConfigClient.beginDraft();
        ServerConfigValues concurrent = values(false, true, "https://new.example/", 40);
        apply(first, 12, concurrent, ServerConfigSnapshot.Result.NONE);
        check(!ServerConfigClient.hasPendingWrite() && ServerConfigClient.statusTranslationKey().endsWith("conflict"),
                "external revision changes conflict with a debounced local draft");

        ServerConfigClient.submit(values(true, true, "https://new.example/", 41));
        setLong("lastSendNanos", 1L);
        ServerConfigClient.tick();
        setLong("inFlightSinceNanos", 0L);
        ServerConfigClient.tick();
        check(!ServerConfigClient.hasPendingWrite() && ServerConfigClient.statusTranslationKey().endsWith("timeout"),
                "timed out writes are discarded with explicit feedback");

        Connection second = new Connection();
        Minecraft.getInstance().setConnection(new ClientPacketListener(second));
        ServerConfigClient.tick();
        check(!ServerConfigClient.canManage() && ServerConfigClient.snapshot() == null,
                "disconnect clears old server settings before a new server session");
        apply(second, 1, initial, ServerConfigSnapshot.Result.NONE);
        check(ServerConfigClient.canManage(), "new connection can load its own server state");
        ServerConfigClient.apply(first, second, new ServerConfigSnapshot(2, true, initial, ServerConfigSnapshot.Result.NONE));
        check(ServerConfigClient.snapshot().revision() == 1, "stale connection packets cannot replace current settings");

        ServerConfigClient.reset();
        Minecraft.getInstance().setConnection(null);
        System.out.println("ServerConfigClientTest passed (" + assertions + " assertions)");
    }

    private static ServerConfigValues values(boolean cave, boolean web, String url, int count) {
        return new ServerConfigValues(cave, web, url, count);
    }

    private static void apply(Connection connection, long revision, ServerConfigValues values,
                              ServerConfigSnapshot.Result result) {
        ServerConfigClient.apply(connection, connection,
                new ServerConfigSnapshot(revision, true, values, result));
    }

    private static void setLong(String name, long value) throws Exception {
        Field field = ServerConfigClient.class.getDeclaredField(name);
        field.setAccessible(true);
        field.setLong(null, value);
    }

    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }
}
