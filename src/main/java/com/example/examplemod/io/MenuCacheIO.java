package com.example.examplemod.io;

import com.example.examplemod.model.CachedMenu;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

public final class MenuCacheIO
{
    private MenuCacheIO()
    {
    }

    public static final class Loaded
    {
        public final Map<String, CachedMenu> menus = new HashMap<>();
        public CachedMenu custom;
    }

    public static void save(File target, Map<String, CachedMenu> snapshot, CachedMenu customSnapshot)
    {
        if (target == null)
        {
            return;
        }
        try
        {
            NBTTagCompound root = new NBTTagCompound();
            root.setTag("Menus", writeMenus(snapshot));
            if (customSnapshot != null)
            {
                root.setTag("Custom", writeMenu(customSnapshot));
            }
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
            if (root == null)
            {
                return out;
            }
            if (root.hasKey("Menus", 9))
            {
                NBTTagList list = root.getTagList("Menus", 10);
                for (int i = 0; i < list.tagCount(); i++)
                {
                    NBTTagCompound tag = list.getCompoundTagAt(i);
                    String key = tag.getString("Key");
                    if (key == null || key.isEmpty())
                    {
                        continue;
                    }
                    out.menus.put(key, readMenu(tag));
                }
            }
            if (root.hasKey("Custom", 10))
            {
                out.custom = readMenu(root.getCompoundTag("Custom"));
            }
        }
        catch (Exception ignored)
        {
            // ignore
        }
        return out;
    }

    private static NBTTagList writeMenus(Map<String, CachedMenu> snapshot)
    {
        NBTTagList list = new NBTTagList();
        if (snapshot == null || snapshot.isEmpty())
        {
            return list;
        }
        for (Map.Entry<String, CachedMenu> entry : snapshot.entrySet())
        {
            String key = entry.getKey();
            CachedMenu menu = entry.getValue();
            if (key == null || key.isEmpty() || menu == null)
            {
                continue;
            }
            NBTTagCompound tag = writeMenu(menu);
            tag.setString("Key", key);
            list.appendTag(tag);
        }
        return list;
    }

    private static NBTTagCompound writeMenu(CachedMenu menu)
    {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString("Title", menu.title == null ? "" : menu.title);
        tag.setInteger("Size", menu.size);
        tag.setString("Hash", menu.hash == null ? "" : menu.hash);
        NBTTagList items = new NBTTagList();
        for (ItemStack stack : menu.items)
        {
            NBTTagCompound itemTag = new NBTTagCompound();
            if (stack != null && !stack.isEmpty())
            {
                stack.writeToNBT(itemTag);
            }
            items.appendTag(itemTag);
        }
        tag.setTag("Items", items);
        return tag;
    }

    private static CachedMenu readMenu(NBTTagCompound tag)
    {
        String title = tag.getString("Title");
        int size = tag.getInteger("Size");
        String hash = tag.getString("Hash");
        NBTTagList itemsTag = tag.getTagList("Items", 10);
        List<ItemStack> items = new ArrayList<>();
        for (int j = 0; j < itemsTag.tagCount(); j++)
        {
            NBTTagCompound itemTag = itemsTag.getCompoundTagAt(j);
            ItemStack stack = new ItemStack(itemTag);
            items.add(stack.isEmpty() ? ItemStack.EMPTY : stack.copy());
        }
        return new CachedMenu(title, size, items, hash);
    }
}

