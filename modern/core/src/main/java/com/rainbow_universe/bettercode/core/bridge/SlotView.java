package com.rainbow_universe.bettercode.core.bridge;

public final class SlotView {
    private final int slotNumber;
    private final int index;
    private final boolean playerInventory;
    private final boolean empty;
    private final String itemId;
    private final String displayName;
    private final String nbt;

    public SlotView(
        int slotNumber,
        int index,
        boolean playerInventory,
        boolean empty,
        String itemId,
        String displayName,
        String nbt
    ) {
        this.slotNumber = slotNumber;
        this.index = index;
        this.playerInventory = playerInventory;
        this.empty = empty;
        this.itemId = itemId == null ? "" : itemId;
        this.displayName = displayName == null ? "" : displayName;
        this.nbt = nbt == null ? "" : nbt;
    }

    public int slotNumber() {
        return slotNumber;
    }

    public int index() {
        return index;
    }

    public boolean playerInventory() {
        return playerInventory;
    }

    public boolean empty() {
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
