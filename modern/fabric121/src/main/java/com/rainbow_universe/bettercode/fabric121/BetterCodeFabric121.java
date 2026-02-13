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
import com.rainbow_universe.bettercode.core.place.PlaceRuntimeEntry;
import com.rainbow_universe.bettercode.core.settings.ModSettingsService;
import com.rainbow_universe.bettercode.core.settings.SettingDef;
import com.rainbow_universe.bettercode.core.settings.SettingType;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.block.Block;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardDisplaySlot;
import net.minecraft.scoreboard.ScoreboardObjective;
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

        ClientTickEvents.END_CLIENT_TICK.register(client ->
            runtime().handleClientTick(new FabricBridge(null), System.currentTimeMillis()));
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
        source.sendError(Text.literal("[" + result.errorCode() + "] " + result.message()));
        return 0;
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
        String dim = mc.world.getRegistryKey().getValue().toString();
        String key = dim + ":" + pos.toShortString();
        if (SELECTED.containsKey(key)) {
            SELECTED.remove(key);
            source.sendFeedback(Text.literal("Removed: " + key + " (selected=" + SELECTED.size() + ")"));
        } else {
            SELECTED.put(key, new SelectedBlock(dim, pos.getX(), pos.getY(), pos.getZ()));
            source.sendFeedback(Text.literal("Added: " + key + " (selected=" + SELECTED.size() + ")"));
        }
        return 1;
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

        void reset() {
            active = false;
            cursor = 0;
            seed = null;
            dimension = null;
            failReason = "";
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
                    DIRECT_PLACE_STATE.failReason = "no selected seed block; use /bc_select on blue-glass start";
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
                    DIRECT_PLACE_STATE.cursor++;
                    System.out.println("[printer-debug] direct_skip_step cursor=" + DIRECT_PLACE_STATE.cursor);
                    return PlaceExecResult.ok(1);
                }

                String rawName = entry.name() == null ? "" : entry.name().trim();
                String rawArgs = entry.argsRaw() == null ? "" : entry.argsRaw().trim();
                if (!rawName.isEmpty() || (!rawArgs.isEmpty() && !"no".equalsIgnoreCase(rawArgs))) {
                    return PlaceExecResult.fail(0, 0, "UNIMPLEMENTED_MENU_ARGS",
                        "name/args runtime not ported yet for direct mode: name='" + rawName + "' args='" + rawArgs + "'");
                }

                String blockId = entry.blockId() == null ? "" : entry.blockId().trim();
                Identifier id = Identifier.tryParse(blockId);
                if (id == null) {
                    return PlaceExecResult.fail(0, 0, "INVALID_BLOCK_ID", "invalid block id: " + blockId);
                }
                Block block = Registries.BLOCK.get(id);
                if (block == null || block.asItem() == null || block.asItem() == ItemStack.EMPTY.getItem()) {
                    return PlaceExecResult.fail(0, 0, "INVALID_BLOCK_ID", "unknown block: " + blockId);
                }

                Item item = block.asItem();
                int slot = findHotbarSlot(mc, item);
                if (slot < 0) {
                    return PlaceExecResult.fail(0, 0, "MISSING_REQUIRED_ITEM", "required item not in hotbar: " + blockId);
                }
                if (mc.player.getInventory().selectedSlot != slot) {
                    mc.player.getInventory().selectedSlot = slot;
                }

                BlockPos target = DIRECT_PLACE_STATE.seed.add(-2 * DIRECT_PLACE_STATE.cursor, 1, 0);
                BlockPos support = target.down();
                BlockHitResult hit = new BlockHitResult(
                    new Vec3d(support.getX() + 0.5, support.getY() + 1.0, support.getZ() + 0.5),
                    Direction.UP,
                    support,
                    false
                );
                ActionResult result = mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit);
                if (result == null || !result.isAccepted()) {
                    return PlaceExecResult.fail(0, 0, "PLACE_INTERACT_REJECTED",
                        "interactBlock rejected at target=" + target + " support=" + support + " result=" + String.valueOf(result));
                }
                DIRECT_PLACE_STATE.cursor++;
                System.out.println("[printer-debug] direct_step_ok block=" + blockId + " target=" + target + " cursor=" + DIRECT_PLACE_STATE.cursor);
                return PlaceExecResult.ok(1);
            }
        }

        private static BlockPos resolveSeed(MinecraftClient mc) {
            String dim = mc.world == null ? "" : mc.world.getRegistryKey().getValue().toString();
            for (SelectedBlock s : SELECTED.values()) {
                if (s == null) {
                    continue;
                }
                if (dim.equals(s.dimension())) {
                    return new BlockPos(s.x(), s.y(), s.z());
                }
            }
            for (SelectedBlock s : SELECTED.values()) {
                if (s != null) {
                    return new BlockPos(s.x(), s.y(), s.z());
                }
            }
            HitResult target = mc.crosshairTarget;
            if (target instanceof BlockHitResult bhr) {
                return bhr.getBlockPos();
            }
            return null;
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

    }
}
