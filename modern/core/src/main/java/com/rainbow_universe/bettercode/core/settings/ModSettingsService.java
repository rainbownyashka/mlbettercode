package com.rainbow_universe.bettercode.core.settings;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ModSettingsService implements SettingsProvider {
    private static final String FILE_NAME = "bettercode_modern_config.json";

    private final Path file;
    private final Map<String, SettingDef> defs;
    private final Map<String, Object> values = new LinkedHashMap<String, Object>();

    private ModSettingsService(Path file, Map<String, SettingDef> defs) {
        this.file = file;
        this.defs = defs;
    }

    public static ModSettingsService createDefault(Path runDir) {
        Map<String, SettingDef> defs = new LinkedHashMap<String, SettingDef>();
        defs.put("hub.useMirror", new SettingDef(
            "hub.useMirror",
            "Use Mirror Hub",
            "false = vercel hub, true = duckdns mirror",
            SettingType.BOOLEAN,
            Boolean.FALSE,
            null,
            null
        ));
        defs.put("network.connectTimeoutMs", new SettingDef(
            "network.connectTimeoutMs",
            "Connect Timeout (ms)",
            "HTTP connect timeout for hub requests",
            SettingType.INTEGER,
            Integer.valueOf(8000),
            Integer.valueOf(1000),
            Integer.valueOf(60000)
        ));
        defs.put("network.readTimeoutMs", new SettingDef(
            "network.readTimeoutMs",
            "Read Timeout (ms)",
            "HTTP read timeout for hub requests",
            SettingType.INTEGER,
            Integer.valueOf(15000),
            Integer.valueOf(1000),
            Integer.valueOf(120000)
        ));
        defs.put("debug.verbose", new SettingDef(
            "debug.verbose",
            "Verbose Debug",
            "Extra debug output in chat/logs",
            SettingType.BOOLEAN,
            Boolean.FALSE,
            null,
            null
        ));
        defs.put("printer.retryMax", new SettingDef(
            "printer.retryMax",
            "Printer Retry Max",
            "Maximum retries for unstable place operations",
            SettingType.INTEGER,
            Integer.valueOf(6),
            Integer.valueOf(0),
            Integer.valueOf(50)
        ));
        defs.put("printer.stepDelayMs", new SettingDef(
            "printer.stepDelayMs",
            "Printer Step Delay (ms)",
            "Delay between queued place steps in runtime tick executor",
            SettingType.INTEGER,
            Integer.valueOf(80),
            Integer.valueOf(0),
            Integer.valueOf(2000)
        ));

        ModSettingsService svc = new ModSettingsService(runDir.resolve(FILE_NAME), defs);
        svc.load();
        return svc;
    }

    public synchronized Map<String, SettingDef> defs() {
        return Collections.unmodifiableMap(defs);
    }

    public synchronized Object getRaw(String key) {
        if (!values.containsKey(key)) {
            SettingDef d = defs.get(key);
            return d == null ? null : d.defaultValue();
        }
        return values.get(key);
    }

    @Override
    public synchronized String getString(String key, String fallback) {
        Object v = getRaw(key);
        if (v == null) {
            return fallback;
        }
        return String.valueOf(v);
    }

    @Override
    public synchronized int getInt(String key, int fallback) {
        Object v = getRaw(key);
        if (v instanceof Number) {
            return ((Number) v).intValue();
        }
        try {
            return v == null ? fallback : Integer.parseInt(String.valueOf(v));
        } catch (Exception e) {
            return fallback;
        }
    }

    @Override
    public synchronized boolean getBoolean(String key, boolean fallback) {
        Object v = getRaw(key);
        if (v instanceof Boolean) {
            return ((Boolean) v).booleanValue();
        }
        if (v == null) {
            return fallback;
        }
        return "true".equalsIgnoreCase(String.valueOf(v));
    }

    public synchronized String toggle(String key) {
        SettingDef d = defs.get(key);
        if (d == null) {
            return "unknown_key";
        }
        if (d.type() != SettingType.BOOLEAN) {
            return "not_boolean";
        }
        boolean next = !getBoolean(key, false);
        values.put(key, Boolean.valueOf(next));
        save();
        return null;
    }

    public synchronized String increment(String key, int delta) {
        SettingDef d = defs.get(key);
        if (d == null) {
            return "unknown_key";
        }
        if (d.type() != SettingType.INTEGER) {
            return "not_integer";
        }
        int value = getInt(key, ((Number) d.defaultValue()).intValue());
        int next = value + delta;
        if (d.minInt() != null && next < d.minInt().intValue()) {
            next = d.minInt().intValue();
        }
        if (d.maxInt() != null && next > d.maxInt().intValue()) {
            next = d.maxInt().intValue();
        }
        values.put(key, Integer.valueOf(next));
        save();
        return null;
    }

    public synchronized String setFromString(String key, String raw) {
        SettingDef d = defs.get(key);
        if (d == null) {
            return "unknown_key";
        }
        String valueRaw = raw == null ? "" : raw.trim();
        if (d.type() == SettingType.STRING) {
            if (valueRaw.isEmpty()) {
                return "empty_value";
            }
            values.put(key, valueRaw);
            save();
            return null;
        }
        if (d.type() == SettingType.BOOLEAN) {
            if (!"true".equalsIgnoreCase(valueRaw) && !"false".equalsIgnoreCase(valueRaw)) {
                return "invalid_boolean";
            }
            values.put(key, Boolean.valueOf(Boolean.parseBoolean(valueRaw)));
            save();
            return null;
        }
        if (d.type() == SettingType.INTEGER) {
            int parsed;
            try {
                parsed = Integer.parseInt(valueRaw);
            } catch (Exception e) {
                return "invalid_integer";
            }
            if (d.minInt() != null && parsed < d.minInt().intValue()) {
                return "below_min:" + d.minInt();
            }
            if (d.maxInt() != null && parsed > d.maxInt().intValue()) {
                return "above_max:" + d.maxInt();
            }
            values.put(key, Integer.valueOf(parsed));
            save();
            return null;
        }
        return "unsupported_type";
    }

    public synchronized void load() {
        values.clear();
        if (!Files.exists(file)) {
            return;
        }
        InputStreamReader reader = null;
        try {
            reader = new InputStreamReader(Files.newInputStream(file), StandardCharsets.UTF_8);
            JsonElement root = new JsonParser().parse(reader);
            if (!root.isJsonObject()) {
                moveBroken();
                return;
            }
            JsonObject obj = root.getAsJsonObject();
            JsonObject vals = obj.has("values") && obj.get("values").isJsonObject() ? obj.getAsJsonObject("values") : null;
            if (vals == null) {
                return;
            }
            for (Map.Entry<String, JsonElement> e : vals.entrySet()) {
                SettingDef d = defs.get(e.getKey());
                if (d == null) {
                    continue;
                }
                JsonElement v = e.getValue();
                if (v == null || v.isJsonNull()) {
                    continue;
                }
                if (d.type() == SettingType.STRING && v.isJsonPrimitive()) {
                    values.put(d.key(), v.getAsString());
                } else if (d.type() == SettingType.BOOLEAN && v.isJsonPrimitive()) {
                    values.put(d.key(), Boolean.valueOf(v.getAsBoolean()));
                } else if (d.type() == SettingType.INTEGER && v.isJsonPrimitive()) {
                    values.put(d.key(), Integer.valueOf(v.getAsInt()));
                }
            }
        } catch (Exception e) {
            moveBroken();
            values.clear();
        } finally {
            try {
                if (reader != null) {
                    reader.close();
                }
            } catch (Exception ignore) { }
        }
    }

    public synchronized void save() {
        try {
            Files.createDirectories(file.getParent());
            JsonObject root = new JsonObject();
            JsonObject vals = new JsonObject();
            for (Map.Entry<String, SettingDef> e : defs.entrySet()) {
                String key = e.getKey();
                Object v = values.containsKey(key) ? values.get(key) : e.getValue().defaultValue();
                if (v instanceof Boolean) {
                    vals.addProperty(key, (Boolean) v);
                } else if (v instanceof Number) {
                    vals.addProperty(key, (Number) v);
                } else if (v != null) {
                    vals.addProperty(key, String.valueOf(v));
                }
            }
            root.add("values", vals);
            OutputStreamWriter writer = null;
            try {
                writer = new OutputStreamWriter(Files.newOutputStream(file), StandardCharsets.UTF_8);
                writer.write(root.toString());
                writer.write("\n");
            } finally {
                if (writer != null) {
                    writer.close();
                }
            }
        } catch (Exception ignore) {
        }
    }

    private void moveBroken() {
        try {
            if (!Files.exists(file)) {
                return;
            }
            Path broken = file.resolveSibling(file.getFileName().toString() + ".broken");
            Files.move(file, broken, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception ignore) {
        }
    }
}
