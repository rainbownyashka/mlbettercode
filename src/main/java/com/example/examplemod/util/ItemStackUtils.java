package com.example.examplemod.util;

import net.minecraft.item.ItemStack;

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
}
