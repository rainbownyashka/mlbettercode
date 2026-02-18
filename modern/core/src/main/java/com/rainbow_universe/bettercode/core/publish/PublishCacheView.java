package com.rainbow_universe.bettercode.core.publish;

import java.util.HashMap;
import java.util.Map;

public final class PublishCacheView {
    public static final class ResolvedSign {
        public final String source;
        public final String key;
        public final String[] lines;

        public ResolvedSign(String source, String key, String[] lines) {
            this.source = source == null ? "" : source;
            this.key = key == null ? "" : key;
            this.lines = copy(lines);
        }
    }

    private final Map<String, String[]> signByScope = new HashMap<String, String[]>();
    private final Map<String, String[]> signByDimPos = new HashMap<String, String[]>();

    public void putScope(String key, String[] lines) {
        if (key == null || key.trim().isEmpty() || isInvalid(lines)) {
            return;
        }
        signByScope.put(key, copy(lines));
    }

    public void putDimPos(String key, String[] lines) {
        if (key == null || key.trim().isEmpty() || isInvalid(lines)) {
            return;
        }
        signByDimPos.put(key, copy(lines));
    }

    public String[] getScope(String key) {
        return copy(signByScope.get(key));
    }

    public String[] getDimPos(String key) {
        return copy(signByDimPos.get(key));
    }

    public ResolvedSign resolve(String scopeKey, String dimPosKey, String[] liveLines) {
        if (!isInvalid(liveLines)) {
            putScope(scopeKey, liveLines);
            putDimPos(dimPosKey, liveLines);
            return new ResolvedSign("live", dimPosKey == null ? scopeKey : dimPosKey, liveLines);
        }
        String[] scope = getScope(scopeKey);
        if (!isInvalid(scope)) {
            return new ResolvedSign("scope", scopeKey, scope);
        }
        String[] dim = getDimPos(dimPosKey);
        if (!isInvalid(dim)) {
            return new ResolvedSign("dim", dimPosKey, dim);
        }
        return null;
    }

    public static boolean isInvalid(String[] lines) {
        if (lines == null || lines.length == 0) {
            return true;
        }
        for (String line : lines) {
            if (line != null && !line.trim().isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private static String[] copy(String[] src) {
        if (src == null) {
            return null;
        }
        String[] out = new String[src.length];
        System.arraycopy(src, 0, out, 0, src.length);
        return out;
    }
}
