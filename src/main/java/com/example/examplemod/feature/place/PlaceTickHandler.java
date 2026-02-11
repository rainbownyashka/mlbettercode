package com.example.examplemod.feature.place;

import com.example.examplemod.model.PlaceEntry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.CPacketCreativeInventoryAction;
import net.minecraft.network.play.client.CPacketHeldItemChange;
import net.minecraft.network.play.client.CPacketPlayer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

final class PlaceTickHandler
{
    private PlaceTickHandler() {}

    static void onClientTick(PlaceModuleHost host, PlaceState state, Minecraft mc, long nowMs)
    {
        if (host == null || state == null || mc == null)
        {
            return;
        }
        if (mc.world == null || mc.player == null)
        {
            state.reset();
            return;
        }
        if (!state.active)
        {
            return;
        }
        if (!host.isEditorModeActive() || !host.isDevCreativeScoreboard(mc))
        {
            return;
        }
        if (host.isHoldingIronOrGoldIngot(mc))
        {
            return;
        }
        if (mc.currentScreen != null)
        {
            if (!(mc.currentScreen instanceof GuiContainer))
            {
                host.closeCurrentScreen();
                if (host.isDebugUi() && mc.player != null)
                {
                    host.debugChat("/place: closed non-container GUI to continue");
                }
            }
            else
            {
                // Some server interactions (cycle/function sign config) may unexpectedly open a container GUI.
                // While we are in post-place sign stages, close such GUIs so printing can continue.
                try
                {
                    if (state.current != null
                        && state.current.placedBlock
                        && state.current.postPlaceKind != PlaceEntry.POST_PLACE_NONE
                        && state.current.postPlaceStage < 3)
                    {
                        host.closeCurrentScreen();
                        if (host.isDebugUi() && mc.player != null)
                        {
                            host.debugChat("/place: closed unexpected container during post-place");
                        }
                    }
                }
                catch (Exception ignore) { }
            }
            return;
        }

        if (state.current == null)
        {
            state.current = state.queue.poll();
            if (state.current == null)
            {
                state.active = false;
                return;
            }
        }

        PlaceEntry entry = state.current;

        // If the block is already present (server delayed updates), detect it and continue without re-placing.
        if (!entry.placedBlock && entry.pos != null && entry.block != null)
        {
            try
            {
                if (mc.world.isBlockLoaded(entry.pos, false)
                    && mc.world.getBlockState(entry.pos).getBlock() == entry.block)
                {
                    entry.placedBlock = true;
                    entry.placedConfirmedMs = nowMs;
                    try
                    {
                        host.cachePlacedBlock(mc.world, entry.pos, entry.block);
                    }
                    catch (Exception ignore) { }
                    startAfterPlaced(entry, nowMs, host);
                }
            }
            catch (Exception ignore) { }
        }

        if (entry.placedBlock
            && entry.pos != null
            && entry.block != null)
        {
            // Server may "ghost place" and then revert the block back to air; handle it by retrying placement.
            try
            {
                if (mc.world.isBlockLoaded(entry.pos, false)
                    && mc.world.getBlockState(entry.pos).getBlock() != entry.block
                    && entry.placedConfirmedMs > 0L
                    && nowMs - entry.placedConfirmedMs > host.placeDelayMs(250L))
                {
                    entry.placedLostCount++;
                    entry.placedBlock = false;
                    entry.placedConfirmedMs = 0L;
                    entry.awaitingMenu = false;
                    entry.needOpenMenu = false;
                    entry.menuStartMs = 0L;
                    entry.lastMenuClickMs = 0L;
                    entry.lastMenuWindowId = -1;
                    entry.awaitingParamsChest = false;
                    entry.needOpenParamsChest = false;
                    entry.awaitingArgs = false;
                    entry.advancedArgIndex = 0;
                    entry.argsStartMs = 0L;
                    entry.lastArgsActionMs = 0L;
                    entry.pendingArgClickSlot = -1;
                    entry.pendingArgClicks = 0;
                    entry.pendingArgNextMs = 0L;
                    entry.postPlaceStage = 0;
                    entry.postPlaceNextMs = 0L;

                    entry.placeAttempts++;
                    entry.nextPlaceAttemptMs = nowMs + host.placeBlockRetryDelayMs();

                    if (host.isDebugUi())
                    {
                        host.debugChat("/place: block reverted by server, retrying placement (lost=" + entry.placedLostCount + ")");
                    }
                    return;
                }
            }
            catch (Exception ignore) { }
        }

        if (entry.placedBlock
            && entry.awaitingMenu
            && !entry.needOpenMenu
            && entry.lastMenuWindowId != -1
            && nowMs - entry.lastMenuClickMs > 300L)
        {
            entry.needOpenMenu = true;
            entry.menuStartMs = nowMs;
            entry.nextMenuActionMs = nowMs + host.placeDelayMs(250L);
            entry.menuClicksSinceOpen = 0;
            entry.triedWindowId = -1;
            entry.menuNonEmptySinceMs = 0L;
            entry.menuNonEmptyWindowId = -1;
        }

        if (entry.placedBlock)
        {
            // If we clicked the sign but the GUI never opened, retry right-click a few times instead of stalling forever.
            if (entry.awaitingMenu
                && !entry.needOpenMenu
                && entry.lastMenuWindowId == -1
                && entry.menuStartMs > 0L
                && nowMs - entry.menuStartMs > host.placeDelayMs(1600L))
            {
                entry.menuOpenAttempts++;
                if (entry.menuOpenAttempts > 8)
                {
                    // Force re-place: either sign isn't clickable anymore or server canceled placement silently.
                    entry.placedBlock = false;
                    entry.placedConfirmedMs = 0L;
                    entry.awaitingMenu = false;
                    entry.needOpenMenu = false;
                    entry.menuStartMs = 0L;
                    entry.lastMenuClickMs = 0L;
                    entry.lastMenuWindowId = -1;
                    entry.menuOpenAttempts = 0;
                    entry.nextPlaceAttemptMs = nowMs + host.placeBlockRetryDelayMs();
                    if (host.isDebugUi())
                    {
                        host.debugChat("/place: menu did not open, forcing re-place");
                    }
                    return;
                }
                entry.needOpenMenu = true;
                entry.nextMenuActionMs = nowMs + host.placeDelayMs(250L);
                entry.menuStartMs = nowMs;
                if (host.isDebugUi())
                {
                    host.debugChat("/place: menu not opened yet, retry click (" + entry.menuOpenAttempts + ")");
                }
                return;
            }

            // Post-place sign interactions (functions/cycles). These don't use menus/containers.
            if (entry.postPlaceKind != PlaceEntry.POST_PLACE_NONE && entry.postPlaceStage < 3)
            {
                if (nowMs < entry.postPlaceNextMs)
                {
                    return;
                }

                BlockPos signPos = host.findSignAtZMinus1(mc.world, entry.pos);
                BlockPos clickPos = signPos != null ? signPos : entry.pos;
                if (clickPos == null)
                {
                    host.setActionBar(false, "&c/placeadvanced: no sign pos", 2500L);
                    state.reset();
                    return;
                }

                if (entry.postPlaceStage == 0)
                {
                    if (entry.postPlaceName == null || entry.postPlaceName.trim().isEmpty())
                    {
                        host.setActionBar(false, "&c/placeadvanced: missing name", 2500L);
                        state.reset();
                        return;
                    }
                    int slot = host.giveQuickInputItemToHotbar(PlaceModule.INPUT_MODE_TEXT, entry.postPlaceName, false);
                    if (slot >= 0 && slot < 9 && mc.player.inventory.currentItem != slot)
                    {
                        mc.player.inventory.currentItem = slot;
                        if (mc.playerController != null)
                        {
                            mc.playerController.updateController();
                        }
                        if (mc.player != null && mc.player.connection != null)
                        {
                            mc.player.connection.sendPacket(new CPacketHeldItemChange(slot));
                        }
                    }
                    try
                    {
                        Vec3d hit = new Vec3d(0.5, 0.5, 0.5);
                        runPlaceClick(mc, clickPos, hit);
                        mc.player.swingArm(EnumHand.MAIN_HAND);
                    }
                    catch (Exception ignore) { }

                    entry.postPlaceStage = 1;
                    entry.postPlaceNextMs = nowMs + host.placeDelayMs(550L);
                    return;
                }

                if (entry.postPlaceStage == 1 && entry.postPlaceKind == PlaceEntry.POST_PLACE_CYCLE)
                {
                    int ticks = Math.max(5, entry.postPlaceCycleTicks);
                    int slot = host.giveQuickInputItemToHotbar(PlaceModule.INPUT_MODE_NUMBER, String.valueOf(ticks), false);
                    if (slot >= 0 && slot < 9 && mc.player.inventory.currentItem != slot)
                    {
                        mc.player.inventory.currentItem = slot;
                        if (mc.playerController != null)
                        {
                            mc.playerController.updateController();
                        }
                        if (mc.player != null && mc.player.connection != null)
                        {
                            mc.player.connection.sendPacket(new CPacketHeldItemChange(slot));
                        }
                    }
                    try
                    {
                        Vec3d hit = new Vec3d(0.5, 0.5, 0.5);
                        runPlaceClick(mc, clickPos, hit);
                        mc.player.swingArm(EnumHand.MAIN_HAND);
                    }
                    catch (Exception ignore) { }

                    entry.postPlaceStage = 2;
                    entry.postPlaceNextMs = nowMs + host.placeDelayMs(550L);
                    return;
                }

                // Done: advance to next entry.
                entry.awaitingMenu = false;
                entry.awaitingParamsChest = false;
                entry.awaitingArgs = false;
                state.current = null;
                return;
            }

            if (entry.awaitingParamsChest && !entry.needOpenParamsChest && mc.currentScreen == null)
            {
                entry.needOpenParamsChest = true;
                entry.paramsStartMs = nowMs;
                entry.nextParamsActionMs = nowMs + host.placeDelayMs(350L);
                entry.paramsOpenAttempts = 0;
            }
            if (entry.awaitingMenu && entry.needOpenMenu)
            {
                if (nowMs < entry.nextMenuActionMs)
                {
                    return;
                }
                BlockPos signPos = host.findSignAtZMinus1(mc.world, entry.pos);
                BlockPos clickPos = signPos != null ? signPos : entry.pos;
                try
                {
                    if (clickPos != null)
                    {
                        Vec3d hitA = new Vec3d(0.5, 0.5, 0.5);
                        runPlaceClick(mc, clickPos, hitA);
                        mc.player.swingArm(EnumHand.MAIN_HAND);
                    }
                }
                catch (Exception ignore) { }

                entry.needOpenMenu = false;
                entry.menuStartMs = nowMs;
                entry.nextMenuActionMs = nowMs + host.placeDelayMs(350L);
                entry.menuClicksSinceOpen = 0;
                entry.triedSlots.clear();
                entry.triedWindowId = -1;
                entry.menuNonEmptySinceMs = 0L;
                entry.menuNonEmptyWindowId = -1;
                return;
            }

            if (entry.awaitingParamsChest && entry.needOpenParamsChest)
            {
                if (nowMs < entry.nextParamsActionMs)
                {
                    return;
                }
                if (entry.paramsStartMs > 0L && nowMs - entry.paramsStartMs > 12000L)
                {
                    host.setActionBar(false, "&c/placeadvanced: params chest timeout", 2500L);
                    host.clearQueuedClicks();
                    host.clearTpPathQueue();
                    state.reset();
                    return;
                }

                BlockPos signPos = entry.pos == null ? null : host.findSignAtZMinus1(mc.world, entry.pos);
                BlockPos clickPos = signPos == null ? null : signPos.add(0, 1, 1);
                if (clickPos == null)
                {
                    clickPos = entry.pos == null ? null : entry.pos.up();
                }
                try
                {
                    if (clickPos != null)
                    {
                        Vec3d hitA = new Vec3d(0.5, 0.5, 0.5);
                        runPlaceClick(mc, clickPos, hitA);
                        mc.player.swingArm(EnumHand.MAIN_HAND);
                    }
                }
                catch (Exception ignore) { }

                entry.paramsOpenAttempts++;
                entry.nextParamsActionMs = nowMs + host.placeDelayMs(350L);
            }
            return;
        }

        if (entry.moveOnly)
        {
            double dx = entry.pos.getX() + 0.5;
            double dy = entry.pos.getY();
            double dz = entry.pos.getZ() - 2.0 + 0.5;

            if (mc.player.getDistanceSq(dx, dy, dz) <= 6.0)
            {
                state.current = null;
                return;
            }
            if (!host.tpPathQueueIsEmpty())
            {
                return;
            }
            host.buildTpPathQueue(mc.world, mc.player.posX, mc.player.posY, mc.player.posZ, dx, dy, dz);
            return;
        }

        double dx = entry.pos.getX() + 0.5;
        double dy = entry.pos.getY();
        double dz = entry.pos.getZ() - 2.0 + 0.5;

        if (mc.player.getDistanceSq(dx, dy, dz) <= 6.0)
        {
            if (entry.firstPlaceAttemptMs == 0L)
            {
                entry.firstPlaceAttemptMs = nowMs;
            }
            if (entry.nextPlaceAttemptMs > 0L && nowMs < entry.nextPlaceAttemptMs)
            {
                return;
            }
            if (entry.placeAttempts >= host.placeMaxPlaceAttempts()
                && entry.firstPlaceAttemptMs > 0L
                && nowMs - entry.firstPlaceAttemptMs > 15000L)
            {
                host.setActionBar(false, "&c/place: block placement retries exceeded", 2500L);
                host.clearQueuedClicks();
                host.clearTpPathQueue();
                state.reset();
                return;
            }
            try
            {
                // AGENT_TAG: place_autorestore_block
                // Server GUIs or packet drops may wipe/replace the held action block while printing.
                // Before each place click, strictly ensure the required block is in hand; if not, re-give it.
                if (entry.block != null)
                {
                    int slot = ensureBlockInHand(mc, entry.block, host, nowMs);
                    if (slot < 0)
                    {
                        host.setActionBar(false, "&c/place: missing block in hotbar", 2000L);
                        return;
                    }
                    if (mc.player.inventory.currentItem != slot)
                    {
                        mc.player.inventory.currentItem = slot;
                        if (mc.playerController != null)
                        {
                            mc.playerController.updateController();
                        }
                        if (mc.player != null && mc.player.connection != null)
                        {
                            mc.player.connection.sendPacket(new CPacketHeldItemChange(slot));
                        }
                    }
                }

                BlockPos placePos = entry.pos == null ? null : entry.pos.down();
                if (placePos != null)
                {
                    Vec3d hitA = new Vec3d(0.5, 0.5, 0.5);
                    runPlaceClick(mc, placePos, hitA);
                    mc.player.swingArm(EnumHand.MAIN_HAND);
                }
            }
            catch (Exception ignore) { }
            entry.placeAttempts++;
            entry.nextPlaceAttemptMs = nowMs + host.placeBlockRetryDelayMs();
            return;
        }
 
        // Important: do NOT rebuild the TP path every client tick.
        // If we rebuild here, tpPathNextMs keeps being pushed forward and the queue never executes.
        if (!host.tpPathQueueIsEmpty())
        {
            return;
        }
        host.buildTpPathQueue(mc.world, mc.player.posX, mc.player.posY, mc.player.posZ, dx, dy, dz);
    }

