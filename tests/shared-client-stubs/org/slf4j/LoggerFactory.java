package org.slf4j;

public final class LoggerFactory {
    private static final Logger LOGGER = (message, cause) -> { };
    private LoggerFactory() {}
    public static Logger getLogger(String name) { return LOGGER; }
}
