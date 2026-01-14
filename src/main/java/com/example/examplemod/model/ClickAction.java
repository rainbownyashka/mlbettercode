package com.example.examplemod.model;

import net.minecraft.inventory.ClickType;

public class ClickAction
{
    public final int slotNumber;
    public final int button;
    public final ClickType type;

    public ClickAction(int slotNumber, int button, ClickType type)
    {
        this.slotNumber = slotNumber;
        this.button = button;
        this.type = type;
    }
}
