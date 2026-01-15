package com.example.examplemod.feature.regallactions;

import com.example.examplemod.model.ClickAction;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.inventory.GuiChest;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.command.ICommandSender;
import net.minecraft.init.Blocks;
import net.minecraft.inventory.ClickType;
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

        host.setActionBar(true, "&aRegAllActions started", 2000L);
        if (host.isDebugUi())
        {
            host.debugChat("/regallactions: sign=" + signPos.toString());
        }
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
            state.records.add(buildRecord(false, null));
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
                state.records.add(buildRecord(false, null));
                finishActionAndReplay(nowMs);
                debugBar("no chest");
                return;
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
            state.chestOpenDeadlineMs = nowMs + 2000L;
            state.chestReadySinceMs = 0L;
            state.chestReadyWindowId = -1;
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
                return;
            }
        }

        if (nowMs < state.nextActionMs)
        {
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
            RegAllRecord record = buildRecord(true, (GuiChest) gui);
            state.records.add(record);
            state.cachedCount++;
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
            host.queueClick(new ClickAction(slot.slotNumber, 0, ClickType.PICKUP));
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
            if (state.menuPathKeys.isEmpty())
            {
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
        host.queueClick(new ClickAction(next.slotNumber, 0, ClickType.PICKUP));
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
            if (isImportedRecord(pathKey, guiTitle, st))
            {
                done.add(k);
                debugLog("skip item (imported) key=" + k + " rawName='" + st.getDisplayName() + "'");
                continue;
            }
            if (!done.contains(k))
            {
                return slot;
            }
        }
        return null;
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
            if (isImportedRecord(RegAllActionsState.pathKey(state.menuPathKeys), "", st))
            {
                done.add(k);
                debugLog("skip subitem (imported) key=" + k + " rawName='" + st.getDisplayName() + "'");
                continue;
            }
            if (!done.contains(k))
            {
                return slot;
            }
        }
        return null;
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
            record.chestItems.addAll(snapshotTopInventorySlots(gui));
        }
        else
        {
            record.chestItems.add("no items");
        }
        return record;
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
        return state.importedVeryLooseKeys.contains(veryLooseKey);
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
        String categoryPath = null;
        String category = null;
        String subItem = null;
        String guiTitle = null;
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
                        String loose = buildLooseRecordKey(category, subItem, guiTitle, signLines);
                        state.importedLooseKeys.add(loose);
                        String veryLoose = buildVeryLooseRecordKey(category, subItem);
                        state.importedVeryLooseKeys.add(veryLoose);
                    }
                    categoryPath = null;
                    category = null;
                    subItem = null;
                    guiTitle = null;
                    signLines = new String[]{"", "", "", ""};
                    continue;
                }
                if (line.startsWith("path="))
                {
                    categoryPath = line.substring("path=".length());
                }
                if (line.startsWith("category="))
                {
                    category = line.substring("category=".length());
                }
                else if (line.startsWith("subitem="))
                {
                    subItem = line.substring("subitem=".length());
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
                else if (line.startsWith("gui="))
                {
                    guiTitle = line.substring("gui=".length());
                }
            }
            if (category != null && subItem != null)
            {
                String key = buildRecordKey(categoryPath, category, subItem, guiTitle, signLines);
                state.importedRecordKeys.add(key);
                String loose = buildLooseRecordKey(category, subItem, guiTitle, signLines);
                state.importedLooseKeys.add(loose);
            }
            state.importLoaded = true;
            String msg = "imported records=" + state.importedRecordKeys.size()
                + " loose=" + state.importedLooseKeys.size()
                + " veryLoose=" + state.importedVeryLooseKeys.size();
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
