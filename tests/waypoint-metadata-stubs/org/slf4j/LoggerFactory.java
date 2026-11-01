package org.slf4j;
public final class LoggerFactory {
    private LoggerFactory() {}
    public static Logger getLogger(String name) {
        return (message, error) -> { System.err.println(message); error.printStackTrace(System.err); };
    }
}
