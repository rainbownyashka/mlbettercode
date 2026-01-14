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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class RegAllActionsModule
{
    private final RegAllActionsHost host;
    private final RegAllActionsState state = new RegAllActionsState();
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

        ensureImportLoaded(mc);

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

        if (state.awaitingSubClickClose)
        {
            if (nowMs - state.subClickMs < 300L)
            {
                return;
            }
            state.awaitingSubClickClose = false;
            state.waitingForChest = true;
            state.nextActionMs = nowMs + actionDelayMs();
            return;
        }

        if (nowMs < state.nextActionMs)
        {
            return;
        }

        if (state.openingChest && nowMs > state.chestOpenDeadlineMs)
        {
            if (state.currentCategoryKey != null && state.currentSubItemKey != null)
            {
                state.records.add(buildRecord(false, null));
            }
            state.openingChest = false;
            state.menuLevel = 0;
            state.nextActionMs = nowMs + 250L;
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
                state.menuLevel = 0;
                state.waitingForChest = false;
                state.openingChest = false;
                state.nextActionMs = nowMs + actionDelayMs();
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

        if (state.awaitingSubClickClose)
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
        }

        if (nowMs < state.nextActionMs)
        {
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
            state.menuLevel = 0;
            state.nextActionMs = nowMs + actionDelayMs();
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

        if (state.menuLevel == 0)
        {
            Slot categorySlot = findCategorySlot(gui);
            if (categorySlot == null)
            {
                state.active = false;
                host.closeCurrentScreen();
                host.setActionBar(true, "&aRegAllActions done (cached=" + state.cachedCount + ")", 3500L);
                debugLog("done: no category slots left");
                return;
            }
            ItemStack stack = categorySlot.getStack();
            String key = buildMenuItemKey(stack, categorySlot.slotNumber);
            if (key == null || key.trim().isEmpty())
            {
                host.closeCurrentScreen();
                state.nextActionMs = nowMs + actionDelayMs();
                return;
            }
            state.currentCategoryKey = key;
            state.currentCategoryStack = stack.copy();
            state.menuLevel = 1;
            host.queueClick(new ClickAction(categorySlot.slotNumber, 0, ClickType.PICKUP));
            state.waitingForCursorClear = true;
            state.cursorClearSinceMs = 0L;
            state.nextActionMs = nowMs + actionDelayMs();
            debugBar("click category");
            debugLog("click category key=" + key
                + " rawName='" + stack.getDisplayName() + "' rawLore='" + readLore(stack) + "' slot=" + categorySlot.slotNumber
                + " doneCategories=" + state.doneCategories.toString());
            return;
        }

        if (state.menuLevel == 1)
        {
            Slot subItemSlot = findSubItemSlot(gui);
            if (subItemSlot == null)
            {
                if (state.currentCategoryKey != null)
                {
                    state.doneCategories.add(state.currentCategoryKey);
                }
                state.currentCategoryKey = null;
                state.currentCategoryStack = ItemStack.EMPTY;
                state.menuLevel = 0;
                host.closeCurrentScreen();
                state.nextActionMs = nowMs + 250L;
                return;
            }
            ItemStack stack = subItemSlot.getStack();
            String key = buildMenuItemKey(stack, subItemSlot.slotNumber);
            if (key == null || key.trim().isEmpty())
            {
                state.nextActionMs = nowMs + actionDelayMs();
                return;
            }
            state.currentSubItemKey = key;
            state.currentSubItemStack = stack.copy();
            markSubItemDone(key);
            host.queueClick(new ClickAction(subItemSlot.slotNumber, 0, ClickType.PICKUP));
            state.awaitingSubClickClose = true;
            state.subClickMs = nowMs;
            state.nextActionMs = nowMs + actionDelayMs();
            debugBar("click subitem");
            Set<String> done = state.doneSubItemsByCategory.get(state.currentCategoryKey);
            debugLog("click subitem key=" + key
                + " rawName='" + stack.getDisplayName() + "' rawLore='" + readLore(stack) + "' slot=" + subItemSlot.slotNumber
                + " doneSubItems=" + (done == null ? "[]" : done.toString()));
        }
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
            if (isImportedRecord(state.currentCategoryStack, st))
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
        record.categoryItem = formatItemStack(state.currentCategoryStack);
        record.subItem = formatItemStack(state.currentSubItemStack);
        record.guiTitle = (gui != null) ? host.getGuiTitle(gui) : "";
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

    private String buildRecordKey(String categoryItem, String subItem, String[] signLines)
    {
        StringBuilder sb = new StringBuilder();
        sb.append(normalizeForKey(categoryItem)).append("|");
        sb.append(normalizeForKey(subItem)).append("|");
        if (signLines != null)
        {
            for (int i = 0; i < signLines.length; i++)
            {
                if (i > 0) sb.append("|");
                sb.append(normalizeForKey(signLines[i]));
            }
        }
        return sb.toString();
    }

    private boolean isImportedRecord(ItemStack categoryStack, ItemStack subItemStack)
    {
        if (state.importedRecordKeys.isEmpty())
        {
            return false;
        }
        String[] signLines = readSignLines();
        String categoryItem = formatItemStack(categoryStack);
        String subItem = formatItemStack(subItemStack);
        String key = buildRecordKey(categoryItem, subItem, signLines);
        return state.importedRecordKeys.contains(key);
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
        String category = null;
        String subItem = null;
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
                        String key = buildRecordKey(category, subItem, signLines);
                        state.importedRecordKeys.add(key);
                    }
                    category = null;
                    subItem = null;
                    signLines = new String[]{"", "", "", ""};
                    continue;
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
            }
            if (category != null && subItem != null)
            {
                String key = buildRecordKey(category, subItem, signLines);
                state.importedRecordKeys.add(key);
            }
            state.importLoaded = true;
            debugLog("imported records=" + state.importedRecordKeys.size());
        }
        catch (Exception e)
        {
            state.importLoaded = true;
            debugLog("import failed: " + e.getClass().getSimpleName());
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
        try (OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(outFile), "UTF-8"))
        {
            writer.write("records=" + state.records.size() + "\n");
            for (int i = 0; i < state.records.size(); i++)
            {
                RegAllRecord r = state.records.get(i);
                writer.write("\n# record " + (i + 1) + "\n");
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
            host.setActionBar(true, "&aExported " + state.records.size() + " records", 3500L);
        }
        catch (Exception e)
        {
            host.setActionBar(false, "&cExport failed: " + e.getClass().getSimpleName(), 3500L);
        }
    }
}
