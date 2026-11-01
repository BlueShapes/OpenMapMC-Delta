package net.aosankaku.openmapdelta.area;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

/** Private data must stay on this client and must never follow a stale editor into another world. */
public final class AreaWorkspaceTest {
    private static int checks;

    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory(Path.of(args[0]), "workspace-");
        AreaWorkspace workspace = new AreaWorkspace(root);
        Object firstConnection = new Object();
        workspace.open(firstConnection, null);
        check(!workspace.available(), "identity-pending sessions cannot persist private areas");
        try (var entries = Files.list(root)) {
            check(entries.findAny().isEmpty(), "pending identity does not create a guessed world directory");
        }
        workspace.open(firstConnection, "world:alpha");
        check(workspace.available(), "known identity enables local persistence");
        AreaDocument alphaBase = workspace.document();
        MapArea privateArea = area(MapArea.Scope.PERSONAL, "青葉川");
        check(workspace.save(alphaBase, alphaBase.upsert(privateArea)) == AreaWorkspace.Result.SAVED,
                "personal area saves to this client");
        check(workspace.document().revision() == 1, "local save advances revision once");
        Path alphaFile = workspace.path();
        byte[] alphaBytes = Files.readAllBytes(alphaFile);
        check(workspace.save(alphaBase, alphaBase.upsert(privateArea.withName("stale"))) == AreaWorkspace.Result.CONFLICT,
                "old editor revisions cannot overwrite a newer local save");
        check(java.util.Arrays.equals(alphaBytes, Files.readAllBytes(alphaFile)), "rejected stale edit preserves bytes");
        AreaDocument current = workspace.document();
        check(workspace.save(current, current.upsert(area(MapArea.Scope.SHARED, "公開禁止"))) == AreaWorkspace.Result.INVALID,
                "shared data cannot enter private persistence");
        check(java.util.Arrays.equals(alphaBytes, Files.readAllBytes(alphaFile)), "invalid scope preserves private data");

        workspace.open(firstConnection, "world:beta");
        check(workspace.document().areas().isEmpty(), "another world has an independent private collection");
        AreaDocument emptyBeta = workspace.document();
        Object replacementConnection = new Object();
        workspace.open(replacementConnection, "world:beta");
        check(workspace.save(emptyBeta, emptyBeta.upsert(privateArea)) == AreaWorkspace.Result.CONFLICT,
                "an editor from the previous connection cannot write even when empty revisions match");
        workspace.open(replacementConnection, "world:alpha");
        if (!workspace.available()) throw new AssertionError("private data must reload after reconnect", workspace.failure());
        check(workspace.document().areas().get(0).name().equals("青葉川"), "returning to a world restores its private data");
        check(workspace.path().equals(alphaFile), "private data uses stable world identity independent of connection");
        check(!alphaFile.toString().contains("world:alpha"), "identity is hashed into a safe path component");

        workspace.reset();
        check(!workspace.available() && workspace.document().areas().isEmpty(), "disconnect clears visible private state");
        check(java.util.Arrays.equals(alphaBytes, Files.readAllBytes(alphaFile)), "disconnect preserves saved private data");
        Files.writeString(alphaFile, "{\"version\":999,\"identity\":\"world:alpha\"}");
        byte[] future = Files.readAllBytes(alphaFile);
        workspace.open(new Object(), "world:alpha");
        check(!workspace.available(), "future or corrupt store disables writes");
        check(workspace.save(workspace.document(), new AreaDocument(0, List.of(privateArea))) == AreaWorkspace.Result.UNAVAILABLE,
                "unreadable store cannot be replaced with empty defaults");
        check(java.util.Arrays.equals(future, Files.readAllBytes(alphaFile)), "future store is left untouched");
        System.out.println("PASS AreaWorkspaceTest: " + checks + " checks");
    }

    private static MapArea area(MapArea.Scope scope, String name) {
        return new MapArea(UUID.randomUUID(), "minecraft:overworld", name, MapArea.Kind.RIVER, scope,
                UUID.randomUUID(), "Player", null, 0,
                MapArea.Geometry.line(List.of(List.of(new MapArea.Point(0, 0), new MapArea.Point(12, 4)))),
                new MapArea.Point(6, 2), MapArea.Style.defaults(0x4D9ED4));
    }

    private static void check(boolean condition, String description) {
        checks++;
        if (!condition) throw new AssertionError(description);
    }
}
