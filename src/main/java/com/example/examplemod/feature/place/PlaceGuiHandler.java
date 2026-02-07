package com.example.examplemod.feature.place;

import com.example.examplemod.model.ClickAction;
import com.example.examplemod.model.MenuStep;
import com.example.examplemod.model.PlaceArg;
import com.example.examplemod.model.PlaceEntry;
import com.example.examplemod.util.ItemStackUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.ClickType;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.init.Items;
import net.minecraft.network.play.client.CPacketCreativeInventoryAction;
import net.minecraft.util.text.TextFormatting;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

final class PlaceGuiHandler
{
    private static final Logger LOGGER = LogManager.getLogger("BetterCode-Place");
    private static final int PAGE_TURN_MAX_RETRIES = 5;
    private static final long PAGE_TURN_TIMEOUT_MS = 1500L;

    private PlaceGuiHandler() {}

    static void onGuiTick(PlaceModuleHost host, PlaceState state, GuiContainer gui, long nowMs)
    {
        if (host == null || state == null || gui == null)
        {
            return;
        }
        if (!state.active || state.current == null)
        {
            return;
        }

        PlaceEntry entry = state.current;

        if (entry.awaitingParamsChest)
        {
            int windowId = gui.inventorySlots == null ? -1 : gui.inventorySlots.windowId;
            if (windowId != -1
                && entry.lastMenuWindowId != -1
                && windowId != entry.lastMenuWindowId
                && nowMs - entry.lastMenuClickMs > host.placeDelayMs(450L))
            {
                entry.awaitingParamsChest = false;
                entry.awaitingArgs = true;
                entry.advancedArgIndex = 0;
                entry.argsStartMs = nowMs;
                entry.lastArgsActionMs = 0L;
                entry.argsMisses = 0;
                entry.usedArgSlots.clear();
                entry.argsGuiPage = 0;
                entry.argsPageTurnPending = false;
                entry.argsPageTurnStartMs = 0L;
                entry.argsPageTurnNextMs = 0L;
                entry.argsPageRetryCount = 0;
                entry.argsPageLastHash = "";
                handleArgs(host, state, gui, nowMs);
            }
            else
            {
                // Some servers don't open the params chest automatically (or do it with big lag).
                // If the window doesn't change for a while, close the menu and let the tick handler open the chest.
                if (entry.paramsStartMs == 0L)
                {
                    entry.paramsStartMs = nowMs;
                }
                if (!entry.needOpenParamsChest
                    && entry.lastMenuWindowId != -1
                    && windowId == entry.lastMenuWindowId
                    && nowMs - entry.paramsStartMs > host.placeParamsChestAutoOpenDelayMs())
                {
                    host.closeCurrentScreen();
                    entry.needOpenParamsChest = true;
                    entry.nextParamsActionMs = nowMs + host.placeDelayMs(350L);
                    entry.paramsOpenAttempts = 0;
                }
            }
            return;
        }

        if (entry.awaitingArgs)
        {
            handleArgs(host, state, gui, nowMs);
            return;
        }

        if (!entry.awaitingMenu)
        {
            return;
        }

        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.player == null || mc.player.openContainer == null)
        {
            return;
        }

        entry.needOpenMenu = false;

        try
        {
            ItemStack carried = mc.player.inventory.getItemStack();
            if (carried != null && !carried.isEmpty())
            {
                return;
            }
        }
        catch (Exception ignore) { }

        if (nowMs - entry.menuStartMs < host.placeDelayMs(300L))
        {
            return;
        }

        int nonEmpty = 0;
        for (Slot slot : gui.inventorySlots.inventorySlots)
        {
            if (slot == null || slot.inventory == mc.player.inventory)
            {
                continue;
            }
            ItemStack st = slot.getStack();
            if (st != null && !st.isEmpty())
            {
                nonEmpty++;
                break;
            }
        }
        if (nonEmpty == 0)
        {
            entry.menuNonEmptySinceMs = 0L;
            entry.menuNonEmptyWindowId = -1;
            return;
        }

        int windowId = mc.player.openContainer.windowId;
        if (entry.menuNonEmptyWindowId != windowId)
        {
            entry.menuNonEmptyWindowId = windowId;
            entry.menuNonEmptySinceMs = nowMs;
            return;
        }
        if (entry.menuNonEmptySinceMs == 0L)
        {
            entry.menuNonEmptySinceMs = nowMs;
            return;
        }
        if (nowMs - entry.menuNonEmptySinceMs < host.placeDelayMs(250L))
        {
            return;
        }

        if (entry.searchKey == null || entry.searchKey.trim().isEmpty())
        {
            entry.awaitingMenu = false;
            state.current = null;
            return;
        }

        if (nowMs - entry.menuStartMs > host.placeDelayMs(10000L))
        {
            host.setActionBar(false, "&c/place: menu timeout", 2000L);
            abort(host, state, "menu_timeout");
            return;
        }

        if (entry.triedWindowId != windowId)
        {
            entry.triedSlots.clear();
            entry.triedWindowId = windowId;
            entry.menuClicksSinceOpen = 0;
        }
        if (entry.lastMenuWindowId == windowId && nowMs - entry.lastMenuClickMs < host.placeDelayMs(300L))
        {
            return;
        }

