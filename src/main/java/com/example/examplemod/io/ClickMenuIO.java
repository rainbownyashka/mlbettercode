package com.example.examplemod.io;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

public final class ClickMenuIO
{
    private ClickMenuIO()
    {
    }

    public static final class Loaded
    {
        public final Map<String, List<ItemStack>> menus = new HashMap<>();
        public final Map<String, String> locs = new HashMap<>();
    }

    public static void save(File target, Map<String, List<ItemStack>> snapshot, Map<String, String> locSnapshot)
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
                for (Map.Entry<String, List<ItemStack>> entry : snapshot.entrySet())
                {
                    String key = entry.getKey();
                    List<ItemStack> items = entry.getValue();
                    if (key == null || key.isEmpty() || items == null)
                    {
                        continue;
                    }
                    NBTTagCompound tag = new NBTTagCompound();
                    tag.setString("Key", key);
                    String loc = locSnapshot == null ? null : locSnapshot.get(key);
                    if (loc != null && !loc.isEmpty())
                    {
                        tag.setString("Loc", loc);
                    }
                    NBTTagList itemsTag = new NBTTagList();
                    for (ItemStack stack : items)
                    {
                        NBTTagCompound itemTag = new NBTTagCompound();
                        if (stack != null && !stack.isEmpty())
                        {
                            stack.writeToNBT(itemTag);
                        }
                        itemsTag.appendTag(itemTag);
                    }
                    tag.setTag("Items", itemsTag);
                    list.appendTag(tag);
                }
            }
            root.setTag("ClickMenus", list);
            CompressedStreamTools.write(root, target);
        }
        catch (Exception ignored)
        {
            // ignore
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
            if (root == null || !root.hasKey("ClickMenus", 9))
            {
                return out;
            }
            NBTTagList list = root.getTagList("ClickMenus", 10);
            for (int i = 0; i < list.tagCount(); i++)
            {
                NBTTagCompound tag = list.getCompoundTagAt(i);
                String key = tag.getString("Key");
                if (key == null || key.isEmpty())
                {
                    continue;
                }
                NBTTagList itemsTag = tag.getTagList("Items", 10);
                List<ItemStack> items = new ArrayList<>();
                for (int j = 0; j < itemsTag.tagCount(); j++)
                {
                    NBTTagCompound itemTag = itemsTag.getCompoundTagAt(j);
                    ItemStack stack = new ItemStack(itemTag);
                    items.add(stack.isEmpty() ? ItemStack.EMPTY : stack.copy());
                }
                out.menus.put(key, items);
                String loc = tag.getString("Loc");
                if (loc != null && !loc.isEmpty())
                {
                    out.locs.put(key, loc);
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

