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
import net.minecraft.util.math.BlockPos;

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

    private final PlaceModuleHost host;
    private final PlaceState state = new PlaceState();

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
        final class Step
        {
            boolean isPause;
            boolean isSkip;
            Block block;
            String name;
            String argsRaw;
            List<PlaceArg> parsedArgs;
        }

        // Multi-row support: "newline" token splits the plan into separate code rows.
        // Each row gets its own allocated free blue-glass start, while preserving the -2X chain inside that row.
        List<List<Step>> rows = new ArrayList<>();
        List<Step> steps = new ArrayList<>();
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
                Step s = new Step();
                s.isSkip = true;
                steps.add(s);
                i++;
                continue;
            }
            if ("air".equalsIgnoreCase(blockTok) || "minecraft:air".equalsIgnoreCase(blockTok))
            {
                Step s = new Step();
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

            Step s = new Step();
            s.isPause = false;
            s.block = b;
            s.name = nameTok == null ? "" : nameTok.trim();
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

        List<BlockPos> scanned = BlueGlassCodeMap.scan(mc.world, java.util.Collections.singleton(seed));
        if (scanned.isEmpty())
        {
            host.setActionBar(false, "&cNo blue glass found near seed", 2500L);
            return;
        }

        List<BlockPos> starts = BlueGlassCodeMap.allocateNearestContiguousOrNearest(
            mc.world,
            scanned,
            rows.size(),
            mc.player.posX,
            mc.player.posY,
            mc.player.posZ,
            BlueGlassCodeMap.DEFAULT_STEP_Z);
        if (starts.isEmpty())
        {
            host.setActionBar(false, "&cNo free blue glass found (need at least " + rows.size() + ")", 4500L);
            reset();
            return;
        }

        BlockPos startGlass = starts.get(0);
        host.setLastGlassPos(startGlass, mc.world.provider.getDimension());

        // Build execution queue: append rows sequentially.
        for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++)
        {
            BlockPos glass = starts.get(rowIndex);
            int p = 0; // reset per row
            for (Step s : rows.get(rowIndex))
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
            "&a/mldsl queued=" + state.queue.size() + " rows=" + rows.size(),
            3500L);
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
