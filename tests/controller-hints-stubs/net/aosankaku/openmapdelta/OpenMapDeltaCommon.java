package net.aosankaku.openmapdelta;
public final class OpenMapDeltaCommon {
    public static final Logger LOGGER=new Logger();
    public static final class Logger {
        public int warnings;
        public void warn(String text,Throwable error) { warnings++; }
    }
}
