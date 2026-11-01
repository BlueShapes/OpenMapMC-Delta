package org.slf4j;

public final class LoggerFactory {
    private LoggerFactory() {}
    public static Logger getLogger(String name) { return new Logger(); }
    public static final class Logger {
        public void warn(String message, Throwable throwable) {}
    }
}
