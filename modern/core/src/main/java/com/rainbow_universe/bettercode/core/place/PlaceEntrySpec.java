package com.rainbow_universe.bettercode.core.place;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class PlaceEntrySpec {
    private final boolean pause;
    private final boolean skip;
    private final String blockId;
    private final String name;
    private final String expectedSign1;
    private final String expectedSign2;
    private final String argsRaw;
    private final List<PlaceArgSpec> args;
    private final boolean negated;
    private final int postPlaceKind;
    private final String postPlaceName;
    private final int postPlaceCycleTicks;

    private PlaceEntrySpec(
        boolean pause,
        boolean skip,
        String blockId,
        String name,
        String expectedSign1,
        String expectedSign2,
        String argsRaw,
        List<PlaceArgSpec> args,
        boolean negated,
        int postPlaceKind,
        String postPlaceName,
        int postPlaceCycleTicks
    ) {
        this.pause = pause;
        this.skip = skip;
        this.blockId = blockId == null ? "" : blockId;
        this.name = name == null ? "" : name;
        this.expectedSign1 = expectedSign1 == null ? "" : expectedSign1;
        this.expectedSign2 = expectedSign2 == null ? "" : expectedSign2;
        this.argsRaw = argsRaw == null ? "" : argsRaw;
        this.args = args == null ? Collections.<PlaceArgSpec>emptyList() : new ArrayList<PlaceArgSpec>(args);
        this.negated = negated;
        this.postPlaceKind = postPlaceKind;
        this.postPlaceName = postPlaceName == null ? "" : postPlaceName;
        this.postPlaceCycleTicks = postPlaceCycleTicks;
    }

    public static PlaceEntrySpec pause() {
        return new PlaceEntrySpec(true, false, "minecraft:air", "", "", "", "", Collections.<PlaceArgSpec>emptyList(),
            false, PlaceRuntimeEntry.POST_PLACE_NONE, "", -1);
    }

    public static PlaceEntrySpec skip() {
        return new PlaceEntrySpec(false, true, "skip", "", "", "", "", Collections.<PlaceArgSpec>emptyList(),
            false, PlaceRuntimeEntry.POST_PLACE_NONE, "", -1);
    }

    public static PlaceEntrySpec block(
        String blockId,
        String name,
        String expectedSign1,
        String expectedSign2,
        String argsRaw,
        List<PlaceArgSpec> args,
        boolean negated,
        int postPlaceKind,
        String postPlaceName,
        int postPlaceCycleTicks
    ) {
        return new PlaceEntrySpec(false, false, blockId, name, expectedSign1, expectedSign2, argsRaw, args,
            negated, postPlaceKind, postPlaceName, postPlaceCycleTicks);
    }

    public boolean isPause() {
        return pause;
    }

    public boolean isSkip() {
        return skip;
    }

    public String blockId() {
        return blockId;
    }

    public String name() {
        return name;
    }

    public String expectedSign1() {
        return expectedSign1;
    }

    public String expectedSign2() {
        return expectedSign2;
    }

    public String argsRaw() {
        return argsRaw;
    }

    public List<PlaceArgSpec> args() {
        return Collections.unmodifiableList(args);
    }

    public boolean negated() {
        return negated;
    }

    public int postPlaceKind() {
        return postPlaceKind;
    }

    public String postPlaceName() {
        return postPlaceName;
    }

    public int postPlaceCycleTicks() {
        return postPlaceCycleTicks;
    }
}
