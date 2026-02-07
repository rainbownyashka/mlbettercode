package com.example.examplemod.feature.export;

import java.util.Locale;

public final class ExportCodeCore {
    private ExportCodeCore() {}

    public interface DebugSink {
        void log(String msg);
    }

    public interface RowContext {
        boolean isLoaded(Pos pos);
        String getBlockId(Pos pos);
        String[] getSignLinesAtEntry(Pos entryPos);
        String getChestJsonAtEntry(Pos entryPos, boolean preferChestCache);
        String getFacing(Pos pos);
    }

    public static final class Pos {
        public final int x;
        public final int y;
        public final int z;

        public Pos(int x, int y, int z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        public Pos add(int dx, int dy, int dz) {
            return new Pos(x + dx, y + dy, z + dz);
        }

        public Pos up() {
            return add(0, 1, 0);
        }

        @Override
        public String toString() {
            return "Pos{" + "x=" + x + ", y=" + y + ", z=" + z + '}';
        }
    }

    public static String buildRowJson(RowContext ctx, Pos glassPos, int maxSteps, int rowIndex,
                                      boolean preferChestCache, DebugSink debug) {
        if (ctx == null || glassPos == null) {
            dbg(debug, "row[" + rowIndex + "] abort: ctx/glass null");
            return null;
        }
        if (!ctx.isLoaded(glassPos)) {
            dbg(debug, "row[" + rowIndex + "] abort: glass unloaded " + glassPos);
            return null;
        }

        Pos start = glassPos.up();
        if (!ctx.isLoaded(start)) {
            dbg(debug, "row[" + rowIndex + "] abort: start unloaded " + start);
            return null;
        }

        dbg(debug, "row[" + rowIndex + "] start glass=" + glassPos + " start=" + start + " maxSteps=" + maxSteps);
        dbg(debug, "row[" + rowIndex + "] logic=v4 entryStep=-2x side=-1x noFallback exportSidePistonEvenWithoutEntry");

        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"row\":").append(rowIndex).append(",");
        sb.append("\"glass\":").append(posJson(glassPos)).append(",");
        sb.append("\"blocks\":[");

        boolean first = true;
        int emptyPairs = 0;

        for (int p = 0; p < maxSteps; p++) {
            Pos entryPos = start.add(-2 * p, 0, 0);
            Pos sidePos = entryPos.add(-1, 0, 0);

            if (!ctx.isLoaded(entryPos) || !ctx.isLoaded(sidePos)) {
                dbg(debug, "row[" + rowIndex + "] stop@p=" + p + " reason=unloaded entry=" + entryPos
                    + " side=" + sidePos);
                break;
            }

            String entryBlock = nz(ctx.getBlockId(entryPos));
            String sideBlock = nz(ctx.getBlockId(sidePos));
            String[] signLines = ctx.getSignLinesAtEntry(entryPos);

            Pos sidePistonPos = null;
            String sidePistonBlock = "";
            if (isPiston(sideBlock)) {
                sidePistonPos = sidePos;
                sidePistonBlock = sideBlock;
            }

            boolean signEmpty = signLines == null || allEmpty(signLines);
            boolean emptySlot = isAir(entryBlock) && sidePistonPos == null && signEmpty;
            if (emptySlot) {
                emptyPairs++;
                dbg(debug, "row[" + rowIndex + "] p=" + p + " emptySlot=true emptyPairs=" + emptyPairs
                    + " entry=" + entryPos + " side=" + sidePos);
                if (emptyPairs >= 2) {
                    dbg(debug, "row[" + rowIndex + "] stop@p=" + p + " reason=two-empty-pairs");
                    break;
                }
                continue;
            }

            emptyPairs = 0;
            String chestJson = ctx.getChestJsonAtEntry(entryPos, preferChestCache);
            boolean hasChest = chestJson != null && !chestJson.isEmpty();
            boolean hasEntryData = !isAir(entryBlock)
                || (signLines != null && !allEmpty(signLines))
                || hasChest;

            dbg(debug, "row[" + rowIndex + "] p=" + p + " export entry=" + entryPos
                + " entryBlock=" + entryBlock
                + " sideBlock=" + sideBlock
                + " sign=" + (signLines == null ? "null" : java.util.Arrays.toString(signLines)));
            dbg(debug, "row[" + rowIndex + "] p=" + p + " chestDetected=" + hasChest);
            if (sidePistonPos != null) {
                dbg(debug, "row[" + rowIndex + "] p=" + p + " sidePistonDetected side=" + sidePistonPos + " sideBlock=" + sidePistonBlock);
            }

            if (hasEntryData) {
                if (!first) sb.append(",");
                first = false;
                sb.append(blockJson(entryPos, entryBlock, signLines, null, chestJson));
            }

            if (sidePistonPos != null) {
                String facing = nz(ctx.getFacing(sidePistonPos)).toLowerCase(Locale.ROOT);
                if (!first) sb.append(",");
                first = false;
                sb.append(blockJson(sidePistonPos, sidePistonBlock, null, facing, null));
                dbg(debug, "row[" + rowIndex + "] p=" + p + " export side piston side=" + sidePistonPos + " facing=" + facing);
            }
        }

        sb.append("]}");
        dbg(debug, "row[" + rowIndex + "] done");
        return sb.toString();
    }

    private static boolean isPiston(String blockId) {
        return "minecraft:piston".equals(blockId) || "minecraft:sticky_piston".equals(blockId);
    }

    private static boolean isAir(String blockId) {
        return blockId == null || blockId.isEmpty() || "minecraft:air".equals(blockId);
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    private static void dbg(DebugSink debug, String msg) {
        if (debug != null) {
            debug.log(msg);
        }
    }

    private static boolean allEmpty(String[] lines) {
        if (lines == null) {
            return true;
        }
        for (String s : lines) {
            if (s != null && !s.trim().isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private static String posJson(Pos pos) {
        if (pos == null) {
            return "{\"x\":0,\"y\":0,\"z\":0}";
        }
        return "{\"x\":" + pos.x + ",\"y\":" + pos.y + ",\"z\":" + pos.z + "}";
    }

    private static String blockJson(Pos pos, String blockId, String[] signLines, String facing, String chestJson) {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"block\":\"").append(escapeJson(blockId == null ? "minecraft:air" : blockId)).append("\",");
        sb.append("\"pos\":").append(posJson(pos));

        if (facing != null && !facing.isEmpty()) {
            sb.append(",\"facing\":\"").append(escapeJson(facing)).append("\"");
        }

        if (signLines != null) {
            sb.append(",\"sign\":[");
            for (int i = 0; i < 4; i++) {
                if (i > 0) sb.append(",");
                String v = i < signLines.length ? signLines[i] : "";
                sb.append("\"").append(escapeJson(v == null ? "" : v)).append("\"");
            }
            sb.append("]");
        }

        if (chestJson != null && !chestJson.isEmpty()) {
            sb.append(",\"chest\":").append(chestJson);
        }

        sb.append("}");
        return sb.toString();
    }

    public static String escapeJson(String s) {
        if (s == null) {
            return "";
        }
        StringBuilder out = new StringBuilder(s.length() + 16);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"': out.append("\\\""); break;
                case '\\': out.append("\\\\"); break;
                case '\b': out.append("\\b"); break;
                case '\f': out.append("\\f"); break;
                case '\n': out.append("\\n"); break;
                case '\r': out.append("\\r"); break;
                case '\t': out.append("\\t"); break;
                default:
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
            }
        }
        return out.toString();
    }
}
