package com.example.examplemod.feature.hub;

import com.example.examplemod.feature.place.PlaceModuleHost;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public final class HubModule
{
    private static final long MAX_DOWNLOAD_BYTES = 800L * 1024L;
    private static final long MAX_TOTAL_DOWNLOAD_BYTES = 2_500L * 1024L;

    private final PlaceModuleHost host;
    private final String baseUrl;
    private volatile String confirmKeyHint = null;
    private volatile PendingLoad pending = null;

    public HubModule(PlaceModuleHost host)
    {
        this.host = host;
        this.baseUrl = "https://mldsl-hub.pages.dev";
    }

    public void setConfirmKeyHint(String hint)
    {
        this.confirmKeyHint = hint;
    }

    public void runCommand(MinecraftServer server, ICommandSender sender, String[] args)
    {
        if (args == null || args.length == 0)
        {
            host.setActionBar(false, "&cUsage: /loadmodule <postId> [file]", 3500L);
            return;
        }

        String postId = args[0] == null ? "" : args[0].trim();
        if (postId.isEmpty())
        {
            host.setActionBar(false, "&cUsage: /loadmodule <postId> [file]", 3500L);
            return;
        }

        String fileName = null;
        if (args.length >= 2 && args[1] != null && !args[1].trim().isEmpty())
        {
            fileName = stripQuotes(args[1].trim());
        }

        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.mcDataDir == null)
        {
            host.setActionBar(false, "&cNo game dir", 2500L);
            return;
        }

        File outDir = new File(mc.mcDataDir, "mldsl_modules" + File.separator + safePath(postId));
        host.setActionBar(true, "&e/loadmodule: downloading...", 2500L);
        final String postIdF = postId;
        final String fileNameF = fileName;
        final File outDirF = outDir;
        final AtomicBoolean finished = new AtomicBoolean(false);
        Thread worker = new Thread(() -> {
            try
            {
                if (!outDirF.exists() && !outDirF.mkdirs())
                {
                    scheduleChat("&c/loadmodule: can't create dir: " + outDirF.getPath());
                    return;
                }

                PendingLoad pl = fileNameF == null
                    ? downloadAll(postIdF, outDirF)
                    : downloadOne(postIdF, fileNameF, outDirF);
                if (pl == null)
                {
                    scheduleChat("&c/loadmodule: download failed");
                    return;
                }
                pending = pl;
                scheduleChat("&a/loadmodule: saved &f" + pl.savedFiles.size() + " &afile(s) into &f" + outDirF.getName());
                scheduleChat("&bСтатистика: &fсобытий=" + pl.events + " &fфункций=" + pl.funcs + " &fциклов=" + pl.loops + " &fдействий≈" + pl.actions);
                if (pl.planFile != null)
                {
                    String hint = confirmKeyHint == null || confirmKeyHint.trim().isEmpty()
                        ? "/confirmload"
                        : "/confirmload (&f" + confirmKeyHint + "&e)";
                    scheduleChat("&eПодтвердить печать plan.json: &a" + hint);
                }
            }
            catch (Exception e)
            {
                scheduleChat("&c/loadmodule: " + e.getClass().getSimpleName());
            }
            finally
            {
                finished.set(true);
            }
        }, "mldsl-hub-download");
        worker.setDaemon(true);
        worker.start();

        Thread watchdog = new Thread(() -> {
            try
            {
                Thread.sleep(30000L);
            }
            catch (InterruptedException ignore) { }
            if (!finished.get())
            {
                scheduleChat("&c/loadmodule: timeout (30s), retry later.");
                host.setActionBar(false, "&c/loadmodule timeout", 3000L);
                try
                {
                    worker.interrupt();
                }
                catch (Exception ignore) { }
            }
        }, "mldsl-hub-download-watchdog");
        watchdog.setDaemon(true);
        watchdog.start();
    }

    public void runConfirmCommand(MinecraftServer server, ICommandSender sender, String[] args)
    {
        PendingLoad pl = pending;
        if (pl == null)
        {
            host.setActionBar(false, "&cNo pending load. Use /loadmodule first.", 3000L);
            return;
        }
        if (pl.planFile == null || !pl.planFile.exists())
        {
            host.setActionBar(false, "&cNo plan.json downloaded for this post.", 3500L);
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.player == null)
        {
            host.setActionBar(false, "&cNo player", 2000L);
            return;
        }
        String abs = pl.planFile.getAbsolutePath().replace("\\", "\\\\");
        String cmd = "/mldsl run \"" + abs + "\"";
        host.setActionBar(true, "&aRunning: " + cmd, 2500L);
        mc.addScheduledTask(() -> mc.player.sendChatMessage(cmd));
    }

    private PendingLoad downloadAll(String postId, File outDir) throws Exception
    {
        List<FileItem> list = fetchFilesList(postId);
        if (list.isEmpty())
        {
            // fallback: download default single file
            return downloadOne(postId, null, outDir);
        }
        long total = 0L;
        PendingLoad pl = new PendingLoad(postId);
        for (FileItem it : list)
        {
            String name = it.name;
            if (name == null || name.trim().isEmpty())
            {
                continue;
            }
            byte[] data = httpGet(buildDownloadUrl(postId, name));
            if (data == null)
            {
                continue;
            }
            total += data.length;
            if (total > MAX_TOTAL_DOWNLOAD_BYTES)
            {
                throw new IllegalStateException("too_big_total");
            }
            File outFile = new File(outDir, safePath(name));
            try (FileOutputStream fos = new FileOutputStream(outFile))
            {
                fos.write(data);
            }
            pl.savedFiles.add(outFile);
            if ("plan.json".equalsIgnoreCase(name))
            {
                pl.planFile = outFile;
            }
            if (name.toLowerCase().endsWith(".mldsl"))
            {
                String text = new String(data, StandardCharsets.UTF_8);
                pl.addStats(text);
            }
        }
        return pl.savedFiles.isEmpty() ? null : pl;
    }

    private PendingLoad downloadOne(String postId, String fileName, File outDir) throws Exception
    {
        String url = buildDownloadUrl(postId, fileName);
        byte[] data = httpGet(url);
        if (data == null)
        {
            return null;
        }
        String outName = fileName == null || fileName.trim().isEmpty() ? "module.mldsl" : safePath(fileName);
        File outFile = new File(outDir, outName);
        try (FileOutputStream fos = new FileOutputStream(outFile))
        {
            fos.write(data);
        }
        PendingLoad pl = new PendingLoad(postId);
        pl.savedFiles.add(outFile);
        if ("plan.json".equalsIgnoreCase(fileName))
        {
            pl.planFile = outFile;
        }
        if (outName.toLowerCase().endsWith(".mldsl"))
        {
            pl.addStats(new String(data, StandardCharsets.UTF_8));
        }
        return pl;
    }

    private List<FileItem> fetchFilesList(String postId) throws Exception
    {
        String url = baseUrl + "/api/post/" + encodePath(postId) + "/files";
        HttpURLConnection conn = null;
        try
        {
            conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(10000);
            conn.setRequestProperty("Accept", "application/json");
            int code = conn.getResponseCode();
            if (code != 200)
            {
                return new ArrayList<>();
            }
            try (InputStreamReader reader = new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))
            {
                JsonElement root = new JsonParser().parse(reader);
                if (!root.isJsonObject())
                {
                    return new ArrayList<>();
                }
                JsonObject obj = root.getAsJsonObject();
                JsonArray files = obj.has("files") && obj.get("files").isJsonArray() ? obj.getAsJsonArray("files") : null;
                if (files == null)
                {
                    return new ArrayList<>();
                }
                List<FileItem> out = new ArrayList<>();
                for (JsonElement el : files)
                {
                    if (el == null || !el.isJsonObject())
                    {
                        continue;
                    }
                    JsonObject fo = el.getAsJsonObject();
                    String name = fo.has("name") ? fo.get("name").getAsString() : null;
                    long size = fo.has("size") ? fo.get("size").getAsLong() : 0L;
                    out.add(new FileItem(name, size));
                }
                return out;
            }
        }
        finally
        {
            if (conn != null)
            {
                conn.disconnect();
            }
        }
    }

    private String buildDownloadUrl(String postId, String fileName)
    {
        String base = baseUrl + "/api/post/" + encodePath(postId) + "/file";
        if (fileName == null || fileName.trim().isEmpty())
        {
            return base;
        }
        return base + "?name=" + urlEncode(fileName);
    }

    private static byte[] httpGet(String rawUrl) throws Exception
    {
        HttpURLConnection conn = null;
        try
        {
            URL url = new URL(rawUrl);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(15000);
            conn.setRequestProperty("Accept", "text/plain");
            int code = conn.getResponseCode();
            if (code != 200)
            {
                return null;
            }
            try (InputStream in = conn.getInputStream(); ByteArrayOutputStream out = new ByteArrayOutputStream())
            {
                byte[] buf = new byte[8192];
                long total = 0;
                while (true)
                {
                    int n = in.read(buf);
                    if (n < 0)
                    {
                        break;
                    }
                    total += n;
                    if (total > MAX_DOWNLOAD_BYTES)
                    {
                        throw new IllegalStateException("too_big");
                    }
                    out.write(buf, 0, n);
                }
                return out.toByteArray();
            }
        }
        finally
        {
            if (conn != null)
            {
                conn.disconnect();
            }
        }
    }

    private void scheduleChat(String msg)
    {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null)
        {
            return;
        }
        mc.addScheduledTask(() -> {
            if (mc.player != null)
            {
                String clean = msg == null ? "" : msg.replace('&', '\u00a7');
                mc.player.sendMessage(new net.minecraft.util.text.TextComponentString("[BetterCode] " + clean));
            }
        });
    }

    private static String stripQuotes(String s)
    {
        if (s == null)
        {
            return null;
        }
        String t = s.trim();
        if ((t.startsWith("\"") && t.endsWith("\"")) || (t.startsWith("'") && t.endsWith("'")))
        {
            return t.substring(1, t.length() - 1);
        }
        return t;
    }

    private static String urlEncode(String s)
    {
        try
        {
            return URLEncoder.encode(s, "UTF-8");
        }
        catch (Exception e)
        {
            return "";
        }
    }

    private static String encodePath(String s)
    {
        // Minimal safe encoding for path segment.
        String v = s == null ? "" : s.trim();
        return v.replace("..", "").replace("/", "").replace("\\", "");
    }

    private static String safePath(String s)
    {
        if (s == null)
        {
            return "x";
        }
        return s.replaceAll("[^a-zA-Z0-9._\\-]+", "_");
    }

    private static final class FileItem
    {
        final String name;
        final long size;

        FileItem(String name, long size)
        {
            this.name = name;
            this.size = size;
        }
    }

    private static final class PendingLoad
    {
        final String postId;
        final List<File> savedFiles = new ArrayList<>();
        File planFile = null;
        int events = 0;
        int funcs = 0;
        int loops = 0;
        int actions = 0;

        PendingLoad(String postId)
        {
            this.postId = postId;
        }

        void addStats(String code)
        {
            if (code == null)
            {
                return;
            }
            String[] lines = code.split("\\r?\\n");
            for (String raw : lines)
            {
                if (raw == null)
                {
                    continue;
                }
                String t = raw.trim();
                if (t.isEmpty() || t.startsWith("#") || t.startsWith("//"))
                {
                    continue;
                }
                String low = t.toLowerCase();
                if (low.contains("event(") || low.contains("событие("))
                {
                    events++;
                    continue;
                }
                if (low.contains("func(") || low.contains("function(") || low.contains("def(") || low.contains("функц"))
                {
                    funcs++;
                    continue;
                }
                if (low.contains("loop(") || low.contains("цикл("))
                {
                    loops++;
                    continue;
                }
                // very rough heuristic for "actions"
                if (t.contains(".") || t.contains("="))
                {
                    actions++;
                }
            }
        }
    }
}
