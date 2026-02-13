package com.rainbow_universe.bettercode.core.place;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

public final class PlaceRuntimeState {
    private final Deque<PlaceRuntimeEntry> queue = new ArrayDeque<PlaceRuntimeEntry>();
    private PlaceRuntimeEntry current;
    private boolean active;
    private int executedCount;
    private int totalCount;

    public void reset() {
        queue.clear();
        current = null;
        active = false;
        executedCount = 0;
        totalCount = 0;
    }

    public void loadFromSpecs(List<PlaceEntrySpec> specs) {
        reset();
        if (specs == null || specs.isEmpty()) {
            return;
        }
        for (PlaceEntrySpec spec : specs) {
            queue.add(PlaceRuntimeEntry.fromSpec(spec));
            totalCount++;
        }
        active = !queue.isEmpty();
    }

    public boolean isActive() {
        return active;
    }

    public PlaceRuntimeEntry currentOrNext() {
        if (!active) {
            return null;
        }
        if (current == null) {
            current = queue.pollFirst();
            if (current == null) {
                active = false;
                return null;
            }
        }
        return current;
    }

    public void markCurrentDone() {
        if (!active) {
            return;
        }
        if (current != null) {
            executedCount++;
        }
        current = null;
        if (queue.isEmpty()) {
            active = false;
        }
    }

    public int executedCount() {
        return executedCount;
    }

    public int totalCount() {
        return totalCount;
    }
}

