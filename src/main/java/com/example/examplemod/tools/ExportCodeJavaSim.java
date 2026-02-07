package com.example.examplemod.tools;

import com.example.examplemod.feature.export.ExportArgDecodeCore;
import com.example.examplemod.feature.export.ExportCodeCore;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class ExportCodeJavaSim {
    private static final Set<String> CHEST_BLOCKS = new HashSet<>();
    static {
        String[] ids = {
            "minecraft:chest","minecraft:trapped_chest","minecraft:ender_chest",
            "minecraft:white_shulker_box","minecraft:orange_shulker_box","minecraft:magenta_shulker_box",
            "minecraft:light_blue_shulker_box","minecraft:yellow_shulker_box","minecraft:lime_shulker_box",
            "minecraft:pink_shulker_box","minecraft:gray_shulker_box","minecraft:silver_shulker_box",
            "minecraft:cyan_shulker_box","minecraft:purple_shulker_box","minecraft:blue_shulker_box",
            "minecraft:brown_shulker_box","minecraft:green_shulker_box","minecraft:red_shulker_box",
            "minecraft:black_shulker_box"
        };
        for (String id : ids) CHEST_BLOCKS.add(id);
    }

    private static final class SimCtx implements ExportCodeCore.RowContext {
        private final Map<String, JsonObject> nodes;
        private final Map<String, JsonObject> cache;

        SimCtx(Map<String, JsonObject> nodes, Map<String, JsonObject> cache) {
            this.nodes = nodes;
            this.cache = cache;
        }

        @Override
        public boolean isLoaded(ExportCodeCore.Pos pos) {
            return true;
        }

        @Override
        public String getBlockId(ExportCodeCore.Pos pos) {
            JsonObject n = nodes.get(key(pos));
            if (n == null || !n.has("block")) return "minecraft:air";
            return asString(n.get("block"), "minecraft:air");
        }

        @Override
        public String[] getSignLinesAtEntry(ExportCodeCore.Pos entryPos) {
            ExportCodeCore.Pos signPos = entryPos.add(0, 0, -1);
            JsonObject n = nodes.get(key(signPos));
            if (n == null || !n.has("sign") || !n.get("sign").isJsonArray()) return null;
            JsonArray a = n.getAsJsonArray("sign");
            String[] out = new String[]{"", "", "", ""};
            for (int i = 0; i < 4 && i < a.size(); i++) {
                out[i] = asString(a.get(i), "");
            }
            return out;
        }

        @Override
        public String getChestJsonAtEntry(ExportCodeCore.Pos entryPos, boolean preferChestCache) {
            ExportCodeCore.Pos chestPos = entryPos.up();
            JsonObject chestNode = nodes.get(key(chestPos));
            if (chestNode == null) return null;
            String chestBlock = asString(chestNode.get("block"), "minecraft:air");
            if (!CHEST_BLOCKS.contains(chestBlock)) return null;

            JsonObject out = new JsonObject();
            out.add("pos", posToJson(chestPos));

            String title = "";
            int size = 27;
            JsonArray liveSlots = new JsonArray();
            if (chestNode.has("chest") && chestNode.get("chest").isJsonObject()) {
                JsonObject chest = chestNode.getAsJsonObject("chest");
                title = asString(chest.get("title"), "");
                size = asInt(chest.get("size"), 27);
                if (chest.has("slots") && chest.get("slots").isJsonArray()) {
                    liveSlots = chest.getAsJsonArray("slots");
                }
            }

            JsonArray bestSlots = liveSlots;
            if (preferChestCache && (bestSlots == null || bestSlots.size() == 0)) {
                String ck = "0:" + key(chestPos);
                JsonObject cached = cache.get(ck);
                if (cached != null && cached.has("slots") && cached.get("slots").isJsonArray()) {
                    JsonArray cs = cached.getAsJsonArray("slots");
                    if (cs.size() > 0) {
                        bestSlots = cs;
                        if (title.trim().isEmpty()) {
                            title = asString(cached.get("title"), title);
                        }
                        if (size <= 0) {
                            size = asInt(cached.get("size"), 27);
                        }
                    }
                }
            }

            out.addProperty("title", title);
            out.addProperty("size", Math.max(1, size));
            out.add("slots", bestSlots == null ? new JsonArray() : bestSlots);
            return out.toString();
        }

        @Override
        public String getFacing(ExportCodeCore.Pos pos) {
            JsonObject n = nodes.get(key(pos));
            if (n == null) return "";
            return asString(n.get("facing"), "").toLowerCase(Locale.ROOT);
        }

        private static String key(ExportCodeCore.Pos p) {
            return p.x + "," + p.y + "," + p.z;
        }
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.out.println("Usage: ExportCodeJavaSim <input.json> <output.json> [--debug] [--no-cache] [--max-steps=N]");
            return;
        }
        File in = new File(args[0]);
        File out = new File(args[1]);

        boolean debug = false;
        boolean noCache = false;
        int maxSteps = 256;
        for (int i = 2; i < args.length; i++) {
            String a = args[i] == null ? "" : args[i].trim();
            if ("--debug".equalsIgnoreCase(a)) debug = true;
            else if ("--no-cache".equalsIgnoreCase(a)) noCache = true;
            else if (a.startsWith("--max-steps=")) {
                try { maxSteps = Integer.parseInt(a.substring("--max-steps=".length())); } catch (Exception ignore) { }
            }
        }

        JsonObject root;
        try (Reader r = new InputStreamReader(new FileInputStream(in), StandardCharsets.UTF_8)) {
            root = new JsonParser().parse(r).getAsJsonObject();
        }

        Map<String, JsonObject> nodes = new HashMap<>();
        if (root.has("nodes") && root.get("nodes").isJsonObject()) {
            JsonObject n = root.getAsJsonObject("nodes");
            for (Map.Entry<String, JsonElement> e : n.entrySet()) {
                if (e.getValue() != null && e.getValue().isJsonObject()) {
                    nodes.put(e.getKey(), e.getValue().getAsJsonObject());
                }
            }
        }

        Map<String, JsonObject> chestCache = new HashMap<>();
        if (root.has("chestCache") && root.get("chestCache").isJsonObject()) {
            JsonObject c = root.getAsJsonObject("chestCache");
            for (Map.Entry<String, JsonElement> e : c.entrySet()) {
                if (e.getValue() != null && e.getValue().isJsonObject()) {
                    chestCache.put(e.getKey(), e.getValue().getAsJsonObject());
                }
            }
        }

        List<ExportCodeCore.Pos> glasses = new ArrayList<>();
        if (root.has("glasses") && root.get("glasses").isJsonArray()) {
            JsonArray a = root.getAsJsonArray("glasses");
            for (JsonElement el : a) {
                if (el == null || !el.isJsonObject()) continue;
                JsonObject p = el.getAsJsonObject();
                glasses.add(new ExportCodeCore.Pos(asInt(p.get("x"), 0), asInt(p.get("y"), 0), asInt(p.get("z"), 0)));
            }
        }

        SimCtx ctx = new SimCtx(nodes, chestCache);

        JsonObject outRoot = new JsonObject();
        outRoot.addProperty("version", 2);
        outRoot.addProperty("scopeKey", asString(root.get("scopeKey"), "SIM:0"));
        outRoot.addProperty("exportedAt", System.currentTimeMillis());
        JsonArray rows = new JsonArray();

        for (int i = 0; i < glasses.size(); i++) {
            ExportCodeCore.Pos glass = glasses.get(i);
            String rowJson = ExportCodeCore.buildRowJson(ctx, glass, Math.max(32, maxSteps), i, !noCache,
                debug ? msg -> System.out.println(msg) : null);
            if (rowJson != null && !rowJson.trim().isEmpty()) {
                JsonObject rowObj = new JsonParser().parse(rowJson).getAsJsonObject();
                annotateDecodedArgs(rowObj);
                rows.add(rowObj);
            }
        }

        outRoot.add("rows", rows);
        if (out.getParentFile() != null && !out.getParentFile().exists()) out.getParentFile().mkdirs();
        try (OutputStreamWriter fw = new OutputStreamWriter(new FileOutputStream(out), StandardCharsets.UTF_8)) {
            fw.write(outRoot.toString());
            fw.write("\n");
        }
        System.out.println("OK: wrote " + out.getAbsolutePath());
    }

    private static void annotateDecodedArgs(JsonObject rowObj) {
        if (rowObj == null || !rowObj.has("blocks") || !rowObj.get("blocks").isJsonArray()) return;
        JsonArray blocks = rowObj.getAsJsonArray("blocks");
        for (JsonElement be : blocks) {
            if (be == null || !be.isJsonObject()) continue;
            JsonObject b = be.getAsJsonObject();
            if (!b.has("chest") || !b.get("chest").isJsonObject()) continue;
            JsonObject chest = b.getAsJsonObject("chest");
            if (!chest.has("slots") || !chest.get("slots").isJsonArray()) continue;
            JsonArray slots = chest.getAsJsonArray("slots");
            List<ExportArgDecodeCore.SlotData> list = new ArrayList<>();
            for (JsonElement se : slots) {
                if (se == null || !se.isJsonObject()) continue;
                JsonObject s = se.getAsJsonObject();
                ExportArgDecodeCore.SlotData sd = new ExportArgDecodeCore.SlotData();
                sd.slot = asInt(s.get("slot"), -1);
                sd.registry = asString(s.get("registry"), "");
                if (sd.registry.isEmpty()) {
                    sd.registry = asString(s.get("id"), "");
                }
                sd.display = asString(s.get("display"), "");
                if (sd.display.isEmpty()) {
                    sd.display = asString(s.get("displayName"), "");
                }
                sd.displayClean = asString(s.get("displayClean"), "");
                if (sd.displayClean.isEmpty()) {
                    sd.displayClean = sd.display;
                }
                sd.nbt = asString(s.get("nbt"), "");
                sd.count = asInt(s.get("count"), 1);
                if (s.has("lore") && s.get("lore").isJsonArray()) {
                    JsonArray la = s.getAsJsonArray("lore");
                    for (JsonElement le : la) sd.lore.add(asString(le, ""));
                }
                list.add(sd);
            }
            JsonArray decoded = new JsonArray();
            for (ExportArgDecodeCore.DecodedArg da : ExportArgDecodeCore.decodeRaw(list)) {
                JsonObject j = new JsonObject();
                j.addProperty("k", da.key);
                j.addProperty("v", da.value);
                decoded.add(j);
            }
            chest.add("decoded", decoded);
            String enumSel = ExportArgDecodeCore.decodeEnumSelectedText(list, 13);
            if (!enumSel.isEmpty()) {
                chest.addProperty("enumSelected", enumSel);
            }
        }
    }

    private static JsonObject posToJson(ExportCodeCore.Pos pos) {
        JsonObject o = new JsonObject();
        o.addProperty("x", pos.x);
        o.addProperty("y", pos.y);
        o.addProperty("z", pos.z);
        return o;
    }

    private static String asString(JsonElement e, String def) {
        try {
            if (e == null || e.isJsonNull()) return def;
            return e.getAsString();
        } catch (Exception ignore) {
            return def;
        }
    }

    private static int asInt(JsonElement e, int def) {
        try {
            if (e == null || e.isJsonNull()) return def;
            return e.getAsInt();
        } catch (Exception ignore) {
            return def;
        }
    }
}
