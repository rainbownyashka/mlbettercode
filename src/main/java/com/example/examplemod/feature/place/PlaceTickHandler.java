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
        if (!state.active)
        {
            return;
        }
        if (mc.world == null || mc.player == null)
        {
            state.reset();
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

        if (entry.placedBlock
            && entry.awaitingMenu
            && !entry.needOpenMenu
            && entry.lastMenuWindowId != -1
            && nowMs - entry.lastMenuClickMs > 300L)
        {
            entry.needOpenMenu = true;
            entry.menuStartMs = nowMs;
            entry.nextMenuActionMs = nowMs + 250L;
            entry.menuClicksSinceOpen = 0;
            entry.triedWindowId = -1;
            entry.menuNonEmptySinceMs = 0L;
            entry.menuNonEmptyWindowId = -1;
        }

        if (entry.placedBlock)
        {
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
                entry.nextMenuActionMs = nowMs + 350L;
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
                entry.nextParamsActionMs = nowMs + 350L;
            }
            return;
        }

        double dx = entry.pos.getX() + 0.5;
        double dy = entry.pos.getY();
        double dz = entry.pos.getZ() - 2.0 + 0.5;

        if (mc.player.getDistanceSq(dx, dy, dz) <= 6.0)
        {
            try
            {
                // Try to hold the correct block if it already exists in hotbar.
                if (entry.block != null)
                {
                    int slot = findHotbarSlot(mc, entry.block);
                    if (slot < 0)
                    {
                        slot = giveBlockToHotbar(mc, entry.block);
                    }
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

                entry.placedBlock = true;
                entry.awaitingMenu = true;
                entry.menuStartMs = System.currentTimeMillis();
                entry.needOpenMenu = true;
                entry.menuOpenAttempts = 0;
                entry.nextMenuActionMs = System.currentTimeMillis() + 350L;
                entry.menuClicksSinceOpen = 0;
                entry.menuNonEmptySinceMs = 0L;
                entry.menuNonEmptyWindowId = -1;
                entry.triedSlots.clear();
                entry.triedWindowId = -1;
            }
            catch (Exception ignore) { }
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
