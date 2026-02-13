package com.rainbow_universe.bettercode.core;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonParseException;
import com.rainbow_universe.bettercode.core.place.PlaceArgSpec;
import com.rainbow_universe.bettercode.core.place.PlaceArgsParser;
import com.rainbow_universe.bettercode.core.place.PlaceEntrySpec;
import com.rainbow_universe.bettercode.core.place.PlacePlanBuilder;
import com.rainbow_universe.bettercode.core.place.PlaceRuntimeEntry;
import com.rainbow_universe.bettercode.core.place.PlaceRuntimeState;
import com.rainbow_universe.bettercode.core.settings.SettingsProvider;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

public final class RuntimeCore {
    private static final long MAX_DOWNLOAD_BYTES = 800L * 1024L;
    private static final long MAX_TOTAL_DOWNLOAD_BYTES = 2_500L * 1024L;
    private static final String HUB_BASE_URL_PRIMARY = "https://mldsl-hub.vercel.app";
    private static final String HUB_BASE_URL_MIRROR = "https://mldsl-hub.duckdns.org";

    private final CoreLogger logger;
    private final SettingsProvider settings;
    private volatile PendingLoad pendingLoad;
    private volatile PendingExecution pendingExecution;

    public RuntimeCore(CoreLogger logger) {
        this(logger, new SettingsProvider() {
            @Override
            public String getString(String key, String fallback) {
                return fallback;
            }

            @Override
            public int getInt(String key, int fallback) {
                return fallback;
            }

            @Override
            public boolean getBoolean(String key, boolean fallback) {
                return fallback;
            }
        });
    }

    public RuntimeCore(CoreLogger logger, SettingsProvider settings) {
        this.logger = logger;
        this.settings = settings;
    }

    public RuntimeResult handleRun(String postId, String configKey, GameBridge bridge) {
        return handleLoadModule(postId, null, configKey, bridge);
    }

