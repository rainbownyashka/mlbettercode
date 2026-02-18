package com.rainbow_universe.bettercode.core.publish;

import com.rainbow_universe.bettercode.core.bridge.SelectedRow;

public final class PublishRowContext {
    private final SelectedRow row;
    private final int glassX;
    private final int glassY;
    private final int glassZ;
    private final int entryX;
    private final int entryY;
    private final int entryZ;
    private final int signX;
    private final int signY;
    private final int signZ;
    private final int paramsX;
    private final int paramsY;
    private final int paramsZ;

    private PublishRowContext(
        SelectedRow row,
        int glassX,
        int glassY,
        int glassZ,
        int entryX,
        int entryY,
        int entryZ,
        int signX,
        int signY,
        int signZ,
        int paramsX,
        int paramsY,
        int paramsZ
    ) {
        this.row = row;
        this.glassX = glassX;
        this.glassY = glassY;
        this.glassZ = glassZ;
        this.entryX = entryX;
        this.entryY = entryY;
        this.entryZ = entryZ;
        this.signX = signX;
        this.signY = signY;
        this.signZ = signZ;
        this.paramsX = paramsX;
        this.paramsY = paramsY;
        this.paramsZ = paramsZ;
    }

    public static PublishRowContext fromSelectedRow(SelectedRow row) {
        if (row == null) {
            return null;
        }
        int glassX = row.x();
        int glassY = row.y();
        int glassZ = row.z();
        int entryX = glassX;
        int entryY = glassY + 1;
        int entryZ = glassZ;
        int signX = entryX;
        int signY = entryY;
        int signZ = entryZ - 1;
        int paramsX = signX;
        int paramsY = signY + 1;
        int paramsZ = signZ + 1;
        return new PublishRowContext(
            row,
            glassX, glassY, glassZ,
            entryX, entryY, entryZ,
            signX, signY, signZ,
            paramsX, paramsY, paramsZ
        );
    }

    public SelectedRow row() {
        return row;
    }

    public int glassX() {
        return glassX;
    }

    public int glassY() {
        return glassY;
    }

    public int glassZ() {
        return glassZ;
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

    public int paramsX() {
        return paramsX;
    }

    public int paramsY() {
        return paramsY;
    }

    public int paramsZ() {
        return paramsZ;
    }
}
