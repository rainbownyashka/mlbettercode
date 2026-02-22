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
import com.rainbow_universe.bettercode.core.place.BlueGlassSearch;
import com.rainbow_universe.bettercode.core.settings.ModSettingsService;
import com.rainbow_universe.bettercode.core.settings.SettingDef;
import com.rainbow_universe.bettercode.core.settings.SettingType;
import com.rainbow_universe.bettercode.core.util.LegacyBlockIdCompat;
import com.rainbow_universe.bettercode.core.util.ReflectCompat;
import com.rainbow_universe.bettercode.core.util.TestcaseTool;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v1.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v1.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
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
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.registry.Registry;

import java.nio.file.Files;
import java.nio.file.Path;
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
    private static int INIT_CALL_COUNT = 0;
    private static long LAST_RUNTIME_TICK_WORLD_TIME = Long.MIN_VALUE;
    private static long LAST_RUNTIME_TICK_WALL_MS = 0L;
    private static String LAST_RUNTIME_TICK_DIM = "";
    private static long LAST_END_TICK_WORLD_TIME = Long.MIN_VALUE;
    private static String LAST_END_TICK_DIM = "";
    private static String LAST_LEGACY_CLICK_KEY = "";
    private static long LAST_LEGACY_CLICK_MS = 0L;
    private static int LAST_LEGACY_CLICK_BURST = 0;
    private static long SNAPSHOT_CACHE_TICK = Long.MIN_VALUE;
    private static int SNAPSHOT_CACHE_SYNC_ID = -1;
    private static int SNAPSHOT_CACHE_SCREEN_ID = -1;
    private static ContainerView SNAPSHOT_CACHE_VIEW = ContainerView.empty();

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
                            )))))
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
            traceRuntimeTickProbe(client);
            handleLocalTpPath(client);
            refreshWorldLoadSeedHint(client);
            runtime().handleClientTick(new FabricBridge(null), System.currentTimeMillis());
        });
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

    private static int issueCodeSelectorTool(FabricClientCommandSource source) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.player == null) {
            source.sendError(new LiteralText("[code selector] player is unavailable"));
            return 0;
        }
        ItemStack stick = new ItemStack(Items.STICK);
        stick.setCustomName(new LiteralText("§eMLDSL Code Selector"));
        try {
            stick.setTag(StringNbtReader.parse("{" + CODE_SELECTOR_TAG + ":1b,Unbreakable:1b}"));
        } catch (Exception ignore) {
        }
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
                RUNTIME = new RuntimeCore(new FabricCoreLogger(), settings());
            }
            return RUNTIME;
        }
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
                    System.out.println("[publish-debug] SIGN_READ be_null pos=" + x + "," + y + "," + z + " block=" + blockId);
                    return null;
                }
                String[] out = new String[4];
                for (int i = 0; i < 4; i++) {
                    out[i] = readSignLineReflect(be, i);
                }
                if (!hasAnySignText(out)) {
                    String[] fromNbt = readSignLinesFromNbt(be);
                    if (hasAnySignText(fromNbt)) {
                        System.out.println("[publish-debug] SIGN_READ adapter=1165 pos=" + x + "," + y + "," + z
                            + " block=" + blockId
                            + " fallback=nbt"
                            + " lines=" + formatLines(fromNbt));
                        return fromNbt;
                    }
                }
                System.out.println("[publish-debug] SIGN_READ adapter=1165 pos=" + x + "," + y + "," + z
                    + " block=" + blockId
                    + " lines=" + formatLines(out));
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
        BlockPos feet = mc.player.getBlockPos();
        BlockPos candidate = feet.add(-2, -1, -2);
        boolean hit = false;
        try {
            hit = mc.world.isChunkLoaded(candidate)
                && mc.world.getBlockState(candidate).getBlock() == Blocks.LIGHT_BLUE_STAINED_GLASS;
        } catch (Exception ignore) {
            hit = false;
        }
        LAST_WORLD_SEED_HINT = hit ? candidate : null;
        System.out.println("[printer-debug] world_load_seed_probe dim=" + dim
            + " playerFeet=" + feet
            + " candidate=" + candidate
            + " hit=" + hit);
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
