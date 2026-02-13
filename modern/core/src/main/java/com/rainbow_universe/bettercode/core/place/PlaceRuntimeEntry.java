package com.rainbow_universe.bettercode.core.place;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class PlaceRuntimeEntry {
    private final boolean pause;
    private final String blockId;
    private final String name;
    private final String argsRaw;
    private final List<PlaceArgSpec> args;

    public PlaceRuntimeEntry(boolean pause, String blockId, String name, String argsRaw, List<PlaceArgSpec> args) {
        this.pause = pause;
        this.blockId = blockId == null ? "" : blockId;
        this.name = name == null ? "" : name;
        this.argsRaw = argsRaw == null ? "" : argsRaw;
        this.args = args == null ? Collections.<PlaceArgSpec>emptyList() : new ArrayList<PlaceArgSpec>(args);
    }

    public static PlaceRuntimeEntry fromSpec(PlaceEntrySpec spec) {
        if (spec == null || spec.isPause()) {
            return new PlaceRuntimeEntry(true, "minecraft:air", "", "", Collections.<PlaceArgSpec>emptyList());
        }
        return new PlaceRuntimeEntry(false, spec.blockId(), spec.name(), spec.argsRaw(), spec.args());
    }

    public boolean isPause() {
        return pause;
    }

    public String blockId() {
        return blockId;
    }

    public String name() {
        return name;
    }

    public String argsRaw() {
        return argsRaw;
    }

    public List<PlaceArgSpec> args() {
        return Collections.unmodifiableList(args);
    }
}

