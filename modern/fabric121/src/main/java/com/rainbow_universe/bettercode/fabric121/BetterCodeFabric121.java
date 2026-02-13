package com.rainbow_universe.bettercode.fabric121;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.rainbow_universe.bettercode.core.CoreLogger;
import com.rainbow_universe.bettercode.core.GameBridge;
import com.rainbow_universe.bettercode.core.RuntimeCore;
import com.rainbow_universe.bettercode.core.RuntimeResult;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.MinecraftClient;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardDisplaySlot;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.text.Text;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;

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
    private static final RuntimeCore RUNTIME = new RuntimeCore(new FabricCoreLogger());
    private static volatile CommandDispatcher<FabricClientCommandSource> CLIENT_DISPATCHER;

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
                    .then(ClientCommandManager.argument("postId", StringArgumentType.word())
                        .executes(ctx -> runMldsl(ctx.getSource(), StringArgumentType.getString(ctx, "postId"), null))
                        .then(ClientCommandManager.argument("config", StringArgumentType.word())
                            .executes(ctx -> runMldsl(
                                ctx.getSource(),
                                StringArgumentType.getString(ctx, "postId"),
                                StringArgumentType.getString(ctx, "config")
                            )))))
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
    }

    private static int loadModule(FabricClientCommandSource source, String postId, String file) {
        RuntimeResult result = RUNTIME.handleLoadModule(postId, file, null, new FabricBridge(source));
        if (result.ok()) {
            source.sendFeedback(Text.literal(result.message()));
            return 1;
        }
        source.sendError(Text.literal("[" + result.errorCode() + "] " + result.message()));
        return 0;
    }

    private static int runMldsl(FabricClientCommandSource source, String postId, String config) {
        RuntimeResult result = RUNTIME.handleRun(postId, config, new FabricBridge(source));
        if (result.ok()) {
            source.sendFeedback(Text.literal(result.message()));
            return 1;
        }
        source.sendError(Text.literal("[" + result.errorCode() + "] " + result.message()));
        return 0;
    }

    private static int confirmLoad(FabricClientCommandSource source) {
        RuntimeResult result = RUNTIME.handleConfirmLoad(new FabricBridge(source));
        if (result.ok()) {
            source.sendFeedback(Text.literal(result.message()));
            return 1;
        }
        source.sendError(Text.literal("[" + result.errorCode() + "] " + result.message()));
        return 0;
    }

    private static int publishModule(FabricClientCommandSource source) {
        RuntimeResult result = RUNTIME.handlePublish(new FabricBridge(source));
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

    private record SelectedBlock(String dimension, int x, int y, int z) { }

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
        public boolean executeClientCommand(String command) {
            String raw = command == null ? "" : command.trim();
            if (raw.isEmpty()) {
                return false;
            }
            if (CLIENT_DISPATCHER == null) {
                return false;
            }
            try {
                int result = CLIENT_DISPATCHER.execute(raw, source);
                return result > 0;
            } catch (Exception e) {
                return false;
            }
        }

        @Override
        public void sendChat(String message) {
            source.sendFeedback(Text.literal(message));
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
