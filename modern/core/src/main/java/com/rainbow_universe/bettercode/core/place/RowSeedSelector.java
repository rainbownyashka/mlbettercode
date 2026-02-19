package com.rainbow_universe.bettercode.core.place;

import com.rainbow_universe.bettercode.core.bridge.BlockPosView;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.ToDoubleFunction;

public final class RowSeedSelector {
    private RowSeedSelector() {
    }

    public static BlockPosView findNextRowSeed(
        BlockPosView rootSeed,
        BlockPosView currentSeed,
        Set<String> usedSeedKeys,
        BlueGlassSearch.Probe probe,
        ToDoubleFunction<BlockPosView> distanceSq
    ) {
        if (rootSeed == null || probe == null || distanceSq == null) {
            return null;
        }
        List<BlockPosView> seeds = new ArrayList<BlockPosView>();
        seeds.add(rootSeed);
        seeds = BlueGlassSearch.scan(seeds, probe);
        if (seeds.isEmpty()) {
            return null;
        }
        String currentKey = key(currentSeed);
        BlockPosView best = null;
        double bestDist = Double.MAX_VALUE;
        for (BlockPosView s : seeds) {
            if (s == null) {
                continue;
            }
            String k = key(s);
            if (!currentKey.isEmpty() && currentKey.equals(k)) {
                continue;
            }
            if (usedSeedKeys != null && usedSeedKeys.contains(k)) {
                continue;
            }
            double d = distanceSq.applyAsDouble(s);
            if (d < bestDist) {
                bestDist = d;
                best = s;
            }
        }
        return best;
    }

    public static String key(BlockPosView pos) {
        if (pos == null) {
            return "";
        }
        return pos.x() + ":" + pos.y() + ":" + pos.z();
    }

    public static String key(int x, int y, int z) {
        return x + ":" + y + ":" + z;
    }
}
