package com.example.examplemod.feature.place;

import com.example.examplemod.model.PlaceEntry;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class PlaceState
{
    static final class PlanRowRuntime
    {
        int logicalRowNumber;
        BlockPos glassPos;
        List<PlaceModule.PlanStep> steps = new ArrayList<>();
        int repairAttempts = 0;
    }

    final Deque<PlaceEntry> queue = new ArrayDeque<>();
    final List<PlanRowRuntime> planRows = new ArrayList<>();
    final Set<Integer> completedPlanRows = new HashSet<>();
    PlaceEntry current = null;
    boolean active = false;
    int totalEntries = 0;
    long startedMs = 0L;

    void reset()
    {
        active = false;
        queue.clear();
        planRows.clear();
        completedPlanRows.clear();
        current = null;
        totalEntries = 0;
        startedMs = 0L;
    }
}
