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
    private final Map<String, String> entryToSignByScopeEntry = new HashMap<String, String>();

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

    public void putEntryToSign(String entryKey, String signPosKey) {
        if (entryKey == null || entryKey.trim().isEmpty() || signPosKey == null || signPosKey.trim().isEmpty()) {
            return;
        }
        String prev = entryToSignByScopeEntry.get(entryKey);
        if (prev == null || !prev.equals(signPosKey)) {
            entryToSignByScopeEntry.put(entryKey, signPosKey);
        }
    }

    public String getEntryToSign(String entryKey) {
        if (entryKey == null) {
            return null;
        }
        String hit = entryToSignByScopeEntry.get(entryKey);
        return hit == null ? null : hit;
    }

    public ResolvedSign resolve(String scopeKey, String dimPosKey, String entryKey, String signPosKey, String[] liveLines) {
        if (!isInvalid(liveLines)) {
            putScope(scopeKey, liveLines);
            putDimPos(dimPosKey, liveLines);
            putEntryToSign(entryKey, signPosKey);
            return new ResolvedSign("live", dimPosKey == null ? scopeKey : dimPosKey, liveLines);
        }
        String[] scope = getScope(scopeKey);
        if (!isInvalid(scope)) {
            return new ResolvedSign("scope", scopeKey, scope);
        }
        String mappedSign = getEntryToSign(entryKey);
        if (mappedSign != null && !mappedSign.trim().isEmpty()) {
            String[] byMapped = getDimPos(mappedSign);
            if (!isInvalid(byMapped)) {
                return new ResolvedSign("entry", mappedSign, byMapped);
            }
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

    public Map<String, String[]> scopeSnapshot() {
        Map<String, String[]> out = new HashMap<String, String[]>();
        for (Map.Entry<String, String[]> e : signByScope.entrySet()) {
            if (e == null) {
                continue;
            }
            out.put(e.getKey(), copy(e.getValue()));
        }
        return out;
    }

    public Map<String, String[]> dimPosSnapshot() {
        Map<String, String[]> out = new HashMap<String, String[]>();
        for (Map.Entry<String, String[]> e : signByDimPos.entrySet()) {
            if (e == null) {
                continue;
            }
            out.put(e.getKey(), copy(e.getValue()));
        }
        return out;
    }

    public Map<String, String> entryToSignSnapshot() {
        return new HashMap<String, String>(entryToSignByScopeEntry);
    }

    public void mergeFrom(PublishCacheView other) {
        if (other == null) {
            return;
        }
        for (Map.Entry<String, String[]> e : other.scopeSnapshot().entrySet()) {
            putScope(e.getKey(), e.getValue());
        }
        for (Map.Entry<String, String[]> e : other.dimPosSnapshot().entrySet()) {
            putDimPos(e.getKey(), e.getValue());
        }
        for (Map.Entry<String, String> e : other.entryToSignSnapshot().entrySet()) {
            putEntryToSign(e.getKey(), e.getValue());
        }
    }
}
