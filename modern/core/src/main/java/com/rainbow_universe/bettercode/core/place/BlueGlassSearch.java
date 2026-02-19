package com.rainbow_universe.bettercode.core.place;

import com.rainbow_universe.bettercode.core.bridge.BlockPosView;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.ToDoubleFunction;

public final class BlueGlassSearch {
    public static final int DEFAULT_STEP_Z = 4;
    public static final int DEFAULT_FLOOR_DY = 10;
    public static final int DEFAULT_MAX_NODES = 4000;

    public interface Probe {
        boolean isBlueGlass(int x, int y, int z);
        boolean isFree(int x, int y, int z);
    }

    private BlueGlassSearch() {
    }

    public static List<BlockPosView> scan(Collection<BlockPosView> seeds, Probe probe) {
        return scan(seeds, probe, DEFAULT_STEP_Z, DEFAULT_FLOOR_DY, DEFAULT_MAX_NODES);
    }

    public static BlockPosView resolveClickedGlass(BlockPosView clicked, Probe probe) {
        if (clicked == null || probe == null) {
            return null;
        }
        final int x = clicked.x();
        final int y = clicked.y();
        final int z = clicked.z();

        // Legacy-compatible anchor candidates:
        // direct glass, block above glass, and code-mode-relative offsets
        // requested by live migration traces (-2 x/z and -1 y variants).
        final int[][] candidates = new int[][]{
            {0, 0, 0},
            {0, -1, 0},
            {-2, 0, 0},
            {0, 0, -2},
            {-2, 0, -2},
            {-2, -1, 0},
            {0, -1, -2},
            {-2, -1, -2}
        };

        for (int[] c : candidates) {
            int gx = x + c[0];
            int gy = y + c[1];
            int gz = z + c[2];
            if (probe.isBlueGlass(gx, gy, gz)) {
                return new BlockPosView(gx, gy, gz);
            }
        }
        return null;
    }

    public static BlockPosView chooseNearestSeed(
        Collection<BlockPosView> seeds,
        Probe probe,
        ToDoubleFunction<BlockPosView> distSq
    ) {
        if (seeds == null || seeds.isEmpty() || probe == null || distSq == null) {
            return null;
        }
        List<BlockPosView> scanned = scan(seeds, probe);
        if (scanned.isEmpty()) {
            scanned = new ArrayList<BlockPosView>(seeds);
        }
        BlockPosView free = nearest(scanned, probe, distSq, true);
        if (free != null) {
            return free;
        }
        return nearest(scanned, probe, distSq, false);
    }

    public static List<BlockPosView> scan(
        Collection<BlockPosView> seeds,
        Probe probe,
        int stepZ,
        int floorDy,
        int maxNodes
    ) {
        if (seeds == null || seeds.isEmpty() || probe == null) {
            return java.util.Collections.emptyList();
        }
        int dz = Math.max(1, Math.abs(stepZ));
        int dy = Math.max(1, Math.abs(floorDy));
        int cap = Math.max(100, maxNodes);

        ArrayDeque<BlockPosView> q = new ArrayDeque<BlockPosView>();
        Set<String> visited = new HashSet<String>();
        for (BlockPosView s : seeds) {
            if (s == null) {
                continue;
            }
            String key = key(s.x(), s.y(), s.z());
            if (visited.add(key)) {
                q.add(s);
            }
        }

        ArrayList<BlockPosView> out = new ArrayList<BlockPosView>();
        while (!q.isEmpty() && visited.size() <= cap) {
            BlockPosView p = q.removeFirst();
            if (p == null) {
                continue;
            }
            int x = p.x();
            int y = p.y();
            int z = p.z();
            if (!probe.isBlueGlass(x, y, z)) {
                continue;
            }
            out.add(p);
            enqueue(q, visited, x, y, z + dz, cap);
            enqueue(q, visited, x, y, z - dz, cap);
            enqueue(q, visited, x, y + dy, z, cap);
            enqueue(q, visited, x, y - dy, z, cap);
        }

        out.sort(Comparator
            .comparingInt(BlockPosView::y)
            .thenComparingInt(BlockPosView::z)
            .thenComparingInt(BlockPosView::x));
        return out;
    }

    public static BlockPosView nearest(
        List<BlockPosView> glasses,
        Probe probe,
        ToDoubleFunction<BlockPosView> distSq,
        boolean requireFree
    ) {
        if (glasses == null || glasses.isEmpty() || probe == null || distSq == null) {
            return null;
        }
        BlockPosView best = null;
        double bestDist = Double.MAX_VALUE;
        for (BlockPosView p : glasses) {
            if (p == null) {
                continue;
            }
            boolean ok = requireFree
                ? probe.isFree(p.x(), p.y(), p.z())
                : probe.isBlueGlass(p.x(), p.y(), p.z());
            if (!ok) {
                continue;
            }
            double d = distSq.applyAsDouble(p);
            if (d < bestDist) {
                bestDist = d;
                best = p;
            }
        }
        return best;
    }

    private static void enqueue(ArrayDeque<BlockPosView> q, Set<String> visited, int x, int y, int z, int cap) {
        if (visited.size() > cap) {
            return;
        }
        String k = key(x, y, z);
        if (visited.add(k)) {
            q.add(new BlockPosView(x, y, z));
        }
    }

    private static String key(int x, int y, int z) {
        return x + ":" + y + ":" + z;
    }
}
