package com.rainbow_universe.bettercode.core.bridge;

public final class SelectedRow {
    private final String dimension;
    private final int entryX;
    private final int entryY;
    private final int entryZ;
    private final int signX;
    private final int signY;
    private final int signZ;

    public SelectedRow(String dimension, int x, int y, int z) {
        this(dimension, x, y, z, x, y, z - 1);
    }

    public SelectedRow(String dimension, int entryX, int entryY, int entryZ, int signX, int signY, int signZ) {
        this.dimension = dimension == null ? "" : dimension;
        this.entryX = entryX;
        this.entryY = entryY;
        this.entryZ = entryZ;
        this.signX = signX;
        this.signY = signY;
        this.signZ = signZ;
    }

    public String dimension() {
        return dimension;
    }

    public int entryX() {
        return entryX;
    }

    public int entryY() {
        return entryY;
    }

    public int entryZ() {
        return entryZ;
    }

    public int signX() {
        return signX;
    }

    public int signY() {
        return signY;
    }

    public int signZ() {
        return signZ;
    }

    public int x() {
        return entryX;
    }

    public int y() {
        return entryY;
    }

    public int z() {
        return entryZ;
    }
}
