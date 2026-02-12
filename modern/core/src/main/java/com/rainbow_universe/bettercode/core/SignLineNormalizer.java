package com.rainbow_universe.bettercode.core;

public final class SignLineNormalizer {
    private SignLineNormalizer() {
    }

    public static String normalizeForMatch(String value) {
        if (value == null) {
            return "";
        }
        // Strip MC formatting codes used as matching noise: §a, §l, etc.
        String stripped = value.replaceAll("(?i)§[0-9A-FK-OR]", "");
        return stripped.trim().replaceAll("\\s+", " ");
    }
}
