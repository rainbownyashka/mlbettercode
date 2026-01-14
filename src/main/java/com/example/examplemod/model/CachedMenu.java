package com.example.examplemod.model;

import net.minecraft.item.ItemStack;

import java.util.List;

public class CachedMenu
{
    public final String title;
    public final int size;
    public final List<ItemStack> items;
    public final String hash;

    public CachedMenu(String title, int size, List<ItemStack> items, String hash)
    {
        this.title = title == null ? "" : title;
        this.size = size;
        this.items = items;
        this.hash = hash == null ? "" : hash;
    }
}
