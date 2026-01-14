package com.example.examplemod.io;

import com.example.examplemod.model.ShulkerHolo;
import java.io.File;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.math.BlockPos;

public final class ShulkerHoloIO
{
    private ShulkerHoloIO()
    {
    }

    public static void save(File target, Map<String, ShulkerHolo> snapshot)
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
                for (ShulkerHolo holo : snapshot.values())
                {
                    if (holo == null || holo.pos == null)
                    {
                        continue;
                    }
                    NBTTagCompound tag = new NBTTagCompound();
                    tag.setInteger("Dim", holo.dim);
                    tag.setInteger("X", holo.pos.getX());
                    tag.setInteger("Y", holo.pos.getY());
                    tag.setInteger("Z", holo.pos.getZ());
                    tag.setInteger("Color", holo.color);
                    tag.setString("Text", holo.text == null ? "" : holo.text);
                    list.appendTag(tag);
                }
            }
            root.setTag("Holos", list);
            CompressedStreamTools.write(root, target);
        }
        catch (Exception ignored)
        {
            // ignore
        }
    }

    public static Map<String, ShulkerHolo> load(File target)
    {
        Map<String, ShulkerHolo> out = new HashMap<>();
        if (target == null || !target.exists())
        {
            return out;
        }
        try
        {
            NBTTagCompound root = CompressedStreamTools.read(target);
            if (root == null || !root.hasKey("Holos", 9))
            {
                return out;
            }
            NBTTagList list = root.getTagList("Holos", 10);
            for (int i = 0; i < list.tagCount(); i++)
            {
                NBTTagCompound tag = list.getCompoundTagAt(i);
                int dim = tag.hasKey("Dim") ? tag.getInteger("Dim") : 0;
                int x = tag.hasKey("X") ? tag.getInteger("X") : 0;
                int y = tag.hasKey("Y") ? tag.getInteger("Y") : 0;
                int z = tag.hasKey("Z") ? tag.getInteger("Z") : 0;
                int color = tag.hasKey("Color") ? tag.getInteger("Color") : 0xFFFFFF;
                String text = tag.getString("Text");
                BlockPos pos = new BlockPos(x, y, z);
                String key = dim + ":" + pos.toString();
                out.put(key, new ShulkerHolo(dim, pos, text, color));
            }
        }
        catch (Exception ignored)
        {
            // ignore
        }
        return out;
    }
}

