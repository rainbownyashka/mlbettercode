package com.example.examplemod.io;

import com.example.examplemod.model.ChestCache;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.math.BlockPos;

public final class ChestIdCacheIO
{
    private ChestIdCacheIO()
    {
    }

    public static void save(File target, Map<String, ChestCache> snapshot)
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
                for (Map.Entry<String, ChestCache> entry : snapshot.entrySet())
                {
                    String id = entry.getKey();
                    ChestCache cache = entry.getValue();
                    if (id == null || id.isEmpty() || cache == null)
                    {
                        continue;
                    }
                    NBTTagCompound tag = new NBTTagCompound();
                    tag.setString("Id", id);
                    tag.setString("Label", cache.label == null ? "" : cache.label);
                    tag.setInteger("Dim", cache.dim);
                    tag.setInteger("X", cache.pos == null ? 0 : cache.pos.getX());
                    tag.setInteger("Y", cache.pos == null ? 0 : cache.pos.getY());
                    tag.setInteger("Z", cache.pos == null ? 0 : cache.pos.getZ());
                    NBTTagList items = new NBTTagList();
                    for (ItemStack stack : cache.items)
                    {
                        NBTTagCompound itemTag = new NBTTagCompound();
                        if (stack != null && !stack.isEmpty())
                        {
                            stack.writeToNBT(itemTag);
                        }
                        items.appendTag(itemTag);
                    }
                    tag.setTag("Items", items);
                    list.appendTag(tag);
                }
            }
            root.setTag("Caches", list);
            CompressedStreamTools.write(root, target);
        }
        catch (Exception ignored)
        {
            // ignore
        }
    }

    public static Map<String, ChestCache> load(File target)
    {
        Map<String, ChestCache> out = new HashMap<>();
        if (target == null || !target.exists())
        {
            return out;
        }
        try
        {
            NBTTagCompound root = CompressedStreamTools.read(target);
            if (root == null || !root.hasKey("Caches", 9))
            {
                return out;
            }
            NBTTagList list = root.getTagList("Caches", 10);
            for (int i = 0; i < list.tagCount(); i++)
            {
                NBTTagCompound tag = list.getCompoundTagAt(i);
                String id = tag.getString("Id");
                if (id == null || id.isEmpty())
                {
                    continue;
                }
                String label = tag.getString("Label");
                int dim = tag.hasKey("Dim") ? tag.getInteger("Dim") : 0;
                int x = tag.hasKey("X") ? tag.getInteger("X") : 0;
                int y = tag.hasKey("Y") ? tag.getInteger("Y") : 0;
                int z = tag.hasKey("Z") ? tag.getInteger("Z") : 0;
                NBTTagList itemsTag = tag.getTagList("Items", 10);
                List<ItemStack> items = new ArrayList<>();
                for (int j = 0; j < itemsTag.tagCount(); j++)
                {
                    NBTTagCompound itemTag = itemsTag.getCompoundTagAt(j);
                    ItemStack stack = new ItemStack(itemTag);
                    items.add(stack.isEmpty() ? ItemStack.EMPTY : stack.copy());
                }
                BlockPos pos = new BlockPos(x, y, z);
                out.put(id, new ChestCache(dim, pos, items, System.currentTimeMillis(), label));
            }
        }
        catch (Exception ignored)
        {
            // ignore
        }
        return out;
    }
}
