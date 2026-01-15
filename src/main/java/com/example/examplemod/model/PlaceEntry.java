package com.example.examplemod.model;

import java.util.HashSet;
import java.util.Set;
import java.util.List;

import net.minecraft.block.Block;
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
    }
}
