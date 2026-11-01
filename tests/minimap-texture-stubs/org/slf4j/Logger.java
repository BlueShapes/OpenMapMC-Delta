package org.slf4j;

public interface Logger {
    default void warn(String message, Throwable error) {}
}
