package com.rainbow_universe.bettercode.core;

public final class ScoreboardContext {
    private final int lineCount;
    private final String detectedTier;
    private final boolean editorLike;

    public ScoreboardContext(int lineCount, String detectedTier, boolean editorLike) {
        this.lineCount = lineCount;
        this.detectedTier = detectedTier;
        this.editorLike = editorLike;
    }

    public int lineCount() {
        return lineCount;
    }

    public String detectedTier() {
        return detectedTier;
    }

    public boolean editorLike() {
        return editorLike;
    }
}
