package com.rainbow_universe.bettercode.core.bridge;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ContainerView {
    private final int windowId;
    private final String title;
    private final int size;
    private final List<SlotView> slots;

    public ContainerView(int windowId, String title, int size, List<SlotView> slots) {
        this.windowId = windowId;
        this.title = title == null ? "" : title;
        this.size = size;
        this.slots = slots == null ? Collections.<SlotView>emptyList() : new ArrayList<SlotView>(slots);
    }

    public static ContainerView empty() {
        return new ContainerView(-1, "", 0, Collections.<SlotView>emptyList());
    }

    public int windowId() {
        return windowId;
    }

    public String title() {
        return title;
    }

    public int size() {
        return size;
    }

    public List<SlotView> slots() {
        return Collections.unmodifiableList(slots);
    }
}
