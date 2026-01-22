package com.example.examplemod.feature.place;

import com.example.examplemod.model.ClickAction;
import com.example.examplemod.model.MenuStep;
import com.example.examplemod.model.PlaceArg;
import com.example.examplemod.model.PlaceEntry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.ClickType;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.CPacketCreativeInventoryAction;
import net.minecraft.util.text.TextFormatting;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

final class PlaceGuiHandler
{
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
        if (arg.clickOnly)
        {
            if (arg.slotIndex == null)
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
            int clickSlot = resolveClickSlotIndex(gui, arg);
            if (clickSlot < 0)
            {
                if (host.isDebugUi() && entry.argsMisses % 10 == 0)
                {
                    host.debugChat(String.format("placeadvanced clickOnly: resolve failed slot=%s guiIndex=%s nonPlayer=%d window=%d",
                        String.valueOf(arg.slotIndex),
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
        Slot target = findArgTargetSlot(host, gui, arg, entry.usedArgSlots);
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
                    host.setActionBar(false, "&c/placeadvanced: no empty hotbar slot for item()", 2500L);
                    abort(host, state, "no_hotbar_slot");
                    return;
                }

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

                host.queueClick(new ClickAction(hotbarContainerSlot, 0, ClickType.PICKUP));
                host.queueClick(new ClickAction(target.slotNumber, 0, ClickType.PICKUP));
                host.queueClick(new ClickAction(hotbarContainerSlot, 0, ClickType.PICKUP));

                entry.tempHotbarSlot = hotbar;
                entry.tempHotbarClearMs = nowMs + host.placeDelayMs(900L);
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
        if (gui == null || gui.inventorySlots == null || arg == null || arg.slotIndex == null)
        {
            return null;
        }
        if (!arg.slotGuiIndex)
        {
            try
            {
                return gui.inventorySlots.getSlot(arg.slotIndex);
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
            if (idx == arg.slotIndex)
            {
                return slot;
            }
            idx++;
        }
        try
        {
            return gui.inventorySlots.getSlot(arg.slotIndex);
        }
        catch (Exception ignore)
        {
            return null;
        }
    }

    private static int resolveClickSlotIndex(GuiContainer gui, PlaceArg arg)
    {
        if (gui == null || gui.inventorySlots == null || arg == null || arg.slotIndex == null)
        {
            return -1;
        }
        if (!arg.slotGuiIndex)
        {
            return arg.slotIndex;
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
            if (idx == arg.slotIndex)
            {
                return slot.slotNumber;
            }
            idx++;
        }
        return arg.slotIndex;
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
}
