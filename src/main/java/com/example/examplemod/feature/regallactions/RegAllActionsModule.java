package com.example.examplemod.feature.regallactions;

import com.example.examplemod.model.ClickAction;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.inventory.GuiChest;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.command.ICommandSender;
import net.minecraft.init.Blocks; 
import net.minecraft.inventory.ContainerChest;
import net.minecraft.inventory.ClickType; 
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.Slot; 
import net.minecraft.item.ItemStack; 
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntitySign;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileInputStream;
import java.io.OutputStreamWriter;
import java.io.InputStreamReader;
import java.io.BufferedReader;
import java.io.InputStream;
import java.util.ArrayList; 
import java.util.Arrays;
import java.util.HashMap; 
import java.util.HashSet; 
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class RegAllActionsModule
{
    private final RegAllActionsHost host;
    private final RegAllActionsState state = new RegAllActionsState();
    private final Map<String, String> signAliases = new HashMap<>();
    private boolean signAliasesLoaded = false;
    private static final Logger LOGGER = LogManager.getLogger("BetterCode-RegAllActions");
    private static final Pattern FORMATTED_ITEM_RE = Pattern.compile("^\\[([^\\s]+) meta=(\\d+)\\]\\s*(.*)$");
    private static final int MENU_PAGE_TURN_MAX_RETRIES = 5;
    private static final long MENU_PAGE_TURN_TIMEOUT_MS = 1500L;
    private static final String NEXT_PAGE_PHRASE_1 = "нажми, чтобы открыть";
    private static final String NEXT_PAGE_PHRASE_2 = "следующую страницу";

    public RegAllActionsModule(RegAllActionsHost host)
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

    public void runDebugCommand(MinecraftServer server, ICommandSender sender, String[] args)
    {
        if (args != null && args.length > 0)
        {
            String val = args[0].toLowerCase(Locale.ROOT);
            if ("on".equals(val) || "1".equals(val) || "true".equals(val))
            {
                state.debugSlow = true;
            }
            else if ("off".equals(val) || "0".equals(val) || "false".equals(val))
            {
                state.debugSlow = false;
            }
            else
            {
                state.debugSlow = !state.debugSlow;
            }
        }
        else
        {
            state.debugSlow = !state.debugSlow;
        }
        host.setActionBar(true, "&eRegAllActions debug=" + (state.debugSlow ? "ON" : "OFF"), 2500L);
    }

    public void runCommand(MinecraftServer server, ICommandSender sender, String[] args)
    {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.world == null || mc.player == null)
        {
            host.setActionBar(false, "&cNo world/player", 2500L);
            return;
        }

        if (args != null && args.length > 0 && "stop".equalsIgnoreCase(args[0]))
        {
            reset();
            host.setActionBar(true, "&cRegAllActions stopped", 2000L);
            return;
        }

        if (args != null && args.length > 0 && "export".equalsIgnoreCase(args[0]))
        {
            exportRecords(mc);
            return;
        }

        boolean resaveMode = false;
        boolean resaveCategoryMode = false;
        String resaveQuery = "";
        if (args != null && args.length > 0)
        {
            if ("resavecat".equalsIgnoreCase(args[0]) || "resavecategory".equalsIgnoreCase(args[0]))
            {
                resaveMode = true;
                resaveCategoryMode = true;
                resaveQuery = sanitizeResaveQuery(joinArgs(args, 1));
            }
            else if ("resave".equalsIgnoreCase(args[0]) || "recache".equalsIgnoreCase(args[0]))
            {
                resaveMode = true;
                if (args.length >= 2)
                {
                    String mode = args[1].toLowerCase(Locale.ROOT);
                    if ("category".equals(mode) || "cat".equals(mode) || "\u043a\u0430\u0442\u0435\u0433\u043e\u0440\u0438\u044f".equals(mode))
                    {
                        resaveCategoryMode = true;
                        resaveQuery = sanitizeResaveQuery(joinArgs(args, 2));
                    }
                    else
                    {
                        resaveQuery = sanitizeResaveQuery(joinArgs(args, 1));
                    }
                }
                else
                {
                    resaveQuery = sanitizeResaveQuery(joinArgs(args, 1));
                }
            }
        }
        if (resaveMode && (resaveQuery == null || resaveQuery.trim().isEmpty()))
        {
            host.setActionBar(false, "&cUsage: /regallactions resave <text>  or  /regallactions resave category <text>", 4000L);
            return;
        }

        if (!host.isEditorModeActive() || !host.isDevCreativeScoreboard(mc))
        {
            host.setActionBar(false, "&cNot in DEV creative mode", 2500L);
            return;
        }

        BlockPos base = host.getLastClickedPos();
        BlockPos signPos = null;
        if (base != null)
        {
            if (host.isLastClickedSign())
            {
                signPos = base;
            }
            else
            {
                BlockPos[] candidates = new BlockPos[]{
                    base,
                    base.north(),
                    base.south(),
                    base.east(),
                    base.west(),
                    base.down(),
                    base.up()
                };
                for (BlockPos p : candidates)
                {
                    if (p == null) continue;
                    TileEntity te = mc.world.getTileEntity(p);
                    if (te instanceof TileEntitySign)
                    {
                        signPos = p;
                        break;
                    }
                }
            }
        }

        if (signPos == null)
        {
            signPos = findNearestSign(mc, 6);
        }

        if (signPos == null)
        {
            host.setActionBar(false, "&cNo sign found near last click (right-click a sign first)", 3500L);
            return;
        }

        reset();
        ensureImportLoaded(mc);
        state.active = true;
        state.signPos = signPos;
        state.nextActionMs = 0L;
        state.menuLevel = 0;
        state.currentCategoryKey = null;
        state.currentSubItemKey = null;
        state.currentCategoryStack = ItemStack.EMPTY;
        state.currentSubItemStack = ItemStack.EMPTY;
        state.resaveMode = resaveMode;
        state.resaveCategoryMode = resaveCategoryMode;
        state.resaveQueryNorm = resaveMode ? normalizeForKey(resaveQuery) : null;
        state.resaveReplaceLooseKeys.clear();

        if (state.resaveMode && state.resaveQueryNorm != null && !state.resaveQueryNorm.isEmpty())
        {
            String mode = state.resaveCategoryMode ? "category" : "action";
            host.setActionBar(true, "&eRegAllActions resave (" + mode + "): " + resaveQuery, 3500L);
        }
        else
        {
            host.setActionBar(true, "&aRegAllActions started", 2000L);
        }
        if (host.isDebugUi())
        {
            host.debugChat("/regallactions: sign=" + signPos.toString());
        }
    }

    private static String joinArgs(String[] args, int start)
    {
        if (args == null || args.length <= start)
        {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = start; i < args.length; i++)
        {
            if (i > start) sb.append(" ");
            sb.append(args[i]);
        }
        return sb.toString().trim();
    }

    private static String sanitizeResaveQuery(String s)
    {
        if (s == null)
        {
            return "";
        }
        String t = s.trim();
        // Minecraft client commands don't parse quotes; they come through as literal characters.
        // Accept both: /regallactions resave сообщение  and /regallactions resave "Отправить сообщение"
        if (t.length() >= 2)
        {
            if ((t.startsWith("\"") && t.endsWith("\"")) || (t.startsWith("'") && t.endsWith("'")))
            {
                t = t.substring(1, t.length() - 1).trim();
            }
        }
        // Remove any remaining quote characters (user may have mixed quoting across args).
        t = t.replace("\"", "").replace("'", "").trim();
        return t;
    }

    private BlockPos findNearestSign(Minecraft mc, int radius)
    {
        if (mc == null || mc.world == null || mc.player == null)
        {
            return null;
        }
        BlockPos origin = mc.player.getPosition();
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        for (int dx = -radius; dx <= radius; dx++)
        {
            for (int dy = -radius; dy <= radius; dy++)
            {
                for (int dz = -radius; dz <= radius; dz++)
                {
                    BlockPos p = origin.add(dx, dy, dz);
                    TileEntity te = mc.world.getTileEntity(p);
                    if (te instanceof TileEntitySign)
                    {
                        double dist = origin.distanceSq(p);
                        if (dist < bestDist)
                        {
                            bestDist = dist;
                            best = p;
                        }
                    }
                }
            }
        }
        return best;
    }

    public void onClientTick(Minecraft mc, long nowMs)
    {
        if (!state.active || host == null || mc == null)
        {
            return;
        }
        if (mc.world == null || mc.player == null)
        {
            reset();
            return;
        }
        if (!host.isEditorModeActive() || !host.isDevCreativeScoreboard(mc))
        {
            reset();
            return;
        }
        if (host.isHoldingIronOrGoldIngot(mc) || host.isInputActive())
        {
            return;
        }

        GuiScreen screen = mc.currentScreen;
        if (screen != null)
        {
            if (!(screen instanceof GuiContainer))
            {
                host.closeCurrentScreen();
            }
            return;
        }

        if (state.pendingClick)
        {
            if (state.pendingIsReplay)
            {
                debugLog("replay click closed gui (abort)");
                state.pendingClick = false;
                state.pendingIsReplay = false;
                state.replaying = false;
                state.waitingForCursorClear = false;
                state.cursorClearSinceMs = 0L;
                state.nextActionMs = nowMs + actionDelayMs();
                return;
            }

            // In resave mode, do not record/cache actions that don't match the query.
            // This prevents "first random action got resaved" when the server closes the menu quickly.
            if (state.resaveMode && state.resaveQueryNorm != null && !state.resaveQueryNorm.isEmpty())
            {
                if (!matchesResaveQuery(state.pendingClickStack))
                {
                    debugLog("resave skip non-matching action rawName='" + state.pendingClickStack.getDisplayName()
                        + "' rawLore='" + readLore(state.pendingClickStack) + "'");
                    state.pendingClick = false;
                    state.pendingClickMs = 0L;
                    state.pendingClickKey = null;
                    state.pendingClickStack = ItemStack.EMPTY;
                    state.pendingGuiTitle = "";
                    state.waitingForCursorClear = false;
                    state.cursorClearSinceMs = 0L;
                    finishActionAndReplay(nowMs);
                    debugBar("resave skip");
                    return;
                }
            }

            state.currentActionStack = state.pendingClickStack.copy();
            state.currentActionGuiTitle = state.pendingGuiTitle;
            state.pendingClick = false;
            state.pendingClickMs = 0L;
            state.pendingClickKey = null;
            state.pendingClickStack = ItemStack.EMPTY;
            state.pendingGuiTitle = "";
            state.waitingForCursorClear = false;
            state.cursorClearSinceMs = 0L;
            state.waitingForChest = true;
            state.chestSpawnStartMs = nowMs;
            state.chestSpawnDeadlineMs = nowMs + 4000L;
            state.nextActionMs = nowMs + actionDelayMs();
            debugBar("action -> wait chest");
            return;
        }

        if (nowMs < state.nextActionMs)
        {
            return;
        }

        if (state.openingChest && nowMs > state.chestOpenDeadlineMs)
        {
            GuiChest timedOutGui = (mc.currentScreen instanceof GuiChest) ? (GuiChest) mc.currentScreen : null;
            boolean hasChestAtTimeout = timedOutGui != null;
            if (hasChestAtTimeout)
            {
                LOGGER.warn("RGA_CHEST timeout but chest still open; forcing snapshot. state={}",
                    host.describeChestSnapshotState(timedOutGui));
            }
            RegAllRecord record = buildRecord(hasChestAtTimeout, timedOutGui);
            state.records.add(record);
            if (onRecordAdded(mc, record))
            {
                return;
            }
            finishActionAndReplay(nowMs);
            return;
        }

        BlockPos signPos = state.signPos;
        if (signPos == null)
        {
            reset();
            return;
        }

        if (state.waitingForChest)
        {
            BlockPos chestPos = signPos.add(0, 1, 1);
            if (mc.world.getBlockState(chestPos).getBlock() != Blocks.TRAPPED_CHEST)
            {
                if (state.chestSpawnStartMs == 0L)
                {
                    state.chestSpawnStartMs = nowMs;
                    state.chestSpawnDeadlineMs = nowMs + 4000L;
                }
                if (nowMs < state.chestSpawnDeadlineMs)
                {
                    if (host.isDebugUi())
                    {
                        String blk = "";
                        try
                        {
                            blk = String.valueOf(mc.world.getBlockState(chestPos).getBlock().getRegistryName());
                        }
                        catch (Exception ignore) { }
                        debugLog("waiting chest spawn at " + chestPos + " block=" + blk
                            + " elapsedMs=" + (nowMs - state.chestSpawnStartMs));
                    }
                    state.nextActionMs = nowMs + 120L;
                    return;
                }
                RegAllRecord record = buildRecord(false, null);
                state.records.add(record);
                if (onRecordAdded(mc, record))
                {
                    return;
                }
                finishActionAndReplay(nowMs);
                debugBar("no chest");
                return;
            }
            if (host.isDebugUi())
            {
                debugLog("chest spawned at " + chestPos + " afterMs=" + (nowMs - state.chestSpawnStartMs));
            }
            try
            {
                mc.playerController.processRightClickBlock(
                    mc.player, mc.world, chestPos,
                    EnumFacing.UP, new Vec3d(0.5, 0.5, 0.5),
                    EnumHand.MAIN_HAND
                );
                mc.player.swingArm(EnumHand.MAIN_HAND);
            }
            catch (Exception ignore) { }

            state.waitingForChest = false;
            state.openingChest = true;
            state.chestWaitStartMs = nowMs;
            state.chestOpenDeadlineMs = nowMs + 7000L;
            state.chestReadySinceMs = 0L;
            state.chestReadyWindowId = -1;
            state.chestWaitLogMs = 0L;
            state.chestSpawnStartMs = 0L;
            state.chestSpawnDeadlineMs = 0L;
            state.nextActionMs = nowMs + actionDelayMs();
            debugBar("open chest");
            return;
        }

        // open category menu (right-click sign)
        if (state.openingSign && nowMs - state.signClickMs < 800L)
        {
            return;
        }
        try
        {
            mc.playerController.processRightClickBlock(
                mc.player, mc.world, signPos,
                EnumFacing.UP, new Vec3d(0.5, 0.5, 0.5),
                EnumHand.MAIN_HAND
            );
            mc.player.swingArm(EnumHand.MAIN_HAND);
        }
        catch (Exception ignore) { }
        state.openingSign = true;
        state.signClickMs = nowMs;
        state.nextActionMs = nowMs + actionDelayMs();
        debugBar("open sign");
    }

    public void onGuiTick(GuiContainer gui, long nowMs)
    {
        if (!state.active || host == null || gui == null)
        {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.player == null || mc.player.openContainer == null)
        {
            return;
        }
        if (host.isInputActive())
        {
            return;
        }

        if (state.waitingForCursorClear)
        {
            boolean empty = true;
            try
            {
                if (mc.player.inventory.getItemStack() != null && !mc.player.inventory.getItemStack().isEmpty())
                {
                    empty = false;
                }
            }
            catch (Exception ignore) { }
            if (!empty)
            {
                state.cursorClearSinceMs = 0L;
                return;
            }
            if (state.cursorClearSinceMs == 0L)
            {
                state.cursorClearSinceMs = nowMs;
                return;
            }
            if (nowMs - state.cursorClearSinceMs < 50L)
            {
                return;
            }
            state.waitingForCursorClear = false;
            state.cursorClearSinceMs = 0L;
            if (state.pendingClick)
            {
                if (state.pendingIsReplay)
                {
                    state.pendingClick = false;
                    state.pendingIsReplay = false;
                    state.pendingClickMs = 0L;
                    state.replayIndex++;
                    state.menuReadySinceMs = 0L;
                    state.menuReadyWindowId = -1;
                    state.nextActionMs = nowMs + actionDelayMs();
                    debugBar("replay ok");
                    return;
                }
                String key = state.pendingClickKey;
                ItemStack st = state.pendingClickStack.copy();
                state.pendingClick = false;
                state.pendingClickMs = 0L;
                state.pendingClickKey = null;
                state.pendingClickStack = ItemStack.EMPTY;
                state.pendingGuiTitle = "";
                state.menuPathKeys.add(key);
                state.menuPathStacks.add(st);
                state.pathCount++;
                state.menuReadySinceMs = 0L;
                state.menuReadyWindowId = -1;
                state.nextActionMs = nowMs + actionDelayMs();
                debugBar("category");
                debugLog("category key=" + key
                    + " rawName='" + st.getDisplayName() + "' rawLore='" + readLore(st)
                    + "' path=" + RegAllActionsState.pathKey(state.menuPathKeys));
                if (state.resaveMode && state.resaveCategoryMode)
                {
                    state.resaveCaptureMenu = true;
                    state.resaveCaptureStartMs = nowMs;
                    try
                    {
                        state.resaveCaptureSourceWindowId = mc.player.openContainer.windowId;
                    }
                    catch (Exception ignore) { }
                    debugBar("resave cat: wait menu");
                }
                return;
            }
        }

        if (nowMs < state.nextActionMs)
        {
            return;
        }

        if (state.menuPageTurnPending)
        {
            String currentHash = buildTopInventoryHash(gui);
            if (!currentHash.equals(state.menuPageLastHash))
            {
                state.menuPageTurnPending = false;
                state.menuPageRetryCount = 0;
                state.menuPageTurnStartMs = 0L;
                state.menuPageLastHash = currentHash;
                state.menuPageIndex++;
                state.nextActionMs = nowMs + actionDelayMs();
                debugLog("page turn ok page=" + state.menuPageIndex + " path=" + RegAllActionsState.pathKey(state.menuPathKeys));
                debugBar("page " + state.menuPageIndex);
                return;
            }
            if (nowMs - state.menuPageTurnStartMs >= MENU_PAGE_TURN_TIMEOUT_MS)
            {
                if (state.menuPageRetryCount >= MENU_PAGE_TURN_MAX_RETRIES)
                {
                    state.menuPageTurnPending = false;
                    state.menuPageRetryCount = 0;
                    state.menuPageTurnStartMs = 0L;
                    host.setActionBar(false, "&eWARN: page turn timeout, partial export", 3000L);
                    debugLog("page turn timeout retries exhausted path=" + RegAllActionsState.pathKey(state.menuPathKeys));
                    return;
                }
                Slot arrow = findNextPageArrowSlot(gui);
                if (arrow == null)
                {
                    state.menuPageTurnPending = false;
                    state.menuPageRetryCount = 0;
                    state.menuPageTurnStartMs = 0L;
                    host.setActionBar(false, "&eWARN: next page arrow missing, partial export", 3000L);
                    debugLog("page turn timeout + arrow missing path=" + RegAllActionsState.pathKey(state.menuPathKeys));
                    return;
                }
                state.menuPageRetryCount++;
                state.menuPageTurnStartMs = nowMs;
                state.menuPageLastHash = currentHash;
                queueClickFromRegAllActions(arrow.slotNumber, "page-turn-retry");
                state.waitingForCursorClear = true;
                state.cursorClearSinceMs = 0L;
                state.nextActionMs = nowMs + actionDelayMs();
                debugLog("page turn retry " + state.menuPageRetryCount + "/" + MENU_PAGE_TURN_MAX_RETRIES
                    + " slot=" + arrow.slotNumber + " path=" + RegAllActionsState.pathKey(state.menuPathKeys));
                return;
            }
            return;
        }

        if (state.pendingClick && !state.pendingIsReplay)
        {
            if (nowMs - state.pendingClickMs > 1500L)
            {
                debugLog("click timeout key=" + state.pendingClickKey);
                state.pendingClick = false;
                state.pendingClickKey = null;
                state.pendingClickStack = ItemStack.EMPTY;
                state.pendingGuiTitle = "";
                state.pendingClickMs = 0L;
                state.waitingForCursorClear = false;
                state.cursorClearSinceMs = 0L;
                state.nextActionMs = nowMs + actionDelayMs();
            }
            return;
        }

        if (state.openingChest)
        {
            if (!(gui instanceof GuiChest))
            {
                return;
            }
            int windowId = mc.player.openContainer.windowId;
            if (state.chestReadyWindowId != windowId)
            {
                state.chestReadyWindowId = windowId;
                state.chestReadySinceMs = nowMs;
                return;
            }
            if (nowMs - state.chestReadySinceMs < 300L)
            {
                return;
            }
            GuiChest chestGui = (GuiChest) gui;
            boolean snapshotStable = host.isChestSnapshotStable(chestGui);
            if (!snapshotStable)
            {
                if (nowMs - state.chestWaitLogMs >= 250L)
                {
                    state.chestWaitLogMs = nowMs;
                    LOGGER.info("RGA_CHEST wait_stable elapsedMs={} timeoutInMs={} state={}",
                        Math.max(0L, nowMs - state.chestWaitStartMs),
                        Math.max(0L, state.chestOpenDeadlineMs - nowMs),
                        host.describeChestSnapshotState(chestGui));
                }
                if (nowMs < state.chestOpenDeadlineMs)
                {
                    state.nextActionMs = nowMs + 80L;
                    return;
                }
                LOGGER.warn("RGA_CHEST stable_wait_timeout; proceeding with fallback snapshot. state={}",
                    host.describeChestSnapshotState(chestGui));
            }
            boolean params = isParamsChestGui(chestGui);
            if (!params)
            {
                debugLog("opened non-params GUI after action; treating as no-chest. title=" + getGuiTitleSafe(gui));
            }
            RegAllRecord record = buildRecord(params, chestGui);
            state.records.add(record);
            state.cachedCount++;
            if (onRecordAdded(mc, record))
            {
                return;
            }
            state.openingChest = false;
            host.closeCurrentScreen();
            finishActionAndReplay(nowMs);
            debugBar("cached chest");
            return;
        }

        if (!hasTopInventory(gui))
        {
            return;
        }
        if (!isMenuStable(gui, nowMs))
        {
            return;
        }
        state.openingSign = false;

        if (state.resaveMode && state.resaveCategoryMode && state.resaveCaptureMenu)
        {
            if (!(gui instanceof GuiChest))
            {
                return;
            }
            int windowId = mc.player.openContainer.windowId;
            if (state.resaveCaptureSourceWindowId == windowId && nowMs - state.resaveCaptureStartMs < 2500L)
            {
                state.nextActionMs = nowMs + 120L;
                return;
            }
            if (nowMs - state.resaveCaptureStartMs > 3500L)
            {
                host.setActionBar(false, "&cResave category failed: menu did not open", 3000L);
                reset();
                return;
            }
            GuiChest chestGui = (GuiChest) gui;
            if (looksLikeCategoryRootMenu(chestGui))
            {
                state.nextActionMs = nowMs + 120L;
                return;
            }
            RegAllRecord record = buildCategoryMenuRecord(chestGui);
            state.records.add(record);
            state.cachedCount++;
            state.resaveCaptureMenu = false;
            if (onRecordAdded(mc, record))
            {
                return;
            }
            host.closeCurrentScreen();
            reset();
            return;
        }

        if (state.replaying)
        {
            if (state.replayIndex >= state.menuPathKeys.size())
            {
                state.replaying = false;
                state.replayIndex = 0;
                state.nextActionMs = nowMs + actionDelayMs();
                return;
            }
            String key = state.menuPathKeys.get(state.replayIndex);
            Slot slot = findSlotByKey(gui, key);
            if (slot == null)
            {
                debugLog("replay slot missing key=" + key + " path=" + RegAllActionsState.pathKey(state.menuPathKeys));
                state.replaying = false;
                state.replayIndex = 0;
                state.nextActionMs = nowMs + actionDelayMs();
                return;
            }
            ItemStack st = slot.getStack();
            state.pendingClick = true;
            state.pendingIsReplay = true;
            state.pendingClickKey = key;
            state.pendingClickStack = st.copy();
            state.pendingClickMs = nowMs;
            state.pendingGuiTitle = getGuiTitleSafe(gui);
            queueClickFromRegAllActions(slot.slotNumber, "replay");
            state.waitingForCursorClear = true;
            state.cursorClearSinceMs = 0L;
            state.nextActionMs = nowMs + actionDelayMs();
            debugBar("replay click");
            return;
        }

        String pathKey = RegAllActionsState.pathKey(state.menuPathKeys);
        Set<String> done = doneSetForPath(pathKey);
        Slot next = findNextMenuSlot(gui, pathKey, done);
        if (next == null)
        {
            if (tryStartPageTurn(gui, nowMs))
            {
                return;
            }
            if (state.menuPathKeys.isEmpty())
            {
                if (state.resaveMode)
                {
                    host.closeCurrentScreen();
                    host.setActionBar(false, "&cResave target not found", 3500L);
                    reset();
                    return;
                }
                state.active = false;
                host.closeCurrentScreen();
                host.setActionBar(true, "&aRegAllActions done (cached=" + state.cachedCount + ")", 3500L);
                debugLog("done: no slots left");
                return;
            }
            popMenuPath();
            state.replaying = true;
            state.replayIndex = 0;
            host.closeCurrentScreen();
            state.nextActionMs = nowMs + actionDelayMs();
            debugBar("menu done");
            return;
        }
        ItemStack stack = next.getStack();
        String key = buildMenuItemKey(stack, next.slotNumber);
        done.add(key);
        state.pendingClick = true;
        state.pendingIsReplay = false;
        state.pendingClickKey = key;
        state.pendingClickStack = stack.copy();
        state.pendingClickMs = nowMs;
        state.pendingGuiTitle = getGuiTitleSafe(gui);
        queueClickFromRegAllActions(next.slotNumber, "menu-click");
        state.waitingForCursorClear = true;
        state.cursorClearSinceMs = 0L;
        state.nextActionMs = nowMs + actionDelayMs();
        debugBar("click item");
        debugLog("click item key=" + key
            + " rawName='" + stack.getDisplayName() + "' rawLore='" + readLore(stack) + "' slot=" + next.slotNumber
            + " done=" + done.toString());
    }

    private boolean hasTopInventory(GuiContainer gui)
    {
        if (gui == null || gui.inventorySlots == null)
        {
            return false;
        }
        for (Slot slot : gui.inventorySlots.inventorySlots)
        {
            if (slot == null || host.isPlayerInventorySlot(gui, slot))
            {
                continue;
            }
            if (slot.getStack() != null && !slot.getStack().isEmpty())
            {
                return true;
            }
        }
        return false;
    }

    private boolean isMenuStable(GuiContainer gui, long nowMs)
    {
        int windowId = Minecraft.getMinecraft().player.openContainer.windowId;
        if (state.menuReadyWindowId != windowId)
        {
            state.menuReadyWindowId = windowId;
            state.menuReadySinceMs = nowMs;
            return false;
        }
        return nowMs - state.menuReadySinceMs >= 250L;
    }

    private String getGuiTitleSafe(GuiContainer gui)
    {
        if (gui instanceof GuiChest)
        {
            return host.getGuiTitle((GuiChest) gui);
        }
        return "";
    }

    private boolean tryStartPageTurn(GuiContainer gui, long nowMs)
    {
        if (!isRegAllMenuForPagination(gui))
        {
            debugLog("page turn skipped: not regallactions menu title=" + getGuiTitleSafe(gui)
                + " path=" + RegAllActionsState.pathKey(state.menuPathKeys));
            return false;
        }
        Slot arrow = findNextPageArrowSlot(gui);
        if (arrow == null)
        {
            return false;
        }
        state.menuPageTurnPending = true;
        state.menuPageRetryCount = 0;
        state.menuPageTurnStartMs = nowMs;
        state.menuPageLastHash = buildTopInventoryHash(gui);
        queueClickFromRegAllActions(arrow.slotNumber, "page-turn-start");
        state.waitingForCursorClear = true;
        state.cursorClearSinceMs = 0L;
        state.nextActionMs = nowMs + actionDelayMs();
        debugLog("page turn start page=" + state.menuPageIndex + " slot=" + arrow.slotNumber
            + " path=" + RegAllActionsState.pathKey(state.menuPathKeys));
        return true;
    }

    private boolean isRegAllMenuForPagination(GuiContainer gui)
    {
        if (!(gui instanceof GuiChest))
        {
            return false;
        }
        GuiChest chest = (GuiChest) gui;
        return looksLikeActionListMenu(chest) || looksLikeCategoryRootMenu(chest);
    }

    private String buildTopInventoryHash(GuiContainer gui)
    {
        StringBuilder sb = new StringBuilder();
        if (gui == null || gui.inventorySlots == null)
        {
            return "";
        }
        for (Slot slot : gui.inventorySlots.inventorySlots)
        {
            if (slot == null || host.isPlayerInventorySlot(gui, slot))
            {
                continue;
            }
            sb.append(slot.slotNumber).append(':').append(buildMenuItemKey(slot.getStack(), slot.slotNumber)).append(';');
        }
        return Integer.toHexString(sb.toString().hashCode());
    }

    private Slot findNextPageArrowSlot(GuiContainer gui)
    {
        if (gui == null || gui.inventorySlots == null)
        {
            return null;
        }
        Slot last = null;
        for (Slot slot : gui.inventorySlots.inventorySlots)
        {
            if (slot == null || host.isPlayerInventorySlot(gui, slot))
            {
                continue;
            }
            if (last == null || slot.slotNumber > last.slotNumber)
            {
                last = slot;
            }
        }
        if (last == null)
        {
            return null;
        }
        ItemStack st = last.getStack();
        if (st == null || st.isEmpty())
        {
            return null;
        }
        if (st.getItem() == null || st.getItem().getRegistryName() == null)
        {
            return null;
        }
        String reg = st.getItem().getRegistryName().toString().toLowerCase(Locale.ROOT);
        if (!reg.contains("arrow"))
        {
            return null;
        }
        String lore = normalizeForKey(readLore(st));
        if (lore.isEmpty())
        {
            return null;
        }
        return (lore.contains(NEXT_PAGE_PHRASE_1) && lore.contains(NEXT_PAGE_PHRASE_2)) ? last : null;
    }

    private void queueClickFromRegAllActions(int slotNumber, String reason)
    {
        LOGGER.info("RGA_CLICK reason={} slot={} active={} path={}",
            reason, slotNumber, state.active, RegAllActionsState.pathKey(state.menuPathKeys));
        debugLog("RGA_CLICK reason=" + reason + " slot=" + slotNumber
            + " active=" + state.active
            + " path=" + RegAllActionsState.pathKey(state.menuPathKeys));
        host.queueClick(new ClickAction(slotNumber, 0, ClickType.PICKUP));
    }

    private Set<String> doneSetForPath(String pathKey)
    {
        Set<String> done = state.doneByPath.get(pathKey);
        if (done == null)
        {
            done = new HashSet<>();
            state.doneByPath.put(pathKey, done);
        }
        return done;
    }

    private Slot findNextMenuSlot(GuiContainer gui, String pathKey, Set<String> done)
    {
        if (gui == null || done == null)
        {
            return null;
        }
        String guiTitle = getGuiTitleSafe(gui);
        // In resave mode we should avoid clicking unrelated actions.
        // However, menus can contain nested categories; don't prune those.
        // Heuristic: if the menu looks like an action list ("Данное действие/событие..."), we only click matching items.
        // If there are no matching items in such a menu, return null to back out to the previous menu.
        boolean resaveActive = state.resaveMode && state.resaveQueryNorm != null && !state.resaveQueryNorm.isEmpty();
        if (resaveActive && state.resaveCategoryMode)
        {
            // Resave-category should ONLY click category items, never actions.
            // Categories can exist at multiple nesting levels; we treat any action-list menu as terminal.
            if (looksLikeActionListMenu(gui))
            {
                debugLog("resave(category): reached action-list menu; backing out. title=" + guiTitle
                    + " path=" + RegAllActionsState.pathKey(state.menuPathKeys));
                return null;
            }

            boolean anyMatch = false;
            for (Slot slot : gui.inventorySlots.inventorySlots)
            {
                if (slot == null || host.isPlayerInventorySlot(gui, slot))
                {
                    continue;
                }
                ItemStack st = slot.getStack();
                if (st == null || st.isEmpty())
                {
                    continue;
                }
                String k = buildMenuItemKey(st, slot.slotNumber);
                if (k == null || k.trim().isEmpty())
                {
                    continue;
                }
                if (!matchesResaveQuery(st))
                {
                    done.add(k);
                    continue;
                }
                anyMatch = true;
                if (!done.contains(k))
                {
                    return slot;
                }
            }
            if (!anyMatch)
            {
                debugLog("resave(category): no matches in category menu; backing out. title=" + guiTitle
                    + " path=" + RegAllActionsState.pathKey(state.menuPathKeys));
            }
            return null;
        }
        boolean restrictToMatches = false;
        if (resaveActive && looksLikeActionListMenu(gui))
        {
            boolean anyMatch = false;
            for (Slot slot : gui.inventorySlots.inventorySlots)
            {
                if (slot == null || host.isPlayerInventorySlot(gui, slot))
                {
                    continue;
                }
                ItemStack st = slot.getStack();
                if (st == null || st.isEmpty())
                {
                    continue;
                }
                if (matchesResaveQuery(st))
                {
                    anyMatch = true;
                    break;
                }
            }
            if (!anyMatch)
            {
                debugLog("resave: no matches in action-list menu; backing out. title=" + guiTitle
                    + " path=" + RegAllActionsState.pathKey(state.menuPathKeys));
                return null;
            }
            restrictToMatches = true;
        }
        for (Slot slot : gui.inventorySlots.inventorySlots)
        {
            if (slot == null || host.isPlayerInventorySlot(gui, slot))
            {
                continue;
            }
            ItemStack st = slot.getStack();
            if (st == null || st.isEmpty())
            {
                continue;
            }
            String k = buildMenuItemKey(st, slot.slotNumber);
            if (k == null || k.trim().isEmpty())
            {
                continue;
            }
            if (restrictToMatches && !matchesResaveQuery(st))
            {
                done.add(k);
                continue;
            }
            if (isImportedRecord(pathKey, guiTitle, st))
            {
                if (!state.resaveMode)
                {
                    done.add(k);
                    debugLog("skip item (imported) key=" + k + " rawName='" + st.getDisplayName() + "'");
                    continue;
                }
            }
            if (!done.contains(k))
            {
                return slot;
            }
        }
        return null;
    }

    private boolean looksLikeActionListMenu(GuiContainer gui)
    {
        if (!(gui instanceof GuiChest))
        {
            return false;
        }
        GuiChest chest = (GuiChest) gui;
        // Root category menus are not action lists (they contain mostly navigation).
        if (looksLikeCategoryRootMenu(chest))
        {
            return false;
        }
        // Detect common RU phrases from action items.
        // Use unicode escapes so this heuristic is not broken by source/console encoding issues.
        final String PHRASE_ACTION = "\u0434\u0430\u043d\u043d\u043e\u0435 \u0434\u0435\u0439\u0441\u0442\u0432\u0438\u0435"; // "данное действие"
        final String PHRASE_EVENT = "\u0434\u0430\u043d\u043d\u043e\u0435 \u0441\u043e\u0431\u044b\u0442\u0438\u0435";   // "данное событие"
        final String PHRASE_RUNS_CODE = "\u0432\u044b\u043f\u043e\u043b\u043d\u044f\u0435\u0442 \u043a\u043e\u0434";     // "выполняет код"
        for (Slot slot : chest.inventorySlots.inventorySlots)
        {
            if (slot == null || host.isPlayerInventorySlot(chest, slot))
            {
                continue;
            }
            ItemStack st = slot.getStack();
            if (st == null || st.isEmpty())
            {
                continue;
            }
            String lore = normalizeForKey(readLore(st));
            if (lore.contains(PHRASE_ACTION) || lore.contains(PHRASE_EVENT) || lore.contains(PHRASE_RUNS_CODE))
            {
                return true;
            }
        }
        return false;
    }

    private void popMenuPath()
    {
        if (!state.menuPathKeys.isEmpty())
        {
            state.menuPathKeys.remove(state.menuPathKeys.size() - 1);
        }
        if (!state.menuPathStacks.isEmpty())
        {
            state.menuPathStacks.remove(state.menuPathStacks.size() - 1);
        }
    }

    private void finishActionAndReplay(long nowMs)
    {
        state.waitingForChest = false;
        state.openingChest = false;
        state.chestSpawnStartMs = 0L;
        state.chestSpawnDeadlineMs = 0L;
        state.chestReadySinceMs = 0L;
        state.chestReadyWindowId = -1;
        state.replaying = !state.menuPathKeys.isEmpty();
        state.replayIndex = 0;
        state.currentActionStack = ItemStack.EMPTY;
        state.currentActionGuiTitle = "";
        state.nextActionMs = nowMs + actionDelayMs();
    }

    private Slot findSlotByKey(GuiContainer gui, String key)
    {
        if (gui == null || key == null)
        {
            return null;
        }
        String want = key.trim();
        if (want.isEmpty())
        {
            return null;
        }

        for (Slot slot : gui.inventorySlots.inventorySlots)
        {
            if (slot == null || host.isPlayerInventorySlot(gui, slot))
            {
                continue;
            }
            if (slot.getStack() == null || slot.getStack().isEmpty())
            {
                continue;
            }
            String k = buildMenuItemKey(slot.getStack(), slot.slotNumber);
            if (k != null && want.equalsIgnoreCase(k))
            {
                return slot;
            }
        }
        return null;
    }

    private Slot findCategorySlot(GuiContainer gui)
    {
        if (state.currentCategoryKey != null && !state.doneCategories.contains(state.currentCategoryKey))
        {
            Slot slot = findSlotByKey(gui, state.currentCategoryKey);
            if (slot != null)
            {
                return slot;
            }
        }
        for (Slot slot : gui.inventorySlots.inventorySlots)
        {
            if (slot == null || host.isPlayerInventorySlot(gui, slot))
            {
                continue;
            }
            ItemStack st = slot.getStack();
            if (st == null || st.isEmpty())
            {
                continue;
            }
            String k = buildMenuItemKey(st, slot.slotNumber);
            if (k == null || k.trim().isEmpty())
            {
                continue;
            }
            if (!state.doneCategories.contains(k))
            {
                return slot;
            }
        }
        return null;
    }

    private Slot findSubItemSlot(GuiContainer gui)
    {
        if (state.currentCategoryKey == null)
        {
            return null;
        }
        Set<String> done = state.doneSubItemsByCategory.get(state.currentCategoryKey);
        if (done == null)
        {
            done = new HashSet<>();
            state.doneSubItemsByCategory.put(state.currentCategoryKey, done);
        }
        for (Slot slot : gui.inventorySlots.inventorySlots)
        {
            if (slot == null || host.isPlayerInventorySlot(gui, slot))
            {
                continue;
            }
            ItemStack st = slot.getStack();
            if (st == null || st.isEmpty())
            {
                continue;
            }
            String k = buildMenuItemKey(st, slot.slotNumber);
            if (k == null || k.trim().isEmpty())
            {
                continue;
            }
            if (state.resaveMode && state.resaveQueryNorm != null && !state.resaveQueryNorm.isEmpty())
            {
                if (!matchesResaveQuery(st))
                {
                    done.add(k);
                    continue;
                }
            }
            if (isImportedRecord(RegAllActionsState.pathKey(state.menuPathKeys), "", st))
            {
                if (!state.resaveMode)
                {
                    done.add(k);
                    debugLog("skip subitem (imported) key=" + k + " rawName='" + st.getDisplayName() + "'");
                    continue;
                }
            }
            if (!done.contains(k))
            {
                return slot;
            }
        }
        return null;
    }

    private boolean matchesResaveQuery(ItemStack st)
    {
        if (st == null || st.isEmpty() || state.resaveQueryNorm == null || state.resaveQueryNorm.isEmpty())
        {
            return false;
        }
        String name = normalizeForKey(host.getItemNameKey(st));
        String lore = normalizeForKey(readLore(st));
        String q = state.resaveQueryNorm;
        if (matchesResaveQueryText(name, lore, q))
        {
            return true;
        }
        // Fuzzy: many actions are referred to with extra verbs ("отправить сообщение" vs "сообщение").
        String q2 = stripCommonRuVerbs(q);
        return !q2.isEmpty() && !q2.equals(q) && matchesResaveQueryText(name, lore, q2);
    }

    private boolean matchesResaveQueryText(String name, String lore, String q)
    {
        // IMPORTANT: match only on the clicked item's own text.
        // Using GUI title or sign text makes the match unstable (shared across many items)
        // and was causing resave to ignore the filter and resave the first random action.
        return (!name.isEmpty() && name.contains(q))
            || (!lore.isEmpty() && lore.contains(q));
    }

    private String stripCommonRuVerbs(String q)
    {
        if (q == null || q.isEmpty())
        {
            return "";
        }
        // Lower-cased already.
        String out = q;
        String[] remove = new String[]{
            "отправить",
            "выдать",
            "установить",
            "создать",
            "получить",
            "поставить",
            "запустить",
            "остановить",
            "добавить",
            "удалить",
            "очистить"
        };
        for (String r : remove)
        {
            out = out.replace(r, "");
        }
        out = out.replaceAll("\\s+", " ").trim();
        return out;
    }

    private boolean onRecordAdded(Minecraft mc, RegAllRecord record)
    {
        if (record == null || !state.resaveMode)
        {
            return false;
        }
        state.resaveReplaceLooseKeys.add(buildLooseRecordKey(record.categoryItem, record.subItem, record.guiTitle, record.signLines));
        state.resaveReplaceVeryLooseKeys.add(buildVeryLooseRecordKey(record.categoryItem, record.subItem));
        state.resaveReplaceStableKeys.add(buildStableRecordKeyFromFormatted(record.categoryItem, record.subItem));

        // Resave is intended to patch a specific broken entry quickly.
        exportRecords(mc);
        host.setActionBar(true, "&aResaved", 2000L);
        reset();
        return true;
    }

    private void markSubItemDone(String key)
    {
        if (state.currentCategoryKey == null || key == null)
        {
            return;
        }
        Set<String> done = state.doneSubItemsByCategory.get(state.currentCategoryKey);
        if (done == null)
        {
            done = new HashSet<>();
            state.doneSubItemsByCategory.put(state.currentCategoryKey, done);
        }
        done.add(key);
    }

    private RegAllRecord buildRecord(boolean hasChest, GuiChest gui)
    {
        RegAllRecord record = new RegAllRecord();
        record.hasChest = hasChest;
        record.categoryPath = RegAllActionsState.pathKey(state.menuPathKeys);
        if (!state.menuPathStacks.isEmpty())
        {
            record.categoryItem = formatItemStack(state.menuPathStacks.get(state.menuPathStacks.size() - 1));
        }
        else
        {
            record.categoryItem = "";
        }
        record.subItem = formatItemStack(state.currentActionStack);
        record.guiTitle = (gui != null) ? host.getGuiTitle(gui) : state.currentActionGuiTitle;
        record.signLines = readSignLines();
        if (hasChest && gui != null)
        {
            List<String> merged = host.snapshotMergedTopInventorySlots(gui);
            if (merged != null && !merged.isEmpty())
            {
                record.chestItems.addAll(merged);
                LOGGER.info("RGA_CHEST snapshot mode=merged items={} state={}",
                    merged.size(), host.describeChestSnapshotState(gui));
            }
            else
            {
                List<String> fallback = snapshotTopInventorySlots(gui);
                record.chestItems.addAll(fallback);
                LOGGER.warn("RGA_CHEST snapshot mode=fallback_visible items={} reason=merged_empty state={}",
                    fallback.size(), host.describeChestSnapshotState(gui));
            }
        }
        else
        {
            record.chestItems.add("no items");
        }
        return record;
    }

    private RegAllRecord buildCategoryMenuRecord(GuiChest gui)
    {
        RegAllRecord record = new RegAllRecord();
        record.hasChest = true;

        // Capture a category submenu snapshot (the menu that appears AFTER clicking a category).
        // Make the key match the parent-menu click moment:
        // - categoryPath: parent path (excluding clicked category)
        // - categoryItem: parent category item (if any)
        // - subItem: clicked category item
        int n = state.menuPathKeys.size();
        if (n > 1)
        {
            record.categoryPath = RegAllActionsState.pathKey(state.menuPathKeys.subList(0, n - 1));
        }
        else
        {
            record.categoryPath = "";
        }

        int m = state.menuPathStacks.size();
        if (m >= 2)
        {
            record.categoryItem = formatItemStack(state.menuPathStacks.get(m - 2));
        }
        else
        {
            record.categoryItem = "";
        }
        if (m >= 1)
        {
            record.subItem = formatItemStack(state.menuPathStacks.get(m - 1));
        }
        else
        {
            record.subItem = "";
        }

        record.guiTitle = (gui != null) ? host.getGuiTitle(gui) : "";
        record.signLines = readSignLines();
        if (gui != null)
        {
            record.chestItems.addAll(snapshotTopInventorySlots(gui));
        }
        else
        {
            record.chestItems.add("no items");
        }
        return record;
    }

    private boolean isParamsChestGui(GuiChest gui)
    {
        if (gui == null || gui.inventorySlots == null)
        {
            return false;
        }
        final String PHRASE_PUT_HERE = "\u043f\u043e\u043b\u043e\u0436\u0438\u0442\u0435 \u0441\u044e\u0434\u0430";         // "положите сюда"
        final String PHRASE_PLACE_HERE = "\u043f\u043e\u043c\u0435\u0441\u0442\u0438\u0442\u0435 \u0441\u044e\u0434\u0430"; // "поместите сюда"
        // Avoid caching category root menus as "params chests".
        if (looksLikeCategoryRootMenu(gui))
        {
            return false;
        }
        for (Slot slot : gui.inventorySlots.inventorySlots)
        {
            if (slot == null || host.isPlayerInventorySlot(gui, slot))
            {
                continue;
            }
            ItemStack st = slot.getStack();
            if (st == null || st.isEmpty())
            {
                continue;
            }
            String reg = st.getItem().getRegistryName() == null ? "" : st.getItem().getRegistryName().toString();
            if ("minecraft:stained_glass_pane".equals(reg))
            {
                return true;
            }
            // Param GUIs typically use instructional lore like "Положите сюда ...".
            String lore = normalizeForKey(readLore(st));
            if (!lore.isEmpty() && (lore.contains(PHRASE_PUT_HERE) || lore.contains(PHRASE_PLACE_HERE)))
            {
                return true;
            }
        }
        return false;
    }

    private boolean looksLikeCategoryRootMenu(GuiChest gui)
    {
        if (gui == null || gui.inventorySlots == null)
        {
            return false;
        }
        int topSize = 0;
        try
        {
            if (gui.inventorySlots instanceof ContainerChest)
            {
                IInventory inv = ((ContainerChest) gui.inventorySlots).getLowerChestInventory();
                if (inv != null)
                {
                    topSize = inv.getSizeInventory();
                }
            }
        }
        catch (Exception ignore) { }
        if (topSize != 9)
        {
            return false;
        }

        // Known "category picker" icons (the ones reported by the user).
        Set<String> allow = new HashSet<>(Arrays.asList(
            "minecraft:chest",
            "minecraft:beacon",
            "minecraft:painting",
            "minecraft:leather_boots",
            "minecraft:apple",
            "minecraft:nether_star",
            "minecraft:anvil",
            "minecraft:armor_stand",
            "minecraft:emerald"
        ));

        int nonEmpty = 0;
        int allowedHits = 0;
        for (Slot slot : gui.inventorySlots.inventorySlots)
        {
            if (slot == null || host.isPlayerInventorySlot(gui, slot))
            {
                continue;
            }
            if (slot.slotNumber < 0 || slot.slotNumber >= 9)
            {
                continue;
            }
            ItemStack st = slot.getStack();
            if (st == null || st.isEmpty())
            {
                continue;
            }
            nonEmpty++;
            String reg = st.getItem().getRegistryName() == null ? "" : st.getItem().getRegistryName().toString();
            if (allow.contains(reg))
            {
                allowedHits++;
            }
        }
        return nonEmpty >= 6 && allowedHits == nonEmpty;
    }

    private String buildMenuItemKey(ItemStack stack, int slotNumber)
    {
        if (stack == null || stack.isEmpty())
        {
            return "";
        }
        String reg = stack.getItem().getRegistryName() == null
            ? "unknown"
            : stack.getItem().getRegistryName().toString();
        String name = normalizeForKey(host.getItemNameKey(stack));
        String lore = normalizeForKey(readLore(stack));
        String raw = reg + "|" + stack.getMetadata() + "|" + name + "|" + lore + "|" + slotNumber;
        return Integer.toHexString(raw.hashCode());
    }

    private String normalizeForKey(String s)
    {
        if (s == null)
        {
            return "";
        }
        String without = net.minecraft.util.text.TextFormatting.getTextWithoutFormattingCodes(s);
        if (without == null)
        {
            without = s;
        }
        return without.replace('\u00A0', ' ').replaceAll("\\s+", " ").trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeSignLineForKey(String s)
    {
        if (s == null)
        {
            return "";
        }
        ensureSignAliasesLoaded(Minecraft.getMinecraft());
        String without = net.minecraft.util.text.TextFormatting.getTextWithoutFormattingCodes(s);
        String aliased = s;
        if (signAliases.containsKey(s))
        {
            aliased = signAliases.get(s);
        }
        else if (without != null && signAliases.containsKey(without))
        {
            aliased = signAliases.get(without);
        }
        return normalizeForKey(aliased);
    }

    private void ensureSignAliasesLoaded(Minecraft mc)
    {
        if (signAliasesLoaded)
        {
            return;
        }
        signAliasesLoaded = true;
        signAliases.put("Выбрать обьект", "Выбрать объект");
        signAliases.put("Массивы", "Работа с массивами");
        signAliases.put("Присв. переменную", "Установить переменную");
        signAliases.put("Если переменная", "Если значение");
        loadAliasesFromResource();
        if (mc != null && mc.mcDataDir != null)
        {
            File file = new File(mc.mcDataDir, "regallactions_aliases.json");
            loadAliasesFromFile(file);
        }
    }

    private void loadAliasesFromResource()
    {
        try (InputStream in = RegAllActionsModule.class.getClassLoader()
            .getResourceAsStream("regallactions_aliases.json"))
        {
            if (in == null)
            {
                return;
            }
            parseAliasesJson(readAll(in));
        }
        catch (Exception ignore) { }
    }

    private void loadAliasesFromFile(File file)
    {
        if (file == null || !file.exists())
        {
            return;
        }
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF-8")))
        {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null)
            {
                sb.append(line).append("\n");
            }
            parseAliasesJson(sb.toString());
        }
        catch (Exception ignore) { }
    }

    private void parseAliasesJson(String json)
    {
        if (json == null)
        {
            return;
        }
        Pattern section = Pattern.compile("\"sign1\"\\s*:\\s*\\{(.*?)\\}", Pattern.DOTALL);
        Matcher secMatch = section.matcher(json);
        if (!secMatch.find())
        {
            return;
        }
        String body = secMatch.group(1);
        Pattern pair = Pattern.compile("\"([^\"]+)\"\\s*:\\s*\"([^\"]*)\"");
        Matcher m = pair.matcher(body);
        while (m.find())
        {
            String key = unescapeJson(m.group(1));
            String val = unescapeJson(m.group(2));
            if (!key.isEmpty())
            {
                signAliases.put(key, val);
            }
        }
    }

    private String unescapeJson(String s)
    {
        if (s == null)
        {
            return "";
        }
        return s.replace("\\\\n", "\n").replace("\\\\\"", "\"").replace("\\\\\\\\", "\\");
    }

    private String readAll(InputStream in) throws Exception
    {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, "UTF-8")))
        {
            String line;
            while ((line = reader.readLine()) != null)
            {
                sb.append(line).append("\n");
            }
        }
        return sb.toString();
    }

    private long actionDelayMs()
    {
        return state.debugSlow ? 1000L : 250L;
    }

    private void debugBar(String text)
    {
        if (!state.debugSlow)
        {
            return;
        }
        host.setActionBar(true, "&7[reg] " + text, 1200L);
    }

    private void debugLog(String text)
    {
        if (!host.isDebugUi())
        {
            return;
        }
        LOGGER.info("{}", text);
    }

    private String[] readSignLines()
    {
        String[] out = new String[]{"", "", "", ""};
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.world == null || state.signPos == null)
        {
            return out;
        }
        TileEntity te = mc.world.getTileEntity(state.signPos);
        if (!(te instanceof TileEntitySign))
        {
            return out;
        }
        TileEntitySign sign = (TileEntitySign) te;
        for (int i = 0; i < 4; i++)
        {
            out[i] = sign.signText[i].getUnformattedText();
        }
        return out;
    }

    private List<String> snapshotTopInventorySlots(GuiContainer gui)
    {
        List<String> out = new ArrayList<>();
        if (gui == null || gui.inventorySlots == null)
        {
            return out;
        }
        for (Slot slot : gui.inventorySlots.inventorySlots)
        {
            if (slot == null)
            {
                continue;
            }
            if (host.isPlayerInventorySlot(gui, slot))
            {
                continue;
            }
            ItemStack stack = slot.getStack();
            if (stack == null || stack.isEmpty())
            {
                continue;
            }
            out.add("slot " + slot.slotNumber + ": " + formatItemStack(stack));
        }
        return out;
    }

    private String formatItemStack(ItemStack stack)
    {
        if (stack == null || stack.isEmpty())
        {
            return "empty";
        }
        String reg = stack.getItem().getRegistryName() == null
            ? "unknown"
            : stack.getItem().getRegistryName().toString();
        String name = stack.getDisplayName();
        String lore = readLore(stack);
        return "[" + reg + " meta=" + stack.getMetadata() + "] " + name + (lore.isEmpty() ? "" : " | " + lore);
    }

    private String buildRecordKey(String categoryPath, String categoryItem, String subItem, String guiTitle, String[] signLines)
    {
        StringBuilder sb = new StringBuilder();
        sb.append(normalizeForKey(categoryPath)).append("|");
        sb.append(normalizeForKey(categoryItem)).append("|");
        sb.append(normalizeForKey(subItem)).append("|");
        sb.append(normalizeForKey(guiTitle)).append("|");
        if (signLines != null)
        {
            for (int i = 0; i < signLines.length; i++)
            {
                if (i > 0) sb.append("|");
                sb.append(normalizeSignLineForKey(signLines[i]));
            }
        }
        return sb.toString();
    }

    private String buildLooseRecordKey(String categoryItem, String subItem, String guiTitle, String[] signLines)
    {
        StringBuilder sb = new StringBuilder();
        sb.append(normalizeForKey(categoryItem)).append("|");
        sb.append(normalizeForKey(subItem)).append("|");
        sb.append(normalizeForKey(guiTitle)).append("|");
        if (signLines != null)
        {
            for (int i = 0; i < signLines.length; i++)
            {
                if (i > 0) sb.append("|");
                sb.append(normalizeSignLineForKey(signLines[i]));
            }
        }
        return sb.toString();
    }

    private String buildVeryLooseRecordKey(String categoryItem, String subItem)
    {
        StringBuilder sb = new StringBuilder();
        sb.append(normalizeForKey(categoryItem)).append("|");
        sb.append(normalizeForKey(subItem));
        return sb.toString();
    }

    private String buildStableRecordKeyFromFormatted(String categoryItem, String subItem)
    {
        return stableItemSigFromFormatted(categoryItem) + "|" + stableItemSigFromFormatted(subItem);
    }

    private String stableItemSigFromFormatted(String formatted)
    {
        if (formatted == null)
        {
            return "";
        }
        Matcher m = FORMATTED_ITEM_RE.matcher(formatted.trim());
        if (!m.matches())
        {
            return normalizeForKey(formatted);
        }
        String reg = m.group(1) == null ? "" : m.group(1).trim();
        String meta = m.group(2) == null ? "0" : m.group(2).trim();
        String rest = m.group(3) == null ? "" : m.group(3);
        String name = rest;
        int idx = rest.indexOf(" | ");
        if (idx >= 0)
        {
            name = rest.substring(0, idx);
        }
        return reg + ":" + meta + ":" + normalizeForKey(name);
    }

    private String stableItemSig(ItemStack stack)
    {
        if (stack == null || stack.isEmpty())
        {
            return "";
        }
        String reg = stack.getItem().getRegistryName() == null ? "" : stack.getItem().getRegistryName().toString();
        String meta = Integer.toString(stack.getMetadata());
        String name = normalizeForKey(stack.getDisplayName());
        return reg + ":" + meta + ":" + name;
    }

    private boolean isImportedRecord(String pathKey, String guiTitle, ItemStack subItemStack)
    {
        if (state.importedRecordKeys.isEmpty())
        {
            return false;
        }
        String[] signLines = readSignLines();
        String categoryItem = "";
        if (!state.menuPathStacks.isEmpty())
        {
            categoryItem = formatItemStack(state.menuPathStacks.get(state.menuPathStacks.size() - 1));
        }
        String subItem = formatItemStack(subItemStack);
        String key = buildRecordKey(pathKey, categoryItem, subItem, guiTitle, signLines);
        if (state.importedRecordKeys.contains(key))
        {
            return true;
        }
        String looseKey = buildLooseRecordKey(categoryItem, subItem, guiTitle, signLines);
        if (state.importedLooseKeys.contains(looseKey))
        {
            return true;
        }
        String veryLooseKey = buildVeryLooseRecordKey(categoryItem, subItem);
        if (state.importedVeryLooseKeys.contains(veryLooseKey))
        {
            return true;
        }
        ItemStack catStack = state.menuPathStacks.isEmpty()
            ? ItemStack.EMPTY
            : state.menuPathStacks.get(state.menuPathStacks.size() - 1);
        String stableKey = stableItemSig(catStack) + "|" + stableItemSig(subItemStack);
        return state.importedStableKeys.contains(stableKey);
    }

    private String readLore(ItemStack stack)
    {
        if (stack == null || stack.isEmpty())
        {
            return "";
        }
        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null || !tag.hasKey("display", 10))
        {
            return "";
        }
        NBTTagCompound display = tag.getCompoundTag("display");
        if (!display.hasKey("Lore", 9))
        {
            return "";
        }
        NBTTagList lore = display.getTagList("Lore", 8);
        if (lore == null || lore.tagCount() == 0)
        {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lore.tagCount(); i++)
        {
            if (i > 0) sb.append(" \\n ");
            sb.append(lore.getStringTagAt(i));
        }
        return sb.toString();
    }

    private void ensureImportLoaded(Minecraft mc)
    {
        if (state.importLoaded || mc == null || mc.mcDataDir == null)
        {
            return;
        }
        File inFile = new File(mc.mcDataDir, "regallactions_export.txt");
        if (!inFile.exists())
        {
            state.importLoaded = true;
            return;
        }

        String categoryPath = "";
        String category = null;
        String subItem = null;
        String guiTitle = "";
        String[] signLines = new String[]{"", "", "", ""};
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(inFile), "UTF-8")))
        {
            String line;
            while ((line = reader.readLine()) != null)
            {
                if (line.startsWith("# record"))
                {
                    if (category != null && subItem != null)
                    {
                        String key = buildRecordKey(categoryPath, category, subItem, guiTitle, signLines);
                        state.importedRecordKeys.add(key);
                        state.importedLooseKeys.add(buildLooseRecordKey(category, subItem, guiTitle, signLines));
                        state.importedVeryLooseKeys.add(buildVeryLooseRecordKey(category, subItem));
                        state.importedStableKeys.add(buildStableRecordKeyFromFormatted(category, subItem));
                    }
                    categoryPath = "";
                    category = null;
                    subItem = null;
                    guiTitle = "";
                    signLines = new String[]{"", "", "", ""};
                    continue;
                }

                if (line.startsWith("records="))
                {
                    continue;
                }
                if (line.startsWith("path="))
                {
                    categoryPath = line.substring("path=".length());
                }
                else if (line.startsWith("category="))
                {
                    category = line.substring("category=".length());
                }
                else if (line.startsWith("subitem="))
                {
                    subItem = line.substring("subitem=".length());
                }
                else if (line.startsWith("gui="))
                {
                    guiTitle = line.substring("gui=".length());
                }
                else if (line.startsWith("sign1="))
                {
                    signLines[0] = line.substring("sign1=".length());
                }
                else if (line.startsWith("sign2="))
                {
                    signLines[1] = line.substring("sign2=".length());
                }
                else if (line.startsWith("sign3="))
                {
                    signLines[2] = line.substring("sign3=".length());
                }
                else if (line.startsWith("sign4="))
                {
                    signLines[3] = line.substring("sign4=".length());
                }
            }

            if (category != null && subItem != null)
            {
                String key = buildRecordKey(categoryPath, category, subItem, guiTitle, signLines);
                state.importedRecordKeys.add(key);
                state.importedLooseKeys.add(buildLooseRecordKey(category, subItem, guiTitle, signLines));
                state.importedVeryLooseKeys.add(buildVeryLooseRecordKey(category, subItem));
                state.importedStableKeys.add(buildStableRecordKeyFromFormatted(category, subItem));
            }

            state.importLoaded = true;
            String msg = "imported records=" + state.importedRecordKeys.size()
                + " loose=" + state.importedLooseKeys.size()
                + " veryLoose=" + state.importedVeryLooseKeys.size()
                + " stable=" + state.importedStableKeys.size();
            LOGGER.info("RegAllActions {}", msg);
            debugLog(msg);
        }
        catch (Exception e)
        {
            state.importLoaded = true;
            String msg = "import failed: " + e.getClass().getSimpleName();
            LOGGER.warn("RegAllActions {}", msg);
            debugLog(msg);
        }
    }

    private void exportRecords(Minecraft mc)
    {
        if (mc == null || mc.mcDataDir == null)
        {
            host.setActionBar(false, "&cNo data dir", 2500L);
            return;
        }
        File outFile = new File(mc.mcDataDir, "regallactions_export.txt");
        List<RegAllRecord> outRecords = new ArrayList<>();
        Set<String> seenKeys = new HashSet<>();
        if (outFile.exists())
        {
            List<RegAllRecord> existing = readExportFile(outFile);
            for (RegAllRecord r : existing)
            {
                String key = buildRecordKey(r.categoryPath, r.categoryItem, r.subItem, r.guiTitle, r.signLines);
                if (seenKeys.add(key))
                {
                    outRecords.add(r);
                }
            }
        }
        if (!state.resaveReplaceLooseKeys.isEmpty()
            || !state.resaveReplaceVeryLooseKeys.isEmpty()
            || !state.resaveReplaceStableKeys.isEmpty())
        {
            outRecords.removeIf(r -> state.resaveReplaceLooseKeys.contains(
                buildLooseRecordKey(r.categoryItem, r.subItem, r.guiTitle, r.signLines))
                || state.resaveReplaceVeryLooseKeys.contains(buildVeryLooseRecordKey(r.categoryItem, r.subItem))
                || state.resaveReplaceStableKeys.contains(buildStableRecordKeyFromFormatted(r.categoryItem, r.subItem)));
            seenKeys.clear();
            for (RegAllRecord r : outRecords)
            {
                seenKeys.add(buildRecordKey(r.categoryPath, r.categoryItem, r.subItem, r.guiTitle, r.signLines));
            }
        }
        for (RegAllRecord r : state.records)
        {
            String key = buildRecordKey(r.categoryPath, r.categoryItem, r.subItem, r.guiTitle, r.signLines);
            if (seenKeys.add(key))
            {
                outRecords.add(r);
            }
        }
        try (OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(outFile), "UTF-8"))
        {
            writer.write("records=" + outRecords.size() + "\n");
            for (int i = 0; i < outRecords.size(); i++)
            {
                RegAllRecord r = outRecords.get(i);
                writer.write("\n# record " + (i + 1) + "\n");
                writer.write("path=" + r.categoryPath + "\n");
                writer.write("category=" + r.categoryItem + "\n");
                writer.write("subitem=" + r.subItem + "\n");
                writer.write("gui=" + r.guiTitle + "\n");
                writer.write("sign1=" + r.signLines[0] + "\n");
                writer.write("sign2=" + r.signLines[1] + "\n");
                writer.write("sign3=" + r.signLines[2] + "\n");
                writer.write("sign4=" + r.signLines[3] + "\n");
                writer.write("hasChest=" + r.hasChest + "\n");
                for (String item : r.chestItems)
                {
                    writer.write("item=" + item + "\n");
                }
            }
            writer.flush();
            host.setActionBar(true, "&aExported " + outRecords.size() + " records", 3500L);
        }
        catch (Exception e)
        {
            host.setActionBar(false, "&cExport failed: " + e.getClass().getSimpleName(), 3500L);
        }
    }

    private List<RegAllRecord> readExportFile(File inFile)
    {
        List<RegAllRecord> out = new ArrayList<>();
        if (inFile == null || !inFile.exists())
        {
            return out;
        }
        String categoryPath = "";
        String category = null;
        String subItem = null;
        String guiTitle = "";
        String[] signLines = new String[]{"", "", "", ""};
        boolean hasChest = false;
        List<String> chestItems = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(inFile), "UTF-8")))
        {
            String line;
            while ((line = reader.readLine()) != null)
            {
                if (line.startsWith("# record"))
                {
                    if (category != null && subItem != null)
                    {
                        RegAllRecord r = new RegAllRecord();
                        r.categoryPath = categoryPath;
                        r.categoryItem = category;
                        r.subItem = subItem;
                        r.guiTitle = guiTitle;
                        r.signLines = signLines;
                        r.hasChest = hasChest;
                        r.chestItems.addAll(chestItems);
                        out.add(r);
                    }
                    categoryPath = "";
                    category = null;
                    subItem = null;
                    guiTitle = "";
                    signLines = new String[]{"", "", "", ""};
                    hasChest = false;
                    chestItems.clear();
                    continue;
                }
                if (line.startsWith("records="))
                {
                    continue;
                }
                if (line.startsWith("path="))
                {
                    categoryPath = line.substring("path=".length());
                }
                else if (line.startsWith("category="))
                {
                    category = line.substring("category=".length());
                }
                else if (line.startsWith("subitem="))
                {
                    subItem = line.substring("subitem=".length());
                }
                else if (line.startsWith("gui="))
                {
                    guiTitle = line.substring("gui=".length());
                }
                else if (line.startsWith("sign1="))
                {
                    signLines[0] = line.substring("sign1=".length());
                }
                else if (line.startsWith("sign2="))
                {
                    signLines[1] = line.substring("sign2=".length());
                }
                else if (line.startsWith("sign3="))
                {
                    signLines[2] = line.substring("sign3=".length());
                }
                else if (line.startsWith("sign4="))
                {
                    signLines[3] = line.substring("sign4=".length());
                }
                else if (line.startsWith("hasChest="))
                {
                    hasChest = Boolean.parseBoolean(line.substring("hasChest=".length()));
                }
                else if (line.startsWith("item="))
                {
                    chestItems.add(line.substring("item=".length()));
                }
            }
            if (category != null && subItem != null)
            {
                RegAllRecord r = new RegAllRecord();
                r.categoryPath = categoryPath;
                r.categoryItem = category;
                r.subItem = subItem;
                r.guiTitle = guiTitle;
                r.signLines = signLines;
                r.hasChest = hasChest;
                r.chestItems.addAll(chestItems);
                out.add(r);
            }
        }
        catch (Exception e)
        {
            LOGGER.warn("RegAllActions export read failed: {}", e.getClass().getSimpleName());
        }
        return out;
    }
}