    public RuntimeResult handleRunLocal(String rawPath, boolean checkOnly, GameBridge bridge) {
        if (rawPath == null || rawPath.trim().isEmpty()) {
            return RuntimeResult.fail(RuntimeErrorCode.PARSE_SCHEMA_MISMATCH, "local path is required");
        }
        Path resolved = resolveLocalPath(rawPath.trim(), bridge.runDirectory());
        if (!Files.exists(resolved)) {
            return RuntimeResult.fail(RuntimeErrorCode.PARSE_SCHEMA_MISMATCH, "local file not found: " + resolved);
        }
        if (!Files.isRegularFile(resolved)) {
            return RuntimeResult.fail(RuntimeErrorCode.PARSE_SCHEMA_MISMATCH, "local path is not a file: " + resolved);
        }
        try {
            long size = Files.size(resolved);
            if (size > MAX_DOWNLOAD_BYTES) {
                return RuntimeResult.fail(RuntimeErrorCode.PARSE_SCHEMA_MISMATCH, "local file too big: " + size + " bytes");
            }
            String ext = lowerExt(resolved.getFileName().toString());
            if ("mldsl".equals(ext)) {
                return RuntimeResult.fail(RuntimeErrorCode.PARSE_SCHEMA_MISMATCH, "local .mldsl compile is not implemented in modern runtime");
            }
            if (!"json".equals(ext)) {
                return RuntimeResult.fail(RuntimeErrorCode.PARSE_SCHEMA_MISMATCH, "unsupported local extension: ." + ext);
            }
            List<PlaceOp> ops = loadPlaceOps(resolved);
            if (checkOnly) {
                return checkPlaceOps(ops, bridge, "local", "-", "default", resolved.toString());
            }
            PendingLoad pl = new PendingLoad("local", "default", "local", resolved.toString());
            pl.planFile = resolved.toFile();
            pl.savedFiles.add(resolved.toFile());
            pendingLoad = pl;
            bridge.sendChat("[confirmload-debug] local plan staged path=" + resolved + " entries=" + ops.size());
            bridge.sendActionBar("local plan ready. use /confirmload");
            return RuntimeResult.ok("Local plan staged: " + resolved.getFileName());
        } catch (Exception e) {
            String reason = describeDownloadError(e);
            return RuntimeResult.fail(RuntimeErrorCode.PARSE_SCHEMA_MISMATCH, "local parse failed: " + reason);
        }
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
            PendingLoad pl;
            if (fileName == null || fileName.trim().isEmpty()) {
                try {
                    pl = downloadAll(safePostId, cfg, outDir);
                } catch (IllegalStateException listErr) {
                    String msg = listErr.getMessage() == null ? "" : listErr.getMessage();
                    if (!msg.contains("empty_files_list")) {
                        throw listErr;
                    }
                    logger.warn("publish-debug", "files list empty for postId=" + safePostId + ", fallback to default /file");
                    pl = downloadDefault(safePostId, cfg, outDir);
                }
            } else {
                pl = downloadOne(safePostId, fileName.trim(), cfg, outDir);
            }
            pendingLoad = pl;
            bridge.sendChat("[publish-debug] downloaded files=" + pl.savedFiles.size() + " postId=" + safePostId + " config=" + pl.configKey);
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
            List<PlaceOp> ops = loadPlaceOps(pl.planFile.toPath());
            int entries = ops.size();
            logPlaceArgsSummary(ops);
            bridge.sendChat("[confirmload-debug] source=" + pl.source
                + " plan=" + pl.planFile.getName()
                + " entries=" + entries
                + " postId=" + pl.postId
                + " config=" + pl.configKey
                + " path=" + (pl.localPath == null ? "-" : pl.localPath));
            if (entries <= 0) {
                return RuntimeResult.fail(RuntimeErrorCode.PLAN_PARSE_FAILED, "plan has no place entries");
            }
            List<PlaceEntrySpec> specs = PlacePlanBuilder.fromOps(ops);
            pendingExecution = new PendingExecution(specs, pl.source, pl.postId, pl.configKey, pl.localPath);
            bridge.sendActionBar("confirmload: runtime queued " + entries + " step(s)");
            return RuntimeResult.ok("Plan queued: " + entries + " place operation(s)");
        } catch (Exception e) {
            String full = stackTraceToString(e);
            logger.error("confirmload-debug", "confirm exception stacktrace:\n" + full);
            String reason = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            if (isCommandExecutionFailure(reason)) {
                return RuntimeResult.fail(RuntimeErrorCode.COMMAND_EXECUTION_FAILED, reason + " | " + full);
            }
            return RuntimeResult.fail(RuntimeErrorCode.PLAN_PARSE_FAILED, "plan parse failed: " + reason + " | " + full);
        }
    }

    public void handleClientTick(GameBridge bridge, long nowMs) {
        PendingExecution exec = pendingExecution;
        if (exec == null || bridge == null) {
            return;
        }
        if (nowMs < exec.nextStepAtMs) {
            return;
        }
        if (!exec.state.isActive()) {
            bridge.sendActionBar("print done: " + exec.state.executedCount() + " step(s)");
            bridge.sendChat("[printer-debug] runtime done source=" + exec.source + " postId=" + exec.postId + " config=" + exec.config);
            pendingExecution = null;
            return;
        }
        PlaceRuntimeEntry stepEntry = exec.state.currentOrNext();
        if (stepEntry == null) {
            pendingExecution = null;
            return;
        }
        PlaceOp op = stepEntry.isPause()
            ? PlaceOp.air()
            : PlaceOp.block(stepEntry.blockId(), stepEntry.name(), stepEntry.argsRaw());
        PlaceExecResult step = bridge.executePlacePlan(singleton(op), false);
        if (!step.ok()) {
            String code = step.errorCode() == null ? "exec_failed" : step.errorCode();
            String msg = step.errorMessage() == null ? "" : step.errorMessage();
            logger.error("printer-debug", "tick step failed source=" + exec.source
                + " step=" + exec.state.executedCount()
                + " errorCode=" + code
                + " reason=" + msg
                + " path=" + (exec.path == null ? "-" : exec.path));
            bridge.sendChat("[printer-debug] runtime failed step=" + exec.state.executedCount() + " code=" + code + " reason=" + msg);
            bridge.sendActionBar("print failed at step " + exec.state.executedCount());
            pendingExecution = null;
            return;
        }
        exec.state.markCurrentDone();
        int delay = settings.getInt("printer.stepDelayMs", 80);
        if (delay < 0) {
            delay = 0;
        }
        exec.nextStepAtMs = nowMs + delay;
        logger.info("printer-debug", "tick step ok source=" + exec.source + " step=" + exec.state.executedCount() + "/" + exec.state.totalCount());
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
                writer.write("  \"config\": \"" + escapeJson(pl.configKey) + "\",\n");
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

    private PendingLoad downloadAll(String postId, String configKey, File outDir) throws Exception {
        List<FileItem> list = fetchFilesList(postId, configKey);
        if (list.isEmpty()) {
            throw new IllegalStateException("empty_files_list");
        }
        long total = 0L;
        PendingLoad pl = new PendingLoad(postId, configKey);
        for (FileItem it : list) {
            if (it.name == null || it.name.trim().isEmpty()) {
                continue;
            }
            byte[] data = httpGet(buildDownloadUrl(postId, it.name, configKey));
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

    private PendingLoad downloadOne(String postId, String fileName, String configKey, File outDir) throws Exception {
        byte[] data = httpGet(buildDownloadUrl(postId, fileName, configKey));
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
        PendingLoad pl = new PendingLoad(postId, configKey);
        pl.savedFiles.add(outFile);
        if ("plan.json".equalsIgnoreCase(fileName)) {
            pl.planFile = outFile;
        }
        if (outName.toLowerCase().endsWith(".mldsl")) {
            pl.addStats(new String(data, StandardCharsets.UTF_8));
        }
        return pl;
    }

    private PendingLoad downloadDefault(String postId, String configKey, File outDir) throws Exception {
        byte[] data = httpGet(buildDefaultDownloadUrl(postId, configKey));
        String outName = "module.mldsl";
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
        PendingLoad pl = new PendingLoad(postId, configKey);
        pl.savedFiles.add(outFile);
        pl.addStats(new String(data, StandardCharsets.UTF_8));
        return pl;
    }

    private List<FileItem> fetchFilesList(String postId, String configKey) throws Exception {
        String hubBaseUrl = resolveHubBaseUrl();
        int connectTimeout = settings.getInt("network.connectTimeoutMs", 8000);
        int readTimeout = settings.getInt("network.readTimeoutMs", 12000);
        String url = hubBaseUrl + "/api/post/" + encodePath(postId) + "/files" + buildConfigQuery(configKey);
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(connectTimeout);
            conn.setReadTimeout(readTimeout);
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

    private String buildDownloadUrl(String postId, String fileName, String configKey) {
        String hubBaseUrl = resolveHubBaseUrl();
        return hubBaseUrl + "/api/post/" + encodePath(postId) + "/file?name=" + urlEncode(fileName) + buildConfigQuery(configKey, true);
    }

    private String buildDefaultDownloadUrl(String postId, String configKey) {
        String hubBaseUrl = resolveHubBaseUrl();
        return hubBaseUrl + "/api/post/" + encodePath(postId) + "/file" + buildConfigQuery(configKey);
    }

    private static String buildConfigQuery(String configKey) {
        if (configKey == null || configKey.trim().isEmpty()) {
            return "";
        }
        return "?config=" + urlEncode(configKey.trim());
    }

    private static String buildConfigQuery(String configKey, boolean hasQuery) {
        if (configKey == null || configKey.trim().isEmpty()) {
            return "";
        }
        return (hasQuery ? "&" : "?") + "config=" + urlEncode(configKey.trim());
    }

    private byte[] httpGet(String rawUrl) throws Exception {
        int connectTimeout = settings.getInt("network.connectTimeoutMs", 8000);
        int readTimeout = settings.getInt("network.readTimeoutMs", 15000);
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(rawUrl).openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(connectTimeout);
            conn.setReadTimeout(readTimeout);
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

    private RuntimeResult checkPlaceOps(List<PlaceOp> ops, GameBridge bridge, String source, String postId, String config, String path) {
        if (ops == null || ops.isEmpty()) {
            return RuntimeResult.fail(RuntimeErrorCode.PLAN_PARSE_FAILED, "plan has no place entries");
        }
        logPlaceArgsSummary(ops);
        PlaceExecResult check = bridge.executePlacePlan(ops, true);
        if (!check.ok()) {
            String code = check.errorCode() == null ? "check_failed" : check.errorCode();
            String msg = check.errorMessage() == null ? "" : check.errorMessage();
            logger.error("printer-debug", "check failed source=" + source
                + " postId=" + postId
                + " config=" + config
                + " path=" + path
                + " failedAt=" + check.failedAt()
                + " code=" + code
                + " reason=" + msg
                + " supportsPlacePlanExecution=" + bridge.supportsPlacePlanExecution());
            RuntimeErrorCode mapped = code.toUpperCase().contains("UNIMPLEMENTED")
                ? RuntimeErrorCode.UNIMPLEMENTED_PLATFORM_OPERATION
                : RuntimeErrorCode.COMMAND_EXECUTION_FAILED;
            return RuntimeResult.fail(mapped, "check failedAt=" + check.failedAt() + " code=" + code + " reason=" + msg);
        }
        return RuntimeResult.ok("check ok: " + check.executed() + " place operation(s)");
    }

    private void logPlaceArgsSummary(List<PlaceOp> ops) {
        if (ops == null || ops.isEmpty()) {
            return;
        }
        List<PlaceEntrySpec> plan = PlacePlanBuilder.fromOps(ops);
        int entries = plan.size();
        int pauses = 0;
        int parsedArgs = 0;
        int parseFailures = 0;
        int parsedItemSpecs = 0;
        for (PlaceEntrySpec entry : plan) {
            if (entry == null) {
                continue;
            }
            if (entry.isPause()) {
                pauses++;
                continue;
            }
            String args = entry.argsRaw();
            if (args == null || args.trim().isEmpty() || "no".equalsIgnoreCase(args.trim())) {
                continue;
            }
            try {
                parsedArgs += entry.args().size();
                for (PlaceArgSpec spec : entry.args()) {
                    if (spec != null && spec.itemSpec() != null) {
                        parsedItemSpecs++;
                    }
                }
            } catch (Exception ex) {
                parseFailures++;
            }
        }
        logger.info("printer-debug", "place_args_summary parsed=" + parsedArgs
            + " itemSpecs=" + parsedItemSpecs
            + " parseFailures=" + parseFailures
            + " entries=" + entries
            + " pauses=" + pauses);
    }

    private List<PlaceOp> loadPlaceOps(Path path) throws Exception {
        InputStreamReader reader = null;
        try {
            reader = new InputStreamReader(Files.newInputStream(path), StandardCharsets.UTF_8);
            JsonElement root = new JsonParser().parse(reader);
            if (!root.isJsonObject()) {
                throw new JsonParseException("plan_not_object");
            }
            JsonObject obj = root.getAsJsonObject();

            if (obj.has("placeadvanced") && obj.get("placeadvanced").isJsonArray()) {
                return parsePlaceOpsFromTokens(readStringArray(obj.getAsJsonArray("placeadvanced")));
            }
            if (obj.has("entries") && obj.get("entries").isJsonArray()) {
                return buildPlaceOpsFromEntries(obj.getAsJsonArray("entries"));
            }
            if (obj.has("steps") && obj.get("steps").isJsonArray()) {
                return buildPlaceOpsFromEntries(obj.getAsJsonArray("steps"));
            }
            if (obj.has("rows") && obj.get("rows").isJsonArray()) {
                return parsePlaceOpsFromTokens(buildPlaceAdvancedArgsFromRows(obj.getAsJsonArray("rows")));
            }
            throw new JsonParseException("missing placeadvanced/entries/steps/rows");
        } finally {
            if (reader != null) {
                reader.close();
            }
        }
    }

    private List<PlaceOp> buildPlaceOpsFromEntries(JsonArray entries) {
        List<PlaceOp> out = new ArrayList<PlaceOp>();
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
                continue;
            }
            if ("air".equalsIgnoreCase(block) || "minecraft:air".equalsIgnoreCase(block)) {
                out.add(PlaceOp.air());
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
            out.add(PlaceOp.block(block, name, args));
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

    private List<PlaceOp> parsePlaceOpsFromTokens(List<String> args) {
        List<PlaceOp> out = new ArrayList<PlaceOp>();
        if (args == null) {
            return out;
        }
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
                out.add(PlaceOp.air());
                i++;
                continue;
            }
            if (i + 2 >= args.size()) {
                throw new IllegalStateException("plan_parse:bad_placeadvanced_triplet at index=" + i);
            }
            String block = args.get(i);
            String name = args.get(i + 1);
            String arg = args.get(i + 2);
            out.add(PlaceOp.block(block, name, arg));
            i += 3;
        }
        return out;
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

    private static Path resolveLocalPath(String rawPath, Path runDir) {
        Path p = Paths.get(rawPath);
        if (!p.isAbsolute()) {
            p = runDir.resolve(rawPath);
        }
        return p.normalize();
    }

    private static String lowerExt(String name) {
        if (name == null) {
            return "";
        }
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot + 1 >= name.length()) {
            return "";
        }
        return name.substring(dot + 1).toLowerCase();
    }

    private static boolean isCommandExecutionFailure(String reason) {
        if (reason == null) {
            return false;
        }
        String low = reason.toLowerCase();
        return low.contains("command_exec:");
    }

    private static String stackTraceToString(Throwable err) {
        if (err == null) {
            return "null";
        }
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        err.printStackTrace(pw);
        pw.flush();
        return sw.toString();
    }

    private static String normalizeBaseUrl(String raw) {
        String value = raw == null ? "" : raw.trim();
        if (value.isEmpty()) {
            return HUB_BASE_URL_PRIMARY;
        }
        if (!value.startsWith("http://") && !value.startsWith("https://")) {
            value = "https://" + value;
        }
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        return value.isEmpty() ? HUB_BASE_URL_PRIMARY : value;
    }

    private String resolveHubBaseUrl() {
        boolean mirror = settings.getBoolean("hub.useMirror", false);
        return mirror ? HUB_BASE_URL_MIRROR : HUB_BASE_URL_PRIMARY;
    }

    private static List<PlaceOp> singleton(PlaceOp op) {
        List<PlaceOp> out = new ArrayList<PlaceOp>();
        out.add(op);
        return out;
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
        final String configKey;
        final String source;
        final String localPath;
        final List<File> savedFiles = new ArrayList<File>();
        File planFile;
        int events;
        int funcs;
        int loops;
        int actions;

        PendingLoad(String postId, String configKey) {
            this(postId, configKey, "hub", null);
        }

        PendingLoad(String postId, String configKey, String source, String localPath) {
            this.postId = postId;
            this.configKey = configKey == null || configKey.trim().isEmpty() ? "default" : configKey.trim();
            this.source = source == null || source.trim().isEmpty() ? "hub" : source.trim();
            this.localPath = localPath;
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

    private static final class PendingExecution {
        final PlaceRuntimeState state = new PlaceRuntimeState();
        final String source;
        final String postId;
        final String config;
        final String path;
        long nextStepAtMs;

        PendingExecution(List<PlaceEntrySpec> specs, String source, String postId, String config, String path) {
            this.source = source == null ? "unknown" : source;
            this.postId = postId == null ? "-" : postId;
            this.config = config == null ? "default" : config;
            this.path = path;
            this.state.loadFromSpecs(specs);
            this.nextStepAtMs = System.currentTimeMillis();
        }
    }
}
