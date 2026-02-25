package com.rainbow_universe.bettercode.fabric1165;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.rainbow_universe.bettercode.core.CoreLogger;
import com.rainbow_universe.bettercode.core.GameBridge;
import com.rainbow_universe.bettercode.core.PlaceExecResult;
import com.rainbow_universe.bettercode.core.PlaceOp;
import com.rainbow_universe.bettercode.core.RuntimeCore;
import com.rainbow_universe.bettercode.core.RuntimeResult;
import com.rainbow_universe.bettercode.core.ScoreboardContext;
import com.rainbow_universe.bettercode.core.ScoreboardParser;
import com.rainbow_universe.bettercode.core.ScoreboardScopeResolver;
import com.rainbow_universe.bettercode.core.bridge.AckState;
import com.rainbow_universe.bettercode.core.bridge.BlockPosView;
import com.rainbow_universe.bettercode.core.bridge.ClickResult;
import com.rainbow_universe.bettercode.core.bridge.ContainerView;
import com.rainbow_universe.bettercode.core.bridge.CursorState;
import com.rainbow_universe.bettercode.core.bridge.CodeSelectorStore;
import com.rainbow_universe.bettercode.core.bridge.SelectedRow;
import com.rainbow_universe.bettercode.core.bridge.SelectedRowNormalizer;
import com.rainbow_universe.bettercode.core.bridge.SlotView;
import com.rainbow_universe.bettercode.core.place.PlaceRuntimeEntry;
import com.rainbow_universe.bettercode.core.place.PlaceRuntimeStepExecutor;
import com.rainbow_universe.bettercode.core.place.BlueGlassSearch;
import com.rainbow_universe.bettercode.core.settings.ModSettingsService;
import com.rainbow_universe.bettercode.core.settings.SettingDef;
import com.rainbow_universe.bettercode.core.settings.SettingType;
import com.rainbow_universe.bettercode.core.util.LegacyBlockIdCompat;
import com.rainbow_universe.bettercode.core.util.ReflectCompat;
import com.rainbow_universe.bettercode.core.util.TestcaseTool;
import com.rainbow_universe.bettercode.core.publish.PublishCacheStore;
import com.rainbow_universe.bettercode.core.publish.PublishCacheView;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v1.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v1.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.StringNbtReader;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.scoreboard.ScoreboardPlayerScore;
import net.minecraft.scoreboard.Team;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.LiteralText;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.registry.Registry;
import com.mojang.blaze3d.systems.RenderSystem;
import org.lwjgl.opengl.GL11;
import org.lwjgl.glfw.GLFW;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class BetterCodeFabric1165 implements ClientModInitializer {
    private static final String CODE_SELECTOR_TAG = "mldsl_code_selector";
    private static final long CODE_SELECTOR_TOGGLE_COOLDOWN_MS = 180L;
    private static final long PLACE_TP_SETTLE_MS = 350L;
    private static final long PLACE_WORLD_TRACE_GAP_MS = 300L;
    private static final String DEV_UTILS_TITLE = "Утилиты разработчика";
    private static final int DEV_UTILS_GUI_SELECTOR_SLOT = 4; // 5th GUI slot (zero-based)
    private static final int DEV_UTILS_REWARD_HOTBAR_SLOT = 4; // local reward slot in hotbar (5th)
    private static long lastCodeSelectorToggleMs = 0L;
    private static String lastBlockCompatLogKey = "";
    private static long lastBlockCompatLogMs = 0L;
    private static final Map<String, SelectedRow> SELECTED = new LinkedHashMap<>();
    private static volatile RuntimeCore RUNTIME;
    private static volatile ModSettingsService SETTINGS;
    private static final DirectPlaceState DIRECT_PLACE_STATE = new DirectPlaceState();
    private static final LocalTpState LOCAL_TP_STATE = new LocalTpState();
    private static volatile String LAST_WORLD_SEED_HINT_DIM = "";
    private static volatile BlockPos LAST_WORLD_SEED_HINT = null;
    private static volatile boolean LAST_EDITOR_LIKE = false;
    private static volatile String LAST_EDITOR_DIM = "";
    private static long LAST_SELECTOR_HIGHLIGHT_MS = 0L;
    private static long LAST_SELECTOR_HIGHLIGHT_LOG_MS = 0L;
    private static int INIT_CALL_COUNT = 0;
    private static long LAST_RUNTIME_TICK_WORLD_TIME = Long.MIN_VALUE;
    private static long LAST_RUNTIME_TICK_WALL_MS = 0L;
    private static String LAST_RUNTIME_TICK_DIM = "";
    private static long LAST_END_TICK_WORLD_TIME = Long.MIN_VALUE;
    private static String LAST_END_TICK_DIM = "";
    private static String LAST_LEGACY_CLICK_KEY = "";
    private static long LAST_LEGACY_CLICK_MS = 0L;
    private static int LAST_LEGACY_CLICK_BURST = 0;
    private static long LAST_TESTCASE_RENDER_LOG_MS = 0L;
    // Name-first parity mode for cross-version table compares.
    private static final boolean REGALL_EXPORT_INCLUDE_ITEM_ID = false;
    private static volatile boolean MENU_HINTS_PRELOADED = false;
    private static int LAST_WORLD_INSTANCE_ID = -1;
    private static long LAST_SCOREBOARD_PARSE_ERR_LOG_MS = 0L;
    private static boolean SPRINTF_ACTIVE = false;
    private static float SPRINTF_BASE_SPEED = Float.NaN;
    private static int SPRINTF_PLAYER_ID = -1;
    private static long SNAPSHOT_CACHE_TICK = Long.MIN_VALUE;
    private static int SNAPSHOT_CACHE_SYNC_ID = -1;
    private static int SNAPSHOT_CACHE_SCREEN_ID = -1;
    private static ContainerView SNAPSHOT_CACHE_VIEW = ContainerView.empty();
    private static final RegAllTablesState REGALL_TABLES = new RegAllTablesState();
    private static final NearbySignCacheState NEARBY_SIGN_CACHE = new NearbySignCacheState();
    private static boolean STOP_HOTKEY_K_DOWN = false;
    private static int LAST_DEV_UTILS_SYNC_ID = -1;
    private static String LAST_DEV_UTILS_DIM = "";
    private static final int NEARBY_SIGN_CACHE_RADIUS_XZ = 8;
    private static final int NEARBY_SIGN_CACHE_RADIUS_Y = 3;
    private static final int NEARBY_SIGN_CACHE_BATCH = 40;
    private static final long NEARBY_SIGN_CACHE_SCAN_GAP_MS = 90L;
    private static final long NEARBY_SIGN_CACHE_SAVE_GAP_MS = 60_000L;

    @Override
    public void onInitializeClient() {
        INIT_CALL_COUNT++;
        System.out.println("[printer-debug] mod_init adapter=fabric1165 initCount=" + INIT_CALL_COUNT
            + " classLoader=" + String.valueOf(getClass().getClassLoader()));
        ClientCommandManager.DISPATCHER.register(
            ClientCommandManager.literal("modsettings")
                .executes(ctx -> modSettingsList(ctx.getSource()))
                .then(ClientCommandManager.literal("toggle")
                    .then(ClientCommandManager.argument("key", StringArgumentType.word())
                        .executes(ctx -> modSettingsToggle(ctx.getSource(), StringArgumentType.getString(ctx, "key")))))
                .then(ClientCommandManager.literal("inc")
                    .then(ClientCommandManager.argument("key", StringArgumentType.word())
                        .then(ClientCommandManager.argument("delta", IntegerArgumentType.integer())
                            .executes(ctx -> modSettingsInc(
                                ctx.getSource(),
                                StringArgumentType.getString(ctx, "key"),
                                IntegerArgumentType.getInteger(ctx, "delta")
                            )))))
                .then(ClientCommandManager.literal("set")
                    .then(ClientCommandManager.argument("key", StringArgumentType.word())
                        .then(ClientCommandManager.argument("value", StringArgumentType.greedyString())
                            .executes(ctx -> modSettingsSet(
                                ctx.getSource(),
                                StringArgumentType.getString(ctx, "key"),
                                StringArgumentType.getString(ctx, "value")
                            )))))
                .then(ClientCommandManager.argument("key", StringArgumentType.word())
                    .executes(ctx -> modSettingsShow(ctx.getSource(), StringArgumentType.getString(ctx, "key"))))
        );

        ClientCommandManager.DISPATCHER.register(
            ClientCommandManager.literal("loadmodule")
                .then(ClientCommandManager.argument("postId", StringArgumentType.word())
                    .executes(ctx -> loadModule(ctx.getSource(), StringArgumentType.getString(ctx, "postId"), null))
                    .then(ClientCommandManager.argument("file", StringArgumentType.greedyString())
                        .executes(ctx -> loadModule(
                            ctx.getSource(),
                            StringArgumentType.getString(ctx, "postId"),
                            StringArgumentType.getString(ctx, "file")
                        ))))
        );

        ClientCommandManager.DISPATCHER.register(
            ClientCommandManager.literal("bc_select")
                .executes(ctx -> toggleCurrentBlock(ctx.getSource()))
        );

        ClientCommandManager.DISPATCHER.register(
            ClientCommandManager.literal("bc_select_clear")
                .executes(ctx -> clearSelection(ctx.getSource()))
        );

        ClientCommandManager.DISPATCHER.register(
            ClientCommandManager.literal("bc_select_list")
                .executes(ctx -> listSelection(ctx.getSource()))
        );

        ClientCommandManager.DISPATCHER.register(
            ClientCommandManager.literal("mldsl")
                .then(ClientCommandManager.literal("run")
                    .then(ClientCommandManager.literal("local")
                        .then(ClientCommandManager.argument("path", StringArgumentType.greedyString())
                            .executes(ctx -> runLocal(ctx.getSource(), StringArgumentType.getString(ctx, "path")))))
                    .then(ClientCommandManager.argument("postId", StringArgumentType.word())
                        .executes(ctx -> runMldsl(ctx.getSource(), StringArgumentType.getString(ctx, "postId"), null, "default"))
                        .then(ClientCommandManager.literal("--config")
                            .then(ClientCommandManager.argument("configFlag", StringArgumentType.word())
                                .executes(ctx -> runMldsl(
                                    ctx.getSource(),
                                    StringArgumentType.getString(ctx, "postId"),
                                    StringArgumentType.getString(ctx, "configFlag"),
                                    "flag"
                                ))))
                        .then(ClientCommandManager.argument("config", StringArgumentType.word())
                            .executes(ctx -> runMldsl(
                                ctx.getSource(),
                                StringArgumentType.getString(ctx, "postId"),
                                StringArgumentType.getString(ctx, "config"),
                                "positional"
                            ))))
                    .then(ClientCommandManager.argument("targetAuto", StringArgumentType.greedyString())
                        .executes(ctx -> runMldslAuto(ctx.getSource(), StringArgumentType.getString(ctx, "targetAuto")))))
                .then(ClientCommandManager.literal("check")
                    .then(ClientCommandManager.literal("local")
                        .then(ClientCommandManager.argument("path", StringArgumentType.greedyString())
                            .executes(ctx -> checkLocal(ctx.getSource(), StringArgumentType.getString(ctx, "path"))))))
        );

        ClientCommandManager.DISPATCHER.register(
            ClientCommandManager.literal("confirmload")
                .executes(ctx -> confirmLoad(ctx.getSource()))
        );

        ClientCommandManager.DISPATCHER.register(
            ClientCommandManager.literal("module")
                .then(ClientCommandManager.literal("publish")
                    .executes(ctx -> publishModule(ctx.getSource())))
        );

        ClientCommandManager.DISPATCHER.register(
            ClientCommandManager.literal("codeselector")
                .executes(ctx -> runCodeSelectorCommand(ctx.getSource()))
        );

        ClientCommandManager.DISPATCHER.register(
            ClientCommandManager.literal("bc_print_plan")
                .executes(ctx -> inspectPlan(ctx.getSource(), defaultPlanPath()))
                .then(ClientCommandManager.argument("path", StringArgumentType.greedyString())
                    .executes(ctx -> inspectPlan(ctx.getSource(), Path.of(StringArgumentType.getString(ctx, "path")))))
        );

        ClientCommandManager.DISPATCHER.register(
            ClientCommandManager.literal("testcase")
                .then(ClientCommandManager.literal("setpos")
                    .executes(ctx -> testcaseSetPos(ctx.getSource())))
                .then(ClientCommandManager.literal("rightclick")
                    .executes(ctx -> testcaseRightClick(ctx.getSource())))
                .then(ClientCommandManager.literal("tp")
                    .executes(ctx -> testcaseTp(ctx.getSource())))
                .then(ClientCommandManager.literal("trapcheck")
                    .executes(ctx -> testcaseTrapcheck(ctx.getSource())))
                .then(ClientCommandManager.literal("gettable")
                    .executes(ctx -> testcaseGetTable(ctx.getSource())))
                .then(ClientCommandManager.literal("gettablefromcache")
                    .executes(ctx -> testcaseGetTableFromCache(ctx.getSource())))
                .then(ClientCommandManager.literal("viewentities")
                    .executes(ctx -> testcaseViewEntities(ctx.getSource())))
                .then(ClientCommandManager.literal("outline1")
                    .executes(ctx -> testcaseOutlineMode(ctx.getSource(), 1)))
                .then(ClientCommandManager.literal("outline2")
                    .executes(ctx -> testcaseOutlineMode(ctx.getSource(), 2)))
                .then(ClientCommandManager.literal("outline3")
                    .executes(ctx -> testcaseOutlineMode(ctx.getSource(), 3)))
                .then(ClientCommandManager.literal("outline4")
                    .executes(ctx -> testcaseOutlineMode(ctx.getSource(), 4)))
                .then(ClientCommandManager.literal("outline")
                    .then(ClientCommandManager.argument("mode", IntegerArgumentType.integer(1, 4))
                        .executes(ctx -> testcaseOutlineMode(
                            ctx.getSource(),
                            IntegerArgumentType.getInteger(ctx, "mode")
                        ))))
        );

        ClientCommandManager.DISPATCHER.register(
            ClientCommandManager.literal("regalltables")
                .executes(ctx -> regAllTablesStart(ctx.getSource(), false))
                .then(ClientCommandManager.literal("select")
                    .executes(ctx -> regAllTablesStart(ctx.getSource(), true)))
                .then(ClientCommandManager.literal("status")
                    .executes(ctx -> regAllTablesStatus(ctx.getSource())))
                .then(ClientCommandManager.literal("stop")
                    .executes(ctx -> regAllTablesStop(ctx.getSource())))
        );

        ClientCommandManager.DISPATCHER.register(
            ClientCommandManager.literal("modhelp")
                .executes(ctx -> modHelp(ctx.getSource()))
        );

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (shouldSkipDuplicateEndTick(client)) {
                return;
            }
            if (isShutdownLikeState(client)) {
                runtime().stopActiveExecution(new FabricBridge(null), "client_shutdown_state");
                clearLocalTpQueue();
                return;
            }
            refreshWorldSessionState(client);
            traceRuntimeTickProbe(client);
            handleGlobalStopHotkey(client);
            handleLocalTpPath(client);
            handleSprintFlyBoost(client);
            handleRegAllTablesTick(client);
            refreshWorldLoadSeedHint(client);
            refreshCodeEntrySeedHint(client);
            renderSelectionHighlights(client);
            handleDevUtilitiesAutoSelector(client);
            runtime().handleClientTick(new FabricBridge(null), System.currentTimeMillis());
            handleNearbySignCacheTick(client);
        });
        WorldRenderEvents.BEFORE_DEBUG_RENDER.register(BetterCodeFabric1165::renderSelectionOutlines);
        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (hand != Hand.MAIN_HAND || player == null || world == null || hitResult == null) {
                return ActionResult.PASS;
            }
            ItemStack held = player.getMainHandStack();
            if (!isCodeSelectorItem(held)) {
                return ActionResult.PASS;
            }
            return toggleSelectionAtHit1165(player, world, hitResult.getBlockPos(), true)
                ? ActionResult.SUCCESS
                : ActionResult.FAIL;
        });
        ScreenEvents.BEFORE_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            ScreenMouseEvents.allowMouseClick(screen).register((s, mouseX, mouseY, button) ->
                !handleDevUtilsPhantomSelectorClick(s, mouseX, mouseY, button));
        });
    }

    private static int loadModule(FabricClientCommandSource source, String postId, String file) {
        RuntimeResult result = runtime().handleLoadModule(postId, file, null, new FabricBridge(source));
        if (result.ok()) {
            source.sendFeedback(new LiteralText(result.message()));
            return 1;
        }
        source.sendError(new LiteralText("[" + result.errorCode() + "] " + result.message()));
        return 0;
    }

    private static int runMldsl(FabricClientCommandSource source, String postId, String config, String syntax) {
        System.out.println("[printer-debug] run args postId=" + postId
            + " config=" + (config == null ? "default" : config)
            + " syntax=" + syntax);
        RuntimeResult result = runtime().handleRun(postId, config, new FabricBridge(source));
        if (result.ok()) {
            source.sendFeedback(new LiteralText(result.message()));
            return 1;
        }
        source.sendError(new LiteralText("[" + result.errorCode() + "] " + result.message()));
        return 0;
    }

    private static int runLocal(FabricClientCommandSource source, String path) {
        RuntimeResult result = runtime().handleRunLocal(path, false, new FabricBridge(source));
        if (result.ok()) {
            source.sendFeedback(new LiteralText(result.message()));
            return 1;
        }
        source.sendError(new LiteralText("[" + result.errorCode() + "] " + result.message()));
        return 0;
    }

    private static int checkLocal(FabricClientCommandSource source, String path) {
        RuntimeResult result = runtime().handleRunLocal(path, true, new FabricBridge(source));
        if (result.ok()) {
            source.sendFeedback(new LiteralText(result.message()));
            return 1;
        }
        source.sendError(new LiteralText("[" + result.errorCode() + "] " + result.message()));
        return 0;
    }

    private static int confirmLoad(FabricClientCommandSource source) {
        RuntimeResult result = runtime().handleConfirmLoad(new FabricBridge(source));
        if (result.ok()) {
            source.sendFeedback(new LiteralText(result.message()));
            return 1;
        }
        source.sendError(new LiteralText("[" + result.errorCode() + "] " + result.message()));
        return 0;
    }

    private static int publishModule(FabricClientCommandSource source) {
        RuntimeResult result = runtime().handlePublish(new FabricBridge(source));
        if (result.ok()) {
            source.sendFeedback(new LiteralText(result.message()));
            return 1;
        }
        if (result.errorCode() == com.rainbow_universe.bettercode.core.RuntimeErrorCode.NO_PENDING_PLAN) {
            MinecraftClient mc = MinecraftClient.getInstance();
            boolean hasSelection = !SELECTED.isEmpty();
            boolean hasTool = hasCodeSelectorInInventory1165(mc);
            if (hasSelection || hasTool) {
                source.sendError(new LiteralText("[NO_PENDING_PLAN] No pending module data. Use /loadmodule first."));
                return 0;
            }
            return issueCodeSelectorTool(source);
        }
        source.sendError(new LiteralText("[" + result.errorCode() + "] " + result.message()));
        return 0;
    }

    private static int runMldslAuto(FabricClientCommandSource source, String targetAuto) {
        String raw = targetAuto == null ? "" : targetAuto.trim();
        if (raw.isEmpty()) {
            source.sendError(new LiteralText("Usage: /mldsl run <postId|path.json> [config|--config <id>]"));
            return 0;
        }
        String unquoted = stripOuterQuotes(raw);
        if (looksLikeLocalPath(unquoted) && !containsConfigTail(unquoted)) {
            return runLocal(source, unquoted);
        }

        // Keep compatibility with /mldsl run <postId> [config] and --config form in greedy fallback.
        String postId = unquoted;
        String config = null;
        String syntax = "auto";
        int cfgIdx = unquoted.indexOf(" --config ");
        if (cfgIdx > 0) {
            postId = unquoted.substring(0, cfgIdx).trim();
            config = unquoted.substring(cfgIdx + " --config ".length()).trim();
            syntax = "auto_flag";
        } else {
            String[] parts = unquoted.split("\\s+");
            if (parts.length >= 2) {
                postId = parts[0];
                config = parts[1];
                syntax = "auto_positional";
            }
        }
        postId = stripOuterQuotes(postId);
        if (postId.isEmpty()) {
            source.sendError(new LiteralText("Usage: /mldsl run <postId|path.json> [config|--config <id>]"));
            return 0;
        }
        return runMldsl(source, postId, (config == null || config.isEmpty()) ? null : stripOuterQuotes(config), syntax);
    }

    private static String stripOuterQuotes(String raw) {
        if (raw == null) {
            return "";
        }
        String v = raw.trim();
        if (v.length() >= 2) {
            char a = v.charAt(0);
            char b = v.charAt(v.length() - 1);
            if ((a == '"' && b == '"') || (a == '\'' && b == '\'')) {
                return v.substring(1, v.length() - 1).trim();
            }
        }
        return v;
    }

    private static boolean looksLikeLocalPath(String v) {
        if (v == null) {
            return false;
        }
        String s = v.trim();
        if (s.isEmpty()) {
            return false;
        }
        String low = s.toLowerCase();
        if (low.endsWith(".json")) {
            return true;
        }
        if (s.contains("\\") || s.contains("/")) {
            return true;
        }
        return s.length() >= 3
            && Character.isLetter(s.charAt(0))
            && s.charAt(1) == ':'
            && (s.charAt(2) == '\\' || s.charAt(2) == '/');
    }

    private static boolean containsConfigTail(String v) {
        if (v == null) {
            return false;
        }
        return v.contains(" --config ");
    }

    private static int issueCodeSelectorTool(FabricClientCommandSource source) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.player == null) {
            source.sendError(new LiteralText("[code selector] player is unavailable"));
            return 0;
        }
        ItemStack stick = createCodeSelectorStack();
        boolean ok = mc.player.giveItemStack(stick);
        if (!ok) {
            source.sendError(new LiteralText("[code selector] inventory full"));
            return 0;
        }
        source.sendFeedback(new LiteralText("[code selector] selector stick granted. Use legacy-style row selection flow."));
        return 1;
    }

    private static int runCodeSelectorCommand(FabricClientCommandSource source) {
        return issueCodeSelectorTool(source);
    }

    private static ItemStack createCodeSelectorStack() {
        ItemStack stick = new ItemStack(Items.STICK);
        stick.setCustomName(new LiteralText("§eMLDSL Code Selector"));
        try {
            stick.setTag(StringNbtReader.parse("{" + CODE_SELECTOR_TAG + ":1b,Unbreakable:1b}"));
        } catch (Exception ignore) {
        }
        return stick;
    }

    private static int testcaseSetPos(FabricClientCommandSource source) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.player == null || mc.world == null) {
            source.sendError(new LiteralText("[testcase] player/world unavailable"));
            return 0;
        }
        BlockPos pos = mc.player.getBlockPos();
        HitResult target = mc.crosshairTarget;
        if (target instanceof BlockHitResult) {
            pos = ((BlockHitResult) target).getBlockPos();
        }
        String dim = String.valueOf(mc.world.getRegistryKey().getValue());
        TestcaseTool.Result result = TestcaseTool.setPos(dim, pos.getX(), pos.getY(), pos.getZ());
        source.sendFeedback(new LiteralText("[testcase] " + result.message()));
        return result.ok() ? 1 : 0;
    }

    private static int testcaseRightClick(FabricClientCommandSource source) {
        TestcaseTool.Result result = TestcaseTool.rightClick(new FabricBridge(source));
        if (result.ok()) {
            source.sendFeedback(new LiteralText("[testcase] " + result.message()));
            return 1;
        }
        source.sendError(new LiteralText("[testcase] " + result.message()));
        return 0;
    }

    private static int testcaseTp(FabricClientCommandSource source) {
        TestcaseTool.Result result = TestcaseTool.tp(new FabricBridge(source));
        if (result.ok()) {
            source.sendFeedback(new LiteralText("[testcase] " + result.message()));
            return 1;
        }
        source.sendError(new LiteralText("[testcase] " + result.message()));
        return 0;
    }

    private static int testcaseTrapcheck(FabricClientCommandSource source) {
        TestcaseTool.Result result = TestcaseTool.checkTrapChest(new FabricBridge(source));
        if (result.ok()) {
            source.sendFeedback(new LiteralText("[testcase] " + result.message()));
            return 1;
        }
        source.sendError(new LiteralText("[testcase] " + result.message()));
        return 0;
    }

    private static int testcaseGetTable(FabricClientCommandSource source) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.player == null || mc.world == null) {
            source.sendError(new LiteralText("[testcase] gettable failed: player/world unavailable"));
            return 0;
        }
        TestcaseTool.MarkerView marker = TestcaseTool.markerView();
        if (marker == null) {
            source.sendError(new LiteralText("[testcase] gettable failed: no marker, use /testcase setpos"));
            return 0;
        }
        String dim = String.valueOf(mc.world.getRegistryKey().getValue());
        if (!dim.equals(marker.dimension())) {
            source.sendError(new LiteralText("[testcase] gettable failed: dimension mismatch current=" + dim + " marker=" + marker.dimension()));
            return 0;
        }
        FabricBridge bridge = new FabricBridge(source);
        List<BlockPos> candidates = collectSignCandidatesForMarker(bridge, marker);
        if (candidates.isEmpty()) {
            source.sendError(new LiteralText("[testcase] gettable failed: no sign candidates from marker"));
            return 0;
        }
        source.sendFeedback(new LiteralText("[testcase] gettable candidates=" + candidates.size() + " marker="
            + marker.x() + "," + marker.y() + "," + marker.z()));
        int nonEmptyCount = 0;
        for (int i = 0; i < candidates.size(); i++) {
            BlockPos p = candidates.get(i);
            String[] lines = bridge.readSignLinesAt(p.getX(), p.getY(), p.getZ());
            boolean ok = hasAnySignText(lines);
            if (ok) {
                nonEmptyCount++;
            }
            source.sendFeedback(new LiteralText("[testcase] gettable[" + i + "] pos="
                + p.getX() + "," + p.getY() + "," + p.getZ()
                + " nonEmpty=" + ok
                + " lines=" + formatLinesShort(lines)));
        }
        if (nonEmptyCount <= 0) {
            source.sendError(new LiteralText("[testcase] gettable: no readable sign lines"));
            return 0;
        }
        return 1;
    }

    private static int testcaseGetTableFromCache(FabricClientCommandSource source) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.player == null || mc.world == null) {
            source.sendError(new LiteralText("[testcase] gettablefromcache failed: player/world unavailable"));
            return 0;
        }
        TestcaseTool.MarkerView marker = TestcaseTool.markerView();
        if (marker == null) {
            source.sendError(new LiteralText("[testcase] gettablefromcache failed: no marker, use /testcase setpos"));
            return 0;
        }
        String dim = String.valueOf(mc.world.getRegistryKey().getValue());
        if (!dim.equals(marker.dimension())) {
            source.sendError(new LiteralText("[testcase] gettablefromcache failed: dimension mismatch current=" + dim + " marker=" + marker.dimension()));
            return 0;
        }
        PublishCacheView cache = effectivePublishCache(mc);
        List<BlockPos> candidates = collectSignCandidatesForMarker(new FabricBridge(source), marker);
        source.sendFeedback(new LiteralText("[testcase] gettablefromcache candidates=" + candidates.size()
            + " cache(scope=" + cache.scopeSnapshot().size()
            + ",dimPos=" + cache.dimPosSnapshot().size()
            + ",entryMap=" + cache.entryToSignSnapshot().size() + ")"));
        int hits = 0;
        for (int i = 0; i < candidates.size(); i++) {
            BlockPos p = candidates.get(i);
            String dimPosKey = dim + ":" + p.getX() + ":" + p.getY() + ":" + p.getZ();
            String[] lines = cache.getDimPos(dimPosKey);
            boolean ok = hasAnySignText(lines);
            if (ok) {
                hits++;
            }
            source.sendFeedback(new LiteralText("[testcase] cache.dimPos[" + i + "] key=" + dimPosKey
                + " hit=" + ok + " lines=" + formatLinesShort(lines)));
        }

        BlockPos entryA = new BlockPos(marker.x(), marker.y(), marker.z());
        BlockPos entryB = entryA.up();
        BlockPos[] entryCandidates = new BlockPos[] {entryA, entryB};
        for (int i = 0; i < entryCandidates.length; i++) {
            BlockPos entry = entryCandidates[i];
            String entryKey = dim + ":entry:" + entry.getX() + ":" + entry.getY() + ":" + entry.getZ();
            String mappedSign = cache.getEntryToSign(entryKey);
            if (mappedSign == null || mappedSign.trim().isEmpty()) {
                source.sendFeedback(new LiteralText("[testcase] cache.entry[" + i + "] key=" + entryKey + " mappedSign=-"));
                continue;
            }
            String[] mappedLines = cache.getDimPos(mappedSign);
            boolean ok = hasAnySignText(mappedLines);
            if (ok) {
                hits++;
            }
            source.sendFeedback(new LiteralText("[testcase] cache.entry[" + i + "] key=" + entryKey
                + " mappedSign=" + mappedSign
                + " hit=" + ok
                + " lines=" + formatLinesShort(mappedLines)));
        }

        if (hits <= 0) {
            source.sendError(new LiteralText("[testcase] gettablefromcache: no cache hits for marker candidates"));
            return 0;
        }
        return 1;
    }

    private static int testcaseViewEntities(FabricClientCommandSource source) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.player == null || mc.world == null) {
            source.sendError(new LiteralText("[testcase] viewentities failed: player/world unavailable"));
            return 0;
        }
        final int radius = 15;
        BlockPos center = mc.player.getBlockPos();
        Iterable<?> iterable = resolveWorldBlockEntities(mc.world);
        if (iterable == null) {
            source.sendError(new LiteralText("[testcase] viewentities failed: world block-entity iterable unavailable"));
            return 0;
        }
        FabricBridge bridge = new FabricBridge(source);
        int inRadius = 0;
        int signCount = 0;
        int signNonEmpty = 0;
        List<String> samples = new ArrayList<String>();
        try {
            for (Object raw : iterable) {
                if (!(raw instanceof BlockEntity)) {
                    continue;
                }
                BlockEntity be = (BlockEntity) raw;
                BlockPos p = be.getPos();
                if (p == null) {
                    continue;
                }
                int dx = p.getX() - center.getX();
                int dy = p.getY() - center.getY();
                int dz = p.getZ() - center.getZ();
                if ((dx * dx + dy * dy + dz * dz) > (radius * radius)) {
                    continue;
                }
                inRadius++;
                if (!isLikelySignBlockEntity(mc, be, p)) {
                    continue;
                }
                signCount++;
                String[] lines = bridge.readSignLinesAt(p.getX(), p.getY(), p.getZ());
                boolean nonEmpty = hasAnySignText(lines);
                if (nonEmpty) {
                    signNonEmpty++;
                }
                if (samples.size() < 5) {
                    samples.add("pos=" + p.getX() + "," + p.getY() + "," + p.getZ()
                        + " nonEmpty=" + nonEmpty
                        + " lines=" + formatLinesShort(lines));
                }
            }
        } catch (Exception e) {
            source.sendError(new LiteralText("[testcase] viewentities failed: " + e.getClass().getSimpleName()));
            return 0;
        }
        source.sendFeedback(new LiteralText("[testcase] viewentities radius=" + radius
            + " blockEntitiesInRadius=" + inRadius
            + " signEntities=" + signCount
            + " signWithText=" + signNonEmpty));
        if (samples.isEmpty()) {
            source.sendFeedback(new LiteralText("[testcase] viewentities samples: none"));
        } else {
            for (int i = 0; i < samples.size(); i++) {
                source.sendFeedback(new LiteralText("[testcase] sample[" + i + "] " + samples.get(i)));
            }
        }
        return 1;
    }

    private static PublishCacheView effectivePublishCache(MinecraftClient mc) {
        PublishCacheView merged = PublishCacheStore.load(mc.runDirectory.toPath());
        synchronized (NEARBY_SIGN_CACHE) {
            if (NEARBY_SIGN_CACHE.loaded && NEARBY_SIGN_CACHE.cache != null) {
                merged.mergeFrom(NEARBY_SIGN_CACHE.cache);
            }
        }
        return merged;
    }

    private static int testcaseOutlineMode(FabricClientCommandSource source, int mode) {
        TestcaseTool.Result result = TestcaseTool.setOutlineMode(mode);
        if (result.ok()) {
            source.sendFeedback(new LiteralText("[testcase] " + result.message()));
            return 1;
        }
        source.sendError(new LiteralText("[testcase] " + result.message()));
        return 0;
    }

    private static int regAllTablesStart(FabricClientCommandSource source, boolean selectMode) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.player == null || mc.world == null) {
            source.sendError(new LiteralText("[regalltables] player/world unavailable"));
            return 0;
        }
        TestcaseTool.MarkerView marker = TestcaseTool.markerView();
        if (marker == null) {
            source.sendError(new LiteralText("[regalltables] no marker. use /testcase setpos on action sign"));
            return 0;
        }
        String dim = String.valueOf(mc.world.getRegistryKey().getValue());
        if (!dim.equals(marker.dimension())) {
            source.sendError(new LiteralText("[regalltables] dimension mismatch current=" + dim + " marker=" + marker.dimension()));
            return 0;
        }
        synchronized (REGALL_TABLES) {
            REGALL_TABLES.reset();
            REGALL_TABLES.active = true;
            REGALL_TABLES.dimension = dim;
            REGALL_TABLES.signX = marker.x();
            REGALL_TABLES.signY = marker.y();
            REGALL_TABLES.signZ = marker.z();
            REGALL_TABLES.selectMode = selectMode;
            REGALL_TABLES.currentPath = new ArrayList<String>();
            REGALL_TABLES.queuePathKeys.add(pathKeyOf(REGALL_TABLES.currentPath));
            REGALL_TABLES.phase = "OPEN_ROOT";
            REGALL_TABLES.startedMs = System.currentTimeMillis();
            REGALL_TABLES.nextActionMs = 0L;
            REGALL_TABLES.lastProgressMs = REGALL_TABLES.startedMs;
        }
        source.sendFeedback(new LiteralText("[regalltables] started mode=" + (selectMode ? "select" : "full")
            + " sign=" + marker.x() + "," + marker.y() + "," + marker.z()));
        System.out.println("[printer-debug] regalltables start dim=" + dim + " sign=" + marker.x() + "," + marker.y() + "," + marker.z());
        return 1;
    }

    private static int regAllTablesStop(FabricClientCommandSource source) {
        MinecraftClient mc = MinecraftClient.getInstance();
        int saved;
        Path outPath = regAllTablesExportPath(mc);
        synchronized (REGALL_TABLES) {
            if (!REGALL_TABLES.active) {
                source.sendFeedback(new LiteralText("[regalltables] not running"));
                return 1;
            }
            REGALL_TABLES.active = false;
            saved = writeRegAllTablesExport(mc, REGALL_TABLES, "manual_stop");
            REGALL_TABLES.reset();
        }
        source.sendFeedback(new LiteralText("[regalltables] stopped. exported records=" + saved + " file=" + outPath));
        return 1;
    }

    private static int regAllTablesStatus(FabricClientCommandSource source) {
        synchronized (REGALL_TABLES) {
            if (!REGALL_TABLES.active) {
                source.sendFeedback(new LiteralText("[regalltables] inactive"));
                return 1;
            }
            source.sendFeedback(new LiteralText(
                "[regalltables] active phase=" + REGALL_TABLES.phase
                    + " records=" + REGALL_TABLES.records.size()
                    + " path=" + pathKeyOf(REGALL_TABLES.currentPath)
                    + " retries=" + REGALL_TABLES.noProgressRecoverCount
                    + " file=" + regAllTablesExportPath(MinecraftClient.getInstance())));
            return 1;
        }
    }

    private static int modHelp(FabricClientCommandSource source) {
        source.sendFeedback(new LiteralText("[modhelp] /mldsl run <postId|path.json> [config]"));
        source.sendFeedback(new LiteralText("[modhelp] /module publish - publish selected rows"));
        source.sendFeedback(new LiteralText("[modhelp] /testcase setpos|rightclick|tp|trapcheck|gettable|gettablefromcache|viewentities|outline1..4"));
        source.sendFeedback(new LiteralText("[modhelp] /regalltables - crawl menu tables from /testcase marker and export tablesexport.txt"));
        source.sendFeedback(new LiteralText("[modhelp] /regalltables select - crawl tables without bulk action mark"));
        source.sendFeedback(new LiteralText("[modhelp] /regalltables stop - stop crawl and export partial result"));
        source.sendFeedback(new LiteralText("[modhelp] Press K - emergency stop active print/crawler"));
        source.sendFeedback(new LiteralText("[modhelp] /modsettings - runtime settings"));
        return 1;
    }

    private static List<BlockPos> collectSignCandidatesForMarker(FabricBridge bridge, TestcaseTool.MarkerView marker) {
        List<BlockPos> out = new ArrayList<>();
        if (bridge == null || marker == null) {
            return out;
        }
        addSignIfPresent(bridge, out, new BlockPos(marker.x(), marker.y(), marker.z()));
        addSignIfPresent(bridge, out, new BlockPos(marker.x(), marker.y(), marker.z() - 1));
        addLegacyEntryScan(bridge, out, marker.x(), marker.y(), marker.z());
        addLegacyEntryScan(bridge, out, marker.x(), marker.y() + 1, marker.z());
        return out;
    }

    private static void addLegacyEntryScan(FabricBridge bridge, List<BlockPos> out, int entryX, int entryY, int entryZ) {
        for (int dy = -2; dy <= 0; dy++) {
            addSignIfPresent(bridge, out, new BlockPos(entryX, entryY + dy, entryZ - 1));
        }
    }

    private static void addSignIfPresent(FabricBridge bridge, List<BlockPos> out, BlockPos pos) {
        if (bridge == null || out == null || pos == null) {
            return;
        }
        if (!bridge.isSignAt(pos.getX(), pos.getY(), pos.getZ())) {
            return;
        }
        for (BlockPos e : out) {
            if (e.getX() == pos.getX() && e.getY() == pos.getY() && e.getZ() == pos.getZ()) {
                return;
            }
        }
        out.add(pos);
    }

    private static String formatLinesShort(String[] lines) {
        if (lines == null) {
            return "null";
        }
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < lines.length; i++) {
            if (i > 0) {
                sb.append(" | ");
            }
            String v = lines[i] == null ? "" : lines[i].trim();
            if (v.length() > 28) {
                v = v.substring(0, 28) + "...";
            }
            sb.append(i).append('=').append(v);
        }
        sb.append(']');
        return sb.toString();
    }

    private static boolean hasAnySignText(String[] lines) {
        if (lines == null || lines.length == 0) {
            return false;
        }
        for (String s : lines) {
            if (s != null && !s.trim().isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private static void handleGlobalStopHotkey(MinecraftClient mc) {
        if (mc == null || mc.player == null || mc.getWindow() == null) {
            STOP_HOTKEY_K_DOWN = false;
            return;
        }
        boolean down = GLFW.glfwGetKey(mc.getWindow().getHandle(), GLFW.GLFW_KEY_K) == GLFW.GLFW_PRESS;
        if (down && !STOP_HOTKEY_K_DOWN) {
            runtime().stopActiveExecution(new FabricBridge(null), "manual_stop_key_k");
            clearLocalTpQueue();
            synchronized (REGALL_TABLES) {
                if (REGALL_TABLES.active) {
                    int saved = writeRegAllTablesExport(mc, REGALL_TABLES, "hotkey_k");
                    Path out = regAllTablesExportPath(mc);
                    System.out.println("[printer-debug] regalltables stop reason=hotkey_k exported=" + saved + " file=" + out);
                    mc.player.sendMessage(new LiteralText("[regalltables] stopped by K. exported=" + saved + " file=" + out), false);
                    REGALL_TABLES.reset();
                }
            }
            mc.player.sendMessage(new LiteralText("[bettercode] stopped by hotkey K"), false);
        }
        STOP_HOTKEY_K_DOWN = down;
    }

    private static void handleRegAllTablesTick(MinecraftClient mc) {
        if (mc == null || mc.player == null || mc.world == null) {
            return;
        }
        Screen screen = mc.currentScreen;
        if (screen != null && !(screen instanceof HandledScreen)) {
            return;
        }
        synchronized (REGALL_TABLES) {
            if (!REGALL_TABLES.active) {
                return;
            }
            long now = System.currentTimeMillis();
            if (now < REGALL_TABLES.nextActionMs) {
                return;
            }
            String dim = String.valueOf(mc.world.getRegistryKey().getValue());
            if (!dim.equals(REGALL_TABLES.dimension)) {
                int saved = writeRegAllTablesExport(mc, REGALL_TABLES, "dimension_changed");
                Path out = regAllTablesExportPath(mc);
                System.out.println("[printer-debug] regalltables stop reason=dimension_changed exported=" + saved + " file=" + out);
                mc.player.sendMessage(new LiteralText("[regalltables] stopped: dimension changed. exported=" + saved + " file=" + out), false);
                REGALL_TABLES.reset();
                return;
            }
            if (now - REGALL_TABLES.startedMs > 15L * 60L * 1000L) {
                int saved = writeRegAllTablesExport(mc, REGALL_TABLES, "timeout_15m");
                Path out = regAllTablesExportPath(mc);
                System.out.println("[printer-debug] regalltables stop reason=timeout exported=" + saved + " file=" + out);
                mc.player.sendMessage(new LiteralText("[regalltables] stopped: timeout. exported=" + saved + " file=" + out), false);
                REGALL_TABLES.reset();
                return;
            }
            if (now - REGALL_TABLES.lastProgressMs > 20_000L) {
                REGALL_TABLES.phase = "OPEN_ROOT";
                REGALL_TABLES.replayIndex = 0;
                closeScreenQuiet(mc);
                REGALL_TABLES.noProgressRecoverCount++;
                if (REGALL_TABLES.noProgressRecoverCount >= 6) {
                    int saved = writeRegAllTablesExport(mc, REGALL_TABLES, "stalled_no_progress");
                    Path out = regAllTablesExportPath(mc);
                    System.out.println("[printer-debug] regalltables stop reason=stalled_no_progress exported=" + saved + " file=" + out);
                    if (mc.player != null) {
                        mc.player.sendMessage(new LiteralText("[regalltables] stopped: stalled_no_progress. exported=" + saved + " file=" + out), false);
                    }
                    REGALL_TABLES.reset();
                    return;
                }
                REGALL_TABLES.nextActionMs = now + 160L;
                return;
            }
            stepRegAllTables(mc, now);
        }
    }

    private static void stepRegAllTables(MinecraftClient mc, long now) {
        FabricBridge bridge = new FabricBridge(null);
        ContainerView view = bridge.getContainerSnapshot();
        if ("OPEN_ROOT".equals(REGALL_TABLES.phase)) {
            if (!bridge.getCursorStack().isEmpty()) {
                REGALL_TABLES.nextActionMs = now + 120L;
                return;
            }
            closeScreenQuiet(mc);
            ClickResult click = bridge.clickBlockLegacy(REGALL_TABLES.signX, REGALL_TABLES.signY, REGALL_TABLES.signZ, "regalltables_open_sign", true);
            REGALL_TABLES.phase = "WAIT_MENU";
            REGALL_TABLES.nextActionMs = now + (click != null && click.accepted() ? 260L : 420L);
            return;
        }
        if ("WAIT_MENU".equals(REGALL_TABLES.phase)) {
            if (view.windowId() < 0 || !hasNonPlayerMenuContent(view)) {
                REGALL_TABLES.nextActionMs = now + 120L;
                return;
            }
            if (REGALL_TABLES.replayIndex < REGALL_TABLES.currentPath.size()) {
                REGALL_TABLES.phase = "REPLAY_PATH";
                REGALL_TABLES.nextActionMs = now + 20L;
                return;
            }
            REGALL_TABLES.phase = "SCAN_MENU";
            REGALL_TABLES.nextActionMs = now + 20L;
            return;
        }
        if ("REPLAY_PATH".equals(REGALL_TABLES.phase)) {
            if (view.windowId() < 0) {
                REGALL_TABLES.phase = "OPEN_ROOT";
                REGALL_TABLES.nextActionMs = now + 160L;
                return;
            }
            String wanted = REGALL_TABLES.currentPath.get(REGALL_TABLES.replayIndex);
            SlotView slot = findSlotByName(view, wanted);
            if (slot == null) {
                REGALL_TABLES.phase = "ADVANCE_MENU";
                REGALL_TABLES.nextActionMs = now + 20L;
                return;
            }
            if (!bridge.getCursorStack().isEmpty()) {
                REGALL_TABLES.nextActionMs = now + 120L;
                return;
            }
            ClickResult click = bridge.clickSlot(view.windowId(), slot.slotNumber(), 0, "PICKUP");
            if (click == null || !click.accepted()) {
                REGALL_TABLES.phase = "ADVANCE_MENU";
                REGALL_TABLES.nextActionMs = now + 20L;
                return;
            }
            REGALL_TABLES.pendingWindowId = view.windowId();
            REGALL_TABLES.pendingMenuSignature = menuSignature(view);
            REGALL_TABLES.pendingClickMs = now;
            REGALL_TABLES.phase = "WAIT_REPLAY_CLICK";
            REGALL_TABLES.nextActionMs = now + 220L;
            return;
        }
        if ("WAIT_REPLAY_CLICK".equals(REGALL_TABLES.phase)) {
            if (!bridge.getCursorStack().isEmpty()) {
                REGALL_TABLES.nextActionMs = now + 120L;
                return;
            }
            if (now - REGALL_TABLES.pendingClickMs < 180L) {
                REGALL_TABLES.nextActionMs = now + 80L;
                return;
            }
            ContainerView afterReplay = bridge.getContainerSnapshot();
            long elapsed = now - REGALL_TABLES.pendingClickMs;
            if (afterReplay.windowId() < 0) {
                // Replayed path node behaved like terminal action (no submenu opened).
                // Do not continue waiting submenu for this path.
                REGALL_TABLES.visitedMenuKeys.add(pathKeyOf(REGALL_TABLES.currentPath));
                System.out.println("[printer-debug] regalltables replay_terminal reason=closed path=" + String.join(" > ", REGALL_TABLES.currentPath));
                REGALL_TABLES.pendingMenuSignature = "";
                REGALL_TABLES.phase = "ADVANCE_MENU";
                REGALL_TABLES.nextActionMs = now + 120L;
                REGALL_TABLES.lastProgressMs = now;
                return;
            }
            String replaySig = menuSignature(afterReplay);
            if (afterReplay.windowId() == REGALL_TABLES.pendingWindowId
                && replaySig.equals(REGALL_TABLES.pendingMenuSignature)) {
                if (elapsed < 700L) {
                    REGALL_TABLES.nextActionMs = now + 120L;
                    return;
                }
                REGALL_TABLES.visitedMenuKeys.add(pathKeyOf(REGALL_TABLES.currentPath));
                System.out.println("[printer-debug] regalltables replay_terminal reason=same_menu_timeout path=" + String.join(" > ", REGALL_TABLES.currentPath)
                    + " window=" + afterReplay.windowId());
                REGALL_TABLES.pendingMenuSignature = "";
                REGALL_TABLES.phase = "ADVANCE_MENU";
                REGALL_TABLES.nextActionMs = now + 120L;
                REGALL_TABLES.lastProgressMs = now;
                return;
            }
            REGALL_TABLES.pendingMenuSignature = "";
            REGALL_TABLES.replayIndex++;
            REGALL_TABLES.phase = "WAIT_MENU";
            REGALL_TABLES.nextActionMs = now + 100L;
            return;
        }
        if ("SCAN_MENU".equals(REGALL_TABLES.phase)) {
            if (view.windowId() < 0 || !hasNonPlayerMenuContent(view)) {
                REGALL_TABLES.phase = "OPEN_ROOT";
                REGALL_TABLES.replayIndex = 0;
                REGALL_TABLES.nextActionMs = now + 180L;
                return;
            }
            String menuKey = pathKeyOf(REGALL_TABLES.currentPath);
            List<SlotView> candidates = collectUntestedMenuSlots(view, menuKey);
            if (candidates.isEmpty()) {
                REGALL_TABLES.visitedMenuKeys.add(menuKey);
                REGALL_TABLES.phase = "ADVANCE_MENU";
                REGALL_TABLES.nextActionMs = now + 20L;
                return;
            }
            SlotView target = candidates.get(0);
            if (!bridge.getCursorStack().isEmpty()) {
                REGALL_TABLES.nextActionMs = now + 120L;
                return;
            }
            ClickResult click = bridge.clickSlot(view.windowId(), target.slotNumber(), 0, "PICKUP");
            if (click == null || !click.accepted()) {
                markMenuSlotTested(menuKey, target);
                REGALL_TABLES.nextActionMs = now + 120L;
                return;
            }
            REGALL_TABLES.pendingMenuKey = menuKey;
            REGALL_TABLES.pendingSlot = target;
            REGALL_TABLES.pendingMenuSnapshot = snapshotMenuSlots(view);
            REGALL_TABLES.pendingWindowId = view.windowId();
            REGALL_TABLES.pendingMenuSignature = menuSignature(view);
            REGALL_TABLES.pendingClickMs = now;
            REGALL_TABLES.phase = "WAIT_TEST_RESULT";
            REGALL_TABLES.nextActionMs = now + 220L;
            REGALL_TABLES.lastProgressMs = now;
            REGALL_TABLES.noProgressRecoverCount = 0;
            return;
        }
        if ("WAIT_TEST_RESULT".equals(REGALL_TABLES.phase)) {
            if (!bridge.getCursorStack().isEmpty()) {
                REGALL_TABLES.nextActionMs = now + 120L;
                return;
            }
            if (now - REGALL_TABLES.pendingClickMs < 180L) {
                REGALL_TABLES.nextActionMs = now + 80L;
                return;
            }
            long elapsed = now - REGALL_TABLES.pendingClickMs;
            ContainerView after = bridge.getContainerSnapshot();
            if (after.windowId() >= 0
                && after.windowId() == REGALL_TABLES.pendingWindowId
                && menuSignature(after).equals(REGALL_TABLES.pendingMenuSignature)
                && elapsed < 700L) {
                REGALL_TABLES.nextActionMs = now + 120L;
                return;
            }
            boolean closed = after.windowId() < 0;
            String type = closed ? "action" : "category";
            String menuPath = String.join(" > ", REGALL_TABLES.currentPath);
            String name = slotLabel(REGALL_TABLES.pendingSlot);
            String itemId = REGALL_TABLES.pendingSlot == null ? "" : safeText(REGALL_TABLES.pendingSlot.itemId());
            appendRegallRecord(menuPath, name, itemId, type);
            if (closed && !REGALL_TABLES.selectMode) {
                for (SlotView s : REGALL_TABLES.pendingMenuSnapshot) {
                    if (s == null || s.playerInventory() || s.empty()) {
                        continue;
                    }
                    appendRegallRecord(menuPath, slotLabel(s), safeText(s.itemId()), "action");
                    markMenuSlotTested(REGALL_TABLES.pendingMenuKey, s);
                }
            }
            markMenuSlotTested(REGALL_TABLES.pendingMenuKey, REGALL_TABLES.pendingSlot);
            if (!closed) {
                ArrayList<String> child = new ArrayList<String>(REGALL_TABLES.currentPath);
                if (!name.isEmpty()) {
                    child.add(name);
                }
                String childKey = pathKeyOf(child);
                if (!REGALL_TABLES.visitedMenuKeys.contains(childKey) && REGALL_TABLES.queuePathKeys.add(childKey)) {
                    REGALL_TABLES.pendingMenus.addLast(child);
                }
            }
            closeScreenQuiet(mc);
            REGALL_TABLES.replayIndex = 0;
            REGALL_TABLES.phase = "OPEN_ROOT";
            REGALL_TABLES.nextActionMs = now + 180L;
            REGALL_TABLES.lastProgressMs = now;
            REGALL_TABLES.pendingMenuKey = "";
            REGALL_TABLES.pendingSlot = null;
            REGALL_TABLES.pendingMenuSnapshot = new ArrayList<SlotView>();
            REGALL_TABLES.pendingMenuSignature = "";
            return;
        }
        if ("ADVANCE_MENU".equals(REGALL_TABLES.phase)) {
            while (!REGALL_TABLES.pendingMenus.isEmpty()) {
                List<String> next = REGALL_TABLES.pendingMenus.removeFirst();
                String key = pathKeyOf(next);
                if (REGALL_TABLES.visitedMenuKeys.contains(key)) {
                    continue;
                }
                REGALL_TABLES.currentPath = new ArrayList<String>(next);
                REGALL_TABLES.replayIndex = 0;
                REGALL_TABLES.phase = "OPEN_ROOT";
                REGALL_TABLES.nextActionMs = now + 160L;
                REGALL_TABLES.lastProgressMs = now;
                REGALL_TABLES.noProgressRecoverCount = 0;
                return;
            }
            int saved = writeRegAllTablesExport(mc, REGALL_TABLES, "done");
            Path out = regAllTablesExportPath(mc);
            System.out.println("[printer-debug] regalltables done records=" + saved + " file=" + out);
            if (mc.player != null) {
                mc.player.sendMessage(new LiteralText("[regalltables] done. exported=" + saved + " file=" + out), false);
            }
            REGALL_TABLES.reset();
        }
    }

    private static void closeScreenQuiet(MinecraftClient mc) {
        if (mc != null && mc.player != null && mc.currentScreen instanceof HandledScreen) {
            mc.player.closeHandledScreen();
        }
    }

    private static List<SlotView> snapshotMenuSlots(ContainerView view) {
        ArrayList<SlotView> out = new ArrayList<SlotView>();
        if (view == null || view.slots() == null) {
            return out;
        }
        for (SlotView s : view.slots()) {
            if (s == null || s.playerInventory()) {
                continue;
            }
            out.add(s);
        }
        return out;
    }

    private static void appendRegallRecord(String path, String item, String itemId, String type) {
        String p = safeText(path);
        String i = safeText(item);
        String id = REGALL_EXPORT_INCLUDE_ITEM_ID ? safeText(itemId) : "";
        String t = safeText(type);
        String key = p + "|" + i + "|" + t;
        if (!REGALL_TABLES.recordKeys.add(key)) {
            return;
        }
        REGALL_TABLES.records.add(new RegAllTablesRecord(p, i, id, t));
    }

    private static String menuSignature(ContainerView view) {
        if (view == null || view.windowId() < 0 || view.slots() == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append(view.windowId()).append('|');
        for (SlotView s : view.slots()) {
            if (s == null || s.playerInventory()) {
                continue;
            }
            sb.append(s.slotNumber()).append(':')
                .append(s.empty() ? "0" : "1").append(':')
                .append(safeText(s.itemId())).append(':')
                .append(slotLabel(s)).append(';');
        }
        return sb.toString();
    }

    private static boolean hasNonPlayerMenuContent(ContainerView view) {
        if (view == null || view.slots() == null || view.windowId() < 0) {
            return false;
        }
        for (SlotView s : view.slots()) {
            if (s == null || s.playerInventory() || s.empty()) {
                continue;
            }
            return true;
        }
        return false;
    }

    private static SlotView findSlotByName(ContainerView view, String wanted) {
        String key = normalizeMenuText(wanted);
        if (key.isEmpty() || view == null || view.slots() == null) {
            return null;
        }
        for (SlotView s : view.slots()) {
            if (s == null || s.playerInventory() || s.empty()) {
                continue;
            }
            String n = normalizeMenuText(s.displayName());
            if (!n.isEmpty() && n.equals(key)) {
                return s;
            }
        }
        for (SlotView s : view.slots()) {
            if (s == null || s.playerInventory() || s.empty()) {
                continue;
            }
            String n = normalizeMenuText(s.displayName());
            if (!n.isEmpty() && (n.contains(key) || key.contains(n))) {
                return s;
            }
        }
        return null;
    }

    private static List<SlotView> collectUntestedMenuSlots(ContainerView view, String menuKey) {
        ArrayList<SlotView> out = new ArrayList<SlotView>();
        if (view == null || view.slots() == null) {
            return out;
        }
        Set<String> tested = REGALL_TABLES.testedSlotKeysByMenu.get(menuKey);
        for (SlotView s : view.slots()) {
            if (s == null || s.playerInventory() || s.empty()) {
                continue;
            }
            String itemId = safeText(s.itemId()).toLowerCase();
            if (itemId.endsWith(":arrow")) {
                continue;
            }
            String k = slotIdentityKey(s);
            if (tested != null && tested.contains(k)) {
                continue;
            }
            out.add(s);
        }
        out.sort(Comparator.comparingInt(SlotView::slotNumber));
        return out;
    }

    private static void markMenuSlotTested(String menuKey, SlotView slot) {
        if (menuKey == null) {
            menuKey = "";
        }
        Set<String> tested = REGALL_TABLES.testedSlotKeysByMenu.get(menuKey);
        if (tested == null) {
            tested = new HashSet<String>();
            REGALL_TABLES.testedSlotKeysByMenu.put(menuKey, tested);
        }
        tested.add(slotIdentityKey(slot));
    }

    private static String slotIdentityKey(SlotView slot) {
        if (slot == null) {
            return "";
        }
        return normalizeMenuText(slot.displayName()) + "|" + safeText(slot.itemId()).toLowerCase();
    }

    private static String slotLabel(SlotView slot) {
        return normalizeMenuText(slot == null ? "" : slot.displayName());
    }

    private static String normalizeMenuText(String raw) {
        if (raw == null) {
            return "";
        }
        String s = raw.replaceAll("(?i)§.", " ").toLowerCase();
        s = s.replace('ё', 'е');
        s = s.replace('\u00A0', ' ');
        s = s.replaceAll("[^\\p{L}\\p{N}\\s]+", " ");
        s = s.replaceAll("\\s+", " ").trim();
        return s;
    }

    private static String pathKeyOf(List<String> path) {
        if (path == null || path.isEmpty()) {
            return "/";
        }
        StringBuilder sb = new StringBuilder();
        for (String p : path) {
            String n = normalizeMenuText(p);
            if (n.isEmpty()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(" > ");
            }
            sb.append(n);
        }
        return sb.length() == 0 ? "/" : sb.toString();
    }

    private static int writeRegAllTablesExport(MinecraftClient mc, RegAllTablesState state, String reason) {
        if (mc == null || mc.runDirectory == null || state == null) {
            return 0;
        }
        Path out = regAllTablesExportPath(mc);
        ArrayList<String> lines = new ArrayList<String>();
        lines.add("");
        lines.add("# ==== regalltables export session ====");
        lines.add("ts=" + System.currentTimeMillis());
        lines.add("reason=" + safeText(reason));
        lines.add("dimension=" + safeText(state.dimension));
        lines.add("sign=" + state.signX + "," + state.signY + "," + state.signZ);
        lines.add("records=" + state.records.size());
        for (int i = 0; i < state.records.size(); i++) {
            RegAllTablesRecord r = state.records.get(i);
            lines.add("");
            lines.add("# record " + (i + 1));
            lines.add("path=" + safeText(r.path));
            lines.add("item=" + safeText(r.item));
            if (REGALL_EXPORT_INCLUDE_ITEM_ID) {
                lines.add("itemId=" + safeText(r.itemId));
            }
            lines.add("type=" + safeText(r.type));
        }
        try {
            Files.write(out, lines, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            return state.records.size();
        } catch (Exception e) {
            System.out.println("[printer-debug] regalltables export failed: " + e.getClass().getSimpleName() + " " + e.getMessage());
            return 0;
        }
    }

    private static Path regAllTablesExportPath(MinecraftClient mc) {
        if (mc == null || mc.runDirectory == null) {
            return Path.of("tablesexport.txt").toAbsolutePath();
        }
        return mc.runDirectory.toPath().resolve("tablesexport.txt").toAbsolutePath().normalize();
    }

    private static String safeText(String s) {
        return s == null ? "" : s;
    }

    private static int toggleCurrentBlock(FabricClientCommandSource source) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null) {
            source.sendError(new LiteralText("No world/player available."));
            return 0;
        }
        HitResult target = mc.crosshairTarget;
        if (!(target instanceof BlockHitResult)) {
            source.sendError(new LiteralText("Look at a block and run /bc_select."));
            return 0;
        }
        BlockPos pos = ((BlockHitResult) target).getBlockPos();
        if (!toggleSelectionAtHit1165(mc.player, mc.world, pos, false)) {
            return 0;
        }
        source.sendFeedback(new LiteralText("Selection toggled (selected=" + SELECTED.size() + ")"));
        return 1;
    }

    private static boolean toggleSelectionAtHit1165(net.minecraft.entity.player.PlayerEntity player, net.minecraft.world.World world, BlockPos clicked, boolean showActionBar) {
        if (player == null || world == null || clicked == null) {
            return false;
        }
        long now = System.currentTimeMillis();
        if (now - lastCodeSelectorToggleMs < CODE_SELECTOR_TOGGLE_COOLDOWN_MS) {
            return true;
        }
        lastCodeSelectorToggleMs = now;
        BlockPos glassPos = resolveCodeSelectorGlassPos1165(world, clicked);
        if (glassPos == null) {
            if (showActionBar) {
                player.sendMessage(new LiteralText("§cКликни по блоку над голубым стеклом"), true);
            }
            return false;
        }
        BlockPos start = glassPos.up();
        if (world.getBlockState(start).isAir()) {
            if (showActionBar) {
                player.sendMessage(new LiteralText("§cПустая строка: над стеклом воздух"), true);
            }
            return false;
        }
        String dim = world.getRegistryKey().getValue().toString();
        CodeSelectorStore.ToggleResult toggled = CodeSelectorStore.toggle(
            SELECTED,
            dim,
            glassPos.getX(),
            glassPos.getY(),
            glassPos.getZ()
        );
        if (showActionBar) {
            player.sendMessage(new LiteralText((toggled.added() ? "§aВыбрано строк: " : "§eУбрано строк: ") + toggled.selectedCount()), true);
        }
        return true;
    }

    private static BlockPos resolveCodeSelectorGlassPos1165(net.minecraft.world.World world, BlockPos clicked) {
        if (world == null || clicked == null) {
            return null;
        }
        BlueGlassSearch.Probe probe = new BlueGlassSearch.Probe() {
            @Override
            public boolean isBlueGlass(int x, int y, int z) {
                return FabricBridge.isBlueGlass(MinecraftClient.getInstance(), x, y, z);
            }

            @Override
            public boolean isFree(int x, int y, int z) {
                return FabricBridge.isFreeGlass(MinecraftClient.getInstance(), x, y, z);
            }
        };
        BlockPosView glass = BlueGlassSearch.resolveClickedGlass(
            new BlockPosView(clicked.getX(), clicked.getY(), clicked.getZ()),
            probe
        );
        return glass == null ? null : new BlockPos(glass.x(), glass.y(), glass.z());
    }

    private static boolean isCodeSelectorItem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        try {
            return stack.getTag() != null && stack.getTag().contains(CODE_SELECTOR_TAG) && stack.getTag().getBoolean(CODE_SELECTOR_TAG);
        } catch (Exception ignore) {
            return false;
        }
    }

    private static boolean hasCodeSelectorInInventory1165(MinecraftClient mc) {
        if (mc == null || mc.player == null || mc.player.inventory == null) {
            return false;
        }
        try {
            for (int i = 0; i < mc.player.inventory.size(); i++) {
                if (isCodeSelectorItem(mc.player.inventory.getStack(i))) {
                    return true;
                }
            }
        } catch (Exception ignore) {
        }
        return false;
    }

    private static int clearSelection(FabricClientCommandSource source) {
        int count = SELECTED.size();
        SELECTED.clear();
        source.sendFeedback(new LiteralText("Selection cleared: " + count));
        return 1;
    }

    private static int listSelection(FabricClientCommandSource source) {
        if (SELECTED.isEmpty()) {
            source.sendFeedback(new LiteralText("Selection is empty."));
            return 1;
        }
        source.sendFeedback(new LiteralText("Selected rows: " + SELECTED.size()));
        for (SelectedRow block : SELECTED.values()) {
            source.sendFeedback(new LiteralText(" - " + block.dimension() + " [" + block.x() + "," + block.y() + "," + block.z() + "]"));
        }
        return 1;
    }

    private static int inspectPlan(FabricClientCommandSource source, Path planPath) {
        Path path = planPath.isAbsolute() ? planPath : runDir().resolve(planPath);
        if (!Files.exists(path)) {
            source.sendError(new LiteralText("Plan not found: " + path));
            return 0;
        }
        try {
            JsonObject obj = new JsonParser().parse(Files.newBufferedReader(path)).getAsJsonObject();
            int entries = obj.has("entries") && obj.get("entries").isJsonArray()
                ? obj.getAsJsonArray("entries").size()
                : 0;
            source.sendFeedback(new LiteralText("Plan: " + path + " entries=" + entries));
            return 1;
        } catch (Exception e) {
            source.sendError(new LiteralText("Plan parse failed: " + e.getMessage()));
            return 0;
        }
    }

    private static Path runDir() {
        return MinecraftClient.getInstance().runDirectory.toPath();
    }

    private static Path defaultPlanPath() {
        return runDir().resolve("plan.json");
    }

    private static ModSettingsService settings() {
        ModSettingsService s = SETTINGS;
        if (s != null) {
            return s;
        }
        synchronized (BetterCodeFabric1165.class) {
            if (SETTINGS == null) {
                SETTINGS = ModSettingsService.createDefault(runDir());
            }
            return SETTINGS;
        }
    }

    private static RuntimeCore runtime() {
        RuntimeCore r = RUNTIME;
        if (r != null) {
            return r;
        }
        synchronized (BetterCodeFabric1165.class) {
            if (RUNTIME == null) {
                FabricCoreLogger logger = new FabricCoreLogger();
                RUNTIME = new RuntimeCore(logger, settings());
                preloadMenuHintsFromEmbeddedTables(logger);
            }
            return RUNTIME;
        }
    }

    private static void preloadMenuHintsFromEmbeddedTables(CoreLogger logger) {
        if (MENU_HINTS_PRELOADED) {
            return;
        }
        synchronized (BetterCodeFabric1165.class) {
            if (MENU_HINTS_PRELOADED) {
                return;
            }
            int links = 0;
            int records = 0;
            String source = "embedded:regalltables/1.16.5/tablesexport.current.txt";
            try (InputStream in = BetterCodeFabric1165.class.getClassLoader()
                .getResourceAsStream("regalltables/1.16.5/tablesexport.current.txt")) {
                if (in == null) {
                    logger.warn("printer-debug", "menu_hint_preload status=missing source=" + source);
                    MENU_HINTS_PRELOADED = true;
                    return;
                }
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8))) {
                    String path = "";
                    String item = "";
                    String type = "";
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (line.startsWith("path=")) {
                            path = line.substring("path=".length()).trim();
                        } else if (line.startsWith("item=")) {
                            item = line.substring("item=".length()).trim();
                        } else if (line.startsWith("type=")) {
                            type = line.substring("type=".length()).trim();
                            if (!item.isEmpty() && ("action".equals(type) || "category".equals(type))) {
                                links += preloadPathLinks(path, item);
                                records++;
                            }
                            path = "";
                            item = "";
                            type = "";
                        }
                    }
                }
                logger.info("printer-debug", "menu_hint_preload status=ok source=" + source + " records=" + records + " links=" + links);
            } catch (Exception e) {
                logger.warn("printer-debug", "menu_hint_preload status=error source=" + source
                    + " err=" + e.getClass().getSimpleName());
            } finally {
                MENU_HINTS_PRELOADED = true;
            }
        }
    }

    private static int preloadPathLinks(String rawPath, String rawItem) {
        ArrayList<String> chain = new ArrayList<String>();
        String path = safeText(rawPath);
        if (!path.isEmpty()) {
            String[] parts = path.split(">");
            for (String p : parts) {
                String v = safeText(p).trim();
                if (!v.isEmpty()) {
                    chain.add(v);
                }
            }
        }
        int added = 0;
        for (int i = 0; i + 1 < chain.size(); i++) {
            added += PlaceRuntimeStepExecutor.preloadMenuHint(chain.get(i), chain.get(i + 1));
        }
        if (!chain.isEmpty()) {
            added += PlaceRuntimeStepExecutor.preloadMenuHint(chain.get(chain.size() - 1), safeText(rawItem));
        }
        return added;
    }

    private static int modSettingsList(FabricClientCommandSource source) {
        source.sendFeedback(new LiteralText("§b[modsettings] list"));
        for (SettingDef d : settings().defs().values()) {
            source.sendFeedback(settingLine(d));
        }
        return 1;
    }

    private static int modSettingsShow(FabricClientCommandSource source, String key) {
        SettingDef d = settings().defs().get(key);
        if (d == null) {
            source.sendError(new LiteralText("[modsettings] unknown key: " + key));
            return 0;
        }
        source.sendFeedback(new LiteralText("§b[modsettings] " + d.key() + " = " + formatValue(settings().getRaw(d.key()))));
        source.sendFeedback(new LiteralText("§7" + d.description()));
        source.sendFeedback(settingControls(d));
        return 1;
    }

    private static int modSettingsToggle(FabricClientCommandSource source, String key) {
        String err = settings().toggle(key);
        if (err != null) {
            source.sendError(new LiteralText("[modsettings] toggle failed: " + err));
            return 0;
        }
        source.sendFeedback(new LiteralText("[modsettings] " + key + " = " + formatValue(settings().getRaw(key))));
        return 1;
    }

    private static int modSettingsInc(FabricClientCommandSource source, String key, int delta) {
        String err = settings().increment(key, delta);
        if (err != null) {
            source.sendError(new LiteralText("[modsettings] inc failed: " + err));
            return 0;
        }
        source.sendFeedback(new LiteralText("[modsettings] " + key + " = " + formatValue(settings().getRaw(key))));
        return 1;
    }

    private static int modSettingsSet(FabricClientCommandSource source, String key, String value) {
        String err = settings().setFromString(key, value);
        if (err != null) {
            source.sendError(new LiteralText("[modsettings] set failed: " + err));
            return 0;
        }
        source.sendFeedback(new LiteralText("[modsettings] " + key + " = " + formatValue(settings().getRaw(key))));
        return 1;
    }

    private static MutableText settingLine(SettingDef d) {
        MutableText line = new LiteralText("§e" + d.key() + "§7 = §f" + formatValue(settings().getRaw(d.key())) + " ");
        line.append(settingControls(d));
        return line;
    }

    private static MutableText settingControls(SettingDef d) {
        if (d.type() == SettingType.BOOLEAN) {
            MutableText t = new LiteralText("");
            t.append(clickToken("[ON]", "/modsettings set " + d.key() + " true"));
            t.append(new LiteralText(" "));
            t.append(clickToken("[OFF]", "/modsettings set " + d.key() + " false"));
            return t;
        }
        if (d.type() == SettingType.INTEGER) {
            MutableText t = new LiteralText("");
            t.append(clickToken("[-10]", "/modsettings inc " + d.key() + " -10"));
            t.append(new LiteralText(" "));
            t.append(clickToken("[-1]", "/modsettings inc " + d.key() + " -1"));
            t.append(new LiteralText(" "));
            t.append(clickToken("[+1]", "/modsettings inc " + d.key() + " 1"));
            t.append(new LiteralText(" "));
            t.append(clickToken("[+10]", "/modsettings inc " + d.key() + " 10"));
            return t;
        }
        MutableText t = new LiteralText("");
        MutableText set = new LiteralText("[SET]");
        set.setStyle(Style.EMPTY.withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/modsettings set " + d.key() + " ")));
        t.append(set);
        return t;
    }

    private static MutableText clickToken(String label, String command) {
        MutableText t = new LiteralText(label);
        t.setStyle(Style.EMPTY.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command)));
        return t;
    }

    private static String formatValue(Object v) {
        return v == null ? "<null>" : String.valueOf(v);
    }

    private static final class DirectPlaceState {
        boolean active;
        int cursor;
        BlockPos seed;
        BlockPos startSeed;
        BlockPos pendingRowSeed;
        String dimension;
        String failReason;
        BlockPos lastEntryTarget;
        BlockPos pendingTarget;
        String pendingBlockId;
        long pendingSinceMs;
        long lastPlaceAttemptMs;
        int placeAttempts;
        long tpSettleUntilMs;
        long lastPlaceWorldTraceMs;
        final Set<String> usedRowSeeds = new HashSet<String>();

        void reset() {
            active = false;
            cursor = 0;
            seed = null;
            startSeed = null;
            pendingRowSeed = null;
            dimension = null;
            failReason = "";
            lastEntryTarget = null;
            pendingTarget = null;
            pendingBlockId = "";
            pendingSinceMs = 0L;
            lastPlaceAttemptMs = 0L;
            placeAttempts = 0;
            tpSettleUntilMs = 0L;
            lastPlaceWorldTraceMs = 0L;
            usedRowSeeds.clear();
        }
    }

    private static final class RegAllTablesState {
        boolean active = false;
        boolean selectMode = false;
        String dimension = "";
        int signX = 0;
        int signY = 0;
        int signZ = 0;
        String phase = "IDLE";
        long startedMs = 0L;
        long nextActionMs = 0L;
        long lastProgressMs = 0L;
        int noProgressRecoverCount = 0;
        int replayIndex = 0;
        List<String> currentPath = new ArrayList<String>();
        final Deque<List<String>> pendingMenus = new ArrayDeque<List<String>>();
        final Set<String> queuePathKeys = new HashSet<String>();
        final Set<String> visitedMenuKeys = new HashSet<String>();
        final Map<String, Set<String>> testedSlotKeysByMenu = new LinkedHashMap<String, Set<String>>();
        final List<RegAllTablesRecord> records = new ArrayList<RegAllTablesRecord>();
        String pendingMenuKey = "";
        SlotView pendingSlot = null;
        List<SlotView> pendingMenuSnapshot = new ArrayList<SlotView>();
        int pendingWindowId = -1;
        String pendingMenuSignature = "";
        long pendingClickMs = 0L;
        final Set<String> recordKeys = new HashSet<String>();

        void reset() {
            active = false;
            selectMode = false;
            dimension = "";
            signX = 0;
            signY = 0;
            signZ = 0;
            phase = "IDLE";
            startedMs = 0L;
            nextActionMs = 0L;
            lastProgressMs = 0L;
            noProgressRecoverCount = 0;
            replayIndex = 0;
            currentPath = new ArrayList<String>();
            pendingMenus.clear();
            queuePathKeys.clear();
            visitedMenuKeys.clear();
            testedSlotKeysByMenu.clear();
            records.clear();
            pendingMenuKey = "";
            pendingSlot = null;
            pendingMenuSnapshot = new ArrayList<SlotView>();
            pendingWindowId = -1;
            pendingMenuSignature = "";
            pendingClickMs = 0L;
            recordKeys.clear();
        }
    }

    private static final class RegAllTablesRecord {
        final String path;
        final String item;
        final String itemId;
        final String type;

        private RegAllTablesRecord(String path, String item, String itemId, String type) {
            this.path = path == null ? "" : path;
            this.item = item == null ? "" : item;
            this.itemId = itemId == null ? "" : itemId;
            this.type = type == null ? "" : type;
        }
    }

    private static final class NearbySignCacheState {
        PublishCacheView cache = new PublishCacheView();
        boolean loaded = false;
        boolean dirty = false;
        String dim = "";
        int centerX = 0;
        int centerY = 0;
        int centerZ = 0;
        long nextScanMs = 0L;
        long lastSaveMs = 0L;
        long lastChangeMs = 0L;
        long lastLogMs = 0L;
        final Deque<BlockPos> queue = new ArrayDeque<BlockPos>();

        void resetRuntime() {
            dim = "";
            centerX = 0;
            centerY = 0;
            centerZ = 0;
            nextScanMs = 0L;
            lastChangeMs = 0L;
            lastLogMs = 0L;
            queue.clear();
        }
    }

    private static final class FabricCoreLogger implements CoreLogger {
        @Override
        public void info(String tag, String message) {
            System.out.println("[" + tag + "] " + message);
        }

        @Override
        public void warn(String tag, String message) {
            System.out.println("[" + tag + "] WARN " + message);
        }

        @Override
        public void error(String tag, String message) {
            System.err.println("[" + tag + "] ERROR " + message);
        }
    }

    private static final class FabricBridge implements GameBridge {
        private final FabricClientCommandSource source;

        private FabricBridge(FabricClientCommandSource source) {
            this.source = source;
        }

        @Override
        public String currentDimension() {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.world == null) {
                return "unknown";
            }
            return mc.world.getRegistryKey().getValue().toString();
        }

        @Override
        public Path runDirectory() {
            return MinecraftClient.getInstance().runDirectory.toPath();
        }

        @Override
        public List<String> scoreboardLines() {
            List<String> out = new ArrayList<>();
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.world == null) {
                return out;
            }
            Scoreboard scoreboard = mc.world.getScoreboard();
            ScoreboardObjective sidebar = scoreboard.getObjectiveForSlot(1);
            if (sidebar == null) {
                return out;
            }
            out.add(sidebar.getDisplayName().getString());
            List<ScoreboardPlayerScore> scores = new ArrayList<>(scoreboard.getAllPlayerScores(sidebar));
            scores.removeIf(score -> score == null || score.getPlayerName() == null || score.getPlayerName().startsWith("#"));
            scores.sort(Comparator.comparingInt(ScoreboardPlayerScore::getScore));
            int from = Math.max(0, scores.size() - 15);
            for (int i = from; i < scores.size(); i++) {
                ScoreboardPlayerScore score = scores.get(i);
                Team team = scoreboard.getPlayerTeam(score.getPlayerName());
                String line = Team.decorateName(team, new LiteralText(score.getPlayerName())).getString();
                if (line == null) {
                    continue;
                }
                out.add("[" + score.getScore() + "] " + line);
            }
            return out;
        }

        @Override
        public boolean supportsPlacePlanExecution() {
            return true;
        }

        @Override
        public void onExecutionStart(int totalSteps) {
            synchronized (DIRECT_PLACE_STATE) {
                DIRECT_PLACE_STATE.reset();
                MinecraftClient mc = MinecraftClient.getInstance();
                if (mc.world == null || mc.player == null || mc.interactionManager == null) {
                    DIRECT_PLACE_STATE.failReason = "player/world/interactionManager unavailable";
                    return;
                }
                BlockPos seed = resolveSeed(mc);
                if (seed == null) {
                    DIRECT_PLACE_STATE.failReason = "no valid light_blue_stained_glass row seed";
                    return;
                }
                DIRECT_PLACE_STATE.seed = seed;
                DIRECT_PLACE_STATE.startSeed = seed;
                DIRECT_PLACE_STATE.dimension = mc.world.getRegistryKey().getValue().toString();
                DIRECT_PLACE_STATE.cursor = 0;
                DIRECT_PLACE_STATE.active = true;
                DIRECT_PLACE_STATE.usedRowSeeds.add(seedKey(seed));
                System.out.println("[printer-debug] direct_runtime_start seed=" + seed + " dim=" + DIRECT_PLACE_STATE.dimension + " steps=" + totalSteps);
            }
        }

        @Override
        public void onExecutionStop() {
            synchronized (DIRECT_PLACE_STATE) {
                DIRECT_PLACE_STATE.reset();
            }
        }

        @Override
        public void onRuntimeStepCompleted(PlaceRuntimeEntry entry) {
            if (entry == null || entry.isPause() || entry.isSkip() || entry.moveOnly()) {
                return;
            }
            synchronized (DIRECT_PLACE_STATE) {
                if (!DIRECT_PLACE_STATE.active) {
                    return;
                }
                DIRECT_PLACE_STATE.cursor++;
                DIRECT_PLACE_STATE.lastEntryTarget = null;
                clearPendingPlaceState(DIRECT_PLACE_STATE);
                System.out.println("[printer-debug] cursor_advance reason=step_completed cursor=" + DIRECT_PLACE_STATE.cursor
                    + " block=" + (entry.blockId() == null ? "" : entry.blockId()));
            }
        }

        @Override
        public PlaceExecResult executePlacePlan(List<PlaceOp> ops, boolean checkOnly) {
            if (ops == null || ops.isEmpty()) {
                return PlaceExecResult.fail(0, 0, "PARSE_SCHEMA_MISMATCH", "no place operations");
            }
            if (checkOnly) {
                return PlaceExecResult.ok(ops.size());
            }
            int executed = 0;
            for (int i = 0; i < ops.size(); i++) {
                PlaceOp op = ops.get(i);
                PlaceRuntimeEntry step = op.kind() == PlaceOp.Kind.AIR
                    ? new PlaceRuntimeEntry(true, false, "minecraft:air", "", "", java.util.Collections.<com.rainbow_universe.bettercode.core.place.PlaceArgSpec>emptyList(), false)
                    : op.kind() == PlaceOp.Kind.SKIP
                        ? new PlaceRuntimeEntry(false, true, "skip", "", "", java.util.Collections.<com.rainbow_universe.bettercode.core.place.PlaceArgSpec>emptyList(), false)
                        : new PlaceRuntimeEntry(false, false, op.blockId(), op.name(), op.args(), java.util.Collections.<com.rainbow_universe.bettercode.core.place.PlaceArgSpec>emptyList(), false);
                PlaceExecResult stepRes = executePlaceStep(step, false);
                if (!stepRes.ok()) {
                    return PlaceExecResult.fail(executed, i, stepRes.errorCode(), stepRes.errorMessage());
                }
                executed++;
            }
            return PlaceExecResult.ok(executed);
        }

        @Override
        public PlaceExecResult executePlaceStep(PlaceRuntimeEntry entry, boolean checkOnly) {
            if (entry == null) {
                return PlaceExecResult.fail(0, 0, "PARSE_SCHEMA_MISMATCH", "null runtime entry");
            }
            if (checkOnly) {
                return PlaceExecResult.ok(1);
            }
            if (entry.isPause()) {
                return PlaceExecResult.ok(1);
            }
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.world == null || mc.player == null || mc.interactionManager == null) {
                return PlaceExecResult.fail(0, 0, "CLIENT_CONTEXT_MISSING", "player/world/interactionManager unavailable");
            }
            synchronized (DIRECT_PLACE_STATE) {
                long now = System.currentTimeMillis();
                if (!DIRECT_PLACE_STATE.active || DIRECT_PLACE_STATE.seed == null) {
                    return PlaceExecResult.fail(0, 0, "DIRECT_RUNTIME_NOT_READY",
                        DIRECT_PLACE_STATE.failReason == null || DIRECT_PLACE_STATE.failReason.isEmpty()
                            ? "direct runtime not started; call /confirmload again"
                            : DIRECT_PLACE_STATE.failReason);
                }

                if (entry.isSkip() || entry.moveOnly()) {
                    BlockPos skipEntry = DIRECT_PLACE_STATE.seed.add(-2 * DIRECT_PLACE_STATE.cursor, 1, 0);
                    DIRECT_PLACE_STATE.lastEntryTarget = skipEntry;
                    double standX = skipEntry.getX() + 0.5;
                    double standY = skipEntry.getY();
                    double standZ = skipEntry.getZ() - 2.0 + 0.5;
                    if (mc.player.squaredDistanceTo(standX, standY, standZ) > 6.0D) {
                        if (isTpPathBusy()) {
                            return PlaceExecResult.inProgress(0, "WAIT_TP_PATH_SKIP");
                        }
                        boolean tpQueued = enqueueTpPath(skipEntry.getX(), skipEntry.getY(), skipEntry.getZ() - 2);
                        if (!tpQueued) {
                            return PlaceExecResult.fail(0, 0, "TP_PATH_FAILED",
                                "cannot tp to skip target=" + skipEntry);
                        }
                        DIRECT_PLACE_STATE.tpSettleUntilMs = now + PLACE_TP_SETTLE_MS;
                        return PlaceExecResult.inProgress(0, "WAIT_TP_PATH_SKIP");
                    }
                    if (now < DIRECT_PLACE_STATE.tpSettleUntilMs) {
                        return PlaceExecResult.inProgress(0, "WAIT_TP_SETTLE_SKIP");
                    }
                    DIRECT_PLACE_STATE.cursor++;
                    System.out.println("[printer-debug] direct_skip_step cursor=" + DIRECT_PLACE_STATE.cursor);
                    return PlaceExecResult.ok(1);
                }
                if (isNewlineControl(entry)) {
                    return advanceToNextRowSeed(mc, now);
                }

                // Core owns menu/args flow, but adapter must still place/re-place the block for the same entry.
                if (entry.forceRePlaceRequested()) {
                    // Re-place must stay on the same runtime step index.
                    // Cursor rollback here causes drift to previous block (legacy mismatch).
                    clearPendingPlaceState(DIRECT_PLACE_STATE);
                    entry.setForceRePlaceRequested(false);
                }
                boolean menuPayload = hasMenuPayload(entry);

                String sourceBlockId = entry.blockId() == null ? "" : entry.blockId().trim();
                String blockId = normalizeBlockIdForRuntime(sourceBlockId);
                Identifier id;
                try {
                    id = new Identifier(blockId);
                } catch (Exception ex) {
                    return PlaceExecResult.fail(0, 0, "INVALID_BLOCK_ID", "invalid block id: " + sourceBlockId);
                }
                Block block = Registry.BLOCK.get(id);
                if (block == null || block.asItem() == null || block.asItem() == ItemStack.EMPTY.getItem()) {
                    return PlaceExecResult.fail(0, 0, "INVALID_BLOCK_ID", "unknown block: " + sourceBlockId);
                }

                Item item = block.asItem();
                int slot = findHotbarSlot(mc, item);
                if (slot < 0) {
                    slot = ensureBlockInHotbar(mc, item, sourceBlockId);
                }
                if (slot < 0) {
                    return PlaceExecResult.fail(0, 0, "MISSING_REQUIRED_ITEM", "required item not in hotbar: " + sourceBlockId);
                }
                if (mc.player.inventory.selectedSlot != slot) {
                    mc.player.inventory.selectedSlot = slot;
                }

                BlockPos entryPos = DIRECT_PLACE_STATE.seed.add(-2 * DIRECT_PLACE_STATE.cursor, 1, 0);
                DIRECT_PLACE_STATE.lastEntryTarget = entryPos;
                BlockPos target = entryPos.down();
                double standX = entryPos.getX() + 0.5;
                double standY = entryPos.getY();
                double standZ = entryPos.getZ() - 2.0 + 0.5;
                String expectedBlockId = String.valueOf(Registry.BLOCK.getId(block));
                if (DIRECT_PLACE_STATE.placeAttempts == 0
                    || DIRECT_PLACE_STATE.lastPlaceAttemptMs <= 0L
                    || now - DIRECT_PLACE_STATE.lastPlaceAttemptMs >= 450L) {
                    System.out.println("[printer-debug] place_step begin cursor=" + DIRECT_PLACE_STATE.cursor
                        + " seed=" + DIRECT_PLACE_STATE.seed
                        + " entry=" + entryPos
                        + " target=" + target
                        + " block=" + sourceBlockId
                        + " blockNormalized=" + blockId
                        + " expected=" + expectedBlockId
                        + " pendingTarget=" + DIRECT_PLACE_STATE.pendingTarget
                        + " pendingBlock=" + DIRECT_PLACE_STATE.pendingBlockId
                        + " attempts=" + DIRECT_PLACE_STATE.placeAttempts);
                }

                if (mc.player.squaredDistanceTo(standX, standY, standZ) > 6.0D) {
                    if (isTpPathBusy()) {
                        return PlaceExecResult.inProgress(0, "WAIT_TP_PATH");
                    }
                    boolean tpQueued = enqueueTpPath(entryPos.getX(), entryPos.getY(), entryPos.getZ() - 2);
                    if (!tpQueued) {
                        return PlaceExecResult.fail(0, 0, "TP_PATH_FAILED",
                            "cannot tp to entry.z-2 for place target=" + entryPos);
                    }
                    DIRECT_PLACE_STATE.tpSettleUntilMs = now + PLACE_TP_SETTLE_MS;
                    return PlaceExecResult.inProgress(0, "WAIT_TP_PATH");
                }
                if (now < DIRECT_PLACE_STATE.tpSettleUntilMs) {
                    return PlaceExecResult.inProgress(0, "WAIT_TP_SETTLE");
                }

                maybeTracePlaceWorldState(mc, entryPos, target, block, now, "before_confirm");

                if (isBlockPlaced(mc, target, block)) {
                    clearPendingPlaceState(DIRECT_PLACE_STATE);
                    if (!menuPayload) {
                        DIRECT_PLACE_STATE.cursor++;
                    }
                    System.out.println("[printer-debug] direct_step_confirmed block=" + sourceBlockId + " target=" + target + " cursor=" + DIRECT_PLACE_STATE.cursor);
                    return PlaceExecResult.ok(1);
                }

                if (isSamePending(DIRECT_PLACE_STATE, target, expectedBlockId)) {
                    if (DIRECT_PLACE_STATE.pendingSinceMs > 0L && now - DIRECT_PLACE_STATE.pendingSinceMs > 6000L) {
                        return PlaceExecResult.fail(0, 0, "PLACE_CONFIRM_TIMEOUT",
                            "block was not confirmed at target=" + target + " expected=" + expectedBlockId
                                + " attempts=" + DIRECT_PLACE_STATE.placeAttempts);
                    }
                    if (DIRECT_PLACE_STATE.lastPlaceAttemptMs > 0L && now - DIRECT_PLACE_STATE.lastPlaceAttemptMs < 160L) {
                        return PlaceExecResult.inProgress(0, "PLACE_WAIT_CONFIRM");
                    }
                } else {
                    DIRECT_PLACE_STATE.pendingTarget = target;
                    DIRECT_PLACE_STATE.pendingBlockId = expectedBlockId;
                    DIRECT_PLACE_STATE.pendingSinceMs = now;
                    DIRECT_PLACE_STATE.lastPlaceAttemptMs = 0L;
                    DIRECT_PLACE_STATE.placeAttempts = 0;
                }

                ClickResult result = clickBlockLegacy(target.getX(), target.getY(), target.getZ(), "place_block", true);
                DIRECT_PLACE_STATE.lastPlaceAttemptMs = now;
                DIRECT_PLACE_STATE.placeAttempts++;
                String currentTargetId = blockIdAt(mc, target);
                String currentUpId = blockIdAt(mc, target.up());
                String currentDownId = blockIdAt(mc, target.down());
                boolean clickAccepted = result != null && result.accepted();
                if (!clickAccepted || DIRECT_PLACE_STATE.placeAttempts == 1 || DIRECT_PLACE_STATE.placeAttempts % 4 == 0) {
                    System.out.println("[printer-debug] PLACE_CLICK_RESULT accepted="
                        + clickAccepted
                        + " reason=" + (result == null ? "null" : result.reason())
                        + " ack=" + (result == null ? "null" : result.ackState())
                        + " attempt=" + DIRECT_PLACE_STATE.placeAttempts
                        + " target=" + target
                        + " blocks={down:" + currentDownId + ",target:" + currentTargetId + ",up:" + currentUpId + "}"
                        + " playerPos=" + mc.player.getX() + "," + mc.player.getY() + "," + mc.player.getZ()
                        + " distToStandSq=" + mc.player.squaredDistanceTo(standX, standY, standZ)
                        + " tpBusy=" + isTpPathBusyNow());
                }
                if (result == null || !result.accepted()) {
                    if (DIRECT_PLACE_STATE.pendingSinceMs > 0L && now - DIRECT_PLACE_STATE.pendingSinceMs > 6000L) {
                        return PlaceExecResult.fail(0, 0, "PLACE_INTERACT_REJECTED",
                            "interactBlock rejected too long at target=" + target
                                + " result=" + String.valueOf(result) + " attempts=" + DIRECT_PLACE_STATE.placeAttempts);
                    }
                    return PlaceExecResult.inProgress(0, "PLACE_RETRY_INTERACT");
                }
                return PlaceExecResult.inProgress(0, "PLACE_WAIT_CONFIRM");
            }
        }

        private static void maybeTracePlaceWorldState(MinecraftClient mc, BlockPos entryPos, BlockPos target, Block expected, long now, String stage) {
            if (mc == null || mc.player == null || mc.world == null || target == null) {
                return;
            }
            if (now - DIRECT_PLACE_STATE.lastPlaceWorldTraceMs < PLACE_WORLD_TRACE_GAP_MS) {
                return;
            }
            DIRECT_PLACE_STATE.lastPlaceWorldTraceMs = now;
            String expectedId = expected == null ? "?" : String.valueOf(Registry.BLOCK.getId(expected));
            String targetId = blockIdAt(mc, target);
            String upId = blockIdAt(mc, target.up());
            String downId = blockIdAt(mc, target.down());
            System.out.println("[printer-debug] PLACE_WORLD_STATE stage=" + stage
                + " entry=" + entryPos
                + " target=" + target
                + " expected=" + expectedId
                + " blocks={down:" + downId + ",target:" + targetId + ",up:" + upId + "}"
                + " playerPos=" + mc.player.getX() + "," + mc.player.getY() + "," + mc.player.getZ()
                + " onGround=" + mc.player.isOnGround()
                + " tpBusy=" + isTpPathBusyNow()
                + " attempts=" + DIRECT_PLACE_STATE.placeAttempts);
        }

        private static String blockIdAt(MinecraftClient mc, BlockPos pos) {
            if (mc == null || mc.world == null || pos == null) {
                return "unknown";
            }
            try {
                return String.valueOf(Registry.BLOCK.getId(mc.world.getBlockState(pos).getBlock()));
            } catch (Exception ignore) {
                return "err";
            }
        }

        private static boolean isTpPathBusyNow() {
            synchronized (LOCAL_TP_STATE) {
                return !LOCAL_TP_STATE.queue.isEmpty();
            }
        }

        private static BlockPos resolveSeed(MinecraftClient mc) {
            if (mc == null || mc.player == null || mc.world == null) {
                return null;
            }
            BlueGlassSearch.Probe probe = new BlueGlassSearch.Probe() {
                @Override
                public boolean isBlueGlass(int x, int y, int z) {
                    return BetterCodeFabric1165.FabricBridge.isBlueGlass(mc, x, y, z);
                }

                @Override
                public boolean isFree(int x, int y, int z) {
                    return BetterCodeFabric1165.FabricBridge.isFreeGlass(mc, x, y, z);
                }
            };
            BlockPos fromPlayerAnchor = resolveSeedFromPlayerAnchor(mc, probe);
            if (fromPlayerAnchor != null) {
                return fromPlayerAnchor;
            }
            BlockPos fromWorldHint = resolveSeedFromWorldHint(mc, probe);
            if (fromWorldHint != null) {
                return fromWorldHint;
            }
            String dim = mc.world.getRegistryKey().getValue().toString();
            List<BlockPosView> seedGlasses = new ArrayList<BlockPosView>();
            for (SelectedRow s : SELECTED.values()) {
                if (s == null) {
                    continue;
                }
                if (!dim.equals(s.dimension())) {
                    continue;
                }
                SelectedRow normalized = SelectedRowNormalizer.normalizeToGlassAnchor(
                    s,
                    (x, y, z) -> isBlueGlass(mc, x, y, z)
                );
                if (normalized == null) {
                    continue;
                }
                if (normalized.y() != s.y()) {
                    System.out.println("[printer-debug] seed_normalized source=selected_entry_to_glass dim=" + s.dimension()
                        + " from=" + s.x() + "," + s.y() + "," + s.z()
                        + " to=" + normalized.x() + "," + normalized.y() + "," + normalized.z());
                }
                seedGlasses.add(new BlockPosView(normalized.x(), normalized.y(), normalized.z()));
            }
            if (seedGlasses.isEmpty()) {
                return null;
            }
            BlockPosView nearestAny = BlueGlassSearch.chooseNearestSeed(seedGlasses, probe,
                p -> mc.player.squaredDistanceTo(p.x() + 0.5, p.y() + 0.5, p.z() + 0.5));
            return nearestAny == null ? null : new BlockPos(nearestAny.x(), nearestAny.y(), nearestAny.z());
        }

        private static BlockPos resolveSeedFromPlayerAnchor(MinecraftClient mc, BlueGlassSearch.Probe probe) {
            if (mc == null || mc.player == null || probe == null) {
                return null;
            }
            BlockPos playerPos = mc.player.getBlockPos();
            int sx = playerPos.getX() - 2;
            int sy = playerPos.getY() - 1;
            int sz = playerPos.getZ() - 2;
            BlockPosView anchor = null;
            String source = "offset(-2,-1,-2)";
            if (probe.isBlueGlass(sx, sy, sz)) {
                anchor = new BlockPosView(sx, sy, sz);
            } else {
                source = "offset_fallbacks";
                anchor = BlueGlassSearch.resolveClickedGlass(
                    new BlockPosView(playerPos.getX(), playerPos.getY(), playerPos.getZ()),
                    probe
                );
            }
            if (anchor == null) {
                System.out.println("[printer-debug] seed_player_anchor_miss player=" + playerPos + " offsetHint=-2,-1,-2");
                return null;
            }
            java.util.ArrayList<BlockPosView> seeds = new java.util.ArrayList<BlockPosView>();
            seeds.add(anchor);
            BlockPosView nearest = BlueGlassSearch.chooseNearestSeed(seeds, probe,
                p -> mc.player.squaredDistanceTo(p.x() + 0.5, p.y() + 0.5, p.z() + 0.5));
            if (nearest == null) {
                return null;
            }
            System.out.println("[printer-debug] seed_player_anchor_hit player=" + playerPos
                + " source=" + source
                + " anchor=" + anchor.x() + "," + anchor.y() + "," + anchor.z()
                + " chosen=" + nearest.x() + "," + nearest.y() + "," + nearest.z());
            return new BlockPos(nearest.x(), nearest.y(), nearest.z());
        }

        private static BlockPos resolveSeedFromWorldHint(MinecraftClient mc, BlueGlassSearch.Probe probe) {
            if (mc == null || mc.player == null || mc.world == null || probe == null) {
                return null;
            }
            BlockPos hint = LAST_WORLD_SEED_HINT;
            if (hint == null) {
                return null;
            }
            String dim = mc.world.getRegistryKey().getValue().toString();
            if (!dim.equals(LAST_WORLD_SEED_HINT_DIM)) {
                return null;
            }
            if (!probe.isBlueGlass(hint.getX(), hint.getY(), hint.getZ())) {
                return null;
            }
            ArrayList<BlockPosView> seeds = new ArrayList<BlockPosView>();
            seeds.add(new BlockPosView(hint.getX(), hint.getY(), hint.getZ()));
            BlockPosView nearest = BlueGlassSearch.chooseNearestSeed(seeds, probe,
                p -> mc.player.squaredDistanceTo(p.x() + 0.5, p.y() + 0.5, p.z() + 0.5));
            if (nearest == null) {
                return null;
            }
            System.out.println("[printer-debug] seed_world_hint_hit dim=" + dim
                + " hint=" + hint
                + " chosen=" + nearest.x() + "," + nearest.y() + "," + nearest.z());
            return new BlockPos(nearest.x(), nearest.y(), nearest.z());
        }

        private static boolean isBlueGlass(MinecraftClient mc, int x, int y, int z) {
            if (mc == null || mc.world == null) {
                return false;
            }
            try {
                BlockPos glass = new BlockPos(x, y, z);
                return mc.world.isChunkLoaded(glass)
                    && mc.world.getBlockState(glass).getBlock() == Blocks.LIGHT_BLUE_STAINED_GLASS;
            } catch (Exception e) {
                return false;
            }
        }

        private static boolean isFreeGlass(MinecraftClient mc, int x, int y, int z) {
            if (!isBlueGlass(mc, x, y, z)) {
                return false;
            }
            try {
                BlockPos glass = new BlockPos(x, y, z);
                return mc.world.getBlockState(glass.up()).isAir();
            } catch (Exception e) {
                return false;
            }
        }

        private static int findHotbarSlot(MinecraftClient mc, Item item) {
            if (mc.player == null || item == null) {
                return -1;
            }
            for (int i = 0; i < 9; i++) {
                ItemStack st = mc.player.inventory.getStack(i);
                if (st != null && !st.isEmpty() && st.getItem() == item) {
                    return i;
                }
            }
            return -1;
        }

        private static int ensureBlockInHotbar(MinecraftClient mc, Item item, String sourceBlockId) {
            if (mc == null || mc.player == null || mc.interactionManager == null || item == null) {
                return -1;
            }
            if (!isCreativePlayer(mc)) {
                return -1;
            }
            int target = findEmptyHotbarSlot(mc);
            if (target < 0) {
                target = 8;
            }
            try {
                ItemStack st = new ItemStack(item);
                mc.player.inventory.setStack(target, st);
                mc.interactionManager.clickCreativeStack(st, 36 + target);
                int resolved = findHotbarSlot(mc, item);
                if (resolved >= 0) {
                    System.out.println("[printer-debug] hotbar_autoinject_ok block=" + sourceBlockId
                        + " slot=" + resolved
                        + " item=" + String.valueOf(Registry.ITEM.getId(item)));
                    return resolved;
                }
            } catch (Exception e) {
                System.out.println("[printer-debug] hotbar_autoinject_fail block=" + sourceBlockId
                    + " reason=" + e.getClass().getSimpleName());
            }
            return -1;
        }

        private static int findEmptyHotbarSlot(MinecraftClient mc) {
            if (mc == null || mc.player == null) {
                return -1;
            }
            for (int i = 0; i < 9; i++) {
                ItemStack st = mc.player.inventory.getStack(i);
                if (st == null || st.isEmpty()) {
                    return i;
                }
            }
            return -1;
        }

        private static boolean isCreativePlayer(MinecraftClient mc) {
            if (mc == null || mc.player == null) {
                return false;
            }
            try {
                return mc.player.abilities.creativeMode;
            } catch (Exception ignore) {
            }
            return false;
        }

        private static BlockPos resolveLastPlacedTarget() {
            synchronized (DIRECT_PLACE_STATE) {
                if (!DIRECT_PLACE_STATE.active || DIRECT_PLACE_STATE.seed == null) {
                    return null;
                }
                if (DIRECT_PLACE_STATE.lastEntryTarget != null) {
                    return DIRECT_PLACE_STATE.lastEntryTarget;
                }
                if (DIRECT_PLACE_STATE.pendingTarget != null) {
                    return DIRECT_PLACE_STATE.pendingTarget.up();
                }
                int currentCursor = Math.max(0, DIRECT_PLACE_STATE.cursor);
                return DIRECT_PLACE_STATE.seed.add(-2 * currentCursor, 1, 0);
            }
        }

        private static BlockPos resolveOffsetFromLastPlaced(int dx, int dy, int dz) {
            BlockPos base = resolveLastPlacedTarget();
            if (base == null) {
                return null;
            }
            return base.add(dx, dy, dz);
        }

        private static boolean isBlockPlaced(MinecraftClient mc, BlockPos pos, Block expected) {
            if (mc == null || mc.world == null || pos == null || expected == null) {
                return false;
            }
            try {
                return mc.world.getBlockState(pos).getBlock() == expected
                    || mc.world.getBlockState(pos.up()).getBlock() == expected;
            } catch (Exception e) {
                return false;
            }
        }

        private static boolean isSamePending(DirectPlaceState state, BlockPos target, String expectedBlockId) {
            if (state == null || target == null || expectedBlockId == null) {
                return false;
            }
            if (state.pendingTarget == null || state.pendingBlockId == null) {
                return false;
            }
            return state.pendingTarget.equals(target) && expectedBlockId.equals(state.pendingBlockId);
        }

        private static void clearPendingPlaceState(DirectPlaceState state) {
            if (state == null) {
                return;
            }
            state.pendingTarget = null;
            state.pendingBlockId = "";
            state.pendingSinceMs = 0L;
            state.lastPlaceAttemptMs = 0L;
            state.placeAttempts = 0;
        }

        @Override
        public boolean executeClientCommand(String command) {
            String raw = command == null ? "" : command.trim();
            if (raw.isEmpty()) {
                System.err.println("[printer-debug] executeClientCommand: empty command");
                return false;
            }
            if (source == null) {
                System.err.println("[printer-debug] executeClientCommand: source is null for cmd=" + raw);
                return false;
            }
            try {
                int result = ClientCommandManager.DISPATCHER.execute(raw, source);
                if (result <= 0) {
                    System.err.println("[printer-debug] executeClientCommand: non-positive result=" + result + " cmd=" + raw);
                }
                return result > 0;
            } catch (Exception e) {
                System.err.println("[printer-debug] executeClientCommand: exception cmd=" + raw
                    + " type=" + e.getClass().getSimpleName()
                    + " msg=" + String.valueOf(e.getMessage()));
                return false;
            }
        }

        @Override
        public void sendChat(String message) {
            if (source != null) {
                source.sendFeedback(new LiteralText(message));
                return;
            }
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.player != null) {
                mc.player.sendMessage(new LiteralText(message), false);
            } else {
                System.out.println("[printer-debug] chat(no-player): " + message);
            }
        }

        @Override
        public void sendActionBar(String message) {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.player != null) {
                mc.player.sendMessage(new LiteralText(message), true);
            }
        }

        @Override
        public boolean openContainerIfNeeded() {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.player == null || mc.interactionManager == null || mc.world == null) {
                return false;
            }
            if (mc.currentScreen instanceof HandledScreen) {
                return true;
            }
            return openMenuAtEntryAnchor();
        }

        private static boolean hasMenuPayload(PlaceRuntimeEntry entry) {
            if (entry == null) {
                return false;
            }
            String rawName = entry.name() == null ? "" : entry.name().trim();
            String rawArgs = entry.argsRaw() == null ? "" : entry.argsRaw().trim();
            return !rawName.isEmpty() || (!rawArgs.isEmpty() && !"no".equalsIgnoreCase(rawArgs));
        }

        private static boolean isNewlineControl(PlaceRuntimeEntry entry) {
            if (entry == null) {
                return false;
            }
            String id = entry.blockId() == null ? "" : entry.blockId().trim().toLowerCase();
            return "newline".equals(id) || "row".equals(id) || "new_line".equals(id);
        }

        private static PlaceExecResult advanceToNextRowSeed(MinecraftClient mc, long now) {
            synchronized (DIRECT_PLACE_STATE) {
                if (!DIRECT_PLACE_STATE.active || DIRECT_PLACE_STATE.startSeed == null) {
                    return PlaceExecResult.fail(0, 0, "NEWLINE_ROW_NOT_READY", "runtime seed is not initialized");
                }
                if (DIRECT_PLACE_STATE.pendingRowSeed == null) {
                    BlockPos next = findNextRowSeed(mc, DIRECT_PLACE_STATE.startSeed, DIRECT_PLACE_STATE.seed, DIRECT_PLACE_STATE.usedRowSeeds);
                    if (next == null) {
                        return PlaceExecResult.fail(0, 0, "NEWLINE_ROW_NOT_FOUND", "no free blue-glass row found for newline");
                    }
                    DIRECT_PLACE_STATE.pendingRowSeed = next;
                    System.out.println("[printer-debug] newline_row selected from=" + DIRECT_PLACE_STATE.seed + " to=" + next);
                }
                BlockPos nextSeed = DIRECT_PLACE_STATE.pendingRowSeed;
                double standX = nextSeed.getX() + 0.5;
                double standY = nextSeed.getY() + 1.0;
                double standZ = nextSeed.getZ() - 2.0 + 0.5;
                if (mc.player.squaredDistanceTo(standX, standY, standZ) > 6.0D) {
                    if (isTpPathBusyNow()) {
                        return PlaceExecResult.inProgress(0, "WAIT_TP_PATH_NEWLINE");
                    }
                    boolean tpQueued = new FabricBridge(null).enqueueTpPath(nextSeed.getX(), nextSeed.getY() + 1, nextSeed.getZ() - 2);
                    if (!tpQueued) {
                        return PlaceExecResult.fail(0, 0, "TP_PATH_FAILED", "cannot tp to newline row seed=" + nextSeed);
                    }
                    DIRECT_PLACE_STATE.tpSettleUntilMs = now + PLACE_TP_SETTLE_MS;
                    return PlaceExecResult.inProgress(0, "WAIT_TP_PATH_NEWLINE");
                }
                if (now < DIRECT_PLACE_STATE.tpSettleUntilMs) {
                    return PlaceExecResult.inProgress(0, "WAIT_TP_SETTLE_NEWLINE");
                }
                DIRECT_PLACE_STATE.seed = nextSeed;
                DIRECT_PLACE_STATE.cursor = 0;
                DIRECT_PLACE_STATE.usedRowSeeds.add(seedKey(nextSeed));
                DIRECT_PLACE_STATE.pendingRowSeed = null;
                clearPendingPlaceState(DIRECT_PLACE_STATE);
                System.out.println("[printer-debug] newline_row switched seed=" + DIRECT_PLACE_STATE.seed + " cursor=0");
                return PlaceExecResult.ok(1);
            }
        }

        private static BlockPos findNextRowSeed(MinecraftClient mc, BlockPos rootSeed, BlockPos currentSeed, Set<String> used) {
            if (mc == null || mc.player == null || mc.world == null || rootSeed == null) {
                return null;
            }
            List<BlockPosView> seeds = new ArrayList<BlockPosView>();
            seeds.add(new BlockPosView(rootSeed.getX(), rootSeed.getY(), rootSeed.getZ()));
            BlueGlassSearch.Probe probe = new BlueGlassSearch.Probe() {
                @Override
                public boolean isBlueGlass(int x, int y, int z) {
                    return BetterCodeFabric1165.FabricBridge.isBlueGlass(mc, x, y, z);
                }

                @Override
                public boolean isFree(int x, int y, int z) {
                    return BetterCodeFabric1165.FabricBridge.isFreeGlass(mc, x, y, z);
                }
            };
            List<BlockPosView> scanned = BlueGlassSearch.scan(seeds, probe);
            if (scanned == null || scanned.isEmpty()) {
                return null;
            }
            BlockPos best = null;
            double bestDist = Double.MAX_VALUE;
            for (BlockPosView s : scanned) {
                if (s == null) {
                    continue;
                }
                if (!probe.isFree(s.x(), s.y(), s.z())) {
                    continue;
                }
                String key = seedKey(s.x(), s.y(), s.z());
                if (used != null && used.contains(key)) {
                    continue;
                }
                double d;
                if (currentSeed != null) {
                    double dx = s.x() - currentSeed.getX();
                    double dy = s.y() - currentSeed.getY();
                    double dz = s.z() - currentSeed.getZ();
                    d = dx * dx + dy * dy + dz * dz;
                } else {
                    d = mc.player.squaredDistanceTo(s.x() + 0.5, s.y() + 0.5, s.z() + 0.5);
                }
                if (d < bestDist) {
                    bestDist = d;
                    best = new BlockPos(s.x(), s.y(), s.z());
                }
            }
            return best;
        }

        private static String seedKey(BlockPos pos) {
            if (pos == null) {
                return "";
            }
            return seedKey(pos.getX(), pos.getY(), pos.getZ());
        }

        private static String seedKey(int x, int y, int z) {
            return x + ":" + y + ":" + z;
        }

        @Override
        public boolean useBlockAt(int x, int y, int z, String purpose) {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.player == null || mc.interactionManager == null || mc.world == null) {
                return false;
            }
            BlockPos pos = new BlockPos(x, y, z);
            BlockHitResult hit = new BlockHitResult(
                new Vec3d(x + 0.5, y + 0.5, z + 0.5),
                Direction.UP,
                pos,
                false
            );
            try {
                ActionResult result = mc.interactionManager.interactBlock(mc.player, mc.world, Hand.MAIN_HAND, hit);
                return result != null && result.isAccepted();
            } catch (Exception ignore) {
                return false;
            }
        }

        @Override
        public boolean useBlockAtOffset(int dx, int dy, int dz, String purpose) {
            BlockPos pos = resolveOffsetFromLastPlaced(dx, dy, dz);
            if (pos == null) {
                return false;
            }
            return useBlockAt(pos.getX(), pos.getY(), pos.getZ(), purpose);
        }

        @Override
        public boolean isAirAt(int x, int y, int z) {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.world == null) {
                return false;
            }
            try {
                return mc.world.getBlockState(new BlockPos(x, y, z)).isAir();
            } catch (Exception ignore) {
                return false;
            }
        }

        @Override
        public boolean isBlockAt(int x, int y, int z, String blockId) {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.world == null || blockId == null || blockId.trim().isEmpty()) {
                return false;
            }
            try {
                String worldId = String.valueOf(Registry.BLOCK.getId(mc.world.getBlockState(new BlockPos(x, y, z)).getBlock()));
                return LegacyBlockIdCompat.normalizeForModern(blockId).equalsIgnoreCase(worldId);
            } catch (Exception ignore) {
                return false;
            }
        }

        @Override
        public boolean isBlockAtOffset(int dx, int dy, int dz, String blockId) {
            BlockPos pos = resolveOffsetFromLastPlaced(dx, dy, dz);
            if (pos == null) {
                return false;
            }
            return isBlockAt(pos.getX(), pos.getY(), pos.getZ(), blockId);
        }

        @Override
        public ClickResult clickSlot(int windowId, int slot, int button, String clickType) {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.player == null || mc.interactionManager == null) {
                return ClickResult.rejected("client_context_missing", AckState.REJECTED);
            }
            try {
                mc.interactionManager.clickSlot(windowId, slot, button, SlotActionType.PICKUP, mc.player);
                return ClickResult.accepted(AckState.PENDING);
            } catch (Exception e) {
                return ClickResult.rejected(e.getClass().getSimpleName() + ": " + String.valueOf(e.getMessage()), AckState.REJECTED);
            }
        }

        @Override
        public CursorState getCursorStack() {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.player == null || mc.player.currentScreenHandler == null) {
                return CursorState.empty();
            }
            try {
                ItemStack st = null;
                Object handler = mc.player.currentScreenHandler;
                try {
                    Object v = handler.getClass().getMethod("getCursorStack").invoke(handler);
                    if (v instanceof ItemStack) {
                        st = (ItemStack) v;
                    }
                } catch (Exception ignore) {
                }
                if (st == null) {
                    try {
                        java.lang.reflect.Field f = handler.getClass().getDeclaredField("cursorStack");
                        f.setAccessible(true);
                        Object v = f.get(handler);
                        if (v instanceof ItemStack) {
                            st = (ItemStack) v;
                        }
                    } catch (Exception ignore) {
                    }
                }
                if (st == null || st.isEmpty()) {
                    return CursorState.empty();
                }
                String itemId = st.getItem() == null ? "" : String.valueOf(Registry.ITEM.getId(st.getItem()));
                String name = textToString(st.getName());
                return new CursorState(false, itemId, name, readNbtString(st));
            } catch (Exception e) {
                return CursorState.empty();
            }
        }

        @Override
        public ContainerView getContainerSnapshot() {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (!(mc.currentScreen instanceof HandledScreen) || mc.player == null) {
                resetSnapshotCache();
                return ContainerView.empty();
            }
            HandledScreen<?> hs = (HandledScreen<?>) mc.currentScreen;
            try {
                long tick = mc.world == null ? Long.MIN_VALUE : mc.world.getTime();
                int syncId = hs.getScreenHandler().syncId;
                int screenId = System.identityHashCode(hs);
                if (tick == SNAPSHOT_CACHE_TICK
                    && syncId == SNAPSHOT_CACHE_SYNC_ID
                    && screenId == SNAPSHOT_CACHE_SCREEN_ID
                    && SNAPSHOT_CACHE_VIEW != null) {
                    return SNAPSHOT_CACHE_VIEW;
                }
                List<SlotView> out = new ArrayList<>();
                int windowId = syncId;
                String title = textToString(hs.getTitle());
                List<Slot> slots = hs.getScreenHandler().slots;
                for (int i = 0; i < slots.size(); i++) {
                    Slot s = slots.get(i);
                    if (s == null) {
                        continue;
                    }
                    ItemStack st = s.getStack();
                    boolean empty = st == null || st.isEmpty();
                    String itemId = "";
                    String display = "";
                    String nbt = "";
                    if (!empty) {
                        itemId = st.getItem() == null ? "" : String.valueOf(Registry.ITEM.getId(st.getItem()));
                        display = textToString(st.getName());
                        nbt = readNbtStringCapped(st, 512);
                    }
                    boolean playerInv = s.inventory == mc.player.inventory;
                    int slotId = readSlotId(s, i);
                    int slotIndex = readSlotIndex(s, slotId);
                    out.add(new SlotView(slotId, slotIndex, playerInv, empty, itemId, display, nbt));
                }
                ContainerView built = new ContainerView(windowId, title, hs.getScreenHandler().slots.size(), out);
                SNAPSHOT_CACHE_TICK = tick;
                SNAPSHOT_CACHE_SYNC_ID = syncId;
                SNAPSHOT_CACHE_SCREEN_ID = screenId;
                SNAPSHOT_CACHE_VIEW = built;
                return built;
            } catch (Exception e) {
                resetSnapshotCache();
                return ContainerView.empty();
            }
        }

        @Override
        public AckState waitForWindowChange(int expectedWindowId, long timeoutMs) {
            ContainerView view = getContainerSnapshot();
            if (view.windowId() < 0) {
                return timeoutMs <= 0 ? AckState.TIMEOUT : AckState.PENDING;
            }
            if (view.windowId() != expectedWindowId) {
                return AckState.ACKED;
            }
            return timeoutMs <= 0 ? AckState.TIMEOUT : AckState.PENDING;
        }

        @Override
        public void closeScreen() {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.player != null) {
                mc.player.closeHandledScreen();
            }
        }

        @Override
        public boolean selectHotbarSlot(int slot) {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.player == null || slot < 0 || slot >= 9) {
                return false;
            }
            mc.player.inventory.selectedSlot = slot;
            return true;
        }

        @Override
        public boolean injectCreativeSlot(int slot, String itemId, String nbt, String displayName) {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.player == null || mc.interactionManager == null || slot < 0 || slot >= 9) {
                return false;
            }
            Identifier id;
            try {
                id = new Identifier(itemId == null ? "" : itemId.trim());
            } catch (Exception e) {
                return false;
            }
            Item item = Registry.ITEM.get(id);
            if (item == null || item == ItemStack.EMPTY.getItem()) {
                return false;
            }
            try {
                ItemStack st = new ItemStack(item);
                if (displayName != null && !displayName.trim().isEmpty()) {
                    st.setCustomName(new LiteralText(displayName.trim()));
                }
                if (nbt != null && !nbt.trim().isEmpty()) {
                    applyNbt(st, nbt.trim());
                }
                mc.player.inventory.setStack(slot, st);
                mc.interactionManager.clickCreativeStack(st, 36 + slot);
                return true;
            } catch (Exception e) {
                return false;
            }
        }

        @Override
        public ClickResult interactBlock(int x, int y, int z) {
            return sendUseItemOnBlock(x, y, z);
        }

        @Override
        public ClickResult sendUseItemOnBlock(int x, int y, int z) {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.player == null || mc.interactionManager == null || mc.world == null) {
                return ClickResult.rejected("client_context_missing", AckState.REJECTED);
            }
            BlockPos pos = new BlockPos(x, y, z);
            BlockHitResult hit = new BlockHitResult(
                new Vec3d(x + 0.5, y + 0.5, z + 0.5),
                Direction.UP,
                pos,
                false
            );
            try {
                ActionResult result = mc.interactionManager.interactBlock(mc.player, mc.world, Hand.MAIN_HAND, hit);
                if (result != null && result.isAccepted()) {
                    return ClickResult.accepted(AckState.ACKED);
                }
                return ClickResult.rejected("interact_rejected:" + String.valueOf(result), AckState.REJECTED);
            } catch (Exception e) {
                return ClickResult.rejected(e.getClass().getSimpleName() + ": " + String.valueOf(e.getMessage()), AckState.REJECTED);
            }
        }

        @Override
        public ClickResult clickBlockLegacy(int x, int y, int z, String purpose, boolean spoofLook) {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.player == null || mc.interactionManager == null || mc.world == null) {
                return ClickResult.rejected("client_context_missing", AckState.REJECTED);
            }
            traceLegacyClickBurst(mc, x, y, z, purpose);
            if (spoofLook) {
                aimAtBlock(mc, x, y, z);
            }
            ClickResult packetLike = sendUseItemOnBlock(x, y, z);
            boolean accepted = packetLike != null && packetLike.accepted();
            boolean interactAccepted = false;
            if (!accepted) {
                interactAccepted = useBlockAt(x, y, z, purpose == null ? "legacy_click_interact" : purpose + "_interact");
            }
            if (accepted || interactAccepted) {
                return ClickResult.accepted(AckState.ACKED);
            }
            String reason = packetLike == null ? "legacy_click_rejected" : packetLike.reason();
            return ClickResult.rejected(reason, AckState.REJECTED);
        }

        @Override
        public long nowMs() {
            return System.currentTimeMillis();
        }

        @Override
        public List<SelectedRow> selectedRows() {
            MinecraftClient mc = MinecraftClient.getInstance();
            List<SelectedRow> out = new ArrayList<SelectedRow>();
            for (SelectedRow s : SELECTED.values()) {
                if (s == null) {
                    continue;
                }
                SelectedRow normalized = SelectedRowNormalizer.normalizeToGlassAnchor(
                    s,
                    (x, y, z) -> isBlueGlass(mc, x, y, z)
                );
                if (normalized == null) {
                    continue;
                }
                if (normalized.y() != s.y()) {
                    System.out.println("[publish-debug] selected_row_normalized source=entry_to_glass dim=" + s.dimension()
                        + " from=" + s.x() + "," + s.y() + "," + s.z()
                        + " to=" + normalized.x() + "," + normalized.y() + "," + normalized.z());
                }
                out.add(normalized);
            }
            return out;
        }

        @Override
        public boolean canUseEditorContext() {
            MinecraftClient mc = MinecraftClient.getInstance();
            return mc != null && mc.player != null && mc.world != null;
        }

        @Override
        public boolean isScreenOpen() {
            MinecraftClient mc = MinecraftClient.getInstance();
            return mc != null && mc.currentScreen != null;
        }

        @Override
        public boolean closeScreenIfOpen() {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc == null || mc.currentScreen == null || mc.player == null) {
                return false;
            }
            mc.player.closeHandledScreen();
            return true;
        }

        @Override
        public boolean enqueueTpPath(int x, int y, int z) {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc == null || mc.player == null || mc.world == null) {
                return false;
            }
            if (y < -64 || y > 320) {
                return false;
            }
            BlockPos target = new BlockPos(x, y, z);
            if (mc.world.getWorldBorder() != null && !mc.world.getWorldBorder().contains(target)) {
                return false;
            }
            double sx = mc.player.getX();
            double sy = mc.player.getY();
            double sz = mc.player.getZ();
            double dx = x - sx;
            double dy = y - sy;
            double dz = z - sz;
            double distSq = dx * dx + dy * dy + dz * dz;
            synchronized (LOCAL_TP_STATE) {
                LOCAL_TP_STATE.queue.clear();
                int safety = 0;
                for (int axis = 0; axis < 3; axis++) {
                    double delta = axis == 0 ? dx : axis == 1 ? dy : dz;
                    while (Math.abs(delta) >= 0.001D && safety < 5000) {
                        double step = Math.min(10.0D, Math.abs(delta)) * Math.signum(delta);
                        double[] move = new double[] {0.0D, 0.0D, 0.0D};
                        if (axis == 0) {
                            move[0] = step;
                            dx -= step;
                            delta = dx;
                        } else if (axis == 1) {
                            move[1] = step;
                            dy -= step;
                            delta = dy;
                        } else {
                            move[2] = step;
                            dz -= step;
                            delta = dz;
                        }
                        LOCAL_TP_STATE.queue.addLast(move);
                        safety++;
                    }
                }
                if (LOCAL_TP_STATE.queue.isEmpty()) {
                    return false;
                }
                // Keep stepped tp path only. Instant local set causes frequent server-side desync/reject on place.
                LOCAL_TP_STATE.nextMs = System.currentTimeMillis();
                System.out.println("[printer-debug] testcase_tp queued target=" + x + "," + y + "," + z
                    + " steps=" + LOCAL_TP_STATE.queue.size());
                return true;
            }
        }

        @Override
        public boolean isTpPathBusy() {
            synchronized (LOCAL_TP_STATE) {
                return !LOCAL_TP_STATE.queue.isEmpty();
            }
        }

        @Override
        public boolean isHoldingBlockerItem() {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc == null || mc.player == null) {
                return false;
            }
            ItemStack held = mc.player.getMainHandStack();
            if (held == null || held.isEmpty() || held.getItem() == null) {
                return false;
            }
            return held.getItem() == Items.IRON_INGOT || held.getItem() == Items.GOLD_INGOT;
        }

        @Override
        public boolean isSignAt(int x, int y, int z) {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc == null || mc.world == null) {
                return false;
            }
            try {
                String blockId = String.valueOf(Registry.BLOCK.getId(mc.world.getBlockState(new BlockPos(x, y, z)).getBlock()));
                if (blockId != null && blockId.toLowerCase().contains("sign")) {
                    return true;
                }
            } catch (Exception ignore) {
            }
            try {
                Object be = mc.world.getBlockEntity(new BlockPos(x, y, z));
                return be != null && be.getClass().getSimpleName().toLowerCase().contains("sign");
            } catch (Exception ignore) {
            }
            return false;
        }

        @Override
        public String[] readSignLinesAt(int x, int y, int z) {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc == null || mc.world == null) {
                return null;
            }
            try {
                String blockId = "";
                try {
                    blockId = String.valueOf(Registry.BLOCK.getId(mc.world.getBlockState(new BlockPos(x, y, z)).getBlock()));
                } catch (Exception ignore) {
                }
                BlockEntity be = mc.world.getBlockEntity(new BlockPos(x, y, z));
                if (be == null) {
                    return null;
                }
                String[] out = new String[4];
                for (int i = 0; i < 4; i++) {
                    out[i] = readSignLineReflect(be, i);
                }
                if (!hasAnySignText(out)) {
                    String[] fromNbt = readSignLinesFromNbt(be);
                    if (hasAnySignText(fromNbt)) {
                        return fromNbt;
                    }
                }
                return out;
            } catch (Exception ignore) {
            }
            return null;
        }

        @Override
        public String dimensionId() {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc == null || mc.world == null) {
                return currentDimension();
            }
            try {
                Object key = mc.world.getRegistryKey();
                Object value = key == null ? null : key.getClass().getMethod("getValue").invoke(key);
                return value == null ? currentDimension() : String.valueOf(value);
            } catch (Exception ignore) {
            }
            return currentDimension();
        }

        @Override
        public boolean supportsLegacyLookSpoof() {
            return true;
        }

        @Override
        public BlockPosView getRuntimeEntryAnchor() {
            BlockPos pos = resolveLastPlacedTarget();
            if (pos == null) {
                return null;
            }
            return new BlockPosView(pos.getX(), pos.getY(), pos.getZ());
        }

        @Override
        public boolean openMenuAtEntryAnchor() {
            BlockPos entry = resolveLastPlacedTarget();
            if (entry == null) {
                return false;
            }
            for (int dy = 0; dy >= -2; dy--) {
                ClickResult sign = clickBlockLegacy(entry.getX(), entry.getY() + dy, entry.getZ() - 1, "menu_open_sign", true);
                if (sign != null && sign.accepted()) {
                    return true;
                }
            }
            ClickResult base = clickBlockLegacy(entry.getX(), entry.getY(), entry.getZ(), "menu_open_entry", true);
            return base != null && base.accepted();
        }

        @Override
        public boolean canTeleportWarmup() {
            return true;
        }

        @Override
        public double distanceSqTo(int x, int y, int z) {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc == null || mc.player == null) {
                return Double.POSITIVE_INFINITY;
            }
            return mc.player.squaredDistanceTo(x + 0.5, y + 0.5, z + 0.5);
        }

        private static int readIntField(Object target, String field, int fallback) {
            if (target == null || field == null || field.isEmpty()) {
                return fallback;
            }
            try {
                Class<?> c = target.getClass();
                while (c != null) {
                    try {
                        java.lang.reflect.Field f = c.getDeclaredField(field);
                        f.setAccessible(true);
                        Object v = f.get(target);
                        if (v instanceof Integer) {
                            return ((Integer) v).intValue();
                        }
                    } catch (NoSuchFieldException ignore) {
                    }
                    c = c.getSuperclass();
                }
            } catch (Exception ignore) {
            }
            return fallback;
        }

        private static String normalizeBlockIdForRuntime(String sourceBlockId) {
            String base = sourceBlockId == null ? "" : sourceBlockId.trim();
            if (base.isEmpty()) {
                return "";
            }
            String vanillaFixed = translateLegacyBlockIdViaVanillaFlattening(base);
            String normalized = LegacyBlockIdCompat.normalizeForModern(vanillaFixed);
            if (!base.equalsIgnoreCase(normalized)) {
                String mode = base.equalsIgnoreCase(vanillaFixed) ? "compat_map" : "vanilla_flattening";
                String key = mode + "|" + base.toLowerCase() + "|" + normalized.toLowerCase();
                long now = System.currentTimeMillis();
                if (!key.equals(lastBlockCompatLogKey) || now - lastBlockCompatLogMs >= 2500L) {
                    lastBlockCompatLogKey = key;
                    lastBlockCompatLogMs = now;
                    System.out.println("[printer-debug] block_id_compat mode=" + mode + " from=" + base + " to=" + normalized);
                }
            }
            return normalized;
        }

        private static String translateLegacyBlockIdViaVanillaFlattening(String sourceBlockId) {
            String namespaced = sourceBlockId.contains(":")
                ? sourceBlockId.toLowerCase()
                : ("minecraft:" + sourceBlockId.toLowerCase());
            String[] classNames = new String[] {
                "net.minecraft.datafixer.fix.BlockStateFlattening",
                "net.minecraft.util.datafix.fixes.BlockStateFlattening"
            };
            for (String className : classNames) {
                try {
                    Class<?> c = Class.forName(className);
                    java.lang.reflect.Method m = c.getDeclaredMethod("lookupBlock", String.class);
                    m.setAccessible(true);
                    Object out = m.invoke(null, namespaced);
                    if (out instanceof String) {
                        String s = ((String) out).trim().toLowerCase();
                        if (!s.isEmpty()) {
                            return s;
                        }
                    }
                } catch (Exception ignore) {
                }
            }
            return namespaced;
        }

        private static int readSlotId(Slot slot, int fallback) {
            if (slot == null) {
                return fallback;
            }
            try {
                return slot.id;
            } catch (Exception ignore) {
            }
            int reflected = readIntField(slot, "id", Integer.MIN_VALUE);
            if (reflected != Integer.MIN_VALUE) {
                return reflected;
            }
            reflected = readIntField(slot, "field_7874", Integer.MIN_VALUE);
            if (reflected != Integer.MIN_VALUE) {
                return reflected;
            }
            return fallback;
        }

        private static int readSlotIndex(Slot slot, int fallback) {
            if (slot == null) {
                return fallback;
            }
            try {
                Object v = slot.getClass().getMethod("getIndex").invoke(slot);
                if (v instanceof Integer) {
                    return ((Integer) v).intValue();
                }
            } catch (Exception ignore) {
            }
            int reflected = readIntField(slot, "index", Integer.MIN_VALUE);
            if (reflected != Integer.MIN_VALUE) {
                return reflected;
            }
            reflected = readIntField(slot, "invSlot", Integer.MIN_VALUE);
            if (reflected != Integer.MIN_VALUE) {
                return reflected;
            }
            reflected = readIntField(slot, "field_7875", Integer.MIN_VALUE);
            if (reflected != Integer.MIN_VALUE) {
                return reflected;
            }
            return fallback;
        }

        private static String textToString(Object text) {
            if (text == null) {
                return "";
            }
            try {
                return String.valueOf(text.getClass().getMethod("getString").invoke(text));
            } catch (Exception ignore) {
            }
            try {
                return String.valueOf(text.getClass().getMethod("asString").invoke(text));
            } catch (Exception ignore) {
            }
            return String.valueOf(text);
        }

        private static String readSignLineReflect(Object be, int line) {
            return ReflectCompat.readSignLineReflect(be, line, FabricBridge::textToString);
        }

        private static boolean hasAnySignText(String[] lines) {
            if (lines == null) {
                return false;
            }
            for (String line : lines) {
                if (line != null && !line.trim().isEmpty()) {
                    return true;
                }
            }
            return false;
        }

        private static String[] readSignLinesFromNbt(BlockEntity be) {
            if (be == null) {
                return null;
            }
            try {
                NbtCompound tag = createBlockEntityNbtReflect(be);
                if (tag == null) {
                    return null;
                }
                String[] out = new String[4];
                boolean any = false;
                for (int i = 0; i < 4; i++) {
                    String key = "Text" + (i + 1);
                    String raw = tag.getString(key);
                    String value = raw == null ? "" : raw;
                    if (!value.trim().isEmpty()) {
                        try {
                            Text parsed = Text.Serializer.fromJson(value);
                            value = textToString(parsed);
                        } catch (Exception ignore) {
                        }
                    }
                    if (value != null && !value.trim().isEmpty()) {
                        any = true;
                    }
                    out[i] = value == null ? "" : value;
                }
                return any ? out : null;
            } catch (Exception ignore) {
            }
            return null;
        }

        private static NbtCompound createBlockEntityNbtReflect(BlockEntity be) {
            if (be == null) {
                return null;
            }
            // Prefer no-arg NBT producer methods that return a compound.
            try {
                java.lang.reflect.Method[] methods = be.getClass().getMethods();
                for (java.lang.reflect.Method m : methods) {
                    if (m == null || m.getParameterCount() != 0) {
                        continue;
                    }
                    if (!NbtCompound.class.isAssignableFrom(m.getReturnType())) {
                        continue;
                    }
                    try {
                        m.setAccessible(true);
                        Object out = m.invoke(be);
                        if (out instanceof NbtCompound) {
                            return (NbtCompound) out;
                        }
                    } catch (Exception ignore) {
                    }
                }
            } catch (Exception ignore) {
            }
            // Fallback to writer-style methods with one NbtCompound arg, including obfuscated names.
            try {
                java.lang.reflect.Method[] methods = be.getClass().getMethods();
                for (java.lang.reflect.Method m : methods) {
                    if (m == null || m.getParameterCount() != 1) {
                        continue;
                    }
                    Class<?> p = m.getParameterTypes()[0];
                    if (p == null || !p.isAssignableFrom(NbtCompound.class)) {
                        continue;
                    }
                    NbtCompound tmp = new NbtCompound();
                    try {
                        m.setAccessible(true);
                        Object out = m.invoke(be, tmp);
                        if (out instanceof NbtCompound) {
                            return (NbtCompound) out;
                        }
                        return tmp;
                    } catch (Exception ignore) {
                    }
                }
            } catch (Exception ignore) {
            }
            return null;
        }

        private static String formatLines(String[] lines) {
            if (lines == null) {
                return "null";
            }
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < lines.length; i++) {
                if (i > 0) {
                    sb.append(" | ");
                }
                String v = lines[i] == null ? "" : lines[i].trim();
                if (v.length() > 64) {
                    v = v.substring(0, 64) + "...";
                }
                sb.append(i).append('=').append(v);
            }
            sb.append(']');
            return sb.toString();
        }

        private static String readNbtString(ItemStack st) {
            if (st == null) {
                return "";
            }
            try {
                Object nbt = st.getClass().getMethod("getNbt").invoke(st);
                return nbt == null ? "" : nbt.toString();
            } catch (Exception ignore) {
            }
            try {
                Object nbt = st.getClass().getMethod("getTag").invoke(st);
                return nbt == null ? "" : nbt.toString();
            } catch (Exception ignore) {
            }
            return "";
        }

        private static String readNbtStringCapped(ItemStack st, int maxLen) {
            String raw = readNbtString(st);
            int limit = maxLen <= 0 ? 0 : maxLen;
            if (limit == 0 || raw == null || raw.length() <= limit) {
                return raw == null ? "" : raw;
            }
            return raw.substring(0, limit);
        }

        private static void applyNbt(ItemStack st, String raw) {
            try {
                Object parsed = StringNbtReader.class.getMethod("parse", String.class).invoke(null, raw);
                try {
                    st.getClass().getMethod("setNbt", parsed.getClass()).invoke(st, parsed);
                    return;
                } catch (Exception ignore) {
                }
                st.getClass().getMethod("setTag", parsed.getClass()).invoke(st, parsed);
            } catch (Exception ignore) {
            }
        }

        private static void aimAtBlock(MinecraftClient mc, int x, int y, int z) {
            if (mc == null || mc.player == null) {
                return;
            }
            double eyeX = mc.player.getX();
            double eyeY = mc.player.getY() + mc.player.getEyeHeight(mc.player.getPose());
            double eyeZ = mc.player.getZ();
            double tx = x + 0.5;
            double ty = y + 0.5;
            double tz = z + 0.5;
            double dx = tx - eyeX;
            double dy = ty - eyeY;
            double dz = tz - eyeZ;
            double horiz = Math.sqrt(dx * dx + dz * dz);
            float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
            float pitch = (float) (-Math.toDegrees(Math.atan2(dy, horiz)));
            try {
                java.lang.reflect.Field fy = mc.player.getClass().getField("yaw");
                fy.setFloat(mc.player, yaw);
                java.lang.reflect.Field fp = mc.player.getClass().getField("pitch");
                fp.setFloat(mc.player, pitch);
            } catch (Exception ignore) {
            }
            sendLookPacketReflect(mc, yaw, pitch);
        }

        private static void sendLookPacketReflect(MinecraftClient mc, float yaw, float pitch) {
            if (mc == null || mc.player == null) {
                return;
            }
            boolean onGround = false;
            try {
                onGround = mc.player.isOnGround();
            } catch (Exception ignore) {
            }
            ReflectCompat.sendLookPacketReflect(mc.player.networkHandler, yaw, pitch, onGround);
        }

    }

    private static final class LocalTpState {
        final Deque<double[]> queue = new ArrayDeque<double[]>();
        long nextMs;
    }

    private static void handleLocalTpPath(MinecraftClient mc) {
        synchronized (LOCAL_TP_STATE) {
            if (LOCAL_TP_STATE.queue.isEmpty()) {
                return;
            }
            if (mc == null || mc.player == null || mc.world == null) {
                LOCAL_TP_STATE.queue.clear();
                return;
            }
            long now = System.currentTimeMillis();
            if (now < LOCAL_TP_STATE.nextMs) {
                return;
            }
            try {
                mc.player.abilities.allowFlying = true;
                mc.player.abilities.flying = true;
                mc.player.sendAbilitiesUpdate();
            } catch (Exception ignore) {
            }
            double[] step = LOCAL_TP_STATE.queue.pollFirst();
            if (step == null) {
                return;
            }
            double nx = mc.player.getX() + step[0];
            double ny = mc.player.getY() + step[1];
            double nz = mc.player.getZ() + step[2];
            if (!setPlayerPositionLocal(mc, nx, ny, nz)) {
                LOCAL_TP_STATE.queue.clear();
                return;
            }
            mc.player.setVelocity(0.0, 0.0, 0.0);
            LOCAL_TP_STATE.nextMs = now + 300L;
        }
    }

    private static void refreshWorldLoadSeedHint(MinecraftClient mc) {
        if (mc == null || mc.player == null || mc.world == null) {
            return;
        }
        String dim = mc.world.getRegistryKey().getValue().toString();
        if (dim.equals(LAST_WORLD_SEED_HINT_DIM)) {
            return;
        }
        LAST_WORLD_SEED_HINT_DIM = dim;
        tryResolveSeedHint(mc, "world_load");
    }

    private static void refreshWorldSessionState(MinecraftClient mc) {
        if (mc == null || mc.world == null) {
            LAST_WORLD_INSTANCE_ID = -1;
            return;
        }
        int worldId = System.identityHashCode(mc.world);
        if (worldId == LAST_WORLD_INSTANCE_ID) {
            return;
        }
        LAST_WORLD_INSTANCE_ID = worldId;
        int selectedCount = SELECTED.size();
        if (selectedCount > 0) {
            SELECTED.clear();
        }
        TestcaseTool.clearMarker();
        synchronized (NEARBY_SIGN_CACHE) {
            NEARBY_SIGN_CACHE.resetRuntime();
        }
        SPRINTF_ACTIVE = false;
        SPRINTF_BASE_SPEED = Float.NaN;
        SPRINTF_PLAYER_ID = -1;
        LAST_DEV_UTILS_SYNC_ID = -1;
        LAST_DEV_UTILS_DIM = "";
        System.out.println("[printer-debug] world_session_reset selectedCleared=" + selectedCount + " testcaseMarkerCleared=1");
    }

    private static void handleDevUtilitiesAutoSelector(MinecraftClient mc) {
        if (mc == null || mc.player == null || mc.world == null) {
            return;
        }
        if (!(mc.currentScreen instanceof HandledScreen)) {
            LAST_DEV_UTILS_SYNC_ID = -1;
            LAST_DEV_UTILS_DIM = "";
            return;
        }
        Screen screen = mc.currentScreen;
        String title = screen == null || screen.getTitle() == null ? "" : screen.getTitle().getString();
        if (title == null || !DEV_UTILS_TITLE.equals(title.trim())) {
            return;
        }
        FabricBridge bridge = new FabricBridge(null);
        boolean editorLike = false;
        try {
            ScoreboardContext ctx = ScoreboardParser.parse(bridge.scoreboardLines());
            editorLike = ctx.editorLike();
        } catch (Exception ignore) {
        }
        boolean creative = false;
        try {
            creative = mc.player.abilities.creativeMode;
        } catch (Exception ignore) {
        }
        if (!editorLike && !creative) {
            return;
        }
        int syncId = mc.player.currentScreenHandler == null ? -1 : mc.player.currentScreenHandler.syncId;
        String dim = String.valueOf(mc.world.getRegistryKey().getValue());
        if (syncId == LAST_DEV_UTILS_SYNC_ID && dim.equals(LAST_DEV_UTILS_DIM)) {
            return;
        }
        LAST_DEV_UTILS_SYNC_ID = syncId;
        LAST_DEV_UTILS_DIM = dim;
        if (mc.player.currentScreenHandler == null) {
            return;
        }
        Slot guiSlot = resolveDevUtilsGuiSlot(mc.player.currentScreenHandler);
        if (guiSlot == null) {
            return;
        }
        ItemStack cur = guiSlot.getStack();
        if (isCodeSelectorItem(cur) || (cur != null && !cur.isEmpty())) {
            return;
        }
        guiSlot.setStack(createCodeSelectorStack());
        System.out.println("[printer-debug] dev_utils_auto_selector placed guiSlot=" + (DEV_UTILS_GUI_SELECTOR_SLOT + 1)
            + " syncId=" + syncId + " editorLike=" + editorLike + " creative=" + creative);
    }

    private static boolean handleDevUtilsPhantomSelectorClick(Screen screen, double mouseX, double mouseY, int button) {
        if (!(screen instanceof HandledScreen)) {
            return false;
        }
        if (button != 0) {
            return false;
        }
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.player == null || mc.world == null || mc.player.inventory == null) {
            return false;
        }
        String title = screen.getTitle() == null ? "" : screen.getTitle().getString();
        if (title == null || !DEV_UTILS_TITLE.equals(title.trim())) {
            return false;
        }
        boolean editorLike = false;
        try {
            ScoreboardContext ctx = ScoreboardParser.parse(new FabricBridge(null).scoreboardLines());
            editorLike = ctx.editorLike();
        } catch (Exception ignore) {
        }
        boolean creative = false;
        try {
            creative = mc.player.abilities.creativeMode;
        } catch (Exception ignore) {
        }
        if (!editorLike && !creative) {
            return false;
        }
        Slot hovered = findHoveredPlayerSlot((HandledScreen<?>) screen, mouseX, mouseY, mc);
        if (hovered == null) {
            return false;
        }
        int hoveredId = FabricBridge.readSlotId(hovered, Integer.MIN_VALUE);
        if (hoveredId != DEV_UTILS_GUI_SELECTOR_SLOT) {
            return false;
        }
        if (mc.player.inventory != null
            && DEV_UTILS_REWARD_HOTBAR_SLOT >= 0
            && DEV_UTILS_REWARD_HOTBAR_SLOT < mc.player.inventory.size()) {
            mc.player.inventory.setStack(DEV_UTILS_REWARD_HOTBAR_SLOT, createCodeSelectorStack());
            mc.player.inventory.selectedSlot = DEV_UTILS_REWARD_HOTBAR_SLOT;
        }
        System.out.println("[printer-debug] dev_utils_auto_selector click_local_cancel guiSlot=" + (DEV_UTILS_GUI_SELECTOR_SLOT + 1)
            + " title=" + DEV_UTILS_TITLE + " editorLike=" + editorLike + " creative=" + creative);
        return true; // cancel network click; local-only behavior
    }

    private static Slot findHoveredPlayerSlot(HandledScreen<?> screen, double mouseX, double mouseY, MinecraftClient mc) {
        if (screen == null || mc == null || mc.player == null || mc.player.currentScreenHandler == null) {
            return null;
        }
        int left = FabricBridge.readIntField(screen, "x", 0);
        int top = FabricBridge.readIntField(screen, "y", 0);
        try {
            for (Slot slot : mc.player.currentScreenHandler.slots) {
                if (slot == null || slot.inventory == mc.player.inventory) {
                    continue;
                }
                int sx = left + slot.x;
                int sy = top + slot.y;
                if (mouseX >= sx && mouseX < sx + 16 && mouseY >= sy && mouseY < sy + 16) {
                    return slot;
                }
            }
        } catch (Exception ignore) {
        }
        return null;
    }

    private static Slot resolveDevUtilsGuiSlot(net.minecraft.screen.ScreenHandler handler) {
        if (handler == null || handler.slots == null) {
            return null;
        }
        for (Slot slot : handler.slots) {
            if (slot == null) {
                continue;
            }
            int id = FabricBridge.readSlotId(slot, Integer.MIN_VALUE);
            if (id == DEV_UTILS_GUI_SELECTOR_SLOT) {
                return slot;
            }
        }
        return null;
    }

    private static void handleNearbySignCacheTick(MinecraftClient mc) {
        if (mc == null || mc.player == null || mc.world == null) {
            return;
        }
        if (runtime().hasActiveExecution()) {
            return;
        }
        synchronized (DIRECT_PLACE_STATE) {
            if (DIRECT_PLACE_STATE.active) {
                return;
            }
        }
        synchronized (REGALL_TABLES) {
            if (REGALL_TABLES.active) {
                return;
            }
        }
        if (mc.currentScreen != null) {
            return;
        }
        long now = System.currentTimeMillis();
        String dim = String.valueOf(mc.world.getRegistryKey().getValue());
        String scopeId = resolveNearbyScopeId(new FabricBridge(null));
        BlockPos feet = mc.player.getBlockPos();
        synchronized (NEARBY_SIGN_CACHE) {
            if (!NEARBY_SIGN_CACHE.loaded) {
                NEARBY_SIGN_CACHE.cache = PublishCacheStore.load(mc.runDirectory.toPath());
                NEARBY_SIGN_CACHE.loaded = true;
                System.out.println("[publish-debug] nearby_sign_cache load scope="
                    + NEARBY_SIGN_CACHE.cache.scopeSnapshot().size()
                    + " dimPos=" + NEARBY_SIGN_CACHE.cache.dimPosSnapshot().size()
                    + " entryToSign=" + NEARBY_SIGN_CACHE.cache.entryToSignSnapshot().size());
            }
            if (now < NEARBY_SIGN_CACHE.nextScanMs) {
                if (NEARBY_SIGN_CACHE.dirty && now - NEARBY_SIGN_CACHE.lastSaveMs >= NEARBY_SIGN_CACHE_SAVE_GAP_MS) {
                    flushNearbySignCache(mc, now);
                }
                return;
            }
            if (!dim.equals(NEARBY_SIGN_CACHE.dim)
                || NEARBY_SIGN_CACHE.queue.isEmpty()
                || Math.abs(feet.getX() - NEARBY_SIGN_CACHE.centerX) > 2
                || Math.abs(feet.getY() - NEARBY_SIGN_CACHE.centerY) > 1
                || Math.abs(feet.getZ() - NEARBY_SIGN_CACHE.centerZ) > 2) {
                rebuildNearbySignQueue(mc, dim, feet);
            }
            if (NEARBY_SIGN_CACHE.queue.isEmpty()) {
                NEARBY_SIGN_CACHE.nextScanMs = now + 300L;
                return;
            }
            FabricBridge bridge = new FabricBridge(null);
            int changed = 0;
            int scanned = 0;
            while (scanned < NEARBY_SIGN_CACHE_BATCH && !NEARBY_SIGN_CACHE.queue.isEmpty()) {
                BlockPos p = NEARBY_SIGN_CACHE.queue.pollFirst();
                scanned++;
                if (p == null || !mc.world.isChunkLoaded(p)) {
                    continue;
                }
                String blockId;
                try {
                    blockId = String.valueOf(Registry.BLOCK.getId(mc.world.getBlockState(p).getBlock()));
                } catch (Exception e) {
                    blockId = "";
                }
                if (blockId == null || !blockId.toLowerCase().contains("sign")) {
                    continue;
                }
                String[] lines = readSignLinesAtQuiet(mc, p.getX(), p.getY(), p.getZ());
                if (!hasAnySignText(lines)) {
                    continue;
                }
                String key = dim + ":" + p.getX() + ":" + p.getY() + ":" + p.getZ();
                String[] prev = NEARBY_SIGN_CACHE.cache.getDimPos(key);
                if (!sameLines(prev, lines)) {
                    NEARBY_SIGN_CACHE.cache.putDimPos(key, lines);
                    String scopeKey = scopeId + ":" + dim + ":nearby:" + p.getX() + ":" + p.getY() + ":" + p.getZ();
                    NEARBY_SIGN_CACHE.cache.putScope(scopeKey, lines);
                    changed++;
                }
            }
            if (changed > 0) {
                NEARBY_SIGN_CACHE.dirty = true;
                NEARBY_SIGN_CACHE.lastChangeMs = now;
                if (now - NEARBY_SIGN_CACHE.lastLogMs >= 1800L) {
                    NEARBY_SIGN_CACHE.lastLogMs = now;
                    System.out.println("[publish-debug] nearby_sign_cache update dim=" + dim
                        + " changed=" + changed
                        + " queueLeft=" + NEARBY_SIGN_CACHE.queue.size());
                }
            }
            if (NEARBY_SIGN_CACHE.dirty && now - NEARBY_SIGN_CACHE.lastSaveMs >= NEARBY_SIGN_CACHE_SAVE_GAP_MS) {
                flushNearbySignCache(mc, now);
            }
            NEARBY_SIGN_CACHE.nextScanMs = now + NEARBY_SIGN_CACHE_SCAN_GAP_MS;
        }
    }

    private static void rebuildNearbySignQueue(MinecraftClient mc, String dim, BlockPos center) {
        NEARBY_SIGN_CACHE.queue.clear();
        NEARBY_SIGN_CACHE.dim = dim == null ? "" : dim;
        NEARBY_SIGN_CACHE.centerX = center.getX();
        NEARBY_SIGN_CACHE.centerY = center.getY();
        NEARBY_SIGN_CACHE.centerZ = center.getZ();
        int fromEntities = enqueueNearbySignBlockEntities(mc, center);
        if (fromEntities > 0) {
            if (System.currentTimeMillis() - NEARBY_SIGN_CACHE.lastLogMs >= 1500L) {
                NEARBY_SIGN_CACHE.lastLogMs = System.currentTimeMillis();
                System.out.println("[publish-debug] nearby_sign_cache source=block_entities count=" + fromEntities);
            }
            return;
        }
        for (int dy = -NEARBY_SIGN_CACHE_RADIUS_Y; dy <= NEARBY_SIGN_CACHE_RADIUS_Y; dy++) {
            for (int dz = -NEARBY_SIGN_CACHE_RADIUS_XZ; dz <= NEARBY_SIGN_CACHE_RADIUS_XZ; dz++) {
                for (int dx = -NEARBY_SIGN_CACHE_RADIUS_XZ; dx <= NEARBY_SIGN_CACHE_RADIUS_XZ; dx++) {
                    NEARBY_SIGN_CACHE.queue.addLast(new BlockPos(
                        center.getX() + dx,
                        center.getY() + dy,
                        center.getZ() + dz
                    ));
                }
            }
        }
    }

    private static int enqueueNearbySignBlockEntities(MinecraftClient mc, BlockPos center) {
        if (mc == null || mc.world == null || center == null) {
            return 0;
        }
        Iterable<?> iterable = resolveWorldBlockEntities(mc.world);
        if (iterable == null) {
            return 0;
        }
        Set<String> seen = new HashSet<String>();
        int added = 0;
        try {
            for (Object raw : iterable) {
                if (!(raw instanceof BlockEntity)) {
                    continue;
                }
                BlockEntity be = (BlockEntity) raw;
                BlockPos p = be.getPos();
                if (p == null) {
                    continue;
                }
                if (Math.abs(p.getX() - center.getX()) > NEARBY_SIGN_CACHE_RADIUS_XZ
                    || Math.abs(p.getY() - center.getY()) > NEARBY_SIGN_CACHE_RADIUS_Y
                    || Math.abs(p.getZ() - center.getZ()) > NEARBY_SIGN_CACHE_RADIUS_XZ) {
                    continue;
                }
                if (!isLikelySignBlockEntity(mc, be, p)) {
                    continue;
                }
                String key = p.getX() + ":" + p.getY() + ":" + p.getZ();
                if (!seen.add(key)) {
                    continue;
                }
                NEARBY_SIGN_CACHE.queue.addLast(p);
                added++;
            }
        } catch (Exception ignore) {
        }
        return added;
    }

    private static Iterable<?> resolveWorldBlockEntities(Object world) {
        if (world == null) {
            return null;
        }
        // Prefer direct iterable methods first.
        String[] methodNames = new String[] {
            "getBlockEntities",
            "iterateBlockEntities"
        };
        for (String name : methodNames) {
            try {
                java.lang.reflect.Method m = world.getClass().getMethod(name);
                Object out = m.invoke(world);
                if (out instanceof Iterable) {
                    return (Iterable<?>) out;
                }
            } catch (Exception ignore) {
            }
        }
        // Fallback: scan fields and pick first iterable containing BlockEntity instances.
        Class<?> c = world.getClass();
        while (c != null && c != Object.class) {
            try {
                java.lang.reflect.Field[] fields = c.getDeclaredFields();
                for (java.lang.reflect.Field f : fields) {
                    if (f == null) {
                        continue;
                    }
                    Class<?> t = f.getType();
                    if (t == null || (!Iterable.class.isAssignableFrom(t) && !java.util.Collection.class.isAssignableFrom(t))) {
                        continue;
                    }
                    try {
                        f.setAccessible(true);
                        Object v = f.get(world);
                        if (!(v instanceof Iterable)) {
                            continue;
                        }
                        Iterable<?> it = (Iterable<?>) v;
                        for (Object sample : it) {
                            if (sample instanceof BlockEntity) {
                                return it;
                            }
                            break;
                        }
                    } catch (Exception ignore) {
                    }
                }
            } catch (Exception ignore) {
            }
            c = c.getSuperclass();
        }
        return null;
    }

    private static boolean isLikelySignBlockEntity(MinecraftClient mc, BlockEntity be, BlockPos p) {
        if (be == null || p == null) {
            return false;
        }
        try {
            String name = be.getClass().getSimpleName();
            if (name != null && name.toLowerCase().contains("sign")) {
                return true;
            }
        } catch (Exception ignore) {
        }
        if (mc == null || mc.world == null) {
            return false;
        }
        try {
            String blockId = String.valueOf(Registry.BLOCK.getId(mc.world.getBlockState(p).getBlock()));
            return blockId != null && blockId.toLowerCase().contains("sign");
        } catch (Exception ignore) {
        }
        return false;
    }

    private static String[] readSignLinesAtQuiet(MinecraftClient mc, int x, int y, int z) {
        if (mc == null || mc.world == null) {
            return null;
        }
        try {
            BlockEntity be = mc.world.getBlockEntity(new BlockPos(x, y, z));
            if (be == null) {
                return null;
            }
            String[] out = new String[4];
            for (int i = 0; i < 4; i++) {
                out[i] = readSignLineReflectQuiet(be, i);
            }
            if (!hasAnySignText(out)) {
                String[] fromNbt = readSignLinesFromNbtQuiet(be);
                if (hasAnySignText(fromNbt)) {
                    return fromNbt;
                }
            }
            return out;
        } catch (Exception ignore) {
        }
        return null;
    }

    private static String readSignLineReflectQuiet(Object be, int line) {
        if (be == null) {
            return "";
        }
        try {
            Object text = be.getClass().getMethod("getTextOnRow", int.class).invoke(be, Integer.valueOf(line));
            String s = normalizeSignTextQuiet(FabricBridge.textToString(text));
            if (!s.isEmpty()) {
                return s;
            }
        } catch (Exception ignore) {
        }
        try {
            Object text = be.getClass().getMethod("getTextOnRow", int.class, boolean.class).invoke(be, Integer.valueOf(line), Boolean.FALSE);
            String s = normalizeSignTextQuiet(FabricBridge.textToString(text));
            if (!s.isEmpty()) {
                return s;
            }
        } catch (Exception ignore) {
        }
        try {
            Object front = be.getClass().getMethod("getFrontText").invoke(be);
            Object msg = front.getClass().getMethod("getMessage", int.class, boolean.class).invoke(front, Integer.valueOf(line), Boolean.FALSE);
            String s = normalizeSignTextQuiet(FabricBridge.textToString(msg));
            if (!s.isEmpty()) {
                return s;
            }
        } catch (Exception ignore) {
        }
        String[] fields = new String[] {"text", "texts", "messages"};
        for (String f : fields) {
            String v = readSignLineFromFieldQuiet(be, f, line);
            if (!v.isEmpty()) {
                return v;
            }
        }
        try {
            java.lang.reflect.Field[] all = be.getClass().getDeclaredFields();
            for (java.lang.reflect.Field f : all) {
                if (f == null || f.getType() == null || !f.getType().isArray()) {
                    continue;
                }
                f.setAccessible(true);
                Object raw = f.get(be);
                if (!(raw instanceof Object[])) {
                    continue;
                }
                Object[] arr = (Object[]) raw;
                if (arr.length < 4 || line < 0 || line >= arr.length) {
                    continue;
                }
                String s = normalizeSignTextQuiet(FabricBridge.textToString(arr[line]));
                if (!s.isEmpty()) {
                    return s;
                }
            }
        } catch (Exception ignore) {
        }
        return "";
    }

    private static String readSignLineFromFieldQuiet(Object be, String fieldName, int line) {
        if (be == null || fieldName == null || fieldName.isEmpty()) {
            return "";
        }
        try {
            java.lang.reflect.Field f = be.getClass().getField(fieldName);
            Object raw = f.get(be);
            if (raw instanceof Object[]) {
                Object[] arr = (Object[]) raw;
                if (line >= 0 && line < arr.length) {
                    return normalizeSignTextQuiet(FabricBridge.textToString(arr[line]));
                }
            }
        } catch (Exception ignore) {
        }
        try {
            Class<?> cls = be.getClass();
            while (cls != null && cls != Object.class) {
                try {
                    java.lang.reflect.Field f = cls.getDeclaredField(fieldName);
                    f.setAccessible(true);
                    Object raw = f.get(be);
                    if (!(raw instanceof Object[])) {
                        cls = cls.getSuperclass();
                        continue;
                    }
                    Object[] arr = (Object[]) raw;
                    if (line < 0 || line >= arr.length) {
                        return "";
                    }
                    return normalizeSignTextQuiet(FabricBridge.textToString(arr[line]));
                } catch (NoSuchFieldException miss) {
                    cls = cls.getSuperclass();
                }
            }
        } catch (Exception ignore) {
        }
        return "";
    }

    private static String[] readSignLinesFromNbtQuiet(BlockEntity be) {
        if (be == null) {
            return null;
        }
        try {
            NbtCompound tag = createBlockEntityNbtReflectQuiet(be);
            if (tag == null) {
                return null;
            }
            String[] out = new String[4];
            boolean any = false;
            for (int i = 0; i < 4; i++) {
                String key = "Text" + (i + 1);
                String raw = tag.getString(key);
                String value = raw == null ? "" : raw;
                if (!value.trim().isEmpty()) {
                    try {
                        Text parsed = Text.Serializer.fromJson(value);
                        value = FabricBridge.textToString(parsed);
                    } catch (Exception ignore) {
                    }
                }
                value = normalizeSignTextQuiet(value);
                if (!value.isEmpty()) {
                    any = true;
                }
                out[i] = value;
            }
            return any ? out : null;
        } catch (Exception ignore) {
        }
        return null;
    }

    private static NbtCompound createBlockEntityNbtReflectQuiet(BlockEntity be) {
        if (be == null) {
            return null;
        }
        try {
            java.lang.reflect.Method[] methods = be.getClass().getMethods();
            for (java.lang.reflect.Method m : methods) {
                if (m == null || m.getParameterCount() != 0) {
                    continue;
                }
                if (!NbtCompound.class.isAssignableFrom(m.getReturnType())) {
                    continue;
                }
                try {
                    m.setAccessible(true);
                    Object out = m.invoke(be);
                    if (out instanceof NbtCompound) {
                        return (NbtCompound) out;
                    }
                } catch (Exception ignore) {
                }
            }
        } catch (Exception ignore) {
        }
        try {
            java.lang.reflect.Method[] methods = be.getClass().getMethods();
            for (java.lang.reflect.Method m : methods) {
                if (m == null || m.getParameterCount() != 1) {
                    continue;
                }
                Class<?> p = m.getParameterTypes()[0];
                if (p == null || !p.isAssignableFrom(NbtCompound.class)) {
                    continue;
                }
                NbtCompound tmp = new NbtCompound();
                try {
                    m.setAccessible(true);
                    Object out = m.invoke(be, tmp);
                    if (out instanceof NbtCompound) {
                        return (NbtCompound) out;
                    }
                    return tmp;
                } catch (Exception ignore) {
                }
            }
        } catch (Exception ignore) {
        }
        return null;
    }

    private static String normalizeSignTextQuiet(String raw) {
        String s = raw == null ? "" : raw.trim();
        if (s.isEmpty()) {
            return "";
        }
        String low = s.toLowerCase();
        if (low.contains("$$lambda$")) {
            return "";
        }
        if (low.startsWith("net.minecraft.class_") && s.indexOf('@') > 0) {
            return "";
        }
        int at = s.indexOf('@');
        if (at > 0 && at < s.length() - 1) {
            String tail = s.substring(at + 1);
            if (tail.matches("[0-9a-fA-F]{4,}")) {
                return "";
            }
        }
        return s;
    }

    private static void flushNearbySignCache(MinecraftClient mc, long now) {
        try {
            PublishCacheStore.save(mc.runDirectory.toPath(), NEARBY_SIGN_CACHE.cache);
            NEARBY_SIGN_CACHE.lastSaveMs = now;
            NEARBY_SIGN_CACHE.dirty = false;
            System.out.println("[publish-debug] nearby_sign_cache saved dimPos="
                + NEARBY_SIGN_CACHE.cache.dimPosSnapshot().size());
        } catch (Exception e) {
            System.out.println("[publish-debug] nearby_sign_cache save_failed type="
                + e.getClass().getSimpleName() + " msg=" + String.valueOf(e.getMessage()));
        }
    }

    private static String resolveNearbyScopeId(FabricBridge bridge) {
        if (bridge == null) {
            return "default";
        }
        try {
            String idLine = ScoreboardScopeResolver.extractScoreboardIdLine(bridge.scoreboardLines());
            return ScoreboardScopeResolver.normalizeScopeId(idLine);
        } catch (Exception ignore) {
            return "default";
        }
    }

    private static boolean sameLines(String[] a, String[] b) {
        if (a == b) {
            return true;
        }
        if (a == null || b == null || a.length != b.length) {
            return false;
        }
        for (int i = 0; i < a.length; i++) {
            String x = a[i] == null ? "" : a[i];
            String y = b[i] == null ? "" : b[i];
            if (!x.equals(y)) {
                return false;
            }
        }
        return true;
    }

    private static void handleSprintFlyBoost(MinecraftClient mc) {
        if (mc == null || mc.player == null || mc.options == null) {
            return;
        }
        boolean enabled = settings().getBoolean("sprintfly.enabled", false);
        int playerId = System.identityHashCode(mc.player);
        if (playerId != SPRINTF_PLAYER_ID) {
            SPRINTF_PLAYER_ID = playerId;
            SPRINTF_ACTIVE = false;
            SPRINTF_BASE_SPEED = Float.NaN;
        }
        float current = readFlySpeed(mc);
        if (!enabled) {
            if (SPRINTF_ACTIVE) {
                restoreFlySpeed(mc);
            } else if (!Float.isNaN(current)) {
                SPRINTF_BASE_SPEED = current;
            }
            return;
        }
        boolean canFly;
        try {
            canFly = mc.player.abilities.allowFlying;
        } catch (Exception e) {
            canFly = false;
        }
        if (!canFly) {
            if (SPRINTF_ACTIVE) {
                restoreFlySpeed(mc);
            } else if (!Float.isNaN(current)) {
                SPRINTF_BASE_SPEED = current;
            }
            return;
        }
        boolean ctrl = false;
        try {
            ctrl = mc.options.keySprint != null && mc.options.keySprint.isPressed();
        } catch (Exception ignore) {
        }
        float target = parseCtrlFlySpeed(settings().getString("sprintfly.ctrlFlySpeed", "0.12"));
        if (ctrl) {
            if (Float.isNaN(SPRINTF_BASE_SPEED) && !Float.isNaN(current)) {
                SPRINTF_BASE_SPEED = current;
            }
            if (Float.isNaN(current) || Math.abs(current - target) > 0.0005f) {
                setFlySpeed(mc, target);
            }
            SPRINTF_ACTIVE = true;
            return;
        }
        if (SPRINTF_ACTIVE) {
            restoreFlySpeed(mc);
            return;
        }
        if (!Float.isNaN(current)) {
            SPRINTF_BASE_SPEED = current;
        }
    }

    private static void restoreFlySpeed(MinecraftClient mc) {
        if (mc == null || mc.player == null) {
            return;
        }
        if (!Float.isNaN(SPRINTF_BASE_SPEED)) {
            setFlySpeed(mc, SPRINTF_BASE_SPEED);
        }
        SPRINTF_ACTIVE = false;
    }

    private static float parseCtrlFlySpeed(String raw) {
        if (raw == null) {
            return 0.12f;
        }
        String v = raw.trim().replace(',', '.');
        try {
            float f = Float.parseFloat(v);
            if (f < 0.01f) {
                return 0.01f;
            }
            if (f > 1.0f) {
                return 1.0f;
            }
            return f;
        } catch (Exception e) {
            return 0.12f;
        }
    }

    private static float readFlySpeed(MinecraftClient mc) {
        if (mc == null || mc.player == null) {
            return Float.NaN;
        }
        try {
            Object abilities = mc.player.abilities;
            try {
                Object v = abilities.getClass().getMethod("getFlySpeed").invoke(abilities);
                if (v instanceof Number) {
                    return ((Number) v).floatValue();
                }
            } catch (Exception ignore) {
            }
            try {
                java.lang.reflect.Field f = abilities.getClass().getDeclaredField("flySpeed");
                f.setAccessible(true);
                Object v = f.get(abilities);
                if (v instanceof Number) {
                    return ((Number) v).floatValue();
                }
            } catch (Exception ignore) {
            }
        } catch (Exception ignore) {
        }
        return Float.NaN;
    }

    private static void setFlySpeed(MinecraftClient mc, float speed) {
        if (mc == null || mc.player == null) {
            return;
        }
        try {
            Object abilities = mc.player.abilities;
            boolean updated = false;
            try {
                abilities.getClass().getMethod("setFlySpeed", Float.TYPE).invoke(abilities, Float.valueOf(speed));
                updated = true;
            } catch (Exception ignore) {
            }
            if (!updated) {
                try {
                    java.lang.reflect.Field f = abilities.getClass().getDeclaredField("flySpeed");
                    f.setAccessible(true);
                    f.setFloat(abilities, speed);
                    updated = true;
                } catch (Exception ignore) {
                }
            }
            if (updated) {
                mc.player.sendAbilitiesUpdate();
            }
        } catch (Exception ignore) {
        }
    }

    private static void refreshCodeEntrySeedHint(MinecraftClient mc) {
        if (mc == null || mc.player == null || mc.world == null) {
            LAST_EDITOR_LIKE = false;
            LAST_EDITOR_DIM = "";
            return;
        }
        String dim = String.valueOf(mc.world.getRegistryKey().getValue());
        boolean editorLike = false;
        try {
            ScoreboardContext ctx = ScoreboardParser.parse(new FabricBridge(null).scoreboardLines());
            editorLike = ctx.editorLike();
        } catch (Throwable t) {
            editorLike = false;
            long now = System.currentTimeMillis();
            if (now - LAST_SCOREBOARD_PARSE_ERR_LOG_MS >= 1800L) {
                LAST_SCOREBOARD_PARSE_ERR_LOG_MS = now;
                System.out.println("[printer-debug] scoreboard_parse_failed type="
                    + t.getClass().getSimpleName()
                    + " message=" + String.valueOf(t.getMessage()));
            }
        }
        boolean dimChanged = !dim.equals(LAST_EDITOR_DIM);
        boolean entered = !LAST_EDITOR_LIKE && editorLike;
        LAST_EDITOR_DIM = dim;
        LAST_EDITOR_LIKE = editorLike;
        if (!entered && !dimChanged) {
            return;
        }
        if (!editorLike) {
            return;
        }
        tryResolveSeedHint(mc, dimChanged ? "code_entry_dim_change" : "code_entry_transition");
    }

    private static void tryResolveSeedHint(MinecraftClient mc, String reason) {
        if (mc == null || mc.player == null || mc.world == null) {
            return;
        }
        String dim = String.valueOf(mc.world.getRegistryKey().getValue());
        BlockPos feet = mc.player.getBlockPos();
        BlockPos fromFeetOffset = feet.add(-2, -1, -2);
        if (isBlueGlassAt(mc, fromFeetOffset)) {
            LAST_WORLD_SEED_HINT = fromFeetOffset;
            logSeedHint(reason, dim, feet, "offset(-2,-1,-2)", fromFeetOffset, true);
            return;
        }
        BlockPos fixed = new BlockPos(219, 0, 219);
        if (isBlueGlassAt(mc, fixed)) {
            LAST_WORLD_SEED_HINT = fixed;
            logSeedHint(reason, dim, feet, "fixed(219,0,219)", fixed, true);
            return;
        }
        BlockPos barrierHint = detectSeedFromBarrierCorner(mc);
        if (barrierHint != null) {
            LAST_WORLD_SEED_HINT = barrierHint;
            logSeedHint(reason, dim, feet, "barrier(max_xz)-5,-5+-1scan", barrierHint, true);
            return;
        }
        LAST_WORLD_SEED_HINT = null;
        logSeedHint(reason, dim, feet, "all_missed", fromFeetOffset, false);
    }

    private static void logSeedHint(String reason, String dim, BlockPos feet, String source, BlockPos candidate, boolean hit) {
        System.out.println("[printer-debug] world_seed_probe reason=" + reason
            + " dim=" + dim
            + " playerFeet=" + feet
            + " source=" + source
            + " candidate=" + candidate
            + " hit=" + hit);
    }

    private static boolean isBlueGlassAt(MinecraftClient mc, BlockPos pos) {
        if (mc == null || mc.world == null || pos == null) {
            return false;
        }
        try {
            return mc.world.isChunkLoaded(pos)
                && mc.world.getBlockState(pos).getBlock() == Blocks.LIGHT_BLUE_STAINED_GLASS;
        } catch (Exception ignore) {
            return false;
        }
    }

    private static BlockPos detectSeedFromBarrierCorner(MinecraftClient mc) {
        if (mc == null || mc.player == null || mc.world == null) {
            return null;
        }
        BlockPos feet = mc.player.getBlockPos();
        final int radius = 384;
        int minX = feet.getX() - radius;
        int maxX = feet.getX() + radius;
        int minZ = feet.getZ() - radius;
        int maxZ = feet.getZ() + radius;
        int bestX = Integer.MIN_VALUE;
        int bestZ = Integer.MIN_VALUE;
        boolean foundBarrier = false;
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                BlockPos p = new BlockPos(x, 0, z);
                if (!mc.world.isChunkLoaded(p)) {
                    continue;
                }
                if (mc.world.getBlockState(p).getBlock() != Blocks.BARRIER) {
                    continue;
                }
                if (!foundBarrier || x > bestX || (x == bestX && z > bestZ)) {
                    bestX = x;
                    bestZ = z;
                    foundBarrier = true;
                }
            }
        }
        if (!foundBarrier) {
            return null;
        }
        int baseX = bestX - 5;
        int baseZ = bestZ - 5;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                BlockPos candidate = new BlockPos(baseX + dx, 0, baseZ + dz);
                if (isBlueGlassAt(mc, candidate)) {
                    System.out.println("[printer-debug] world_seed_from_barrier_success barrierCorner="
                        + bestX + ",0," + bestZ
                        + " base=" + baseX + ",0," + baseZ
                        + " found=" + candidate);
                    return candidate;
                }
            }
        }
        System.out.println("[printer-debug] world_seed_from_barrier_miss barrierCorner="
            + bestX + ",0," + bestZ
            + " base=" + baseX + ",0," + baseZ
            + " scan=3x3");
        return null;
    }

    private static void renderSelectionHighlights(MinecraftClient mc) {
        if (mc == null || mc.player == null || mc.world == null) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - LAST_SELECTOR_HIGHLIGHT_MS < 220L) {
            return;
        }
        LAST_SELECTOR_HIGHLIGHT_MS = now;
        String dim = String.valueOf(mc.world.getRegistryKey().getValue());
        int selectedInDim = 0;
        int shown = 0;
        java.util.Iterator<Map.Entry<String, SelectedRow>> it = SELECTED.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, SelectedRow> e = it.next();
            SelectedRow row = e.getValue();
            if (row == null || !dim.equals(row.dimension())) {
                continue;
            }
            BlockPos anchor = new BlockPos(row.x(), row.y(), row.z());
            if (!isBlueGlassAt(mc, anchor)) {
                it.remove();
                System.out.println("[printer-debug] selector_auto_unselect reason=anchor_missing dim="
                    + row.dimension() + " pos=" + row.x() + "," + row.y() + "," + row.z());
                continue;
            }
            selectedInDim++;
            if (shown >= 80) {
                break;
            }
            shown++;
        }
        if (selectedInDim > 0 && shown == 0 && now - LAST_SELECTOR_HIGHLIGHT_LOG_MS >= 1500L) {
            LAST_SELECTOR_HIGHLIGHT_LOG_MS = now;
            System.out.println("[printer-debug] selector_highlight_skipped dim=" + dim
                + " selectedInDim=" + selectedInDim
                + " shown=" + shown);
        }
    }

    private static void renderSelectionOutlines(WorldRenderContext context) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (context == null || mc == null || mc.world == null || mc.player == null) {
            return;
        }
        String dim = String.valueOf(mc.world.getRegistryKey().getValue());
        TestcaseTool.MarkerView marker = TestcaseTool.markerView();
        if (SELECTED.isEmpty() && (marker == null || !dim.equals(marker.dimension()))) {
            return;
        }
        if (context.camera() == null) {
            return;
        }
        MatrixStack matrices = context.matrixStack();
        if (matrices == null) {
            return;
        }
        List<Box> boxes = collectRenderBoxes(mc, dim, marker);
        if (boxes.isEmpty()) {
            return;
        }
        int mode = TestcaseTool.outlineMode();
        if (mode == 1) {
            drawBoxesTessellatorWorld(boxes, false, 2.0F);
        } else if (mode == 2) {
            drawBoxesTessellatorCamera(boxes, context, false, 2.0F);
        } else if (mode == 3) {
            drawBoxesTessellatorCamera(boxes, context, true, 2.0F);
        } else {
            drawBoxesTessellatorCamera(boxes, context, false, 3.0F);
        }
        long now = System.currentTimeMillis();
        if (now - LAST_TESTCASE_RENDER_LOG_MS > 1200L) {
            LAST_TESTCASE_RENDER_LOG_MS = now;
            System.out.println("[printer-debug] testcase_outline_rendered mode=" + mode + " boxes=" + boxes.size());
        }
    }

    private static List<Box> collectRenderBoxes(MinecraftClient mc, String dim, TestcaseTool.MarkerView marker) {
        List<Box> boxes = new ArrayList<>();
        int drawn = 0;
        for (SelectedRow row : SELECTED.values()) {
            if (row == null || !dim.equals(row.dimension())) {
                continue;
            }
            BlockPos anchor = new BlockPos(row.x(), row.y(), row.z());
            if (!isBlueGlassAt(mc, anchor)) {
                continue;
            }
            boxes.add(new Box(anchor.up()).expand(0.003));
            drawn++;
            if (drawn >= 120) {
                break;
            }
        }
        if (marker != null && dim.equals(marker.dimension())) {
            BlockPos markerPos = new BlockPos(marker.x(), marker.y(), marker.z());
            boxes.add(new Box(markerPos).expand(0.01));
        }
        return boxes;
    }

    private static void drawBoxesTessellatorWorld(List<Box> boxes, boolean depthEnabled, float lineWidth) {
        float[] outline = selectionOutlineColor();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.lineWidth(lineWidth);
        if (depthEnabled) {
            RenderSystem.enableDepthTest();
            RenderSystem.depthMask(true);
        } else {
            RenderSystem.disableDepthTest();
            RenderSystem.depthMask(false);
        }
        RenderSystem.disableTexture();
        try {
            BufferBuilder bb = Tessellator.getInstance().getBuffer();
            bb.begin(GL11.GL_LINES, VertexFormats.POSITION_COLOR);
            for (Box box : boxes) {
                emitBoxEdges(
                    bb,
                    box.minX, box.minY, box.minZ,
                    box.maxX, box.maxY, box.maxZ,
                    outline[0], outline[1], outline[2], 1.0F
                );
            }
            Tessellator.getInstance().draw();
        } finally {
            RenderSystem.enableTexture();
            RenderSystem.depthMask(true);
            RenderSystem.enableDepthTest();
            RenderSystem.lineWidth(1.0F);
            RenderSystem.disableBlend();
        }
    }

    private static void drawBoxesTessellatorCamera(
        List<Box> boxes,
        WorldRenderContext context,
        boolean depthEnabled,
        float lineWidth
    ) {
        if (context == null || context.camera() == null) {
            return;
        }
        Vec3d cam = context.camera().getPos();
        float[] outline = selectionOutlineColor();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.lineWidth(lineWidth);
        if (depthEnabled) {
            RenderSystem.enableDepthTest();
            RenderSystem.depthMask(true);
        } else {
            RenderSystem.disableDepthTest();
            RenderSystem.depthMask(false);
        }
        RenderSystem.disableTexture();
        try {
            BufferBuilder bb = Tessellator.getInstance().getBuffer();
            bb.begin(GL11.GL_LINES, VertexFormats.POSITION_COLOR);
            for (Box box : boxes) {
                double minX = box.minX - cam.x;
                double minY = box.minY - cam.y;
                double minZ = box.minZ - cam.z;
                double maxX = box.maxX - cam.x;
                double maxY = box.maxY - cam.y;
                double maxZ = box.maxZ - cam.z;
                emitBoxEdges(
                    bb,
                    minX, minY, minZ,
                    maxX, maxY, maxZ,
                    outline[0], outline[1], outline[2], 1.0F
                );
            }
            Tessellator.getInstance().draw();
        } finally {
            RenderSystem.enableTexture();
            RenderSystem.depthMask(true);
            RenderSystem.enableDepthTest();
            RenderSystem.lineWidth(1.0F);
            RenderSystem.disableBlend();
        }
    }

    private static void emitBoxEdges(
        BufferBuilder bb,
        double minX, double minY, double minZ,
        double maxX, double maxY, double maxZ,
        float r, float g, float b, float a
    ) {
        // Bottom face edges
        emitLine(bb, minX, minY, minZ, maxX, minY, minZ, r, g, b, a);
        emitLine(bb, maxX, minY, minZ, maxX, minY, maxZ, r, g, b, a);
        emitLine(bb, maxX, minY, maxZ, minX, minY, maxZ, r, g, b, a);
        emitLine(bb, minX, minY, maxZ, minX, minY, minZ, r, g, b, a);
        // Top face edges
        emitLine(bb, minX, maxY, minZ, maxX, maxY, minZ, r, g, b, a);
        emitLine(bb, maxX, maxY, minZ, maxX, maxY, maxZ, r, g, b, a);
        emitLine(bb, maxX, maxY, maxZ, minX, maxY, maxZ, r, g, b, a);
        emitLine(bb, minX, maxY, maxZ, minX, maxY, minZ, r, g, b, a);
        // Vertical edges
        emitLine(bb, minX, minY, minZ, minX, maxY, minZ, r, g, b, a);
        emitLine(bb, maxX, minY, minZ, maxX, maxY, minZ, r, g, b, a);
        emitLine(bb, maxX, minY, maxZ, maxX, maxY, maxZ, r, g, b, a);
        emitLine(bb, minX, minY, maxZ, minX, maxY, maxZ, r, g, b, a);
    }

    private static void emitLine(
        BufferBuilder bb,
        double x1, double y1, double z1,
        double x2, double y2, double z2,
        float r, float g, float b, float a
    ) {
        bb.vertex(x1, y1, z1).color(r, g, b, a).next();
        bb.vertex(x2, y2, z2).color(r, g, b, a).next();
    }

    private static float[] selectionOutlineColor() {
        String raw = settings().getString("selector.outlineColor", "255,255,0");
        float[] fallback = new float[] {1.0F, 1.0F, 0.0F};
        if (raw == null) {
            return fallback;
        }
        String[] parts = raw.trim().split("[,\\s]+");
        if (parts.length < 3) {
            return fallback;
        }
        try {
            int r = clamp255(Integer.parseInt(parts[0]));
            int g = clamp255(Integer.parseInt(parts[1]));
            int b = clamp255(Integer.parseInt(parts[2]));
            return new float[] {r / 255.0F, g / 255.0F, b / 255.0F};
        } catch (Exception e) {
            return fallback;
        }
    }

    private static int clamp255(int v) {
        if (v < 0) {
            return 0;
        }
        if (v > 255) {
            return 255;
        }
        return v;
    }

    private static void traceRuntimeTickProbe(MinecraftClient mc) {
        if (mc == null || mc.world == null) {
            return;
        }
        long worldTick = mc.world.getTime();
        long nowMs = System.currentTimeMillis();
        String dim = mc.world.getRegistryKey() == null ? "unknown" : String.valueOf(mc.world.getRegistryKey().getValue());
        if (worldTick == LAST_RUNTIME_TICK_WORLD_TIME && dim.equals(LAST_RUNTIME_TICK_DIM)) {
            long dtMs = LAST_RUNTIME_TICK_WALL_MS <= 0L ? -1L : (nowMs - LAST_RUNTIME_TICK_WALL_MS);
            System.out.println("[printer-debug] runtime_tick_duplicate_detected dim=" + dim
                + " worldTick=" + worldTick
                + " dtMs=" + dtMs
                + " initCount=" + INIT_CALL_COUNT);
        }
        LAST_RUNTIME_TICK_WORLD_TIME = worldTick;
        LAST_RUNTIME_TICK_WALL_MS = nowMs;
        LAST_RUNTIME_TICK_DIM = dim;
    }

    private static boolean shouldSkipDuplicateEndTick(MinecraftClient mc) {
        if (mc == null || mc.world == null) {
            LAST_END_TICK_WORLD_TIME = Long.MIN_VALUE;
            LAST_END_TICK_DIM = "";
            return false;
        }
        long worldTick = mc.world.getTime();
        String dim = mc.world.getRegistryKey() == null ? "unknown" : String.valueOf(mc.world.getRegistryKey().getValue());
        if (worldTick == LAST_END_TICK_WORLD_TIME && dim.equals(LAST_END_TICK_DIM)) {
            return true;
        }
        LAST_END_TICK_WORLD_TIME = worldTick;
        LAST_END_TICK_DIM = dim;
        return false;
    }

    private static void traceLegacyClickBurst(MinecraftClient mc, int x, int y, int z, String purpose) {
        if (mc == null || mc.world == null) {
            return;
        }
        long nowMs = System.currentTimeMillis();
        long worldTick = mc.world.getTime();
        String key = (purpose == null ? "-" : purpose)
            + "|" + x + "," + y + "," + z
            + "|tick=" + worldTick;
        if (key.equals(LAST_LEGACY_CLICK_KEY) && nowMs - LAST_LEGACY_CLICK_MS <= 180L) {
            LAST_LEGACY_CLICK_BURST++;
            if (LAST_LEGACY_CLICK_BURST <= 4 || LAST_LEGACY_CLICK_BURST % 5 == 0) {
                System.out.println("[printer-debug] legacy_click_duplicate_suspect burst=" + LAST_LEGACY_CLICK_BURST
                    + " key=" + key
                    + " dtMs=" + (nowMs - LAST_LEGACY_CLICK_MS)
                    + " initCount=" + INIT_CALL_COUNT);
            }
        } else {
            LAST_LEGACY_CLICK_BURST = 0;
        }
        LAST_LEGACY_CLICK_KEY = key;
        LAST_LEGACY_CLICK_MS = nowMs;
    }

    private static boolean isShutdownLikeState(MinecraftClient mc) {
        if (mc == null) {
            return true;
        }
        if (mc.world == null || mc.player == null) {
            return true;
        }
        if (mc.currentScreen == null) {
            return false;
        }
        String n = mc.currentScreen.getClass().getSimpleName().toLowerCase();
        return n.contains("save") || n.contains("progress") || n.contains("downloading") || n.contains("disconnect");
    }

    private static void clearLocalTpQueue() {
        synchronized (LOCAL_TP_STATE) {
            if (!LOCAL_TP_STATE.queue.isEmpty()) {
                System.out.println("[printer-debug] shutdown_tp_queue_cleared size=" + LOCAL_TP_STATE.queue.size());
            }
            LOCAL_TP_STATE.queue.clear();
            LOCAL_TP_STATE.nextMs = 0L;
        }
    }

    private static void resetSnapshotCache() {
        SNAPSHOT_CACHE_TICK = Long.MIN_VALUE;
        SNAPSHOT_CACHE_SYNC_ID = -1;
        SNAPSHOT_CACHE_SCREEN_ID = -1;
        SNAPSHOT_CACHE_VIEW = ContainerView.empty();
    }

    private static boolean setPlayerPositionLocal(MinecraftClient mc, double x, double y, double z) {
        if (mc == null || mc.player == null) {
            return false;
        }
        try {
            mc.player.setPosition(x, y, z);
            return true;
        } catch (Exception ignore) {
        }
        try {
            java.lang.reflect.Method m = mc.player.getClass().getMethod("updatePosition", double.class, double.class, double.class);
            m.invoke(mc.player, x, y, z);
            return true;
        } catch (Exception ignore) {
        }
        try {
            java.lang.reflect.Method m = mc.player.getClass().getMethod("refreshPositionAndAngles", double.class, double.class, double.class, float.class, float.class);
            m.invoke(mc.player, x, y, z, mc.player.yaw, mc.player.pitch);
            return true;
        } catch (Exception ignore) {
        }
        return false;
    }
}

