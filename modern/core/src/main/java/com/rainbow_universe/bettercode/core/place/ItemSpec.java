package com.rainbow_universe.bettercode.core.place;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ItemSpec {
    private final String itemId;
    private final int count;
    private final int meta;
    private final String name;
    private final List<String> loreLines;
    private final String nbtRaw;

    public ItemSpec(String itemId, int count, int meta, String name, List<String> loreLines, String nbtRaw) {
        this.itemId = itemId;
        this.count = count;
        this.meta = meta;
        this.name = name == null ? "" : name;
        this.loreLines = loreLines == null ? Collections.<String>emptyList() : new ArrayList<String>(loreLines);
        this.nbtRaw = nbtRaw == null ? "" : nbtRaw;
    }

    public String itemId() {
        return itemId;
    }

    public int count() {
        return count;
    }

    public int meta() {
        return meta;
    }

    public String name() {
        return name;
    }

    public List<String> loreLines() {
        return Collections.unmodifiableList(loreLines);
    }

    public String nbtRaw() {
        return nbtRaw;
    }
}

