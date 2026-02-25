package com.rainbow_universe.bettercode.core.publish;

import com.rainbow_universe.bettercode.core.GameBridge;
import com.rainbow_universe.bettercode.core.bridge.SelectedRow;
import com.rainbow_universe.bettercode.core.publish.export.ExportCodeCore;

import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public final class PublishLiveExportExecutor {
    public interface Trace {
        void trace(String stage, String details);
    }

    public static final class Result {
        public final boolean ok;
        public final String errorCode;
        public final String errorMessage;
        public final Path exportFile;
        public final int exportedRows;

        private Result(boolean ok, String errorCode, String errorMessage, Path exportFile, int exportedRows) {
            this.ok = ok;
            this.errorCode = errorCode == null ? "" : errorCode;
            this.errorMessage = errorMessage == null ? "" : errorMessage;
            this.exportFile = exportFile;
            this.exportedRows = exportedRows;
        }

        public static Result ok(Path exportFile, int exportedRows) {
            return new Result(true, "", "", exportFile, exportedRows);
        }

        public static Result fail(String errorCode, String errorMessage) {
            return new Result(false, errorCode, errorMessage, null, 0);
        }
    }

    private PublishLiveExportExecutor() {
    }

    public static Result export(
        GameBridge bridge,
        List<SelectedRow> selectedRows,
        String scopeKey,
        int maxSteps,
        boolean preferChestCache,
        Trace trace
    ) {
        if (bridge == null) {
            return Result.fail("PUBLISH_EXPORT_FAILED", "bridge unavailable");
        }
        if (selectedRows == null || selectedRows.isEmpty()) {
            return Result.fail("PUBLISH_EXPORT_FAILED", "no selected rows for live export");
        }
        int effectiveMaxSteps = Math.max(32, maxSteps);
        List<SelectedRow> rows = new ArrayList<SelectedRow>(selectedRows);
        Collections.sort(rows, new Comparator<SelectedRow>() {
            @Override
            public int compare(SelectedRow a, SelectedRow b) {
                if (a == b) {
                    return 0;
                }
                if (a == null) {
                    return -1;
                }
                if (b == null) {
                    return 1;
                }
                int cy = Integer.compare(a.y(), b.y());
                if (cy != 0) {
                    return cy;
                }
                int cz = Integer.compare(a.z(), b.z());
                if (cz != 0) {
                    return cz;
                }
                return Integer.compare(a.x(), b.x());
            }
        });
        String normScope = scopeKey == null ? "default" : scopeKey.trim();
        if (normScope.isEmpty()) {
            normScope = "default";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"version\":2,");
        sb.append("\"scopeKey\":\"").append(ExportCodeCore.escapeJson(normScope)).append("\",");
        sb.append("\"exportedAt\":").append(System.currentTimeMillis()).append(",");
        sb.append("\"rows\":[");

        int rowIndex = 0;
        int exportedRows = 0;
        boolean firstRow = true;
        for (SelectedRow row : rows) {
            if (row == null) {
                continue;
            }
            final int glassX = row.x();
            final int glassY = row.y();
            final int glassZ = row.z();
            ExportCodeCore.RowContext ctx = new ExportCodeCore.RowContext() {
                @Override
                public boolean isLoaded(ExportCodeCore.Pos pos) {
                    if (pos == null) {
                        return false;
                    }
                    String id = bridge.getBlockIdAt(pos.x, pos.y, pos.z);
                    return id != null && !id.trim().isEmpty();
                }

                @Override
                public String getBlockId(ExportCodeCore.Pos pos) {
                    if (pos == null) {
                        return "minecraft:air";
                    }
                    String id = bridge.getBlockIdAt(pos.x, pos.y, pos.z);
                    if (id == null || id.trim().isEmpty()) {
                        return "minecraft:air";
                    }
                    return id.trim();
                }

                @Override
                public String[] getSignLinesAtEntry(ExportCodeCore.Pos entryPos) {
                    if (entryPos == null) {
                        return null;
                    }
                    for (int dy = -2; dy <= 0; dy++) {
                        int sx = entryPos.x;
                        int sy = entryPos.y + dy;
                        int sz = entryPos.z - 1;
                        if (!bridge.isSignAt(sx, sy, sz)) {
                            continue;
                        }
                        String[] lines = bridge.readSignLinesAt(sx, sy, sz);
                        if (hasText(lines)) {
                            return lines;
                        }
                    }
                    return null;
                }

                @Override
                public String getChestJsonAtEntry(ExportCodeCore.Pos entryPos, boolean preferCache) {
                    if (entryPos == null) {
                        return "";
                    }
                    String json = bridge.getChestJsonAtEntry(entryPos.x, entryPos.y, entryPos.z, preferCache);
                    return json == null ? "" : json;
                }

                @Override
                public String getFacing(ExportCodeCore.Pos pos) {
                    if (pos == null) {
                        return "";
                    }
                    String facing = bridge.getBlockFacingAt(pos.x, pos.y, pos.z);
                    return facing == null ? "" : facing;
                }
            };
            ExportCodeCore.Pos glassPos = new ExportCodeCore.Pos(glassX, glassY, glassZ);
            String rowJson = ExportCodeCore.buildRowJson(ctx, glassPos, effectiveMaxSteps, rowIndex, preferChestCache, null);
            rowIndex++;
            if (rowJson == null || rowJson.trim().isEmpty()) {
                continue;
            }
            if (!firstRow) {
                sb.append(",");
            }
            firstRow = false;
            sb.append(rowJson);
            exportedRows++;
        }
        sb.append("]}");

        if (exportedRows <= 0) {
            return Result.fail("PUBLISH_EXPORT_FAILED", "no valid rows exported from current selection");
        }
        try {
            String fileName = "exportcode_publish_" + System.currentTimeMillis() + ".json";
            Path out = bridge.runDirectory().resolve(fileName);
            OutputStreamWriter writer = null;
            try {
                writer = new OutputStreamWriter(Files.newOutputStream(out), StandardCharsets.UTF_8);
                writer.write(sb.toString());
                writer.write("\n");
            } finally {
                if (writer != null) {
                    writer.close();
                }
            }
            if (trace != null) {
                trace.trace("publish.exportcode.generated", "rows=" + exportedRows + " file=" + out.toAbsolutePath());
            }
            return Result.ok(out, exportedRows);
        } catch (Exception e) {
            return Result.fail("PUBLISH_EXPORT_FAILED", e.getClass().getSimpleName() + ": " + String.valueOf(e.getMessage()));
        }
    }

    private static boolean hasText(String[] lines) {
        if (lines == null) {
            return false;
        }
        for (String line : lines) {
            if (line != null && !line.trim().isEmpty()) {
                return true;
            }
        }
        return false;
    }
}
