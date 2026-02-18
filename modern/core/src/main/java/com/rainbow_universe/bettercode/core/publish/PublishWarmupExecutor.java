package com.rainbow_universe.bettercode.core.publish;

import com.rainbow_universe.bettercode.core.GameBridge;
import com.rainbow_universe.bettercode.core.bridge.ClickResult;
import com.rainbow_universe.bettercode.core.bridge.ContainerView;
import com.rainbow_universe.bettercode.core.bridge.SlotView;
import com.rainbow_universe.bettercode.core.bridge.SelectedRow;

public final class PublishWarmupExecutor {
    public static final class Result {
        public final boolean done;
        public final String errorCode;
        public final String errorMessage;

        private Result(boolean done, String errorCode, String errorMessage) {
            this.done = done;
            this.errorCode = errorCode == null ? "" : errorCode;
            this.errorMessage = errorMessage == null ? "" : errorMessage;
        }

        public static Result done() {
            return new Result(true, "", "");
        }

        public static Result fail(String code, String message) {
            return new Result(false, code, message);
        }
    }

    public interface Trace {
        void trace(String stage, String details);
    }

    private PublishWarmupExecutor() {
    }

    public static Result run(PublishSessionState state, GameBridge bridge, Trace trace) {
        if (state == null || bridge == null || trace == null) {
            return Result.fail("PUBLISH_WARMUP_TIMEOUT", "null publish warmup context");
        }
        state.warmupActive = true;
        while (true) {
            long now = bridge.nowMs();
            if (now >= state.timeoutAtMs) {
                trace.trace("warmup.stop", "reason=timeout");
                state.warmupActive = false;
                return Result.fail("PUBLISH_WARMUP_TIMEOUT", "warmup timeout");
            }
            Result blocked = checkBlocked(state, bridge, trace);
            if (blocked != null) {
                state.warmupActive = false;
                return blocked;
            }
            if (state.currentChest == null) {
                while (!state.warmupQueue.isEmpty() && state.currentChest == null) {
                    SelectedRow row = state.warmupQueue.pollFirst();
                    if (row == null) {
                        continue;
                    }
                    state.currentChest = row;
                    trace.trace("warmup.pick", "pos=" + row.x() + "," + row.y() + "," + row.z()
                        + " pass=" + state.warmupPass + " queueLeft=" + state.warmupQueue.size());
                }
                if (state.currentChest == null) {
                    if (state.warmupPass == 0 && !state.warmupRetryQueue.isEmpty()) {
                        state.warmupPass = 1;
                        state.warmupQueue.addAll(state.warmupRetryQueue);
                        state.warmupRetryQueue.clear();
                        trace.trace("warmup.retry_pass", "count=" + state.warmupQueue.size());
                        continue;
                    }
                    if (now < state.settleUntilMs) {
                        trace.trace("warmup.wait", "reason=settling now=" + now + " settleUntil=" + state.settleUntilMs);
                        continue;
                    }
                    state.warmupActive = false;
                    trace.trace("warmup.done", "rows=" + state.selectedRows.size() + " pass=" + state.warmupPass);
                    return Result.done();
                }
            }

            SelectedRow row = state.currentChest;
            int targetX = row.x();
            int targetY = row.y();
            int targetZ = row.z() - 2;
            boolean tpQueued = bridge.enqueueTpPath(targetX, targetY, targetZ);
            trace.trace("warmup.tp", "target=" + targetX + "," + targetY + "," + targetZ + " queued=" + tpQueued);
            if (!tpQueued) {
                state.blockedReason = "tp_path_busy";
                trace.trace("warmup.wait", "reason=tp_path_busy");
                state.warmupRetryQueue.addLast(row);
                state.currentChest = null;
                continue;
            }
            ContainerView beforeOpen = bridge.getContainerSnapshot();
            String beforeHash = hashNonPlayer(beforeOpen);
            boolean opened = bridge.useBlockAt(row.x(), row.y(), row.z(), "publish_warmup_open");
            if (!opened) {
                opened = bridge.useBlockAt(row.x(), row.y() + 1, row.z(), "publish_warmup_open_fallback");
            }
            boolean progressed = false;
            if (opened) {
                bridge.waitForWindowChange(beforeOpen == null ? -1 : beforeOpen.windowId(), 1_200L);
                ContainerView afterOpen = bridge.getContainerSnapshot();
                String afterHash = hashNonPlayer(afterOpen);
                progressed = isOpenProgress(beforeOpen, afterOpen, beforeHash, afterHash);
            }
            trace.trace("warmup.open", "pos=" + row.x() + "," + row.y() + "," + row.z()
                + " opened=" + opened + " progressed=" + progressed);
            if (!opened || !progressed) {
                if (state.warmupPass == 0) {
                    state.warmupRetryQueue.addLast(row);
                }
                state.currentChest = null;
                continue;
            }
            Result pageResult = processPagedChest(state, bridge, trace, row);
            if (!pageResult.done) {
                state.warmupActive = false;
                return pageResult;
            }
            state.settleUntilMs = bridge.nowMs() + PublishSessionState.WARMUP_SETTLE_MS;
            state.currentChest = null;
        }
    }