    private static void startAfterPlaced(PlaceEntry entry, long nowMs, PlaceModuleHost host)
    {
        if (entry == null)
        {
            return;
        }
        try
        {
            if (entry.postPlaceKind != PlaceEntry.POST_PLACE_NONE)
            {
                entry.awaitingMenu = false;
                entry.needOpenMenu = false;
                entry.postPlaceStage = 0;
                entry.postPlaceNextMs = nowMs + host.placeDelayMs(450L);
            }
            else
            {
                entry.awaitingMenu = true;
                entry.menuStartMs = nowMs;
                entry.needOpenMenu = true;
                entry.menuOpenAttempts = 0;
                entry.nextMenuActionMs = nowMs + host.placeDelayMs(350L);
                entry.menuClicksSinceOpen = 0;
                entry.menuNonEmptySinceMs = 0L;
                entry.menuNonEmptyWindowId = -1;
                entry.triedSlots.clear();
                entry.triedWindowId = -1;
            }
        }
        catch (Exception ignore) { }
    }

    private static int findHotbarSlot(Minecraft mc, net.minecraft.block.Block block)
    {
        if (mc == null || mc.player == null || block == null)
        {
            return -1;
        }
        net.minecraft.item.Item target = net.minecraft.item.Item.getItemFromBlock(block);
        if (target == null)
        {
            return -1;
        }
        for (int h = 0; h < 9; h++)
        {
            net.minecraft.item.ItemStack stack = mc.player.inventory.getStackInSlot(h);
            if (!stack.isEmpty() && stack.getItem() == target)
            {
                return h;
            }
        }
        return -1;
    }

