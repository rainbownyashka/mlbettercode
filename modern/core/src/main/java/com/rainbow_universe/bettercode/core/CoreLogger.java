package com.rainbow_universe.bettercode.core;

public interface CoreLogger {
    void info(String tag, String message);
    void warn(String tag, String message);
    void error(String tag, String message);
}
