package com.rainbow_universe.bettercode.core.publish;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

public final class PublishCacheStore {
    private PublishCacheStore() {
    }

    public static PublishCacheView load(Path runDirectory) {
        PublishCacheView out = new PublishCacheView();
        Path file = cacheFile(runDirectory);
        if (file == null || !Files.isRegularFile(file)) {
            return out;
        }
        InputStreamReader reader = null;
        try {
            reader = new InputStreamReader(Files.newInputStream(file), StandardCharsets.UTF_8);
            JsonElement root = new JsonParser().parse(reader);
            if (root == null || !root.isJsonObject()) {
                return out;
            }
            JsonObject obj = root.getAsJsonObject();
            JsonObject scope = obj.has("scope") && obj.get("scope").isJsonObject() ? obj.getAsJsonObject("scope") : null;
            JsonObject dimPos = obj.has("dimPos") && obj.get("dimPos").isJsonObject() ? obj.getAsJsonObject("dimPos") : null;
            JsonObject entryToSign = obj.has("entryToSign") && obj.get("entryToSign").isJsonObject() ? obj.getAsJsonObject("entryToSign") : null;
            if (scope != null) {
                for (Map.Entry<String, JsonElement> e : scope.entrySet()) {
                    out.putScope(e.getKey(), readLines(e.getValue()));
                }
            }
            if (dimPos != null) {
                for (Map.Entry<String, JsonElement> e : dimPos.entrySet()) {
                    out.putDimPos(e.getKey(), readLines(e.getValue()));
                }
            }
            if (entryToSign != null) {
                for (Map.Entry<String, JsonElement> e : entryToSign.entrySet()) {
                    if (e == null || e.getValue() == null || !e.getValue().isJsonPrimitive()) {
                        continue;
                    }
                    out.putEntryToSign(e.getKey(), e.getValue().getAsString());
                }
            }
        } catch (Exception ignore) {
        } finally {
            try {
                if (reader != null) {
                    reader.close();
                }
            } catch (Exception ignore) {
            }
        }
        return out;
    }

    public static void save(Path runDirectory, PublishCacheView cache) {
        if (runDirectory == null || cache == null) {
            return;
        }
        Path file = cacheFile(runDirectory);
        if (file == null) {
            return;
        }
        try {
            Files.createDirectories(file.getParent());
            JsonObject root = new JsonObject();
            root.addProperty("version", Integer.valueOf(1));

            JsonObject scope = new JsonObject();
            for (Map.Entry<String, String[]> e : cache.scopeSnapshot().entrySet()) {
                scope.add(e.getKey(), toJsonArray(e.getValue()));
            }
            root.add("scope", scope);

            JsonObject dimPos = new JsonObject();
            for (Map.Entry<String, String[]> e : cache.dimPosSnapshot().entrySet()) {
                dimPos.add(e.getKey(), toJsonArray(e.getValue()));
            }
            root.add("dimPos", dimPos);

            JsonObject entryToSign = new JsonObject();
            for (Map.Entry<String, String> e : cache.entryToSignSnapshot().entrySet()) {
                if (e.getKey() == null || e.getValue() == null) {
                    continue;
                }
                entryToSign.addProperty(e.getKey(), e.getValue());
            }
            root.add("entryToSign", entryToSign);

            OutputStreamWriter writer = null;
            try {
                writer = new OutputStreamWriter(Files.newOutputStream(file), StandardCharsets.UTF_8);
                writer.write(root.toString());
            } finally {
                if (writer != null) {
                    writer.close();
                }
            }
        } catch (Exception ignore) {
        }
    }

    private static JsonArray toJsonArray(String[] lines) {
        JsonArray arr = new JsonArray();
        if (lines == null) {
            return arr;
        }
        for (String line : lines) {
            arr.add(line == null ? "" : line);
        }
        return arr;
    }

    private static String[] readLines(JsonElement el) {
        if (el == null || !el.isJsonArray()) {
            return null;
        }
        JsonArray arr = el.getAsJsonArray();
        if (arr.size() <= 0) {
            return null;
        }
        String[] out = new String[arr.size()];
        for (int i = 0; i < arr.size(); i++) {
            JsonElement item = arr.get(i);
            out[i] = item == null || item.isJsonNull() ? "" : item.getAsString();
        }
        return out;
    }

    private static Path cacheFile(Path runDirectory) {
        if (runDirectory == null) {
            return null;
        }
        return runDirectory.resolve("mldsl_cache").resolve("publish_sign_cache.json");
    }
}