        MenuStep step = findMenuStep(host, gui, entry.searchKey);
        if (step == null)
        {
            if (entry.randomClicks > 250)
            {
                host.setActionBar(false, "&c/place: random search exhausted", 2500L);
                abort(host, state, "random_exhausted");
                return;
            }
            if (nowMs < entry.nextMenuActionMs)
            {
                return;
            }
            entry.nextMenuActionMs = nowMs + host.placeDelayMs(220L);

            MenuStep rnd = findRandomMenuStep(host, gui, entry);
            if (rnd == null)
            {
                if (entry.menuClicksSinceOpen == 0)
                {
                    host.setActionBar(false, "&c/place: no path found in GUI", 2000L);
                    abort(host, state, "no_path_gui");
                    return;
                }

                host.closeCurrentScreen();
                entry.needOpenMenu = true;
                entry.awaitingMenu = true;
                entry.menuStartMs = nowMs;
                entry.nextMenuActionMs = nowMs + host.placeDelayMs(450L);
                entry.menuClicksSinceOpen = 0;
                entry.triedSlots.clear();
                entry.triedWindowId = -1;
                return;
            }

            host.queueClick(new ClickAction(rnd.slotNumber, 0, ClickType.PICKUP));
            entry.triedSlots.add(rnd.slotNumber);
            try
            {
                Slot s = gui.inventorySlots.getSlot(rnd.slotNumber);
                if (s != null)
                {
                    ItemStack st = s.getStack();
                    if (st != null && !st.isEmpty())
                    {
                        String k = host.normalizeForMatch(host.getItemNameKey(st));
                        if (!k.isEmpty())
                        {
                            entry.triedItemKeys.add(k);
                        }
                    }
                }
            }
            catch (Exception ignore) { }
            entry.lastMenuClickMs = nowMs;
            entry.lastMenuWindowId = windowId;
            entry.randomClicks++;
            entry.menuClicksSinceOpen++;
            entry.menuStartMs = nowMs;
            return;
        }

