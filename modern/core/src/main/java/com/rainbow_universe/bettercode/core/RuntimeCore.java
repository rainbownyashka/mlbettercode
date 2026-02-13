package com.rainbow_universe.bettercode.core;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonParseException;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
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
        return handleLoadModule(postId, null, configKey, bridge);
    }

    public RuntimeResult handleLoadModule(String postId, String fileName, String configKey, GameBridge bridge) {
        if (postId == null || postId.trim().isEmpty()) {
            return RuntimeResult.fail(RuntimeErrorCode.DOWNLOAD_FAILED, "postId is required");
        }
        String cfg = configKey == null || configKey.trim().isEmpty() ? "default" : configKey.trim();
        String safePostId = safePath(postId.trim());
        ScoreboardContext ctx = ScoreboardParser.parse(bridge.scoreboardLines());
        logger.info("printer-debug",
            "run requested postId=" + safePostId
                + " file=" + (fileName == null ? "all" : fileName)
                + " config=" + cfg
                + " dim=" + bridge.currentDimension()
                + " tier=" + ctx.detectedTier()
                + " editorLike=" + ctx.editorLike()
        );
        try {
            File outDir = bridge.runDirectory().resolve("mldsl_modules").resolve(safePostId).toFile();
            if (!outDir.exists() && !outDir.mkdirs()) {
                return RuntimeResult.fail(RuntimeErrorCode.DOWNLOAD_FAILED, "cannot create module dir: " + outDir.getPath());
            }
            PendingLoad pl = (fileName == null || fileName.trim().isEmpty())
                ? downloadAll(safePostId, outDir)
                : downloadOne(safePostId, fileName.trim(), outDir);
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
            List<String> placeArgs = loadPlaceAdvancedArgs(pl.planFile.toPath());
            int entries = countPlaceEntries(placeArgs);
            bridge.sendChat("[confirmload-debug] plan=" + pl.planFile.getName() + " entries=" + entries + " postId=" + pl.postId);
            if (entries <= 0) {
                return RuntimeResult.fail(RuntimeErrorCode.PLAN_PARSE_FAILED, "plan has no place entries");
            }
            int executed = executePlaceAdvanced(bridge, placeArgs);
            bridge.sendActionBar("confirmload: queued " + executed + " placeadvanced command(s)");
            return RuntimeResult.ok("Plan applied via /placeadvanced commands: " + executed);
        } catch (Exception e) {
            return RuntimeResult.fail(RuntimeErrorCode.PLAN_PARSE_FAILED, "plan parse failed: " + describeDownloadError(e));
        }
    }

    public RuntimeResult handlePublish(GameBridge bridge) {
        ScoreboardContext ctx = ScoreboardParser.parse(bridge.scoreboardLines());
        logger.info("publish-debug",
            "publish requested scoreboardLines=" + ctx.lineCount() + " tier=" + ctx.detectedTier() + " editorLike=" + ctx.editorLike());

        PendingLoad pl = pendingLoad;
        if (pl == null || pl.savedFiles.isEmpty()) {
            return RuntimeResult.fail(RuntimeErrorCode.NO_PENDING_PLAN, "No pending module data. Use /loadmodule first.");
        }
        try {
            Path publishRoot = bridge.runDirectory().resolve("mldsl_publish");
            Files.createDirectories(publishRoot);
            String bundleName = "bundle_" + safePath(pl.postId) + "_" + System.currentTimeMillis();
            Path bundleDir = publishRoot.resolve(bundleName);
            Files.createDirectories(bundleDir);

            int copied = 0;
            for (File src : pl.savedFiles) {
                if (src == null || !src.exists()) {
                    continue;
                }
                Path dst = bundleDir.resolve(safePath(src.getName()));
                Files.copy(src.toPath(), dst, StandardCopyOption.REPLACE_EXISTING);
                copied++;
            }
            Path meta = bundleDir.resolve("publish_meta.json");
            OutputStreamWriter writer = null;
            try {
                writer = new OutputStreamWriter(Files.newOutputStream(meta), StandardCharsets.UTF_8);
                writer.write("{\n");
                writer.write("  \"postId\": \"" + escapeJson(pl.postId) + "\",\n");
                writer.write("  \"generatedAt\": " + System.currentTimeMillis() + ",\n");
                writer.write("  \"copiedFiles\": " + copied + ",\n");
                writer.write("  \"events\": " + pl.events + ",\n");
                writer.write("  \"funcs\": " + pl.funcs + ",\n");
                writer.write("  \"loops\": " + pl.loops + ",\n");
                writer.write("  \"actions\": " + pl.actions + "\n");
                writer.write("}\n");
            } finally {
                if (writer != null) {
                    writer.close();
                }
            }

            bridge.sendChat("[publish-debug] prepared bundle=" + bundleDir.getFileName() + " files=" + copied);
            bridge.sendActionBar("publish bundle ready: " + bundleDir.getFileName());
            return RuntimeResult.ok("Publish bundle prepared at: " + bundleDir.toAbsolutePath());
        } catch (Exception e) {
            String reason = describeDownloadError(e);
            logger.error("publish-debug", "publish prep failed reason=" + reason);
            return RuntimeResult.fail(RuntimeErrorCode.PUBLISH_PREP_FAILED, reason);
        }
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

    private PendingLoad downloadOne(String postId, String fileName, File outDir) throws Exception {
        byte[] data = httpGet(buildDownloadUrl(postId, fileName));
        String outName = safePath(fileName);
        File outFile = new File(outDir, outName);
        FileOutputStream fos = null;
        try {
            fos = new FileOutputStream(outFile);
            fos.write(data);
        } finally {
            if (fos != null) {
                fos.close();
            }
        }
        PendingLoad pl = new PendingLoad(postId);
        pl.savedFiles.add(outFile);
        if ("plan.json".equalsIgnoreCase(fileName)) {
            pl.planFile = outFile;
        }
        if (outName.toLowerCase().endsWith(".mldsl")) {
            pl.addStats(new String(data, StandardCharsets.UTF_8));
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

    private List<String> loadPlaceAdvancedArgs(Path path) throws Exception {
        InputStreamReader reader = null;
        try {
            reader = new InputStreamReader(Files.newInputStream(path), StandardCharsets.UTF_8);
            JsonElement root = new JsonParser().parse(reader);
            if (!root.isJsonObject()) {
                throw new JsonParseException("plan_not_object");
            }
            JsonObject obj = root.getAsJsonObject();

            if (obj.has("placeadvanced") && obj.get("placeadvanced").isJsonArray()) {
                return readStringArray(obj.getAsJsonArray("placeadvanced"));
            }
            if (obj.has("entries") && obj.get("entries").isJsonArray()) {
                return buildPlaceAdvancedArgsFromEntries(obj.getAsJsonArray("entries"));
            }
            if (obj.has("steps") && obj.get("steps").isJsonArray()) {
                return buildPlaceAdvancedArgsFromEntries(obj.getAsJsonArray("steps"));
            }
            if (obj.has("rows") && obj.get("rows").isJsonArray()) {
                return buildPlaceAdvancedArgsFromRows(obj.getAsJsonArray("rows"));
            }
            throw new JsonParseException("missing placeadvanced/entries/steps/rows");
        } finally {
            if (reader != null) {
                reader.close();
            }
        }
    }

    private int executePlaceAdvanced(GameBridge bridge, List<String> args) {
        int i = 0;
        int executed = 0;
        while (i < args.size()) {
            String tok = args.get(i);
            if (tok == null) {
                i++;
                continue;
            }
            String low = tok.trim().toLowerCase();
            if ("newline".equals(low)) {
                i++;
                continue;
            }
            if ("air".equals(low) || "minecraft:air".equals(low)) {
                if (!bridge.executeClientCommand("placeadvanced air")) {
                    throw new IllegalStateException("placeadvanced_air_failed");
                }
                executed++;
                i++;
                continue;
            }
            if (i + 2 >= args.size()) {
                throw new IllegalStateException("bad_placeadvanced_triplet");
            }
            String block = args.get(i);
            String name = args.get(i + 1);
            String arg = args.get(i + 2);
            String cmd = "placeadvanced " + quoteIfNeeded(block) + " " + quoteIfNeeded(name) + " " + quoteIfNeeded(arg);
            if (!bridge.executeClientCommand(cmd)) {
                throw new IllegalStateException("placeadvanced_exec_failed");
            }
            executed++;
            i += 3;
        }
        if (executed <= 0) {
            throw new IllegalStateException("no_commands_executed");
        }
        return executed;
    }

    private static List<String> buildPlaceAdvancedArgsFromEntries(JsonArray entries) {
        List<String> out = new ArrayList<String>();
        for (JsonElement el : entries) {
            if (el == null || !el.isJsonObject()) {
                continue;
            }
            JsonObject e = el.getAsJsonObject();
            String block = getString(e, "block");
            if (block == null || block.trim().isEmpty()) {
                continue;
            }
            if ("newline".equalsIgnoreCase(block) || "row".equalsIgnoreCase(block)) {
                out.add("newline");
                continue;
            }
            if ("air".equalsIgnoreCase(block) || "minecraft:air".equalsIgnoreCase(block)) {
                out.add("air");
                continue;
            }
            String name = getString(e, "name");
            if (name == null) {
                name = "";
            }
            String args = getString(e, "args");
            if (args == null && e.has("argsList") && e.get("argsList").isJsonArray()) {
                List<String> parts = readStringArray(e.getAsJsonArray("argsList"));
                args = joinWithComma(parts);
            }
            if (args == null) {
                args = "no";
            }
            out.add(block);
            out.add(name);
            out.add(args);
        }
        return out;
    }

    private static List<String> buildPlaceAdvancedArgsFromRows(JsonArray rows) {
        List<String> out = new ArrayList<String>();
        if (rows == null) {
            return out;
        }
        boolean first = true;
        for (JsonElement el : rows) {
            if (el == null || !el.isJsonArray()) {
                continue;
            }
            List<String> toks = readStringArray(el.getAsJsonArray());
            if (toks.isEmpty()) {
                continue;
            }
            if (!first) {
                out.add("newline");
            }
            out.addAll(toks);
            first = false;
        }
        return out;
    }

    private static int countPlaceEntries(List<String> args) {
        if (args == null) {
            return 0;
        }
        int count = 0;
        int i = 0;
        while (i < args.size()) {
            String tok = args.get(i);
            if (tok == null) {
                i++;
                continue;
            }
            String low = tok.trim().toLowerCase();
            if ("newline".equals(low)) {
                i++;
                continue;
            }
            if ("air".equals(low) || "minecraft:air".equals(low)) {
                count++;
                i++;
                continue;
            }
            if (i + 2 >= args.size()) {
                break;
            }
            count++;
            i += 3;
        }
        return count;
    }

    private static List<String> readStringArray(JsonArray arr) {
        List<String> out = new ArrayList<String>();
        if (arr == null) {
            return out;
        }
        for (JsonElement el : arr) {
            if (el == null || el.isJsonNull()) {
                continue;
            }
            if (!el.isJsonPrimitive()) {
                continue;
            }
            String s = el.getAsString();
            if (s != null) {
                out.add(s);
            }
        }
        return out;
    }

    private static String getString(JsonObject o, String key) {
        if (o == null || key == null || !o.has(key)) {
            return null;
        }
        JsonElement el = o.get(key);
        if (el == null || el.isJsonNull()) {
            return null;
        }
        if (!el.isJsonPrimitive()) {
            return null;
        }
        return el.getAsString();
    }

    private static String quoteIfNeeded(String s) {
        if (s == null) {
            return "\"\"";
        }
        String t = s.trim();
        if (t.contains("\"")) {
            t = t.replace("\"", "'");
        }
        boolean need = t.isEmpty() || t.contains(" ");
        if (!need) {
            return t;
        }
        return "\"" + t + "\"";
    }

    private static String joinWithComma(List<String> parts) {
        if (parts == null || parts.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(parts.get(i));
        }
        return sb.toString();
    }

    private static String escapeJson(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.replace("\\", "\\\\").replace("\"", "\\\"");
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
