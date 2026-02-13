package com.example.examplemod.io;

import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

public final class CodeCacheIO
{
    public static final class Loaded
    {
        public final Map<String, String> blocksByKey = new HashMap<>();
        public final Map<String, String[]> signsByKey = new HashMap<>();
        public final Map<String, String[]> signsByDimPos = new HashMap<>();
        public final Map<String, Long> entryToSignByKey = new HashMap<>();
    }

    private CodeCacheIO()
    {
    }

    public static void save(File target, Map<String, String> blocksByKey, Map<String, String[]> signsByKey,
        Map<String, String[]> signsByDimPos, Map<String, Long> entryToSignByKey)
    {
        if (target == null)
        {
            return;
        }
        try
        {
            NBTTagCompound root = new NBTTagCompound();

            // Blocks
            NBTTagList blocks = new NBTTagList();
            if (blocksByKey != null && !blocksByKey.isEmpty())
            {
                for (Map.Entry<String, String> e : blocksByKey.entrySet())
                {
                    String k = e.getKey();
                    String v = e.getValue();
                    if (k == null || k.isEmpty() || v == null || v.isEmpty())
                    {
                        continue;
                    }
                    NBTTagCompound tag = new NBTTagCompound();
                    tag.setString("K", k);
                    tag.setString("V", v);
                    blocks.appendTag(tag);
                }
            }
            root.setTag("Blocks", blocks);

            // Signs (4 lines)
            NBTTagList signs = new NBTTagList();
            if (signsByKey != null && !signsByKey.isEmpty())
            {
                for (Map.Entry<String, String[]> e : signsByKey.entrySet())
                {
                    String k = e.getKey();
                    String[] lines = e.getValue();
                    if (k == null || k.isEmpty() || lines == null)
                    {
                        continue;
                    }
                    NBTTagCompound tag = new NBTTagCompound();
                    tag.setString("K", k);
                    for (int i = 0; i < 4; i++)
                    {
                        String v = i < lines.length ? lines[i] : "";
                        tag.setString("L" + i, v == null ? "" : v);
                    }
                    signs.appendTag(tag);
                }
            }
            root.setTag("Signs", signs);

            // Signs by dim:pos (persistent fallback when scope key isn't available)
            NBTTagList dimPosSigns = new NBTTagList();
            if (signsByDimPos != null && !signsByDimPos.isEmpty())
            {
                for (Map.Entry<String, String[]> e : signsByDimPos.entrySet())
                {
                    String k = e.getKey();
                    String[] lines = e.getValue();
                    if (k == null || k.isEmpty() || lines == null)
                    {
                        continue;
                    }
                    NBTTagCompound tag = new NBTTagCompound();
                    tag.setString("K", k);
                    for (int i = 0; i < 4; i++)
                    {
                        String v = i < lines.length ? lines[i] : "";
                        tag.setString("L" + i, v == null ? "" : v);
                    }
                    dimPosSigns.appendTag(tag);
                }
            }
            root.setTag("SignsByDimPos", dimPosSigns);

            // Entry -> Sign position mapping
            NBTTagList entrySigns = new NBTTagList();
            if (entryToSignByKey != null && !entryToSignByKey.isEmpty())
            {
                for (Map.Entry<String, Long> e : entryToSignByKey.entrySet())
                {
                    String k = e.getKey();
                    Long v = e.getValue();
                    if (k == null || k.isEmpty() || v == null)
                    {
                        continue;
                    }
                    NBTTagCompound tag = new NBTTagCompound();
                    tag.setString("K", k);
                    tag.setLong("V", v.longValue());
                    entrySigns.appendTag(tag);
                }
            }
            root.setTag("EntrySigns", entrySigns);

            CompressedStreamTools.write(root, target);
        }
        catch (Exception e)
        {
            System.err.println("[BetterCode] CodeCacheIO.save failed: " + e);
        }
    }

    public static Loaded load(File target)
    {
        Loaded out = new Loaded();
        if (target == null || !target.exists())
        {
            return out;
        }
        try
        {
            NBTTagCompound root = CompressedStreamTools.read(target);
            if (root == null)
            {
                return out;
            }

            if (root.hasKey("Blocks", 9))
            {
                NBTTagList blocks = root.getTagList("Blocks", 10);
                for (int i = 0; i < blocks.tagCount(); i++)
                {
                    NBTTagCompound tag = blocks.getCompoundTagAt(i);
                    String k = tag.getString("K");
                    String v = tag.getString("V");
                    if (k != null && !k.isEmpty() && v != null && !v.isEmpty())
                    {
                        out.blocksByKey.put(k, v);
                    }
                }
            }

            if (root.hasKey("Signs", 9))
            {
                NBTTagList signs = root.getTagList("Signs", 10);
                for (int i = 0; i < signs.tagCount(); i++)
                {
                    NBTTagCompound tag = signs.getCompoundTagAt(i);
                    String k = tag.getString("K");
                    if (k == null || k.isEmpty())
                    {
                        continue;
                    }
                    String[] lines = new String[]{"", "", "", ""};
                    for (int li = 0; li < 4; li++)
                    {
                        String v = tag.getString("L" + li);
                        lines[li] = v == null ? "" : v;
                    }
                    out.signsByKey.put(k, lines);
                }
            }

            if (root.hasKey("SignsByDimPos", 9))
            {
                NBTTagList signs = root.getTagList("SignsByDimPos", 10);
                for (int i = 0; i < signs.tagCount(); i++)
                {
                    NBTTagCompound tag = signs.getCompoundTagAt(i);
                    String k = tag.getString("K");
                    if (k == null || k.isEmpty())
                    {
                        continue;
                    }
                    String[] lines = new String[]{"", "", "", ""};
                    for (int li = 0; li < 4; li++)
                    {
                        String v = tag.getString("L" + li);
                        lines[li] = v == null ? "" : v;
                    }
                    out.signsByDimPos.put(k, lines);
                }
            }

            if (root.hasKey("EntrySigns", 9))
            {
                NBTTagList entrySigns = root.getTagList("EntrySigns", 10);
                for (int i = 0; i < entrySigns.tagCount(); i++)
                {
                    NBTTagCompound tag = entrySigns.getCompoundTagAt(i);
                    String k = tag.getString("K");
                    long v = tag.getLong("V");
                    if (k != null && !k.isEmpty())
                    {
                        out.entryToSignByKey.put(k, v);
                    }
                }
            }
        }
        catch (Exception e)
        {
            System.err.println("[BetterCode] CodeCacheIO.load failed: " + e);
        }
        return out;
    }
}

