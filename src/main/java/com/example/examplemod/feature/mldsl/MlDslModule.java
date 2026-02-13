package com.example.examplemod.feature.mldsl;

import com.example.examplemod.feature.place.PlaceModule;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public final class MlDslModule
{
    private static final Gson GSON = new Gson();

    private final MlDslHost host;
    private final PlaceModule placeModule;

    public MlDslModule(MlDslHost host, PlaceModule placeModule)
    {
        this.host = host;
        this.placeModule = placeModule;
    }

    public void runCommand(MinecraftServer server, ICommandSender sender, String[] args)
    {
        if (args == null || args.length == 0)
        {
            host.setActionBar(false, "&cUsage: /mldsl run|check [path|postId] [--config id] [--start N]", 3500L);
            return;
        }

        String sub = args[0] == null ? "" : args[0].trim().toLowerCase();
        if (!"run".equals(sub) && !"check".equals(sub))
        {
            host.setActionBar(false, "&cUsage: /mldsl run|check [path|postId] [--config id] [--start N]", 3500L);
            return;
        }

        int start = 1;
        String path = null;
        String configId = null;
        StringBuilder quotedPath = null;
        char quoteChar = 0;
        for (int i = 1; i < args.length; i++)
        {
            String a = args[i];
            if (a == null)
            {
                continue;
            }
            if (quotedPath != null)
            {
                quotedPath.append(' ').append(a);
                if (a.endsWith(String.valueOf(quoteChar)))
                {
                    path = stripOuterQuotes(quotedPath.toString());
                    quotedPath = null;
                    quoteChar = 0;
                }
                continue;
            }
            if ("--start".equalsIgnoreCase(a) && i + 1 < args.length)
            {
                try
                {
                    start = Math.max(1, Integer.parseInt(args[i + 1]));
                }
                catch (NumberFormatException ignore)
                {
                    // ignore
                }
                i++;
                continue;
            }
            if ("--config".equalsIgnoreCase(a) && i + 1 < args.length)
            {
                String rawCfg = args[i + 1];
                if (rawCfg != null)
                {
                    configId = stripOuterQuotes(rawCfg.trim());
                }
                i++;
                continue;
            }
            if (path == null)
            {
                String t = a.trim();
                if (startsQuote(t) && !endsSameQuote(t))
                {
                    quotedPath = new StringBuilder(t);
                    quoteChar = t.charAt(0);
                    continue;
                }
                path = stripOuterQuotes(t);
            }
        }
        if (quotedPath != null && path == null)
        {
            // Unclosed quote: still try best-effort.
            path = stripOuterQuotes(quotedPath.toString());
        }

        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.mcDataDir == null)
        {
            host.setActionBar(false, "&cNo game dir", 2500L);
            return;
        }

        File file = resolvePlanPath(mc.mcDataDir, path);
        if (file == null || !file.exists())
        {
            if (path != null && looksLikePostId(path))
            {
                host.setActionBar(false, "&cModule not downloaded. Run /loadmodule " + path + " first.", 4000L);
                return;
            }
            host.setActionBar(false, "&cPlan not found: " + (file == null ? "null" : file.getPath()), 3500L);
            return;
        }
        if (configId != null && !configId.isEmpty())
        {
            // 1.12 flow prints local plan.json; config is selected on Hub download side.
            host.setActionBar(true, "&e--config ignored in local 1.12 run (download stage decides config)", 2800L);
        }

        runPlan(file, start, "check".equals(sub), server, sender);
    }

    public boolean runPlan(File file, int start, boolean checkOnly, MinecraftServer server, ICommandSender sender)
    {
        if (file == null || !file.exists())
        {
            host.setActionBar(false, "&cPlan not found: " + (file == null ? "null" : file.getPath()), 3500L);
            return false;
        }

        List<String> placeArgs;
        try
        {
            placeArgs = loadPlaceAdvancedArgs(file);
        }
        catch (Exception e)
        {
            String msg = e.getMessage();
            if (msg == null)
            {
                msg = "";
            }
            if (msg.length() > 120)
            {
                msg = msg.substring(0, 120);
            }
            host.setActionBar(false, "&cPlan parse error: " + e.getClass().getSimpleName() + " " + msg, 5000L);
            return false;
        }

        if (placeArgs.isEmpty())
        {
            host.setActionBar(false, "&cEmpty plan", 2500L);
            return false;
        }

        int totalEntries = countPlaceEntries(placeArgs);
        int skipEntries = Math.max(0, start - 1);
        if (skipEntries > 0)
        {
            placeArgs = skipPlaceEntries(placeArgs, skipEntries);
        }

        if (checkOnly)
        {
            host.setActionBar(true,
                "&a/mldsl check: entries=" + totalEntries + " start=" + start + " scanning...",
                3500L);
            placeModule.runPlaceAdvancedPlanCheckCommand(server, sender, placeArgs.toArray(new String[0]));
            return true;
        }

        host.setActionBar(true,
            "&aq/mldsl run: entries=" + totalEntries + " start=" + start + " queued=" + countPlaceEntries(placeArgs),
            3500L);

        // Plan-run uses the blue-glass code map allocator (±4Z, ±10Y), instead of the legacy -2X row placer.
        placeModule.runPlaceAdvancedPlanCommand(server, sender, placeArgs.toArray(new String[0]));
        return true;
    }

    private static File resolvePlanPath(File gameDir, String raw)
    {
        if (raw == null || raw.trim().isEmpty())
        {
            return new File(gameDir, "plan.json");
        }
        String s = raw.trim();
        s = stripOuterQuotes(s);

        if (looksLikePostId(s))
        {
            return new File(new File(new File(gameDir, "mldsl_modules"), safePath(s)), "plan.json");
        }

        File f = new File(s);
        if (f.isAbsolute())
        {
            return f;
        }
        return new File(gameDir, s);
    }

    private static boolean startsQuote(String s)
    {
        return s != null && (s.startsWith("\"") || s.startsWith("'"));
    }

    private static boolean endsSameQuote(String s)
    {
        if (s == null || s.length() < 2)
        {
            return false;
        }
        char q = s.charAt(0);
        return (q == '"' || q == '\'') && s.charAt(s.length() - 1) == q;
    }

    private static String stripOuterQuotes(String s)
    {
        if (s == null)
        {
            return null;
        }
        String t = s.trim();
        if (t.length() >= 2)
        {
            char first = t.charAt(0);
            char last = t.charAt(t.length() - 1);
            if ((first == '"' && last == '"') || (first == '\'' && last == '\''))
            {
                return t.substring(1, t.length() - 1).trim();
            }
        }
        return t;
    }

    private static boolean looksLikePostId(String s)
    {
        if (s == null)
        {
            return false;
        }
        String t = s.trim().toLowerCase();
        return t.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    }

    private static String safePath(String s)
    {
        if (s == null)
        {
            return "x";
        }
        return s.replaceAll("[^a-zA-Z0-9._\\-]+", "_");
    }

    private static List<String> loadPlaceAdvancedArgs(File file) throws Exception
    {
        try (InputStreamReader reader = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))
        {
            JsonElement root = new JsonParser().parse(reader);
            if (!root.isJsonObject())
            {
                throw new JsonParseException("root not object");
            }
            JsonObject obj = root.getAsJsonObject();

            // Option A: {"placeadvanced":[...tokens...]}
            if (obj.has("placeadvanced") && obj.get("placeadvanced").isJsonArray())
            {
                return readStringArray(obj.getAsJsonArray("placeadvanced"));
            }

            // Option B: {"entries":[{"block":"diamond_block","name":"вход","args":"no"}, ...]}
            if (obj.has("entries") && obj.get("entries").isJsonArray())
            {
                return buildPlaceAdvancedArgsFromEntries(obj.getAsJsonArray("entries"));
            }

            // Option C: {"steps":[...]} (alias of entries)
            if (obj.has("steps") && obj.get("steps").isJsonArray())
            {
                return buildPlaceAdvancedArgsFromEntries(obj.getAsJsonArray("steps"));
            }

            // Option D: {"rows":[ [...tokens...], [...tokens...] ]}
            if (obj.has("rows") && obj.get("rows").isJsonArray())
            {
                return buildPlaceAdvancedArgsFromRows(obj.getAsJsonArray("rows"));
            }

            throw new JsonParseException("missing placeadvanced/entries/steps/rows");
        }
    }

    private static List<String> buildPlaceAdvancedArgsFromEntries(JsonArray entries)
    {
        List<String> out = new ArrayList<>();
        for (JsonElement el : entries)
        {
            if (el == null || !el.isJsonObject())
            {
                continue;
            }
            JsonObject e = el.getAsJsonObject();
            String block = getString(e, "block");
            if (block == null || block.trim().isEmpty())
            {
                continue;
            }
            if ("newline".equalsIgnoreCase(block) || "row".equalsIgnoreCase(block))
            {
                out.add("newline");
                continue;
            }
            if ("air".equalsIgnoreCase(block) || "minecraft:air".equalsIgnoreCase(block))
            {
                out.add("air");
                continue;
            }
            if ("skip".equalsIgnoreCase(block))
            {
                out.add("skip");
                continue;
            }
            String name = getString(e, "name");
            if (name == null)
            {
                name = "";
            }

            String args = getString(e, "args");
            if (args == null && e.has("argsList") && e.get("argsList").isJsonArray())
            {
                List<String> parts = readStringArray(e.getAsJsonArray("argsList"));
                args = String.join(",", parts);
            }
            if (args == null)
            {
                args = "no";
            }

            out.add(quoteIfNeeded(block));
            out.add(quoteIfNeeded(name));
            out.add(quoteIfNeeded(args));
        }
        return out;
    }

    private static List<String> buildPlaceAdvancedArgsFromRows(JsonArray rows)
    {
        List<String> out = new ArrayList<>();
        if (rows == null)
        {
            return out;
        }
        boolean first = true;
        for (JsonElement el : rows)
        {
            if (el == null || !el.isJsonArray())
            {
                continue;
            }
            List<String> toks = readStringArray(el.getAsJsonArray());
            if (toks.isEmpty())
            {
                continue;
            }
            if (!first)
            {
                out.add("newline");
            }
            out.addAll(toks);
            first = false;
        }
        return out;
    }

    private static String getString(JsonObject o, String key)
    {
        if (o == null || key == null || !o.has(key))
        {
            return null;
        }
        JsonElement el = o.get(key);
        if (el == null || el.isJsonNull())
        {
            return null;
        }
        if (!el.isJsonPrimitive())
        {
            return null;
        }
        return el.getAsString();
    }

    private static List<String> readStringArray(JsonArray arr)
    {
        List<String> out = new ArrayList<>();
        if (arr == null)
        {
            return out;
        }
        for (JsonElement el : arr)
        {
            if (el == null || el.isJsonNull())
            {
                continue;
            }
            if (!el.isJsonPrimitive())
            {
                continue;
            }
            String s = el.getAsString();
            if (s != null)
            {
                out.add(s);
            }
        }
        return out;
    }

    private static String quoteIfNeeded(String s)
    {
        if (s == null)
        {
            return "\"\"";
        }
        String t = s.trim();
        // PlaceParser.splitArgsPreserveQuotes does not support escaping quotes (\"), so we must not generate it.
        // If a token contains quotes, replace them with ' to keep the plan runnable.
        if (t.contains("\""))
        {
            t = t.replace("\"", "'");
        }
        boolean need = t.isEmpty() || t.contains(" ");
        if (!need)
        {
            return t;
        }
        return "\"" + t + "\"";
    }

    private static int countPlaceEntries(List<String> args)
    {
        if (args == null)
        {
            return 0;
        }
        int count = 0;
        int i = 0;
        while (i < args.size())
        {
            String tok = args.get(i);
            if (tok != null && ("air".equalsIgnoreCase(tok) || "\"air\"".equalsIgnoreCase(tok) || "minecraft:air".equalsIgnoreCase(tok)))
            {
                count++;
                i++;
                continue;
            }
            if (i + 2 >= args.size())
            {
                break;
            }
            count++;
            i += 3;
        }
        return count;
    }

    private static List<String> skipPlaceEntries(List<String> args, int skip)
    {
        if (args == null || skip <= 0)
        {
            return args;
        }
        int i = 0;
        int skipped = 0;
        while (i < args.size() && skipped < skip)
        {
            String tok = args.get(i);
            if (tok != null && ("air".equalsIgnoreCase(tok) || "\"air\"".equalsIgnoreCase(tok) || "minecraft:air".equalsIgnoreCase(tok)))
            {
                i++;
            }
            else
            {
                i += 3;
            }
            skipped++;
        }
        if (i <= 0)
        {
            return args;
        }
        return new ArrayList<>(args.subList(Math.min(i, args.size()), args.size()));
    }
}
