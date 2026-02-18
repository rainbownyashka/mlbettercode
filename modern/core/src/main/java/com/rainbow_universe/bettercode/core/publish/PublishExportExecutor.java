package com.rainbow_universe.bettercode.core.publish;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.rainbow_universe.bettercode.core.bridge.SelectedRow;

import java.io.File;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public final class PublishExportExecutor {
    public static final class Result {
        public final boolean ok;
        public final String errorCode;
        public final String errorMessage;
        public final Path bundleDir;
        public final int copiedFiles;

        private Result(boolean ok, String errorCode, String errorMessage, Path bundleDir, int copiedFiles) {
            this.ok = ok;
            this.errorCode = errorCode == null ? "" : errorCode;
            this.errorMessage = errorMessage == null ? "" : errorMessage;
            this.bundleDir = bundleDir;
            this.copiedFiles = copiedFiles;
        }

        public static Result ok(Path dir, int copied) {
            return new Result(true, "", "", dir, copied);
        }

        public static Result fail(String code, String message) {
            return new Result(false, code, message, null, 0);
        }
    }

    private PublishExportExecutor() {
    }

    public static Result prepareBundle(PublishSessionState state, Path runDir) {
        if (state == null || runDir == null) {
            return Result.fail("PUBLISH_PREP_FAILED", "null export context");
        }
        try {
            Path publishRoot = runDir.resolve("mldsl_publish");
            Files.createDirectories(publishRoot);
            String bundleName = "bundle_" + safe(state.postId) + "_" + System.currentTimeMillis();
            Path bundleDir = publishRoot.resolve(bundleName);
            Files.createDirectories(bundleDir);
            state.bundleDir = bundleDir.toFile();

            int copied = 0;
            for (File src : state.sourceFiles) {
                if (src == null || !src.exists()) {
                    continue;
                }
                Path dst = bundleDir.resolve(safe(src.getName()));
                Files.copy(src.toPath(), dst, StandardCopyOption.REPLACE_EXISTING);
                copied++;
            }
            if (!state.selectedRows.isEmpty()) {
                JsonObject root = new JsonObject();
                root.addProperty("type", "bettercode.selection");
                root.addProperty("count", state.selectedRows.size());
                JsonArray rows = new JsonArray();
                for (SelectedRow row : state.selectedRows) {
                    if (row == null) {
                        continue;
                    }
                    JsonObject o = new JsonObject();
                    o.addProperty("dimension", row.dimension());
                    o.addProperty("x", row.x());
                    o.addProperty("y", row.y());
                    o.addProperty("z", row.z());
                    rows.add(o);
                }
                root.add("rows", rows);
                Files.write(bundleDir.resolve("selection_rows.json"), root.toString().getBytes(StandardCharsets.UTF_8));
            }

            Path meta = bundleDir.resolve("publish_meta.json");
            OutputStreamWriter writer = null;
            try {
                writer = new OutputStreamWriter(Files.newOutputStream(meta), StandardCharsets.UTF_8);
                writer.write("{\n");
                writer.write("  \"postId\": \"" + esc(state.postId) + "\",\n");
                writer.write("  \"config\": \"" + esc(state.config) + "\",\n");
                writer.write("  \"generatedAt\": " + System.currentTimeMillis() + ",\n");
                writer.write("  \"copiedFiles\": " + copied + ",\n");
                writer.write("  \"selectedRows\": " + state.selectedRows.size() + "\n");
                writer.write("}\n");
            } finally {
                if (writer != null) {
                    writer.close();
                }
            }
            return Result.ok(bundleDir, copied);
        } catch (Exception e) {
            return Result.fail("PUBLISH_PREP_FAILED", e.getClass().getSimpleName() + ": " + String.valueOf(e.getMessage()));
        }
    }

    private static String safe(String value) {
        if (value == null || value.trim().isEmpty()) {
            return "x";
        }
        return value.replaceAll("[^a-zA-Z0-9._\\-]+", "_");
    }

    private static String esc(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
