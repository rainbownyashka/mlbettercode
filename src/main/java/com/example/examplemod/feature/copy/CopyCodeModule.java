package com.example.examplemod.feature.copy;

import com.example.examplemod.feature.codemap.BlueGlassCodeMap;
import net.minecraft.client.Minecraft;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

public final class CopyCodeModule
{
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
        void buildTpPathQueue(World world, double fromX, double fromY, double fromZ, double toX, double toY, double toZ);
        void clearTpPathQueue();
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
    private List<BlockPos> sourceBlocks = new ArrayList<>();
    private int blockIndex = 0;

    private Stage stage = Stage.IDLE;
    private long nextMs = 0L;
    private int worldRefSwitch = 0;
    private long switchSentMs = 0L;
    private Vec3d switchFromPos = null;
    private BlockPos currentSource;

    private boolean prevPauseOnLostFocus = true;
    private boolean shiftHeld = false;

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
        sourceBlocks.clear();
        floorOffsets.clear();
        floorIndex = 0;
        blockIndex = 0;
        currentSource = null;
        worldRefSwitch = 0;
        switchSentMs = 0L;
        switchFromPos = null;
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
        sourceBlocks.clear();
        currentSource = null;
        nextMs = System.currentTimeMillis();
        worldRefSwitch = 0;
        switchSentMs = 0L;
        switchFromPos = null;

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
        if (!host.isEditorModeActive() || !host.isDevCreativeScoreboard(mc))
        {
            cancel("not in dev/creative");
            return;
        }

        // Always keep flying on while active.
        ensureFlying(mc);

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
            if (floorIndex >= floorOffsets.size())
            {
                cancel("done");
                return;
            }
            scanFloor(mc, floorOffsets.get(floorIndex));
            if (sourceBlocks.isEmpty())
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

        if (blockIndex >= sourceBlocks.size() && stage != Stage.SCAN_FLOOR)
        {
            floorIndex++;
            sourceBlocks.clear();
            stage = Stage.SCAN_FLOOR;
            nextMs = nowMs + 250L;
            return;
        }

        switch (stage)
        {
            case TP_TO_SOURCE:
                currentSource = sourceBlocks.get(blockIndex);
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
                if (currentSource == null)
                {
                    stage = Stage.NEXT;
                    return;
                }
                doBreak(mc, currentSource);
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
                // wait a bit so UI/state settles
                stage = Stage.TP_TO_DEST;
                nextMs = nowMs + 250L;
                return;

            case TP_TO_DEST:
                if (!host.tpPathQueueIsEmpty())
                {
                    nextMs = nowMs + 100L;
                    return;
                }
                if (currentSource == null)
                {
                    stage = Stage.NEXT;
                    return;
                }
                BlockPos dest = currentSource.add(0, copyYOffset, 0);
                tpNear(mc, dest);
                stage = Stage.PLACE_BEACON;
                nextMs = nowMs + 650L;
                return;

            case PLACE_BEACON:
                if (currentSource == null)
                {
                    stage = Stage.NEXT;
                    return;
                }
                BlockPos placeAt = currentSource.add(0, copyYOffset, 0);
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
                stage = Stage.NEXT;
                nextMs = nowMs + 250L;
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
        sourceBlocks.clear();
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
        LinkedHashSet<BlockPos> out = new LinkedHashSet<>();
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
                out.add(above);
            }
        }
        sourceBlocks.addAll(out);
        if (host.isDebugUi())
        {
            host.debugChat("&e/copycode scan y=" + floorSeed.getY() + " blocks=" + sourceBlocks.size());
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
            return;
        }
        worldRefSwitch = System.identityHashCode(mc.world);
        switchSentMs = System.currentTimeMillis();
        switchFromPos = new Vec3d(mc.player.posX, mc.player.posY, mc.player.posZ);
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