    private static Result checkBlocked(PublishSessionState state, GameBridge bridge, Trace trace) {
        if (!bridge.canUseEditorContext()) {
            state.blockedReason = "not_dev_creative";
            trace.trace("warmup.wait", "reason=not_dev_creative");
            return Result.fail("PUBLISH_CONTEXT_BLOCKED", "editor/dev context is not active");
        }
        String expectedDim = state.dimension == null ? "" : state.dimension.trim();
        String currentDim = bridge.currentDimension() == null ? "" : bridge.currentDimension().trim();
        if (!expectedDim.isEmpty() && !currentDim.isEmpty() && !expectedDim.equals(currentDim)) {
            state.blockedReason = "dimension_mismatch";
            trace.trace("warmup.wait", "reason=dimension_mismatch current=" + currentDim + " expected=" + expectedDim);
            return Result.fail("PUBLISH_CONTEXT_BLOCKED", "dimension mismatch");
        }
        if (bridge.isHoldingBlockerItem() || bridge.isScreenOpen()) {
            boolean closed = !bridge.isScreenOpen() || bridge.closeScreenIfOpen();
            state.blockedReason = "blocked heldIngot=" + bridge.isHoldingBlockerItem() + " screen=" + bridge.isScreenOpen();
            trace.trace("warmup.wait", "reason=blocked heldIngot=" + bridge.isHoldingBlockerItem()
                + " autoCache=false screen=" + bridge.isScreenOpen() + " closed=" + closed);
            if (bridge.isHoldingBlockerItem() || !closed) {
                return Result.fail("PUBLISH_CONTEXT_BLOCKED", "blocked by held item/screen");
            }
        }
        if (bridge.isTpPathBusy()) {
            state.blockedReason = "tp_path_busy";
            trace.trace("warmup.wait", "reason=tp_path_busy");
            return Result.fail("PUBLISH_CONTEXT_BLOCKED", "tp path is busy");
        }
        return null;
    }

