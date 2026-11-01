package net.aosankaku.openmapdelta.network;

import java.util.Optional;

public final class ClientWorldIdentityState {
    private ClientWorldIdentityState() {}
    public static Optional<String> get(Object connection) { return Optional.empty(); }
    public static boolean requiresIdentity(Object connection) { return false; }
}
