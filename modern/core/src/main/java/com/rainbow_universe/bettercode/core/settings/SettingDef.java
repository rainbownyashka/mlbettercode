package com.rainbow_universe.bettercode.core.settings;

public final class SettingDef {
    private final String key;
    private final String title;
    private final String description;
    private final SettingType type;
    private final Object defaultValue;
    private final Integer minInt;
    private final Integer maxInt;

    public SettingDef(String key, String title, String description, SettingType type, Object defaultValue, Integer minInt, Integer maxInt) {
        this.key = key;
        this.title = title;
        this.description = description;
        this.type = type;
        this.defaultValue = defaultValue;
        this.minInt = minInt;
        this.maxInt = maxInt;
    }

    public String key() { return key; }
    public String title() { return title; }
    public String description() { return description; }
    public SettingType type() { return type; }
    public Object defaultValue() { return defaultValue; }
    public Integer minInt() { return minInt; }
    public Integer maxInt() { return maxInt; }
}
