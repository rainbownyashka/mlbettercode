package com.example.examplemod.model;

import net.minecraft.client.shader.Framebuffer;
import net.minecraft.util.math.BlockPos;

public class TestChestHolo
{
    public final BlockPos pos;
    public final ChestCache cache;
    public final boolean useFbo;
    public final float scale;
    public final int textWidth;
    public final int texSize;
    public Framebuffer fbo;
    public String lastHash;

    public TestChestHolo(BlockPos pos, ChestCache cache, boolean useFbo, float scale, int textWidth, int texSize)
    {
        this.pos = pos;
        this.cache = cache;
        this.useFbo = useFbo;
        this.scale = scale;
        this.textWidth = textWidth;
        this.texSize = texSize;
    }
}

