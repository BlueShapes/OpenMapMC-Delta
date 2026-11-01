package net.aosankaku.openmapdelta.network;

public final class ClientServerState {
    public static boolean allowed = true;
    private ClientServerState() {}
    public static boolean isCaveAllowed() { return allowed; }
}