    private static int giveBlockToHotbar(Minecraft mc, net.minecraft.block.Block block)
    {
        if (mc == null || mc.player == null || block == null)
        {
            return -1;
        }
        if (!mc.player.capabilities.isCreativeMode)
        {
            return -1;
        }
        int slot = -1;
        for (int i = 0; i < 9; i++)
        {
            ItemStack stack = mc.player.inventory.getStackInSlot(i);
            if (stack.isEmpty())
            {
                slot = i;
                break;
            }
        }
        if (slot == -1)
        {
            slot = mc.player.inventory.currentItem;
        }
        ItemStack stack = new ItemStack(block);
        mc.player.inventory.setInventorySlotContents(slot, stack);
        if (mc.player.connection != null)
        {
            mc.player.connection.sendPacket(new CPacketCreativeInventoryAction(36 + slot, stack));
        }
        return slot;
    }

    private static int ensureBlockInHand(Minecraft mc, net.minecraft.block.Block block, PlaceModuleHost host, long nowMs)
    {
        if (mc == null || mc.player == null || block == null)
        {
            return -1;
        }
        net.minecraft.item.Item target = net.minecraft.item.Item.getItemFromBlock(block);
        if (target == null)
        {
            return -1;
        }

        int slot = findHotbarSlot(mc, block);
        if (slot < 0)
        {
            slot = giveBlockToHotbar(mc, block);
            if (slot >= 0 && host != null)
            {
                host.setActionBar(true, "&e/place: restored required block to hotbar", 900L);
            }
        }
        if (slot < 0)
        {
            return -1;
        }

        ItemStack inSlot = mc.player.inventory.getStackInSlot(slot);
        boolean ok = !inSlot.isEmpty() && inSlot.getItem() == target;
        if (!ok)
        {
            // Force overwrite the target slot in creative mode when server/UI replaced it unexpectedly.
            if (!mc.player.capabilities.isCreativeMode)
            {
                return -1;
            }
            ItemStack stack = new ItemStack(block);
            mc.player.inventory.setInventorySlotContents(slot, stack);
            if (mc.player.connection != null)
            {
                mc.player.connection.sendPacket(new CPacketCreativeInventoryAction(36 + slot, stack));
            }
            if (host != null)
            {
                host.setActionBar(true, "&e/place: re-issued action block after inventory mismatch", 900L);
            }
        }

        // Short settle window helps avoid instant mis-click right after a forced re-give.
        if (host != null && nowMs > 0L)
        {
            // no-op marker; caller controls retry timing via nextPlaceAttemptMs
        }
        return slot;
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

    private static void runPlaceClick(Minecraft mc, BlockPos target, Vec3d hit)
    {
        if (mc == null || mc.player == null || mc.world == null || mc.playerController == null || target == null || hit == null)
        {
            return;
        }
        sendLookPacket(mc, target);
        if (mc.player.connection != null)
        {
            mc.player.connection.sendPacket(new net.minecraft.network.play.client.CPacketPlayerTryUseItemOnBlock(
                target, EnumFacing.UP, EnumHand.MAIN_HAND, (float) hit.x, (float) hit.y, (float) hit.z
            ));
        }
        mc.playerController.processRightClickBlock(
            mc.player, mc.world, target, EnumFacing.UP, hit, EnumHand.MAIN_HAND
        );
    }
}
