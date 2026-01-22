package com.example.examplemod.model;

import java.util.HashSet;
import java.util.Set;
import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;

public class PlaceEntry
{
    public static final int POST_PLACE_NONE = 0;
    public static final int POST_PLACE_SIGN_NAME = 1; // right-click sign with TEXT item
    public static final int POST_PLACE_CYCLE = 2; // sign name + tick period (NUMBER)

    public BlockPos pos;
    public Block block;
    public String searchKey;
    public int desiredSlotIndex;
    public boolean placedBlock;
    public boolean awaitingMenu;
    public long menuStartMs;
    public long lastMenuClickMs;
    public int lastMenuWindowId;

    // Random exploration fallback for /place when no cached path is found.
    public boolean needOpenMenu;
    public int menuOpenAttempts;
    public int randomClicks;
    public long nextMenuActionMs;
    public final Set<Integer> triedSlots = new HashSet<>();
    public final Set<String> triedItemKeys = new HashSet<>();
    public int triedWindowId = -1;
    public int menuClicksSinceOpen = 0;
    public long menuNonEmptySinceMs;
    public int menuNonEmptyWindowId;

    // /placeadvanced: optional parameter assignment plan (parsed later).
    public String advancedArgsRaw;
    public List<PlaceArg> advancedArgs;
    public boolean awaitingArgs;
    public int advancedArgIndex;
    public long argsStartMs;
    public long lastArgsActionMs;
    public int argsMisses;
    public final Set<Integer> usedArgSlots = new HashSet<>();
    public int pendingArgClickSlot;
    public int pendingArgClicks;
    public long pendingArgNextMs;

    // /placeadvanced: after selecting the menu item, some servers open a parameter GUI via a spawned chest.
    public boolean awaitingParamsChest;
    public boolean needOpenParamsChest;
    public int paramsOpenAttempts;
    public long paramsStartMs;
    public long nextParamsActionMs;

    // Post-place configuration (functions/cycles).
    public int postPlaceKind = POST_PLACE_NONE;
    public String postPlaceName;
    public int postPlaceCycleTicks = -1;
    public int postPlaceStage = 0;
    public long postPlaceNextMs = 0L;

    // Move-only step (no placement, only TP path advance).
    public boolean moveOnly = false;

    // Placement retry (server may cancel fast placements / anti-cheat).
    public int placeAttempts = 0;
    public long nextPlaceAttemptMs = 0L;
    public long firstPlaceAttemptMs = 0L;

    // Placement validation: server may "ghost place" then revert.
    public long placedConfirmedMs = 0L;
    public int placedLostCount = 0;

    // Temporary hotbar slot used for item(...) injection (so we can click it into GUI)
    public int tempHotbarSlot = -1; // 0..8
    public long tempHotbarClearMs = 0L;
    public ItemStack tempHotbarOriginal = ItemStack.EMPTY;

    // Item(...) click injection sequence (hotbar -> target -> hotbar)
    public int tempItemSeqStage = -1; // -1 = inactive, 0..2 steps, 3=done
    public int tempItemSeqSlot0 = -1;
    public int tempItemSeqSlot1 = -1;
    public int tempItemSeqSlot2 = -1;
    public int tempItemTargetSlot = -1;
    public int tempItemExtraClicks = 0;

    public PlaceEntry(BlockPos pos, Block block)
    {
        this.pos = pos;
        this.block = block;
        this.searchKey = null;
        this.desiredSlotIndex = -1;
        this.placedBlock = false;
        this.awaitingMenu = false;
        this.menuStartMs = 0L;
        this.lastMenuClickMs = 0L;
        this.lastMenuWindowId = -1;
        this.needOpenMenu = false;
        this.menuOpenAttempts = 0;
        this.randomClicks = 0;
        this.nextMenuActionMs = 0L;
        this.advancedArgsRaw = null;
        this.advancedArgs = null;
        this.awaitingArgs = false;
        this.advancedArgIndex = 0;
        this.argsStartMs = 0L;
        this.lastArgsActionMs = 0L;
        this.argsMisses = 0;
        this.pendingArgClickSlot = -1;
        this.pendingArgClicks = 0;
        this.pendingArgNextMs = 0L;
        this.awaitingParamsChest = false;
        this.needOpenParamsChest = false;
        this.paramsOpenAttempts = 0;
        this.paramsStartMs = 0L;
        this.nextParamsActionMs = 0L;
        this.triedWindowId = -1;
        this.menuClicksSinceOpen = 0;
        this.menuNonEmptySinceMs = 0L;
        this.menuNonEmptyWindowId = -1;

        this.postPlaceKind = POST_PLACE_NONE;
        this.postPlaceName = null;
        this.postPlaceCycleTicks = -1;
        this.postPlaceStage = 0;
        this.postPlaceNextMs = 0L;

        this.moveOnly = false;
        this.placedConfirmedMs = 0L;
        this.placedLostCount = 0;
        this.tempHotbarSlot = -1;
        this.tempHotbarClearMs = 0L;
        this.tempHotbarOriginal = ItemStack.EMPTY;
        this.tempItemSeqStage = -1;
        this.tempItemSeqSlot0 = -1;
        this.tempItemSeqSlot1 = -1;
        this.tempItemSeqSlot2 = -1;
        this.tempItemTargetSlot = -1;
        this.tempItemExtraClicks = 0;
    }

    public PlaceEntry(BlockPos pos, Block block, String searchKey)
    {
        this.pos = pos;
        this.block = block;
        this.searchKey = searchKey;
        this.desiredSlotIndex = -1;
        this.placedBlock = false;
        this.awaitingMenu = false;
        this.menuStartMs = 0L;
        this.lastMenuClickMs = 0L;
        this.lastMenuWindowId = -1;
        this.needOpenMenu = false;
        this.menuOpenAttempts = 0;
        this.randomClicks = 0;
        this.nextMenuActionMs = 0L;
        this.advancedArgsRaw = null;
        this.advancedArgs = null;
        this.awaitingArgs = false;
        this.advancedArgIndex = 0;
        this.argsStartMs = 0L;
        this.lastArgsActionMs = 0L;
        this.argsMisses = 0;
        this.pendingArgClickSlot = -1;
        this.pendingArgClicks = 0;
        this.pendingArgNextMs = 0L;
        this.awaitingParamsChest = false;
        this.needOpenParamsChest = false;
        this.paramsOpenAttempts = 0;
        this.paramsStartMs = 0L;
        this.nextParamsActionMs = 0L;
        this.triedWindowId = -1;
        this.menuClicksSinceOpen = 0;
        this.menuNonEmptySinceMs = 0L;
        this.menuNonEmptyWindowId = -1;

        this.postPlaceKind = POST_PLACE_NONE;
        this.postPlaceName = null;
        this.postPlaceCycleTicks = -1;
        this.postPlaceStage = 0;
        this.postPlaceNextMs = 0L;

        this.moveOnly = false;
        this.placedConfirmedMs = 0L;
        this.placedLostCount = 0;
        this.tempHotbarSlot = -1;
        this.tempHotbarClearMs = 0L;
        this.tempHotbarOriginal = ItemStack.EMPTY;
        this.tempItemSeqStage = -1;
        this.tempItemSeqSlot0 = -1;
        this.tempItemSeqSlot1 = -1;
        this.tempItemSeqSlot2 = -1;
        this.tempItemTargetSlot = -1;
        this.tempItemExtraClicks = 0;
    }
}
