package com.rainbow_universe.bettercode.core;

import java.util.List;
import java.util.Locale;

public final class ScoreboardParser {
    private ScoreboardParser() {
    }

    public static ScoreboardContext parse(List<String> lines) {
        if (lines == null || lines.isEmpty()) {
            return new ScoreboardContext(0, "unknown", false);
        }

        String tier = "unknown";
        boolean editor = false;
        for (String raw : lines) {
            String line = SignLineNormalizer.normalizeForMatch(raw).toLowerCase(Locale.ROOT);
            if (line.contains("редакт") || line.contains("editor") || line.contains("code")) {
                editor = true;
            }
            if (line.contains("legend")) {
                tier = "legend";
                continue;
            }
            if (line.contains("king")) {
                tier = "king";
                continue;
            }
            if (line.contains("hero")) {
                tier = "hero";
                continue;
            }
            if (line.contains("expert")) {
                tier = "expert";
                continue;
            }
            if (line.contains("skilled")) {
                tier = "skilled";
                continue;
            }
            if (line.contains("gamer")) {
                tier = "gamer";
                continue;
            }
            if (line.contains("player") || line.contains("игрок")) {
                tier = "player";
            }
        }
        return new ScoreboardContext(lines.size(), tier, editor);
    }
}
