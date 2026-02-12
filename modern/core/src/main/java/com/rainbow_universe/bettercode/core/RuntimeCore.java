package com.rainbow_universe.bettercode.core;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class RuntimeCore {
    private static final long MAX_DOWNLOAD_BYTES = 800L * 1024L;
    private static final long MAX_TOTAL_DOWNLOAD_BYTES = 2_500L * 1024L;
    private static final String DEFAULT_BASE_URL = "https://mldsl-hub.vercel.app";

    private final CoreLogger logger;
    private final String hubBaseUrl;
    private volatile PendingLoad pendingLoad;

    public RuntimeCore(CoreLogger logger) {
        this.logger = logger;
        this.hubBaseUrl = normalizeBaseUrl(System.getProperty("bettercode.hub.url"));
    }

    public RuntimeResult handleRun(String postId, String configKey, GameBridge bridge) {
        if (postId == null || postId.trim().isEmpty()) {
            return RuntimeResult.fail(RuntimeErrorCode.DOWNLOAD_FAILED, "postId is required");
        }
        String cfg = configKey == null || configKey.trim().isEmpty() ? "default" : configKey.trim();
        String safePostId = safePath(postId.trim());
        logger.info("printer-debug", "run requested postId=" + safePostId + " config=" + cfg + " dim=" + bridge.currentDimension());
        try {
            File outDir = bridge.runDirectory().resolve("mldsl_modules").resolve(safePostId).toFile();
            if (!outDir.exists() && !outDir.mkdirs()) {
                return RuntimeResult.fail(RuntimeErrorCode.DOWNLOAD_FAILED, "cannot create module dir: " + outDir.getPath());
            }
            PendingLoad pl = downloadAll(safePostId, outDir);
            pendingLoad = pl;
            bridge.sendChat("[publish-debug] downloaded files=" + pl.savedFiles.size() + " postId=" + safePostId);
            bridge.sendChat("[publish-debug] events=" + pl.events + " funcs=" + pl.funcs + " loops=" + pl.loops + " actions~=" + pl.actions);
            if (pl.planFile != null) {
                bridge.sendActionBar("plan.json downloaded. use /confirmload");
            }
            return RuntimeResult.ok("Saved " + pl.savedFiles.size() + " file(s) to " + outDir.getName());
        } catch (Exception e) {
            String reason = describeDownloadError(e);
            logger.error("publish-debug", "run download failed postId=" + safePostId + " reason=" + reason);
            return RuntimeResult.fail(RuntimeErrorCode.DOWNLOAD_FAILED, reason);
        }
    }

    public RuntimeResult handleConfirmLoad(GameBridge bridge) {
        logger.info("confirmload-debug", "confirm requested scoreboardLines=" + bridge.scoreboardLines().size());
        PendingLoad pl = pendingLoad;
        if (pl == null) {
            return RuntimeResult.fail(RuntimeErrorCode.NO_PENDING_PLAN, "No pending module load. Use /mldsl run <postId> first.");
        }
        if (pl.planFile == null || !pl.planFile.exists()) {
            return RuntimeResult.fail(RuntimeErrorCode.NO_PENDING_PLAN, "No plan.json in pending load.");
        }
        try {
            int entries = readPlanEntries(pl.planFile.toPath());
            bridge.sendChat("[confirmload-debug] plan=" + pl.planFile.getName() + " entries=" + entries + " postId=" + pl.postId);
            return RuntimeResult.fail(
                RuntimeErrorCode.UNIMPLEMENTED_PLATFORM_OPERATION,
                "Plan parsed (" + entries + " entries), but place pipeline parity is not wired yet."
            );
        } catch (Exception e) {
            return RuntimeResult.fail(RuntimeErrorCode.PLAN_PARSE_FAILED, "plan parse failed: " + e.getClass().getSimpleName());
        }
    }

    public RuntimeResult handlePublish(GameBridge bridge) {
        logger.info("publish-debug", "publish requested scoreboardLines=" + bridge.scoreboardLines().size());
        return RuntimeResult.fail(
            RuntimeErrorCode.UNIMPLEMENTED_PLATFORM_OPERATION,
            "Publish pipeline parity is not wired for this modern adapter yet."
        );
    }

    private PendingLoad downloadAll(String postId, File outDir) throws Exception {
        List<FileItem> list = fetchFilesList(postId);
        if (list.isEmpty()) {
            throw new IllegalStateException("empty_files_list");
        }
        long total = 0L;
        PendingLoad pl = new PendingLoad(postId);
        for (FileItem it : list) {
            if (it.name == null || it.name.trim().isEmpty()) {
                continue;
            }
            byte[] data = httpGet(buildDownloadUrl(postId, it.name));
            total += data.length;
            if (total > MAX_TOTAL_DOWNLOAD_BYTES) {
                throw new IllegalStateException("too_big_total");
            }
            File outFile = new File(outDir, safePath(it.name));
            FileOutputStream fos = null;
            try {
                fos = new FileOutputStream(outFile);
                fos.write(data);
            } finally {
                if (fos != null) {
                    fos.close();
                }
            }
            pl.savedFiles.add(outFile);
            if ("plan.json".equalsIgnoreCase(it.name)) {
                pl.planFile = outFile;
            }
            if (it.name.toLowerCase().endsWith(".mldsl")) {
                pl.addStats(new String(data, StandardCharsets.UTF_8));
            }
        }
        return pl;
    }

    private List<FileItem> fetchFilesList(String postId) throws Exception {
        String url = hubBaseUrl + "/api/post/" + encodePath(postId) + "/files";
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(12000);
            conn.setRequestProperty("Accept", "application/json");
            int code = conn.getResponseCode();
            if (code != 200) {
                throw new IllegalStateException("http_" + code);
            }
            InputStreamReader reader = null;
            try {
                reader = new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8);
                JsonElement root = new JsonParser().parse(reader);
                if (!root.isJsonObject()) {
                    throw new IllegalStateException("files_not_object");
                }
                JsonObject obj = root.getAsJsonObject();
                JsonArray files = obj.has("files") && obj.get("files").isJsonArray() ? obj.getAsJsonArray("files") : null;
                if (files == null) {
                    throw new IllegalStateException("files_missing");
                }
                List<FileItem> out = new ArrayList<FileItem>();
                for (JsonElement el : files) {
                    if (el == null || !el.isJsonObject()) {
                        continue;
                    }
                    JsonObject fo = el.getAsJsonObject();
                    String name = fo.has("name") ? fo.get("name").getAsString() : null;
                    long size = fo.has("size") ? fo.get("size").getAsLong() : 0L;
                    out.add(new FileItem(name, size));
                }
                return out;
            } finally {
                if (reader != null) {
                    reader.close();
                }
            }
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    private String buildDownloadUrl(String postId, String fileName) {
        return hubBaseUrl + "/api/post/" + encodePath(postId) + "/file?name=" + urlEncode(fileName);
    }

    private byte[] httpGet(String rawUrl) throws Exception {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(rawUrl).openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(15000);
            conn.setRequestProperty("Accept", "application/octet-stream");
            int code = conn.getResponseCode();
            if (code != 200) {
                throw new IllegalStateException("http_" + code);
            }
            InputStream in = null;
            ByteArrayOutputStream out = null;
            try {
                in = conn.getInputStream();
                out = new ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                long total = 0;
                while (true) {
                    int n = in.read(buf);
                    if (n < 0) {
                        break;
                    }
                    total += n;
                    if (total > MAX_DOWNLOAD_BYTES) {
                        throw new IllegalStateException("too_big");
                    }
                    out.write(buf, 0, n);
                }
                return out.toByteArray();
            } finally {
                if (in != null) {
                    in.close();
                }
                if (out != null) {
                    out.close();
                }
            }
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    private int readPlanEntries(Path path) throws Exception {
        InputStreamReader reader = null;
        try {
            reader = new InputStreamReader(Files.newInputStream(path), StandardCharsets.UTF_8);
            JsonElement root = new JsonParser().parse(reader);
            if (!root.isJsonObject()) {
                throw new IllegalStateException("plan_not_object");
            }
            JsonObject obj = root.getAsJsonObject();
            if (!obj.has("entries") || !obj.get("entries").isJsonArray()) {
                throw new IllegalStateException("entries_missing");
            }
            return obj.getAsJsonArray("entries").size();
        } finally {
            if (reader != null) {
                reader.close();
            }
        }
    }

    private static String describeDownloadError(Throwable err) {
        Throwable e = err;
        while (e != null && e.getCause() != null) {
            e = e.getCause();
        }
        if (e == null) {
            return "unknown_error";
        }
        if (e instanceof SocketTimeoutException) {
            return "timeout";
        }
        if (e instanceof UnknownHostException) {
            return "unknown_host";
        }
        String msg = e.getMessage() == null ? "" : e.getMessage().trim();
        if (msg.isEmpty()) {
            return e.getClass().getSimpleName();
        }
        if (msg.length() > 120) {
            msg = msg.substring(0, 120);
        }
        return msg.replace('\n', ' ').replace('\r', ' ');
    }

    private static String normalizeBaseUrl(String raw) {
        String value = raw == null ? "" : raw.trim();
        if (value.isEmpty()) {
            return DEFAULT_BASE_URL;
        }
        if (!value.startsWith("http://") && !value.startsWith("https://")) {
            value = "https://" + value;
        }
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        return value.isEmpty() ? DEFAULT_BASE_URL : value;
    }

    private static String safePath(String s) {
        if (s == null) {
            return "x";
        }
        return s.replaceAll("[^a-zA-Z0-9._\\-]+", "_");
    }

    private static String encodePath(String s) {
        String v = s == null ? "" : s.trim();
        return v.replace("..", "").replace("/", "").replace("\\", "");
    }

    private static String urlEncode(String s) {
        try {
            return java.net.URLEncoder.encode(s, "UTF-8");
        } catch (Exception e) {
            return "";
        }
    }

    private static final class FileItem {
        final String name;
        final long size;

        FileItem(String name, long size) {
            this.name = name;
            this.size = size;
        }
    }

    private static final class PendingLoad {
        final String postId;
        final List<File> savedFiles = new ArrayList<File>();
        File planFile;
        int events;
        int funcs;
        int loops;
        int actions;

        PendingLoad(String postId) {
            this.postId = postId;
        }

        void addStats(String code) {
            if (code == null) {
                return;
            }
            String[] lines = code.split("\\r?\\n");
            for (String raw : lines) {
                if (raw == null) {
                    continue;
                }
                String t = raw.trim();
                if (t.isEmpty() || t.startsWith("#") || t.startsWith("//")) {
                    continue;
                }
                String low = t.toLowerCase();
                if (low.contains("event(") || low.contains("событие(")) {
                    events++;
                    continue;
                }
                if (low.contains("func(") || low.contains("function(") || low.contains("def(") || low.contains("функц")) {
                    funcs++;
                    continue;
                }
                if (low.contains("loop(") || low.contains("цикл(")) {
                    loops++;
                    continue;
                }
                if (t.contains(".") || t.contains("=")) {
                    actions++;
                }
            }
        }
    }
}
