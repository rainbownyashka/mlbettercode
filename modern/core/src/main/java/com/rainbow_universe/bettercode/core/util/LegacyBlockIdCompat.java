package com.rainbow_universe.bettercode.core.util;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public final class LegacyBlockIdCompat {
    private static final Map<String, String> LEGACY_TO_MODERN = buildMap();

    private LegacyBlockIdCompat() {
    }

    public static String normalizeForModern(String rawBlockId) {
        if (rawBlockId == null) {
            return "";
        }
        String v = rawBlockId.trim();
        if (v.isEmpty()) {
            return "";
        }
        String namespaced = v.contains(":") ? v.toLowerCase() : ("minecraft:" + v.toLowerCase());
        String mapped = LEGACY_TO_MODERN.get(namespaced);
        return mapped == null ? namespaced : mapped;
    }

    private static Map<String, String> buildMap() {
        Map<String, String> out = new HashMap<String, String>();
        // 1.12 -> 1.13+ block id renames used in user plans.
        out.put("minecraft:planks", "minecraft:oak_planks");
        out.put("minecraft:grass", "minecraft:grass_block");
        out.put("minecraft:bed", "minecraft:red_bed");
        out.put("minecraft:wool", "minecraft:white_wool");
        out.put("minecraft:stained_glass", "minecraft:white_stained_glass");
        out.put("minecraft:stained_glass_pane", "minecraft:white_stained_glass_pane");
        out.put("minecraft:hardened_clay", "minecraft:terracotta");
        out.put("minecraft:stained_hardened_clay", "minecraft:white_terracotta");
        out.put("minecraft:wooden_door", "minecraft:oak_door");
        out.put("minecraft:wooden_button", "minecraft:oak_button");
        out.put("minecraft:wooden_pressure_plate", "minecraft:oak_pressure_plate");
        out.put("minecraft:wooden_slab", "minecraft:oak_slab");
        return Collections.unmodifiableMap(out);
    }
}
