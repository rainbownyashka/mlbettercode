package com.rainbow_universe.bettercode.core.settings;

public interface SettingsProvider {
    String getString(String key, String fallback);
    int getInt(String key, int fallback);
    boolean getBoolean(String key, boolean fallback);
}
