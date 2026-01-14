package com.example.examplemod.io;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.math.BlockPos;

public final class CodeBlueGlassIO
{
    private CodeBlueGlassIO()
    {
    }

    public static void save(File target, Map<String, BlockPos> snapshot)
    {
        if (target == null)
        {
            return;
        }
        try
        {
            NBTTagCompound root = new NBTTagCompound();
            NBTTagList list = new NBTTagList();
            if (snapshot != null && !snapshot.isEmpty())
            {
                for (Map.Entry<String, BlockPos> entry : snapshot.entrySet())
                {
                    String key = entry.getKey();
                    BlockPos pos = entry.getValue();
                    if (key == null || key.isEmpty() || pos == null)
                    {
                        continue;
                    }
                    NBTTagCompound tag = new NBTTagCompound();
                    tag.setString("Key", key);
                    tag.setInteger("X", pos.getX());
                    tag.setInteger("Y", pos.getY());
                    tag.setInteger("Z", pos.getZ());
                    list.appendTag(tag);
                }
            }
            root.setTag("Blue", list);
            CompressedStreamTools.write(root, target);
        }
        catch (Exception ignored)
        {
            // ignore
        }
    }

    public static Map<String, BlockPos> load(File target)
    {
        Map<String, BlockPos> out = new HashMap<>();
        if (target == null || !target.exists())
        {
            return out;
        }
        try
        {
            NBTTagCompound root = CompressedStreamTools.read(target);
            if (root == null || !root.hasKey("Blue", 9))
            {
                return out;
            }
            NBTTagList list = root.getTagList("Blue", 10);
            for (int i = 0; i < list.tagCount(); i++)
            {
                NBTTagCompound tag = list.getCompoundTagAt(i);
                String key = tag.getString("Key");
                int x = tag.hasKey("X") ? tag.getInteger("X") : 0;
                int y = tag.hasKey("Y") ? tag.getInteger("Y") : 0;
                int z = tag.hasKey("Z") ? tag.getInteger("Z") : 0;
                if (key != null && !key.isEmpty())
                {
                    out.put(key, new BlockPos(x, y, z));
                }
            }
        }
        catch (Exception ignored)
        {
            // ignore
        }
        return out;
    }
}

