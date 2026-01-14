package com.example.examplemod.model;

import net.minecraft.item.ItemStack;

public class CopiedSlot
{
    public final int slotNumber;
    public final ItemStack stack;

    public CopiedSlot(int slotNumber, ItemStack stack)
    {
        this.slotNumber = slotNumber;
        this.stack = stack == null ? ItemStack.EMPTY : stack;
    }
}
