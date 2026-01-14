package com.example.examplemod.model;

import net.minecraft.util.math.BlockPos;

public final class ShulkerHolo
{
    public final int dim;
    public final BlockPos pos;
    public final String text;
    public final int color;

    public ShulkerHolo(int dim, BlockPos pos, String text, int color)
    {
        this.dim = dim;
        this.pos = pos;
        this.text = text == null ? "" : text;
        this.color = color;
    }
}

