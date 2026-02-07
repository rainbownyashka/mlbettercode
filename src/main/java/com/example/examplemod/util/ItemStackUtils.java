package com.example.examplemod.util;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import java.util.ArrayList;
import java.util.List;

public final class ItemStackUtils
{
    private ItemStackUtils() {}

    public static List<ItemStack> copyItemStackList(List<ItemStack> items)
    {
        List<ItemStack> copy = new ArrayList<>();
        if (items == null)
        {
            return copy;
        }
        for (ItemStack stack : items)
        {
            copy.add(stack == null || stack.isEmpty() ? ItemStack.EMPTY : stack.copy());
        }
        return copy;
    }

    public static List<String> getLore(ItemStack stack)
    {
        List<String> out = new ArrayList<>();
        if (stack == null || stack.isEmpty())
        {
            return out;
        }
        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null || !tag.hasKey("display", 10))
        {
            return out;
        }
        NBTTagCompound display = tag.getCompoundTag("display");
        if (!display.hasKey("Lore", 9))
        {
            return out;
        }
        NBTTagList lore = display.getTagList("Lore", 8);
        for (int i = 0; i < lore.tagCount(); i++)
        {
            out.add(lore.getStringTagAt(i));
        }
        return out;
    }
}
