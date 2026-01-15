package com.example.examplemod.model;

public class PlaceArg
{
    public final String keyRaw;
    public final String keyNorm;
    public final Integer glassMetaFilter;
    public final int mode;
    public final String valueRaw;
    public final int clicks;
    public final boolean saveVariable;
    public final Integer slotIndex;
    public final boolean clickOnly;
    public final boolean slotGuiIndex;

    public PlaceArg(String keyRaw, String keyNorm, Integer glassMetaFilter, int mode, String valueRaw, int clicks,
        boolean saveVariable, Integer slotIndex, boolean clickOnly, boolean slotGuiIndex)
    {
        this.keyRaw = keyRaw;
        this.keyNorm = keyNorm;
        this.glassMetaFilter = glassMetaFilter;
        this.mode = mode;
        this.valueRaw = valueRaw;
        this.clicks = clicks;
        this.saveVariable = saveVariable;
        this.slotIndex = slotIndex;
        this.clickOnly = clickOnly;
        this.slotGuiIndex = slotGuiIndex;
    }
}
