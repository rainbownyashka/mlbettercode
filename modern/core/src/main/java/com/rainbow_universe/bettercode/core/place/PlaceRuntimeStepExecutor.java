package com.rainbow_universe.bettercode.core.place;

import com.rainbow_universe.bettercode.core.CoreLogger;
import com.rainbow_universe.bettercode.core.GameBridge;
import com.rainbow_universe.bettercode.core.PlaceExecResult;
import com.rainbow_universe.bettercode.core.bridge.AckState;
import com.rainbow_universe.bettercode.core.bridge.BlockPosView;
import com.rainbow_universe.bettercode.core.bridge.ClickResult;
import com.rainbow_universe.bettercode.core.bridge.ContainerView;
import com.rainbow_universe.bettercode.core.bridge.CursorState;
import com.rainbow_universe.bettercode.core.bridge.SlotView;
import com.rainbow_universe.bettercode.core.settings.SettingsProvider;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class PlaceRuntimeStepExecutor {
    private static final long MENU_TIMEOUT_MS = 6000L;
    private static final long MENU_REOPEN_AFTER_MS = 1600L;
    private static final long MENU_NON_EMPTY_STABLE_MS = 250L;
    private static final long PARAMS_TIMEOUT_MS = 6000L;
    private static final long PARAMS_REOPEN_AFTER_MS = 1200L;
    private static final long PARAMS_WINDOW_STABLE_MS = 140L;
    private static final long ARGS_TIMEOUT_MS = 12000L;
    private static final long ARGS_ACTION_MIN_GAP_MS = 180L;
    private static final long CURSOR_TIMEOUT_MS = 5000L;
    private static final long POST_PLACE_START_DELAY_MS = 450L;
    private static final long POST_PLACE_NEGATE_DELAY_MS = 350L;
    private static final long POST_PLACE_STEP_DELAY_MS = 550L;
    private static final int MAX_ARG_MISSES = 60;
    private static final int MAX_MENU_OPEN_ATTEMPTS = 8;
    private static final int MAX_MENU_REPLACE_CYCLES = 2;
    private static final int MAX_RANDOM_ROUTE_CLICKS = 250;
    private static final int MAX_RANDOM_SAME_HASH_MISSES = 250;
    private static final long RANDOM_ROUTE_MIN_GAP_MS = 220L;
    private static final long MENU_CLICK_MIN_GAP_MS = 300L;
    private static final long MENU_NEXT_ACTION_MIN_GAP_MS = 220L;
    private static final int MAX_PLACED_LOST_COUNT = 6;
    private static final long BLOCK_RECHECK_MIN_ELAPSED_MS = 1200L;
    private static final int BLOCK_RECHECK_MISS_REQUIRED = 3;
    private static final long PAGE_TURN_TIMEOUT_MS = 1500L;
    private static final int PAGE_TURN_MAX_RETRIES = 5;
    private static final long VERBOSE_TRACE_GAP_MS = 1600L;
    private static long lastVerboseTraceMs = 0L;
    private static final int MAX_MENU_HINT_KEYS = 512;
    private static final Map<String, Set<String>> MENU_SUBMENU_HINTS = new HashMap<String, Set<String>>();

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
        long now = bridge.nowMs();
        int delay = settings.getInt("printer.stepDelayMs", 80);
        if (delay < 0) {
            delay = 0;
        }
        if (entry.moveOnly()) {
            clearGuiStageState(entry);
            logger.info("printer-debug", "runtime_state=SKIP_STEP moveOnly=1");
            return bridge.executePlaceStep(entry, false);
        }
        String rawName = entry.name() == null ? "" : entry.name().trim();
        String rawArgs = entry.argsRaw() == null ? "" : entry.argsRaw().trim();
        boolean hasMenuPayload = !rawName.isEmpty() || (!rawArgs.isEmpty() && !"no".equalsIgnoreCase(rawArgs));
        if (!hasMenuPayload) {
            logger.info("printer-debug", "runtime_state=SKIP_STEP moveOnly=0");
            return bridge.executePlaceStep(entry, false);
        }
        logStepTrace(logger, entry, hasMenuPayload, now);
        boolean verboseTrace = settings.getBoolean("printer.verboseRuntimeTrace", false) && shouldEmitVerboseTrace(now);
        boolean verboseMenuSnapshot = settings.getBoolean("printer.verboseMenuSnapshot", false);
        if (verboseTrace) {
            logger.info("printer-debug",
                "runtime_tick placed=" + entry.placedBlock()
                    + " awaitingMenu=" + entry.awaitingMenu()
                    + " needOpenMenu=" + entry.needOpenMenu()
                    + " awaitingParams=" + entry.awaitingParamsChest()
                    + " awaitingArgs=" + entry.awaitingArgs()
                    + " menuAttempts=" + entry.menuOpenAttempts()
                    + " menuClicks=" + entry.menuClicksSinceOpen()
                    + " randomClicks=" + entry.randomClicks()
                    + " blockId=" + safe(entry.blockId())
                    + " nameRaw=" + safe(entry.name()));
        }
        // Global cursor gate: no new actions (including new block placement) while cursor is occupied.
        if (!ensureCursorClear(entry, bridge, now)) {
            if (isCursorTimeout(entry, now)) {
                return fail(logger, "CURSOR_NOT_EMPTY", "global cursor guard timeout");
            }
            return PlaceExecResult.inProgress(0, "CURSOR_WAIT_GLOBAL");
        }

        if (!entry.placedBlock()) {
            // Use the original runtime entry so adapter can observe re-place intent flags
            // and keep position parity for force-replace cycles.
            PlaceExecResult placed = bridge.executePlaceStep(entry, false);
            if (!placed.ok()) {
                return placed;
            }
            if (placed.inProgress()) {
                return placed;
            }
            entry.setPlacedBlock(true);
            entry.setPlacedConfirmedMs(now);
            if (entry.postPlaceKind() == PlaceRuntimeEntry.POST_PLACE_SIGN_NAME
                || entry.postPlaceKind() == PlaceRuntimeEntry.POST_PLACE_CYCLE) {
                entry.setAwaitingMenu(false);
                entry.setAwaitingParamsChest(false);
                entry.setAwaitingArgs(false);
                entry.setPostPlaceStage(0);
                entry.setPostPlaceNextMs(now + Math.max(POST_PLACE_START_DELAY_MS, delay));
                logger.info("printer-debug", "runtime_state=POST_PLACE_START kind=" + entry.postPlaceKind() + " reason=after_place");
                return PlaceExecResult.inProgress(0, "POST_PLACE");
            }
            entry.setAwaitingMenu(true);
            entry.setNeedOpenMenu(true);
            entry.setMenuStartMs(now);
            entry.setMenuRetrySinceMs(now);
            entry.setLastOpenAttemptMs(0L);
            entry.setMenuOpenAttempts(0);
            entry.setForceRePlaceRequested(false);
            entry.setMenuReplaceCount(0);
            entry.setNextMenuActionMs(now + Math.max(MENU_NEXT_ACTION_MIN_GAP_MS, delay));
            logger.info("printer-debug", "runtime_state=PLACE_BLOCK confirmed=1");
            return PlaceExecResult.inProgress(0, "OPEN_MENU");
        }

        if (entry.placedBlock() && entry.placedConfirmedMs() > 0L) {
            // Accept both legacy planes: entry.down (expected) and entry (server/client offset jitter).
            boolean blockStillPlaced = bridge.isBlockAtOffset(0, -1, 0, entry.blockId())
                || bridge.isBlockAtOffset(0, 0, 0, entry.blockId());
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

        if (entry.postPlaceKind() != PlaceRuntimeEntry.POST_PLACE_NONE
            && !entry.awaitingMenu()
            && !entry.awaitingParamsChest()
            && !entry.awaitingArgs()) {
            return executePostPlace(entry, bridge, logger, now, delay);
        }

        if (entry.awaitingParamsChest()) {
            if (!hasAdvancedArgs(entry)) {
                entry.setAwaitingParamsChest(false);
                entry.setNeedOpenParamsChest(false);
                entry.setAwaitingArgs(false);
                entry.setArgsWindowId(-1);
                entry.setParamsReadyWindowId(-1);
                entry.setParamsReadySinceMs(0L);
                logger.info("printer-debug", "runtime_state=SKIP_PARAMS_NO_ARGS");
                return finishOrPostPlace(entry, bridge, logger, now, delay);
            }
            ContainerView view = bridge.getContainerSnapshot();
            int expectedWindowId = entry.lastMenuWindowId();
            AckState ack = bridge.waitForWindowChange(expectedWindowId, 0L);
            boolean switched = view.windowId() >= 0 && expectedWindowId >= 0 && view.windowId() != expectedWindowId;
            if (verboseTrace) {
                logger.info("printer-debug",
                    "params_snapshot expectedWindow=" + expectedWindowId
                        + " window=" + view.windowId()
                        + " ack=" + ack
                        + " needOpenParams=" + entry.needOpenParamsChest()
                        + " paramsAttempts=" + entry.paramsOpenAttempts());
            }
            boolean switchedOrAcked = switched || ack == AckState.ACKED;
            boolean paramsReady = view.windowId() >= 0 && countNonPlayerSlots(view) > 0;
            boolean paramsChestReady = hasParamsChestNearTarget(bridge);
            String paramsHash = buildNonPlayerHash(view);
            if (paramsReady) {
                if (entry.paramsReadyWindowId() != view.windowId()) {
                    entry.setParamsReadyWindowId(view.windowId());
                    entry.setParamsReadySinceMs(now);
                    logger.info("printer-debug",
                        "runtime_state=WAIT_PARAMS_CHEST action=window_ready window=" + view.windowId()
                            + " nonPlayer=" + countNonPlayerSlots(view)
                            + " chestReady=" + paramsChestReady
                            + " title=" + safe(view.title())
                            + " hash=" + safe(paramsHash));
                }
            } else {
                entry.setParamsReadyWindowId(-1);
                entry.setParamsReadySinceMs(0L);
            }
            boolean paramsStable = entry.paramsReadyWindowId() == view.windowId()
                && entry.paramsReadySinceMs() > 0L
                && now - entry.paramsReadySinceMs() >= PARAMS_WINDOW_STABLE_MS;
            if (paramsReady && paramsStable) {
                logger.info("printer-debug",
                    "params_apply_guard window=" + view.windowId()
                        + " chestReady=" + paramsChestReady
                        + " nonPlayer=" + countNonPlayerSlots(view)
                        + " title=" + safe(view.title())
                        + " hash=" + safe(paramsHash)
                        + " expectedWindow=" + expectedWindowId
                        + " ack=" + ack);
                if (!paramsChestReady) {
                    if (!entry.needOpenParamsChest() && now - entry.paramsStartMs() >= PARAMS_REOPEN_AFTER_MS) {
                        bridge.closeScreen();
                        entry.setNeedOpenParamsChest(true);
                        entry.setNextParamsActionMs(now + Math.max(120, delay));
                        entry.setParamsReadyWindowId(-1);
                        entry.setParamsReadySinceMs(0L);
                        logger.info("printer-debug",
                            "runtime_state=WAIT_PARAMS_CHEST action=close_for_reopen reason=guard_chest_missing window=" + view.windowId()
                                + " nonPlayer=" + countNonPlayerSlots(view)
                                + " title=" + safe(view.title())
                                + " hash=" + safe(paramsHash));
                    }
                    return PlaceExecResult.inProgress(0, "WAIT_PARAMS_CHEST");
                }
                if (!ensureCursorClear(entry, bridge, now)) {
                    if (isCursorTimeout(entry, now)) {
                        return fail(logger, "CURSOR_NOT_EMPTY", "cursor not empty before args stage");
                    }
                    return PlaceExecResult.inProgress(0, "CURSOR_WAIT");
                }
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
                entry.setParamsReadyWindowId(-1);
                entry.setParamsReadySinceMs(0L);
                logger.info("printer-debug", "runtime_state=OPEN_PARAMS_CHEST switched=1");
                return PlaceExecResult.inProgress(0, "APPLY_ARGS");
            }
            if (paramsReady && !paramsStable) {
                return PlaceExecResult.inProgress(0, "WAIT_PARAMS_STABLE");
            }
            if (switchedOrAcked && !paramsReady) {
                long elapsed = entry.paramsStartMs() <= 0L ? 0L : (now - entry.paramsStartMs());
                if (!entry.needOpenParamsChest() && elapsed < PARAMS_REOPEN_AFTER_MS) {
                    logger.info("printer-debug",
                        "runtime_state=WAIT_PARAMS_CHEST action=transition_wait window=" + view.windowId()
                            + " nonPlayer=" + countNonPlayerSlots(view)
                            + " chestReady=" + paramsChestReady
                            + " ack=" + ack
                            + " elapsed=" + elapsed
                            + " title=" + safe(view.title())
                            + " hash=" + safe(paramsHash));
                    return PlaceExecResult.inProgress(0, "WAIT_PARAMS_CHEST");
                }
                if (!entry.needOpenParamsChest()) {
                    if (paramsChestReady) {
                        logger.info("printer-debug",
                            "runtime_state=WAIT_PARAMS_CHEST action=hold_chest_detected window=" + view.windowId()
                                + " nonPlayer=" + countNonPlayerSlots(view)
                                + " chestReady=1 ack=" + ack
                                + " title=" + safe(view.title())
                                + " hash=" + safe(paramsHash));
                        return PlaceExecResult.inProgress(0, "WAIT_PARAMS_CHEST");
                    }
                    entry.setNeedOpenParamsChest(true);
                    entry.setNextParamsActionMs(now + Math.max(120, delay));
                    logger.info("printer-debug",
                        "runtime_state=WAIT_PARAMS_CHEST action=reopen_not_ready window=" + view.windowId()
                            + " nonPlayer=" + countNonPlayerSlots(view)
                            + " chestReady=" + paramsChestReady
                            + " ack=" + ack
                            + " elapsed=" + elapsed
                            + " title=" + safe(view.title())
                            + " hash=" + safe(paramsHash));
                }
                return PlaceExecResult.inProgress(0, "WAIT_PARAMS_CHEST");
            }
            if (entry.paramsStartMs() <= 0L) {
                entry.setParamsStartMs(now);
                entry.setNextParamsActionMs(now + Math.max(120, delay));
            }
            if (now - entry.paramsStartMs() > PARAMS_TIMEOUT_MS) {
                return fail(logger, "PARAMS_CHEST_TIMEOUT", "params chest did not open in time");
            }
            boolean sameWindow = view.windowId() >= 0 && expectedWindowId >= 0 && view.windowId() == expectedWindowId;
            boolean noWindow = view.windowId() < 0;
            if (sameWindow && !entry.needOpenParamsChest() && now - entry.paramsStartMs() >= PARAMS_REOPEN_AFTER_MS) {
                bridge.closeScreen();
                entry.setNeedOpenParamsChest(true);
                entry.setNextParamsActionMs(now + Math.max(120, delay));
                entry.setParamsReadyWindowId(-1);
                entry.setParamsReadySinceMs(0L);
                logger.info("printer-debug", "runtime_state=WAIT_PARAMS_CHEST action=close_for_reopen");
                return PlaceExecResult.inProgress(0, "WAIT_PARAMS_CHEST");
            }
            if (noWindow && !entry.needOpenParamsChest() && now - entry.paramsStartMs() >= PARAMS_REOPEN_AFTER_MS) {
                entry.setNeedOpenParamsChest(true);
                entry.setNextParamsActionMs(now + Math.max(120, delay));
                entry.setParamsReadyWindowId(-1);
                entry.setParamsReadySinceMs(0L);
                logger.info("printer-debug", "runtime_state=WAIT_PARAMS_CHEST action=reopen_no_window");
                return PlaceExecResult.inProgress(0, "WAIT_PARAMS_CHEST");
            }
            if (entry.needOpenParamsChest() && now >= entry.nextParamsActionMs()) {
                boolean trappedChestReady = hasTrappedChestNearTarget(bridge);
                if (!trappedChestReady) {
                    entry.setAwaitingParamsChest(false);
                    entry.setNeedOpenParamsChest(false);
                    entry.setParamsOpenAttempts(0);
                    entry.setParamsStartMs(0L);
                    entry.setNextParamsActionMs(0L);
                    entry.setParamsReadyWindowId(-1);
                    entry.setParamsReadySinceMs(0L);
                    entry.setAwaitingArgs(false);
                    entry.setArgsWindowId(-1);
                    entry.setAwaitingMenu(true);
                    entry.setNeedOpenMenu(true);
                    entry.setMenuStartMs(now);
                    entry.setMenuRetrySinceMs(now);
                    entry.setLastOpenAttemptMs(0L);
                    entry.setNextMenuActionMs(now + Math.max(MENU_NEXT_ACTION_MIN_GAP_MS, delay));
                    bridge.closeScreen();
                    logger.info("printer-debug",
                        "runtime_state=WAIT_PARAMS_CHEST action=return_to_menu_no_trapped_chest anchor=" + describeAnchor(bridge));
                    return PlaceExecResult.inProgress(0, "OPEN_MENU");
                }
                if (!ensureCursorClear(entry, bridge, now)) {
                    if (isCursorTimeout(entry, now)) {
                        return fail(logger, "CURSOR_NOT_EMPTY", "cursor not empty during params reopen");
                    }
                    return PlaceExecResult.inProgress(0, "CURSOR_WAIT");
                }
                boolean opened = tryOpenParamsTarget(bridge, logger);
                entry.setParamsOpenAttempts(entry.paramsOpenAttempts() + 1);
                entry.setNextParamsActionMs(now + Math.max(120, delay));
                entry.setParamsReadyWindowId(-1);
                entry.setParamsReadySinceMs(0L);
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
                if (entry.lastMenuWindowId() >= 0) {
                    entry.setLastMenuWindowId(-1);
                    if (verboseTrace) {
                        logger.info("printer-debug", "menu_snapshot window_closed resetLastMenuWindow=1");
                    }
                }
                if (verboseTrace) {
                    logger.info("printer-debug",
                        "menu_snapshot window=-1 attempts=" + entry.menuOpenAttempts()
                            + " needOpenMenu=" + entry.needOpenMenu()
                            + " menuStartMs=" + entry.menuStartMs());
                }
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
                    entry.setNextMenuActionMs(now + Math.max(MENU_NEXT_ACTION_MIN_GAP_MS, delay));
                }
                if (entry.needOpenMenu() && now >= entry.nextMenuActionMs()) {
                    if (!ensureCursorClear(entry, bridge, now)) {
                        if (isCursorTimeout(entry, now)) {
                            return fail(logger, "CURSOR_NOT_EMPTY", "cursor not empty during menu open");
                        }
                        return PlaceExecResult.inProgress(0, "MENU_CURSOR_WAIT");
                    }
                    if (!hasSignAtMenuAnchor(bridge)) {
                        entry.setMenuOpenAttempts(entry.menuOpenAttempts() + 1);
                        if (!startMenuForceReplace(entry, now, delay, logger, "menu_sign_missing")) {
                            return fail(logger, "MENU_SIGN_NOT_FOUND", "menu sign missing near runtime entry");
                        }
                        logger.info("printer-debug", "runtime_state=FORCE_REPLACE reason=menu_sign_missing");
                        return PlaceExecResult.inProgress(0, "FORCE_REPLACE");
                    }
                    int openAttempt = entry.menuOpenAttempts() + 1;
                    boolean opened = tryOpenMenuTarget(bridge, logger);
                    entry.setNeedOpenMenu(false);
                    entry.setMenuRetrySinceMs(now);
                    entry.setLastOpenAttemptMs(now);
                    entry.setNextMenuActionMs(now + Math.max(MENU_NEXT_ACTION_MIN_GAP_MS, delay));
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
            if (verboseTrace) {
                String snapshotSummary = verboseMenuSnapshot ? summarizeNonPlayerSlots(view, 10) : "-";
                logger.info("printer-debug",
                    "menu_snapshot window=" + view.windowId()
                        + " nonPlayerSlots=" + countNonPlayerSlots(view)
                        + " nonPlayerItems=" + hasNonPlayerItems(view)
                        + " hash=" + buildNonPlayerHash(view)
                        + " summary=" + snapshotSummary);
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
                rememberLearnedMenuFromWindow(entry, view);
                entry.setTriedWindowId(view.windowId());
                entry.setMenuClicksSinceOpen(0);
                entry.clearTriedMenuSlots();
            }
            if (entry.lastMenuWindowId() == view.windowId()
                && entry.lastMenuClickMs() > 0L
                && now - entry.lastMenuClickMs() < Math.max(MENU_CLICK_MIN_GAP_MS, delay)) {
                return PlaceExecResult.inProgress(0, "MENU_ACTION_GAP");
            }
            if (now < entry.nextMenuActionMs()) {
                return PlaceExecResult.inProgress(0, "MENU_ACTION_GAP");
            }

            NameRoute route = parseNameRoute(entry.name(), menuRouteResolver.resolvePrimaryMenuKey(entry));
            logger.info("printer-debug",
                "menu_route raw=" + safe(entry.name())
                    + " base=" + route.baseKey
                    + " scope=" + route.scopeKey);
            if (!route.scopeKey.isEmpty() && entry.menuClicksSinceOpen() == 0) {
                if (!ensureCursorClear(entry, bridge, now)) {
                    if (isCursorTimeout(entry, now)) {
                        return fail(logger, "CURSOR_NOT_EMPTY", "cursor not empty during menu scope click");
                    }
                    return PlaceExecResult.inProgress(0, "MENU_CURSOR_WAIT");
                }
                int scopeSlot = findSlotByKey(view, route.scopeKey, true);
                if (scopeSlot < 0) {
                    return fail(logger, "SCOPE_MENU_NOT_FOUND", "scope menu not found: " + route.scopeKey);
                }
                ClickResult scopeClick = clickSlotWithTrace(
                    bridge, view, view.windowId(), scopeSlot, 0, "PICKUP", logger, "menu_scope");
                if (!scopeClick.accepted()) {
                    return fail(logger, "SCOPE_MENU_CLICK_FAILED", scopeClick.reason());
                }
                entry.setMenuClicksSinceOpen(1);
                entry.markTriedMenuSlot(scopeSlot);
                entry.setLastMenuClickedKey(menuSlotKey(view, scopeSlot));
                entry.setLastMenuWindowId(view.windowId());
                entry.setLastMenuClickMs(now);
                // Reset scope wait timeout from the actual scope click moment.
                entry.setMenuStartMs(now);
                entry.setNextMenuActionMs(now + Math.max(MENU_NEXT_ACTION_MIN_GAP_MS, delay));
                entry.setMenuRouteSameHashMisses(0);
                entry.setMenuRouteLastHash("");
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
            int slot = findSlotByAnyKey(view, routeKey, true);
            if (slot < 0) {
                int learned = findSlotByLearnedMenuHints(view, routeKey);
                if (learned >= 0) {
                    slot = learned;
                    logger.info("printer-debug", "menu_route_learned_fallback key=" + routeKey + " slot=" + slot);
                }
            }
            if (slot < 0) {
                String menuHash = buildNonPlayerHash(view);
                if (!menuHash.isEmpty()) {
                    if (menuHash.equals(entry.menuRouteLastHash())) {
                        entry.setMenuRouteSameHashMisses(entry.menuRouteSameHashMisses() + 1);
                    } else {
                        entry.setMenuRouteLastHash(menuHash);
                        entry.setMenuRouteSameHashMisses(1);
                    }
                }
                boolean detailedMissLog = verboseTrace && (entry.randomClicks() % 8 == 0);
                if (detailedMissLog) {
                    logger.info("printer-debug",
                        "menu_route_miss key=" + routeKey
                            + " normalized=" + norm(routeKey)
                            + " summary=" + summarizeNonPlayerSlots(view, 20));
                } else {
                    logger.info("printer-debug",
                        "menu_route_miss key=" + routeKey
                            + " normalized=" + norm(routeKey));
                }
                if (!route.scopeKey.isEmpty()) {
                    return fail(logger, "SCOPE_TARGET_NOT_FOUND", "menu key not found: " + routeKey);
                }
                if (entry.menuRouteSameHashMisses() > MAX_RANDOM_SAME_HASH_MISSES) {
                    int stagnantMisses = entry.menuRouteSameHashMisses();
                    if (now - entry.menuStartMs() < MENU_TIMEOUT_MS) {
                        entry.setNeedOpenMenu(true);
                        entry.setMenuRetrySinceMs(now);
                        entry.setNextMenuActionMs(now + Math.max(MENU_NEXT_ACTION_MIN_GAP_MS, delay));
                        entry.setMenuClicksSinceOpen(0);
                        entry.setMenuRouteSameHashMisses(0);
                        entry.setMenuRouteLastHash("");
                        logger.info("printer-debug",
                            "runtime_state=ROUTE_MENU_STAGNANT reopen=1 misses=" + stagnantMisses);
                        return PlaceExecResult.inProgress(0, "WAIT_MENU_ROUTE_REOPEN");
                    }
                    return fail(logger, "NO_PATH_GUI",
                        "menu route stagnant hash misses=" + stagnantMisses + " key=" + routeKey);
                }
                if (entry.randomClicks() >= MAX_RANDOM_ROUTE_CLICKS) {
                    return fail(logger, "RANDOM_EXHAUSTED", "random search exhausted");
                }
                int randomSlot = findRandomMenuSlot(view, entry);
                if (randomSlot < 0) {
                    // Legacy parity: if no clicks were made in this menu window and no route/random slot exists,
                    // this is a hard dead-end. Otherwise, close/reopen and continue search.
                    if (entry.menuClicksSinceOpen() == 0) {
                        return fail(logger, "NO_PATH_GUI", "menu key not found and random path unavailable: " + routeKey);
                    }
                    bridge.closeScreen();
                    entry.setNeedOpenMenu(true);
                    entry.setMenuRetrySinceMs(now);
                    entry.setNextMenuActionMs(now + Math.max(MENU_NEXT_ACTION_MIN_GAP_MS, delay));
                    entry.setMenuClicksSinceOpen(0);
                    entry.setTriedWindowId(-1);
                    entry.setMenuNonEmptySinceMs(0L);
                    entry.setMenuNonEmptyWindowId(-1);
                    entry.clearTriedMenuSlots();
                    return PlaceExecResult.inProgress(0, "WAIT_MENU_ROUTE");
                }
                if (!ensureCursorClear(entry, bridge, now)) {
                    if (isCursorTimeout(entry, now)) {
                        return fail(logger, "CURSOR_NOT_EMPTY", "cursor not empty during random menu click");
                    }
                    return PlaceExecResult.inProgress(0, "MENU_CURSOR_WAIT");
                }
                ClickResult rndClick = clickSlotWithTrace(
                    bridge, view, view.windowId(), randomSlot, 0, "PICKUP", logger, "menu_random");
                if (!rndClick.accepted()) {
                    return fail(logger, "MENU_CLICK_FAILED", rndClick.reason());
                }
                entry.setRandomClicks(entry.randomClicks() + 1);
                entry.setMenuClicksSinceOpen(entry.menuClicksSinceOpen() + 1);
                entry.markTriedMenuSlot(randomSlot);
                entry.setLastMenuClickedKey(menuSlotKey(view, randomSlot));
                entry.setLastMenuWindowId(view.windowId());
                entry.setLastMenuClickMs(now);
                entry.setMenuStartMs(now);
                entry.setNextMenuActionMs(now + Math.max(delay, RANDOM_ROUTE_MIN_GAP_MS));
                logger.info("printer-debug",
                    "runtime_state=ROUTE_MENU_RANDOM slot=" + randomSlot + " randomClicks=" + entry.randomClicks());
                return PlaceExecResult.inProgress(0, "ROUTE_MENU_RANDOM");
            }
            if (!ensureCursorClear(entry, bridge, now)) {
                if (isCursorTimeout(entry, now)) {
                    return fail(logger, "CURSOR_NOT_EMPTY", "cursor not empty during menu target click");
                }
                return PlaceExecResult.inProgress(0, "MENU_CURSOR_WAIT");
            }
            ClickResult click = clickSlotWithTrace(
                bridge, view, view.windowId(), slot, 0, "PICKUP", logger, "menu_target");
            if (!click.accepted()) {
                return fail(logger, "MENU_CLICK_FAILED", click.reason());
            }
            entry.markTriedMenuSlot(slot);
            entry.setLastMenuClickedKey(menuSlotKey(view, slot));
            entry.setMenuRouteSameHashMisses(0);
            entry.setMenuRouteLastHash("");
            if (!hasAdvancedArgs(entry)) {
                entry.setAwaitingMenu(false);
                entry.setAwaitingParamsChest(false);
                entry.setNeedOpenParamsChest(false);
                entry.setAwaitingArgs(false);
                entry.setArgsWindowId(-1);
                entry.setParamsReadyWindowId(-1);
                entry.setParamsReadySinceMs(0L);
                entry.setLastMenuWindowId(view.windowId());
                entry.setLastMenuClickMs(now);
                entry.setMenuClicksSinceOpen(0);
                logger.info("printer-debug", "runtime_state=SKIP_PARAMS_NO_ARGS");
                return finishOrPostPlace(entry, bridge, logger, now, delay);
            }
            entry.setAwaitingMenu(false);
            entry.setAwaitingParamsChest(true);
            entry.setParamsStartMs(now);
            entry.setNextParamsActionMs(now + delay);
            entry.setParamsOpenAttempts(0);
            entry.setNeedOpenParamsChest(false);
            entry.setParamsReadyWindowId(-1);
            entry.setParamsReadySinceMs(0L);
            entry.setArgsWindowId(-1);
            entry.setLastMenuWindowId(view.windowId());
            entry.setLastMenuClickMs(now);
            entry.setMenuClicksSinceOpen(0);
            entry.setNextMenuActionMs(now + Math.max(MENU_NEXT_ACTION_MIN_GAP_MS, delay));
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
                    int prevWindowId = entry.argsWindowId();
                    if (entry.pendingArgClicks() == 0 && entry.advancedArgIndex() == 0 && entry.argsMisses() == 0) {
                        logger.info("printer-debug",
                            "runtime_state=ARGS_WINDOW_REBIND from=" + prevWindowId + " to=" + view.windowId());
                        entry.setArgsWindowId(view.windowId());
                        entry.setArgsMisses(1);
                        return PlaceExecResult.inProgress(0, "ARGS_WINDOW_REBIND");
                    }
                    if (entry.argsMisses() < MAX_ARG_MISSES) {
                        entry.setArgsMisses(entry.argsMisses() + 1);
                        entry.setPendingArgClickSlot(-1);
                        entry.setPendingArgClicks(0);
                        entry.setPendingArgNextMs(now + Math.max(120, delay));
                        entry.setArgsWindowId(view.windowId());
                        logger.info("printer-debug",
                            "runtime_state=ARGS_PENDING_RECOVER reason=window_changed from=" + prevWindowId + " to=" + view.windowId());
                        return PlaceExecResult.inProgress(0, "ARGS_PENDING_RECOVER");
                    }
                    return fail(logger, "ARGS_WINDOW_CHANGED", "args window changed during pending clicks");
                }
                if (countNonPlayerSlots(view) <= 0) {
                    entry.setPendingArgNextMs(now + Math.max(80, delay));
                    return PlaceExecResult.inProgress(0, "ARGS_CONTAINER_EMPTY");
                }
                if (!hasNonPlayerSlotNumber(view, entry.pendingArgClickSlot())) {
                    if (entry.argsMisses() < MAX_ARG_MISSES) {
                        int missingSlot = entry.pendingArgClickSlot();
                        entry.setArgsMisses(entry.argsMisses() + 1);
                        entry.setPendingArgClickSlot(-1);
                        entry.setPendingArgClicks(0);
                        entry.setPendingArgNextMs(now + Math.max(120, delay));
                        logger.info("printer-debug",
                            "runtime_state=ARGS_PENDING_RECOVER reason=slot_missing slot=" + missingSlot
                                + " misses=" + entry.argsMisses());
                        return PlaceExecResult.inProgress(0, "ARGS_PENDING_RECOVER");
                    }
                    return fail(logger, "ARGS_PENDING_SLOT_MISSING",
                        "pending slot disappeared: " + entry.pendingArgClickSlot());
                }
                if (!ensureCursorClear(entry, bridge, now)) {
                    if (isCursorTimeout(entry, now)) {
                        return fail(logger, "CURSOR_NOT_EMPTY", "cursor not empty (5s)");
                    }
                    return PlaceExecResult.inProgress(0, "CURSOR_WAIT");
                }
                ClickResult click = clickSlotWithTrace(
                    bridge, view, view.windowId(), entry.pendingArgClickSlot(), 0, "PICKUP", logger, "args_pending");
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
                return finishOrPostPlace(entry, bridge, logger, now, delay);
            }

            ContainerView view = bridge.getContainerSnapshot();
            if (view.windowId() < 0) {
                return fail(logger, "ARGS_CONTAINER_CLOSED", "container closed while args pending");
            }
            if (entry.argsWindowId() < 0) {
                entry.setArgsWindowId(view.windowId());
            } else if (view.windowId() != entry.argsWindowId()) {
                if (entry.advancedArgIndex() == 0 && entry.argsMisses() == 0) {
                    logger.info("printer-debug",
                        "runtime_state=ARGS_WINDOW_REBIND from=" + entry.argsWindowId() + " to=" + view.windowId());
                    entry.setArgsWindowId(view.windowId());
                    entry.setArgsMisses(1);
                    return PlaceExecResult.inProgress(0, "ARGS_WINDOW_REBIND");
                }
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
                    logger.info("printer-debug",
                        "runtime_state=ARGS_PAGE_ROUTE_SKIP reason=" + safe(route.reason)
                            + " key=" + safe(arg.keyRaw())
                            + " idx=" + entry.advancedArgIndex());
                    entry.setAdvancedArgIndex(entry.advancedArgIndex() + 1);
                    entry.setLastArgsActionMs(now);
                    return PlaceExecResult.inProgress(0, "ARGS_PAGE_ROUTE_SKIP");
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
                ClickResult c1 = clickSlotWithTrace(
                    bridge, view, view.windowId(), hotbarContainerSlot, 0, "PICKUP", logger, "args_input_pick_hotbar");
                if (!c1.accepted()) {
                    return fail(logger, "ITEM_CLICK_FAILED", c1.reason());
                }
                ClickResult c2 = clickSlotWithTrace(
                    bridge, view, view.windowId(), targetSlot, 0, "PICKUP", logger, "args_input_place_target");
                if (!c2.accepted()) {
                    return fail(logger, "ITEM_CLICK_FAILED", c2.reason());
                }
                CursorState cursorAfterTarget = bridge.getCursorStack();
                if (!cursorAfterTarget.isEmpty()) {
                    ClickResult c3 = clickSlotWithTrace(
                        bridge, view, view.windowId(), hotbarContainerSlot, 0, "PICKUP", logger, "args_input_restore_hotbar");
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
            ClickResult c1 = clickSlotWithTrace(
                bridge, view, view.windowId(), hotbarContainerSlot, 0, "PICKUP", logger, "args_item_pick_hotbar");
            if (!c1.accepted()) {
                return fail(logger, "INPUT_CLICK_FAILED", c1.reason());
            }
            ClickResult c2 = clickSlotWithTrace(
                bridge, view, view.windowId(), targetSlot, 0, "PICKUP", logger, "args_item_place_target");
            if (!c2.accepted()) {
                return fail(logger, "INPUT_CLICK_FAILED", c2.reason());
            }
            CursorState cursor = bridge.getCursorStack();
            if (!cursor.isEmpty()) {
                ClickResult c3 = clickSlotWithTrace(
                    bridge, view, view.windowId(), hotbarContainerSlot, 0, "PICKUP", logger, "args_item_restore_hotbar");
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

        return finishOrPostPlace(entry, bridge, logger, now, delay);
    }

    private static boolean shouldEmitVerboseTrace(long now) {
        if (now <= 0L) {
            return true;
        }
        synchronized (PlaceRuntimeStepExecutor.class) {
            if (now - lastVerboseTraceMs < VERBOSE_TRACE_GAP_MS) {
                return false;
            }
            lastVerboseTraceMs = now;
            return true;
        }
    }

    private static void logStepTrace(CoreLogger logger, PlaceRuntimeEntry entry, boolean hasMenuPayload, long now) {
        if (logger == null || entry == null) {
            return;
        }
        String phase = resolvePhase(entry, hasMenuPayload);
        int argIdx = entry.advancedArgIndex();
        String argMode = "";
        String argKey = "";
        int argClicks = 0;
        boolean argClickOnly = false;
        if (entry.awaitingArgs() && entry.args() != null && argIdx >= 0 && argIdx < entry.args().size()) {
            PlaceArgSpec arg = entry.args().get(argIdx);
            if (arg != null) {
                argMode = inputModeName(arg.mode());
                argKey = safe(arg.keyRaw());
                argClicks = arg.clicks();
                argClickOnly = arg.clickOnly();
            }
        }
        logger.info("printer-debug",
            "step_trace ts=" + now
                + " phase=" + phase
                + " block=" + safe(entry.blockId())
                + " name=" + safe(entry.name())
                + " flags={place=" + (!entry.placedBlock())
                + ",menu=" + entry.awaitingMenu()
                + ",params=" + entry.awaitingParamsChest()
                + ",args=" + entry.awaitingArgs()
                + ",postPlace=" + (entry.postPlaceKind() != PlaceRuntimeEntry.POST_PLACE_NONE)
                + ",negate=" + (entry.postPlaceKind() == PlaceRuntimeEntry.POST_PLACE_NEGATE || entry.negated())
                + ",needOpenMenu=" + entry.needOpenMenu()
                + ",needOpenParams=" + entry.needOpenParamsChest()
                + ",forceRePlace=" + entry.forceRePlaceRequested()
                + ",moveOnly=" + entry.moveOnly()
                + "}"
                + " windows={menuLast=" + entry.lastMenuWindowId()
                + ",args=" + entry.argsWindowId()
                + ",paramsReady=" + entry.paramsReadyWindowId()
                + "}"
                + " counts={menuAttempts=" + entry.menuOpenAttempts()
                + ",paramsAttempts=" + entry.paramsOpenAttempts()
                + ",randomClicks=" + entry.randomClicks()
                + ",argIdx=" + argIdx
                + ",pendingArgSlot=" + entry.pendingArgClickSlot()
                + ",pendingArgClicks=" + entry.pendingArgClicks()
                + "}"
                + " arg={mode=" + argMode
                + ",key=" + argKey
                + ",clicks=" + argClicks
                + ",clickOnly=" + argClickOnly
                + "}");
    }

    private static String resolvePhase(PlaceRuntimeEntry entry, boolean hasMenuPayload) {
        if (entry == null) {
            return "UNKNOWN";
        }
        if (!hasMenuPayload) {
            return "PLACE_ONLY";
        }
        if (!entry.placedBlock()) {
            return "PLACE_BLOCK";
        }
        if (entry.awaitingMenu()) {
            return "FIND_ACTION_SIGN_ROUTE_MENU";
        }
        if (entry.awaitingParamsChest()) {
            return "FILLING_CHEST_OPEN_PARAMS";
        }
        if (entry.awaitingArgs()) {
            return "FILLING_CHEST_APPLY_ARGS";
        }
        if (entry.postPlaceKind() != PlaceRuntimeEntry.POST_PLACE_NONE) {
            return "POST_PLACE";
        }
        return "FINISH_STEP";
    }

    private static String inputModeName(int mode) {
        if (mode == PlaceInputMode.TEXT) {
            return "TEXT";
        }
        if (mode == PlaceInputMode.NUMBER) {
            return "NUMBER";
        }
        if (mode == PlaceInputMode.VARIABLE) {
            return "VARIABLE";
        }
        if (mode == PlaceInputMode.ARRAY) {
            return "ARRAY";
        }
        if (mode == PlaceInputMode.LOCATION) {
            return "LOCATION";
        }
        if (mode == PlaceInputMode.APPLE) {
            return "APPLE";
        }
        if (mode == PlaceInputMode.ITEM) {
            return "ITEM";
        }
        return "UNKNOWN(" + mode + ")";
    }

    private static PlaceExecResult finishOrPostPlace(
        PlaceRuntimeEntry entry,
        GameBridge bridge,
        CoreLogger logger,
        long now,
        int delay
    ) {
        if (entry == null) {
            return PlaceExecResult.ok(1);
        }
        if (entry.postPlaceKind() == PlaceRuntimeEntry.POST_PLACE_NONE && entry.negated()) {
            entry.setPostPlaceKind(PlaceRuntimeEntry.POST_PLACE_NEGATE);
            entry.setPostPlaceStage(0);
            entry.setPostPlaceNextMs(now + Math.max(POST_PLACE_NEGATE_DELAY_MS, delay));
            if (logger != null) {
                logger.info("printer-debug", "runtime_state=POST_PLACE_START kind=" + entry.postPlaceKind() + " reason=negated_after_gui");
            }
            return PlaceExecResult.inProgress(0, "POST_PLACE");
        }
        if (entry.postPlaceKind() == PlaceRuntimeEntry.POST_PLACE_NONE) {
            return PlaceExecResult.ok(1);
        }
        if (entry.postPlaceNextMs() <= 0L) {
            entry.setPostPlaceNextMs(now + Math.max(POST_PLACE_START_DELAY_MS, delay));
        }
        return executePostPlace(entry, bridge, logger, now, delay);
    }

    private static PlaceExecResult executePostPlace(
        PlaceRuntimeEntry entry,
        GameBridge bridge,
        CoreLogger logger,
        long now,
        int delay
    ) {
        if (entry == null || entry.postPlaceKind() == PlaceRuntimeEntry.POST_PLACE_NONE) {
            return PlaceExecResult.ok(1);
        }
        if (entry.postPlaceNextMs() > 0L && now < entry.postPlaceNextMs()) {
            return PlaceExecResult.inProgress(0, "POST_PLACE_WAIT");
        }
        if (!ensureCursorClear(entry, bridge, now)) {
            if (isCursorTimeout(entry, now)) {
                return fail(logger, "CURSOR_NOT_EMPTY", "cursor not empty during postPlace");
            }
            return PlaceExecResult.inProgress(0, "POST_PLACE_CURSOR_WAIT");
        }
        BlockPosView target = resolvePostPlaceTarget(bridge, logger);
        if (target == null) {
            return fail(logger, "POST_PLACE_SIGN_NOT_FOUND", "postPlace target sign is missing");
        }
        if (logger != null) {
            logger.info("printer-debug",
                "post_place_target kind=" + entry.postPlaceKind()
                    + " stage=" + entry.postPlaceStage()
                    + " target=" + target.x() + "," + target.y() + "," + target.z());
        }

        int kind = entry.postPlaceKind();
        int stage = entry.postPlaceStage();
        if (kind == PlaceRuntimeEntry.POST_PLACE_SIGN_NAME) {
            if (stage <= 0) {
                String value = safe(entry.postPlaceName()).trim();
                if (value.isEmpty()) {
                    return fail(logger, "POST_PLACE_NAME_EMPTY", "postPlace name is empty");
                }
                int hotbarIdx = chooseHotbarInjectionIndex(bridge.getContainerSnapshot());
                String display = displayValueForMode(PlaceInputMode.TEXT, value);
                String nbt = nbtForMode(PlaceInputMode.TEXT, display, false);
                if (!bridge.injectCreativeSlot(hotbarIdx, templateItemIdForMode(PlaceInputMode.TEXT), nbt, display)) {
                    return fail(logger, "POST_PLACE_INJECT_FAILED", "cannot inject text input item");
                }
                if (!bridge.selectHotbarSlot(hotbarIdx)) {
                    return fail(logger, "POST_PLACE_SELECT_FAILED", "cannot select postPlace hotbar slot");
                }
                if (!clickPostPlaceTarget(bridge, target, "post_place_sign_name", logger)) {
                    return fail(logger, "POST_PLACE_CLICK_FAILED", "postPlace sign-name click rejected");
                }
                entry.setPostPlaceStage(1);
                entry.setPostPlaceNextMs(now + Math.max(POST_PLACE_STEP_DELAY_MS, delay));
                logger.info("printer-debug", "runtime_state=POST_PLACE kind=sign_name stage=1");
                return PlaceExecResult.inProgress(0, "POST_PLACE");
            }
            if (entry.negated()) {
                entry.setPostPlaceKind(PlaceRuntimeEntry.POST_PLACE_NEGATE);
                entry.setPostPlaceStage(0);
                entry.setPostPlaceNextMs(now + Math.max(POST_PLACE_NEGATE_DELAY_MS, delay));
                logger.info("printer-debug", "runtime_state=POST_PLACE_START kind=" + entry.postPlaceKind() + " reason=negated_after_sign_name");
                return PlaceExecResult.inProgress(0, "POST_PLACE");
            }
            clearPostPlaceState(entry);
            return PlaceExecResult.ok(1);
        }

        if (kind == PlaceRuntimeEntry.POST_PLACE_CYCLE) {
            if (stage <= 0) {
                String value = safe(entry.postPlaceName()).trim();
                if (value.isEmpty()) {
                    return fail(logger, "POST_PLACE_NAME_EMPTY", "postPlace cycle name is empty");
                }
                int hotbarIdx = chooseHotbarInjectionIndex(bridge.getContainerSnapshot());
                String display = displayValueForMode(PlaceInputMode.TEXT, value);
                String nbt = nbtForMode(PlaceInputMode.TEXT, display, false);
                if (!bridge.injectCreativeSlot(hotbarIdx, templateItemIdForMode(PlaceInputMode.TEXT), nbt, display)) {
                    return fail(logger, "POST_PLACE_INJECT_FAILED", "cannot inject cycle-name input item");
                }
                if (!bridge.selectHotbarSlot(hotbarIdx)) {
                    return fail(logger, "POST_PLACE_SELECT_FAILED", "cannot select postPlace cycle-name slot");
                }
                if (!clickPostPlaceTarget(bridge, target, "post_place_cycle_name", logger)) {
                    return fail(logger, "POST_PLACE_CLICK_FAILED", "postPlace cycle-name click rejected");
                }
                entry.setPostPlaceStage(1);
                entry.setPostPlaceNextMs(now + Math.max(POST_PLACE_STEP_DELAY_MS, delay));
                logger.info("printer-debug", "runtime_state=POST_PLACE kind=cycle stage=1");
                return PlaceExecResult.inProgress(0, "POST_PLACE");
            }
            if (stage == 1) {
                int ticks = Math.max(5, entry.postPlaceCycleTicks());
                int hotbarIdx = chooseHotbarInjectionIndex(bridge.getContainerSnapshot());
                String display = displayValueForMode(PlaceInputMode.NUMBER, String.valueOf(ticks));
                String nbt = nbtForMode(PlaceInputMode.NUMBER, display, false);
                if (!bridge.injectCreativeSlot(hotbarIdx, templateItemIdForMode(PlaceInputMode.NUMBER), nbt, display)) {
                    return fail(logger, "POST_PLACE_INJECT_FAILED", "cannot inject cycle-ticks input item");
                }
                if (!bridge.selectHotbarSlot(hotbarIdx)) {
                    return fail(logger, "POST_PLACE_SELECT_FAILED", "cannot select postPlace cycle slot");
                }
                if (!clickPostPlaceTarget(bridge, target, "post_place_cycle_ticks", logger)) {
                    return fail(logger, "POST_PLACE_CLICK_FAILED", "postPlace cycle-ticks click rejected");
                }
                entry.setPostPlaceStage(2);
                entry.setPostPlaceNextMs(now + Math.max(POST_PLACE_STEP_DELAY_MS, delay));
                logger.info("printer-debug", "runtime_state=POST_PLACE kind=cycle stage=2 ticks=" + ticks);
                return PlaceExecResult.inProgress(0, "POST_PLACE");
            }
            if (entry.negated()) {
                entry.setPostPlaceKind(PlaceRuntimeEntry.POST_PLACE_NEGATE);
                entry.setPostPlaceStage(0);
                entry.setPostPlaceNextMs(now + Math.max(POST_PLACE_NEGATE_DELAY_MS, delay));
                logger.info("printer-debug", "runtime_state=POST_PLACE_START kind=" + entry.postPlaceKind() + " reason=negated_after_cycle");
                return PlaceExecResult.inProgress(0, "POST_PLACE");
            }
            clearPostPlaceState(entry);
            return PlaceExecResult.ok(1);
        }

        if (kind == PlaceRuntimeEntry.POST_PLACE_NEGATE) {
            if (stage <= 0) {
                int hotbarIdx = chooseHotbarInjectionIndex(bridge.getContainerSnapshot());
                if (!bridge.injectCreativeSlot(hotbarIdx, "minecraft:arrow", "", "")) {
                    return fail(logger, "POST_PLACE_INJECT_FAILED", "cannot inject negate arrow");
                }
                if (!bridge.selectHotbarSlot(hotbarIdx)) {
                    return fail(logger, "POST_PLACE_SELECT_FAILED", "cannot select negate arrow slot");
                }
                if (!clickPostPlaceTarget(bridge, target, "post_place_negate", logger)) {
                    return fail(logger, "POST_PLACE_CLICK_FAILED", "postPlace negate click rejected");
                }
                entry.setPostPlaceStage(1);
                entry.setPostPlaceNextMs(now + Math.max(POST_PLACE_STEP_DELAY_MS, delay));
                logger.info("printer-debug", "runtime_state=POST_PLACE kind=negate stage=1");
                return PlaceExecResult.inProgress(0, "POST_PLACE");
            }
            clearPostPlaceState(entry);
            return PlaceExecResult.ok(1);
        }

        clearPostPlaceState(entry);
        return PlaceExecResult.ok(1);
    }

    private static BlockPosView resolvePostPlaceTarget(GameBridge bridge, CoreLogger logger) {
        if (bridge == null) {
            return null;
        }
        BlockPosView anchor = bridge.getRuntimeEntryAnchor();
        if (anchor == null) {
            return null;
        }
        for (int dy = 1; dy >= -3; dy--) {
            int x = anchor.x();
            int y = anchor.y() + dy;
            int z = anchor.z() - 1;
            if (bridge.isSignAt(x, y, z)) {
                if (logger != null) {
                    logger.info("printer-debug", "post_place_sign_found at=" + x + "," + y + "," + z + " dy=" + dy);
                }
                return new BlockPosView(x, y, z);
            }
        }
        if (logger != null) {
            logger.info("printer-debug",
                "post_place_sign_missing fallback_to_anchor anchor="
                    + anchor.x() + "," + anchor.y() + "," + anchor.z());
        }
        return new BlockPosView(anchor.x(), anchor.y(), anchor.z());
    }

    private static boolean clickPostPlaceTarget(GameBridge bridge, BlockPosView target, String purpose, CoreLogger logger) {
        if (bridge == null || target == null) {
            return false;
        }
        int[][] points = buildPostPlaceClickPoints(bridge, target);
        for (int i = 0; i < points.length; i++) {
            int x = points[i][0];
            int y = points[i][1];
            int z = points[i][2];
            ClickResult legacy = bridge.clickBlockLegacy(x, y, z, purpose, true);
            if (logger != null) {
                logger.info("printer-debug",
                    "post_place_click probe=legacy idx=" + i
                        + " target=" + x + "," + y + "," + z
                        + " accepted=" + (legacy != null && legacy.accepted())
                        + " reason=" + (legacy == null ? "null" : safe(legacy.reason())));
            }
            if (legacy != null && legacy.accepted()) {
                return true;
            }
            ClickResult packet = bridge.sendUseItemOnBlock(x, y, z);
            if (logger != null) {
                logger.info("printer-debug",
                    "post_place_click probe=packet idx=" + i
                        + " target=" + x + "," + y + "," + z
                        + " accepted=" + (packet != null && packet.accepted())
                        + " reason=" + (packet == null ? "null" : safe(packet.reason())));
            }
            if (packet != null && packet.accepted()) {
                return true;
            }
            ClickResult interact = bridge.interactBlock(x, y, z);
            if (logger != null) {
                logger.info("printer-debug",
                    "post_place_click probe=interact idx=" + i
                        + " target=" + x + "," + y + "," + z
                        + " accepted=" + (interact != null && interact.accepted())
                        + " reason=" + (interact == null ? "null" : safe(interact.reason())));
            }
            if (interact != null && interact.accepted()) {
                return true;
            }
        }
        return false;
    }

    private static int[][] buildPostPlaceClickPoints(GameBridge bridge, BlockPosView target) {
        java.util.ArrayList<int[]> pts = new java.util.ArrayList<int[]>();
        pts.add(new int[] {target.x(), target.y(), target.z()});
        pts.add(new int[] {target.x(), target.y() + 1, target.z()});
        pts.add(new int[] {target.x(), target.y() - 1, target.z()});
        BlockPosView anchor = bridge == null ? null : bridge.getRuntimeEntryAnchor();
        if (anchor != null) {
            for (int dy = 1; dy >= -3; dy--) {
                pts.add(new int[] {anchor.x(), anchor.y() + dy, anchor.z() - 1});
            }
            pts.add(new int[] {anchor.x(), anchor.y(), anchor.z()});
        }
        int[][] out = new int[pts.size()][3];
        for (int i = 0; i < pts.size(); i++) {
            out[i] = pts.get(i);
        }
        return out;
    }

    private static void clearPostPlaceState(PlaceRuntimeEntry entry) {
        if (entry == null) {
            return;
        }
        entry.setPostPlaceKind(PlaceRuntimeEntry.POST_PLACE_NONE);
        entry.setPostPlaceName("");
        entry.setPostPlaceCycleTicks(-1);
        entry.setPostPlaceStage(0);
        entry.setPostPlaceNextMs(0L);
    }

    private static boolean hasAdvancedArgs(PlaceRuntimeEntry entry) {
        return entry != null && entry.args() != null && !entry.args().isEmpty();
    }

    private static PlaceExecResult fail(CoreLogger logger, String code, String detail) {
        String safeCode = code == null ? "UNKNOWN" : code;
        String safeDetail = detail == null ? "" : detail.replace('\n', ' ').replace('\r', ' ');
        if (logger != null) {
            logger.info("printer-debug", "runtime_state=FAILED reason=" + safeCode + " detail=" + safeDetail);
        }
        return PlaceExecResult.fail(0, 0, safeCode, safeDetail);
    }

    private static boolean tryOpenMenuTarget(GameBridge bridge, CoreLogger logger) {
        if (bridge == null) {
            return false;
        }
        // Strict legacy path: sign (z-1 from entry) -> entry block; no generic fallback.
        boolean opened = bridge.openMenuAtEntryAnchor();
        if (logger != null) {
            logger.info("printer-debug", "menu_open_target opened=" + opened + " anchor=" + describeAnchor(bridge));
        }
        return opened;
    }

    private static boolean tryOpenParamsTarget(GameBridge bridge, CoreLogger logger) {
        if (bridge == null) {
            return false;
        }
        BlockPosView anchor = bridge.getRuntimeEntryAnchor();
        if (anchor != null) {
            // Legacy parity: params click prefers sign(z-1) + (0,1,1), where sign Y may vary in [-2..0] from entry.
            for (int dy = 0; dy >= -2; dy--) {
                int x = anchor.x();
                int y = anchor.y() + dy + 1;
                int z = anchor.z();
                ClickResult legacy = bridge.clickBlockLegacy(
                    x,
                    y,
                    z,
                    "params_open_sign_offset",
                    true
                );
                if (logger != null) {
                    logger.info("printer-debug",
                        "params_open_probe dy=" + dy
                            + " target=" + x + "," + y + "," + z
                            + " accepted=" + (legacy != null && legacy.accepted())
                            + " reason=" + (legacy == null ? "null" : safe(legacy.reason())));
                }
                if (legacy != null && legacy.accepted()) {
                    return true;
                }
            }
        }
        if (logger != null) {
            logger.info("printer-debug", "params_open_probe result=not_opened anchor=" + describeAnchor(bridge));
        }
        return false;
    }

    private static ClickResult clickSlotWithTrace(
        GameBridge bridge,
        ContainerView view,
        int windowId,
        int slot,
        int button,
        String clickType,
        CoreLogger logger,
        String stage
    ) {
        SlotView slotView = findSlotByNumber(view, slot);
        if (logger != null) {
            logger.info("printer-debug",
                "gui_click stage=" + safe(stage)
                    + " window=" + windowId
                    + " slot=" + slot
                    + " playerInv=" + (slotView != null && slotView.playerInventory())
                    + " empty=" + (slotView == null || slotView.empty())
                    + " item=" + (slotView == null ? "-" : safe(slotView.itemId()))
                    + " display=" + (slotView == null ? "-" : safe(slotView.displayName())));
        }
        ClickResult click = bridge.clickSlot(windowId, slot, button, clickType);
        if (logger != null) {
            logger.info("printer-debug",
                "gui_click_result stage=" + safe(stage)
                    + " window=" + windowId
                    + " slot=" + slot
                    + " accepted=" + (click != null && click.accepted())
                    + " ack=" + (click == null ? "null" : String.valueOf(click.ackState()))
                    + " reason=" + (click == null ? "null" : safe(click.reason())));
        }
        return click;
    }

    private static SlotView findSlotByNumber(ContainerView view, int slot) {
        if (view == null || view.slots() == null) {
            return null;
        }
        for (SlotView s : view.slots()) {
            if (s != null && s.slotNumber() == slot) {
                return s;
            }
        }
        return null;
    }

    private static String describeAnchor(GameBridge bridge) {
        if (bridge == null) {
            return "null_bridge";
        }
        BlockPosView anchor = bridge.getRuntimeEntryAnchor();
        if (anchor == null) {
            return "null_anchor";
        }
        return anchor.x() + "," + anchor.y() + "," + anchor.z();
    }

    private static boolean hasSignAtMenuAnchor(GameBridge bridge) {
        if (bridge == null) {
            return false;
        }
        BlockPosView anchor = bridge.getRuntimeEntryAnchor();
        if (anchor == null) {
            return false;
        }
        for (int dy = 0; dy >= -2; dy--) {
            if (bridge.isSignAt(anchor.x(), anchor.y() + dy, anchor.z() - 1)) {
                return true;
            }
        }
        return false;
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
        entry.setParamsReadyWindowId(-1);
        entry.setParamsReadySinceMs(0L);
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
        entry.clearTriedMenuSlots();
        entry.setMenuNonEmptySinceMs(0L);
        entry.setMenuNonEmptyWindowId(-1);
        entry.setRandomClicks(0);
        entry.setNeedOpenMenu(true);
        entry.setNextMenuActionMs(now + Math.max(120, delay));
        entry.setMenuOpenAttempts(0);
        entry.setForceRePlaceRequested(true);
        entry.setPostPlaceStage(0);
        entry.setPostPlaceNextMs(0L);
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
            String combined = slotSearchText(s);
            if (combined.equals(normKey)) {
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
            String combined = slotSearchText(s);
            if (combined.contains(normKey)) {
                return s.slotNumber();
            }
        }
        return -1;
    }

    private static int findSlotByAnyKey(ContainerView view, String key, boolean skipPlayer) {
        for (String candidate : alternateMenuKeys(key)) {
            int slot = findSlotByKey(view, candidate, skipPlayer);
            if (slot >= 0) {
                return slot;
            }
            slot = findSlotByTokenMatch(view, candidate, skipPlayer);
            if (slot >= 0) {
                return slot;
            }
            slot = findSlotByNbtTokenMatch(view, candidate, skipPlayer);
            if (slot >= 0) {
                return slot;
            }
            slot = findSlotByCombinedTokenMatch(view, candidate, skipPlayer);
            if (slot >= 0) {
                return slot;
            }
        }
        return -1;
    }

    private static List<String> alternateMenuKeys(String key) {
        ArrayList<String> out = new ArrayList<String>();
        String base = norm(key);
        if (!base.isEmpty()) {
            out.add(base);
            String stripped = stripMenuNoiseTokens(base);
            if (!stripped.isEmpty() && !stripped.equals(base)) {
                out.add(stripped);
            }
            addPathSegmentAliases(out, base);
            if (base.startsWith("событие ")) {
                out.add(base.substring("событие ".length()).trim());
            }
            if (base.startsWith("действие ")) {
                out.add(base.substring("действие ".length()).trim());
            }
            if (base.startsWith("условие ")) {
                out.add(base.substring("условие ".length()).trim());
            }
            if (base.startsWith("при ")) {
                out.add(base.substring("при ".length()).trim());
            }
            if (base.contains("вход игрока")) {
                out.add("событие входа");
                out.add("событие входа игрока");
                out.add("при входе игрока");
                out.add("при входе");
                out.add("вход");
            }
            if (base.contains("событие входа")) {
                out.add("вход игрока");
                out.add("входа игрока");
                out.add("при входе игрока");
                out.add("при входе");
                out.add("вход");
            }
            if (base.contains("выдать предмет")) {
                out.add("выдать");
                out.add("предмет");
            }
            if (base.contains("запустить функцию")) {
                out.add("запустить");
                out.add("функцию");
                out.add("функция");
            }
        }
        ArrayList<String> dedup = new ArrayList<String>();
        java.util.HashSet<String> seen = new java.util.HashSet<String>();
        for (String s : out) {
            String n = norm(s);
            if (n.isEmpty() || !seen.add(n)) {
                continue;
            }
            dedup.add(n);
        }
        return dedup;
    }

    private static void addPathSegmentAliases(List<String> out, String base) {
        if (out == null || base == null) {
            return;
        }
        String[] chains = base.split(">");
        if (chains.length <= 1) {
            return;
        }
        for (String c : chains) {
            String n = norm(c);
            if (!n.isEmpty()) {
                out.add(n);
                String stripped = stripMenuNoiseTokens(n);
                if (!stripped.isEmpty() && !stripped.equals(n)) {
                    out.add(stripped);
                }
            }
        }
        String last = norm(chains[chains.length - 1]);
        if (!last.isEmpty()) {
            out.add(last);
        }
    }

    private static String stripMenuNoiseTokens(String s) {
        String v = norm(s);
        if (v.isEmpty()) {
            return "";
        }
        v = v.replace("событие ", " ")
            .replace("действие ", " ")
            .replace("условие ", " ")
            .replace("при ", " ");
        v = v.replaceAll("\\s+", " ").trim();
        return v;
    }

    private static int findSlotByTokenMatch(ContainerView view, String key, boolean skipPlayer) {
        if (view == null) {
            return -1;
        }
        String normKey = norm(key);
        if (normKey.isEmpty()) {
            return -1;
        }
        String[] wanted = normKey.split(" ");
        for (SlotView s : view.slots()) {
            if (s == null) {
                continue;
            }
            if (skipPlayer && s.playerInventory()) {
                continue;
            }
            String slotText = slotSearchText(s);
            if (slotText.isEmpty()) {
                continue;
            }
            boolean all = true;
            for (String t : wanted) {
                if (t == null || t.isEmpty()) {
                    continue;
                }
                if (!slotText.contains(t)) {
                    all = false;
                    break;
                }
            }
            if (all) {
                return s.slotNumber();
            }
        }
        return -1;
    }

    private static int findSlotByNbtTokenMatch(ContainerView view, String key, boolean skipPlayer) {
        if (view == null) {
            return -1;
        }
        String normKey = norm(key);
        if (normKey.isEmpty()) {
            return -1;
        }
        String[] wanted = normKey.split(" ");
        for (SlotView s : view.slots()) {
            if (s == null) {
                continue;
            }
            if (skipPlayer && s.playerInventory()) {
                continue;
            }
            String hay = slotSearchText(s);
            if (hay.isEmpty()) {
                continue;
            }
            boolean all = true;
            for (String t : wanted) {
                if (t == null || t.isEmpty()) {
                    continue;
                }
                if (!hay.contains(t)) {
                    all = false;
                    break;
                }
            }
            if (all) {
                return s.slotNumber();
            }
        }
        return -1;
    }

    private static int findSlotByCombinedTokenMatch(ContainerView view, String key, boolean skipPlayer) {
        if (view == null) {
            return -1;
        }
        String normKey = norm(key);
        if (normKey.isEmpty()) {
            return -1;
        }
        String[] wanted = normKey.split(" ");
        for (SlotView s : view.slots()) {
            if (s == null) {
                continue;
            }
            if (skipPlayer && s.playerInventory()) {
                continue;
            }
            String hay = slotSearchText(s);
            if (hay.isEmpty()) {
                continue;
            }
            boolean all = true;
            for (String t : wanted) {
                if (t == null || t.isEmpty()) {
                    continue;
                }
                if (!hay.contains(t)) {
                    all = false;
                    break;
                }
            }
            if (all) {
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
        String s = decodeEscapedUnicode(raw);
        s = s.replaceAll("(?i)§.", " ");
        // Drop common JSON/NBT service keys to keep only semantic tokens used in GUI text.
        s = s.replaceAll("(?i)\\b(text|translate|extra|italic|bold|underlined|strikethrough|obfuscated|color|clickevent|hoverevent|action|value|nbt|display|name|lore)\\b", " ");
        s = s.toLowerCase();
        s = s.replace('ё', 'е');
        s = s.replace('\u00A0', ' ');
        s = s.replaceAll("[^\\p{L}\\p{N}\\s]+", " ");
        s = s.replaceAll("\\s+", " ");
        return s.trim();
    }

    private static String slotSearchText(SlotView s) {
        if (s == null) {
            return "";
        }
        String display = norm(s.displayName());
        String nbt = norm(s.nbt());
        if (display.isEmpty()) {
            return nbt;
        }
        if (nbt.isEmpty()) {
            return display;
        }
        return (display + " " + nbt).trim();
    }

    private static String decodeEscapedUnicode(String raw) {
        if (raw == null || raw.isEmpty() || raw.indexOf("\\u") < 0) {
            return raw == null ? "" : raw;
        }
        StringBuilder out = new StringBuilder(raw.length());
        int i = 0;
        while (i < raw.length()) {
            char c = raw.charAt(i);
            if (c == '\\' && i + 5 < raw.length() && raw.charAt(i + 1) == 'u') {
                String hex = raw.substring(i + 2, i + 6);
                try {
                    int v = Integer.parseInt(hex, 16);
                    out.append((char) v);
                    i += 6;
                    continue;
                } catch (Exception ignore) {
                }
            }
            out.append(c);
            i++;
        }
        return out.toString();
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
        ClickResult click = clickSlotWithTrace(
            bridge, view, view.windowId(), nextArrow.slotNumber(), 0, "PICKUP", null, "args_page_next");
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

    private static void clearGuiStageState(PlaceRuntimeEntry entry) {
        entry.setAwaitingMenu(false);
        entry.setNeedOpenMenu(false);
        entry.setLastMenuWindowId(-1);
        entry.setMenuClicksSinceOpen(0);
        entry.setMenuNonEmptySinceMs(0L);
        entry.setMenuNonEmptyWindowId(-1);
        entry.setTriedWindowId(-1);
        entry.setAwaitingParamsChest(false);
        entry.setNeedOpenParamsChest(false);
        entry.setParamsOpenAttempts(0);
        entry.setParamsStartMs(0L);
        entry.setNextParamsActionMs(0L);
        entry.setParamsReadyWindowId(-1);
        entry.setParamsReadySinceMs(0L);
        entry.setAwaitingArgs(false);
        entry.setArgsWindowId(-1);
        entry.setPendingArgClickSlot(-1);
        entry.setPendingArgClicks(0);
        entry.setPendingArgNextMs(0L);
        entry.setArgsMisses(0);
        entry.clearUsedArgSlots();
    }

    private static boolean hasParamsChestNearTarget(GameBridge bridge) {
        if (bridge == null) {
            return false;
        }
        BlockPosView anchor = bridge.getRuntimeEntryAnchor();
        if (anchor == null) {
            return false;
        }
        String[] ids = new String[] {
            "minecraft:trapped_chest",
            "minecraft:chest",
            "minecraft:barrel"
        };
        int[][] offsets = new int[][] {
            {0, 1, 0},
            {0, 2, 0},
            {0, 0, 0},
            {0, -1, 0}
        };
        for (int[] off : offsets) {
            for (String id : ids) {
                if (bridge.isBlockAt(anchor.x() + off[0], anchor.y() + off[1], anchor.z() + off[2], id)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean hasTrappedChestNearTarget(GameBridge bridge) {
        if (bridge == null) {
            return false;
        }
        BlockPosView anchor = bridge.getRuntimeEntryAnchor();
        if (anchor == null) {
            return false;
        }
        int[][] offsets = new int[][] {
            {0, 1, 0},
            {0, 2, 0},
            {0, 0, 0},
            {0, -1, 0}
        };
        for (int[] off : offsets) {
            if (bridge.isBlockAt(anchor.x() + off[0], anchor.y() + off[1], anchor.z() + off[2], "minecraft:trapped_chest")) {
                return true;
            }
        }
        return false;
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

    private static int findRandomMenuSlot(ContainerView view, PlaceRuntimeEntry entry) {
        if (view == null || view.slots() == null) {
            return -1;
        }
        java.util.ArrayList<Integer> regular = new java.util.ArrayList<Integer>();
        java.util.ArrayList<Integer> arrow = new java.util.ArrayList<Integer>();
        for (SlotView s : view.slots()) {
            if (s == null || s.playerInventory() || s.empty()) {
                continue;
            }
            if (entry != null && entry.isTriedMenuSlot(s.slotNumber())) {
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
        int seed = entry == null || entry.randomClicks() < 0 ? 0 : entry.randomClicks();
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

    private static String summarizeNonPlayerSlots(ContainerView view, int limit) {
        if (view == null || view.slots() == null) {
            return "-";
        }
        StringBuilder sb = new StringBuilder();
        int count = 0;
        for (SlotView s : view.slots()) {
            if (s == null || s.playerInventory()) {
                continue;
            }
            if (count > 0) {
                sb.append(" | ");
            }
            sb.append('#').append(s.slotNumber())
                .append(" idx=").append(s.index())
                .append(" item=").append(safe(s.itemId()))
                .append(" name=").append(safe(norm(s.displayName())));
            count++;
            if (count >= limit) {
                sb.append(" ...");
                break;
            }
        }
        return sb.length() == 0 ? "-" : sb.toString();
    }

    private static void rememberLearnedMenuFromWindow(PlaceRuntimeEntry entry, ContainerView view) {
        if (entry == null || view == null) {
            return;
        }
        String clickKey = norm(entry.lastMenuClickedKey());
        if (clickKey.isEmpty() || view.windowId() < 0 || !hasNonPlayerItems(view)) {
            return;
        }
        HashSet<String> learned = new HashSet<String>();
        for (SlotView s : view.slots()) {
            if (s == null || s.playerInventory() || s.empty()) {
                continue;
            }
            String dn = norm(s.displayName());
            if (!dn.isEmpty()) {
                learned.add(dn);
            }
            String nn = norm(s.nbt());
            if (!nn.isEmpty()) {
                learned.add(nn);
            }
        }
        if (learned.isEmpty()) {
            return;
        }
        if (MENU_SUBMENU_HINTS.size() > MAX_MENU_HINT_KEYS && !MENU_SUBMENU_HINTS.containsKey(clickKey)) {
            MENU_SUBMENU_HINTS.clear();
        }
        Set<String> existing = MENU_SUBMENU_HINTS.get(clickKey);
        if (existing == null) {
            existing = new HashSet<String>();
            MENU_SUBMENU_HINTS.put(clickKey, existing);
        }
        existing.addAll(learned);
        entry.setLastMenuClickedKey("");
    }

    private static int findSlotByLearnedMenuHints(ContainerView view, String routeKey) {
        if (view == null || view.slots() == null) {
            return -1;
        }
        String wanted = norm(routeKey);
        if (wanted.isEmpty()) {
            return -1;
        }
        for (SlotView s : view.slots()) {
            if (s == null || s.playerInventory() || s.empty()) {
                continue;
            }
            String k = norm(s.displayName());
            if (k.isEmpty()) {
                continue;
            }
            Set<String> hints = MENU_SUBMENU_HINTS.get(k);
            if (hints == null || hints.isEmpty()) {
                continue;
            }
            for (String h : hints) {
                String hh = norm(h);
                if (hh.isEmpty()) {
                    continue;
                }
                if (hh.equals(wanted) || hh.contains(wanted)) {
                    return s.slotNumber();
                }
            }
        }
        return -1;
    }

    private static String menuSlotKey(ContainerView view, int slotNumber) {
        SlotView s = findSlotBySlotNumber(view, slotNumber);
        return s == null ? "" : norm(s.displayName());
    }

    private static SlotView findSlotBySlotNumber(ContainerView view, int slotNumber) {
        if (view == null || view.slots() == null) {
            return null;
        }
        for (SlotView s : view.slots()) {
            if (s != null && s.slotNumber() == slotNumber) {
                return s;
            }
        }
        return null;
    }

    private static String safe(String v) {
        return v == null ? "" : v;
    }
}

