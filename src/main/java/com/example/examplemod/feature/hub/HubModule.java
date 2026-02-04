package com.example.examplemod.feature.hub;

import com.example.examplemod.feature.place.PlaceModuleHost;
import net.minecraft.client.Minecraft;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public final class HubModule
{
    private static final long MAX_DOWNLOAD_BYTES = 800L * 1024L;

    private final PlaceModuleHost host;
    private final String baseUrl;

    public HubModule(PlaceModuleHost host)
    {
        this.host = host;
        this.baseUrl = "https://mldsl-hub.pages.dev";
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

        String url = buildDownloadUrl(postId, fileName);
        File outDir = new File(mc.mcDataDir, "mldsl_modules" + File.separator + safePath(postId));
        File outFile = new File(outDir, fileName == null || fileName.trim().isEmpty() ? "module.mldsl" : safePath(fileName));

        host.setActionBar(true, "&e/loadmodule: downloading...", 2500L);
        final String showName = outFile.getName();
        new Thread(() -> {
            try
            {
                byte[] data = httpGet(url);
                if (data == null)
                {
                    scheduleChat("&c/loadmodule: download failed");
                    return;
                }
                if (!outDir.exists() && !outDir.mkdirs())
                {
                    scheduleChat("&c/loadmodule: can't create dir: " + outDir.getPath());
                    return;
                }
                try (FileOutputStream fos = new FileOutputStream(outFile))
                {
                    fos.write(data);
                }
                scheduleChat("&a/loadmodule: saved &f" + showName + " &a(" + data.length + " B)");
            }
            catch (Exception e)
            {
                scheduleChat("&c/loadmodule: " + e.getClass().getSimpleName());
            }
        }, "mldsl-hub-download").start();
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
        mc.addScheduledTask(() -> host.debugChat(msg));
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
}
