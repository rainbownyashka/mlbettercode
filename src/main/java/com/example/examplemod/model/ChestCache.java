package com.example.examplemod.model;

import java.util.List;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;

public final class ChestCache
{
    public final int dim;
    public final BlockPos pos;
    public final List<ItemStack> items;
    public final long updatedMs;
    public final String label;

    public ChestCache(int dim, BlockPos pos, List<ItemStack> items, long updatedMs, String label)
    {
        this.dim = dim;
        this.pos = pos;
        this.items = items;
        this.updatedMs = updatedMs;
        this.label = label == null ? "" : label;
    }
}

