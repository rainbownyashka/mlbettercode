package com.rainbow_universe.bettercode.core.place;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class PlaceRuntimeEntry {
    public static final int POST_PLACE_NONE = 0;
    public static final int POST_PLACE_SIGN_NAME = 1;
    public static final int POST_PLACE_CYCLE = 2;

    private final boolean pause;
    private final boolean skip;
    private final String blockId;
    private final String name;
    private final String argsRaw;
    private final List<PlaceArgSpec> args;

    // Runtime mutable state (legacy PlaceEntry parity baseline)
    private boolean placedBlock;
    private boolean awaitingMenu;
    private long menuStartMs;
    private long lastMenuClickMs;
    private int lastMenuWindowId;
    private boolean needOpenMenu;
    private int menuOpenAttempts;
    private int randomClicks;
    private long nextMenuActionMs;
    private int triedWindowId;
    private int menuClicksSinceOpen;
    private long menuNonEmptySinceMs;
    private int menuNonEmptyWindowId;

    private boolean awaitingArgs;
    private int advancedArgIndex;
    private long argsStartMs;
    private long lastArgsActionMs;
    private int argsMisses;
    private int pendingArgClickSlot;
    private int pendingArgClicks;
    private long pendingArgNextMs;
    private int argsGuiPage;
    private boolean argsPageTurnPending;
    private long argsPageTurnStartMs;
    private long argsPageTurnNextMs;
    private int argsPageRetryCount;
    private String argsPageLastHash;

    private boolean awaitingParamsChest;
    private boolean needOpenParamsChest;
    private int paramsOpenAttempts;
    private long paramsStartMs;
    private long nextParamsActionMs;

    private int postPlaceKind;
    private String postPlaceName;
    private int postPlaceCycleTicks;
    private int postPlaceStage;
    private long postPlaceNextMs;

    private boolean moveOnly;
    private int placeAttempts;
    private long nextPlaceAttemptMs;
    private long firstPlaceAttemptMs;
    private long placedConfirmedMs;
    private int placedLostCount;

    public PlaceRuntimeEntry(boolean pause, boolean skip, String blockId, String name, String argsRaw, List<PlaceArgSpec> args) {
        this.pause = pause;
        this.skip = skip;
        this.blockId = blockId == null ? "" : blockId;
        this.name = name == null ? "" : name;
        this.argsRaw = argsRaw == null ? "" : argsRaw;
        this.args = args == null ? Collections.<PlaceArgSpec>emptyList() : new ArrayList<PlaceArgSpec>(args);
        this.placedBlock = false;
        this.awaitingMenu = false;
        this.menuStartMs = 0L;
        this.lastMenuClickMs = 0L;
        this.lastMenuWindowId = -1;
        this.needOpenMenu = false;
        this.menuOpenAttempts = 0;
        this.randomClicks = 0;
        this.nextMenuActionMs = 0L;
        this.triedWindowId = -1;
        this.menuClicksSinceOpen = 0;
        this.menuNonEmptySinceMs = 0L;
        this.menuNonEmptyWindowId = -1;
        this.awaitingArgs = false;
        this.advancedArgIndex = 0;
        this.argsStartMs = 0L;
        this.lastArgsActionMs = 0L;
        this.argsMisses = 0;
        this.pendingArgClickSlot = -1;
        this.pendingArgClicks = 0;
        this.pendingArgNextMs = 0L;
        this.argsGuiPage = 0;
        this.argsPageTurnPending = false;
        this.argsPageTurnStartMs = 0L;
        this.argsPageTurnNextMs = 0L;
        this.argsPageRetryCount = 0;
        this.argsPageLastHash = "";
        this.awaitingParamsChest = false;
        this.needOpenParamsChest = false;
        this.paramsOpenAttempts = 0;
        this.paramsStartMs = 0L;
        this.nextParamsActionMs = 0L;
        this.postPlaceKind = POST_PLACE_NONE;
        this.postPlaceName = "";
        this.postPlaceCycleTicks = -1;
        this.postPlaceStage = 0;
        this.postPlaceNextMs = 0L;
        this.moveOnly = skip;
        this.placeAttempts = 0;
        this.nextPlaceAttemptMs = 0L;
        this.firstPlaceAttemptMs = 0L;
        this.placedConfirmedMs = 0L;
        this.placedLostCount = 0;
    }

    public static PlaceRuntimeEntry fromSpec(PlaceEntrySpec spec) {
        if (spec == null || spec.isPause()) {
            return new PlaceRuntimeEntry(true, false, "minecraft:air", "", "", Collections.<PlaceArgSpec>emptyList());
        }
        return new PlaceRuntimeEntry(false, spec.isSkip(), spec.blockId(), spec.name(), spec.argsRaw(), spec.args());
    }

    public boolean isPause() {
        return pause;
    }

    public boolean isSkip() {
        return skip;
    }

    public String blockId() {
        return blockId;
    }

    public String name() {
        return name;
    }

    public String argsRaw() {
        return argsRaw;
    }

    public List<PlaceArgSpec> args() {
        return Collections.unmodifiableList(args);
    }

    public boolean placedBlock() { return placedBlock; }
    public void setPlacedBlock(boolean placedBlock) { this.placedBlock = placedBlock; }
    public boolean awaitingMenu() { return awaitingMenu; }
    public void setAwaitingMenu(boolean awaitingMenu) { this.awaitingMenu = awaitingMenu; }
    public long menuStartMs() { return menuStartMs; }
    public void setMenuStartMs(long menuStartMs) { this.menuStartMs = menuStartMs; }
    public long lastMenuClickMs() { return lastMenuClickMs; }
    public void setLastMenuClickMs(long lastMenuClickMs) { this.lastMenuClickMs = lastMenuClickMs; }
    public int lastMenuWindowId() { return lastMenuWindowId; }
    public void setLastMenuWindowId(int lastMenuWindowId) { this.lastMenuWindowId = lastMenuWindowId; }
    public boolean needOpenMenu() { return needOpenMenu; }
    public void setNeedOpenMenu(boolean needOpenMenu) { this.needOpenMenu = needOpenMenu; }
    public int menuOpenAttempts() { return menuOpenAttempts; }
    public void setMenuOpenAttempts(int menuOpenAttempts) { this.menuOpenAttempts = menuOpenAttempts; }
    public int randomClicks() { return randomClicks; }
    public void setRandomClicks(int randomClicks) { this.randomClicks = randomClicks; }
    public long nextMenuActionMs() { return nextMenuActionMs; }
    public void setNextMenuActionMs(long nextMenuActionMs) { this.nextMenuActionMs = nextMenuActionMs; }
    public int triedWindowId() { return triedWindowId; }
    public void setTriedWindowId(int triedWindowId) { this.triedWindowId = triedWindowId; }
    public int menuClicksSinceOpen() { return menuClicksSinceOpen; }
    public void setMenuClicksSinceOpen(int menuClicksSinceOpen) { this.menuClicksSinceOpen = menuClicksSinceOpen; }
    public long menuNonEmptySinceMs() { return menuNonEmptySinceMs; }
    public void setMenuNonEmptySinceMs(long menuNonEmptySinceMs) { this.menuNonEmptySinceMs = menuNonEmptySinceMs; }
    public int menuNonEmptyWindowId() { return menuNonEmptyWindowId; }
    public void setMenuNonEmptyWindowId(int menuNonEmptyWindowId) { this.menuNonEmptyWindowId = menuNonEmptyWindowId; }
    public boolean awaitingArgs() { return awaitingArgs; }
    public void setAwaitingArgs(boolean awaitingArgs) { this.awaitingArgs = awaitingArgs; }
    public int advancedArgIndex() { return advancedArgIndex; }
    public void setAdvancedArgIndex(int advancedArgIndex) { this.advancedArgIndex = advancedArgIndex; }
    public long argsStartMs() { return argsStartMs; }
    public void setArgsStartMs(long argsStartMs) { this.argsStartMs = argsStartMs; }
    public long lastArgsActionMs() { return lastArgsActionMs; }
    public void setLastArgsActionMs(long lastArgsActionMs) { this.lastArgsActionMs = lastArgsActionMs; }
    public int argsMisses() { return argsMisses; }
    public void setArgsMisses(int argsMisses) { this.argsMisses = argsMisses; }
    public int pendingArgClickSlot() { return pendingArgClickSlot; }
    public void setPendingArgClickSlot(int pendingArgClickSlot) { this.pendingArgClickSlot = pendingArgClickSlot; }
    public int pendingArgClicks() { return pendingArgClicks; }
    public void setPendingArgClicks(int pendingArgClicks) { this.pendingArgClicks = pendingArgClicks; }
    public long pendingArgNextMs() { return pendingArgNextMs; }
    public void setPendingArgNextMs(long pendingArgNextMs) { this.pendingArgNextMs = pendingArgNextMs; }
    public int argsGuiPage() { return argsGuiPage; }
    public void setArgsGuiPage(int argsGuiPage) { this.argsGuiPage = argsGuiPage; }
    public boolean argsPageTurnPending() { return argsPageTurnPending; }
    public void setArgsPageTurnPending(boolean argsPageTurnPending) { this.argsPageTurnPending = argsPageTurnPending; }
    public long argsPageTurnStartMs() { return argsPageTurnStartMs; }
    public void setArgsPageTurnStartMs(long argsPageTurnStartMs) { this.argsPageTurnStartMs = argsPageTurnStartMs; }
    public long argsPageTurnNextMs() { return argsPageTurnNextMs; }
    public void setArgsPageTurnNextMs(long argsPageTurnNextMs) { this.argsPageTurnNextMs = argsPageTurnNextMs; }
    public int argsPageRetryCount() { return argsPageRetryCount; }
    public void setArgsPageRetryCount(int argsPageRetryCount) { this.argsPageRetryCount = argsPageRetryCount; }
    public String argsPageLastHash() { return argsPageLastHash; }
    public void setArgsPageLastHash(String argsPageLastHash) { this.argsPageLastHash = argsPageLastHash == null ? "" : argsPageLastHash; }
    public boolean awaitingParamsChest() { return awaitingParamsChest; }
    public void setAwaitingParamsChest(boolean awaitingParamsChest) { this.awaitingParamsChest = awaitingParamsChest; }
    public boolean needOpenParamsChest() { return needOpenParamsChest; }
    public void setNeedOpenParamsChest(boolean needOpenParamsChest) { this.needOpenParamsChest = needOpenParamsChest; }
    public int paramsOpenAttempts() { return paramsOpenAttempts; }
    public void setParamsOpenAttempts(int paramsOpenAttempts) { this.paramsOpenAttempts = paramsOpenAttempts; }
    public long paramsStartMs() { return paramsStartMs; }
    public void setParamsStartMs(long paramsStartMs) { this.paramsStartMs = paramsStartMs; }
    public long nextParamsActionMs() { return nextParamsActionMs; }
    public void setNextParamsActionMs(long nextParamsActionMs) { this.nextParamsActionMs = nextParamsActionMs; }
    public int postPlaceKind() { return postPlaceKind; }
    public void setPostPlaceKind(int postPlaceKind) { this.postPlaceKind = postPlaceKind; }
    public String postPlaceName() { return postPlaceName; }
    public void setPostPlaceName(String postPlaceName) { this.postPlaceName = postPlaceName == null ? "" : postPlaceName; }
    public int postPlaceCycleTicks() { return postPlaceCycleTicks; }
    public void setPostPlaceCycleTicks(int postPlaceCycleTicks) { this.postPlaceCycleTicks = postPlaceCycleTicks; }
    public int postPlaceStage() { return postPlaceStage; }
    public void setPostPlaceStage(int postPlaceStage) { this.postPlaceStage = postPlaceStage; }
    public long postPlaceNextMs() { return postPlaceNextMs; }
    public void setPostPlaceNextMs(long postPlaceNextMs) { this.postPlaceNextMs = postPlaceNextMs; }
    public boolean moveOnly() { return moveOnly; }
    public void setMoveOnly(boolean moveOnly) { this.moveOnly = moveOnly; }
    public int placeAttempts() { return placeAttempts; }
    public void setPlaceAttempts(int placeAttempts) { this.placeAttempts = placeAttempts; }
    public long nextPlaceAttemptMs() { return nextPlaceAttemptMs; }
    public void setNextPlaceAttemptMs(long nextPlaceAttemptMs) { this.nextPlaceAttemptMs = nextPlaceAttemptMs; }
    public long firstPlaceAttemptMs() { return firstPlaceAttemptMs; }
    public void setFirstPlaceAttemptMs(long firstPlaceAttemptMs) { this.firstPlaceAttemptMs = firstPlaceAttemptMs; }
    public long placedConfirmedMs() { return placedConfirmedMs; }
    public void setPlacedConfirmedMs(long placedConfirmedMs) { this.placedConfirmedMs = placedConfirmedMs; }
    public int placedLostCount() { return placedLostCount; }
    public void setPlacedLostCount(int placedLostCount) { this.placedLostCount = placedLostCount; }
}
