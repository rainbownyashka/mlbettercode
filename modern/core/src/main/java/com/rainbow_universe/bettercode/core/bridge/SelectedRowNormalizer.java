package com.rainbow_universe.bettercode.core.bridge;

public final class SelectedRowNormalizer {
    @FunctionalInterface
    public interface BlueGlassProbe {
        boolean isBlueGlass(int x, int y, int z);
    }

    private SelectedRowNormalizer() {
    }

    public static SelectedRow normalizeToGlassAnchor(SelectedRow row, BlueGlassProbe probe) {
        if (row == null || probe == null) {
            return null;
        }
        int x = row.x();
        int y = row.y();
        int z = row.z();
        if (probe.isBlueGlass(x, y, z)) {
            return new SelectedRow(row.dimension(), x, y, z);
        }
        if (probe.isBlueGlass(x, y - 1, z)) {
            return new SelectedRow(row.dimension(), x, y - 1, z);
        }
        return null;
    }
}
