package com.example.examplemod.model;

public class PlaceArg
{
    public final String keyRaw;
    public final String keyNorm;
    public final Integer glassMetaFilter;
    public final int mode;
    public final String valueRaw;

    public PlaceArg(String keyRaw, String keyNorm, Integer glassMetaFilter, int mode, String valueRaw)
    {
        this.keyRaw = keyRaw;
        this.keyNorm = keyNorm;
        this.glassMetaFilter = glassMetaFilter;
        this.mode = mode;
        this.valueRaw = valueRaw;
    }
}
