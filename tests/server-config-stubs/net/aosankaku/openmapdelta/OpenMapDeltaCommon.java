package net.aosankaku.openmapdelta;
import java.util.ArrayList;
import java.util.List;
public final class OpenMapDeltaCommon {
    public static final Log LOGGER = new Log();
    public static final class Log {
        public final List<InfoMessage> infos = new ArrayList<>();
        public Runnable infoObserver;
        public void warn(String message, Object... args) {}
        public void error(String message, Object... args) {}
        public void info(String message, Object... args) {
            infos.add(new InfoMessage(message, args.clone()));
            if (infoObserver != null) infoObserver.run();
        }
        public void clearInfo() { infos.clear(); infoObserver = null; }
    }
    public record InfoMessage(String template, Object[] arguments) {}
}
