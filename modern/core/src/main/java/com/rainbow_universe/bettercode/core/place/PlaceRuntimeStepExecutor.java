package com.rainbow_universe.bettercode.core.place;

import com.rainbow_universe.bettercode.core.CoreLogger;
import com.rainbow_universe.bettercode.core.GameBridge;
import com.rainbow_universe.bettercode.core.PlaceExecResult;
import com.rainbow_universe.bettercode.core.bridge.AckState;
import com.rainbow_universe.bettercode.core.bridge.ClickResult;
import com.rainbow_universe.bettercode.core.bridge.ContainerView;
import com.rainbow_universe.bettercode.core.bridge.CursorState;
import com.rainbow_universe.bettercode.core.bridge.SlotView;
import com.rainbow_universe.bettercode.core.settings.SettingsProvider;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class PlaceRuntimeStepExecutor {
    private static final long MENU_TIMEOUT_MS = 6000L;
    private static final long MENU_REOPEN_AFTER_MS = 1600L;
    private static final long MENU_NON_EMPTY_STABLE_MS = 250L;
    private static final long PARAMS_TIMEOUT_MS = 6000L;
    private static final long PARAMS_REOPEN_AFTER_MS = 1200L;
    private static final long ARGS_TIMEOUT_MS = 12000L;
    private static final long ARGS_ACTION_MIN_GAP_MS = 180L;
    private static final long CURSOR_TIMEOUT_MS = 5000L;
    private static final int MAX_ARG_MISSES = 60;
    private static final int MAX_MENU_OPEN_ATTEMPTS = 8;
    private static final int MAX_MENU_REPLACE_CYCLES = 2;
    private static final int MAX_PLACED_LOST_COUNT = 6;
    private static final long BLOCK_RECHECK_MIN_ELAPSED_MS = 1200L;
    private static final int BLOCK_RECHECK_MISS_REQUIRED = 3;
    private static final long PAGE_TURN_TIMEOUT_MS = 1500L;
    private static final int PAGE_TURN_MAX_RETRIES = 5;

    private PlaceRuntimeStepExecutor() {
    }

    public static PlaceExecResult execute(
        PlaceRuntimeEntry entry,
        GameBridge bridge,
        SettingsProvider settings,
        CoreLogger logger,
        MenuRouteResolver menuRouteResolver
    ) {
        if (entry == null) {
            return fail(logger, "PARSE_SCHEMA_MISMATCH", "null runtime entry");
        }
        String rawName = entry.name() == null ? "" : entry.name().trim();
        String rawArgs = entry.argsRaw() == null ? "" : entry.argsRaw().trim();
        boolean hasMenuPayload = !rawName.isEmpty() || (!rawArgs.isEmpty() && !"no".equalsIgnoreCase(rawArgs));
        if (!hasMenuPayload) {
            return bridge.executePlaceStep(entry, false);
        }

        long now = bridge.nowMs();
        int delay = settings.getInt("printer.stepDelayMs", 80);
        if (delay < 0) {
            delay = 0;
        }

        if (!entry.placedBlock()) {
            PlaceRuntimeEntry blockOnly = new PlaceRuntimeEntry(
                false, false, entry.blockId(), "", "no", java.util.Collections.<PlaceArgSpec>emptyList()
            );
            PlaceExecResult placed = bridge.executePlaceStep(blockOnly, false);
            if (!placed.ok()) {
                return placed;
            }
            entry.setPlacedBlock(true);
            entry.setPlacedConfirmedMs(now);
            entry.setAwaitingMenu(true);
            entry.setNeedOpenMenu(true);
            entry.setMenuStartMs(now);
            entry.setMenuRetrySinceMs(now);
            entry.setLastOpenAttemptMs(0L);
            entry.setMenuOpenAttempts(0);
            entry.setForceRePlaceRequested(false);
            entry.setMenuReplaceCount(0);
            entry.setNextMenuActionMs(now + delay);
            logger.info("printer-debug", "runtime_state=PLACE_BLOCK confirmed=1");
            return PlaceExecResult.inProgress(0, "OPEN_MENU");
        }

        if (entry.placedBlock() && entry.placedConfirmedMs() > 0L) {
            boolean blockStillPlaced = bridge.isBlockAtOffset(0, 0, 0, entry.blockId());
            if (blockStillPlaced) {
                entry.setBlockRecheckMisses(0);
                entry.setBlockRecheckStartMs(0L);
            } else {
                ContainerView currentView = bridge.getContainerSnapshot();
                boolean menuWindowVisible = currentView != null && currentView.windowId() >= 0;
                boolean menuProgressed = menuWindowVisible
                    || entry.lastMenuWindowId() >= 0
                    || entry.awaitingParamsChest()
                    || entry.awaitingArgs()
                    || (entry.awaitingMenu() && entry.menuOpenAttempts() > 0 && entry.lastOpenAttemptMs() > 0L);
                if (!menuProgressed) {
                    long elapsed = now - entry.placedConfirmedMs();
                    if (elapsed >= BLOCK_RECHECK_MIN_ELAPSED_MS) {
                        if (entry.blockRecheckStartMs() <= 0L) {
                            entry.setBlockRecheckStartMs(now);
                        }
                        int misses = entry.blockRecheckMisses() + 1;
                        entry.setBlockRecheckMisses(misses);
                        logger.info("printer-debug", "runtime_state=BLOCK_RECHECK miss=" + misses + " elapsed=" + elapsed);
                        if (misses >= BLOCK_RECHECK_MISS_REQUIRED) {
                            int lost = entry.placedLostCount() + 1;
                            entry.setPlacedLostCount(lost);
                            if (lost > MAX_PLACED_LOST_COUNT) {
                                return fail(logger, "BLOCK_REVERTED_TOO_MANY_TIMES", "lost=" + lost);
                            }
                            entry.setMenuReplaceCount(0);
                            resetToRePlace(entry, now, delay);
                            logger.info("printer-debug", "runtime_state=FORCE_REPLACE reason=block_reverted lost=" + lost);
                            return PlaceExecResult.inProgress(0, "FORCE_REPLACE");
                        }
                    }
                }
            }
        }

        if (entry.placedBlock()
            && entry.awaitingMenu()
            && !entry.needOpenMenu()
            && entry.lastMenuWindowId() != -1
            && now - entry.lastMenuClickMs() > 300L) {
            entry.setNeedOpenMenu(true);
            entry.setMenuRetrySinceMs(now);
            entry.setNextMenuActionMs(now + Math.max(120L, delay));
            entry.setMenuClicksSinceOpen(0);
            entry.setTriedWindowId(-1);
            entry.setMenuNonEmptySinceMs(0L);
            entry.setMenuNonEmptyWindowId(-1);
        }

        if (entry.awaitingParamsChest()) {
            ContainerView view = bridge.getContainerSnapshot();
            int expectedWindowId = entry.lastMenuWindowId();
            AckState ack = bridge.waitForWindowChange(expectedWindowId, 0L);
            boolean switched = view.windowId() >= 0 && expectedWindowId >= 0 && view.windowId() != expectedWindowId;
            if (switched || ack == AckState.ACKED) {
                entry.setAwaitingParamsChest(false);
                entry.setAwaitingArgs(true);
                entry.setArgsStartMs(now);
                entry.setLastArgsActionMs(now);
                entry.setPendingArgClickSlot(-1);
                entry.setPendingArgClicks(0);
                entry.setPendingArgNextMs(0L);
                entry.setArgsWindowId(view.windowId());
                entry.setAdvancedArgIndex(0);
                entry.setArgsMisses(0);
                entry.clearUsedArgSlots();
                logger.info("printer-debug", "runtime_state=OPEN_PARAMS_CHEST switched=1");
                return PlaceExecResult.inProgress(0, "APPLY_ARGS");
            }
            if (entry.paramsStartMs() <= 0L) {
                entry.setParamsStartMs(now);
                entry.setNextParamsActionMs(now + Math.max(120, delay));
            }
            if (now - entry.paramsStartMs() > PARAMS_TIMEOUT_MS) {
                return fail(logger, "PARAMS_CHEST_TIMEOUT", "params chest did not open in time");
            }
            boolean sameWindow = view.windowId() >= 0 && expectedWindowId >= 0 && view.windowId() == expectedWindowId;
            if (sameWindow && !entry.needOpenParamsChest() && now - entry.paramsStartMs() >= PARAMS_REOPEN_AFTER_MS) {
                bridge.closeScreen();
                entry.setNeedOpenParamsChest(true);
                entry.setNextParamsActionMs(now + Math.max(120, delay));
                logger.info("printer-debug", "runtime_state=WAIT_PARAMS_CHEST action=close_for_reopen");
                return PlaceExecResult.inProgress(0, "WAIT_PARAMS_CHEST");
            }
            if (entry.needOpenParamsChest() && now >= entry.nextParamsActionMs()) {
                boolean opened = tryOpenParamsTarget(bridge);
                entry.setParamsOpenAttempts(entry.paramsOpenAttempts() + 1);
                entry.setNextParamsActionMs(now + Math.max(120, delay));
                logger.info("printer-debug",
                    "runtime_state=WAIT_PARAMS_CHEST action=reopen opened=" + opened + " attempts=" + entry.paramsOpenAttempts());
                if (!opened && entry.paramsOpenAttempts() >= MAX_MENU_OPEN_ATTEMPTS) {
                    return fail(logger, "PARAMS_TARGET_NOT_FOUND", "params reopen target rejected");
                }
            }
            return PlaceExecResult.inProgress(0, "WAIT_PARAMS_CHEST");
        }

        if (entry.awaitingMenu()) {
            ContainerView view = bridge.getContainerSnapshot();
            if (view.windowId() < 0) {
                if (!entry.needOpenMenu()
                    && entry.lastMenuWindowId() == -1
                    && entry.menuRetrySinceMs() > 0L
                    && now - entry.menuRetrySinceMs() > MENU_REOPEN_AFTER_MS) {
                    if (entry.menuOpenAttempts() >= MAX_MENU_OPEN_ATTEMPTS) {
                        if (!startMenuForceReplace(entry, now, delay, logger, "menu_not_opened")) {
                            return fail(logger, "MENU_OPEN_REPLACE_EXHAUSTED", "menu re-place cycles exhausted");
                        }
                        logger.info("printer-debug", "runtime_state=FORCE_REPLACE reason=menu_not_opened");
                        return PlaceExecResult.inProgress(0, "FORCE_REPLACE");
                    }
                    entry.setNeedOpenMenu(true);
                    entry.setMenuRetrySinceMs(now);
                    entry.setNextMenuActionMs(now + Math.max(120, delay));
                }
                if (entry.needOpenMenu() && now >= entry.nextMenuActionMs()) {
                    int openAttempt = entry.menuOpenAttempts() + 1;
                    boolean opened = tryOpenMenuTarget(bridge);
                    entry.setNeedOpenMenu(false);
                    entry.setMenuRetrySinceMs(now);
                    entry.setLastOpenAttemptMs(now);
                    entry.setNextMenuActionMs(now + Math.max(120, delay));
                    entry.setMenuOpenAttempts(openAttempt);
                    logger.info("printer-debug",
                        "runtime_state=OPEN_MENU opened=" + opened
                            + " attempt=" + openAttempt
                            + " target=sign_z-1_or_block purpose=menu_open");
                    if (!opened && entry.menuOpenAttempts() >= MAX_MENU_OPEN_ATTEMPTS) {
                        if (!startMenuForceReplace(entry, now, delay, logger, "menu_target_not_found")) {
                            return fail(logger, "MENU_OPEN_REPLACE_EXHAUSTED", "menu target missing after replace cycles");
                        }
                        logger.info("printer-debug", "runtime_state=FORCE_REPLACE reason=menu_target_not_found");
                        return PlaceExecResult.inProgress(0, "FORCE_REPLACE");
                    }
                }
                if (now - entry.menuStartMs() > MENU_TIMEOUT_MS && entry.menuOpenAttempts() >= MAX_MENU_OPEN_ATTEMPTS) {
                    if (!startMenuForceReplace(entry, now, delay, logger, "menu_timeout")) {
                        return fail(logger, "MENU_OPEN_REPLACE_EXHAUSTED", "menu timeout after replace cycles");
                    }
                    logger.info("printer-debug", "runtime_state=FORCE_REPLACE reason=menu_timeout");
                    return PlaceExecResult.inProgress(0, "FORCE_REPLACE");
                }
                return PlaceExecResult.inProgress(0, "WAIT_MENU_ACK");
            }
            if (!hasNonPlayerItems(view)) {
                entry.setMenuNonEmptySinceMs(0L);
                entry.setMenuNonEmptyWindowId(-1);
                return PlaceExecResult.inProgress(0, "WAIT_MENU_CONTENT");
            }
            if (entry.menuNonEmptyWindowId() != view.windowId()) {
                entry.setMenuNonEmptyWindowId(view.windowId());
                entry.setMenuNonEmptySinceMs(now);
                return PlaceExecResult.inProgress(0, "WAIT_MENU_CONTENT_STABLE");
            }
            if (entry.menuNonEmptySinceMs() <= 0L) {
                entry.setMenuNonEmptySinceMs(now);
                return PlaceExecResult.inProgress(0, "WAIT_MENU_CONTENT_STABLE");
            }
            if (now - entry.menuNonEmptySinceMs() < Math.max(MENU_NON_EMPTY_STABLE_MS, delay)) {
                return PlaceExecResult.inProgress(0, "WAIT_MENU_CONTENT_STABLE");
            }
            if (entry.triedWindowId() != view.windowId()) {
                entry.setTriedWindowId(view.windowId());
                entry.setMenuClicksSinceOpen(0);
            }
            if (entry.lastMenuWindowId() == view.windowId()
                && entry.lastMenuClickMs() > 0L
                && now - entry.lastMenuClickMs() < Math.max(180L, delay)) {
                return PlaceExecResult.inProgress(0, "MENU_ACTION_GAP");
            }

            NameRoute route = parseNameRoute(entry.name(), menuRouteResolver.resolvePrimaryMenuKey(entry));
            if (!route.scopeKey.isEmpty() && entry.menuClicksSinceOpen() == 0) {
                int scopeSlot = findSlotByKey(view, route.scopeKey, false);
                if (scopeSlot < 0) {
                    return fail(logger, "SCOPE_MENU_NOT_FOUND", "scope menu not found: " + route.scopeKey);
                }
                ClickResult scopeClick = bridge.clickSlot(view.windowId(), scopeSlot, 0, "PICKUP");
                if (!scopeClick.accepted()) {
                    return fail(logger, "SCOPE_MENU_CLICK_FAILED", scopeClick.reason());
                }
                entry.setMenuClicksSinceOpen(1);
                entry.setLastMenuWindowId(view.windowId());
                entry.setLastMenuClickMs(now);
                // Reset scope wait timeout from the actual scope click moment.
                entry.setMenuStartMs(now);
                entry.setNextMenuActionMs(now + delay);
                logger.info("printer-debug", "runtime_state=ROUTE_SCOPE slot=" + scopeSlot + " key=" + route.scopeKey);
                return PlaceExecResult.inProgress(0, "ROUTE_SCOPE");
            }
            if (!route.scopeKey.isEmpty() && entry.menuClicksSinceOpen() == 1) {
                AckState scopeAck = bridge.waitForWindowChange(entry.lastMenuWindowId(), 0L);
                boolean sameWindow = view.windowId() == entry.lastMenuWindowId();
                if ((scopeAck == AckState.PENDING || scopeAck == AckState.TIMEOUT) && sameWindow && now < entry.menuStartMs() + MENU_TIMEOUT_MS) {
                    return PlaceExecResult.inProgress(0, "WAIT_SCOPE_ACK");
                }
                if ((scopeAck == AckState.REJECTED || scopeAck == AckState.TIMEOUT) && sameWindow && now - entry.menuStartMs() > MENU_TIMEOUT_MS) {
                    return fail(logger, "SCOPE_MENU_ACK_TIMEOUT", "scope menu ack timeout");
                }
            }

            String routeKey = route.baseKey;
            int slot = findSlotByKey(view, routeKey, false);
            if (slot < 0) {
                if (!route.scopeKey.isEmpty()) {
                    return fail(logger, "SCOPE_TARGET_NOT_FOUND", "menu key not found: " + routeKey);
                }
                if (entry.randomClicks() > 250) {
                    return fail(logger, "RANDOM_EXHAUSTED", "random search exhausted");
                }
                int randomSlot = findRandomMenuSlot(view, entry.randomClicks());
                if (randomSlot < 0) {
                    return fail(logger, "NO_PATH_GUI", "menu key not found and random path unavailable: " + routeKey);
                }
                ClickResult rndClick = bridge.clickSlot(view.windowId(), randomSlot, 0, "PICKUP");
                if (!rndClick.accepted()) {
                    return fail(logger, "MENU_CLICK_FAILED", rndClick.reason());
                }
                entry.setRandomClicks(entry.randomClicks() + 1);
                entry.setMenuClicksSinceOpen(entry.menuClicksSinceOpen() + 1);
                entry.setLastMenuWindowId(view.windowId());
                entry.setLastMenuClickMs(now);
                entry.setMenuStartMs(now);
                entry.setNextMenuActionMs(now + delay);
                logger.info("printer-debug",
                    "runtime_state=ROUTE_MENU_RANDOM slot=" + randomSlot + " randomClicks=" + entry.randomClicks());
                return PlaceExecResult.inProgress(0, "ROUTE_MENU_RANDOM");
            }
            ClickResult click = bridge.clickSlot(view.windowId(), slot, 0, "PICKUP");
            if (!click.accepted()) {
                return fail(logger, "MENU_CLICK_FAILED", click.reason());
            }
            entry.setAwaitingMenu(false);
            entry.setAwaitingParamsChest(true);
            entry.setParamsStartMs(now);
            entry.setNextParamsActionMs(now + delay);
            entry.setParamsOpenAttempts(0);
            entry.setNeedOpenParamsChest(false);
            entry.setArgsWindowId(-1);
            entry.setLastMenuWindowId(view.windowId());
            entry.setLastMenuClickMs(now);
            entry.setMenuClicksSinceOpen(0);
            logger.info("printer-debug", "runtime_state=ROUTE_MENU slot=" + slot + " key=" + routeKey);
            return PlaceExecResult.inProgress(0, "OPEN_PARAMS_CHEST");
        }

        if (entry.awaitingArgs()) {
            if (now - entry.argsStartMs() > ARGS_TIMEOUT_MS) {
                return fail(logger, "ARGS_TIMEOUT", "args runtime timeout");
            }

            if (entry.pendingArgClicks() > 0) {
                if (now < entry.pendingArgNextMs()) {
                    return PlaceExecResult.inProgress(0, "APPLY_ARGS_PENDING");
                }
                ContainerView view = bridge.getContainerSnapshot();
                if (view.windowId() < 0) {
                    return fail(logger, "ARGS_CONTAINER_CLOSED", "container closed during pending clicks");
                }
                if (entry.argsWindowId() >= 0 && view.windowId() != entry.argsWindowId()) {
                    return fail(logger, "ARGS_WINDOW_CHANGED", "args window changed during pending clicks");
                }
                if (countNonPlayerSlots(view) <= 0) {
                    entry.setPendingArgNextMs(now + Math.max(80, delay));
                    return PlaceExecResult.inProgress(0, "ARGS_CONTAINER_EMPTY");
                }
                if (!hasNonPlayerSlotNumber(view, entry.pendingArgClickSlot())) {
                    return fail(logger, "ARGS_PENDING_SLOT_MISSING",
                        "pending slot disappeared: " + entry.pendingArgClickSlot());
                }
                if (!ensureCursorClear(entry, bridge, now)) {
                    if (isCursorTimeout(entry, now)) {
                        return fail(logger, "CURSOR_NOT_EMPTY", "cursor not empty (5s)");
                    }
                    return PlaceExecResult.inProgress(0, "CURSOR_WAIT");
                }
                ClickResult click = bridge.clickSlot(view.windowId(), entry.pendingArgClickSlot(), 0, "PICKUP");
                if (!click.accepted()) {
                    return fail(logger, "ARGS_CLICK_FAILED", click.reason());
                }
                entry.setPendingArgClicks(entry.pendingArgClicks() - 1);
                entry.setPendingArgNextMs(now + delay);
                if (entry.pendingArgClicks() <= 0) {
                    entry.setPendingArgClickSlot(-1);
                    entry.setAdvancedArgIndex(entry.advancedArgIndex() + 1);
                }
                entry.setLastArgsActionMs(now);
                return PlaceExecResult.inProgress(0, "APPLY_ARGS_PENDING");
            }
            if (entry.lastArgsActionMs() > 0L && now - entry.lastArgsActionMs() < Math.max(ARGS_ACTION_MIN_GAP_MS, delay)) {
                return PlaceExecResult.inProgress(0, "ARGS_ACTION_GAP");
            }

            List<PlaceArgSpec> args = entry.args();
            if (args == null || args.isEmpty() || entry.advancedArgIndex() >= args.size()) {
                if (!ensureCursorClear(entry, bridge, now)) {
                    if (isCursorTimeout(entry, now)) {
                        return fail(logger, "CURSOR_NOT_EMPTY", "cursor not empty (5s)");
                    }
                    return PlaceExecResult.inProgress(0, "CURSOR_WAIT");
                }
                entry.setAwaitingArgs(false);
                entry.setArgsWindowId(-1);
                bridge.closeScreen();
                return PlaceExecResult.ok(1);
            }

            ContainerView view = bridge.getContainerSnapshot();
            if (view.windowId() < 0) {
                return fail(logger, "ARGS_CONTAINER_CLOSED", "container closed while args pending");
            }
            if (entry.argsWindowId() < 0) {
                entry.setArgsWindowId(view.windowId());
            } else if (view.windowId() != entry.argsWindowId()) {
                return fail(logger, "ARGS_WINDOW_CHANGED", "args window changed");
            }
            if (countNonPlayerSlots(view) <= 0) {
                return PlaceExecResult.inProgress(0, "ARGS_CONTAINER_EMPTY");
            }

            PlaceArgSpec arg = args.get(entry.advancedArgIndex());
            Integer routedIndex = arg.slotIndex();
            if (arg.slotIndex() != null && arg.slotGuiIndex()) {
                SlotRouteResult route = ensureArgGuiPage(entry, arg, view, bridge, now, delay);
                if (route.waiting) {
                    return PlaceExecResult.inProgress(0, "ARGS_PAGE_WAIT");
                }
                if (route.skipArg) {
                    return fail(logger, "ARGS_PAGE_ROUTE_FAILED", route.reason);
                }
                routedIndex = route.resolvedSlotIndex;
            }
            int targetSlot = resolveArgTargetSlot(view, arg, routedIndex, entry);
            if (targetSlot < 0) {
                entry.setArgsMisses(entry.argsMisses() + 1);
                if (entry.argsMisses() > MAX_ARG_MISSES) {
                    return fail(logger, "ARGS_SLOT_NOT_FOUND", "cannot find args slot for key=" + arg.keyRaw());
                }
                return PlaceExecResult.inProgress(0, "ARGS_SLOT_WAIT");
            }
            entry.setArgsMisses(0);

            if (arg.mode() == PlaceInputMode.ITEM && arg.itemSpec() != null) {
                if (!ensureCursorClear(entry, bridge, now)) {
                    if (isCursorTimeout(entry, now)) {
                        return fail(logger, "CURSOR_NOT_EMPTY", "cursor not empty (5s)");
                    }
                    return PlaceExecResult.inProgress(0, "CURSOR_WAIT");
                }
                int hotbarIdx = chooseHotbarInjectionIndex(view);
                SlotView originalHotbar = findPlayerSlotViewByIndex(view, hotbarIdx);
                logger.info("PLACE_ITEM_TRACE",
                    "step=begin key=" + arg.keyRaw()
                        + " targetSlot=" + targetSlot
                        + " hotbar=" + hotbarIdx
                        + " hotbarOriginalEmpty=" + (originalHotbar == null || originalHotbar.empty()));
                if (!bridge.injectCreativeSlot(hotbarIdx, arg.itemSpec().itemId(), arg.itemSpec().nbtRaw(), arg.itemSpec().name())) {
                    return fail(logger, "ITEM_INJECT_FAILED", "injectCreativeSlot failed");
                }
                entry.markUsedArgSlot(targetSlot);
                int hotbarContainerSlot = findPlayerSlotByIndex(view, hotbarIdx);
                if (hotbarContainerSlot < 0) {
                    return fail(logger, "ITEM_HOTBAR_SLOT_NOT_FOUND", "hotbar slot not found in container");
                }
                ClickResult c1 = bridge.clickSlot(view.windowId(), hotbarContainerSlot, 0, "PICKUP");
                if (!c1.accepted()) {
                    return fail(logger, "ITEM_CLICK_FAILED", c1.reason());
                }
                ClickResult c2 = bridge.clickSlot(view.windowId(), targetSlot, 0, "PICKUP");
                if (!c2.accepted()) {
                    return fail(logger, "ITEM_CLICK_FAILED", c2.reason());
                }
                CursorState cursorAfterTarget = bridge.getCursorStack();
                if (!cursorAfterTarget.isEmpty()) {
                    ClickResult c3 = bridge.clickSlot(view.windowId(), hotbarContainerSlot, 0, "PICKUP");
                    if (!c3.accepted()) {
                        return fail(logger, "ITEM_CLICK_FAILED", c3.reason());
                    }
                }
                CursorState cursorAfter = bridge.getCursorStack();
                logger.info("PLACE_ITEM_TRACE",
                    "step=after_click key=" + arg.keyRaw()
                        + " targetSlot=" + targetSlot
                        + " cursorAfterTargetEmpty=" + cursorAfterTarget.isEmpty()
                        + " cursorAfterEmpty=" + cursorAfter.isEmpty());
                if (!restoreInjectedHotbar(bridge, hotbarIdx, originalHotbar)) {
                    return fail(logger, "ITEM_RESTORE_FAILED", "failed to restore temp hotbar slot");
                }
                logger.info("PLACE_ITEM_TRACE",
                    "step=restore key=" + arg.keyRaw()
                        + " targetSlot=" + targetSlot
                        + " hotbar=" + hotbarIdx
                        + " restoredEmpty=" + (originalHotbar == null || originalHotbar.empty()));
                if (arg.clicks() > 0) {
                    entry.setPendingArgClickSlot(targetSlot);
                    entry.setPendingArgClicks(arg.clicks());
                    entry.setPendingArgNextMs(now + delay);
                } else {
                    entry.setAdvancedArgIndex(entry.advancedArgIndex() + 1);
                }
                entry.setLastArgsActionMs(now);
                return PlaceExecResult.inProgress(0, "APPLY_ARGS_ITEM");
            }

            if (arg.clickOnly()) {
                int totalClicks = arg.clicks() > 0 ? arg.clicks() : 1;
                entry.markUsedArgSlot(targetSlot);
                entry.setPendingArgClickSlot(targetSlot);
                entry.setPendingArgClicks(totalClicks);
                entry.setPendingArgNextMs(now + Math.max(80, delay));
                entry.setLastArgsActionMs(now);
                logger.info("printer-debug", "runtime_state=APPLY_ARGS_CLICK_ONLY slot=" + targetSlot + " total=" + totalClicks);
                return PlaceExecResult.inProgress(0, "APPLY_ARGS_CLICK_ONLY_QUEUE");
            }

            if (!ensureCursorClear(entry, bridge, now)) {
                if (isCursorTimeout(entry, now)) {
                    return fail(logger, "CURSOR_NOT_EMPTY", "cursor not empty (5s)");
                }
                return PlaceExecResult.inProgress(0, "CURSOR_WAIT");
            }
            String templateItemId = templateItemIdForMode(arg.mode());
            String display = displayValueForMode(arg.mode(), arg.valueRaw());
            String nbt = nbtForMode(arg.mode(), display, arg.saveVariable());
            int hotbarIdx = chooseHotbarInjectionIndex(view);
            SlotView originalHotbar = findPlayerSlotViewByIndex(view, hotbarIdx);
            if (!bridge.injectCreativeSlot(hotbarIdx, templateItemId, nbt, display)) {
                return fail(logger, "INPUT_INJECT_FAILED",
                    "injectCreativeSlot failed mode=" + arg.mode() + " item=" + templateItemId);
            }
            entry.markUsedArgSlot(targetSlot);
            int hotbarContainerSlot = findPlayerSlotByIndex(view, hotbarIdx);
            if (hotbarContainerSlot < 0) {
                return fail(logger, "INPUT_HOTBAR_SLOT_NOT_FOUND", "hotbar slot not found in container");
            }
            ClickResult c1 = bridge.clickSlot(view.windowId(), hotbarContainerSlot, 0, "PICKUP");
            if (!c1.accepted()) {
                return fail(logger, "INPUT_CLICK_FAILED", c1.reason());
            }
            ClickResult c2 = bridge.clickSlot(view.windowId(), targetSlot, 0, "PICKUP");
            if (!c2.accepted()) {
                return fail(logger, "INPUT_CLICK_FAILED", c2.reason());
            }
            CursorState cursor = bridge.getCursorStack();
            if (!cursor.isEmpty()) {
                ClickResult c3 = bridge.clickSlot(view.windowId(), hotbarContainerSlot, 0, "PICKUP");
                if (!c3.accepted()) {
                    return fail(logger, "INPUT_CLICK_FAILED", c3.reason());
                }
            }
            if (!restoreInjectedHotbar(bridge, hotbarIdx, originalHotbar)) {
                return fail(logger, "INPUT_RESTORE_FAILED", "failed to restore temp hotbar slot");
            }
            if (arg.clicks() > 0) {
                entry.setPendingArgClickSlot(targetSlot);
                entry.setPendingArgClicks(arg.clicks());
                entry.setPendingArgNextMs(now + delay);
            } else {
                entry.setAdvancedArgIndex(entry.advancedArgIndex() + 1);
            }
            entry.setLastArgsActionMs(now);
            logger.info("printer-debug", "runtime_state=APPLY_ARGS_INPUT mode=" + arg.mode() + " slot=" + targetSlot);
            return PlaceExecResult.inProgress(0, "APPLY_ARGS_INPUT");
        }

        return PlaceExecResult.ok(1);
    }

    private static PlaceExecResult fail(CoreLogger logger, String code, String detail) {
        String safeCode = code == null ? "UNKNOWN" : code;
        String safeDetail = detail == null ? "" : detail.replace('\n', ' ').replace('\r', ' ');
        if (logger != null) {
            logger.info("printer-debug", "runtime_state=FAILED reason=" + safeCode + " detail=" + safeDetail);
        }
        return PlaceExecResult.fail(0, 0, safeCode, safeDetail);
    }

    private static boolean tryOpenMenuTarget(GameBridge bridge) {
        if (bridge == null) {
            return false;
        }
        if (bridge.openContainerIfNeeded()) {
            return true;
        }
        // Legacy parity: prefer sign at Z-1, then fallback to event block itself.
        if (bridge.useBlockAtOffset(0, 0, -1, "menu_open_sign")) {
            return true;
        }
        return bridge.useBlockAtOffset(0, 0, 0, "menu_open_block");
    }

    private static boolean tryOpenParamsTarget(GameBridge bridge) {
        if (bridge == null) {
            return false;
        }
        // Legacy sign+offset resolves to entry.up in this grid.
        if (bridge.useBlockAtOffset(0, 1, 0, "params_open")) {
            return true;
        }
        return bridge.openContainerIfNeeded();
    }

    private static boolean startMenuForceReplace(
        PlaceRuntimeEntry entry,
        long now,
        int delay,
        CoreLogger logger,
        String reason
    ) {
        if (entry == null) {
            return false;
        }
        int replaceCycle = entry.menuReplaceCount() + 1;
        entry.setMenuReplaceCount(replaceCycle);
        if (replaceCycle > MAX_MENU_REPLACE_CYCLES) {
            return false;
        }
        resetToRePlace(entry, now, delay);
        if (logger != null) {
            logger.info("printer-debug",
                "runtime_state=MENU_REPLACE cycle=" + replaceCycle + " reason=" + (reason == null ? "unknown" : reason));
        }
        return true;
    }

    private static void resetToRePlace(PlaceRuntimeEntry entry, long now, int delay) {
        if (entry == null) {
            return;
        }
        entry.setPlacedBlock(false);
        entry.setPlacedConfirmedMs(0L);
        entry.setBlockRecheckMisses(0);
        entry.setBlockRecheckStartMs(0L);
        entry.setAwaitingMenu(false);
        entry.setNeedOpenMenu(false);
        entry.setMenuStartMs(now);
        entry.setMenuRetrySinceMs(now);
        entry.setLastOpenAttemptMs(0L);
        entry.setLastMenuClickMs(0L);
        entry.setLastMenuWindowId(-1);
        entry.setAwaitingParamsChest(false);
        entry.setNeedOpenParamsChest(false);
        entry.setParamsOpenAttempts(0);
        entry.setParamsStartMs(0L);
        entry.setNextParamsActionMs(0L);
        entry.setAwaitingArgs(false);
        entry.setAdvancedArgIndex(0);
        entry.setArgsStartMs(0L);
        entry.setLastArgsActionMs(0L);
        entry.setPendingArgClickSlot(-1);
        entry.setPendingArgClicks(0);
        entry.setPendingArgNextMs(0L);
        entry.setArgsWindowId(-1);
        entry.setArgsMisses(0);
        entry.setMenuClicksSinceOpen(0);
        entry.setTriedWindowId(-1);
        entry.setMenuNonEmptySinceMs(0L);
        entry.setMenuNonEmptyWindowId(-1);
        entry.setRandomClicks(0);
        entry.setNeedOpenMenu(true);
        entry.setNextMenuActionMs(now + Math.max(120, delay));
        entry.setMenuOpenAttempts(0);
        entry.setForceRePlaceRequested(true);
    }

    private static boolean ensureCursorClear(PlaceRuntimeEntry entry, GameBridge bridge, long now) {
        CursorState cursor = bridge.getCursorStack();
        if (cursor.isEmpty()) {
            entry.setCursorNotEmptySinceMs(0L);
            return true;
        }
        if (entry.cursorNotEmptySinceMs() <= 0L) {
            entry.setCursorNotEmptySinceMs(now);
        }
        return false;
    }

    private static boolean isCursorTimeout(PlaceRuntimeEntry entry, long now) {
        return entry != null
            && entry.cursorNotEmptySinceMs() > 0L
            && (now - entry.cursorNotEmptySinceMs() > CURSOR_TIMEOUT_MS);
    }

    private static int resolveArgTargetSlot(ContainerView view, PlaceArgSpec arg, Integer overrideSlotIndex, PlaceRuntimeEntry entry) {
        if (arg == null || view == null) {
            return -1;
        }
        Integer slotIndex = overrideSlotIndex != null ? overrideSlotIndex : arg.slotIndex();
        if (slotIndex != null) {
            for (SlotView s : view.slots()) {
                if (s == null || s.playerInventory()) {
                    continue;
                }
                if (s.index() == slotIndex.intValue()) {
                    if (entry != null && entry.isUsedArgSlot(s.slotNumber())) {
                        return -1;
                    }
                    return s.slotNumber();
                }
            }
        }

        int glassCandidate = findArgTargetFromGlassBase(view, arg, entry);
        if (glassCandidate >= 0) {
            return glassCandidate;
        }

        String key = norm(arg.keyNorm());
        if (key.isEmpty()) {
            key = norm(arg.keyRaw());
        }
        return findArgSlotByKey(view, key, entry);
    }

    private static int findArgTargetFromGlassBase(ContainerView view, PlaceArgSpec arg, PlaceRuntimeEntry entry) {
        if (view == null || arg == null) {
            return -1;
        }
        String key = norm(arg.keyNorm());
        if (key.isEmpty()) {
            key = norm(arg.keyRaw());
        }
        List<SlotView> nonPlayer = new ArrayList<SlotView>();
        for (SlotView s : view.slots()) {
            if (s != null && !s.playerInventory()) {
                nonPlayer.add(s);
            }
        }
        if (nonPlayer.isEmpty()) {
            return -1;
        }
        Map<Integer, SlotView> byIndex = new HashMap<Integer, SlotView>();
        for (SlotView s : nonPlayer) {
            byIndex.put(Integer.valueOf(s.index()), s);
        }

        List<SlotView> bases = new ArrayList<SlotView>();
        for (SlotView s : nonPlayer) {
            if (!isGlassPaneSlot(s)) {
                continue;
            }
            if (!matchesGlassMeta(s, arg.glassMetaFilter())) {
                continue;
            }
            if (!key.isEmpty()) {
                String name = norm(s.displayName());
                if (!name.contains(key)) {
                    continue;
                }
            }
            bases.add(s);
        }
        if (bases.isEmpty()) {
            return -1;
        }
        bases.sort((a, b) -> Integer.compare(a.slotNumber(), b.slotNumber()));

        for (SlotView base : bases) {
            int candidate = findCandidateSlotForArg(view, byIndex, base, entry);
            if (candidate >= 0) {
                return candidate;
            }
        }
        return -1;
    }

    private static int findCandidateSlotForArg(ContainerView view, Map<Integer, SlotView> byIndex, SlotView base, PlaceRuntimeEntry entry) {
        if (view == null || byIndex == null || base == null) {
            return -1;
        }
        int[] offsets = new int[] {9, -1, 1, -9}; // down, left, right, up
        Integer bestEmpty = null;
        for (int off : offsets) {
            int idx = base.index() + off;
            if ((off == -1 && base.index() % 9 == 0) || (off == 1 && base.index() % 9 == 8)) {
                continue;
            }
            SlotView s = byIndex.get(Integer.valueOf(idx));
            if (s == null || s.playerInventory()) {
                continue;
            }
            if (entry != null && entry.isUsedArgSlot(s.slotNumber())) {
                continue;
            }
            if (s.empty()) {
                if (bestEmpty == null) {
                    bestEmpty = Integer.valueOf(s.slotNumber());
                }
                continue;
            }
            if (isGlassPaneSlot(s)) {
                continue;
            }
            return s.slotNumber();
        }
        return bestEmpty == null ? -1 : bestEmpty.intValue();
    }

    private static boolean isGlassPaneSlot(SlotView s) {
        if (s == null) {
            return false;
        }
        String id = s.itemId() == null ? "" : s.itemId().toLowerCase();
        if (id.endsWith(":glass_pane")) {
            return true;
        }
        return id.contains("_stained_glass_pane");
    }

    private static boolean matchesGlassMeta(SlotView s, Integer glassMetaFilter) {
        if (glassMetaFilter == null) {
            return true;
        }
        if (s == null) {
            return false;
        }
        String id = s.itemId() == null ? "" : s.itemId().toLowerCase();
        String[] legacyMetaToColor = new String[] {
            "white", "orange", "magenta", "light_blue",
            "yellow", "lime", "pink", "gray",
            "light_gray", "cyan", "purple", "blue",
            "brown", "green", "red", "black"
        };
        int idx = glassMetaFilter.intValue();
        if (idx < 0 || idx >= legacyMetaToColor.length) {
            return false;
        }
        String color = legacyMetaToColor[idx];
        if (id.contains(color + "_stained_glass_pane")) {
            return true;
        }
        // Legacy "silver" maps to modern "light_gray".
        if ("light_gray".equals(color) && id.contains("silver_stained_glass_pane")) {
            return true;
        }
        return false;
    }

    private static int findArgSlotByKey(ContainerView view, String key, PlaceRuntimeEntry entry) {
        String normKey = norm(key);
        if (normKey.isEmpty() || view == null) {
            return -1;
        }
        for (SlotView s : view.slots()) {
            if (s == null || s.playerInventory()) {
                continue;
            }
            if (entry != null && entry.isUsedArgSlot(s.slotNumber())) {
                continue;
            }
            String slotText = norm(s.displayName());
            if (slotText.equals(normKey)) {
                return s.slotNumber();
            }
        }
        for (SlotView s : view.slots()) {
            if (s == null || s.playerInventory()) {
                continue;
            }
            if (entry != null && entry.isUsedArgSlot(s.slotNumber())) {
                continue;
            }
            String slotText = norm(s.displayName());
            if (slotText.contains(normKey)) {
                return s.slotNumber();
            }
        }
        return -1;
    }

    private static int findSlotByKey(ContainerView view, String key, boolean skipPlayer) {
        String normKey = norm(key);
        if (normKey.isEmpty() || view == null) {
            return -1;
        }
        // Legacy flow prefers exact item-name match before looser contains().
        for (SlotView s : view.slots()) {
            if (s == null) {
                continue;
            }
            if (skipPlayer && s.playerInventory()) {
                continue;
            }
            String slotText = norm(s.displayName());
            if (slotText.equals(normKey)) {
                return s.slotNumber();
            }
        }
        for (SlotView s : view.slots()) {
            if (s == null) {
                continue;
            }
            if (skipPlayer && s.playerInventory()) {
                continue;
            }
            String slotText = norm(s.displayName());
            if (slotText.contains(normKey)) {
                return s.slotNumber();
            }
        }
        return -1;
    }

    private static int findPlayerSlotByIndex(ContainerView view, int index) {
        for (SlotView s : view.slots()) {
            if (s != null && s.playerInventory() && s.index() == index) {
                return s.slotNumber();
            }
        }
        return -1;
    }

    private static SlotView findPlayerSlotViewByIndex(ContainerView view, int index) {
        if (view == null) {
            return null;
        }
        for (SlotView s : view.slots()) {
            if (s != null && s.playerInventory() && s.index() == index) {
                return s;
            }
        }
        return null;
    }

    private static int chooseHotbarInjectionIndex(ContainerView view) {
        if (view == null) {
            return 8;
        }
        for (SlotView s : view.slots()) {
            if (s == null || !s.playerInventory()) {
                continue;
            }
            if (s.index() >= 0 && s.index() <= 8 && s.empty()) {
                return s.index();
            }
        }
        return 8;
    }

    private static boolean restoreInjectedHotbar(GameBridge bridge, int hotbarIdx, SlotView originalHotbar) {
        if (bridge == null) {
            return false;
        }
        if (originalHotbar == null || originalHotbar.empty()) {
            // Prefer empty slot for temporary injection, nothing to restore.
            return true;
        }
        return bridge.injectCreativeSlot(
            hotbarIdx,
            originalHotbar.itemId(),
            originalHotbar.nbt(),
            originalHotbar.displayName()
        );
    }

    private static String norm(String raw) {
        if (raw == null) {
            return "";
        }
        String s = raw.toLowerCase();
        s = s.replace('ё', 'е');
        return s.trim();
    }

    private static String nbtForMode(int mode, String display, boolean saveVar) {
        if ((mode == PlaceInputMode.VARIABLE || mode == PlaceInputMode.ARRAY) && saveVar) {
            return "{display:{LocName:\"save\",Lore:[\"\\u00a7d\\u0421\\u041e\\u0425\\u0420\\u0410\\u041d\\u0415\\u041d\\u041e\"]}}";
        }
        if (mode == PlaceInputMode.APPLE) {
            String safe = escapeNbt(display);
            return "{display:{LocName:\"" + safe + "\"}}";
        }
        return "";
    }

    private static String escapeNbt(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String templateItemIdForMode(int mode) {
        if (mode == PlaceInputMode.NUMBER) {
            return "minecraft:slime_ball";
        }
        if (mode == PlaceInputMode.VARIABLE) {
            return "minecraft:magma_cream";
        }
        if (mode == PlaceInputMode.ARRAY) {
            return "minecraft:item_frame";
        }
        if (mode == PlaceInputMode.LOCATION) {
            return "minecraft:paper";
        }
        if (mode == PlaceInputMode.APPLE) {
            return "minecraft:apple";
        }
        return "minecraft:book";
    }

    private static String displayValueForMode(int mode, String raw) {
        String v = raw == null ? "" : raw.trim();
        if (mode == PlaceInputMode.TEXT) {
            if (v.startsWith("&r")) {
                v = "§r" + v.substring(2);
            } else if (!v.startsWith("§r")) {
                v = "§r" + v;
            }
            return v;
        }
        if (mode == PlaceInputMode.NUMBER) {
            String n = extractNumber(v);
            return n.isEmpty() ? v : n;
        }
        return v;
    }

    private static String extractNumber(String raw) {
        if (raw == null) {
            return "";
        }
        String s = raw.trim();
        boolean dot = false;
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (i == 0 && (c == '-' || c == '+')) {
                out.append(c);
                continue;
            }
            if (c >= '0' && c <= '9') {
                out.append(c);
                continue;
            }
            if (c == '.' && !dot) {
                out.append(c);
                dot = true;
                continue;
            }
        }
        return out.toString();
    }

    private static final class NameRoute {
        final String baseKey;
        final String scopeKey;

        NameRoute(String baseKey, String scopeKey) {
            this.baseKey = baseKey == null ? "" : baseKey;
            this.scopeKey = scopeKey == null ? "" : scopeKey;
        }
    }

    private static NameRoute parseNameRoute(String rawName, String fallbackBaseKey) {
        String raw = rawName == null ? "" : rawName.trim();
        if (raw.isEmpty()) {
            return new NameRoute(norm(fallbackBaseKey), "");
        }
        int idx = raw.indexOf("||");
        if (idx < 0) {
            return new NameRoute(norm(raw), "");
        }
        String base = norm(raw.substring(0, idx).trim());
        if (base.isEmpty()) {
            base = norm(fallbackBaseKey);
        }
        String scopeRaw = norm(raw.substring(idx + 2).trim());
        if (scopeRaw.contains("игрок по условию")) {
            return new NameRoute(base, "выбрать игроков по условию");
        }
        if (scopeRaw.contains("моб по условию")) {
            return new NameRoute(base, "выбрать мобов по условию");
        }
        if (scopeRaw.contains("сущность по условию")) {
            return new NameRoute(base, "выбрать сущности по условию");
        }
        return new NameRoute(base.isEmpty() ? norm(fallbackBaseKey) : base, "");
    }

    private static final class SlotRouteResult {
        final boolean waiting;
        final boolean skipArg;
        final Integer resolvedSlotIndex;
        final String reason;

        SlotRouteResult(boolean waiting, boolean skipArg, Integer resolvedSlotIndex, String reason) {
            this.waiting = waiting;
            this.skipArg = skipArg;
            this.resolvedSlotIndex = resolvedSlotIndex;
            this.reason = reason == null ? "" : reason;
        }
    }

    private static SlotRouteResult ensureArgGuiPage(
        PlaceRuntimeEntry entry,
        PlaceArgSpec arg,
        ContainerView view,
        GameBridge bridge,
        long now,
        int delay
    ) {
        int nonPlayer = countNonPlayerSlots(view);
        if (nonPlayer <= 0 || arg == null || arg.slotIndex() == null) {
            return new SlotRouteResult(false, false, arg == null ? null : arg.slotIndex(), "");
        }
        int abs = arg.slotIndex().intValue();
        int targetPage = abs / nonPlayer;
        int local = abs % nonPlayer;
        if (targetPage <= 0) {
            entry.setArgsGuiPage(0);
            entry.setArgsPageTurnPending(false);
            entry.setArgsPageRetryCount(0);
            return new SlotRouteResult(false, false, local, "");
        }
        if (entry.argsGuiPage() > targetPage) {
            return new SlotRouteResult(false, true, null, "cannot_navigate_back current=" + entry.argsGuiPage() + " target=" + targetPage);
        }
        if (entry.argsGuiPage() == targetPage) {
            entry.setArgsPageTurnPending(false);
            entry.setArgsPageRetryCount(0);
            return new SlotRouteResult(false, false, local, "");
        }

        if (entry.argsPageTurnPending()) {
            String curHash = buildNonPlayerHash(view);
            boolean changed = !curHash.equals(entry.argsPageLastHash());
            AckState ack = bridge.waitForWindowChange(view.windowId(), 0L);
            boolean acked = ack == AckState.ACKED;
            if (bridge.getCursorStack().isEmpty() && (changed || acked)) {
                entry.setArgsGuiPage(entry.argsGuiPage() + 1);
                entry.setArgsPageTurnPending(false);
                entry.setArgsPageTurnStartMs(0L);
                entry.setArgsPageTurnNextMs(now + Math.max(50, delay));
                entry.setArgsPageRetryCount(0);
                if (entry.argsGuiPage() >= targetPage) {
                    return new SlotRouteResult(false, false, local, "");
                }
                return new SlotRouteResult(true, false, null, "");
            }
            if (now - entry.argsPageTurnStartMs() > PAGE_TURN_TIMEOUT_MS) {
                entry.setArgsPageTurnPending(false);
                entry.setArgsPageTurnStartMs(0L);
                entry.setArgsPageTurnNextMs(now + Math.max(80, delay));
                entry.setArgsPageRetryCount(entry.argsPageRetryCount() + 1);
            } else {
                return new SlotRouteResult(true, false, null, "");
            }
        }

        if (entry.argsPageRetryCount() >= PAGE_TURN_MAX_RETRIES) {
            return new SlotRouteResult(false, true, null, "retries_exhausted:" + entry.argsPageRetryCount());
        }
        if (now < entry.argsPageTurnNextMs()) {
            return new SlotRouteResult(true, false, null, "");
        }

        SlotView nextArrow = findNextPageArrowSlot(view);
        if (nextArrow == null) {
            return new SlotRouteResult(false, true, null, "next_page_arrow_not_found");
        }
        ClickResult click = bridge.clickSlot(view.windowId(), nextArrow.slotNumber(), 0, "PICKUP");
        if (!click.accepted()) {
            entry.setArgsPageRetryCount(entry.argsPageRetryCount() + 1);
            entry.setArgsPageTurnNextMs(now + Math.max(120, delay));
            return new SlotRouteResult(true, false, null, "");
        }
        entry.setArgsPageLastHash(buildNonPlayerHash(view));
        entry.setArgsPageTurnPending(true);
        entry.setArgsPageTurnStartMs(now);
        entry.setArgsPageTurnNextMs(now + Math.max(120, delay));
        return new SlotRouteResult(true, false, null, "");
    }

    private static int countNonPlayerSlots(ContainerView view) {
        if (view == null) {
            return 0;
        }
        int count = 0;
        for (SlotView s : view.slots()) {
            if (s != null && !s.playerInventory()) {
                count++;
            }
        }
        return count;
    }

    private static SlotView findNextPageArrowSlot(ContainerView view) {
        if (view == null) {
            return null;
        }
        SlotView last = null;
        for (SlotView s : view.slots()) {
            if (s == null || s.playerInventory()) {
                continue;
            }
            last = s;
        }
        if (last != null && isNextPageArrowCandidate(last)) {
            return last;
        }
        for (SlotView s : view.slots()) {
            if (s == null || s.playerInventory()) {
                continue;
            }
            if (isNextPageArrowCandidate(s)) {
                return s;
            }
        }
        if (last != null && isArrowItem(last)) {
            return last;
        }
        return null;
    }

    private static int findRandomMenuSlot(ContainerView view, int randomClicks) {
        if (view == null || view.slots() == null) {
            return -1;
        }
        java.util.ArrayList<Integer> regular = new java.util.ArrayList<Integer>();
        java.util.ArrayList<Integer> arrow = new java.util.ArrayList<Integer>();
        for (SlotView s : view.slots()) {
            if (s == null || s.playerInventory() || s.empty()) {
                continue;
            }
            if (isArrowItem(s)) {
                arrow.add(Integer.valueOf(s.slotNumber()));
            } else {
                regular.add(Integer.valueOf(s.slotNumber()));
            }
        }
        java.util.Collections.sort(regular);
        java.util.Collections.sort(arrow);
        java.util.ArrayList<Integer> candidates = regular.isEmpty() ? arrow : regular;
        if (candidates.isEmpty()) {
            return -1;
        }
        int seed = randomClicks < 0 ? 0 : randomClicks;
        int idx = seed % candidates.size();
        return candidates.get(idx).intValue();
    }

    private static boolean isArrowItem(SlotView s) {
        if (s == null) {
            return false;
        }
        String id = s.itemId() == null ? "" : s.itemId().toLowerCase();
        return id.endsWith(":arrow");
    }

    private static boolean isNextPageArrowCandidate(SlotView s) {
        if (!isArrowItem(s)) {
            return false;
        }
        String hay = normalizePageLoreLine(s.displayName()) + " " + normalizePageLoreLine(s.nbt());
        boolean hasOpen = hay.contains("нажми чтобы открыть") || hay.contains("open");
        boolean hasNext = hay.contains("следующую страницу") || hay.contains("next page");
        return hasOpen && hasNext;
    }

    private static String normalizePageLoreLine(String line) {
        if (line == null || line.isEmpty()) {
            return "";
        }
        String n = line.replace('\u00A0', ' ').toLowerCase();
        n = n.replace('ё', 'е');
        n = n.replaceAll("[^\\p{L}\\p{N}\\s]+", " ");
        n = n.replaceAll("\\s+", " ").trim();
        return n;
    }

    private static String buildNonPlayerHash(ContainerView view) {
        if (view == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (SlotView s : view.slots()) {
            if (s == null || s.playerInventory()) {
                continue;
            }
            sb.append(s.index()).append(':')
                .append(s.itemId()).append(':')
                .append(s.displayName()).append(':')
                .append(s.nbt()).append(';');
        }
        return Integer.toHexString(sb.toString().hashCode());
    }

    private static boolean hasNonPlayerItems(ContainerView view) {
        if (view == null || view.slots() == null) {
            return false;
        }
        for (SlotView s : view.slots()) {
            if (s == null || s.playerInventory()) {
                continue;
            }
            if (!s.empty()) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasNonPlayerSlotNumber(ContainerView view, int slotNumber) {
        if (view == null || view.slots() == null || slotNumber < 0) {
            return false;
        }
        for (SlotView s : view.slots()) {
            if (s == null || s.playerInventory()) {
                continue;
            }
            if (s.slotNumber() == slotNumber) {
                return true;
            }
        }
        return false;
    }
}

