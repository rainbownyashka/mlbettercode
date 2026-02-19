package com.rainbow_universe.bettercode.fabric121;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.brigadier.CommandDispatcher;
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
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.StringNbtReader;
import net.minecraft.registry.Registries;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardDisplaySlot;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.ClickEvent;
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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class BetterCodeFabric121 implements ClientModInitializer {
    private static final String CODE_SELECTOR_TAG = "mldsl_code_selector";
    private static final long CODE_SELECTOR_TOGGLE_COOLDOWN_MS = 180L;
    private static long lastCodeSelectorToggleMs = 0L;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Map<String, SelectedBlock> SELECTED = new LinkedHashMap<>();
    private static volatile CommandDispatcher<FabricClientCommandSource> CLIENT_DISPATCHER;
    private static volatile ModSettingsService SETTINGS;
    private static volatile RuntimeCore RUNTIME;
    private static final DirectPlaceState DIRECT_PLACE_STATE = new DirectPlaceState();

    @Override
    public void onInitializeClient() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> CLIENT_DISPATCHER = dispatcher);

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(
            ClientCommandManager.literal("bc_select")
                .executes(ctx -> toggleCurrentBlock(ctx.getSource()))
        ));

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(
            ClientCommandManager.literal("bc_select_clear")
                .executes(ctx -> clearSelection(ctx.getSource()))
        ));

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(
            ClientCommandManager.literal("bc_select_list")
                .executes(ctx -> listSelection(ctx.getSource()))
        ));

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(
            ClientCommandManager.literal("bc_export_selected")
                .executes(ctx -> exportSelection(ctx.getSource(), "selection"))
                .then(ClientCommandManager.argument("name", StringArgumentType.greedyString())
                    .executes(ctx -> exportSelection(ctx.getSource(), StringArgumentType.getString(ctx, "name"))))
        ));

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(
            ClientCommandManager.literal("bc_print_plan")
                .executes(ctx -> inspectPlan(ctx.getSource(), defaultPlanPath()))
                .then(ClientCommandManager.argument("path", StringArgumentType.greedyString())
                    .executes(ctx -> inspectPlan(ctx.getSource(), Path.of(StringArgumentType.getString(ctx, "path")))))
        ));

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(
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
        ));

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(
            ClientCommandManager.literal("loadmodule")
                .then(ClientCommandManager.argument("postId", StringArgumentType.word())
                    .executes(ctx -> loadModule(ctx.getSource(), StringArgumentType.getString(ctx, "postId"), null))
                    .then(ClientCommandManager.argument("file", StringArgumentType.greedyString())
                        .executes(ctx -> loadModule(
                            ctx.getSource(),
                            StringArgumentType.getString(ctx, "postId"),
                            StringArgumentType.getString(ctx, "file")
                        ))))
        ));

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(
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
        ));

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(
            ClientCommandManager.literal("confirmload")
                .executes(ctx -> confirmLoad(ctx.getSource()))
        ));

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(
            ClientCommandManager.literal("module")
                .then(ClientCommandManager.literal("publish")
                    .executes(ctx -> publishModule(ctx.getSource())))
        ));

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(
            ClientCommandManager.literal("codeselector")
                .executes(ctx -> runCodeSelectorCommand(ctx.getSource()))
        ));

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(
            ClientCommandManager.literal("testcase")
                .then(ClientCommandManager.literal("setpos")
                    .executes(ctx -> testcaseSetPos(ctx.getSource())))
                .then(ClientCommandManager.literal("rightclick")
                    .executes(ctx -> testcaseRightClick(ctx.getSource())))
                .then(ClientCommandManager.literal("tp")
                    .executes(ctx -> testcaseTp(ctx.getSource())))
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client ->
            runtime().handleClientTick(new FabricBridge(null), System.currentTimeMillis()));
        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (hand != Hand.MAIN_HAND || player == null || world == null || hitResult == null) {
                return ActionResult.PASS;
            }
            ItemStack held = player.getMainHandStack();
            if (!isCodeSelectorItem(held)) {
                return ActionResult.PASS;
            }
            return toggleSelectionAtHit121(player, world, hitResult.getBlockPos(), true)
                ? ActionResult.SUCCESS
                : ActionResult.FAIL;
        });
    }

    private static int loadModule(FabricClientCommandSource source, String postId, String file) {
        RuntimeResult result = runtime().handleLoadModule(postId, file, null, new FabricBridge(source));
        if (result.ok()) {
            source.sendFeedback(Text.literal(result.message()));
            return 1;
        }
        source.sendError(Text.literal("[" + result.errorCode() + "] " + result.message()));
        return 0;
    }

    private static int runMldsl(FabricClientCommandSource source, String postId, String config, String syntax) {
        System.out.println("[printer-debug] run args postId=" + postId
            + " config=" + (config == null ? "default" : config)
            + " syntax=" + syntax);
        RuntimeResult result = runtime().handleRun(postId, config, new FabricBridge(source));
        if (result.ok()) {
            source.sendFeedback(Text.literal(result.message()));
            return 1;
        }
        source.sendError(Text.literal("[" + result.errorCode() + "] " + result.message()));
        return 0;
    }

    private static int runLocal(FabricClientCommandSource source, String path) {
        RuntimeResult result = runtime().handleRunLocal(path, false, new FabricBridge(source));
        if (result.ok()) {
            source.sendFeedback(Text.literal(result.message()));
            return 1;
        }
        source.sendError(Text.literal("[" + result.errorCode() + "] " + result.message()));
        return 0;
    }

    private static int checkLocal(FabricClientCommandSource source, String path) {
        RuntimeResult result = runtime().handleRunLocal(path, true, new FabricBridge(source));
        if (result.ok()) {
            source.sendFeedback(Text.literal(result.message()));
            return 1;
        }
        source.sendError(Text.literal("[" + result.errorCode() + "] " + result.message()));
        return 0;
    }

    private static int confirmLoad(FabricClientCommandSource source) {
        RuntimeResult result = runtime().handleConfirmLoad(new FabricBridge(source));
        if (result.ok()) {
            source.sendFeedback(Text.literal(result.message()));
            return 1;
        }
        source.sendError(Text.literal("[" + result.errorCode() + "] " + result.message()));
        return 0;
    }

    private static int publishModule(FabricClientCommandSource source) {
        RuntimeResult result = runtime().handlePublish(new FabricBridge(source));
        if (result.ok()) {
            source.sendFeedback(Text.literal(result.message()));
            return 1;
        }
        if (result.errorCode() == com.rainbow_universe.bettercode.core.RuntimeErrorCode.NO_PENDING_PLAN) {
            MinecraftClient mc = MinecraftClient.getInstance();
            boolean hasSelection = !SELECTED.isEmpty();
            boolean hasTool = hasCodeSelectorInInventory121(mc);
            if (hasSelection || hasTool) {
                source.sendError(Text.literal("[NO_PENDING_PLAN] No pending module data. Use /loadmodule first."));
                return 0;
            }
            return issueCodeSelectorTool(source);
        }
        source.sendError(Text.literal("[" + result.errorCode() + "] " + result.message()));
        return 0;
    }

    private static int issueCodeSelectorTool(FabricClientCommandSource source) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.player == null) {
            source.sendError(Text.literal("[code selector] player is unavailable"));
            return 0;
        }
        ItemStack stick = new ItemStack(Items.STICK);
        applyCustomName(stick, "§eMLDSL Code Selector");
        applyNbtString(stick, "{" + CODE_SELECTOR_TAG + ":1b,Unbreakable:1b}");
        boolean ok = mc.player.giveItemStack(stick);
        if (!ok) {
            source.sendError(Text.literal("[code selector] inventory full"));
            return 0;
        }
        source.sendFeedback(Text.literal("[code selector] selector stick granted. Use legacy-style row selection flow."));
        return 1;
    }

    private static int runCodeSelectorCommand(FabricClientCommandSource source) {
        return issueCodeSelectorTool(source);
    }

    private static int testcaseSetPos(FabricClientCommandSource source) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.player == null || mc.world == null) {
            source.sendError(Text.literal("[testcase] player/world unavailable"));
            return 0;
        }
        BlockPos pos = mc.player.getBlockPos();
        HitResult target = mc.crosshairTarget;
        if (target instanceof BlockHitResult) {
            pos = ((BlockHitResult) target).getBlockPos();
        }
        String dim = String.valueOf(mc.world.getRegistryKey().getValue());
        TestcaseTool.Result result = TestcaseTool.setPos(dim, pos.getX(), pos.getY(), pos.getZ());
        source.sendFeedback(Text.literal("[testcase] " + result.message()));
        return result.ok() ? 1 : 0;
    }

    private static int testcaseRightClick(FabricClientCommandSource source) {
        TestcaseTool.Result result = TestcaseTool.rightClick(new FabricBridge(source));
        if (result.ok()) {
            source.sendFeedback(Text.literal("[testcase] " + result.message()));
            return 1;
        }
        source.sendError(Text.literal("[testcase] " + result.message()));
        return 0;
    }

    private static int testcaseTp(FabricClientCommandSource source) {
        TestcaseTool.Result result = TestcaseTool.tp(new FabricBridge(source));
        if (result.ok()) {
            source.sendFeedback(Text.literal("[testcase] " + result.message()));
            return 1;
        }
        source.sendError(Text.literal("[testcase] " + result.message()));
        return 0;
    }

    private static void applyCustomName(ItemStack stack, String name) {
        if (stack == null || name == null || name.trim().isEmpty()) {
            return;
        }
        try {
            Object text = Text.class.getMethod("literal", String.class).invoke(null, name.trim());
            for (java.lang.reflect.Method method : stack.getClass().getMethods()) {
                if ("setCustomName".equals(method.getName()) && method.getParameterCount() == 1) {
                    method.invoke(stack, text);
                    return;
                }
            }
        } catch (Exception ignore) {
        }
    }

    private static void applyNbtString(ItemStack stack, String rawNbt) {
        if (stack == null || rawNbt == null || rawNbt.trim().isEmpty()) {
            return;
        }
        try {
            Object parsed = StringNbtReader.class.getMethod("parse", String.class).invoke(null, rawNbt.trim());
            for (java.lang.reflect.Method method : stack.getClass().getMethods()) {
                if (("setNbt".equals(method.getName()) || "setTag".equals(method.getName()))
                    && method.getParameterCount() == 1) {
                    method.invoke(stack, parsed);
                    return;
                }
            }
        } catch (Exception ignore) {
        }
    }

    private static int toggleCurrentBlock(FabricClientCommandSource source) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null) {
            source.sendError(Text.literal("No world/player available."));
            return 0;
        }
        HitResult target = mc.crosshairTarget;
        if (!(target instanceof BlockHitResult bhr)) {
            source.sendError(Text.literal("Look at a block and run /bc_select."));
            return 0;
        }
        BlockPos pos = bhr.getBlockPos();
        if (!toggleSelectionAtHit121(mc.player, mc.world, pos, false)) {
            return 0;
        }
        source.sendFeedback(Text.literal("Selection toggled (selected=" + SELECTED.size() + ")"));
        return 1;
    }

    private static boolean toggleSelectionAtHit121(net.minecraft.entity.player.PlayerEntity player, net.minecraft.world.World world, BlockPos clicked, boolean showActionBar) {
        if (player == null || world == null || clicked == null) {
            return false;
        }
        long now = System.currentTimeMillis();
        if (now - lastCodeSelectorToggleMs < CODE_SELECTOR_TOGGLE_COOLDOWN_MS) {
            return true;
        }
        lastCodeSelectorToggleMs = now;
        BlockPos glassPos = resolveCodeSelectorGlassPos121(world, clicked);
        if (glassPos == null) {
            if (showActionBar) {
                player.sendMessage(Text.literal("§cКликни по блоку над голубым стеклом"), true);
            }
            return false;
        }
        BlockPos start = glassPos.up();
        if (world.getBlockState(start).isAir()) {
            if (showActionBar) {
                player.sendMessage(Text.literal("§cПустая строка: над стеклом воздух"), true);
            }
            return false;
        }
        String dim = world.getRegistryKey().getValue().toString();
        String key = dim + ":" + glassPos.toShortString();
        if (SELECTED.containsKey(key)) {
            SELECTED.remove(key);
            if (showActionBar) {
                player.sendMessage(Text.literal("§eУбрано строк: " + SELECTED.size()), true);
            }
            return true;
        }
        SELECTED.put(key, new SelectedBlock(dim, glassPos.getX(), glassPos.getY(), glassPos.getZ()));
        if (showActionBar) {
            player.sendMessage(Text.literal("§aВыбрано строк: " + SELECTED.size()), true);
        }
        return true;
    }

    private static BlockPos resolveCodeSelectorGlassPos121(net.minecraft.world.World world, BlockPos clicked) {
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
            Object nbt = stack.getClass().getMethod("getNbt").invoke(stack);
            if (nbt == null) {
                return false;
            }
            Object has = nbt.getClass().getMethod("contains", String.class).invoke(nbt, CODE_SELECTOR_TAG);
            if (!(has instanceof Boolean) || !((Boolean) has)) {
                return false;
            }
            Object v = nbt.getClass().getMethod("getBoolean", String.class).invoke(nbt, CODE_SELECTOR_TAG);
            return v instanceof Boolean && ((Boolean) v);
        } catch (Exception ignore) {
        }
        return false;
    }

    private static boolean hasCodeSelectorInInventory121(MinecraftClient mc) {
        if (mc == null || mc.player == null || mc.player.getInventory() == null) {
            return false;
        }
        try {
            for (int i = 0; i < mc.player.getInventory().size(); i++) {
                if (isCodeSelectorItem(mc.player.getInventory().getStack(i))) {
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
        source.sendFeedback(Text.literal("Selection cleared: " + count));
        return 1;
    }

    private static int listSelection(FabricClientCommandSource source) {
        if (SELECTED.isEmpty()) {
            source.sendFeedback(Text.literal("Selection is empty."));
            return 1;
        }
        source.sendFeedback(Text.literal("Selected rows: " + SELECTED.size()));
        for (SelectedBlock block : SELECTED.values()) {
            source.sendFeedback(Text.literal(" - " + block.dimension + " [" + block.x + "," + block.y + "," + block.z + "]"));
        }
        return 1;
    }

    private static int exportSelection(FabricClientCommandSource source, String name) {
        String clean = name == null ? "selection" : name.trim();
        if (clean.isEmpty()) clean = "selection";
        if (SELECTED.isEmpty()) {
            source.sendError(Text.literal("Selection is empty. Use /bc_select first."));
            return 0;
        }
        Path out = runDir().resolve("exportcode_" + clean + "_" + System.currentTimeMillis() + ".json");
        JsonObject root = new JsonObject();
        root.addProperty("type", "bettercode.fabric121.selection");
        root.addProperty("name", clean);
        root.addProperty("count", SELECTED.size());
        JsonArray rows = new JsonArray();
        for (SelectedBlock block : SELECTED.values()) {
            JsonObject o = new JsonObject();
            o.addProperty("dimension", block.dimension);
            o.addProperty("x", block.x);
            o.addProperty("y", block.y);
            o.addProperty("z", block.z);
            rows.add(o);
        }
        root.add("rows", rows);
        try {
            Files.writeString(out, GSON.toJson(root));
            source.sendFeedback(Text.literal("Export saved: " + out));
            return 1;
        } catch (IOException e) {
            source.sendError(Text.literal("Export failed: " + e.getMessage()));
            return 0;
        }
    }

    private static int inspectPlan(FabricClientCommandSource source, Path planPath) {
        Path path = planPath.isAbsolute() ? planPath : runDir().resolve(planPath);
        if (!Files.exists(path)) {
            source.sendError(Text.literal("Plan not found: " + path));
            return 0;
        }
        try {
            JsonObject obj = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
            int entries = obj.has("entries") && obj.get("entries").isJsonArray()
                ? obj.getAsJsonArray("entries").size()
                : 0;
            source.sendFeedback(Text.literal("Plan: " + path + " entries=" + entries));
            return 1;
        } catch (Exception e) {
            source.sendError(Text.literal("Plan parse failed: " + e.getMessage()));
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
        synchronized (BetterCodeFabric121.class) {
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
        synchronized (BetterCodeFabric121.class) {
            if (RUNTIME == null) {
                RUNTIME = new RuntimeCore(new FabricCoreLogger(), settings());
            }
            return RUNTIME;
        }
    }

    private static int modSettingsList(FabricClientCommandSource source) {
        source.sendFeedback(Text.literal("§b[modsettings] list"));
        for (SettingDef d : settings().defs().values()) {
            source.sendFeedback(settingLine(d));
        }
        return 1;
    }

    private static int modSettingsShow(FabricClientCommandSource source, String key) {
        SettingDef d = settings().defs().get(key);
        if (d == null) {
            source.sendError(Text.literal("[modsettings] unknown key: " + key));
            return 0;
        }
        source.sendFeedback(Text.literal("§b[modsettings] " + d.key() + " = " + formatValue(settings().getRaw(d.key()))));
        source.sendFeedback(Text.literal("§7" + d.description()));
        source.sendFeedback(settingControls(d));
        return 1;
    }

    private static int modSettingsToggle(FabricClientCommandSource source, String key) {
        String err = settings().toggle(key);
        if (err != null) {
            source.sendError(Text.literal("[modsettings] toggle failed: " + err));
            return 0;
        }
        source.sendFeedback(Text.literal("[modsettings] " + key + " = " + formatValue(settings().getRaw(key))));
        return 1;
    }

    private static int modSettingsInc(FabricClientCommandSource source, String key, int delta) {
        String err = settings().increment(key, delta);
        if (err != null) {
            source.sendError(Text.literal("[modsettings] inc failed: " + err));
            return 0;
        }
        source.sendFeedback(Text.literal("[modsettings] " + key + " = " + formatValue(settings().getRaw(key))));
        return 1;
    }

    private static int modSettingsSet(FabricClientCommandSource source, String key, String value) {
        String err = settings().setFromString(key, value);
        if (err != null) {
            source.sendError(Text.literal("[modsettings] set failed: " + err));
            return 0;
        }
        source.sendFeedback(Text.literal("[modsettings] " + key + " = " + formatValue(settings().getRaw(key))));
        return 1;
    }

    private static MutableText settingLine(SettingDef d) {
        MutableText line = Text.literal("§e" + d.key() + "§7 = §f" + formatValue(settings().getRaw(d.key())) + " ");
        line.append(settingControls(d));
        return line;
    }

    private static MutableText settingControls(SettingDef d) {
        if (d.type() == SettingType.BOOLEAN) {
            MutableText t = Text.literal("");
            t.append(clickToken("[ON]", "/modsettings set " + d.key() + " true"));
            t.append(Text.literal(" "));
            t.append(clickToken("[OFF]", "/modsettings set " + d.key() + " false"));
            return t;
        }
        if (d.type() == SettingType.INTEGER) {
            MutableText t = Text.literal("");
            t.append(clickToken("[-10]", "/modsettings inc " + d.key() + " -10"));
            t.append(Text.literal(" "));
            t.append(clickToken("[-1]", "/modsettings inc " + d.key() + " -1"));
            t.append(Text.literal(" "));
            t.append(clickToken("[+1]", "/modsettings inc " + d.key() + " 1"));
            t.append(Text.literal(" "));
            t.append(clickToken("[+10]", "/modsettings inc " + d.key() + " 10"));
            return t;
        }
        MutableText t = Text.literal("");
        MutableText set = Text.literal("[SET]");
        set.setStyle(Style.EMPTY.withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/modsettings set " + d.key() + " ")));
        t.append(set);
        return t;
    }

    private static MutableText clickToken(String label, String command) {
        MutableText t = Text.literal(label);
        t.setStyle(Style.EMPTY.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command)));
        return t;
    }

    private static String formatValue(Object v) {
        return v == null ? "<null>" : String.valueOf(v);
    }

    private record SelectedBlock(String dimension, int x, int y, int z) { }

    private static final class DirectPlaceState {
        boolean active;
        int cursor;
        BlockPos seed;
        String dimension;
        String failReason;
        BlockPos pendingTarget;
        String pendingBlockId;
        long pendingSinceMs;
        long lastPlaceAttemptMs;
        int placeAttempts;

        void reset() {
            active = false;
            cursor = 0;
            seed = null;
            dimension = null;
            failReason = "";
            pendingTarget = null;
            pendingBlockId = "";
            pendingSinceMs = 0L;
            lastPlaceAttemptMs = 0L;
            placeAttempts = 0;
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
            ScoreboardObjective sidebar = scoreboard.getObjectiveForSlot(ScoreboardDisplaySlot.SIDEBAR);
            if (sidebar == null) {
                return out;
            }
            out.add(sidebar.getDisplayName().getString());
            List<Object> scores = getScoreEntries(scoreboard, sidebar);
            scores.removeIf(score -> {
                String playerName = getScorePlayerName(score);
                return playerName == null || playerName.startsWith("#");
            });
            scores.sort((a, b) -> Integer.compare(getScoreValue(a), getScoreValue(b)));
            int from = Math.max(0, scores.size() - 15);
            for (int i = from; i < scores.size(); i++) {
                Object score = scores.get(i);
                String playerName = getScorePlayerName(score);
                if (playerName == null || playerName.trim().isEmpty()) {
                    continue;
                }
                out.add("[" + getScoreValue(score) + "] " + playerName);
            }
            return out;
        }

        private List<Object> getScoreEntries(Scoreboard scoreboard, ScoreboardObjective objective) {
            List<Object> out = new ArrayList<Object>();
            if (scoreboard == null || objective == null) {
                return out;
            }
            Object raw = invokeScoreMethod(scoreboard, "getAllPlayerScores", objective);
            if (!(raw instanceof Iterable<?>)) {
                raw = invokeScoreMethod(scoreboard, "getScoreboardEntries", objective);
            }
            if (!(raw instanceof Iterable<?>)) {
                raw = invokeScoreMethod(scoreboard, "getAllScores", objective);
            }
            if (raw instanceof Iterable<?>) {
                for (Object o : (Iterable<?>) raw) {
                    out.add(o);
                }
            }
            return out;
        }

        private int getScoreValue(Object score) {
            if (score == null) {
                return 0;
            }
            try {
                Object raw = invokeScoreMethod(score, "getScore");
                if (!(raw instanceof Number)) {
                    raw = invokeScoreMethod(score, "getScorePoints");
                }
                if (!(raw instanceof Number)) {
                    raw = invokeScoreMethod(score, "getValue");
                }
                if (raw instanceof Number) {
                    return ((Number) raw).intValue();
                }
            } catch (Throwable ignored) {
            }
            return 0;
        }

        private String getScorePlayerName(Object score) {
            if (score == null) {
                return null;
            }
            try {
                Object raw = invokeScoreMethod(score, "getPlayerName");
                if (raw == null) {
                    raw = invokeScoreMethod(score, "getOwner");
                }
                if (raw == null) {
                    raw = invokeScoreMethod(score, "getName");
                }
                return raw == null ? null : raw.toString();
            } catch (Throwable ignored) {
            }
            return null;
        }

        private Object invokeScoreMethod(Object target, String method, Object... args) {
            if (target == null || method == null) {
                return null;
            }
            try {
                Class<?>[] sig = new Class<?>[args == null ? 0 : args.length];
                for (int i = 0; i < sig.length; i++) {
                    sig[i] = args[i].getClass();
                }
                return target.getClass().getMethod(method, sig).invoke(target, args);
            } catch (Throwable ignored) {
            }
            return null;
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
                    DIRECT_PLACE_STATE.failReason = "no selected seed block; use /bc_select on light-blue-glass start";
                    return;
                }
                DIRECT_PLACE_STATE.seed = seed;
                DIRECT_PLACE_STATE.dimension = mc.world.getRegistryKey().getValue().toString();
                DIRECT_PLACE_STATE.cursor = 0;
                DIRECT_PLACE_STATE.active = true;
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
                    ? new PlaceRuntimeEntry(true, false, "minecraft:air", "", "", java.util.Collections.<com.rainbow_universe.bettercode.core.place.PlaceArgSpec>emptyList())
                    : op.kind() == PlaceOp.Kind.SKIP
                        ? new PlaceRuntimeEntry(false, true, "skip", "", "", java.util.Collections.<com.rainbow_universe.bettercode.core.place.PlaceArgSpec>emptyList())
                        : new PlaceRuntimeEntry(false, false, op.blockId(), op.name(), op.args(), java.util.Collections.<com.rainbow_universe.bettercode.core.place.PlaceArgSpec>emptyList());
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
                if (!DIRECT_PLACE_STATE.active || DIRECT_PLACE_STATE.seed == null) {
                    return PlaceExecResult.fail(0, 0, "DIRECT_RUNTIME_NOT_READY",
                        DIRECT_PLACE_STATE.failReason == null || DIRECT_PLACE_STATE.failReason.isEmpty()
                            ? "direct runtime not started; call /confirmload again"
                            : DIRECT_PLACE_STATE.failReason);
                }

                if (entry.isSkip() || entry.moveOnly()) {
                    clearPendingPlaceState(DIRECT_PLACE_STATE);
                    DIRECT_PLACE_STATE.cursor++;
                    System.out.println("[printer-debug] direct_skip_step cursor=" + DIRECT_PLACE_STATE.cursor);
                    return PlaceExecResult.ok(1);
                }

                // Core owns menu/args flow, but adapter must still place/re-place the block for the same entry.
                if (entry.forceRePlaceRequested() && DIRECT_PLACE_STATE.cursor > 0) {
                    DIRECT_PLACE_STATE.cursor--;
                }
                if (entry.forceRePlaceRequested()) {
                    clearPendingPlaceState(DIRECT_PLACE_STATE);
                    entry.setForceRePlaceRequested(false);
                }
                boolean menuPayload = hasMenuPayload(entry);

                String sourceBlockId = entry.blockId() == null ? "" : entry.blockId().trim();
                String blockId = normalizeBlockIdForRuntime(sourceBlockId);
                Identifier id = Identifier.tryParse(blockId);
                if (id == null) {
                    return PlaceExecResult.fail(0, 0, "INVALID_BLOCK_ID", "invalid block id: " + sourceBlockId);
                }
                Block block = Registries.BLOCK.get(id);
                if (block == null || block.asItem() == null || block.asItem() == ItemStack.EMPTY.getItem()) {
                    return PlaceExecResult.fail(0, 0, "INVALID_BLOCK_ID", "unknown block: " + sourceBlockId);
                }

                Item item = block.asItem();
                int slot = findHotbarSlot(mc, item);
                if (slot < 0) {
                    return PlaceExecResult.fail(0, 0, "MISSING_REQUIRED_ITEM", "required item not in hotbar: " + sourceBlockId);
                }
                if (mc.player.getInventory().selectedSlot != slot) {
                    mc.player.getInventory().selectedSlot = slot;
                }

                BlockPos entryPos = DIRECT_PLACE_STATE.seed.add(-2 * DIRECT_PLACE_STATE.cursor, 1, 0);
                BlockPos target = entryPos.down();
                String expectedBlockId = String.valueOf(Registries.BLOCK.getId(block));
                long now = System.currentTimeMillis();

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

        private static BlockPos resolveSeed(MinecraftClient mc) {
            if (mc == null || mc.player == null || mc.world == null) {
                return null;
            }
            String dim = mc.world.getRegistryKey().getValue().toString();
            List<BlockPosView> seedGlasses = new ArrayList<BlockPosView>();
            for (SelectedBlock s : SELECTED.values()) {
                if (s == null) {
                    continue;
                }
                if (!dim.equals(s.dimension())) {
                    continue;
                }
                SelectedRow normalized = SelectedRowNormalizer.normalizeToGlassAnchor(
                    new SelectedRow(s.dimension(), s.x(), s.y(), s.z()),
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
            BlueGlassSearch.Probe probe = new BlueGlassSearch.Probe() {
                @Override
                public boolean isBlueGlass(int x, int y, int z) {
                    return BetterCodeFabric121.FabricBridge.isBlueGlass(mc, x, y, z);
                }

                @Override
                public boolean isFree(int x, int y, int z) {
                    return BetterCodeFabric121.FabricBridge.isFreeGlass(mc, x, y, z);
                }
            };
            BlockPosView nearestAny = BlueGlassSearch.chooseNearestSeed(seedGlasses, probe,
                p -> mc.player.squaredDistanceTo(p.x() + 0.5, p.y() + 0.5, p.z() + 0.5));
            return nearestAny == null ? null : new BlockPos(nearestAny.x(), nearestAny.y(), nearestAny.z());
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
                ItemStack st = mc.player.getInventory().getStack(i);
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
                int lastPlacedCursor = Math.max(0, DIRECT_PLACE_STATE.cursor - 1);
                return DIRECT_PLACE_STATE.seed.add(-2 * lastPlacedCursor, 1, 0);
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
                return mc.world.getBlockState(pos).getBlock() == expected;
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
            if (CLIENT_DISPATCHER == null) {
                System.err.println("[printer-debug] executeClientCommand: dispatcher is null for cmd=" + raw);
                return false;
            }
            try {
                int result = CLIENT_DISPATCHER.execute(raw, source);
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
                source.sendFeedback(Text.literal(message));
                return;
            }
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.player != null) {
                mc.player.sendMessage(Text.literal(message), false);
            } else {
                System.out.println("[printer-debug] chat(no-player): " + message);
            }
        }

        @Override
        public void sendActionBar(String message) {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.player != null) {
                mc.player.sendMessage(Text.literal(message), true);
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
                ActionResult result = mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit);
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
                String worldId = String.valueOf(Registries.BLOCK.getId(mc.world.getBlockState(new BlockPos(x, y, z)).getBlock()));
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
                ItemStack st = mc.player.currentScreenHandler.getCursorStack();
                if (st == null || st.isEmpty()) {
                    return CursorState.empty();
                }
                String itemId = st.getItem() == null ? "" : String.valueOf(Registries.ITEM.getId(st.getItem()));
                String name = st.getName() == null ? "" : st.getName().getString();
                return new CursorState(false, itemId, name, readNbtString(st));
            } catch (Exception e) {
                return CursorState.empty();
            }
        }

        @Override
        public ContainerView getContainerSnapshot() {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (!(mc.currentScreen instanceof HandledScreen<?> hs) || mc.player == null) {
                return ContainerView.empty();
            }
            try {
                List<SlotView> out = new ArrayList<>();
                int windowId = hs.getScreenHandler().syncId;
                String title = hs.getTitle() == null ? "" : hs.getTitle().getString();
                for (Slot s : hs.getScreenHandler().slots) {
                    if (s == null) {
                        continue;
                    }
                    ItemStack st = s.getStack();
                    boolean empty = st == null || st.isEmpty();
                    String itemId = "";
                    String display = "";
                    String nbt = "";
                    if (!empty) {
                        itemId = st.getItem() == null ? "" : String.valueOf(Registries.ITEM.getId(st.getItem()));
                        display = st.getName() == null ? "" : st.getName().getString();
                        nbt = readNbtString(st);
                    }
                    boolean playerInv = s.inventory == mc.player.getInventory();
                    out.add(new SlotView(readIntField(s, "id", -1), readIntField(s, "index", -1), playerInv, empty, itemId, display, nbt));
                }
                return new ContainerView(windowId, title, hs.getScreenHandler().slots.size(), out);
            } catch (Exception e) {
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
            mc.player.getInventory().selectedSlot = slot;
            return true;
        }

        @Override
        public boolean injectCreativeSlot(int slot, String itemId, String nbt, String displayName) {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.player == null || mc.interactionManager == null || slot < 0 || slot >= 9) {
                return false;
            }
            Identifier id = Identifier.tryParse(itemId == null ? "" : itemId.trim());
            if (id == null) {
                return false;
            }
            Item item = Registries.ITEM.get(id);
            if (item == null || item == ItemStack.EMPTY.getItem()) {
                return false;
            }
            try {
                ItemStack st = new ItemStack(item);
                applyDisplayName(st, displayName);
                if (nbt != null && !nbt.trim().isEmpty()) {
                    applyNbt(st, nbt.trim());
                }
                mc.player.getInventory().setStack(slot, st);
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
                ActionResult result = mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit);
                if (result != null && result.isAccepted()) {
                    return ClickResult.accepted(AckState.PENDING);
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
            if (spoofLook) {
                aimAtBlock(mc, x, y, z);
            }
            ClickResult packetLike = sendUseItemOnBlock(x, y, z);
            boolean accepted = packetLike != null && packetLike.accepted();
            boolean interactAccepted = useBlockAt(x, y, z, purpose == null ? "legacy_click_interact" : purpose + "_interact");
            if (accepted || interactAccepted) {
                return ClickResult.accepted(AckState.PENDING);
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
            List<SelectedRow> out = new ArrayList<SelectedRow>();
            MinecraftClient mc = MinecraftClient.getInstance();
            for (SelectedBlock s : SELECTED.values()) {
                if (s == null) {
                    continue;
                }
                SelectedRow normalized = SelectedRowNormalizer.normalizeToGlassAnchor(
                    new SelectedRow(s.dimension(), s.x(), s.y(), s.z()),
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
            return executeClientCommand("tp " + x + " " + y + " " + z)
                || executeClientCommand("/tp " + x + " " + y + " " + z);
        }

        @Override
        public boolean isTpPathBusy() {
            return false;
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
                String blockId = String.valueOf(Registries.BLOCK.getId(mc.world.getBlockState(new BlockPos(x, y, z)).getBlock()));
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
                Object be = mc.world.getBlockEntity(new BlockPos(x, y, z));
                if (be == null) {
                    return null;
                }
                String[] out = new String[4];
                for (int i = 0; i < 4; i++) {
                    out[i] = readSignLineReflect(be, i);
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
                java.lang.reflect.Field f = target.getClass().getField(field);
                f.setAccessible(true);
                Object v = f.get(target);
                if (v instanceof Integer) {
                    return ((Integer) v).intValue();
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
                System.out.println("[printer-debug] block_id_compat mode=" + mode + " from=" + base + " to=" + normalized);
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

        private static String readSignLineReflect(Object be, int line) {
            return ReflectCompat.readSignLineReflect(be, line, FabricBridge::asString);
        }

        private static String asString(Object text) {
            if (text == null) {
                return "";
            }
            try {
                return String.valueOf(text.getClass().getMethod("getString").invoke(text));
            } catch (Exception ignore) {
            }
            return String.valueOf(text);
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

        private static void applyDisplayName(ItemStack st, String displayName) {
            if (st == null || displayName == null || displayName.trim().isEmpty()) {
                return;
            }
            try {
                Object text = Text.class.getMethod("literal", String.class).invoke(null, displayName.trim());
                for (java.lang.reflect.Method m : st.getClass().getMethods()) {
                    if ("setCustomName".equals(m.getName()) && m.getParameterCount() == 1) {
                        m.invoke(st, text);
                        return;
                    }
                }
            } catch (Exception ignore) {
            }
        }

        private static void applyNbt(ItemStack st, String raw) {
            if (st == null || raw == null || raw.isEmpty()) {
                return;
            }
            try {
                Object parsed = StringNbtReader.class.getMethod("parse", String.class).invoke(null, raw);
                for (java.lang.reflect.Method m : st.getClass().getMethods()) {
                    if (("setNbt".equals(m.getName()) || "setTag".equals(m.getName())) && m.getParameterCount() == 1) {
                        m.invoke(st, parsed);
                        return;
                    }
                }
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
                mc.player.setYaw(yaw);
                mc.player.setPitch(pitch);
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
}
