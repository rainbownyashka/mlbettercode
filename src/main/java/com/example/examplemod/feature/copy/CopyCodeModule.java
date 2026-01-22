package com.example.examplemod.feature.copy;

import com.example.examplemod.feature.codemap.BlueGlassCodeMap;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.GuiIngameMenu;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.command.ICommandSender;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.CPacketEntityAction;
import net.minecraft.network.play.client.CPacketHeldItemChange;
import net.minecraft.network.play.client.CPacketPlayer;
import net.minecraft.network.play.client.CPacketPlayerDigging;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class CopyCodeModule
{
    private static final int BLUE_GLASS_META = 3;

    public interface Host
    {
        boolean isEditorModeActive();
        boolean isDevCreativeScoreboard(Minecraft mc);
        void setActionBar(boolean ok, String text, long timeMs);
        void debugChat(String text);
        boolean isDebugUi();

        BlockPos getLastGlassPos();
        int getLastGlassDim();
        void setLastGlassPos(BlockPos pos, int dim);
        java.util.Map<String, BlockPos> getCodeBlueGlassById();
        String getCodeGlassScopeKey(World world);

        boolean tpPathQueueIsEmpty();
        int tpPathQueueSize();
        void buildTpPathQueue(World world, double fromX, double fromY, double fromZ, double toX, double toY, double toZ);
        void clearTpPathQueue();
    }

    private static final class BlockTask
    {
        final int floorOffset;
        final int relX;
        final int relZ;

        BlockTask(int floorOffset, int relX, int relZ)
        {
            this.floorOffset = floorOffset;
            this.relX = relX;
            this.relZ = relZ;
        }
    }

    private enum Stage
    {
        IDLE,
        SCAN_FLOOR,
        TP_TO_SOURCE,
        SHIFT_DOWN,
        BREAK_BLOCK,
        WAIT_AFTER_BREAK,
        SWITCH_TO_ID2,
        WAIT_WORLD_2,
        DEV_2,
        WAIT_DEV_2,
        TP_TO_DEST,
        PLACE_BEACON,
        WAIT_AFTER_PLACE,
        SWITCH_TO_ID1,
        WAIT_WORLD_1,
        DEV_1,
        WAIT_DEV_1,
        NEXT
    }

    private final Host host;

    private boolean active = false;
    private String id1;
    private String id2;
    private int copyYOffset;
    private List<Integer> floorOffsets = new ArrayList<>();

    private int floorIndex = 0;
    private List<BlockTask> floorTasks = new ArrayList<>();
    private int blockIndex = 0;
    private int currentFloorOffset = 0;

    private Stage stage = Stage.IDLE;
    private long nextMs = 0L;
    private int worldRefSwitch = 0;
    private long switchSentMs = 0L;
    private Vec3d switchFromPos = null;
    private BlockPos currentSource;

    private boolean prevPauseOnLostFocus = true;
    private boolean shiftHeld = false;

    private int floorCacheWorldRef = 0;
    private final Map<Integer, BlockPos> floorMaxGlassCache = new HashMap<>();

    private long nextEtaMs = 0L;
    private double avgSwitchMs = -1.0;
    private double avgTpSteps = -1.0;
    private int floorsScanned = 0;
    private int blocksScannedTotal = 0;

    public CopyCodeModule(Host host)
    {
        this.host = host;
    }

    public void cancel(String reason)
    {
        Minecraft mc = Minecraft.getMinecraft();
        try
        {
            releaseShift(mc);
        }
        catch (Exception ignore) { }
        try
        {
            if (mc != null)
            {
                mc.gameSettings.pauseOnLostFocus = prevPauseOnLostFocus;
            }
        }
        catch (Exception ignore) { }
        try
        {
            if (host != null)
            {
                host.clearTpPathQueue();
            }
        }
        catch (Exception ignore) { }
        active = false;
        stage = Stage.IDLE;
        floorTasks.clear();
        floorOffsets.clear();
        floorIndex = 0;
        blockIndex = 0;
        currentFloorOffset = 0;
        currentSource = null;
        floorCacheWorldRef = 0;
        floorMaxGlassCache.clear();
        worldRefSwitch = 0;
        switchSentMs = 0L;
        switchFromPos = null;
        nextEtaMs = 0L;
        if (host != null)
        {
            host.setActionBar(true, "&eCopy stopped: " + reason, 2500L);
        }
    }

    public void runCopyCommand(ICommandSender sender, String[] args)
    {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.player == null || mc.world == null)
        {
            return;
        }
        if (!host.isEditorModeActive() || !host.isDevCreativeScoreboard(mc))
        {
            host.setActionBar(false, "&cDEV mode only (creative + scoreboard)", 2500L);
            return;
        }
        if (args == null || args.length < 4)
        {
            host.setActionBar(false, "&cUsage: /copycode <id1> <id2> <yoffset> <floorsCSV>", 4500L);
            return;
        }

        id1 = args[0];
        id2 = args[1];
        try
        {
            copyYOffset = Integer.parseInt(args[2]);
        }
        catch (Exception e)
        {
            host.setActionBar(false, "&cInvalid yoffset (need int)", 3000L);
            return;
        }

        floorOffsets = parseFloors(args[3]);
        if (floorOffsets.isEmpty())
        {
            host.setActionBar(false, "&cInvalid floors list (example: 0,10,20)", 3500L);
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

        prevPauseOnLostFocus = mc.gameSettings.pauseOnLostFocus;
        mc.gameSettings.pauseOnLostFocus = false;

        active = true;
        stage = Stage.SCAN_FLOOR;
        floorIndex = 0;
        blockIndex = 0;
        floorTasks.clear();
        currentFloorOffset = 0;
        currentSource = null;
        nextMs = System.currentTimeMillis();
        worldRefSwitch = 0;
        switchSentMs = 0L;
        switchFromPos = null;
        nextEtaMs = nextMs;
        avgSwitchMs = -1.0;
        avgTpSteps = -1.0;
        floorsScanned = 0;
        blocksScannedTotal = 0;
        floorCacheWorldRef = 0;
        floorMaxGlassCache.clear();

        host.setActionBar(true, "&aCopy started: floors=" + floorOffsets + " yoff=" + copyYOffset, 3500L);
    }

    public void onClientTick(Minecraft mc, long nowMs)
    {
        if (!active || stage == Stage.IDLE)
        {
            return;
        }
        if (mc == null || mc.player == null || mc.world == null || mc.playerController == null)
        {
            return;
        }
        if (nowMs < nextMs)
        {
            return;
        }

        // Auto-close random server GUIs (ads, etc). Allow only chat, inventory and ESC menu.
        if (closeUnexpectedGui(mc))
        {
            nextMs = nowMs + 300L;
            return;
        }

        int wref = System.identityHashCode(mc.world);
        if (floorCacheWorldRef != wref)
        {
            floorCacheWorldRef = wref;
            floorMaxGlassCache.clear();
        }

        // Only keep flying while in Dev+Creative (tppath + safe movement).
        if (host.isDevCreativeScoreboard(mc))
        {
            tryUpdateLastGlassFromPlayer(mc);
            ensureFlying(mc);
        }

        if (nowMs >= nextEtaMs)
        {
            showEta(nowMs);
            nextEtaMs = nowMs + 1000L;
        }

        try
        {
            // Avoid cross-GUI carry bugs: always try to keep cursor empty.
            if (!mc.player.inventory.getItemStack().isEmpty())
            {
                // Try to drop it (creative) by picking up in an empty hotbar/inv slot isn't safe here;
                // simply cancel to avoid corrupting further steps.
                host.setActionBar(false, "&c/copycode: cursor not empty, canceling", 3500L);
                cancel("cursor not empty");
                return;
            }
        }
        catch (Exception ignore) { }

        if (stage == Stage.SCAN_FLOOR)
        {
            if (!host.isDevCreativeScoreboard(mc))
            {
                host.setActionBar(false, "&c/copycode: открой Dev (нужен для скана/ломания)", 2500L);
                nextMs = nowMs + 600L;
                return;
            }
            if (floorIndex >= floorOffsets.size())
            {
                cancel("done");
                return;
            }
            scanFloor(mc, floorOffsets.get(floorIndex));
            if (floorTasks.isEmpty())
            {
                floorIndex++;
                nextMs = nowMs + 250L;
                return;
            }
            blockIndex = 0;
            stage = Stage.TP_TO_SOURCE;
            nextMs = nowMs + 250L;
            return;
        }

        if (blockIndex >= floorTasks.size() && stage != Stage.SCAN_FLOOR)
        {
            floorIndex++;
            floorTasks.clear();
            stage = Stage.SCAN_FLOOR;
            nextMs = nowMs + 250L;
            return;
        }

        switch (stage)
        {
            case TP_TO_SOURCE:
                if (!host.isDevCreativeScoreboard(mc))
                {
                    // We can only tppath in Dev+Creative.
                    stage = Stage.DEV_1;
                    nextMs = nowMs + 250L;
                    return;
                }
                BlockTask task = floorTasks.get(blockIndex);
                currentFloorOffset = task == null ? currentFloorOffset : task.floorOffset;
                BlockPos maxGlass = task == null ? null : getFloorMaxGlass(mc, task.floorOffset);
                currentSource = resolveBlockPosFromMaxGlass(maxGlass, task, 0);
                if (currentSource == null)
                {
                    blockIndex++;
                    nextMs = nowMs + 50L;
                    return;
                }
                if (!host.tpPathQueueIsEmpty())
                {
                    nextMs = nowMs + 100L;
                    return;
                }
                tpNear(mc, currentSource);
                stage = Stage.SHIFT_DOWN;
                nextMs = nowMs + 600L;
                return;

            case SHIFT_DOWN:
                pressShift(mc);
                shiftHeld = true;
                stage = Stage.BREAK_BLOCK;
                nextMs = nowMs + 500L;
                return;

            case BREAK_BLOCK:
                BlockTask taskB = floorTasks.get(blockIndex);
                BlockPos maxGlassB = taskB == null ? null : getFloorMaxGlass(mc, taskB.floorOffset);
                BlockPos breakPos = resolveBlockPosFromMaxGlass(maxGlassB, taskB, 0);
                if (breakPos == null)
                {
                    stage = Stage.NEXT;
                    return;
                }
                doBreak(mc, breakPos);
                stage = Stage.WAIT_AFTER_BREAK;
                nextMs = nowMs + 3000L;
                return;

            case WAIT_AFTER_BREAK:
                releaseShift(mc);
                sendChat(mc, "/ad " + id2);
                markSwitchStart(mc);
                stage = Stage.WAIT_WORLD_2;
                nextMs = nowMs + 250L;
                return;

            case WAIT_WORLD_2:
                if (switchReady(mc, nowMs))
                {
                    updateAvgSwitch(nowMs);
                    nextMs = nowMs + 1000L;
                    stage = Stage.DEV_2;
                }
                else
                {
                    nextMs = nowMs + 250L;
                }
                return;

            case DEV_2:
                sendChat(mc, "/dev");
                stage = Stage.WAIT_DEV_2;
                nextMs = nowMs + 1250L;
                return;

            case WAIT_DEV_2:
                if (!host.isDevCreativeScoreboard(mc))
                {
                    // Some servers drop us into play mode after /ad. Retry /dev until it sticks.
                    if ((nowMs - switchSentMs) > 15000L)
                    {
                        cancel("dev mode not available");
                        return;
                    }
                    stage = Stage.DEV_2;
                    nextMs = nowMs + 1500L;
                    return;
                }
                stage = Stage.TP_TO_DEST;
                nextMs = nowMs + 350L;
                return;

            case TP_TO_DEST:
                if (!host.isDevCreativeScoreboard(mc))
                {
                    stage = Stage.DEV_2;
                    nextMs = nowMs + 250L;
                    return;
                }
                if (!host.tpPathQueueIsEmpty())
                {
                    nextMs = nowMs + 100L;
                    return;
                }
                BlockTask taskD = floorTasks.get(blockIndex);
                BlockPos maxGlassD = taskD == null ? null : getFloorMaxGlass(mc, taskD.floorOffset);
                BlockPos dest = resolveBlockPosFromMaxGlass(maxGlassD, taskD, copyYOffset);
                if (dest == null)
                {
                    stage = Stage.NEXT;
                    return;
                }
                tpNear(mc, dest);
                stage = Stage.PLACE_BEACON;
                nextMs = nowMs + 650L;
                return;

            case PLACE_BEACON:
                BlockTask taskP = floorTasks.get(blockIndex);
                BlockPos maxGlassP = taskP == null ? null : getFloorMaxGlass(mc, taskP.floorOffset);
                BlockPos placeAt = resolveBlockPosFromMaxGlass(maxGlassP, taskP, copyYOffset);
                if (placeAt == null)
                {
                    stage = Stage.NEXT;
                    return;
                }
                if (!placeBeacon(mc, placeAt))
                {
                    cancel("failed to place beacon");
                    return;
                }
                stage = Stage.WAIT_AFTER_PLACE;
                nextMs = nowMs + 2000L;
                return;

            case WAIT_AFTER_PLACE:
                sendChat(mc, "/ad " + id1);
                stage = Stage.WAIT_WORLD_1;
                nextMs = nowMs + 250L;
                markSwitchStart(mc);
                return;

            case WAIT_WORLD_1:
                if (switchReady(mc, nowMs))
                {
                    updateAvgSwitch(nowMs);
                    nextMs = nowMs + 1000L;
                    stage = Stage.DEV_1;
                }
                else
                {
                    nextMs = nowMs + 250L;
                }
                return;

            case DEV_1:
                sendChat(mc, "/dev");
                stage = Stage.WAIT_DEV_1;
                nextMs = nowMs + 1250L;
                return;

            case WAIT_DEV_1:
                if (!host.isDevCreativeScoreboard(mc))
                {
                    if ((nowMs - switchSentMs) > 15000L)
                    {
                        cancel("dev mode not available");
                        return;
                    }
                    stage = Stage.DEV_1;
                    nextMs = nowMs + 1500L;
                    return;
                }
                stage = Stage.NEXT;
                nextMs = nowMs + 350L;
                return;

            case NEXT:
                blockIndex++;
                stage = Stage.TP_TO_SOURCE;
                nextMs = nowMs + 250L;
                return;

            default:
                return;
        }
    }

    private void scanFloor(Minecraft mc, int floorOffset)
    {
        floorTasks.clear();
        currentFloorOffset = floorOffset;
        if (mc == null || mc.world == null)
        {
            return;
        }
        BlockPos seed = host.getLastGlassPos();
        if (seed == null)
        {
            return;
        }
        BlockPos floorSeed = seed.add(0, floorOffset, 0);
        List<BlockPos> scanned = BlueGlassCodeMap.scan(mc.world, Arrays.asList(floorSeed));
        if (scanned.isEmpty())
        {
            return;
        }
        BlockPos maxZ = null;
        for (BlockPos g : scanned)
        {
            if (g == null || g.getY() != floorSeed.getY())
            {
                continue;
            }
            if (maxZ == null || g.getZ() > maxZ.getZ())
            {
                maxZ = g;
            }
        }
        if (maxZ == null)
        {
            return;
        }
        floorMaxGlassCache.put(floorOffset, maxZ);

        LinkedHashSet<BlockTask> out = new LinkedHashSet<>();
        for (BlockPos g : scanned)
        {
            if (g == null)
            {
                continue;
            }
            if (g.getY() != floorSeed.getY())
            {
                continue;
            }
            BlockPos above = g.up();
            if (!mc.world.isBlockLoaded(above))
            {
                continue;
            }
            if (!mc.world.isAirBlock(above))
            {
                out.add(new BlockTask(floorOffset, g.getX() - maxZ.getX(), g.getZ() - maxZ.getZ()));
            }
        }
        floorTasks.addAll(out);
        floorsScanned++;
        blocksScannedTotal += floorTasks.size();
        if (host.isDebugUi())
        {
            host.debugChat("&e/copycode scan y=" + floorSeed.getY() + " blocks=" + floorTasks.size());
        }
    }

    private static List<Integer> parseFloors(String csv)
    {
        List<Integer> out = new ArrayList<>();
        if (csv == null)
        {
            return out;
        }
        for (String p : csv.split(","))
        {
            String s = p == null ? "" : p.trim();
            if (s.isEmpty())
            {
                continue;
            }
            try
            {
                out.add(Integer.parseInt(s));
            }
            catch (Exception ignore) { }
        }
        return out;
    }

    private BlockPos getFloorMaxGlass(Minecraft mc, int floorOffset)
    {
        if (mc == null || mc.world == null)
        {
            return null;
        }
        BlockPos cached = floorMaxGlassCache.get(floorOffset);
        if (cached != null)
        {
            return cached;
        }
        BlockPos base = host.getLastGlassPos();
        if (base == null || !mc.world.isBlockLoaded(base))
        {
            tryUpdateLastGlassFromPlayer(mc);
            base = host.getLastGlassPos();
        }
        if (base == null)
        {
            return null;
        }
        BlockPos floorSeed = base.add(0, floorOffset, 0);
        List<BlockPos> scanned = BlueGlassCodeMap.scan(mc.world, Arrays.asList(floorSeed));
        if (scanned.isEmpty())
        {
            return null;
        }
        BlockPos maxZ = null;
        for (BlockPos g : scanned)
        {
            if (g == null || g.getY() != floorSeed.getY())
            {
                continue;
            }
            if (maxZ == null || g.getZ() > maxZ.getZ())
            {
                maxZ = g;
            }
        }
        if (maxZ != null)
        {
            floorMaxGlassCache.put(floorOffset, maxZ);
        }
        return maxZ;
    }

    private static BlockPos resolveBlockPosFromMaxGlass(BlockPos maxGlass, BlockTask task, int extraY)
    {
        if (maxGlass == null || task == null)
        {
            return null;
        }
        return maxGlass.add(task.relX, 1 + extraY, task.relZ);
    }

    private void tryUpdateLastGlassFromPlayer(Minecraft mc)
    {
        if (mc == null || mc.world == null || mc.player == null)
        {
            return;
        }
        if (!host.isDevCreativeScoreboard(mc))
        {
            return;
        }
        BlockPos guess = new BlockPos((int) Math.floor(mc.player.posX) - 2, 0, (int) Math.floor(mc.player.posZ) - 2);
        BlockPos found = findBlueGlassNear(mc.world, guess, 6);
        if (found != null)
        {
            BlockPos prev = host.getLastGlassPos();
            if (prev == null || !prev.equals(found))
            {
                host.setLastGlassPos(found, mc.world.provider.getDimension());
                floorMaxGlassCache.clear();
            }
        }
    }

    private static BlockPos findBlueGlassNear(World world, BlockPos center, int r)
    {
        if (world == null || center == null)
        {
            return null;
        }
        if (isBlueGlass(world, center))
        {
            return center;
        }
        int rr = Math.max(1, Math.min(16, r));
        for (int dz = -rr; dz <= rr; dz++)
        {
            for (int dx = -rr; dx <= rr; dx++)
            {
                if (dx == 0 && dz == 0)
                {
                    continue;
                }
                BlockPos p = center.add(dx, 0, dz);
                if (isBlueGlass(world, p))
                {
                    return p;
                }
            }
        }
        return null;
    }

    private static boolean isBlueGlass(World world, BlockPos pos)
    {
        if (world == null || pos == null || !world.isBlockLoaded(pos))
        {
            return false;
        }
        try
        {
            IBlockState st = world.getBlockState(pos);
            Block b = st == null ? null : st.getBlock();
            if (b != Blocks.STAINED_GLASS)
            {
                return false;
            }
            return b.getMetaFromState(st) == BLUE_GLASS_META;
        }
        catch (Exception ignore)
        {
            return false;
        }
    }

    private void tpNear(Minecraft mc, BlockPos target)
    {
        if (mc == null || mc.player == null || mc.world == null || target == null)
        {
            return;
        }
        // Stand at z+2 (as requested) and same x/y.
        double tx = target.getX() + 0.5;
        double ty = target.getY();
        double tz = target.getZ() + 2.0 + 0.5;
        host.buildTpPathQueue(mc.world, mc.player.posX, mc.player.posY, mc.player.posZ, tx, ty, tz);
        try
        {
            int steps = host.tpPathQueueSize();
            if (steps > 0)
            {
                if (avgTpSteps < 0.0)
                {
                    avgTpSteps = steps;
                }
                else
                {
                    avgTpSteps = (avgTpSteps * 0.8) + (steps * 0.2);
                }
            }
        }
        catch (Exception ignore) { }
    }

    private static void ensureFlying(Minecraft mc)
    {
        try
        {
            if (mc == null || mc.player == null)
            {
                return;
            }
            if (!mc.player.capabilities.isFlying)
            {
                mc.player.capabilities.isFlying = true;
                mc.player.sendPlayerAbilities();
            }
        }
        catch (Exception ignore) { }
    }

    private static void pressShift(Minecraft mc)
    {
        if (mc == null || mc.player == null)
        {
            return;
        }
        try
        {
            if (mc.player.connection != null)
            {
                mc.player.connection.sendPacket(new CPacketEntityAction(mc.player, CPacketEntityAction.Action.START_SNEAKING));
            }
        }
        catch (Exception ignore) { }
        try
        {
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindSneak.getKeyCode(), true);
        }
        catch (Exception ignore) { }
    }

    private void releaseShift(Minecraft mc)
    {
        shiftHeld = false;
        if (mc == null || mc.player == null)
        {
            return;
        }
        try
        {
            if (mc.player.connection != null)
            {
                mc.player.connection.sendPacket(new CPacketEntityAction(mc.player, CPacketEntityAction.Action.STOP_SNEAKING));
            }
        }
        catch (Exception ignore) { }
        try
        {
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindSneak.getKeyCode(), false);
        }
        catch (Exception ignore) { }
    }

    private void markSwitchStart(Minecraft mc)
    {
        if (mc == null || mc.world == null || mc.player == null)
        {
            worldRefSwitch = 0;
            switchSentMs = System.currentTimeMillis();
            switchFromPos = null;
            floorMaxGlassCache.clear();
            return;
        }
        worldRefSwitch = System.identityHashCode(mc.world);
        switchSentMs = System.currentTimeMillis();
        switchFromPos = new Vec3d(mc.player.posX, mc.player.posY, mc.player.posZ);
        floorMaxGlassCache.clear();
    }

    private boolean switchReady(Minecraft mc, long nowMs)
    {
        if (mc == null || mc.world == null || mc.player == null)
        {
            return false;
        }
        if (worldRefSwitch != 0 && System.identityHashCode(mc.world) != worldRefSwitch)
        {
            return true;
        }
        if (switchFromPos != null)
        {
            double dx = mc.player.posX - switchFromPos.x;
            double dy = mc.player.posY - switchFromPos.y;
            double dz = mc.player.posZ - switchFromPos.z;
            if ((dx * dx + dy * dy + dz * dz) >= (32.0 * 32.0))
            {
                return true;
            }
        }
        // Fallback: proceed after some time, some servers keep the same world instance.
        return switchSentMs != 0L && (nowMs - switchSentMs) >= 8000L;
    }

    private static boolean closeUnexpectedGui(Minecraft mc)
    {
        if (mc == null)
        {
            return false;
        }
        GuiScreen s = mc.currentScreen;
        if (s == null)
        {
            return false;
        }
        if (s instanceof GuiChat || s instanceof GuiInventory || s instanceof GuiIngameMenu)
        {
            return false;
        }
        try
        {
            if (mc.player != null)
            {
                mc.player.closeScreen();
            }
            else
            {
                mc.displayGuiScreen(null);
            }
        }
        catch (Exception ignore) { }
        return true;
    }

    private void updateAvgSwitch(long nowMs)
    {
        if (switchSentMs <= 0L)
        {
            return;
        }
        long dt = Math.max(0L, nowMs - switchSentMs);
        if (avgSwitchMs < 0.0)
        {
            avgSwitchMs = dt;
        }
        else
        {
            avgSwitchMs = (avgSwitchMs * 0.8) + (dt * 0.2);
        }
    }

    private void showEta(long nowMs)
    {
        if (!active || stage == Stage.IDLE)
        {
            return;
        }
        int floorsTotal = floorOffsets == null ? 0 : floorOffsets.size();
        int floorHuman = Math.max(1, floorIndex + 1);
        int blocksTotal = floorTasks == null ? 0 : floorTasks.size();
        int blockHuman = Math.max(1, Math.min(blockIndex + 1, Math.max(1, blocksTotal)));

        int blocksLeftThisFloor = Math.max(0, blocksTotal - blockIndex);
        int floorsLeftAfterThis = Math.max(0, floorsTotal - floorIndex - 1);
        double avgBlocksPerFloor = (floorsScanned > 0) ? ((double) blocksScannedTotal / (double) floorsScanned) : (double) Math.max(1, blocksTotal);
        int blocksLeftFuture = (int) Math.round(avgBlocksPerFloor * floorsLeftAfterThis);
        int blocksLeft = blocksLeftThisFloor + blocksLeftFuture;

        long etaMs = estimateRemainingMs(blocksLeft);
        String eta = formatEta(etaMs);
        String avgLoad = formatMs(avgSwitchMs < 0.0 ? 0.0 : avgSwitchMs);
        host.setActionBar(true,
            "&e/copycode &7этаж " + floorHuman + "/" + Math.max(1, floorsTotal)
                + " &7блок " + blockHuman + "/" + Math.max(1, blocksTotal)
                + " &fETA " + eta
                + (avgSwitchMs > 0.0 ? " &8(load~" + avgLoad + ")" : ""),
            1100L);
    }

    private long estimateRemainingMs(int blocksLeft)
    {
        if (blocksLeft <= 0)
        {
            return 0L;
        }
        double switchMs = avgSwitchMs > 0.0 ? avgSwitchMs : 7000.0;
        double tpSteps = avgTpSteps > 0.0 ? avgTpSteps : 8.0;
        double tpMs = (tpSteps * 300.0) + 600.0;

        // Rough per-block time model, based on CopyCodeModule stage delays.
        double perBlock =
            // TP to source + settle
            tpMs
            // Shift + break + wait
            + 500.0 + 3000.0
            // /ad id2 + load + /dev wait
            + (switchMs + 1000.0 + 1250.0)
            // TP to dest + settle + place + wait
            + ((tpSteps * 300.0) + 650.0) + 2000.0
            // /ad id1 + load + /dev wait
            + (switchMs + 1000.0 + 1250.0);

        return (long) Math.max(0.0, perBlock * (double) blocksLeft);
    }

    private static String formatEta(long ms)
    {
        long s = Math.max(0L, ms / 1000L);
        long m = s / 60L;
        long ss = s % 60L;
        return String.format(Locale.ROOT, "%02d:%02d", m, ss);
    }

    private static String formatMs(double ms)
    {
        if (ms <= 0.0)
        {
            return "0s";
        }
        return String.format(Locale.ROOT, "%.1fs", ms / 1000.0);
    }

    private static void sendLookPacket(Minecraft mc, BlockPos target)
    {
        if (mc == null || mc.player == null || mc.player.connection == null || target == null)
        {
            return;
        }
        Vec3d eyes = mc.player.getPositionEyes(1.0F);
        Vec3d center = new Vec3d(target.getX() + 0.5, target.getY() + 0.5, target.getZ() + 0.5);
        double dx = center.x - eyes.x;
        double dy = center.y - eyes.y;
        double dz = center.z - eyes.z;
        double dist = Math.sqrt(dx * dx + dz * dz);
        if (dist < 0.0001)
        {
            return;
        }
        float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        float pitch = (float) (-Math.toDegrees(Math.atan2(dy, dist)));
        mc.player.connection.sendPacket(new CPacketPlayer.Rotation(yaw, pitch, mc.player.onGround));
    }

    private static void doBreak(Minecraft mc, BlockPos pos)
    {
        if (mc == null || mc.player == null || mc.world == null || mc.playerController == null || pos == null)
        {
            return;
        }
        sendLookPacket(mc, pos);
        try
        {
            if (mc.player.connection != null)
            {
                mc.player.connection.sendPacket(new CPacketPlayerDigging(CPacketPlayerDigging.Action.START_DESTROY_BLOCK, pos, EnumFacing.UP));
                mc.player.connection.sendPacket(new CPacketPlayerDigging(CPacketPlayerDigging.Action.STOP_DESTROY_BLOCK, pos, EnumFacing.UP));
            }
        }
        catch (Exception ignore) { }
        try
        {
            mc.playerController.onPlayerDamageBlock(pos, EnumFacing.UP);
            mc.playerController.clickBlock(pos, EnumFacing.UP);
        }
        catch (Exception ignore) { }
        try
        {
            mc.player.swingArm(EnumHand.MAIN_HAND);
        }
        catch (Exception ignore) { }
    }

    private static boolean placeBeacon(Minecraft mc, BlockPos placeAt)
    {
        if (mc == null || mc.player == null || mc.world == null || mc.playerController == null || placeAt == null)
        {
            return false;
        }
        if (!mc.world.isAirBlock(placeAt))
        {
            // already occupied
            return true;
        }

        int slot = -1;
        for (int i = 0; i < 9; i++)
        {
            ItemStack st = mc.player.inventory.getStackInSlot(i);
            if (!st.isEmpty() && st.getItem() == net.minecraft.item.Item.getItemFromBlock(Blocks.BEACON))
            {
                slot = i;
                break;
            }
        }
        if (slot < 0)
        {
            // Put beacon into current hotbar slot (best-effort).
            slot = mc.player.inventory.currentItem;
            ItemStack beacon = new ItemStack(Blocks.BEACON);
            mc.player.inventory.setInventorySlotContents(slot, beacon);
            if (mc.player.connection != null)
            {
                mc.player.connection.sendPacket(new net.minecraft.network.play.client.CPacketCreativeInventoryAction(36 + slot, beacon));
            }
        }

        if (mc.player.inventory.currentItem != slot)
        {
            mc.player.inventory.currentItem = slot;
            if (mc.player.connection != null)
            {
                mc.player.connection.sendPacket(new CPacketHeldItemChange(slot));
            }
        }

        BlockPos click = placeAt.down();
        Vec3d hit = new Vec3d(0.5, 0.5, 0.5);
        sendLookPacket(mc, placeAt);
        try
        {
            if (mc.player.connection != null)
            {
                mc.player.connection.sendPacket(new net.minecraft.network.play.client.CPacketPlayerTryUseItemOnBlock(
                    click, EnumFacing.UP, EnumHand.MAIN_HAND, (float) hit.x, (float) hit.y, (float) hit.z
                ));
            }
            mc.playerController.processRightClickBlock(mc.player, mc.world, click, EnumFacing.UP, hit, EnumHand.MAIN_HAND);
            mc.player.swingArm(EnumHand.MAIN_HAND);
        }
        catch (Exception ignore) { }
        return true;
    }

    private static void sendChat(Minecraft mc, String msg)
    {
        if (mc == null || mc.player == null || msg == null || msg.trim().isEmpty())
        {
            return;
        }
        mc.player.sendChatMessage(msg);
    }
}
