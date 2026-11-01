package net.aosankaku.openmapdelta;

/** Minimal compile-time logger stub for the isolated server tests. */
public final class OpenMapDeltaCommon {
    public static final Logger LOGGER = new Logger();

    private OpenMapDeltaCommon() {}

    public static final class Logger {
        public void warn(String message, Object... arguments) {}
        public void error(String message, Object... arguments) {}
    }
}
