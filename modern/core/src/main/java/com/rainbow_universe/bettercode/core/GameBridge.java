package com.rainbow_universe.bettercode.core;

import com.rainbow_universe.bettercode.core.bridge.AckState;
import com.rainbow_universe.bettercode.core.bridge.BlockPosView;
import com.rainbow_universe.bettercode.core.bridge.ClickResult;
import com.rainbow_universe.bettercode.core.bridge.ContainerView;
import com.rainbow_universe.bettercode.core.bridge.CursorState;
import com.rainbow_universe.bettercode.core.bridge.SelectedRow;
import com.rainbow_universe.bettercode.core.place.PlaceRuntimeEntry;

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

public interface GameBridge {
    default void onExecutionStart(int totalSteps) {
    }

    default void onExecutionStop() {
    }

    default void onRuntimeStepCompleted(PlaceRuntimeEntry entry) {
    }

    String currentDimension();
    Path runDirectory();
    List<String> scoreboardLines();
    boolean supportsPlacePlanExecution();
    PlaceExecResult executePlacePlan(List<PlaceOp> ops, boolean checkOnly);
    PlaceExecResult executePlaceStep(PlaceRuntimeEntry entry, boolean checkOnly);
    boolean executeClientCommand(String command);
    void sendChat(String message);
    void sendActionBar(String message);

    boolean openContainerIfNeeded();
    boolean useBlockAt(int x, int y, int z, String purpose);
    boolean useBlockAtOffset(int dx, int dy, int dz, String purpose);
    boolean isAirAt(int x, int y, int z);
    boolean isBlockAt(int x, int y, int z, String blockId);
    boolean isBlockAtOffset(int dx, int dy, int dz, String blockId);
    ClickResult clickSlot(int windowId, int slot, int button, String clickType);
    CursorState getCursorStack();
    ContainerView getContainerSnapshot();
    AckState waitForWindowChange(int expectedWindowId, long timeoutMs);
    void closeScreen();
    boolean selectHotbarSlot(int slot);
    boolean injectCreativeSlot(int slot, String itemId, String nbt, String displayName);
    ClickResult interactBlock(int x, int y, int z);
    ClickResult sendUseItemOnBlock(int x, int y, int z);
    ClickResult clickBlockLegacy(int x, int y, int z, String purpose, boolean spoofLook);
    long nowMs();

    default List<SelectedRow> selectedRows() {
        return Collections.emptyList();
    }

    boolean canUseEditorContext();
    boolean isScreenOpen();
    boolean closeScreenIfOpen();
    boolean enqueueTpPath(int x, int y, int z);
    boolean isTpPathBusy();
    boolean isHoldingBlockerItem();
    boolean isSignAt(int x, int y, int z);
    String[] readSignLinesAt(int x, int y, int z);
    default String getBlockIdAt(int x, int y, int z) {
        return "";
    }
    default String getBlockFacingAt(int x, int y, int z) {
        return "";
    }
    default String getChestJsonAtEntry(int entryX, int entryY, int entryZ, boolean preferCache) {
        return "";
    }
    String dimensionId();
    boolean supportsLegacyLookSpoof();
    BlockPosView getRuntimeEntryAnchor();
    boolean openMenuAtEntryAnchor();
    boolean canTeleportWarmup();
    double distanceSqTo(int x, int y, int z);
}
