package com.rainbow_universe.bettercode.core.bridge;

public final class CursorState {
    private final boolean empty;
    private final String itemId;
    private final String displayName;
    private final String nbt;

    public CursorState(boolean empty, String itemId, String displayName, String nbt) {
        this.empty = empty;
        this.itemId = itemId == null ? "" : itemId;
        this.displayName = displayName == null ? "" : displayName;
        this.nbt = nbt == null ? "" : nbt;
    }

    public static CursorState empty() {
        return new CursorState(true, "", "", "");
    }

    public boolean isEmpty() {
        return empty;
    }

    public String itemId() {
        return itemId;
    }

    public String displayName() {
        return displayName;
    }

    public String nbt() {
        return nbt;
    }
}