    private static Result processPagedChest(PublishSessionState state, GameBridge bridge, Trace trace, SelectedRow row) {
        state.nextPageRetryCount = 0;
        int pageTurns = 0;
        while (pageTurns < 32) {
            ContainerView view = bridge.getContainerSnapshot();
            if (view == null || view.windowId() < 0) {
                return Result.done();
            }
            SlotView next = findNextPageArrow(view);
            if (next == null) {
                return Result.done();
            }
            String prevHash = hashNonPlayer(view);
            ClickResult click = bridge.clickSlot(view.windowId(), next.slotNumber(), 0, "PICKUP");
            if (click == null || !click.accepted()) {
                state.nextPageRetryCount++;
                trace.trace("warmup.wait", "reason=page_click_rejected retry=" + state.nextPageRetryCount);
            } else {
                bridge.waitForWindowChange(view.windowId(), 800L);
                ContainerView after = bridge.getContainerSnapshot();
                String afterHash = hashNonPlayer(after);
                if (prevHash.equals(afterHash)) {
                    state.nextPageRetryCount++;
                    trace.trace("warmup.wait", "reason=has_next_page_but_not_allowed retry=" + state.nextPageRetryCount);
                } else {
                    pageTurns++;
                    state.nextPageRetryCount = 0;
                    trace.trace("warmup.page_turn", "pos=" + row.x() + "," + row.y() + "," + row.z() + " page=" + pageTurns);
                }
            }
            if (state.nextPageRetryCount > PublishSessionState.MAX_NEXT_PAGE_RETRIES) {
                trace.trace("autocache.close", "reason=next_page_retry_exhausted pos="
                    + row.x() + "," + row.y() + "," + row.z() + " retries=" + state.nextPageRetryCount);
                return Result.fail("PUBLISH_NEXT_PAGE_RETRY_EXHAUSTED", "next page retry exhausted");
            }
        }
        trace.trace("autocache.close", "reason=next_page_retry_exhausted pos="
            + row.x() + "," + row.y() + "," + row.z() + " retries=loop_guard");
        return Result.fail("PUBLISH_NEXT_PAGE_RETRY_EXHAUSTED", "next page loop guard");
    }

    private static SlotView findNextPageArrow(ContainerView view) {
        if (view == null || view.slots() == null) {
            return null;
        }
        int lastIndex = Math.max(0, view.size() - 1);
        SlotView lastSlotArrow = null;
        for (SlotView s : view.slots()) {
            if (s == null || s.playerInventory() || s.empty()) {
                continue;
            }
            if (s.index() != lastIndex) {
                continue;
            }
            String item = s.itemId() == null ? "" : s.itemId().toLowerCase();
            if (item.endsWith(":arrow") || item.endsWith(":spectral_arrow")) {
                lastSlotArrow = s;
                break;
            }
        }
        if (lastSlotArrow != null) {
            return lastSlotArrow;
        }
        SlotView fallback = null;
        for (SlotView s : view.slots()) {
            if (s == null || s.playerInventory() || s.empty()) {
                continue;
            }
            String item = s.itemId() == null ? "" : s.itemId().toLowerCase();
            String name = s.displayName() == null ? "" : s.displayName().toLowerCase();
            boolean isArrow = item.endsWith(":arrow") || item.endsWith(":spectral_arrow");
            boolean nameSuggestsNext = name.contains("next")
                || name.contains("след")
                || name.contains("впер");
            if (isArrow && nameSuggestsNext) {
                return s;
            }
            if (isArrow && fallback == null) {
                fallback = s;
            }
        }
        return fallback;
    }

    private static boolean isOpenProgress(ContainerView before, ContainerView after, String beforeHash, String afterHash) {
        if (after != null && after.windowId() >= 0 && (before == null || after.windowId() != before.windowId())) {
            return true;
        }
        if (after != null && after.size() > 0 && (before == null || before.size() <= 0)) {
            return true;
        }
        if (beforeHash == null) {
            beforeHash = "x";
        }
        if (afterHash == null) {
            afterHash = "x";
        }
        return !beforeHash.equals(afterHash);
    }

    private static String hashNonPlayer(ContainerView view) {
        if (view == null || view.slots() == null) {
            return "x";
        }
        StringBuilder sb = new StringBuilder();
        for (SlotView s : view.slots()) {
            if (s == null || s.playerInventory()) {
                continue;
            }
            sb.append(s.slotNumber()).append(':')
                .append(s.empty()).append(':')
                .append(s.itemId()).append(':')
                .append(s.displayName()).append(';');
        }
        return Integer.toHexString(sb.toString().hashCode());
    }
}
