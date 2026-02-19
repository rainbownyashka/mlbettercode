package com.rainbow_universe.bettercode.core;

import java.util.List;

public final class ScoreboardScopeResolver {
    private static final String DEFAULT_SCOPE_ID = "default";

    private ScoreboardScopeResolver() {
    }

    public static String extractScoreboardIdLine(List<String> lines) {
        if (lines == null || lines.isEmpty()) {
            return null;
        }
        String fallbackAnyId = null;
        for (String raw : lines) {
            ParsedLine parsed = parseScoredLine(raw);
            String line = parsed.text;
            if (line == null) {
                continue;
            }
            String clean = line.trim();
            if (clean.isEmpty()) {
                continue;
            }
            if (parsed.hasScore && parsed.score == 12 && clean.contains("ID")) {
                return clean;
            }
            if (fallbackAnyId == null && clean.contains("ID")) {
                fallbackAnyId = clean;
            }
        }
        return fallbackAnyId;
    }

    public static String normalizeScopeId(String raw) {
        if (raw == null) {
            return DEFAULT_SCOPE_ID;
        }
        String clean = raw.trim();
        return clean.isEmpty() ? DEFAULT_SCOPE_ID : clean;
    }

    private static ParsedLine parseScoredLine(String raw) {
        if (raw == null) {
            return ParsedLine.of(false, 0, null);
        }
        String text = raw.trim();
        if (text.isEmpty()) {
            return ParsedLine.of(false, 0, "");
        }
        if (!text.startsWith("[")) {
            return ParsedLine.of(false, 0, text);
        }
        int right = text.indexOf(']');
        if (right <= 1 || right + 1 >= text.length()) {
            return ParsedLine.of(false, 0, text);
        }
        try {
            int score = Integer.parseInt(text.substring(1, right).trim());
            return ParsedLine.of(true, score, text.substring(right + 1).trim());
        } catch (NumberFormatException ignored) {
            return ParsedLine.of(false, 0, text);
        }
    }

    private static final class ParsedLine {
        final boolean hasScore;
        final int score;
        final String text;

        private ParsedLine(boolean hasScore, int score, String text) {
            this.hasScore = hasScore;
            this.score = score;
            this.text = text;
        }

        static ParsedLine of(boolean hasScore, int score, String text) {
            return new ParsedLine(hasScore, score, text);
        }
    }
}
