package net.aosankaku.openmapdelta.platform;
import java.nio.file.Path;
public final class DataPaths {
    public static Path directory;
    public static Path configDirectory() { return directory; }
    public static void ensureMigrated() {}
}
