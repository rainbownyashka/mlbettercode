package com.example.examplemod.feature.codemap;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class BlueGlassCodeMap
{
    public static final int DEFAULT_STEP_Z = 4;
    public static final int DEFAULT_FLOOR_DY = 10;
    public static final int DEFAULT_MAX_NODES = 4000;

    private static final int BLUE_GLASS_META = 3;

    private BlueGlassCodeMap()
    {
    }

    public static boolean isBlueGlass(World world, BlockPos pos)
    {
        if (world == null || pos == null || !world.isBlockLoaded(pos))
        {
            return false;
        }
        IBlockState state = world.getBlockState(pos);
        if (state == null)
        {
            return false;
        }
        Block b = state.getBlock();
        if (b != Blocks.STAINED_GLASS)
        {
            return false;
        }
        return b.getMetaFromState(state) == BLUE_GLASS_META;
    }

    public static boolean isFree(World world, BlockPos glassPos)
    {
        return world != null && glassPos != null && world.isBlockLoaded(glassPos) && world.isAirBlock(glassPos.up());
    }

    public static List<BlockPos> scan(World world, Collection<BlockPos> seeds)
    {
        return scan(world, seeds, DEFAULT_STEP_Z, DEFAULT_FLOOR_DY, DEFAULT_MAX_NODES);
    }

    public static List<BlockPos> scan(World world, Collection<BlockPos> seeds, int stepZ, int floorDy, int maxNodes)
    {
        if (world == null || seeds == null || seeds.isEmpty())
        {
            return Collections.emptyList();
        }
        int dz = Math.max(1, Math.abs(stepZ));
        int dy = Math.max(1, Math.abs(floorDy));
        int cap = Math.max(100, maxNodes);

        ArrayDeque<BlockPos> q = new ArrayDeque<>();
        Set<BlockPos> visited = new HashSet<>();
        for (BlockPos s : seeds)
        {
            if (s == null)
            {
                continue;
            }
            if (visited.add(s))
            {
                q.add(s);
            }
        }

        List<BlockPos> out = new ArrayList<>();
        while (!q.isEmpty() && visited.size() <= cap)
        {
            BlockPos pos = q.removeFirst();
            if (pos == null)
            {
                continue;
            }
            if (!world.isBlockLoaded(pos))
            {
                continue;
            }
            if (!isBlueGlass(world, pos))
            {
                continue;
            }
            out.add(pos);

            BlockPos n1 = pos.add(0, 0, dz);
            BlockPos n2 = pos.add(0, 0, -dz);
            BlockPos n3 = pos.add(0, dy, 0);
            BlockPos n4 = pos.add(0, -dy, 0);

            if (visited.size() <= cap && visited.add(n1))
            {
                q.add(n1);
            }
            if (visited.size() <= cap && visited.add(n2))
            {
                q.add(n2);
            }
            if (visited.size() <= cap && visited.add(n3))
            {
                q.add(n3);
            }
            if (visited.size() <= cap && visited.add(n4))
            {
                q.add(n4);
            }
        }

        out.sort(Comparator.<BlockPos>comparingInt(BlockPos::getY)
            .thenComparingInt(BlockPos::getZ)
            .thenComparingInt(BlockPos::getX));

        return out;
    }

    public static List<BlockPos> allocateNearestContiguousOrNearest(World world, List<BlockPos> allBlueGlass, int needed,
        double playerX, double playerY, double playerZ, int stepZ)
    {
        if (world == null || allBlueGlass == null || needed <= 0)
        {
            return Collections.emptyList();
        }
        int dz = Math.max(1, Math.abs(stepZ));

        List<BlockPos> free = new ArrayList<>();
        for (BlockPos p : allBlueGlass)
        {
            if (isFree(world, p))
            {
                free.add(p);
            }
        }
        if (free.size() < needed)
        {
            return Collections.emptyList();
        }

        // Group by Y floor.
        Map<Integer, List<BlockPos>> byY = new HashMap<>();
        for (BlockPos p : free)
        {
            byY.computeIfAbsent(p.getY(), k -> new ArrayList<>()).add(p);
        }

        List<BlockPos> best = null;
        double bestDist = Double.POSITIVE_INFINITY;

        for (Map.Entry<Integer, List<BlockPos>> e : byY.entrySet())
        {
            List<BlockPos> list = e.getValue();
            list.sort(Comparator.comparingInt(BlockPos::getZ));
            int runStart = 0;
            while (runStart < list.size())
            {
                int runEnd = runStart + 1;
                while (runEnd < list.size())
                {
                    int prevZ = list.get(runEnd - 1).getZ();
                    int curZ = list.get(runEnd).getZ();
                    if (curZ - prevZ != dz)
                    {
                        break;
                    }
                    runEnd++;
                }

                int runLen = runEnd - runStart;
                if (runLen >= needed)
                {
                    // sliding window inside this run
                    for (int start = runStart; start <= runEnd - needed; start++)
                    {
                        int mid = start + (needed - 1) / 2;
                        BlockPos center = list.get(mid);
                        double dx = center.getX() + 0.5 - playerX;
                        double dy = center.getY() + 0.5 - playerY;
                        double dzp = center.getZ() + 0.5 - playerZ;
                        double d2 = dx * dx + dy * dy + dzp * dzp;
                        if (d2 < bestDist)
                        {
                            bestDist = d2;
                            best = new ArrayList<>(list.subList(start, start + needed));
                        }
                    }
                }
                runStart = runEnd;
            }
        }

        if (best != null)
        {
            return best;
        }

        // Fallback: nearest free glasses (not necessarily contiguous).
        free.sort(Comparator.comparingDouble(p -> dist2(p, playerX, playerY, playerZ)));
        return new ArrayList<>(free.subList(0, needed));
    }

    private static double dist2(BlockPos p, double x, double y, double z)
    {
        double dx = p.getX() + 0.5 - x;
        double dy = p.getY() + 0.5 - y;
        double dz = p.getZ() + 0.5 - z;
        return dx * dx + dy * dy + dz * dz;
    }
}