        host.queueClick(new ClickAction(step.slotNumber, 0, ClickType.PICKUP));
        entry.triedSlots.add(step.slotNumber);
        try
        {
            Slot s = gui.inventorySlots.getSlot(step.slotNumber);
            if (s != null)
            {
                ItemStack st = s.getStack();
                if (st != null && !st.isEmpty())
                {
                    String k = host.normalizeForMatch(host.getItemNameKey(st));
                    if (!k.isEmpty())
                    {
                        entry.triedItemKeys.add(k);
                    }
                }
            }
        }
        catch (Exception ignore) { }
        entry.menuClicksSinceOpen++;
        entry.lastMenuClickMs = nowMs;
        entry.lastMenuWindowId = windowId;
        if (step.directHit)
        {
            if (entry.advancedArgs != null && !entry.advancedArgs.isEmpty())
            {
                entry.awaitingMenu = false;
                entry.awaitingArgs = false;
                entry.awaitingParamsChest = true;
                entry.needOpenParamsChest = false;
                entry.advancedArgIndex = 0;
                entry.argsStartMs = 0L;
                entry.lastArgsActionMs = 0L;
                entry.argsMisses = 0;
                entry.usedArgSlots.clear();
                entry.argsGuiPage = 0;
                entry.argsPageTurnPending = false;
                entry.argsPageTurnStartMs = 0L;
                entry.argsPageTurnNextMs = 0L;
                entry.argsPageRetryCount = 0;
                entry.argsPageLastHash = "";
                entry.paramsStartMs = nowMs;
                entry.nextParamsActionMs = 0L;
                entry.paramsOpenAttempts = 0;
            }
            else
            {
                entry.awaitingMenu = false;
                state.current = null;
            }
        }
        else
        {
            entry.menuStartMs = nowMs;
        }
    }

    private static void abort(PlaceModuleHost host, PlaceState state, String reason)
    {
        if (host != null)
        {
            host.clearQueuedClicks();
            host.clearTpPathQueue();
        }
        if (state != null)
        {
            state.reset();
        }
        if (host != null && host.isDebugUi())
        {
            host.debugChat("/place aborted: " + reason);
        }
    }

    private static MenuStep findRandomMenuStep(PlaceModuleHost host, GuiContainer gui, PlaceEntry entry)
    {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.player == null)
        {
            return null;
        }

        List<Slot> candidates = new ArrayList<>();
        for (Slot slot : gui.inventorySlots.inventorySlots)
        {
            if (slot == null || slot.inventory == mc.player.inventory)
            {
                continue;
            }
            if (entry.triedSlots.contains(slot.slotNumber))
            {
                continue;
            }
            ItemStack stack = slot.getStack();
            if (stack == null || stack.isEmpty())
            {
                continue;
            }
            String key = host.normalizeForMatch(host.getItemNameKey(stack));
            if (!key.isEmpty() && entry.triedItemKeys.contains(key))
            {
                continue;
            }
            candidates.add(slot);
        }
        if (candidates.isEmpty())
        {
            return null;
        }
        Slot chosen = candidates.get((int) (Math.random() * candidates.size()));
        return new MenuStep(chosen.slotNumber, false);
    }

    private static MenuStep findMenuStep(PlaceModuleHost host, GuiContainer gui, String searchKey)
    {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.player == null)
        {
            return null;
        }
        String normSearch = host.normalizeForMatch(searchKey);
        MenuStep direct = findDirectMenuMatch(host, gui, mc.player, normSearch);
        if (direct != null)
        {
            return direct;
        }
        for (Slot slot : gui.inventorySlots.inventorySlots)
        {
            if (slot == null || slot.inventory == mc.player.inventory)
            {
                continue;
            }
            ItemStack stack = slot.getStack();
            if (stack == null || stack.isEmpty())
            {
                continue;
            }
            String key = host.getItemNameKey(stack);
            List<ItemStack> sub = host.getClickMenuMap().get(key);
            if (menuContainsSearch(host, sub, normSearch))
            {
                return new MenuStep(slot.slotNumber, false);
            }
        }
        return null;
    }

    private static MenuStep findDirectMenuMatch(PlaceModuleHost host, GuiContainer gui, EntityPlayer player, String normSearch)
    {
        // Prefer exact match to avoid ambiguous contains() cases like:
        // "Сравнить числа" vs "Сравнить числа (Облегчённая версия)".
        for (Slot slot : gui.inventorySlots.inventorySlots)
        {
            if (slot == null || slot.inventory == player.inventory)
            {
                continue;
            }
            ItemStack stack = slot.getStack();
            if (stack == null || stack.isEmpty())
            {
                continue;
            }
            String key = host.normalizeForMatch(host.getItemNameKey(stack));
            if (!key.isEmpty() && key.equals(normSearch))
            {
                return new MenuStep(slot.slotNumber, true);
            }
        }
        for (Slot slot : gui.inventorySlots.inventorySlots)
        {
            if (slot == null || slot.inventory == player.inventory)
            {
                continue;
            }
            ItemStack stack = slot.getStack();
            if (stack == null || stack.isEmpty())
            {
                continue;
            }
            String key = host.normalizeForMatch(host.getItemNameKey(stack));
            if (!key.isEmpty() && key.contains(normSearch))
            {
                return new MenuStep(slot.slotNumber, true);
            }
        }
        return null;
    }

    private static boolean menuContainsSearch(PlaceModuleHost host, List<ItemStack> items, String normSearch)
    {
        if (items == null || normSearch == null || normSearch.isEmpty())
        {
            return false;
        }
        for (ItemStack stack : items)
        {
            if (stack == null || stack.isEmpty())
            {
                continue;
            }
            String key = host.normalizeForMatch(host.getItemNameKey(stack));
            if (!key.isEmpty() && key.equals(normSearch))
            {
                return true;
            }
        }
        for (ItemStack stack : items)
        {
            if (stack == null || stack.isEmpty())
            {
                continue;
            }
            String key = host.normalizeForMatch(host.getItemNameKey(stack));
            if (!key.isEmpty() && key.contains(normSearch))
            {
                return true;
            }
        }
        return false;
    }

    private static void handleArgs(PlaceModuleHost host, PlaceState state, GuiContainer gui, long nowMs)
    {
        PlaceEntry entry = state.current;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.player == null)
        {
            return;
        }

        // Continue item(...) click injection sequence (hotbar -> target -> hotbar).
        if (entry.tempItemSeqStage >= 0 && entry.tempItemSeqStage < 3)
        {
            if (nowMs < entry.pendingArgNextMs)
            {
                return;
            }

            int slotToClick = entry.tempItemSeqStage == 0 ? entry.tempItemSeqSlot0
                : entry.tempItemSeqStage == 1 ? entry.tempItemSeqSlot1 : entry.tempItemSeqSlot2;
            if (slotToClick < 0)
            {
                abort(host, state, "item_seq_bad_slot");
                return;
            }

            try
            {
                // Don't proceed with a dirty cursor unless it's step 1 (we intentionally carry the item).
                if (entry.tempItemSeqStage != 1)
                {
                    ItemStack carried = mc.player.inventory.getItemStack();
                    if (carried != null && !carried.isEmpty())
                    {
                        if (!tryClearCarriedToInventory(gui, mc))
                        {
                            host.setActionBar(false, "&c/placeadvanced: cursor not empty", 2500L);
                            abort(host, state, "cursor_not_empty");
                            return;
                        }
                        entry.pendingArgNextMs = nowMs + host.placeDelayMs(350L);
                        return;
                    }
                }
            }
            catch (Exception ignore) { }

            host.queueClick(new ClickAction(slotToClick, 0, ClickType.PICKUP));
            entry.tempItemSeqStage++;
            entry.pendingArgNextMs = nowMs + host.placeDelayMs(500L);
            entry.lastArgsActionMs = nowMs;

            if (entry.tempItemSeqStage >= 3)
            {
                // Restore original hotbar stack.
                try
                {
                    if (entry.tempHotbarSlot >= 0 && entry.tempHotbarSlot < 9 && mc.player.connection != null)
                    {
                        ItemStack restore = entry.tempHotbarOriginal == null ? ItemStack.EMPTY : entry.tempHotbarOriginal;
                        mc.player.inventory.setInventorySlotContents(entry.tempHotbarSlot, restore);
                        mc.player.connection.sendPacket(new CPacketCreativeInventoryAction(36 + entry.tempHotbarSlot, restore));
                    }
                }
                catch (Exception ignore) { }

                // Mark arg as consumed.
                if (entry.tempItemTargetSlot >= 0)
                {
                    entry.usedArgSlots.add(entry.tempItemTargetSlot);
                }
                entry.advancedArgIndex++;
                if (entry.tempItemExtraClicks > 0 && entry.tempItemTargetSlot >= 0)
                {
                    entry.pendingArgClickSlot = entry.tempItemTargetSlot;
                    entry.pendingArgClicks = entry.tempItemExtraClicks;
                    entry.pendingArgNextMs = nowMs + host.placeDelayMs(500L);
                }

                entry.tempHotbarSlot = -1;
                entry.tempHotbarOriginal = ItemStack.EMPTY;
                entry.tempHotbarClearMs = 0L;
                entry.tempItemSeqStage = -1;
                entry.tempItemSeqSlot0 = -1;
                entry.tempItemSeqSlot1 = -1;
                entry.tempItemSeqSlot2 = -1;
                entry.tempItemTargetSlot = -1;
                entry.tempItemExtraClicks = 0;
            }
            return;
        }

        // If we used a temp hotbar slot for item(...) injection, clear it after a short delay so it
        // doesn't remain in the player's inventory (server may reject/ignore the GUI insert).
        if (entry.tempHotbarSlot >= 0 && entry.tempHotbarSlot < 9 && entry.tempHotbarClearMs > 0L && nowMs >= entry.tempHotbarClearMs)
        {
            try
            {
                if (mc.player.connection != null)
                {
                    mc.player.inventory.setInventorySlotContents(entry.tempHotbarSlot, ItemStack.EMPTY);
                    mc.player.connection.sendPacket(new CPacketCreativeInventoryAction(36 + entry.tempHotbarSlot, ItemStack.EMPTY));
                }
            }
            catch (Exception ignore) { }
            entry.tempHotbarSlot = -1;
            entry.tempHotbarClearMs = 0L;
        }
        if (entry.pendingArgClicks > 0)
        {
            try
            {
                ItemStack carried = mc.player.inventory.getItemStack();
                if (carried != null && !carried.isEmpty())
                {
                    return;
                }
            }
            catch (Exception ignore) { }
            if (nowMs >= entry.pendingArgNextMs && entry.pendingArgClickSlot >= 0)
            {
                host.queueClick(new ClickAction(entry.pendingArgClickSlot, 0, ClickType.PICKUP));
                entry.pendingArgClicks--;
                entry.pendingArgNextMs = nowMs + host.placeDelayMs(500L);
                entry.lastArgsActionMs = nowMs;
                if (entry.pendingArgClicks <= 0)
                {
                    entry.pendingArgClickSlot = -1;
                }
            }
            return;
        }
        if (entry.advancedArgs == null || entry.advancedArgs.isEmpty())
        {
            entry.awaitingArgs = false;
            state.current = null;
            return;
        }
        if (entry.advancedArgIndex >= entry.advancedArgs.size())
        {
            try
            {
                ItemStack carried = mc.player.inventory.getItemStack();
                if (carried != null && !carried.isEmpty())
                {
                    if (!tryClearCarriedToInventory(gui, mc))
                    {
                        host.setActionBar(false, "&c/placeadvanced: cursor not empty", 2500L);
                        abort(host, state, "cursor_not_empty");
                        return;
                    }
                }
            }
            catch (Exception ignore) { }
            entry.awaitingArgs = false;
            host.closeCurrentScreen();
            state.current = null;
            return;
        }
        if (nowMs - entry.argsStartMs > 12000L)
        {
            host.setActionBar(false, "&c/placeadvanced: args timeout", 2500L);
            abort(host, state, "args_timeout");
            return;
        }
        if (host.isInputActive())
        {
            return;
        }
        if (entry.lastArgsActionMs > 0 && nowMs - entry.lastArgsActionMs < host.placeDelayMs(180L))
        {
            return;
        }

        PlaceArg arg = entry.advancedArgs.get(entry.advancedArgIndex);
        Integer resolvedGuiIndex = arg.slotIndex;
        if (arg.slotIndex != null && arg.slotGuiIndex)
        {
            SlotRouteResult route = ensureArgGuiPage(host, state, gui, entry, arg, nowMs);
            if (route.waiting)
            {
                return;
            }
            if (route.skipArg)
            {
                entry.advancedArgIndex++;
                entry.lastArgsActionMs = nowMs;
                return;
            }
            resolvedGuiIndex = route.resolvedSlotIndex;
        }

        if (arg.clickOnly)
        {
            if (resolvedGuiIndex == null)
            {
                entry.argsMisses++;
                return;
            }
            if (entry.argsStartMs > 0 && nowMs - entry.argsStartMs < host.placeDelayMs(250L))
            {
                return;
            }
            if (countNonPlayerSlots(gui) == 0)
            {
                return;
            }
            int clickSlot = resolveClickSlotIndex(gui, arg, resolvedGuiIndex);
            if (clickSlot < 0)
            {
                if (host.isDebugUi() && entry.argsMisses % 10 == 0)
                {
                    host.debugChat(String.format("placeadvanced clickOnly: resolve failed slot=%s guiIndex=%s nonPlayer=%d window=%d",
                        String.valueOf(resolvedGuiIndex),
                        String.valueOf(arg.slotGuiIndex),
                        countNonPlayerSlots(gui),
                        gui.inventorySlots.windowId));
                }
                entry.argsMisses++;
                return;
            }
            if (host.isDebugUi())
            {
                host.debugChat(String.format("placeadvanced clickOnly: slot=%d guiIndex=%s clicks=%d window=%d",
                    clickSlot,
                    String.valueOf(arg.slotGuiIndex),
                    arg.clicks > 0 ? arg.clicks : 1,
                    gui.inventorySlots.windowId));
            }
            entry.usedArgSlots.add(clickSlot);
            entry.pendingArgClickSlot = clickSlot;
            entry.pendingArgClicks = arg.clicks > 0 ? arg.clicks : 1;
            entry.pendingArgNextMs = nowMs + host.placeDelayMs(500L);
            entry.lastArgsActionMs = nowMs;
            entry.advancedArgIndex++;
            return;
        }

        Slot target;
        if (resolvedGuiIndex != null)
        {
            target = resolveArgSlot(gui, arg, resolvedGuiIndex);
            if (target == null || target.inventory == mc.player.inventory
                || (entry.usedArgSlots != null && entry.usedArgSlots.contains(target.slotNumber)))
            {
                target = null;
            }
        }
        else
        {
            target = findArgTargetSlot(host, gui, arg, entry.usedArgSlots);
        }

        if (target == null)
        {
            entry.argsMisses++;
            if (host.isDebugUi() && entry.argsMisses % 10 == 0)
            {
                host.debugChat(String.format("placeadvanced: no slot key='%s' slot=%s guiIndex=%s meta=%s misses=%d",
                    arg.keyRaw,
                    String.valueOf(arg.slotIndex),
                    String.valueOf(arg.slotGuiIndex),
                    String.valueOf(arg.glassMetaFilter),
                    entry.argsMisses));
            }
            if (entry.argsMisses > 60)
            {
                host.setActionBar(false, "&c/placeadvanced: no matching args slot", 2500L);
                abort(host, state, "no_args_slot");
            }
            return;
        }

        ItemStack presetStack = target.getStack();
        if (arg.mode == PlaceModule.INPUT_MODE_ITEM)
        {
            try
            {
                ItemStack carried = mc.player.inventory.getItemStack();
                if (carried != null && !carried.isEmpty())
                {
                    return;
                }
            }
            catch (Exception ignore) { }

            ItemStack stack = PlaceParser.parseItemSpec(arg.valueRaw);
            if (stack == null || stack.isEmpty())
            {
                host.setActionBar(false, "&c/placeadvanced: invalid item()", 2500L);
                abort(host, state, "invalid_item");
                return;
            }
            try
            {
                // Many servers reject "sendSlotPacket" for custom GUIs; simulate normal click placement:
                // put stack into a temp hotbar slot (creative inventory action), pick it up, click target slot.
                int hotbar = -1;
                for (int h = 0; h < 9; h++)
                {
                    ItemStack hs = mc.player.inventory.getStackInSlot(h);
                    if (hs == null || hs.isEmpty())
                    {
                        hotbar = h;
                        break;
                    }
                }
                if (hotbar < 0)
                {
                    // Fallback: reuse current hotbar slot and restore after injection.
                    hotbar = mc.player.inventory.currentItem;
                }

                entry.tempHotbarSlot = hotbar;
                try
                {
                    entry.tempHotbarOriginal = mc.player.inventory.getStackInSlot(hotbar).copy();
                }
                catch (Exception ignore) { entry.tempHotbarOriginal = ItemStack.EMPTY; }

                mc.player.inventory.setInventorySlotContents(hotbar, stack);
                if (mc.player.connection != null)
                {
                    mc.player.connection.sendPacket(new CPacketCreativeInventoryAction(36 + hotbar, stack));
                }

                int hotbarContainerSlot = -1;
                for (int i = 0; i < gui.inventorySlots.inventorySlots.size(); i++)
                {
                    Slot s = gui.inventorySlots.inventorySlots.get(i);
                    if (s != null && s.inventory == mc.player.inventory && s.getSlotIndex() == hotbar)
                    {
                        hotbarContainerSlot = i;
                        break;
                    }
                }
                if (hotbarContainerSlot < 0)
                {
                    host.setActionBar(false, "&c/placeadvanced: hotbar slot not found in container", 2500L);
                    abort(host, state, "no_hotbar_container_slot");
                    return;
                }

                entry.tempItemSeqStage = 0;
                entry.tempItemSeqSlot0 = hotbarContainerSlot;
                entry.tempItemSeqSlot1 = target.slotNumber;
                entry.tempItemSeqSlot2 = hotbarContainerSlot;
                entry.tempItemTargetSlot = target.slotNumber;
                entry.tempItemExtraClicks = arg.clicks > 0 ? arg.clicks : 0;
                entry.pendingArgNextMs = nowMs + host.placeDelayMs(500L);
                entry.lastArgsActionMs = nowMs;
                entry.argsMisses = 0;
                return;
            }
            catch (Exception ignore) { }
        }
        else
        {
            String preset = presetStack == null || presetStack.isEmpty() ? "" : host.extractEntryText(presetStack, arg.mode);
            ItemStack template = host.templateForMode(arg.mode);
            host.setInputSaveVariable(arg.saveVariable);
            host.startSlotInput(gui, target, template, arg.mode, preset, "Arg: " + arg.keyRaw);
            host.setInputText(arg.valueRaw);
            host.submitInputText(false);
        }

        entry.usedArgSlots.add(target.slotNumber);
        entry.advancedArgIndex++;
        if (arg.clicks > 0)
        {
            entry.pendingArgClickSlot = target.slotNumber;
            entry.pendingArgClicks = arg.clicks;
            entry.pendingArgNextMs = nowMs + host.placeDelayMs(500L);
        }
        entry.argsMisses = 0;
        entry.lastArgsActionMs = nowMs;
        if (entry.advancedArgIndex >= entry.advancedArgs.size())
        {
            entry.awaitingArgs = false;
            host.closeCurrentScreen();
            state.current = null;
        }
    }

    private static Slot findArgTargetSlot(PlaceModuleHost host, GuiContainer gui, PlaceArg arg, Set<Integer> used)
    {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.player == null)
        {
            return null;
        }
        if (arg.slotIndex != null)
        {
            try
            {
                Slot slot = resolveArgSlot(gui, arg);
                if (slot == null || slot.inventory == mc.player.inventory)
                {
                    return null;
                }
                if (used != null && used.contains(slot.slotNumber))
                {
                    return null;
                }
                return slot;
            }
            catch (Exception ignore)
            {
                return null;
            }
        }
        if (arg.keyNorm == null || arg.keyNorm.isEmpty())
        {
            if (arg.glassMetaFilter == null)
            {
                // Allow empty key to match any glass target.
                // Keep going and only filter by glass meta or availability.
            }
        }

        List<Slot> bases = new ArrayList<>();
        for (Slot slot : gui.inventorySlots.inventorySlots)
        {
            if (slot == null || slot.inventory == mc.player.inventory)
            {
                continue;
            }
            ItemStack st = slot.getStack();
            if (st == null || st.isEmpty() || !host.isGlassPane(st))
            {
                continue;
            }
            if (st.getMetadata() == 15)
            {
                continue;
            }
            if (arg.glassMetaFilter != null && st.getMetadata() != arg.glassMetaFilter.intValue())
            {
                continue;
            }
            if (arg.keyNorm != null && !arg.keyNorm.isEmpty())
            {
                String name = host.normalizeForMatch(TextFormatting.getTextWithoutFormattingCodes(st.getDisplayName()));
                if (name.isEmpty() || !name.contains(arg.keyNorm))
                {
                    continue;
                }
            }
            bases.add(slot);
        }
        if (bases.isEmpty())
        {
            return null;
        }
        bases.sort(Comparator.comparingInt(a -> a.slotNumber));

        for (Slot base : bases)
        {
            Slot candidate = findCandidateSlotForArg(host, gui, base);
            if (candidate == null)
            {
                continue;
            }
            if (used != null && used.contains(candidate.slotNumber))
            {
                continue;
            }
            return candidate;
        }
        return null;
    }

    private static boolean tryClearCarriedToInventory(GuiContainer gui, Minecraft mc)
    {
        if (gui == null || mc == null || mc.player == null || mc.playerController == null)
        {
            return false;
        }
        try
        {
            ItemStack carried = mc.player.inventory.getItemStack();
            if (carried == null || carried.isEmpty())
            {
                return true;
            }
            for (Slot s : gui.inventorySlots.inventorySlots)
            {
                if (s == null || s.inventory != mc.player.inventory)
                {
                    continue;
                }
                if (!s.getHasStack())
                {
                    mc.playerController.windowClick(gui.inventorySlots.windowId, s.slotNumber, 0, ClickType.PICKUP, mc.player);
                    return true;
                }
            }
        }
        catch (Exception ignore) { }
        return false;
    }

    private static Slot findCandidateSlotForArg(PlaceModuleHost host, GuiContainer gui, Slot base)
    {
        int x = base.xPos;
        int y = base.yPos;
        int[][] offsets = new int[][]{ {0, 18}, {-18, 0}, {18, 0}, {0, -18} };
        Slot bestEmpty = null;
        for (int[] off : offsets)
        {
            Slot slot = findSlotAt(gui, x + off[0], y + off[1]);
            if (slot == null)
            {
                continue;
            }
            if (!slot.getHasStack())
            {
                if (bestEmpty == null)
                {
                    bestEmpty = slot;
                }
                continue;
            }
            ItemStack st = slot.getStack();
            if (st != null && !st.isEmpty())
            {
                if (host.isGlassPane(st))
                {
                    continue;
                }
                return slot;
            }
        }
        return bestEmpty;
    }

    private static Slot findSlotAt(GuiContainer gui, int x, int y)
    {
        for (Slot slot : gui.inventorySlots.inventorySlots)
        {
            if (slot != null && slot.xPos == x && slot.yPos == y)
            {
                return slot;
            }
        }
        return null;
    }

    private static Slot resolveArgSlot(GuiContainer gui, PlaceArg arg)
    {
        return resolveArgSlot(gui, arg, arg == null ? null : arg.slotIndex);
    }

    private static Slot resolveArgSlot(GuiContainer gui, PlaceArg arg, Integer slotIndex)
    {
        if (gui == null || gui.inventorySlots == null || arg == null || slotIndex == null)
        {
            return null;
        }
        if (slotIndex == null)
        {
            return null;
        }
        if (!arg.slotGuiIndex)
        {
            try
            {
                return gui.inventorySlots.getSlot(slotIndex);
            }
            catch (Exception ignore)
            {
                return null;
            }
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.player == null)
        {
            return null;
        }
        int idx = 0;
        for (Slot slot : gui.inventorySlots.inventorySlots)
        {
            if (slot == null || slot.inventory == mc.player.inventory)
            {
                continue;
            }
            if (idx == slotIndex)
            {
                return slot;
            }
            idx++;
        }
        return null;
    }

    private static int resolveClickSlotIndex(GuiContainer gui, PlaceArg arg, Integer slotIndex)
    {
        if (gui == null || gui.inventorySlots == null || arg == null || slotIndex == null)
        {
            return -1;
        }
        if (!arg.slotGuiIndex)
        {
            return slotIndex;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.player == null)
        {
            return -1;
        }
        int idx = 0;
        for (Slot slot : gui.inventorySlots.inventorySlots)
        {
            if (slot == null || slot.inventory == mc.player.inventory)
            {
                continue;
            }
            if (idx == slotIndex)
            {
                return slot.slotNumber;
            }
            idx++;
        }
        return -1;
    }

    private static int countNonPlayerSlots(GuiContainer gui)
    {
        Minecraft mc = Minecraft.getMinecraft();
        if (gui == null || gui.inventorySlots == null || mc == null || mc.player == null)
        {
            return 0;
        }
        int count = 0;
        for (Slot slot : gui.inventorySlots.inventorySlots)
        {
            if (slot == null || slot.inventory == mc.player.inventory)
            {
                continue;
            }
            count++;
        }
        return count;
    }

    private static final class SlotRouteResult
    {
        final boolean waiting;
        final boolean skipArg;
        final Integer resolvedSlotIndex;

        SlotRouteResult(boolean waiting, boolean skipArg, Integer resolvedSlotIndex)
        {
            this.waiting = waiting;
            this.skipArg = skipArg;
            this.resolvedSlotIndex = resolvedSlotIndex;
        }
    }

    private static SlotRouteResult ensureArgGuiPage(
        PlaceModuleHost host,
        PlaceState state,
        GuiContainer gui,
        PlaceEntry entry,
        PlaceArg arg,
        long nowMs)
    {
        int nonPlayerSlots = countNonPlayerSlots(gui);
        if (nonPlayerSlots <= 0 || arg.slotIndex == null)
        {
            return new SlotRouteResult(false, false, arg.slotIndex);
        }

        int abs = arg.slotIndex;
        int targetPage = abs / nonPlayerSlots;
        int targetLocal = abs % nonPlayerSlots;

        if (targetPage <= 0)
        {
            entry.argsGuiPage = 0;
            entry.argsPageTurnPending = false;
            entry.argsPageRetryCount = 0;
            return new SlotRouteResult(false, false, targetLocal);
        }

        if (entry.argsGuiPage > targetPage)
        {
            host.setActionBar(false, "&cplaceadvanced: нельзя листать назад к странице аргумента", 2500L);
            LOGGER.info("PLACE_PAGE_CLICK blocked_back currentPage={} targetPage={} slot={}",
                entry.argsGuiPage + 1, targetPage + 1, abs);
            if (host.isDebugUi())
            {
                host.debugChat("placeadvanced page route: currentPage=" + entry.argsGuiPage + " targetPage=" + targetPage
                    + " slot=" + abs + " -> skip");
            }
            return new SlotRouteResult(false, true, null);
        }

        if (entry.argsGuiPage == targetPage)
        {
            entry.argsPageTurnPending = false;
            entry.argsPageRetryCount = 0;
            return new SlotRouteResult(false, false, targetLocal);
        }

        if (entry.argsPageTurnPending)
        {
            Minecraft mc = Minecraft.getMinecraft();
            boolean cursorEmpty = true;
            try
            {
                ItemStack carried = mc == null || mc.player == null ? ItemStack.EMPTY : mc.player.inventory.getItemStack();
                cursorEmpty = carried == null || carried.isEmpty();
            }
            catch (Exception ignore) { }

            String currentHash = buildNonPlayerHash(gui);
            boolean changed = entry.argsPageLastHash != null && !entry.argsPageLastHash.equals(currentHash);
            if (cursorEmpty && changed)
            {
                entry.argsGuiPage++;
                entry.argsPageTurnPending = false;
                entry.argsPageTurnStartMs = 0L;
                entry.argsPageTurnNextMs = nowMs + host.placeDelayMs(150L);
                entry.argsPageRetryCount = 0;
                if (host.isDebugUi())
                {
                    host.debugChat("placeadvanced page switched to " + (entry.argsGuiPage + 1));
                }
                LOGGER.info("PLACE_PAGE_CLICK switched page={} targetPage={} slot={}",
                    entry.argsGuiPage + 1, targetPage + 1, abs);
                if (entry.argsGuiPage >= targetPage)
                {
                    return new SlotRouteResult(false, false, targetLocal);
                }
                return new SlotRouteResult(true, false, null);
            }
            if (nowMs - entry.argsPageTurnStartMs > PAGE_TURN_TIMEOUT_MS)
            {
                entry.argsPageTurnPending = false;
                entry.argsPageTurnStartMs = 0L;
                entry.argsPageTurnNextMs = nowMs + host.placeDelayMs(180L);
                entry.argsPageRetryCount++;
                if (host.isDebugUi())
                {
                    host.debugChat("placeadvanced page wait timeout: retry=" + entry.argsPageRetryCount
                        + "/" + PAGE_TURN_MAX_RETRIES + " targetPage=" + (targetPage + 1));
                }
                LOGGER.info("PLACE_PAGE_CLICK wait_timeout retry={}/{} targetPage={} slot={}",
                    entry.argsPageRetryCount, PAGE_TURN_MAX_RETRIES, targetPage + 1, abs);
            }
            else
            {
                return new SlotRouteResult(true, false, null);
            }
        }

        if (entry.argsPageRetryCount >= PAGE_TURN_MAX_RETRIES)
        {
            host.setActionBar(false, "&cplaceadvanced: таймаут листания страницы аргумента", 2500L);
            LOGGER.info("PLACE_PAGE_CLICK retries_exhausted targetPage={} slot={}", targetPage + 1, abs);
            if (host.isDebugUi())
            {
                host.debugChat("placeadvanced page route failed: retries exhausted targetPage=" + (targetPage + 1));
            }
            return new SlotRouteResult(false, true, null);
        }

        if (nowMs < entry.argsPageTurnNextMs)
        {
            return new SlotRouteResult(true, false, null);
        }

        Slot nextArrow = findNextPageArrowSlot(gui);
        if (nextArrow == null)
        {
            host.setActionBar(false, "&cplaceadvanced: стрелка следующей страницы не найдена", 2500L);
            LOGGER.info("PLACE_PAGE_CLICK no_next_arrow targetPage={} slot={}", targetPage + 1, abs);
            if (host.isDebugUi())
            {
                host.debugChat("placeadvanced page route failed: no next arrow targetPage=" + (targetPage + 1));
            }
            return new SlotRouteResult(false, true, null);
        }

        host.queueClick(new ClickAction(nextArrow.slotNumber, 0, ClickType.PICKUP));
        LOGGER.info("PLACE_PAGE_CLICK click currentPage={} targetPage={} slot={} arrowSlot={}",
            entry.argsGuiPage + 1, targetPage + 1, abs, nextArrow.slotNumber);
        entry.lastArgsActionMs = nowMs;
        entry.argsPageLastHash = buildNonPlayerHash(gui);
        entry.argsPageTurnPending = true;
        entry.argsPageTurnStartMs = nowMs;
        entry.argsPageTurnNextMs = nowMs + host.placeDelayMs(250L);
        if (host.isDebugUi())
        {
            host.debugChat("placeadvanced page click: current=" + (entry.argsGuiPage + 1)
                + " target=" + (targetPage + 1)
                + " slot=" + abs
                + " arrowSlot=" + nextArrow.slotNumber);
        }
        return new SlotRouteResult(true, false, null);
    }

    private static String buildNonPlayerHash(GuiContainer gui)
    {
        if (gui == null || gui.inventorySlots == null)
        {
            return "";
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.player == null)
        {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (Slot slot : gui.inventorySlots.inventorySlots)
        {
            if (slot == null || slot.inventory == mc.player.inventory)
            {
                continue;
            }
            ItemStack st = slot.getStack();
            if (st == null || st.isEmpty())
            {
                sb.append("[];");
            }
            else
            {
                sb.append(st.getItem().getRegistryName()).append(':')
                    .append(st.getMetadata()).append(':')
                    .append(st.getCount()).append(':')
                    .append(st.hasDisplayName() ? TextFormatting.getTextWithoutFormattingCodes(st.getDisplayName()) : "")
                    .append(';');
            }
        }
        return Integer.toHexString(sb.toString().hashCode());
    }

    private static Slot findNextPageArrowSlot(GuiContainer gui)
    {
        if (gui == null || gui.inventorySlots == null)
        {
            return null;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.player == null)
        {
            return null;
        }

        List<Slot> nonPlayer = new ArrayList<>();
        for (Slot slot : gui.inventorySlots.inventorySlots)
        {
            if (slot == null || slot.inventory == mc.player.inventory)
            {
                continue;
            }
            nonPlayer.add(slot);
        }
        if (nonPlayer.isEmpty())
        {
            return null;
        }
        Slot last = nonPlayer.get(nonPlayer.size() - 1);
        ItemStack st = last.getStack();
        return isNextPageArrowItem(st) ? last : null;
    }

    private static boolean isNextPageArrowItem(ItemStack st)
    {
        if (st == null || st.isEmpty() || st.getItem() != Items.ARROW)
        {
            return false;
        }
        List<String> lore = ItemStackUtils.getLore(st);
        if (lore == null || lore.isEmpty())
        {
            return false;
        }
        boolean hasOpen = false;
        boolean hasNext = false;
        for (String ln : lore)
        {
            String n = normalizePageLoreLine(ln);
            if (n.contains("нажми чтобы открыть"))
            {
                hasOpen = true;
            }
            if (n.contains("следующую страницу"))
            {
                hasNext = true;
            }
        }
        return hasOpen && hasNext;
    }

    private static String normalizePageLoreLine(String line)
    {
        String n = line == null ? "" : TextFormatting.getTextWithoutFormattingCodes(line);
        if (n == null)
        {
            n = "";
        }
        n = n.replace('\u00A0', ' ').toLowerCase();
        n = n.replaceAll("[^\\p{L}\\p{N}\\s]+", " ");
        n = n.replaceAll("\\s+", " ").trim();
        return n;
    }
}
