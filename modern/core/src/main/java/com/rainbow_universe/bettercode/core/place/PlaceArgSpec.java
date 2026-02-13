package com.rainbow_universe.bettercode.core.place;

public final class PlaceArgSpec {
    private final String keyRaw;
    private final String keyNorm;
    private final Integer glassMetaFilter;
    private final int mode;
    private final String valueRaw;
    private final int clicks;
    private final boolean saveVariable;
    private final Integer slotIndex;
    private final boolean clickOnly;
    private final boolean slotGuiIndex;

    public PlaceArgSpec(
        String keyRaw,
        String keyNorm,
        Integer glassMetaFilter,
        int mode,
        String valueRaw,
        int clicks,
        boolean saveVariable,
        Integer slotIndex,
        boolean clickOnly,
        boolean slotGuiIndex
    ) {
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

    public String keyRaw() { return keyRaw; }
    public String keyNorm() { return keyNorm; }
    public Integer glassMetaFilter() { return glassMetaFilter; }
    public int mode() { return mode; }
    public String valueRaw() { return valueRaw; }
    public int clicks() { return clicks; }
    public boolean saveVariable() { return saveVariable; }
    public Integer slotIndex() { return slotIndex; }
    public boolean clickOnly() { return clickOnly; }
    public boolean slotGuiIndex() { return slotGuiIndex; }
}

