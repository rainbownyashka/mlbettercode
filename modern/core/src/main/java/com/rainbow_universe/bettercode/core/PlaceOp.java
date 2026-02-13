package com.rainbow_universe.bettercode.core;

public final class PlaceOp {
    public enum Kind {
        AIR,
        BLOCK
    }

    private final Kind kind;
    private final String blockId;
    private final String name;
    private final String args;

    private PlaceOp(Kind kind, String blockId, String name, String args) {
        this.kind = kind;
        this.blockId = blockId;
        this.name = name;
        this.args = args;
    }

    public static PlaceOp air() {
        return new PlaceOp(Kind.AIR, "minecraft:air", "", "");
    }

    public static PlaceOp block(String blockId, String name, String args) {
        return new PlaceOp(Kind.BLOCK, blockId, name, args);
    }

    public Kind kind() {
        return kind;
    }

    public String blockId() {
        return blockId;
    }

    public String name() {
        return name;
    }

    public String args() {
        return args;
    }
}

