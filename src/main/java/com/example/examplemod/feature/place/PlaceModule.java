package com.example.examplemod.feature.place;

import com.example.examplemod.feature.codemap.BlueGlassCodeMap;
import com.example.examplemod.model.PlaceArg;
import com.example.examplemod.model.PlaceEntry;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.command.ICommandSender;
import net.minecraft.init.Blocks;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntitySign;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextFormatting;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class PlaceModule
{
    public static final int INPUT_MODE_TEXT = 0;
    public static final int INPUT_MODE_NUMBER = 1;
    public static final int INPUT_MODE_VARIABLE = 2;
    public static final int INPUT_MODE_ARRAY = 3;
    public static final int INPUT_MODE_LOCATION = 4;
    public static final int INPUT_MODE_APPLE = 5;
    public static final int INPUT_MODE_ITEM = 6;

    private final PlaceModuleHost host;
    private final PlaceState state = new PlaceState();
    private List<BlockPos> lastScannedBlueGlass = new ArrayList<>();
    private int lastScannedBlueGlassDim = Integer.MIN_VALUE;

    private static final class PlanStep
    {
        boolean isPause;
        boolean isSkip;
        Block block;
        String name;
        String expectedSign1;
        String expectedSign2;
        String argsRaw;
        List<PlaceArg> parsedArgs;
    }

    public PlaceModule(PlaceModuleHost host)
    {
        this.host = host;
    }

    public void reset()
    {
        state.reset();
    }

    public boolean isActive()
    {
        return state.active;
    }

    public boolean isPostPlaceActive()
    {
        return state.active
            && state.current != null
            && state.current.placedBlock
            && state.current.postPlaceKind != PlaceEntry.POST_PLACE_NONE
            && state.current.postPlaceStage < 3;
    }

    public void onClientTick(Minecraft mc, long nowMs)
    {
        PlaceTickHandler.onClientTick(host, state, mc, nowMs);
    }

    public void onGuiTick(GuiContainer gui, long nowMs)
    {
        PlaceGuiHandler.onGuiTick(host, state, gui, nowMs);
    }

    public void runPlaceAdvancedCommand(MinecraftServer server, ICommandSender sender, String[] args)
    {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.world == null || mc.player == null)
        {
            host.setActionBar(false, "&cNo world/player", 2000L);
            return;
        }

        if (args == null || args.length == 0)
        {
            host.setActionBar(false, "&cUsage: /placeadvanced <block> <name> <args|no> ...", 3500L);
            return;
        }

        BlockPos glassPos = host.getLastGlassPos();
        int glassDim = host.getLastGlassDim();
        if (glassPos == null || glassDim != mc.world.provider.getDimension())
        {
            String key = host.getCodeGlassScopeKey(mc.world);
            if (key != null)
            {
                glassPos = host.getCodeBlueGlassById().get(key);
                if (glassPos != null)
                {
                    host.setLastGlassPos(glassPos, mc.world.provider.getDimension());
                }
            }
            if (glassPos == null)
            {
                host.setActionBar(false, "&cNo blue glass position recorded", 2000L);
                return;
            }
        }

        List<String> tokens = PlaceParser.splitArgsPreserveQuotes(String.join(" ", args));

        BlockPos glass = glassPos;
        int p = 0;
        int i = 0;
        while (i < tokens.size())
        {
            String blockTok = tokens.get(i);
            if ("air".equalsIgnoreCase(blockTok) || "minecraft:air".equalsIgnoreCase(blockTok))
            {
                PlaceEntry pause = new PlaceEntry(mc.player.getPosition(), Blocks.AIR, "");
                pause.searchKey = "";
                state.queue.add(pause);
                i++;
                continue;
            }
            if (i + 2 >= tokens.size())
            {
                host.setActionBar(false,
                    "&cUsage: /placeadvanced <block> <name> <args|no> ... (you can also insert 'air' as a pause)",
                    4500L);
                reset();
                return;
            }

            String nameTok = tokens.get(i + 1);
            String argsTok = tokens.get(i + 2);
            i += 3;

            String blockName = blockTok.contains(":") ? blockTok : ("minecraft:" + blockTok);
            Block b = Block.getBlockFromName(blockName);
            if (b == null)
            {
                host.setActionBar(false, "&cUnknown block: " + blockTok, 2500L);
                reset();
                return;
            }
            BlockPos target = glass.add(-2 * p, 1, 0);
            String search = nameTok == null ? "" : nameTok.trim();
            String norm = host.normalizeForMatch(search);

            PlaceEntry entry = new PlaceEntry(target, b, norm);

            // Special blocks:
            // - lapis_block: Function (name is set by right-clicking its sign with a TEXT item in hand)
            // - emerald_block: Cycle (name set like function, then right-click with NUMBER item to set tick period)
            if (b == Blocks.LAPIS_BLOCK)
            {
                entry.searchKey = "";
                entry.postPlaceKind = PlaceEntry.POST_PLACE_SIGN_NAME;
                entry.postPlaceName = search;
            }
            else if (b == Blocks.EMERALD_BLOCK)
            {
                entry.searchKey = "";
                entry.postPlaceKind = PlaceEntry.POST_PLACE_CYCLE;
                entry.postPlaceName = search;
                entry.postPlaceCycleTicks = parseCycleTicks(argsTok);
            }
            else
            {
                entry.searchKey = norm;
                if (!"no".equalsIgnoreCase(argsTok))
                {
                    entry.advancedArgsRaw = argsTok;
                    List<PlaceArg> parsed = PlaceParser.parsePlaceAdvancedArgs(argsTok, host);
                    if (parsed != null && !parsed.isEmpty())
                    {
                        entry.advancedArgs = parsed;
                    }
                }
            }

            state.queue.add(entry);
            p++;
        }

        state.active = !state.queue.isEmpty();
        state.current = null;
        host.setActionBar(true, "&a/placeadvanced queued=" + state.queue.size(), 2000L);
    }

    /**
     * Plan-run variant of /placeadvanced: allocates free blue-glass slots (scan: ±4Z, ±10Y) and places entries there.
     * Keeps manual /placeadvanced behavior unchanged.
     */
    public void runPlaceAdvancedPlanCommand(MinecraftServer server, ICommandSender sender, String[] args)
    {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.world == null || mc.player == null)
        {
            host.setActionBar(false, "&cNo world/player", 2000L);
            return;
        }

        if (args == null || args.length == 0)
        {
            host.setActionBar(false, "&cUsage: /mldsl run [path] [--start N]", 3500L);
            return;
        }

        BlockPos seed = host.getLastGlassPos();
        int seedDim = host.getLastGlassDim();
        if (seed == null || seedDim != mc.world.provider.getDimension())
        {
            String key = host.getCodeGlassScopeKey(mc.world);
            if (key != null)
            {
                seed = host.getCodeBlueGlassById().get(key);
                if (seed != null)
                {
                    host.setLastGlassPos(seed, mc.world.provider.getDimension());
                }
            }
        }
        if (seed == null)
        {
            host.setActionBar(false, "&cNo blue glass position recorded", 2500L);
            return;
        }

        List<String> tokens = PlaceParser.splitArgsPreserveQuotes(String.join(" ", args));
        if (tokens.isEmpty())
        {
            host.setActionBar(false, "&cEmpty plan", 2000L);
            return;
        }

        // IMPORTANT:
        // Plan-run must preserve the original /placeadvanced spatial behavior (one line placed from ONE start glass,
        // using -2X offsets). The blue-glass scan is only used to choose a suitable free START glass.
        //
        // The previous implementation allocated a separate blue-glass slot PER entry, which caused "event -> jump to next"
        // behavior and broke server-side expectations (actions that must follow a specific event/block chain).

        // Parse steps first (so we know how many "real" placements we need and can validate tokens).
        // Multi-row support: "newline" token splits the plan into separate code rows.
        // Each row gets its own allocated free blue-glass start, while preserving the -2X chain inside that row.
        List<List<PlanStep>> rows = new ArrayList<>();
        List<PlanStep> steps = new ArrayList<>();
        int i = 0;
        while (i < tokens.size())
        {
            String blockTok = tokens.get(i);
            if ("newline".equalsIgnoreCase(blockTok) || "row".equalsIgnoreCase(blockTok))
            {
                if (!steps.isEmpty())
                {
                    rows.add(steps);
                    steps = new ArrayList<>();
                }
                i++;
                continue;
            }
            if ("skip".equalsIgnoreCase(blockTok))
            {
                PlanStep s = new PlanStep();
                s.isSkip = true;
                steps.add(s);
                i++;
                continue;
            }
            if ("air".equalsIgnoreCase(blockTok) || "minecraft:air".equalsIgnoreCase(blockTok))
            {
                PlanStep s = new PlanStep();
                s.isPause = true;
                steps.add(s);
                i++;
                continue;
            }
            if (i + 2 >= tokens.size())
            {
                host.setActionBar(false,
                    "&cPlan token error (expected <block> <name> <args|no> ...)", 4500L);
                reset();
                return;
            }

            String nameTok = tokens.get(i + 1);
            String argsTok = tokens.get(i + 2);
            i += 3;

            String blockName = blockTok.contains(":") ? blockTok : ("minecraft:" + blockTok);
            Block b = Block.getBlockFromName(blockName);
            if (b == null)
            {
                host.setActionBar(false, "&cUnknown block: " + blockTok, 2500L);
                reset();
                return;
            }

            PlanStep s = new PlanStep();
            s.isPause = false;
            s.block = b;
            String rawName = nameTok == null ? "" : nameTok.trim();
            if (rawName.contains("||"))
            {
                String[] parts = rawName.split("\\Q||\\E", -1);
                s.name = parts.length >= 1 ? parts[0].trim() : rawName;
                if (parts.length == 2)
                {
                    s.expectedSign2 = parts[1].trim();
                }
            }
            else
            {
                s.name = rawName;
            }
            if (!"no".equalsIgnoreCase(argsTok))
            {
                s.argsRaw = argsTok;
                List<PlaceArg> parsed = PlaceParser.parsePlaceAdvancedArgs(argsTok, host);
                if (parsed != null && !parsed.isEmpty())
                {
                    s.parsedArgs = parsed;
                }
            }
            steps.add(s);
        }
        if (!steps.isEmpty())
        {
            rows.add(steps);
        }
        if (rows.isEmpty())
        {
            host.setActionBar(false, "&cEmpty plan", 2000L);
            return;
        }

        List<BlockPos> seedList = new ArrayList<>();
        seedList.add(seed);
        try
        {
            Map<String, BlockPos> all = host.getCodeBlueGlassById();
            if (all != null && !all.isEmpty())
            {
                for (BlockPos p : all.values())
                {
                    if (p != null)
                    {
                        seedList.add(p);
                    }
                }
            }
        }
        catch (Exception ignore) { }

        List<BlockPos> scanned = BlueGlassCodeMap.scan(mc.world, seedList);
        if (scanned.isEmpty())
        {
            // If chunks are unloaded (you flew away), fall back to the last known scan in this dimension.
            if (lastScannedBlueGlassDim == mc.world.provider.getDimension() && lastScannedBlueGlass != null && !lastScannedBlueGlass.isEmpty())
            {
                scanned = new ArrayList<>(lastScannedBlueGlass);
            }
            else
            {
                host.setActionBar(false, "&cNo blue glass found near seed", 2500L);
                return;
            }
        }
        else
        {
            lastScannedBlueGlass = new ArrayList<>(scanned);
            lastScannedBlueGlassDim = mc.world.provider.getDimension();
        }

        // If we have a previous scan for this dimension, merge it in (helps when seed/glass IDs change).
        if (lastScannedBlueGlassDim == mc.world.provider.getDimension() && lastScannedBlueGlass != null && !lastScannedBlueGlass.isEmpty())
        {
            java.util.LinkedHashSet<BlockPos> merged = new java.util.LinkedHashSet<>(scanned);
            merged.addAll(lastScannedBlueGlass);
            scanned = new ArrayList<>(merged);
        }

        // Pre-skip: if a row already exists anywhere in the scanned blue-glass area, skip it.
        // This avoids relying on the allocator picking the same start glass across runs.
        List<List<PlanStep>> pendingRows = new ArrayList<>();
        List<Integer> pendingRowNumbers = new ArrayList<>();
        int skippedRows = 0;
        for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++)
        {
            List<PlanStep> row = rows.get(rowIndex);
            boolean already = false;

            // Fast filter: only consider candidate glass positions where the first real block matches.
            Block firstBlock = null;
            for (PlanStep s : row)
            {
                if (s == null || s.isPause || s.isSkip)
                {
                    continue;
                }
                firstBlock = s.block;
                break;
            }
            if (firstBlock == null)
            {
                // Empty row: treat as already done.
                already = true;
            }
            else
            {
                for (BlockPos candidate : scanned)
                {
                    if (candidate == null)
                    {
                        continue;
                    }
                    Block b0 = mc.world.getBlockState(candidate.add(0, 1, 0)).getBlock();
                    if (b0 != firstBlock)
                    {
                        continue;
                    }
                    if (rowAlreadyMatches(host, mc, candidate, row, rowIndex + 1, false))
                    {
                        already = true;
                        break;
                    }
                }
            }
            if (already)
            {
                skippedRows++;
                host.debugChat("&e/mldsl: skipped row " + (rowIndex + 1) + " (already exists)");
            }
            else
            {
                pendingRows.add(row);
                pendingRowNumbers.add(rowIndex + 1);
            }
        }

        List<BlockPos> starts = new ArrayList<>();
        if (!pendingRows.isEmpty())
        {
            starts = BlueGlassCodeMap.allocateNearestContiguousOrNearest(
                mc.world,
                scanned,
                pendingRows.size(),
                mc.player.posX,
                mc.player.posY,
                mc.player.posZ,
                BlueGlassCodeMap.DEFAULT_STEP_Z);
            if (starts.isEmpty())
            {
                host.setActionBar(false, "&cNo free blue glass found (need at least " + pendingRows.size() + ")", 4500L);
                reset();
                return;
            }
        }

        if (!starts.isEmpty())
        {
            BlockPos startGlass = starts.get(0);
            host.setLastGlassPos(startGlass, mc.world.provider.getDimension());
        }

        // Build execution queue: append rows sequentially.
        for (int rowIndex = 0; rowIndex < pendingRows.size(); rowIndex++)
        {
            BlockPos glass = starts.get(rowIndex);
            int logicalRowNumber = pendingRowNumbers.get(rowIndex);
            int p = 0; // reset per row
            for (PlanStep s : pendingRows.get(rowIndex))
            {
                if (s.isPause)
                {
                    PlaceEntry pause = new PlaceEntry(mc.player.getPosition(), Blocks.AIR, "");
                    pause.searchKey = "";
                    state.queue.add(pause);
                    continue;
                }
                if (s.isSkip)
                {
                    BlockPos target = glass.add(-2 * p, 1, 0);
                    PlaceEntry move = new PlaceEntry(target, Blocks.AIR, "");
                    move.searchKey = "";
                    move.moveOnly = true;
                    state.queue.add(move);
                    p++;
                    continue;
                }

                BlockPos target = glass.add(-2 * p, 1, 0);

                String name = s.name == null ? "" : s.name;
                String norm = host.normalizeForMatch(name);
                PlaceEntry entry = new PlaceEntry(target, s.block, norm);

                if (s.block == Blocks.LAPIS_BLOCK)
                {
                    entry.searchKey = "";
                    entry.postPlaceKind = PlaceEntry.POST_PLACE_SIGN_NAME;
                    entry.postPlaceName = name;
                }
                else if (s.block == Blocks.EMERALD_BLOCK)
                {
                    entry.searchKey = "";
                    entry.postPlaceKind = PlaceEntry.POST_PLACE_CYCLE;
                    entry.postPlaceName = name;
                    entry.postPlaceCycleTicks = parseCycleTicks(s.argsRaw == null ? "no" : s.argsRaw);
                }
                else
                {
                    entry.searchKey = norm;
                    if (s.argsRaw != null)
                    {
                        entry.advancedArgsRaw = s.argsRaw;
                    }
                    if (s.parsedArgs != null && !s.parsedArgs.isEmpty())
                    {
                        entry.advancedArgs = s.parsedArgs;
                    }
                }
                state.queue.add(entry);
                p++;
            }
        }

        state.active = !state.queue.isEmpty();
        state.current = null;
        host.setActionBar(true,
            "&a/mldsl queued=" + state.queue.size() + " rows=" + rows.size() + " skipped=" + skippedRows,
            3500L);
    }

    public void runPlaceAdvancedPlanCheckCommand(MinecraftServer server, ICommandSender sender, String[] args)
    {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.world == null || mc.player == null)
        {
            host.setActionBar(false, "&cNo world/player", 2000L);
            return;
        }
        if (args == null || args.length == 0)
        {
            host.setActionBar(false, "&cEmpty plan args", 2000L);
            return;
        }

        // Parse steps and rows (same rules as runPlaceAdvancedPlanCommand).
        List<String> tokens = PlaceParser.splitArgsPreserveQuotes(String.join(" ", args));
        if (tokens == null || tokens.isEmpty())
        {
            host.setActionBar(false, "&cEmpty plan", 2000L);
            return;
        }

        List<List<PlanStep>> rows = new ArrayList<>();
        List<PlanStep> steps = new ArrayList<>();
        int i = 0;
        while (i < tokens.size())
        {
            String blockTok = tokens.get(i);
            if ("newline".equalsIgnoreCase(blockTok) || "row".equalsIgnoreCase(blockTok))
            {
                if (!steps.isEmpty())
                {
                    rows.add(steps);
                    steps = new ArrayList<>();
                }
                i++;
                continue;
            }
            if ("skip".equalsIgnoreCase(blockTok))
            {
                PlanStep s = new PlanStep();
                s.isSkip = true;
                steps.add(s);
                i++;
                continue;
            }
            if ("air".equalsIgnoreCase(blockTok) || "minecraft:air".equalsIgnoreCase(blockTok))
            {
                PlanStep s = new PlanStep();
                s.isPause = true;
                steps.add(s);
                i++;
                continue;
            }
            if (i + 2 >= tokens.size())
            {
                host.setActionBar(false, "&cPlan token error (expected <block> <name> <args|no> ...)", 4500L);
                return;
            }

            String nameTok = tokens.get(i + 1);
            String argsTok = tokens.get(i + 2);
            i += 3;

            String blockName = blockTok.contains(":") ? blockTok : ("minecraft:" + blockTok);
            Block b = Block.getBlockFromName(blockName);
            if (b == null)
            {
                host.setActionBar(false, "&cUnknown block: " + blockTok, 2500L);
                return;
            }

            PlanStep s = new PlanStep();
            s.isPause = false;
            s.block = b;
            String rawName = nameTok == null ? "" : nameTok.trim();
                if (rawName.contains("||"))
                {
                    String[] parts = rawName.split("\\Q||\\E", -1);
                    s.name = parts.length >= 1 ? parts[0].trim() : rawName;
                    if (parts.length == 2)
                    {
                        s.expectedSign2 = parts[1].trim();
                    }
                }
            else
            {
                s.name = rawName;
            }
            if (!"no".equalsIgnoreCase(argsTok))
            {
                s.argsRaw = argsTok;
                List<PlaceArg> parsed = PlaceParser.parsePlaceAdvancedArgs(argsTok, host);
                if (parsed != null && !parsed.isEmpty())
                {
                    s.parsedArgs = parsed;
                }
            }
            steps.add(s);
        }
        if (!steps.isEmpty())
        {
            rows.add(steps);
        }
        if (rows.isEmpty())
        {
            host.setActionBar(false, "&cEmpty plan", 2000L);
            return;
        }

        BlockPos seed = host.getLastGlassPos();
        if (seed == null)
        {
            host.setActionBar(false, "&cNo blue glass position recorded", 2000L);
            return;
        }

        List<BlockPos> seedList = new ArrayList<>();
        seedList.add(seed);
        try
        {
            Map<String, BlockPos> all = host.getCodeBlueGlassById();
            if (all != null && !all.isEmpty())
            {
                for (BlockPos p : all.values())
                {
                    if (p != null)
                    {
                        seedList.add(p);
                    }
                }
            }
        }
        catch (Exception ignore) { }

        List<BlockPos> scanned = BlueGlassCodeMap.scan(mc.world, seedList);
        if (scanned.isEmpty())
        {
            if (lastScannedBlueGlassDim == mc.world.provider.getDimension() && lastScannedBlueGlass != null && !lastScannedBlueGlass.isEmpty())
            {
                scanned = new ArrayList<>(lastScannedBlueGlass);
            }
            else
            {
                host.setActionBar(false, "&cNo blue glass found near seed", 2500L);
                return;
            }
        }
        else
        {
            lastScannedBlueGlass = new ArrayList<>(scanned);
            lastScannedBlueGlassDim = mc.world.provider.getDimension();
        }

        if (lastScannedBlueGlassDim == mc.world.provider.getDimension() && lastScannedBlueGlass != null && !lastScannedBlueGlass.isEmpty())
        {
            java.util.LinkedHashSet<BlockPos> merged = new java.util.LinkedHashSet<>(scanned);
            merged.addAll(lastScannedBlueGlass);
            scanned = new ArrayList<>(merged);
        }

        host.setActionBar(true,
            "&e/mldsl check: scanned=" + scanned.size() + " seeds=" + seedList.size() + " rows=" + rows.size(),
            2500L);
        if (host.isDebugUi())
        {
            host.debugChat("&e/mldsl check: seed=" + seed + " seeds=" + seedList.size()
                + " scanned=" + scanned.size()
                + " dy=" + BlueGlassCodeMap.DEFAULT_FLOOR_DY
                + " dz=" + BlueGlassCodeMap.DEFAULT_STEP_Z);
            if (!scanned.isEmpty())
            {
                BlockPos first = scanned.get(0);
                BlockPos last = scanned.get(scanned.size() - 1);
                host.debugChat("&e/mldsl check: scanned y=[" + first.getY() + ".." + last.getY() + "] z=[" + first.getZ() + ".." + last.getZ() + "]");
            }
        }

        int foundRows = 0;
        for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++)
        {
            List<PlanStep> row = rows.get(rowIndex);
            Block firstBlock = null;
            for (PlanStep st : row)
            {
                if (st == null || st.isPause || st.isSkip)
                {
                    continue;
                }
                firstBlock = st.block;
                break;
            }
            boolean found = false;
            BlockPos foundAt = null;
            int candidates = 0;
            for (BlockPos candidate : scanned)
            {
                if (candidate == null)
                {
                    continue;
                }
                if (firstBlock != null)
                {
                    BlockPos b0Pos = candidate.add(0, 1, 0);
                    Block b0;
                    if (!mc.world.isBlockLoaded(b0Pos, false))
                    {
                        b0 = host.getCachedPlacedBlock(mc.world, b0Pos);
                    }
                    else
                    {
                        b0 = mc.world.getBlockState(b0Pos).getBlock();
                    }
                    if (b0 != firstBlock)
                    {
                        continue;
                    }
                }
                candidates++;
                if (rowAlreadyMatches(host, mc, candidate, row, rowIndex + 1, false))
                {
                    found = true;
                    foundAt = candidate;
                    break;
                }
            }
            if (found)
            {
                foundRows++;
                host.debugChat("&a/mldsl check: row " + (rowIndex + 1) + " FOUND at " + foundAt);
            }
            else
            {
                host.debugChat("&c/mldsl check: row " + (rowIndex + 1) + " NOT found");
                if (host.isDebugUi())
                {
                    host.debugChat("&e/mldsl check: row " + (rowIndex + 1) + " candidates=" + candidates
                        + " firstBlock=" + (firstBlock == null ? "null" : firstBlock.getRegistryName()));
                }
            }
        }
        host.setActionBar(true, "&a/mldsl check done: found=" + foundRows + "/" + rows.size(), 3500L);
    }

    private static boolean rowAlreadyMatches(
        PlaceModuleHost host,
        Minecraft mc,
        BlockPos glass,
        List<PlanStep> steps,
        int rowNumber,
        boolean verbose)
    {
        if (host == null || mc == null || mc.world == null || glass == null || steps == null)
        {
            return false;
        }
        int p = 0;
        for (PlanStep s : steps)
        {
            if (s == null)
            {
                continue;
            }
            if (s.isPause)
            {
                continue;
            }
            if (s.isSkip)
            {
                p++;
                continue;
            }
            if (s.block == null)
            {
                return false;
            }
            BlockPos entryPos = glass.add(-2 * p, 1, 0);
            Block existing;
            if (!mc.world.isBlockLoaded(entryPos, false))
            {
                existing = host.getCachedPlacedBlock(mc.world, entryPos);
            }
            else
            {
                existing = mc.world.getBlockState(entryPos).getBlock();
            }
            if (existing != s.block)
            {
                if (verbose)
                {
                    host.debugChat(
                        "&e/mldsl skip-check row " + rowNumber + ": block mismatch p=" + p
                            + " want=" + s.block.getRegistryName()
                            + " got=" + (existing == null ? "null" : existing.getRegistryName()));
                }
                return false;
            }

            String expectedName = s.name == null ? "" : s.name.trim();
            String expectedSign2 = s.expectedSign2 == null ? "" : s.expectedSign2.trim();
            // NOTE: expectedSign1 is the category label (e.g. "Действие игрока") and is NOT written on the sign.
            // For reliable skip detection we compare only action/sign text (expectedSign2) and (optionally) menu name.
            if (!expectedName.isEmpty() || !expectedSign2.isEmpty())
            {
                BlockPos signPos = host.findSignAtZMinus1(mc.world, entryPos);
                if (signPos == null)
                {
                    if (verbose)
                    {
                        host.debugChat("&e/mldsl skip-check row " + rowNumber + ": no sign at p=" + p);
                    }
                    return false;
                }
                String expectedMenuNorm = host.normalizeForMatch(expectedName);
                String expectedSign2Norm = host.normalizeForMatch(expectedSign2);

                // menu name usually isn't written on the sign for actions; when expectedSign2 is present,
                // match only sign2. Use menu-only matching for blocks like functions/events where sign2 is empty.
                boolean menuOk = (expectedSign2Norm != null && !expectedSign2Norm.isEmpty())
                    || expectedMenuNorm == null || expectedMenuNorm.isEmpty();
                boolean s2Ok = expectedSign2Norm == null || expectedSign2Norm.isEmpty();
                StringBuilder actual = verbose ? new StringBuilder() : null;

                String[] cachedLines = null;
                TileEntity te = mc.world.getTileEntity(signPos);
                if (te instanceof TileEntitySign)
                {
                    TileEntitySign sign = (TileEntitySign) te;
                    for (ITextComponent line : sign.signText)
                    {
                        if (line == null)
                        {
                            continue;
                        }
                        String txt = TextFormatting.getTextWithoutFormattingCodes(line.getUnformattedText());
                        String lineNorm = host.normalizeForMatch(txt);
                        if (actual != null)
                        {
                            if (actual.length() > 0) actual.append(" | ");
                            actual.append(lineNorm == null ? "" : lineNorm);
                        }

                        if (!menuOk && lineNorm != null && !lineNorm.isEmpty() && lineNorm.equals(expectedMenuNorm))
                        {
                            menuOk = true;
                        }
                        if (!s2Ok && lineNorm != null && !lineNorm.isEmpty() && lineNorm.equals(expectedSign2Norm))
                        {
                            s2Ok = true;
                        }
                    }
                }
                else
                {
                    cachedLines = host.getCachedSignLines(mc.world, signPos);
                    if (cachedLines == null)
                    {
                        if (verbose)
                        {
                            host.debugChat("&e/mldsl skip-check row " + rowNumber + ": sign not loaded and no cache p=" + p);
                        }
                        return false;
                    }
                    for (int li = 0; li < cachedLines.length; li++)
                    {
                        String txt = cachedLines[li];
                        String lineNorm = host.normalizeForMatch(txt);
                        if (actual != null)
                        {
                            if (actual.length() > 0) actual.append(" | ");
                            actual.append(lineNorm == null ? "" : lineNorm);
                        }
                        if (!menuOk && lineNorm != null && !lineNorm.isEmpty() && lineNorm.equals(expectedMenuNorm))
                        {
                            menuOk = true;
                        }
                        if (!s2Ok && lineNorm != null && !lineNorm.isEmpty() && lineNorm.equals(expectedSign2Norm))
                        {
                            s2Ok = true;
                        }
                    }
                }
                if (!(menuOk && s2Ok))
                {
                    if (verbose)
                    {
                        host.debugChat(
                            "&e/mldsl skip-check row " + rowNumber + ": sign mismatch p=" + p
                                + " menu=" + (expectedMenuNorm == null ? "" : expectedMenuNorm)
                                + " sign2=" + (expectedSign2Norm == null ? "" : expectedSign2Norm)
                                + " actual=" + (actual == null ? "" : actual.toString())
                                + " (rawSign2=" + expectedSign2 + ", rawMenu=" + expectedName + ")");
                    }
                    return false;
                }
            }
            p++;
        }
        return true;
    }

    private static int parseCycleTicks(String raw)
    {
        if (raw == null)
        {
            return 5;
        }
        String t = raw.trim();
        if (t.isEmpty() || "no".equalsIgnoreCase(t))
        {
            return 5;
        }
        // Allow "ticks=20" or raw "20"
        String digits = t;
        int eq = t.indexOf('=');
        if (eq >= 0)
        {
            String key = t.substring(0, eq).trim().toLowerCase();
            if (key.contains("tick"))
            {
                digits = t.substring(eq + 1).trim();
            }
        }
        digits = digits.replaceAll("[^0-9]", "");
        if (digits.isEmpty())
        {
            return 5;
        }
        try
        {
            return Math.max(5, Integer.parseInt(digits));
        }
        catch (Exception ignore)
        {
            return 5;
        }
    }

    public void runPlaceBlocksCommand(MinecraftServer server, ICommandSender sender, String[] args)
    {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.world == null || mc.player == null)
        {
            host.setActionBar(false, "&cNo world/player", 2000L);
            return;
        }

        if (args == null || args.length == 0)
        {
            host.setActionBar(false, "&cUsage: /place <block1> [block2]...", 3000L);
            return;
        }

        BlockPos glassPos = host.getLastGlassPos();
        int glassDim = host.getLastGlassDim();
        if (glassPos == null || glassDim != mc.world.provider.getDimension())
        {
            String key = host.getCodeGlassScopeKey(mc.world);
            if (key != null)
            {
                glassPos = host.getCodeBlueGlassById().get(key);
                if (glassPos != null)
                {
                    host.setLastGlassPos(glassPos, mc.world.provider.getDimension());
                }
            }
            if (glassPos == null)
            {
                host.setActionBar(false, "&cNo blue glass position recorded", 2000L);
                return;
            }
        }

        List<String> tokens = PlaceParser.splitArgsPreserveQuotes(String.join(" ", args).trim());
        if (tokens.size() % 2 != 0)
        {
            host.setActionBar(false, "&cUsage: /place <block> <name> [<block> <name> ...]", 4000L);
            return;
        }

        int pairs = tokens.size() / 2;
        for (int pi = 0; pi < pairs; pi++)
        {
            String blockTok = tokens.get(pi * 2);
            String nameTok = tokens.get(pi * 2 + 1);
            String blockName = blockTok.contains(":") ? blockTok : ("minecraft:" + blockTok);
            Block b = Block.getBlockFromName(blockName);
            if (b == null)
            {
                continue;
            }
            BlockPos target = glassPos.add(-2 * pi, 1, 0);
            String search = nameTok == null ? "" : nameTok.trim();
            String norm = host.normalizeForMatch(search);
            state.queue.add(new PlaceEntry(target, b, norm));
        }

        state.active = !state.queue.isEmpty();
        state.current = null;
        host.setActionBar(true, "&a/place queued=" + state.queue.size(), 2000L);
    }
}
