package com.rainbow_universe.bettercode.core.publish;

import com.rainbow_universe.bettercode.core.GameBridge;
import com.rainbow_universe.bettercode.core.bridge.SelectedRow;

public final class PublishSignResolver {
    public static final class Result {
        public final boolean ok;
        public final String source;
        public final String key;
        public final String[] lines;
        public final String reason;

        private Result(boolean ok, String source, String key, String[] lines, String reason) {
            this.ok = ok;
            this.source = source == null ? "" : source;
            this.key = key == null ? "" : key;
            this.lines = lines == null ? null : copy(lines);
            this.reason = reason == null ? "" : reason;
        }

        public static Result ok(String source, String key, String[] lines) {
            return new Result(true, source, key, lines, "");
        }

        public static Result fail(String reason) {
            return new Result(false, "", "", null, reason);
        }
    }

    private PublishSignResolver() {
    }

    public static Result resolve(SelectedRow row, PublishCacheView cacheView, GameBridge bridge, String scopeCacheKey) {
        if (row == null || cacheView == null || bridge == null) {
            return Result.fail("null_context");
        }
        PublishRowContext ctx = PublishRowContext.fromSelectedRow(row);
        if (ctx == null) {
            return Result.fail("null_context");
        }
        String dim = normalizeDim(bridge.dimensionId(), row.dimension(), bridge.currentDimension());
        int entryX = ctx.entryX();
        int entryY = ctx.entryY();
        int entryZ = ctx.entryZ();

        int signX = ctx.signX();
        int signY = ctx.signY();
        int signZ = ctx.signZ();
        System.out.println("[publish-debug] SIGN_RESOLVE begin dim=" + dim
            + " entry=" + entryX + "," + entryY + "," + entryZ
            + " sign_hint=" + signX + "," + signY + "," + signZ);

        // Legacy parity: find sign at z-1 from entry across y offsets [-2..0].
        int[] signPos = findLegacySignPos(bridge, entryX, entryY, entryZ);
        String[] liveLines = null;
        boolean signPresent = false;
        if (signPos != null) {
            signX = signPos[0];
            signY = signPos[1];
            signZ = signPos[2];
            signPresent = true;
            liveLines = bridge.readSignLinesAt(signX, signY, signZ);
            System.out.println("[publish-debug] SIGN_RESOLVE sign_found pos="
                + signX + "," + signY + "," + signZ
                + " liveLines=" + formatLines(liveLines));
        } else {
            System.out.println("[publish-debug] SIGN_RESOLVE sign_missing_at_zminus1 entry="
                + entryX + "," + entryY + "," + entryZ);
        }

        String scopeBase = (scopeCacheKey == null || scopeCacheKey.trim().isEmpty()) ? "default:" + dim : scopeCacheKey.trim() + ":" + dim;
        String scopeKey = scopeBase + ":row:" + entryX + ":" + entryY + ":" + entryZ;
        String dimPosKey = dim + ":" + signX + ":" + signY + ":" + signZ;
        String entryKey = dim + ":entry:" + entryX + ":" + entryY + ":" + entryZ;
        String signPosKey = dim + ":" + signX + ":" + signY + ":" + signZ;
        PublishCacheView.ResolvedSign resolved = cacheView.resolve(scopeKey, dimPosKey, entryKey, signPosKey, liveLines);
        if (resolved == null || PublishCacheView.isInvalid(resolved.lines)) {
            System.out.println("[publish-debug] SIGN_RESOLVE unresolved reason="
                + (resolved == null ? "resolved_null" : "resolved_invalid")
                + " signPresent=" + signPresent
                + " liveLines=" + formatLines(liveLines)
                + " keys scope=" + scopeKey + " dimPos=" + dimPosKey + " entry=" + entryKey);
            if (!signPresent) {
                return Result.fail("sign_missing");
            }
            if (PublishCacheView.isInvalid(liveLines)) {
                return Result.fail("sign_empty");
            }
            return Result.fail("cache_miss");
        }
        System.out.println("[publish-debug] SIGN_RESOLVE ok source=" + resolved.source + " key=" + resolved.key
            + " lines=" + formatLines(resolved.lines));
        return Result.ok(resolved.source, resolved.key, resolved.lines);
    }

    private static int[] findLegacySignPos(GameBridge bridge, int entryX, int entryY, int entryZ) {
        if (bridge == null) {
            return null;
        }
        for (int dy = -2; dy <= 0; dy++) {
            int sx = entryX;
            int sy = entryY + dy;
            int sz = entryZ - 1;
            if (bridge.isSignAt(sx, sy, sz)) {
                return new int[]{sx, sy, sz};
            }
        }
        return null;
    }

    private static String normalizeDim(String preferred, String rowDim, String fallback) {
        if (preferred != null && !preferred.trim().isEmpty()) {
            return preferred.trim();
        }
        if (rowDim != null && !rowDim.trim().isEmpty()) {
            return rowDim.trim();
        }
        return fallback == null ? "" : fallback.trim();
    }

    private static String[] copy(String[] src) {
        if (src == null) {
            return null;
        }
        String[] out = new String[src.length];
        System.arraycopy(src, 0, out, 0, src.length);
        return out;
    }

    private static String formatLines(String[] lines) {
        if (lines == null) {
            return "null";
        }
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < lines.length; i++) {
            if (i > 0) {
                sb.append(" | ");
            }
            String v = lines[i] == null ? "" : lines[i].trim();
            if (v.length() > 64) {
                v = v.substring(0, 64) + "...";
            }
            sb.append(i).append('=').append(v);
        }
        sb.append(']');
        return sb.toString();
    }
}
