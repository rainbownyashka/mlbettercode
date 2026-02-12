package com.example.examplemod.feature.hub;

import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

final class DonorTierResolver
{
    private static final List<String> TIER_ORDER = Arrays.asList(
        "gamer", "skilled", "expert", "hero", "king", "legend"
    );

    private static final Map<String, Set<String>> IDS_BY_TIER = loadRules();

    private DonorTierResolver() { }

    // [TAG:donor-tier-by-file] Resolve donor tier by presence of action IDs in downloaded files.
    static Result resolveFromFiles(List<File> files)
    {
        if (files == null || files.isEmpty())
        {
            return Result.empty();
        }

        Set<String> allTokens = new LinkedHashSet<>();
        for (File f : files)
        {
            if (f == null || !f.exists() || !f.isFile())
            {
                continue;
            }
            String name = f.getName().toLowerCase(Locale.ROOT);
            if (!(name.endsWith(".mldsl") || name.endsWith(".json") || name.endsWith(".txt") || name.endsWith(".md")))
            {
                continue;
            }
            try
            {
                byte[] data = Files.readAllBytes(f.toPath());
                String text = new String(data, StandardCharsets.UTF_8);
                extractNumericTokens(text, allTokens);
            }
            catch (Exception ignore)
            {
                // Ignore unreadable files; result still deterministic from readable ones.
            }
        }

        if (allTokens.isEmpty())
        {
            return Result.empty();
        }

        int bestRank = -1;
        Set<String> matched = new LinkedHashSet<>();
        String bestTier = "player";

        for (int i = 0; i < TIER_ORDER.size(); i++)
        {
            String tier = TIER_ORDER.get(i);
            Set<String> ids = IDS_BY_TIER.get(tier);
            if (ids == null || ids.isEmpty())
            {
                continue;
            }
            boolean hasAny = false;
            for (String id : ids)
            {
                if (allTokens.contains(id))
                {
                    hasAny = true;
                    matched.add(id);
                }
            }
            if (hasAny && i >= bestRank)
            {
                bestRank = i;
                bestTier = tier;
            }
        }

        return new Result(bestTier, matched);
    }

    private static void extractNumericTokens(String text, Set<String> out)
    {
        if (text == null || text.isEmpty())
        {
            return;
        }
        int n = text.length();
        int i = 0;
        while (i < n)
        {
            char c = text.charAt(i);
            if (c < '0' || c > '9')
            {
                i++;
                continue;
            }
            int j = i;
            while (j < n)
            {
                char d = text.charAt(j);
                if (d < '0' || d > '9')
                {
                    break;
                }
                j++;
            }
            int len = j - i;
            if (len > 0 && len <= 6)
            {
                out.add(text.substring(i, j));
            }
            i = j + 1;
        }
    }

    private static Map<String, Set<String>> loadRules()
    {
        Map<String, Set<String>> map = new LinkedHashMap<>();
        for (String t : TIER_ORDER)
        {
            map.put(t, new LinkedHashSet<>());
        }

        try (InputStream in = DonorTierResolver.class.getResourceAsStream("/donaterequire.txt"))
        {
            if (in == null)
            {
                return map;
            }
            List<String> lines = Arrays.asList(new String(readAll(in), StandardCharsets.UTF_8).split("\\r?\\n"));
            String currentTier = null;
            for (String raw : lines)
            {
                String line = raw == null ? "" : raw.trim();
                if (line.isEmpty())
                {
                    continue;
                }
                String low = line.toLowerCase(Locale.ROOT);
                if (low.endsWith(" can"))
                {
                    String t = low.substring(0, low.length() - 4).trim();
                    currentTier = map.containsKey(t) ? t : null;
                    continue;
                }
                if (currentTier == null)
                {
                    continue;
                }
                if (isDigits(line))
                {
                    map.get(currentTier).add(line);
                }
            }
        }
        catch (Exception ignore)
        {
            return map;
        }

        return map;
    }

    private static boolean isDigits(String s)
    {
        if (s == null || s.isEmpty())
        {
            return false;
        }
        for (int i = 0; i < s.length(); i++)
        {
            char c = s.charAt(i);
            if (c < '0' || c > '9')
            {
                return false;
            }
        }
        return true;
    }

    private static byte[] readAll(InputStream in) throws Exception
    {
        byte[] buf = new byte[8192];
        int n;
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        while ((n = in.read(buf)) > 0)
        {
            out.write(buf, 0, n);
        }
        return out.toByteArray();
    }

    static final class Result
    {
        final String tier;
        final Set<String> ids;

        Result(String tier, Set<String> ids)
        {
            this.tier = tier == null ? "player" : tier;
            this.ids = ids == null ? Collections.<String>emptySet() : ids;
        }

        static Result empty()
        {
            return new Result("player", Collections.<String>emptySet());
        }

        String idsPreview(int max)
        {
            if (ids.isEmpty())
            {
                return "none";
            }
            List<String> sorted = new ArrayList<>(ids);
            Collections.sort(sorted);
            if (sorted.size() <= max)
            {
                return String.join(",", sorted);
            }
            return String.join(",", sorted.subList(0, max)) + ",...";
        }
    }
}
