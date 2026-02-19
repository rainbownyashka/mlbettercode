package com.rainbow_universe.bettercode.core.bridge;

import java.util.Map;

public final class CodeSelectorStore {
    public static final class ToggleResult {
        private final boolean added;
        private final int selectedCount;

        public ToggleResult(boolean added, int selectedCount) {
            this.added = added;
            this.selectedCount = selectedCount;
        }

        public boolean added() {
            return added;
        }

        public int selectedCount() {
            return selectedCount;
        }
    }

    private CodeSelectorStore() {
    }

    public static String key(String dimension, int x, int y, int z) {
        String dim = dimension == null ? "" : dimension;
        return dim + ":" + x + "," + y + "," + z;
    }

    public static ToggleResult toggle(
        Map<String, SelectedRow> selected,
        String dimension,
        int x,
        int y,
        int z
    ) {
        if (selected == null) {
            return new ToggleResult(false, 0);
        }
        String k = key(dimension, x, y, z);
        if (selected.containsKey(k)) {
            selected.remove(k);
            return new ToggleResult(false, selected.size());
        }
        selected.put(k, new SelectedRow(dimension, x, y, z));
        return new ToggleResult(true, selected.size());
    }
}
