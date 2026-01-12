package com.example.examplemod;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.netty.buffer.Unpooled;
import net.minecraft.block.Block;
import net.minecraft.block.BlockPistonBase;
import net.minecraft.block.BlockShulkerBox;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.GuiDisconnected;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.inventory.GuiChest;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.client.gui.inventory.GuiShulkerBox;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.CommandBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.inventory.ClickType;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.InventoryBasic;
import net.minecraft.inventory.Slot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.EnumDyeColor;
import net.minecraft.scoreboard.Score;
import net.minecraft.scoreboard.ScoreObjective;
import net.minecraft.scoreboard.ScorePlayerTeam;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.network.PacketBuffer;
import net.minecraft.network.play.client.CPacketCreativeInventoryAction;
import net.minecraft.network.play.client.CPacketCustomPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.tileentity.TileEntityShulkerBox;
import net.minecraft.tileentity.TileEntitySign;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.GameType;
import net.minecraft.world.World;
import net.minecraftforge.client.ClientCommandHandler;
import net.minecraftforge.client.event.ClientChatEvent;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.fml.client.event.ConfigChangedEvent;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import net.minecraftforge.fml.common.event.FMLServerStoppingEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import org.apache.logging.log4j.Logger;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.lang.reflect.Method;
import java.net.BindException;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.TimeUnit;

@Mod(modid = ExampleMod.MODID, name = ExampleMod.NAME, version = ExampleMod.VERSION,
    guiFactory = "com.example.examplemod.ModGuiFactory")
public class ExampleMod
{
    public static final String MODID = "bettercode";
    public static final String NAME = "Creative+ BetterCode";
    public static final String VERSION = "1.0.0";
    private static final int BASE_PORT = 8765;
    private static final int MAX_PORT = 8790;
    private static final long REGISTRY_TTL_MS = 15000L;
    private static final long REGISTRY_HEARTBEAT_MS = 5000L;
    private static final long BAR_DEFAULT_MS = 3000L;
    private static final long CLIENT_CHAT_COOLDOWN_MS = 700L;
    private static final long HOTBAR_DOUBLE_TAP_MS = 250L;
    private static final long MENU_CACHE_ARM_MS = 5000L;
    private static final String CODE_MENU_TITLE = "Code Menu";
    private static final int INPUT_MODE_TEXT = 0;
    private static final int INPUT_MODE_NUMBER = 1;
    private static final int INPUT_MODE_VARIABLE = 2;
    private static final int INPUT_MODE_ARRAY = 3;
    private static final int INPUT_MODE_LOCATION = 4;
    private static final int INPUT_CONTEXT_SLOT = 0;
    private static final int INPUT_CONTEXT_GIVE = 1;
    private static final int ENTRY_RECENT_LIMIT = 10;
    private static final int ENTRY_FREQUENT_MIN = 3;
    private static final String ARRAY_MARK = "\u2398";
    private static final int MENU_CACHE_MAX = 48;
    private static final int ENTRY_COUNT_MAX = 300;
    private static final long EDITOR_MODE_GRACE_MS = 30000L;
    private static final int CHEST_CACHE_MAX = 256;
    private static final int CHEST_SNAPSHOT_TICK_INTERVAL = 5;
    private static final double CHEST_PREVIEW_RANGE = 15.0;
    private static final String EDIT_TEST_MARKER = "edit_test_ok";
    private static final String HOLO_LABEL = "DIRT";
    private static final String CHEST_HOLO_LABEL = "Chest";
    private static final int CHEST_HOLO_TEX_SIZE = 1024;
    private static final int CHEST_HOLO_TEXT_WIDTH = 40;
    private static final float CHEST_HOLO_SCALE = 0.0118F;
    private static final String[] ENTITY_BUTTON_LABELS = new String[]
    {
        "selected", "player", "default", "entity", "damager",
        "victim", "selection", "random", "shooter"
    };

    private static Logger logger;
    private static Configuration config;
    private static boolean enableSecondHotbar = true;
    private static volatile String lastChatMessage = "";
    private static volatile String lastChatPlayer = "";
    private static volatile long lastChatTimeMs = 0L;
    private static volatile long lastApiChatSentMs = 0L;
    private static volatile String cachedServerPlayersJson = "{\"count\":0,\"players\":[]}";
    private static volatile String cachedClientTabJson = "{\"count\":0,\"players\":[]}";
    private static volatile String cachedServerCoordsJson = "{\"count\":0,\"players\":[]}";
    private static volatile String cachedClientCoordsJson = "{\"count\":0,\"players\":[]}";
    private HttpServer httpServer;
    private HttpServer registryServer;
    private MinecraftServer mcServer;
    private int apiPort = -1;
    private boolean registryEnabled = false;
    private final Map<String, RegistryEntry> registryEntries = new ConcurrentHashMap<>();
    private ScheduledExecutorService registryHeartbeat;
    private static volatile String actionBarText = "";
    private static volatile String actionBar2Text = "";
    private static volatile long actionBarExpireMs = 0L;
    private static volatile long actionBar2ExpireMs = 0L;
    private final ItemStack[][] hotbarSets = new ItemStack[2][9];
    private final long[] lastHotbarTapMs = new long[9];
    private final boolean[] hotbarKeyDown = new boolean[9];
    private int activeHotbarSet = 0;
    private World lastWorldRef = null;
    private boolean inputActive = false;
    private boolean inputSaveVariable = false;
    private GuiTextField inputField;
    private boolean inputRepeatEnabled = false;
    private int inputTargetWindowId = -1;
    private int inputTargetSlotNumber = -1;
    private String inputTitle = "";
    private int inputMode = INPUT_MODE_TEXT;
    private int inputContext = INPUT_CONTEXT_SLOT;
    private ItemStack inputSlotTemplate = ItemStack.EMPTY;
    private ItemStack inputGiveTemplate = ItemStack.EMPTY;
    private final Map<String, List<InputEntry>> recentEntriesById = new HashMap<>();
    private final Map<String, Map<String, Integer>> entryCountsById = new HashMap<>();
    private final Set<String> savedVariableNames = new HashSet<>();
    private boolean typePickerActive = false;
    private int typePickerWindowId = -1;
    private int typePickerSlotNumber = -1;
    private int quickApplyWindowId = -1;
    private int quickApplySlotNumber = -1;
    private Method getSlotUnderMouseMethod;
    private final Map<String, CachedMenu> menuCache = new LinkedHashMap<String, CachedMenu>(16, 0.75f, true)
    {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, CachedMenu> eldest)
        {
            return size() > MENU_CACHE_MAX;
        }
    };
    private CachedMenu customMenuCache = null;
    private final List<ClickAction> queuedClicks = new ArrayList<>();
    private String pendingCacheKey = null;
    private long pendingCacheMs = 0L;
    private boolean fakeMenuActive = false;
    private String fakeMenuKey = null;
    private boolean awaitingCacheSnapshot = false;
    private int cacheOpenTicks = 0;
    private boolean editorModeActive = false;
    private boolean editorModeWasActive = false;
    private boolean captureCustomMenuArmed = false;
    private boolean captureCustomMenuNow = false;
    private boolean codeMenuActive = false;
    private IInventory codeMenuInventory = null;
    private KeyBinding keyOpenCodeMenu;
    private KeyBinding keyOpenCodeMenuAlt;
    private KeyBinding keyTpForward;
    private boolean codeMenuKeyDown = false;
    private boolean tpForwardKeyDown = false;
    private int tpScrollSteps = 0;
    private int tpScrollQueue = 0;
    private int tpScrollDir = 0;
    private long tpScrollNextMs = 0L;
    private final Deque<double[]> tpPathQueue = new ArrayDeque<>();
    private long tpPathNextMs = 0L;
    private File codeBlueGlassFile = null;
    private boolean codeBlueGlassDirty = false;
    private long lastCodeBlueGlassSaveMs = 0L;
    private final AtomicBoolean codeBlueGlassSaveQueued = new AtomicBoolean(false);
    private final Map<String, BlockPos> codeBlueGlassById = new HashMap<>();
    private String signSearchQuery = null;
    private int signSearchDim = 0;
    private final List<BlockPos> signSearchMatches = new ArrayList<>();
    private boolean debugUi = false;
    private long lastDevCommandMs = 0L;
    private boolean pendingDev = false;
    private long pendingDevUntilMs = 0L;
    private String cachePathRoot = null;
    private final List<CopiedSlot> copiedSlots = new ArrayList<>();
    private final Map<String, ChestCache> chestCaches = new LinkedHashMap<String, ChestCache>(32, 0.75f, true)
    {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, ChestCache> eldest)
        {
            return size() > CHEST_CACHE_MAX;
        }
    };
    private final Map<String, ChestCache> chestIdCaches = new LinkedHashMap<String, ChestCache>(64, 0.75f, true)
    {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, ChestCache> eldest)
        {
            return size() > CHEST_CACHE_MAX;
        }
    };
    private final Map<String, ChestPreviewFbo> chestPreviewFbos = new LinkedHashMap<String, ChestPreviewFbo>(32, 0.75f, true)
    {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, ChestPreviewFbo> eldest)
        {
            return size() > CHEST_CACHE_MAX;
        }
    };
    private File entriesFile = null;
    private File chestCacheFile = null;
    private File noteFile = null;
    private File menuCacheFile = null;
    private File shulkerHoloFile = null;
    private boolean entriesDirty = false;
    private long lastEntriesSaveMs = 0L;
    private String noteText = "";
    private BlockPos lastClickedPos = null;
    private int lastClickedDim = 0;
    private long lastClickedMs = 0L;
    private boolean lastClickedChest = false;
    private String lastClickedLabel = null;
    private boolean lastClickedIsSign = false;

    private BlockPos lastGlassPos = null;
    private int lastGlassDim = 0;
    private boolean pendingChestSnapshot = false;
    private long pendingChestUntilMs = 0L;
    private String lastSnapshotInfo = "";
    private final Map<String, Boolean> chestFaceSouth = new HashMap<>();
    private final Map<String, Double> chestYOffset = new HashMap<>();
    private boolean allowChestSnapshot = false;
    private long allowChestUntilMs = 0L;
    private long lastChestSnapshotMs = 0L;
    private long lastChestSnapshotTick = -1L;
    private boolean lastEditorModeActive = false;
    private boolean chestIdDirty = false;
    private long lastChestIdSaveMs = 0L;
    private boolean menuCacheDirty = false;
    private long lastMenuCacheSaveMs = 0L;
    private boolean shulkerHoloDirty = false;
    private long lastShulkerHoloSaveMs = 0L;
    private long lastConfigCheckMs = 0L;
    private long lastConfigStamp = 0L;
    private ExecutorService ioExecutor;
    private final AtomicBoolean entriesSaveQueued = new AtomicBoolean(false);
    private final AtomicBoolean menuSaveQueued = new AtomicBoolean(false);
    private final AtomicBoolean chestSaveQueued = new AtomicBoolean(false);
    private final AtomicBoolean shulkerHoloSaveQueued = new AtomicBoolean(false);
    private boolean pendingShulkerEdit = false;
    private long pendingShulkerUntilMs = 0L;
    private BlockPos pendingShulkerPos = null;
    private int pendingShulkerDim = 0;
    private int pendingShulkerColor = 0xFFFFFF;
    private boolean shulkerEditActive = false;
    private int shulkerEditWindowId = -1;
    private int shulkerEditSlotNumber = -1;
    private BlockPos shulkerEditPos = null;
    private int shulkerEditDim = 0;
    private int shulkerEditColor = 0xFFFFFF;
    private float chestHoloScale = CHEST_HOLO_SCALE;
    private int chestHoloTextWidth = CHEST_HOLO_TEXT_WIDTH;
    private int chestHoloTexSize = CHEST_HOLO_TEX_SIZE;
    private String lastHoloInfo = "";
    private boolean chestHoloForceRerender = true;
    private boolean chestHoloUseTestPipeline = false;
    private boolean chestHoloSmoothFont = true;
    private boolean chestHoloUseGuiRender = true;
    private static int chestHoloTextColor = 0xFFFFFF;
    private final Map<String, TestChestHolo> chestTestHolos = new HashMap<>();
    private final Map<String, ShulkerHolo> shulkerHolos = new HashMap<>();
    private float shulkerHoloScale = 0.06F;
    private double shulkerHoloYOffset = 1.15;
    private double shulkerHoloZOffset = 0.0;
    private boolean shulkerHoloBillboard = true;
    private boolean shulkerHoloDepth = false;
    private boolean shulkerHoloCull = false;
    private float lastTestScale = CHEST_HOLO_SCALE;
    private int lastTestTextWidth = CHEST_HOLO_TEXT_WIDTH;
    private int lastTestTexSize = CHEST_HOLO_TEX_SIZE;
    private java.lang.reflect.Field lowerChestField;
    private java.lang.reflect.Field guiLeftField;
    private java.lang.reflect.Field guiTopField;
    private java.lang.reflect.Field xSizeField;
    private java.lang.reflect.Field ySizeField;
    private java.lang.reflect.Field fontTextureField;
    private boolean testHoloActive = false;
    private BlockPos testHoloPos = null;
    private final List<TestChestHolo> testChestHolos = new ArrayList<>();

    @EventHandler
    public void preInit(FMLPreInitializationEvent event)
    {
        logger = event.getModLog();
        MinecraftForge.EVENT_BUS.register(this);
        loadConfig(event);
        initEntriesFile(event);
        initIoExecutor();
    }

    @EventHandler
    public void init(FMLInitializationEvent event)
    {
        // some example code
        logger.info("DIRT BLOCK >> {}", Blocks.DIRT.getRegistryName());
        initHotbarStorage();
        registerKeybinds();
        startHttpServer();
    }

    @EventHandler
    public void postInit(FMLPostInitializationEvent event)
    {
        registerClientCommands();
    }

    @EventHandler
    public void onServerStarting(FMLServerStartingEvent event)
    {
        mcServer = event.getServer();
    }

    @EventHandler
    public void onServerStopping(FMLServerStoppingEvent event)
    {
        mcServer = null;
    }

    @SubscribeEvent
    public void onServerChat(ServerChatEvent event)
    {
        lastChatPlayer = event.getPlayer().getName();
        lastChatMessage = event.getMessage();
        lastChatTimeMs = System.currentTimeMillis();
    }

    @SubscribeEvent
    public void onConfigChanged(ConfigChangedEvent.OnConfigChangedEvent event)
    {
        if (event != null && MODID.equals(event.getModID()))
        {
            syncConfig(false);
            setActionBar(true, "&eConfig: hotbar=" + enableSecondHotbar + " holo=#"
                + String.format(Locale.ROOT, "%06X", chestHoloTextColor), 3000L);
            if (!enableSecondHotbar)
            {
                Minecraft mc = Minecraft.getMinecraft();
                if (mc != null)
                {
                    activeHotbarSet = 0;
                    if (mc.player != null)
                    {
                        loadHotbar(mc, activeHotbarSet);
                    }
                }
                for (int i = 0; i < hotbarKeyDown.length; i++)
                {
                    hotbarKeyDown[i] = false;
                    lastHotbarTapMs[i] = 0L;
                }
            }
        }
    }

    @SubscribeEvent
    public void onClientChat(ClientChatReceivedEvent event)
    {
        String msg = event.getMessage().getUnformattedText();
        lastChatMessage = msg;
        lastChatTimeMs = System.currentTimeMillis();
        lastChatPlayer = parseChatPlayer(msg);
    }

    @SubscribeEvent
    public void onClientChatSend(ClientChatEvent event)
    {
        if (event == null || event.getMessage() == null)
        {
            return;
        }
        String message = event.getMessage().trim();
        if (!message.startsWith("/"))
        {
            return;
        }
        String lower = message.toLowerCase(Locale.ROOT);
        String cmd = lower.split("\\s+")[0];
        if ("/dev".equals(cmd))
        {
            editorModeActive = true;
            editorModeWasActive = true;
            pendingDev = true;
            lastDevCommandMs = System.currentTimeMillis();
            pendingDevUntilMs = lastDevCommandMs + EDITOR_MODE_GRACE_MS;
            setActionBar(true, "&b\u0412\u044b \u0432\u043e\u0448\u043b\u0438 \u0432 \u043a\u043e\u0434", BAR_DEFAULT_MS);
            return;
        }
        if (isExitCodeCommand(cmd))
        {
            editorModeActive = false;
            editorModeWasActive = false;
        }
    }

    private void startHttpServer()
    {
        if (httpServer != null)
        {
            return;
        }

        try
        {
            for (int port = BASE_PORT; port <= MAX_PORT; port++)
            {
                try
                {
                    httpServer = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
                    apiPort = port;
                    break;
                }
                catch (BindException e)
                {
                    // Port in use, try the next one.
                }
            }

            if (httpServer == null)
            {
                logger.error("No available port for API server in range {}-{}", BASE_PORT, MAX_PORT);
                return;
            }

            httpServer.createContext("/player", this::handlePlayers);
            httpServer.createContext("/player/self", this::handleSelfPlayer);
            httpServer.createContext("/players/tab", this::handleTabPlayers);
            httpServer.createContext("/players/coords", this::handlePlayerCoords);
            httpServer.createContext("/chat/last", this::handleLastChat);
            httpServer.createContext("/chat/send", this::handleChatSend);
            httpServer.createContext("/command", this::handleCommand);
            httpServer.createContext("/block", this::handleBlock);
            httpServer.createContext("/book/write", this::handleBookWrite);
            httpServer.createContext("/bar", this::handleActionBar);
            httpServer.createContext("/bar2", this::handleActionBar2);

            if (apiPort == BASE_PORT)
            {
                registryServer = httpServer;
                registryEnabled = true;
                registerRegistryEndpoints(registryServer);
            }

            httpServer.setExecutor(Executors.newCachedThreadPool());
            httpServer.start();

            if (apiPort != BASE_PORT)
            {
                startRegistryServer();
            }

            startRegistryHeartbeat();
            logger.info("Local API started on http://127.0.0.1:{} (registry:{})", apiPort,
                registryEnabled ? BASE_PORT : "none");
        }
        catch (IOException e)
        {
            logger.error("Failed to start local API", e);
            httpServer = null;
        }
    }

    private void stopHttpServer()
    {
        if (httpServer != null)
        {
            httpServer.stop(0);
            httpServer = null;
            logger.info("Local API stopped");
        }
        if (registryServer != null && registryServer != httpServer)
        {
            registryServer.stop(0);
            registryServer = null;
        }
        if (registryHeartbeat != null)
        {
            registryHeartbeat.shutdownNow();
            registryHeartbeat = null;
        }
    }

    private void handlePlayers(HttpExchange exchange) throws IOException
    {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod()))
        {
            sendJson(exchange, 405, "{\"error\":\"method_not_allowed\"}");
            return;
        }

        String payload = mcServer != null ? cachedServerPlayersJson : cachedClientTabJson;
        sendJson(exchange, 200, payload);
    }

    private void handleSelfPlayer(HttpExchange exchange) throws IOException
    {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod()))
        {
            sendJson(exchange, 405, "{\"error\":\"method_not_allowed\"}");
            return;
        }

        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.player == null)
        {
            if (mcServer != null && mcServer.getPlayerList() != null && !mcServer.getPlayerList().getPlayers().isEmpty())
            {
                EntityPlayerMP player = mcServer.getPlayerList().getPlayers().get(0);
                sendJson(exchange, 200, buildSelfJson(player, "server"));
            }
            else
            {
                sendJson(exchange, 503, "{\"ok\":false,\"error\":\"no_player\"}");
            }
        }
        else
        {
            sendJson(exchange, 200, buildSelfJson(mc.player, "client"));
        }
    }

    private void handleTabPlayers(HttpExchange exchange) throws IOException
    {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod()))
        {
            sendJson(exchange, 405, "{\"error\":\"method_not_allowed\"}");
            return;
        }

        sendJson(exchange, 200, cachedClientTabJson);
    }

    private void handlePlayerCoords(HttpExchange exchange) throws IOException
    {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod()))
        {
            sendJson(exchange, 405, "{\"error\":\"method_not_allowed\"}");
            return;
        }

        String payload = mcServer != null ? cachedServerCoordsJson : cachedClientCoordsJson;
        sendJson(exchange, 200, payload);
    }

    private void handleLastChat(HttpExchange exchange) throws IOException
    {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod()))
        {
            sendJson(exchange, 405, "{\"error\":\"method_not_allowed\"}");
            return;
        }

        String payload = "{\"player\":\"" + escapeJson(lastChatPlayer) + "\",\"message\":\""
            + escapeJson(lastChatMessage) + "\",\"timeMs\":" + lastChatTimeMs + "}";
        sendJson(exchange, 200, payload);
    }

    private void handleCommand(HttpExchange exchange) throws IOException
    {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod()))
        {
            sendJson(exchange, 405, "{\"error\":\"method_not_allowed\"}");
            return;
        }

        String body = readBody(exchange.getRequestBody()).trim();
        String query = exchange.getRequestURI().getRawQuery();
        String cmd = body.isEmpty() ? getQueryParam(query, "cmd") : body;
        String asPlayer = getQueryParam(query, "player");

        if (cmd == null || cmd.trim().isEmpty())
        {
            sendJson(exchange, 400, "{\"error\":\"missing_cmd\"}");
            return;
        }

        String cmdFinal = stripSlash(cmd.trim());
        if (mcServer == null)
        {
            String blockReason = getClientBlockReason();
            if (blockReason != null)
            {
                sendJson(exchange, 429, "{\"ok\":false,\"error\":\"" + blockReason + "\"}");
                return;
            }
        }
        CommandResult result = executeCommand(cmdFinal, asPlayer);

        if (result.executed)
        {
            sendJson(exchange, 200, "{\"ok\":true,\"mode\":\"" + result.mode + "\"}");
        }
        else
        {
            sendJson(exchange, 503, "{\"ok\":false,\"error\":\"no_world\"}");
        }
    }

    private void handleBlock(HttpExchange exchange) throws IOException
    {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod()))
        {
            sendJson(exchange, 405, "{\"error\":\"method_not_allowed\"}");
            return;
        }

        String query = exchange.getRequestURI().getRawQuery();
        Integer x = parseIntParam(query, "x");
        Integer y = parseIntParam(query, "y");
        Integer z = parseIntParam(query, "z");
        Integer dim = parseIntParam(query, "dim");

        if (x == null || y == null || z == null)
        {
            sendJson(exchange, 400, "{\"error\":\"missing_coords\"}");
            return;
        }

        World world = getWorld(dim);
        if (world == null)
        {
            sendJson(exchange, 503, "{\"ok\":false,\"error\":\"no_world\"}");
            return;
        }

        BlockPos pos = new BlockPos(x, y, z);
        if (!world.isBlockLoaded(pos))
        {
            sendJson(exchange, 200, "{\"ok\":false,\"error\":\"unloaded\"}");
            return;
        }

        IBlockState state = world.getBlockState(pos);
        ResourceLocation id = state.getBlock().getRegistryName();
        int numericId = Block.getIdFromBlock(state.getBlock());
        int meta = state.getBlock().getMetaFromState(state);
        String payload = "{\"ok\":true,\"id\":\"" + escapeJson(id == null ? "" : id.toString())
            + "\",\"idNum\":" + numericId + ",\"meta\":" + meta + "}";
        sendJson(exchange, 200, payload);
    }

    private void handleChatSend(HttpExchange exchange) throws IOException
    {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod()))
        {
            sendJson(exchange, 405, "{\"error\":\"method_not_allowed\"}");
            return;
        }

        String body = readBody(exchange.getRequestBody()).trim();
        String query = exchange.getRequestURI().getRawQuery();
        Map<String, String> params = parseParams(query, body);
        String text = params.get("text");
        if (text == null || text.trim().isEmpty())
        {
            sendJson(exchange, 400, "{\"error\":\"missing_text\"}");
            return;
        }
        text = text.trim();

        if (text.startsWith("/"))
        {
            if (mcServer == null)
            {
                String blockReason = getClientBlockReason();
                if (blockReason != null)
                {
                    sendJson(exchange, 429, "{\"ok\":false,\"error\":\"" + blockReason + "\"}");
                    return;
                }
            }
            CommandResult result = executeCommand(stripSlash(text), params.get("player"));
            if (result.executed)
            {
                sendJson(exchange, 200, "{\"ok\":true,\"mode\":\"" + result.mode + "\"}");
            }
            else
            {
                sendJson(exchange, 503, "{\"ok\":false,\"error\":\"no_world\"}");
            }
            return;
        }

        if (mcServer == null)
        {
            String blockReason = getClientBlockReason();
            if (blockReason != null)
            {
                sendJson(exchange, 429, "{\"ok\":false,\"error\":\"" + blockReason + "\"}");
                return;
            }
        }

        if (sendChatMessage(text))
        {
            sendJson(exchange, 200, "{\"ok\":true,\"mode\":\"client\"}");
        }
        else if (broadcastServerMessage(text))
        {
            sendJson(exchange, 200, "{\"ok\":true,\"mode\":\"server_broadcast\"}");
        }
        else
        {
            sendJson(exchange, 503, "{\"ok\":false,\"error\":\"no_player\"}");
        }
    }

    private void handleBookWrite(HttpExchange exchange) throws IOException
    {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod()))
        {
            sendJson(exchange, 405, "{\"error\":\"method_not_allowed\"}");
            return;
        }

        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.player == null)
        {
            sendJson(exchange, 503, "{\"ok\":false,\"error\":\"no_player\"}");
            return;
        }

        String body = readBody(exchange.getRequestBody()).trim();
        String query = exchange.getRequestURI().getRawQuery();
        Map<String, String> params = parseParams(query, body);

        String text = params.get("text");
        if (text == null) text = "";
        int pages = parseIntParam(params.get("pages"), 1);
        if (pages < 1) pages = 1;
        if (pages > 200) pages = 200;
        boolean sign = "1".equals(params.get("sign"));
        String title = params.get("title");
        if (title == null || title.isEmpty())
        {
            title = "Book";
        }

        ItemStack stack = mc.player.getHeldItemMainhand();
        if (stack.isEmpty() || stack.getItem() != Items.WRITABLE_BOOK)
        {
            sendJson(exchange, 400, "{\"ok\":false,\"error\":\"hold_writable_book\"}");
            return;
        }

        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null) tag = new NBTTagCompound();

        NBTTagList list = new NBTTagList();
        List<String> pageTexts = extractPageTexts(params);
        if (!pageTexts.isEmpty())
        {
            int max = Math.min(200, pageTexts.size());
            for (int i = 0; i < max; i++)
            {
                list.appendTag(new NBTTagString(pageTexts.get(i)));
            }
        }
        else
        {
            for (int i = 0; i < pages; i++)
            {
                list.appendTag(new NBTTagString(text));
            }
        }
        tag.setTag("pages", list);

        if (sign)
        {
            tag.setString("author", mc.player.getName());
            tag.setString("title", title);
        }

        stack.setTagCompound(tag);

        PacketBuffer buffer = new PacketBuffer(Unpooled.buffer());
        buffer.writeItemStack(stack);
        String channel = sign ? "MC|BSign" : "MC|BEdit";
        mc.player.connection.sendPacket(new CPacketCustomPayload(channel, buffer));

        sendJson(exchange, 200, "{\"ok\":true}");
    }

    private void handleActionBar(HttpExchange exchange) throws IOException
    {
        handleActionBarCommon(exchange, true);
    }

    private void handleActionBar2(HttpExchange exchange) throws IOException
    {
        handleActionBarCommon(exchange, false);
    }

    private void handleActionBarCommon(HttpExchange exchange, boolean primary) throws IOException
    {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod()))
        {
            sendJson(exchange, 405, "{\"error\":\"method_not_allowed\"}");
            return;
        }

        String body = readBody(exchange.getRequestBody()).trim();
        String query = exchange.getRequestURI().getRawQuery();
        Map<String, String> params = parseParams(query, body);
        String text = params.get("text");
        if (text == null || text.trim().isEmpty())
        {
            sendJson(exchange, 400, "{\"error\":\"missing_text\"}");
            return;
        }
        long duration = parseLongParam(params.get("time"), BAR_DEFAULT_MS);
        setActionBar(primary, text, duration);
        sendJson(exchange, 200, "{\"ok\":true}");
    }

    private static void sendJson(HttpExchange exchange, int status, String payload) throws IOException
    {
        byte[] data = payload.getBytes(StandardCharsets.UTF_8);
        Headers headers = exchange.getResponseHeaders();
        headers.set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, data.length);
        try (OutputStream os = exchange.getResponseBody())
        {
            os.write(data);
        }
    }

    private static String readBody(InputStream is) throws IOException
    {
        byte[] buffer = new byte[4096];
        int read;
        StringBuilder sb = new StringBuilder();
        while ((read = is.read(buffer)) != -1)
        {
            sb.append(new String(buffer, 0, read, StandardCharsets.UTF_8));
        }
        return sb.toString();
    }

    private static String getQueryParam(String query, String key)
    {
        if (query == null || query.isEmpty())
        {
            return null;
        }

        String[] parts = query.split("&");
        for (String part : parts)
        {
            int idx = part.indexOf('=');
            if (idx <= 0) continue;
            String k = decodeUrl(part.substring(0, idx));
            if (key.equals(k))
            {
                return decodeUrl(part.substring(idx + 1));
            }
        }
        return null;
    }

    private static Map<String, String> parseParams(String query, String body)
    {
        Map<String, String> result = new HashMap<>();
        if (query != null && !query.isEmpty())
        {
            result.putAll(parseQueryString(query));
        }
        if (body != null && !body.isEmpty())
        {
            if (body.contains("="))
            {
                result.putAll(parseQueryString(body));
            }
            else
            {
                result.put("text", body);
            }
        }
        return result;
    }

    private static Map<String, String> parseQueryString(String query)
    {
        Map<String, String> out = new HashMap<>();
        String[] parts = query.split("&");
        for (String part : parts)
        {
            int idx = part.indexOf('=');
            if (idx <= 0) continue;
            String k = decodeUrl(part.substring(0, idx));
            String v = decodeUrl(part.substring(idx + 1));
            out.put(k, v);
        }
        return out;
    }

    private static Integer parseIntParam(String query, String key)
    {
        String value = getQueryParam(query, key);
        if (value == null || value.isEmpty())
        {
            return null;
        }
        try
        {
            return Integer.parseInt(value.trim());
        }
        catch (NumberFormatException e)
        {
            return null;
        }
    }

    private static int parseIntParam(String value, int fallback)
    {
        if (value == null) return fallback;
        try
        {
            return Integer.parseInt(value.trim());
        }
        catch (NumberFormatException e)
        {
            return fallback;
        }
    }

    private static long parseLongParam(String value, long fallback)
    {
        if (value == null) return fallback;
        try
        {
            return Long.parseLong(value.trim());
        }
        catch (NumberFormatException e)
        {
            return fallback;
        }
    }

    private static float parseFloat(String value, float fallback)
    {
        if (value == null) return fallback;
        try
        {
            return Float.parseFloat(value.trim());
        }
        catch (NumberFormatException e)
        {
            return fallback;
        }
    }

    private static double parseDouble(String value, double fallback)
    {
        if (value == null) return fallback;
        try
        {
            return Double.parseDouble(value.trim());
        }
        catch (NumberFormatException e)
        {
            return fallback;
        }
    }

    private World getWorld(Integer dim)
    {
        if (mcServer != null)
        {
            int d = dim == null ? 0 : dim;
            return mcServer.getWorld(d);
        }
        Minecraft mc = Minecraft.getMinecraft();
        return mc == null ? null : mc.world;
    }

    private static String parseChatPlayer(String msg)
    {
        if (msg == null) return "";
        if (msg.startsWith("<"))
        {
            int end = msg.indexOf('>');
            if (end > 1)
            {
                return msg.substring(1, end).trim();
            }
        }
        return "";
    }

    private static String stripSlash(String cmd)
    {
        if (cmd.startsWith("/"))
        {
            return cmd.substring(1);
        }
        return cmd;
    }

    private static String decodeUrl(String s)
    {
        try
        {
            return URLDecoder.decode(s, "UTF-8");
        }
        catch (Exception e)
        {
            return s;
        }
    }

    private static String escapeJson(String s)
    {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t");
    }

    private static boolean isExitCodeCommand(String cmd)
    {
        return "/build".equals(cmd)
            || "/play".equals(cmd)
            || "/s".equals(cmd)
            || "/lobby".equals(cmd)
            || "/hub".equals(cmd)
            || "/\u0437\u0434\u0444\u043d".equals(cmd)
            || "/\u0438\u0433\u0448\u0434\u0432".equals(cmd);
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END || mcServer == null || mcServer.getPlayerList() == null)
        {
            return;
        }

        List<EntityPlayerMP> players = mcServer.getPlayerList().getPlayers();
        cachedServerPlayersJson = buildPlayerListJson(players);
        cachedServerCoordsJson = buildPlayerCoordsJson(players);
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END)
        {
            return;
        }

        Minecraft mc = Minecraft.getMinecraft();
        if (mc != null && mc.world != lastWorldRef)
        {
            lastWorldRef = mc.world;
            if (mc.world != null)
            {
                initHotbarsForWorld(mc);
            }
        }
        long now = System.currentTimeMillis();
        if (allowChestSnapshot && now > allowChestUntilMs)
        {
            clearChestClickState();
        }
        long worldTick = (mc != null && mc.world != null) ? mc.world.getTotalWorldTime() : -1L;
        if (mc != null && mc.currentScreen instanceof GuiChest)
        {
            if (lastClickedChest && allowChestSnapshot)
            {
                lastClickedMs = now;
                if (!pendingChestSnapshot)
                {
                    pendingChestSnapshot = true;
                    pendingChestUntilMs = lastClickedMs + 5000L;
                }
                if (worldTick >= 0 && worldTick != lastChestSnapshotTick
                    && (worldTick % CHEST_SNAPSHOT_TICK_INTERVAL == 0))
                {
                    snapshotCurrentContainer((GuiContainer) mc.currentScreen);
                    lastChestSnapshotMs = now;
                    lastChestSnapshotTick = worldTick;
                }
            }
        }
        if (pendingDev)
        {
            if (now > pendingDevUntilMs)
            {
                pendingDev = false;
            }
            else if (mc != null && mc.playerController != null && mc.playerController.isInCreativeMode()
                && mc.playerController.getCurrentGameType() == GameType.CREATIVE)
            {
                editorModeActive = true;
                editorModeWasActive = true;
                pendingDev = false;
            }
        }
        boolean scoreboardCodeMode = false;
        if (mc != null && mc.playerController != null && mc.playerController.isInCreativeMode()
            && mc.playerController.getCurrentGameType() == GameType.CREATIVE)
        {
            String title = getScoreboardTitle();
            if ("\u0420\u0415\u0414\u0410\u041a\u0422\u041e\u0420 \u0418\u0413\u0420\u042b".equals(title))
            {  
                scoreboardCodeMode = true;
                // DEV=true если креатив и найден скорборд
                editorModeActive = true;
                editorModeWasActive = true;
                pendingDev = false;
            }
        }
        if (mc != null && mc.playerController != null && mc.playerController.isInCreativeMode()
            && mc.playerController.getCurrentGameType() == GameType.CREATIVE && !scoreboardCodeMode)
        {
            editorModeActive = false;
            editorModeWasActive = false;
            codeMenuActive = false;
            codeMenuInventory = null;
            typePickerActive = false;
            setInputActive(false);
        }
        if (mc != null && mc.currentScreen == null && !pendingChestSnapshot && !allowChestSnapshot)
        {
            lastClickedPos = null;
            lastClickedChest = false;
            lastClickedLabel = null;
        }
        if (pendingShulkerEdit && now > pendingShulkerUntilMs)
        {
            pendingShulkerEdit = false;
            pendingShulkerPos = null;
        }
        if (pendingShulkerEdit && mc != null && mc.currentScreen instanceof GuiContainer)
        {
            handlePendingShulkerEdit((GuiContainer) mc.currentScreen);
        }
        if (editorModeActive && mc != null && mc.world != null && !lastEditorModeActive)
        {
            ensureChestCaches(mc.world.provider.getDimension());
        }
        lastEditorModeActive = editorModeActive;
        if (editorModeActive && !pendingDev && (mc == null || mc.player == null || mc.playerController == null
            || !mc.playerController.isInCreativeMode()
            || mc.playerController.getCurrentGameType() != GameType.CREATIVE))
        {
            if (now - lastDevCommandMs > EDITOR_MODE_GRACE_MS)
            {
                editorModeActive = false;
                editorModeWasActive = false;
                codeMenuActive = false;
                codeMenuInventory = null;
                typePickerActive = false;
                setInputActive(false);
            }
        }
        if (pendingChestSnapshot)
        {
            if (now > pendingChestUntilMs)
            {
                pendingChestSnapshot = false;
            }
            else if (mc != null && !lastClickedIsSign)
            {
                if (mc.currentScreen instanceof GuiContainer && !(mc.currentScreen instanceof GuiInventory))
                {
                    if (worldTick >= 0 && worldTick != lastChestSnapshotTick)
                    {
                        snapshotCurrentContainer((GuiContainer) mc.currentScreen);
                        lastChestSnapshotTick = worldTick;
                    }
                    pendingChestSnapshot = false;
                    lastSnapshotInfo = "snap:screen=" + mc.currentScreen.getClass().getSimpleName()
                        + " pos=" + (lastClickedPos == null ? "-" : lastClickedPos.toString());
                }
                else if (mc.player != null && mc.player.openContainer != null
                    && mc.player.openContainer != mc.player.inventoryContainer)
                {
                    if (worldTick >= 0 && worldTick != lastChestSnapshotTick)
                    {
                        snapshotOpenContainer(mc.player.openContainer);
                        lastChestSnapshotTick = worldTick;
                    }
                    pendingChestSnapshot = false;
                    lastSnapshotInfo = "snap:container=" + mc.player.openContainer.getClass().getSimpleName()
                        + " pos=" + (lastClickedPos == null ? "-" : lastClickedPos.toString());
                }
                else
                {
                    lastSnapshotInfo = "snap:waiting screen="
                        + (mc.currentScreen == null ? "-" : mc.currentScreen.getClass().getSimpleName())
                        + " container=" + (mc.player == null || mc.player.openContainer == null ? "-"
                        : mc.player.openContainer.getClass().getSimpleName());
                }
            }
        }
        if (mc != null)
        {
            lastClickedChest = mc.currentScreen instanceof GuiChest && !lastClickedIsSign;
        }
        if (mc == null || mc.getConnection() == null)
        {
            cachedClientTabJson = "{\"count\":0,\"players\":[]}";
        }
        else
        {
            List<String> names = new ArrayList<>();
            for (NetworkPlayerInfo info : mc.getConnection().getPlayerInfoMap())
            {
                if (info.getGameProfile() != null)
                {
                    names.add(info.getGameProfile().getName());
                }
            }
            cachedClientTabJson = buildNameListJson(names);
        }

        if (mc == null || mc.world == null)
        {
            cachedClientCoordsJson = "{\"count\":0,\"players\":[]}";
        }
        else
        {
            cachedClientCoordsJson = buildPlayerCoordsJson(mc.world.playerEntities);
        }

        handleMenuCacheTick(mc);
        handleCodeMenuKeys(mc);
        handleTpForwardKey(mc);
        handleTpScrollQueue(mc);
        handleTpPathQueue(mc);
        handleHotbarSwap(mc);
        checkConfigFileChanges();
        saveEntriesIfNeeded();
        saveMenuCacheIfNeeded();
        saveChestIdCachesIfNeeded();
        saveShulkerHolosIfNeeded();
        saveCodeBlueGlassIfNeeded();
    }

    private static String buildNameListJson(List<String> names)
    {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"count\":").append(names.size()).append(",\"players\":[");
        for (int i = 0; i < names.size(); i++)
        {
            if (i > 0) sb.append(",");
            sb.append("\"").append(escapeJson(names.get(i))).append("\"");
        }
        sb.append("]}");
        return sb.toString();
    }

    private static String buildPlayerListJson(List<? extends net.minecraft.entity.player.EntityPlayer> players)
    {
        List<String> names = new ArrayList<>();
        for (net.minecraft.entity.player.EntityPlayer p : players)
        {
            names.add(p.getName());
        }
        return buildNameListJson(names);
    }

    private static String buildPlayerCoordsJson(List<? extends net.minecraft.entity.player.EntityPlayer> players)
    {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"count\":").append(players.size()).append(",\"players\":[");
        for (int i = 0; i < players.size(); i++)
        {
            if (i > 0) sb.append(",");
            net.minecraft.entity.player.EntityPlayer p = players.get(i);
            int dim = p.world == null ? 0 : p.world.provider.getDimension();
            sb.append("{\"name\":\"").append(escapeJson(p.getName())).append("\",")
                .append("\"x\":").append(formatCoord(p.posX)).append(",")
                .append("\"y\":").append(formatCoord(p.posY)).append(",")
                .append("\"z\":").append(formatCoord(p.posZ)).append(",")
                .append("\"dim\":").append(dim).append("}");
        }
        sb.append("]}");
        return sb.toString();
    }

    private String buildSelfJson(net.minecraft.entity.player.EntityPlayer player, String mode)
    {
        int dim = player.world == null ? 0 : player.world.provider.getDimension();
        return "{\"ok\":true,\"name\":\"" + escapeJson(player.getName()) + "\",\"x\":"
            + formatCoord(player.posX) + ",\"y\":" + formatCoord(player.posY) + ",\"z\":"
            + formatCoord(player.posZ) + ",\"dim\":" + dim + ",\"mode\":\""
            + mode + "\",\"port\":" + apiPort + "}";
    }

    private static String formatCoord(double value)
    {
        return String.format(Locale.ROOT, "%.3f", value);
    }

    private void startRegistryServer() throws IOException
    {
        try
        {
            registryServer = HttpServer.create(new InetSocketAddress("127.0.0.1", BASE_PORT), 0);
            registryEnabled = true;
            registerRegistryEndpoints(registryServer);
            registryServer.setExecutor(Executors.newCachedThreadPool());
            registryServer.start();
        }
        catch (BindException e)
        {
            registryEnabled = false;
            registryServer = null;
        }
    }

    private void registerRegistryEndpoints(HttpServer server)
    {
        server.createContext("/registry/ping", this::handleRegistryPing);
        server.createContext("/registry/register", this::handleRegistryRegister);
        server.createContext("/registry/clients", this::handleRegistryClients);
    }

    private void handleRegistryPing(HttpExchange exchange) throws IOException
    {
        sendJson(exchange, 200, "{\"ok\":true}");
    }

    private void handleRegistryRegister(HttpExchange exchange) throws IOException
    {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod()))
        {
            sendJson(exchange, 405, "{\"error\":\"method_not_allowed\"}");
            return;
        }

        String body = readBody(exchange.getRequestBody()).trim();
        String query = exchange.getRequestURI().getRawQuery();
        String name = body.isEmpty() ? getQueryParam(query, "name") : getQueryParam(body, "name");
        String portStr = body.isEmpty() ? getQueryParam(query, "port") : getQueryParam(body, "port");
        String mode = body.isEmpty() ? getQueryParam(query, "mode") : getQueryParam(body, "mode");

        if (name == null || name.isEmpty() || portStr == null)
        {
            sendJson(exchange, 400, "{\"ok\":false,\"error\":\"missing_name_or_port\"}");
            return;
        }

        int port;
        try
        {
            port = Integer.parseInt(portStr.trim());
        }
        catch (NumberFormatException e)
        {
            sendJson(exchange, 400, "{\"ok\":false,\"error\":\"bad_port\"}");
            return;
        }

        String key = port + ":" + name;
        RegistryEntry entry = new RegistryEntry(name, port, mode == null ? "" : mode);
        entry.lastSeenMs = System.currentTimeMillis();
        registryEntries.put(key, entry);

        sendJson(exchange, 200, "{\"ok\":true}");
    }

    private void handleRegistryClients(HttpExchange exchange) throws IOException
    {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod()))
        {
            sendJson(exchange, 405, "{\"error\":\"method_not_allowed\"}");
            return;
        }

        pruneRegistry();
        StringBuilder sb = new StringBuilder();
        sb.append("{\"count\":").append(registryEntries.size()).append(",\"clients\":[");
        boolean first = true;
        for (RegistryEntry entry : registryEntries.values())
        {
            if (!first) sb.append(",");
            first = false;
            sb.append("{\"name\":\"").append(escapeJson(entry.name)).append("\",")
                .append("\"port\":").append(entry.port).append(",")
                .append("\"mode\":\"").append(escapeJson(entry.mode)).append("\",")
                .append("\"lastSeenMs\":").append(entry.lastSeenMs).append("}");
        }
        sb.append("]}");
        sendJson(exchange, 200, sb.toString());
    }

    private void pruneRegistry()
    {
        long now = System.currentTimeMillis();
        registryEntries.entrySet().removeIf(e -> now - e.getValue().lastSeenMs > REGISTRY_TTL_MS);
    }

    private void startRegistryHeartbeat()
    {
        if (registryHeartbeat != null)
        {
            return;
        }
        registryHeartbeat = Executors.newSingleThreadScheduledExecutor();
        registryHeartbeat.scheduleAtFixedRate(this::sendRegistryHeartbeat, 0, REGISTRY_HEARTBEAT_MS,
            TimeUnit.MILLISECONDS);
    }

    private void sendRegistryHeartbeat()
    {
        String name = getSelfName();
        if (name == null || name.isEmpty() || apiPort <= 0)
        {
            return;
        }

        String mode = mcServer != null ? "server" : "client";
        String body = "name=" + urlEncode(name) + "&port=" + apiPort + "&mode=" + urlEncode(mode);
        try
        {
            URL url = new URL("http://127.0.0.1:" + BASE_PORT + "/registry/register");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(500);
            conn.setReadTimeout(500);
            conn.setDoOutput(true);
            byte[] data = body.getBytes(StandardCharsets.UTF_8);
            conn.getOutputStream().write(data);
            conn.getOutputStream().flush();
            conn.getInputStream().close();
            conn.disconnect();
        }
        catch (IOException ignored)
        {
            // Registry not available, ignore.
        }
    }

    private String getSelfName()
    {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc != null && mc.player != null)
        {
            return mc.player.getName();
        }
        if (mcServer != null && mcServer.getPlayerList() != null && !mcServer.getPlayerList().getPlayers().isEmpty())
        {
            return mcServer.getPlayerList().getPlayers().get(0).getName();
        }
        return "";
    }

    private static String urlEncode(String s)
    {
        try
        {
            return java.net.URLEncoder.encode(s, "UTF-8");
        }
        catch (Exception e)
        {
            return s;
        }
    }

    private void registerClientCommands()
    {
        ClientCommandHandler.instance.registerCommand(new CBarCommand("cbar", true));
        ClientCommandHandler.instance.registerCommand(new CBarCommand("cbar2", false));
        ClientCommandHandler.instance.registerCommand(new GenCommand());
        ClientCommandHandler.instance.registerCommand(new DebugCommand());
        ClientCommandHandler.instance.registerCommand(new ConfigDebugCommand());
        ClientCommandHandler.instance.registerCommand(new TestHoloCommand());
        ClientCommandHandler.instance.registerCommand(new TestChestHoloCommand());
        ClientCommandHandler.instance.registerCommand(new TestShulkerHoloCommand());
        ClientCommandHandler.instance.registerCommand(new NoteCommand());
        ClientCommandHandler.instance.registerCommand(new ScoreLineCommand());
        ClientCommandHandler.instance.registerCommand(new ScoreTitleCommand());
        ClientCommandHandler.instance.registerCommand(new ApiPortCommand());
        ClientCommandHandler.instance.registerCommand(new GuiExportCommand());
        ClientCommandHandler.instance.registerCommand(new TestTpLocalCommand());
        ClientCommandHandler.instance.registerCommand(new TpPathCommand());
        ClientCommandHandler.instance.registerCommand(new SignSearchCommand());
        ClientCommandHandler.instance.registerCommand(new ExportLineCommand());
    }


    private void registerKeybinds()
    {
        keyOpenCodeMenu = new KeyBinding("key.mcpythonapi.codemenu", Keyboard.KEY_R, "key.categories.mcpythonapi");
        keyOpenCodeMenuAlt = new KeyBinding("key.mcpythonapi.codemenu_alt", Keyboard.KEY_RMENU,
            "key.categories.mcpythonapi");
        keyTpForward = new KeyBinding("key.mcpythonapi.tpfwd", Keyboard.KEY_G, "key.categories.mcpythonapi");
        ClientRegistry.registerKeyBinding(keyOpenCodeMenu);
        ClientRegistry.registerKeyBinding(keyOpenCodeMenuAlt);
        ClientRegistry.registerKeyBinding(keyTpForward);
    }

    private static class CBarCommand extends CommandBase
    {
        private final String name;
        private final boolean primary;

        CBarCommand(String name, boolean primary)
        {
            this.name = name;
            this.primary = primary;
        }

        @Override
        public String getName()
        {
            return name;
        }

        @Override
        public String getUsage(ICommandSender sender)
        {
            return "/" + name + " <text>";
        }

        @Override
        public void execute(MinecraftServer server, ICommandSender sender, String[] args)
        {
            if (args.length == 0)
            {
                return;
            }
            String text = String.join(" ", args);
            setActionBar(primary, text, BAR_DEFAULT_MS);
        }

        @Override
        public int getRequiredPermissionLevel()
        {
            return 0;
        }
    }

    private static class GenCommand extends CommandBase
    {
        @Override
        public String getName()
        {
            return "gen";
        }

        @Override
        public String getUsage(ICommandSender sender)
        {
            return "/gen <name> <start-end>";
        }

        @Override
        public void execute(MinecraftServer server, ICommandSender sender, String[] args)
        {
            if (args.length < 2)
            {
                return;
            }
            Minecraft mc = Minecraft.getMinecraft();
            if (mc == null || mc.player == null)
            {
                return;
            }
            String base = args[0];
            int[] range = parseRange(args[1]);
            if (range == null)
            {
                return;
            }
            int start = range[0];
            int end = range[1];
            int count = Math.min(9, end - start + 1);
            for (int i = 0; i < count; i++)
            {
                int index = start + i;
                ItemStack stack = new ItemStack(Items.MAGMA_CREAM, 1);
                stack.setStackDisplayName(base + index);
                mc.player.inventory.setInventorySlotContents(i, stack);
            }
        }

        @Override
        public int getRequiredPermissionLevel()
        {
            return 0;
        }
    }

    private class DebugCommand extends CommandBase
    {
        @Override
        public String getName()
        {
            return "mpdebug";
        }

        @Override
        public String getUsage(ICommandSender sender)
        {
            return "/mpdebug";
        }

        @Override
        public void execute(MinecraftServer server, ICommandSender sender, String[] args)
        {
            debugUi = !debugUi;
            setActionBar(true, debugUi ? "&aDebug ON" : "&cDebug OFF", 2000L);
        }

        @Override
        public int getRequiredPermissionLevel()
        {
            return 0;
        }
    }

    private class TestHoloCommand extends CommandBase
    {
        @Override
        public String getName()
        {
            return "testholo";
        }

        @Override
        public String getUsage(ICommandSender sender)
        {
            return "/testholo [on|off]";
        }

        @Override
        public void execute(MinecraftServer server, ICommandSender sender, String[] args)
        {
            Minecraft mc = Minecraft.getMinecraft();
            if (mc == null || mc.player == null)
            {
                return;
            }
            if (args.length > 0)
            {
                String arg = args[0].toLowerCase(Locale.ROOT);
                if ("on".equals(arg))
                {
                    testHoloActive = true;
                }
                else if ("off".equals(arg))
                {
                    testHoloActive = false;
                }
            }
            else
            {
                testHoloActive = !testHoloActive;
            }
            if (testHoloActive)
            {
                testHoloPos = new BlockPos(mc.player.posX, mc.player.posY + 1.5, mc.player.posZ);
                setActionBar(true, "&aHolo ON", 1500L);
            }
            else
            {
                setActionBar(true, "&cHolo OFF", 1500L);
            }
        }

        @Override
        public int getRequiredPermissionLevel()
        {
            return 0;
        }
    }

    private class TestChestHoloCommand extends CommandBase
    {
        @Override
        public String getName()
        {
            return "testchestholo";
        }

        @Override
        public String getUsage(ICommandSender sender)
        {
            return "/testchestholo add [gui|fbo] [s=0.016] [w=40] [tex=1024] | set [s=0.016] [w=40] [tex=1024] [mode=cache|test|gui] [font=linear|nearest] | clear";
        }

        @Override
        public void execute(MinecraftServer server, ICommandSender sender, String[] args)
        {
            Minecraft mc = Minecraft.getMinecraft();
            if (mc == null || mc.player == null || mc.world == null)
            {
                return;
            }
            if (args.length == 0)
            {
                addTestChestHolo(mc, false, CHEST_HOLO_SCALE, CHEST_HOLO_TEXT_WIDTH, CHEST_HOLO_TEX_SIZE);
                return;
            }
            String arg = args[0].toLowerCase(Locale.ROOT);
            if ("clear".equals(arg))
            {
                testChestHolos.clear();
                setActionBar(true, "&cChest holo cleared", 1500L);
                return;
            }
            if ("set".equals(arg))
            {
                float scale = chestHoloScale;
                int textWidth = chestHoloTextWidth;
                int texSize = chestHoloTexSize;
                boolean force = chestHoloForceRerender;
                boolean useTest = chestHoloUseTestPipeline;
                boolean smoothFont = chestHoloSmoothFont;
                boolean useGui = chestHoloUseGuiRender;
                for (int i = 1; i < args.length; i++)
                {
                    String opt = args[i];
                    if (opt.startsWith("s="))
                    {
                        try { scale = Float.parseFloat(opt.substring(2)); } catch (Exception e) { }
                    }
                    else if (opt.startsWith("w="))
                    {
                        try { textWidth = Integer.parseInt(opt.substring(2)); } catch (Exception e) { }
                    }
                    else if (opt.startsWith("tex="))
                    {
                        try { texSize = Integer.parseInt(opt.substring(4)); } catch (Exception e) { }
                    }
                    else if (opt.startsWith("force="))
                    {
                        force = "1".equals(opt.substring(6)) || "true".equalsIgnoreCase(opt.substring(6));
                    }
                    else if (opt.startsWith("mode="))
                    {
                        String mode = opt.substring(5).toLowerCase(Locale.ROOT);
                        if ("gui".equals(mode))
                        {
                            useGui = true;
                            useTest = false;
                        }
                        else if ("test".equals(mode))
                        {
                            useTest = true;
                            useGui = false;
                        }
                        else
                        {
                            useTest = false;
                            useGui = false;
                        }
                    }
                    else if (opt.startsWith("font="))
                    {
                        String mode = opt.substring(5).toLowerCase(Locale.ROOT);
                        smoothFont = !"nearest".equals(mode);
                    }
                }
                chestHoloScale = scale;
                chestHoloTextWidth = textWidth;
                chestHoloTexSize = texSize;
                chestHoloForceRerender = force;
                chestHoloUseTestPipeline = useTest;
                chestHoloSmoothFont = smoothFont;
                chestHoloUseGuiRender = useGui;
                lastTestScale = scale;
                lastTestTextWidth = textWidth;
                lastTestTexSize = texSize;
                chestPreviewFbos.clear();
                chestTestHolos.clear();
                setActionBar(true, "&aChest holo set", 1500L);
                return;
            }
            boolean useFbo = "fbo".equals(arg);
            float scale = CHEST_HOLO_SCALE;
            int textWidth = CHEST_HOLO_TEXT_WIDTH;
            int texSize = CHEST_HOLO_TEX_SIZE;
            boolean force = chestHoloForceRerender;
            boolean smoothFont = chestHoloSmoothFont;
            for (int i = 1; i < args.length; i++)
            {
                String opt = args[i];
                if (opt.startsWith("s="))
                {
                    try { scale = Float.parseFloat(opt.substring(2)); } catch (Exception e) { }
                }
                else if (opt.startsWith("w="))
                {
                    try { textWidth = Integer.parseInt(opt.substring(2)); } catch (Exception e) { }
                }
                else if (opt.startsWith("tex="))
                {
                    try { texSize = Integer.parseInt(opt.substring(4)); } catch (Exception e) { }
                }
                else if (opt.startsWith("force="))
                {
                    force = "1".equals(opt.substring(6)) || "true".equalsIgnoreCase(opt.substring(6));
                }
                else if (opt.startsWith("font="))
                {
                    String mode = opt.substring(5).toLowerCase(Locale.ROOT);
                    smoothFont = !"nearest".equals(mode);
                }
            }
            chestHoloScale = scale;
            chestHoloTextWidth = textWidth;
            chestHoloTexSize = texSize;
            chestHoloForceRerender = force;
            chestHoloSmoothFont = smoothFont;
            chestHoloUseGuiRender = false;
            lastTestScale = scale;
            lastTestTextWidth = textWidth;
            lastTestTexSize = texSize;
            chestPreviewFbos.clear();
            addTestChestHolo(mc, useFbo, scale, textWidth, texSize);
        }

        @Override
        public int getRequiredPermissionLevel()
        {
            return 0;
        }
    }

    private class ConfigDebugCommand extends CommandBase
    {
        @Override
        public String getName()
        {
            return "mpcfg";
        }

        @Override
        public String getUsage(ICommandSender sender)
        {
            return "/mpcfg";
        }

        @Override
        public void execute(MinecraftServer server, ICommandSender sender, String[] args)
        {
            File cfg = config == null ? null : config.getConfigFile();
            String path = cfg == null ? "null" : cfg.getAbsolutePath();
            long stamp = cfg == null ? 0L : cfg.lastModified();
            setActionBar(true, "&eCfg file: " + path, 4000L);
            setActionBar(false, "&eCfg hotbar=" + enableSecondHotbar + " holo=#"
                + String.format(Locale.ROOT, "%06X", chestHoloTextColor) + " t=" + stamp, 4000L);
        }

        @Override
        public int getRequiredPermissionLevel()
        {
            return 0;
        }
    }

    private class TestShulkerHoloCommand extends CommandBase
    {
        @Override
        public String getName()
        {
            return "testshulkerholo";
        }

        @Override
        public String getUsage(ICommandSender sender)
        {
            return "/testshulkerholo set [s=0.03] [y=1.15] [z=0.0] | clear | me <text> | mefront <text> | mode [billboard|fixed] | depth [on|off] | cull [on|off] | info";
        }

        @Override
        public int getRequiredPermissionLevel()
        {
            return 0;
        }

        @Override
        public boolean checkPermission(MinecraftServer server, ICommandSender sender)
        {
            return true;
        }

        @Override
        public void execute(MinecraftServer server, ICommandSender sender, String[] args)
        {
            if (args.length == 0)
            {
                setActionBar(true, "&eShulker holo s=" + shulkerHoloScale + " y=" + shulkerHoloYOffset
                    + " z=" + shulkerHoloZOffset, 3000L);
                return;
            }
            String cmd = args[0].toLowerCase(Locale.ROOT);
            if ("clear".equals(cmd))
            {
                shulkerHolos.clear();
                shulkerHoloDirty = true;
                setActionBar(true, "&cShulker holos cleared", 1500L);
                return;
            }
            if ("info".equals(cmd))
            {
                int count = shulkerHolos.size();
                StringBuilder sb = new StringBuilder();
                sb.append("Shulker holos=").append(count);
                if (count > 0)
                {
                    ShulkerHolo first = shulkerHolos.values().iterator().next();
                    if (first != null && first.pos != null)
                    {
                        sb.append(" pos=").append(first.pos.getX()).append(",")
                            .append(first.pos.getY()).append(",").append(first.pos.getZ());
                    }
                }
                setActionBar(true, sb.toString(), 3000L);
                return;
            }
            if ("me".equals(cmd))
            {
                Minecraft mc = Minecraft.getMinecraft();
                if (mc == null || mc.player == null)
                {
                    setActionBar(true, "&cNo player", 1500L);
                    return;
                }
                if (args.length < 2)
                {
                    setActionBar(true, "&cText required", 1500L);
                    return;
                }
                StringBuilder sb = new StringBuilder();
                for (int i = 1; i < args.length; i++)
                {
                    if (i > 1)
                    {
                        sb.append(' ');
                    }
                    sb.append(args[i]);
                }
                BlockPos pos = mc.player.getPosition().up(2);
                int dim = mc.player.dimension;
                putShulkerHolo(dim, pos, sb.toString(), 0xFFFFFF);
                setActionBar(true, "&aShulker holo me", 1500L);
                return;
            }
            if ("mefront".equals(cmd))
            {
                Minecraft mc = Minecraft.getMinecraft();
                if (mc == null || mc.player == null)
                {
                    setActionBar(true, "&cNo player", 1500L);
                    return;
                }
                if (args.length < 2)
                {
                    setActionBar(true, "&cText required", 1500L);
                    return;
                }
                StringBuilder sb = new StringBuilder();
                for (int i = 1; i < args.length; i++)
                {
                    if (i > 1)
                    {
                        sb.append(' ');
                    }
                    sb.append(args[i]);
                }
                Vec3d look = mc.player.getLookVec();
                BlockPos pos = new BlockPos(mc.player.posX + look.x * 2.0,
                    mc.player.posY + look.y * 2.0 + 1.0,
                    mc.player.posZ + look.z * 2.0);
                int dim = mc.player.dimension;
                putShulkerHolo(dim, pos, sb.toString(), 0xFFFFFF);
                setActionBar(true, "&aShulker holo front", 1500L);
                return;
            }
            if ("set".equals(cmd))
            {
                for (int i = 1; i < args.length; i++)
                {
                    String token = args[i];
                    if (token.startsWith("s="))
                    {
                        shulkerHoloScale = ExampleMod.parseFloat(token.substring(2), shulkerHoloScale);
                    }
                    else if (token.startsWith("y="))
                    {
                        shulkerHoloYOffset = ExampleMod.parseDouble(token.substring(2), shulkerHoloYOffset);
                    }
                    else if (token.startsWith("z="))
                    {
                        shulkerHoloZOffset = ExampleMod.parseDouble(token.substring(2), shulkerHoloZOffset);
                    }
                }
                setActionBar(true, "&aShulker holo set", 1500L);
                return;
            }
            if ("mode".equals(cmd))
            {
                if (args.length < 2)
                {
                    setActionBar(true, "&eMode=" + (shulkerHoloBillboard ? "billboard" : "fixed"), 1500L);
                    return;
                }
                String mode = args[1].toLowerCase(Locale.ROOT);
                shulkerHoloBillboard = !"fixed".equals(mode);
                setActionBar(true, "&aShulker holo mode=" + (shulkerHoloBillboard ? "billboard" : "fixed"), 1500L);
                return;
            }
            if ("depth".equals(cmd))
            {
                if (args.length < 2)
                {
                    setActionBar(true, "&eDepth=" + (shulkerHoloDepth ? "on" : "off"), 1500L);
                    return;
                }
                String mode = args[1].toLowerCase(Locale.ROOT);
                shulkerHoloDepth = "on".equals(mode);
                setActionBar(true, "&aShulker holo depth=" + (shulkerHoloDepth ? "on" : "off"), 1500L);
                return;
            }
            if ("cull".equals(cmd))
            {
                if (args.length < 2)
                {
                    setActionBar(true, "&eCull=" + (shulkerHoloCull ? "on" : "off"), 1500L);
                    return;
                }
                String mode = args[1].toLowerCase(Locale.ROOT);
                shulkerHoloCull = "on".equals(mode);
                setActionBar(true, "&aShulker holo cull=" + (shulkerHoloCull ? "on" : "off"), 1500L);
                return;
            }
            setActionBar(false, "&cUnknown args", 1500L);
        }
    }

    private class ApiPortCommand extends CommandBase
    {
        @Override
        public String getName()
        {
            return "apiport";
        }

        @Override
        public String getUsage(ICommandSender sender)
        {
            return "/apiport";
        }

        @Override
        public void execute(MinecraftServer server, ICommandSender sender, String[] args)
        {
            Minecraft mc = Minecraft.getMinecraft();
            if (mc != null && mc.player != null)
            {
                mc.player.sendMessage(new TextComponentString("API port: " + apiPort));
            }
        }

        @Override
        public int getRequiredPermissionLevel()
        {
            return 0;
        }
    }

    private class NoteCommand extends CommandBase
    {
        @Override
        public String getName()
        {
            return "note";
        }

        @Override
        public String getUsage(ICommandSender sender)
        {
            return "/note save <text> | /note read";
        }

        @Override
        public void execute(MinecraftServer server, ICommandSender sender, String[] args)
        {
            if (args.length == 0)
            {
                setActionBar(true, "&e" + getUsage(sender), 3000L);
                return;
            }
            String sub = args[0].toLowerCase(Locale.ROOT);
            if ("read".equals(sub))
            {
                String text = noteText == null ? "" : noteText;
                if (text.isEmpty())
                {
                    setActionBar(true, "&eNote is empty.", 2000L);
                }
                else if (sender != null)
                {
                    sender.sendMessage(new TextComponentString(text));
                }
                return;
            }
            if ("save".equals(sub))
            {
                if (args.length < 2)
                {
                    setActionBar(true, "&eNote text missing.", 2000L);
                    return;
                }
                StringBuilder sb = new StringBuilder();
                for (int i = 1; i < args.length; i++)
                {
                    if (i > 1)
                    {
                        sb.append(' ');
                    }
                    sb.append(args[i]);
                }
                noteText = sb.toString();
                saveNote();
                setActionBar(true, "&aNote saved.", 1500L);
                return;
            }
            setActionBar(true, "&e" + getUsage(sender), 3000L);
        }

        @Override
        public int getRequiredPermissionLevel()
        {
            return 0;
        }
    }

    private class ScoreLineCommand extends CommandBase
    {
        @Override
        public String getName()
        {
            return "scoreline";
        }

        @Override
        public String getUsage(ICommandSender sender)
        {
            return "/scoreline <score>";
        }

        @Override
        public void execute(MinecraftServer server, ICommandSender sender, String[] args)
        {
            if (args.length < 1)
            {
                setActionBar(true, "&e" + getUsage(sender), 3000L);
                return;
            }
            int scoreValue;
            try
            {
                scoreValue = Integer.parseInt(args[0]);
            }
            catch (NumberFormatException e)
            {
                setActionBar(true, "&cInvalid score.", 2000L);
                return;
            }
            String line = getScoreboardLineByScore(scoreValue);
            if (line == null || line.isEmpty())
            {
                setActionBar(true, "&eNo line for score " + scoreValue, 2000L);
                return;
            }
            if (sender != null)
            {
                sender.sendMessage(new TextComponentString(line));
            }
        }

        @Override
        public int getRequiredPermissionLevel()
        {
            return 0;
        }
    }

    private class ScoreTitleCommand extends CommandBase
    {
        @Override
        public String getName()
        {
            return "scoretitle";
        }

        @Override
        public String getUsage(ICommandSender sender)
        {
            return "/scoretitle";
        }

        @Override
        public void execute(MinecraftServer server, ICommandSender sender, String[] args)
        {
            String title = getScoreboardTitle();
            if (title == null || title.isEmpty())
            {
                setActionBar(true, "&eNo scoreboard title.", 2000L);
                return;
            }
            if (sender != null)
            {
                sender.sendMessage(new TextComponentString(title));
            }
        }

        @Override
        public int getRequiredPermissionLevel()
        {
            return 0;
        }
    }

    private class GuiExportCommand extends CommandBase
    {
        @Override
        public String getName()
        {
            return "guiexport";
        }

        @Override
        public String getUsage(ICommandSender sender)
        {
            return "/guiexport [raw|clean|both]";
        }

        @Override
        public void execute(MinecraftServer server, ICommandSender sender, String[] args)
        {
            Minecraft mc = Minecraft.getMinecraft();
            if (mc == null || !(mc.currentScreen instanceof GuiContainer))
            {
                setActionBar(true, "&eOpen a GUI container first.", 2000L);
                return;
            }
            String mode = args.length > 0 ? args[0].toLowerCase(Locale.ROOT) : "both";
            boolean wantRaw = "raw".equals(mode) || "both".equals(mode);
            boolean wantClean = "clean".equals(mode) || "both".equals(mode);
            if (!wantRaw && !wantClean)
            {
                setActionBar(true, "&e" + getUsage(sender), 3000L);
                return;
            }
            exportGuiToClipboard((GuiContainer) mc.currentScreen, wantRaw, wantClean);
        }

        @Override
        public int getRequiredPermissionLevel()
        {
            return 0;
        }
    }

    private class TestTpLocalCommand extends CommandBase
    {
        @Override
        public String getName()
        {
            return "testtplocal";
        }

        @Override
        public String getUsage(ICommandSender sender)
        {
            return "/testtplocal <dx> <dy> <dz>";
        }

        @Override
        public void execute(MinecraftServer server, ICommandSender sender, String[] args)
        {
            Minecraft mc = Minecraft.getMinecraft();
            if (mc == null || mc.player == null || mc.playerController == null)
            {
                return;
            }
            if (!mc.playerController.isInCreativeMode()
                || mc.playerController.getCurrentGameType() != GameType.CREATIVE)
            {
                setActionBar(false, "&cCreative only.", 2000L);
                return;
            }
            if (args.length < 3)
            {
                setActionBar(true, "&e" + getUsage(sender), 3000L);
                return;
            }
            double dx;
            double dy;
            double dz;
            try
            {
                dx = Double.parseDouble(args[0]);
                dy = Double.parseDouble(args[1]);
                dz = Double.parseDouble(args[2]);
            }
            catch (NumberFormatException e)
            {
                setActionBar(true, "&cInvalid offset.", 2000L);
                return;
            }
            double nx = mc.player.posX + dx;
            double ny = mc.player.posY + dy;
            double nz = mc.player.posZ + dz;
            mc.player.setPosition(nx, ny, nz);
            mc.player.motionX = 0.0;
            mc.player.motionY = 0.0;
            mc.player.motionZ = 0.0;
            setActionBar(true, "&aLocal TP: " + (int) dx + " " + (int) dy + " " + (int) dz, 1500L);
        }

        @Override
        public int getRequiredPermissionLevel()
        {
            return 0;
        }
    }

    private class TpPathCommand extends CommandBase
    {
        @Override
        public String getName()
        {
            return "tppath";
        }

        @Override
        public String getUsage(ICommandSender sender)
        {
            return "/tppath <x> <y> <z>";
        }

        @Override
        public void execute(MinecraftServer server, ICommandSender sender, String[] args)
        {
            Minecraft mc = Minecraft.getMinecraft();
            if (mc == null || mc.player == null || mc.playerController == null)
            {
                return;
            }
            if (!editorModeActive || !mc.playerController.isInCreativeMode()
                || mc.playerController.getCurrentGameType() != GameType.CREATIVE)
            {
                setActionBar(false, "&cCreative+code only.", 2000L);
                return;
            }
            if (args.length < 3)
            {
                setActionBar(true, "&e" + getUsage(sender), 3000L);
                return;
            }
            double tx;
            double ty;
            double tz;
            try
            {
                tx = Double.parseDouble(args[0]);
                ty = Double.parseDouble(args[1]);
                tz = Double.parseDouble(args[2]);
            }
            catch (NumberFormatException e)
            {
                setActionBar(true, "&cInvalid coords.", 2000L);
                return;
            }
            buildTpPathQueue(mc.world, mc.player.posX, mc.player.posY, mc.player.posZ, tx, ty, tz);
        }

        @Override
        public int getRequiredPermissionLevel()
        {
            return 0;
        }
    }

    private class SignSearchCommand extends CommandBase
    {
        @Override
        public String getName()
        {
            return "tsearch";
        }

        @Override
        public String getUsage(ICommandSender sender)
        {
            return "/tsearch <text> | /tsearch clear";
        }

        @Override
        public void execute(MinecraftServer server, ICommandSender sender, String[] args)
        {
            Minecraft mc = Minecraft.getMinecraft();
            if (mc == null || mc.world == null)
            {
                return;
            }
            if (args.length == 0)
            {
                setActionBar(true, "&e" + getUsage(sender), 3000L);
                return;
            }
            String first = args[0].toLowerCase(Locale.ROOT);
            if ("clear".equals(first))
            {
                signSearchQuery = null;
                signSearchMatches.clear();
                setActionBar(true, "&eSign search cleared", 2000L);
                return;
            }
            String query = String.join(" ", args).trim();
            if (query.isEmpty())
            {
                setActionBar(true, "&cText required", 2000L);
                return;
            }
            signSearchQuery = query.toLowerCase(Locale.ROOT);
            signSearchDim = mc.world.provider.getDimension();
            signSearchMatches.clear();
            for (TileEntity tile : new ArrayList<>(mc.world.loadedTileEntityList))
            {
                if (!(tile instanceof TileEntitySign))
                {
                    continue;
                }
                TileEntitySign sign = (TileEntitySign) tile;
                if (signMatchesQuery(sign, signSearchQuery))
                {
                    signSearchMatches.add(sign.getPos());
                }
            }
            setActionBar(true, "&aSigns: " + signSearchMatches.size(), 2000L);
        }

        @Override
        public int getRequiredPermissionLevel()
        {
            return 0;
        }
    }

    private void addTestChestHolo(Minecraft mc, boolean useFbo, float scale, int textWidth, int texSize)
    {
        BlockPos pos = new BlockPos(mc.player.posX, mc.player.posY + 1.0, mc.player.posZ);
        List<ItemStack> items = new ArrayList<>();
        for (int i = 0; i < 54; i++)
        {
            items.add(new ItemStack(Blocks.DIRT, 64));
        }
        ChestCache cache = new ChestCache(mc.world.provider.getDimension(), pos, items, System.currentTimeMillis(),
            CHEST_HOLO_LABEL);
        TestChestHolo holo = new TestChestHolo(pos, cache, useFbo, scale, textWidth, texSize);
        testChestHolos.add(holo);
        setActionBar(true, useFbo ? "&aChest holo FBO" : "&aChest holo GUI", 1500L);
    }

    private static int[] parseRange(String text)
    {
        String[] parts = text.split("-");
        if (parts.length != 2) return null;
        try
        {
            int start = Integer.parseInt(parts[0].trim());
            int end = Integer.parseInt(parts[1].trim());
            if (end < start) return null;
            return new int[]{start, end};
        }
        catch (NumberFormatException e)
        {
            return null;
        }
    }

    private static void setActionBar(boolean primary, String text, long durationMs)
    {
        String formatted = applyColorCodes(text);
        long until = System.currentTimeMillis() + Math.max(0, durationMs);
        if (primary)
        {
            actionBarText = formatted;
            actionBarExpireMs = until;
        }
        else
        {
            actionBar2Text = formatted;
            actionBar2ExpireMs = until;
        }
    }

    private static String applyColorCodes(String text)
    {
        return text == null ? "" : text.replace("&", "\u00a7");
    }

    private static int parseHexColor(String text, int fallback)
    {
        if (text == null)
        {
            return fallback;
        }
        String clean = text.trim();
        if (clean.isEmpty())
        {
            return fallback;
        }
        if (!clean.startsWith("#") && clean.length() <= 6)
        {
            clean = "#" + clean;
        }
        try
        {
            return Integer.decode(clean);
        }
        catch (NumberFormatException e)
        {
            return fallback;
        }
    }

    private static List<String> extractPageTexts(Map<String, String> params)
    {
        List<Integer> indexes = new ArrayList<>();
        for (String key : params.keySet())
        {
            if (key.startsWith("p"))
            {
                try
                {
                    int idx = Integer.parseInt(key.substring(1));
                    if (idx >= 1 && idx <= 200)
                    {
                        indexes.add(idx);
                    }
                }
                catch (NumberFormatException ignored)
                {
                    // ignore
                }
            }
        }
        if (indexes.isEmpty())
        {
            return Collections.emptyList();
        }
        Collections.sort(indexes);
        int max = indexes.get(indexes.size() - 1);
        List<String> pages = new ArrayList<>(Collections.nCopies(max, ""));
        for (int idx : indexes)
        {
            String value = params.get("p" + idx);
            pages.set(idx - 1, value == null ? "" : value);
        }
        return pages;
    }

    private String getClientBlockReason()
    {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc != null && mc.currentScreen instanceof GuiChat)
        {
            return "chat_open";
        }
        long now = System.currentTimeMillis();
        if (lastApiChatSentMs > 0 && now - lastApiChatSentMs < CLIENT_CHAT_COOLDOWN_MS)
        {
            return "chat_cooldown";
        }
        return null;
    }

    private void initHotbarStorage()
    {
        for (int s = 0; s < hotbarSets.length; s++)
        {
            for (int i = 0; i < hotbarSets[s].length; i++)
            {
                hotbarSets[s][i] = ItemStack.EMPTY;
            }
        }
    }

    public static Configuration getConfig()
    {
        return config;
    }

    private void initHotbarsForWorld(Minecraft mc)
    {
        activeHotbarSet = 0;
        clearHotbarSet(1);
        saveCurrentHotbar(mc, 0);
        long now = System.currentTimeMillis();
        boolean keepDev = pendingDev || (now - lastDevCommandMs <= EDITOR_MODE_GRACE_MS);
        if (!keepDev)
        {
            editorModeActive = false;
            editorModeWasActive = false;
        }
        menuCache.clear();
        customMenuCache = null;
        chestCaches.clear();
        pendingCacheKey = null;
        fakeMenuActive = false;
        fakeMenuKey = null;
        awaitingCacheSnapshot = false;
        queuedClicks.clear();
        typePickerActive = false;
        quickApplyWindowId = -1;
        quickApplySlotNumber = -1;
        setInputActive(false);
        signSearchQuery = null;
        signSearchMatches.clear();
        for (int i = 0; i < lastHotbarTapMs.length; i++)
        {
            lastHotbarTapMs[i] = 0L;
            hotbarKeyDown[i] = false;
        }
    }

    private void clearHotbarSet(int setIndex)
    {
        for (int i = 0; i < 9; i++)
        {
            hotbarSets[setIndex][i] = ItemStack.EMPTY;
        }
    }

    private void saveCurrentHotbar(Minecraft mc, int setIndex)
    {
        if (mc == null || mc.player == null)
        {
            return;
        }
        for (int i = 0; i < 9; i++)
        {
            ItemStack stack = mc.player.inventory.getStackInSlot(i);
            hotbarSets[setIndex][i] = stack.isEmpty() ? ItemStack.EMPTY : stack.copy();
        }
    }

    private void loadHotbar(Minecraft mc, int setIndex)
    {
        if (mc == null || mc.player == null)
        {
            return;
        }
        for (int i = 0; i < 9; i++)
        {
            ItemStack stack = hotbarSets[setIndex][i];
            ItemStack target = stack.isEmpty() ? ItemStack.EMPTY : stack.copy();
            mc.player.inventory.setInventorySlotContents(i, target);
            sendCreativeSlotUpdate(mc, i, target);
        }
        mc.player.inventory.markDirty();
    }

    private void sendCreativeSlotUpdate(Minecraft mc, int inventoryIndex, ItemStack stack)
    {
        if (mc == null || mc.getConnection() == null || mc.player == null)
        {
            return;
        }
        int size = mc.player.inventory.getSizeInventory();
        if (inventoryIndex < 0 || inventoryIndex >= size)
        {
            return;
        }
        if (mc.playerController == null || !mc.playerController.isInCreativeMode())
        {
            return;
        }
        int slotId = inventoryIndex < 9 ? 36 + inventoryIndex : inventoryIndex;
        mc.getConnection().sendPacket(new CPacketCreativeInventoryAction(slotId, stack));
    }

    private ItemStack copyStackForGive(ItemStack stack)
    {
        if (stack == null || stack.isEmpty())
        {
            return ItemStack.EMPTY;
        }
        ItemStack copy = stack.copy();
        copy.setCount(1);
        if (stack.hasTagCompound())
        {
            copy.setTagCompound(stack.getTagCompound().copy());
        }
        if (stack.hasDisplayName())
        {
            copy.setStackDisplayName(stack.getDisplayName());
        }
        return copy;
    }

    private void handleHotbarSwap(Minecraft mc)
    {
        if (mc == null || mc.gameSettings == null)
        {
            return;
        }
        if (mc.player == null || mc.playerController == null)
        {
            return;
        }
        if (!mc.playerController.isInCreativeMode() || mc.playerController.getCurrentGameType() != GameType.CREATIVE)
        {
            return;
        }
        if (!enableSecondHotbar)
        {
            if (activeHotbarSet != 0)
            {
                activeHotbarSet = 0;
                loadHotbar(mc, activeHotbarSet);
            }
            for (int i = 0; i < 9; i++)
            {
                hotbarKeyDown[i] = false;
                lastHotbarTapMs[i] = 0L;
            }
            return;
        }

        for (int i = 0; i < 9; i++)
        {
            boolean down = mc.gameSettings.keyBindsHotbar[i].isKeyDown();
            if (down && !hotbarKeyDown[i])
            {
                long now = System.currentTimeMillis();
                if (now - lastHotbarTapMs[i] <= HOTBAR_DOUBLE_TAP_MS)
                {
                    saveCurrentHotbar(mc, activeHotbarSet);
                    activeHotbarSet = 1 - activeHotbarSet;
                    loadHotbar(mc, activeHotbarSet);
                    lastHotbarTapMs[i] = 0L;
                }
                else
                {
                    lastHotbarTapMs[i] = now;
                }
            }
            hotbarKeyDown[i] = down;
        }
    }

    @SubscribeEvent
    public void onGuiMouseInput(GuiScreenEvent.MouseInputEvent.Pre event)
    {
        GuiScreen gui = event.getGui();
        if (!(gui instanceof GuiContainer))
        {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        int mouseX = getScaledMouseX(mc, gui);
        int mouseY = getScaledMouseY(mc, gui);
        if (inputActive)
        {
            if (Mouse.getEventButtonState())
            {
                if (handleSidePanelClick((GuiContainer) gui, mouseX, mouseY))
                {
                    event.setCanceled(true);
                    return;
                }
                if (inputMode == INPUT_MODE_VARIABLE && handleVariableButtonClick(mouseX, mouseY, (GuiContainer) gui))
                {
                    event.setCanceled(true);
                    return;
                }
                if (inputMode == INPUT_MODE_VARIABLE && isSaveVariableButtonClicked(mouseX, mouseY, gui))
                {
                    toggleVariableSavedState();
                    event.setCanceled(true);
                    return;
                }
                if (inputMode == INPUT_MODE_ARRAY && isSaveButtonClicked(mouseX, mouseY, gui))
                {
                    appendArrayMark();
                    event.setCanceled(true);
                    return;
                }
                if (isShiftDown() && handleVariableInsertFromSlot((GuiContainer) gui))
                {
                    event.setCanceled(true);
                    return;
                }
                ensureInputField();
                if (inputField != null)
                {
                    inputField.mouseClicked(mouseX, mouseY, Mouse.getEventButton());
                }
                event.setCanceled(true);
            }
            return;
        }
        if (Mouse.getEventButtonState() && handleSidePanelClick((GuiContainer) gui, mouseX, mouseY))
        {
            event.setCanceled(true);
            return;
        }
        if (codeMenuActive && isCodeMenuScreen(gui))
        {
            if (Mouse.getEventButtonState())
            {
                handleCodeMenuClick((GuiContainer) gui);
                event.setCanceled(true);
            }
            return;
        }
        if (Mouse.getEventButtonState() && Mouse.getEventButton() == 1
            && (Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT))
            && editorModeActive && mc != null && mc.playerController != null
            && mc.playerController.isInCreativeMode()
            && mc.playerController.getCurrentGameType() == GameType.CREATIVE)
        {
            if (handleShiftEditClick((GuiContainer) gui))
            {
                event.setCanceled(true);
                return;
            }
        }
        if (fakeMenuActive)
        {
            if (Mouse.getEventButtonState())
            {
                event.setCanceled(true);
            }
            return;
        }
        if (!editorModeActive || mc == null || mc.playerController == null
            || !mc.playerController.isInCreativeMode()
            || mc.playerController.getCurrentGameType() != GameType.CREATIVE)
        {
            return;
        }
        if (!Mouse.getEventButtonState() || Mouse.getEventButton() != 0)
        {
            return;
        }

        GuiContainer container = (GuiContainer) gui;
        Slot hovered = getSlotUnderMouse(container);
        if (hovered == null || !hovered.getHasStack())
        {
            return;
        }

        ItemStack hoveredStack = hovered.getStack();
        if (isTextGlassPane(hoveredStack))
        {
            if (handleGlassClick(container, hovered, hoveredStack, Items.BOOK, INPUT_MODE_TEXT))
            {
                event.setCanceled(true);
            }
            return;
        }

        if (isNumberGlassPane(hoveredStack))
        {
            if (handleGlassClick(container, hovered, hoveredStack, Items.SLIME_BALL, INPUT_MODE_NUMBER))
            {
                event.setCanceled(true);
            }
            return;
        }

        if (isLocationGlassPane(hoveredStack))
        {
            if (handleGlassClick(container, hovered, hoveredStack, Items.PAPER, INPUT_MODE_LOCATION))
            {
                event.setCanceled(true);
            }
            return;
        }

        if (isVariableGlassPane(hoveredStack))
        {
            if (handleGlassClick(container, hovered, hoveredStack, Items.MAGMA_CREAM, INPUT_MODE_VARIABLE))
            {
                event.setCanceled(true);
            }
            return;
        }

        if (isArrayGlassPane(hoveredStack))
        {
            if (handleGlassClick(container, hovered, hoveredStack, Items.ITEM_FRAME, INPUT_MODE_ARRAY))
            {
                event.setCanceled(true);
            }
        }
    }

    @SubscribeEvent
    public void onGuiKeyInput(GuiScreenEvent.KeyboardInputEvent.Pre event)
    {
        if (!inputActive)
        {
            handleCopyPaste(event);
        }
        if (!inputActive)
        {
            return;
        }
        GuiScreen gui = event.getGui();
        if (!(gui instanceof GuiContainer))
        {
            setInputActive(false);
            return;
        }

        if (!Keyboard.getEventKeyState())
        {
            return;
        }
        int key = Keyboard.getEventKey();
        char ch = Keyboard.getEventCharacter();

        if (key == Keyboard.KEY_ESCAPE)
        {
            setInputActive(false);
            event.setCanceled(true);
            return;
        }
        if (key == Keyboard.KEY_RETURN || key == Keyboard.KEY_NUMPADENTER)
        {
            boolean giveExtra = Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT);
            submitInputText(giveExtra);
            event.setCanceled(true);
            return;
        }
        if (key == Keyboard.KEY_TAB)
        {
            if (Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT))
            {
                submitInputText(false);
            }
            else
            {
                applySuggestion();
            }
            event.setCanceled(true);
            return;
        }

        ensureInputField();
        if (inputField != null)
        {
            inputField.textboxKeyTyped(ch, key);
            if (inputMode == INPUT_MODE_NUMBER)
            {
                inputField.setText(filterNumber(inputField.getText()));
                inputField.setCursorPositionEnd();
            }
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onGuiDraw(GuiScreenEvent.DrawScreenEvent.Post event)
    {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.fontRenderer == null)
        {
            return;
        }
        GuiScreen gui = event.getGui();
        if (!(gui instanceof GuiContainer))
        {
            return;
        }

        GlStateManager.disableLighting();
        GlStateManager.disableDepth();
        GlStateManager.enableBlend();
        GlStateManager.color(1f, 1f, 1f, 1f);

        if (editorModeActive && mc.playerController != null && mc.playerController.isInCreativeMode()
            && mc.playerController.getCurrentGameType() == GameType.CREATIVE)
        {
            drawSidePanels((GuiContainer) gui, event.getMouseX(), event.getMouseY());
        }

        if (!inputActive)
        {
            GlStateManager.enableDepth();
            return;
        }

        int width = event.getGui().width;
        int height = event.getGui().height;
        int boxWidth = Math.min(300, width - 40);
        int boxHeight = 56;
        int x1 = (width - boxWidth) / 2;
        int y1 = height - 86;
        int x2 = x1 + boxWidth;
        int y2 = y1 + boxHeight;
        net.minecraft.client.gui.Gui.drawRect(x1, y1, x2, y2, 0xAA000000);
        String title = inputTitle == null || inputTitle.isEmpty() ? "Input" : inputTitle;
        ensureInputField();
        if (inputField != null)
        {
            inputField.x = x1 + 6;
            inputField.y = y1 + 18;
            inputField.width = boxWidth - 12;
            inputField.height = 12;
            inputField.setEnableBackgroundDrawing(false);
            inputField.drawTextBox();
        }
        String rawLine = getInputText();
        String preview;
        if (inputMode == INPUT_MODE_NUMBER)
        {
            preview = applyColorCodes("&c" + rawLine);
        }
        else if (inputMode == INPUT_MODE_LOCATION)
        {
            preview = applyColorCodes("&b" + rawLine);
        }
        else if (inputMode == INPUT_MODE_VARIABLE || inputMode == INPUT_MODE_ARRAY)
        {
            preview = applyColorCodes("&r" + normalizePlainName(rawLine));
        }
        else
        {
            preview = applyColorCodes(rawLine);
        }
        mc.fontRenderer.drawStringWithShadow("\u00a7f\u00a7l" + title, x1 + 6, y1 + 6, 0xFFFFFF);
        mc.fontRenderer.drawStringWithShadow(preview, x1 + 6, y1 + 30, 0xFFFFFF);
        drawSuggestion(mc, rawLine, x1 + 6, y1 + 18);
        if (inputMode == INPUT_MODE_ARRAY)
        {
            drawSaveButton(mc, x2 - 48, y1 + 6);
        }
        if (inputMode == INPUT_MODE_VARIABLE)
        {
            drawSaveButton(mc, x2 - 48, y1 + 6);
            drawVariableButtons(mc, x1, y1, boxWidth);
        }
        GlStateManager.enableDepth();
    }

    @SubscribeEvent
    public void onGuiOpen(GuiOpenEvent event)
    {
        GuiScreen gui = event.getGui();
        if (gui == null)
        {
            if (allowChestSnapshot && lastClickedChest)
            {
                long now = System.currentTimeMillis();
                pendingChestSnapshot = true;
                pendingChestUntilMs = now + 1000L;
                lastClickedMs = now;
                if (allowChestUntilMs < now + 1500L)
                {
                    allowChestUntilMs = now + 1500L;
                }
            }
            return;
        }
        if (gui instanceof GuiDisconnected)
        {
            saveEntriesIfNeeded();
            saveMenuCacheIfNeeded();
            saveChestIdCachesIfNeeded();
            editorModeActive = false;
            editorModeWasActive = false;
            codeMenuActive = false;
            codeMenuInventory = null;
            setInputActive(false);
            menuCache.clear();
            customMenuCache = null;
            clearEntryCaches();
            chestCaches.clear();
            pendingCacheKey = null;
            fakeMenuActive = false;
            fakeMenuKey = null;
            awaitingCacheSnapshot = false;
            queuedClicks.clear();
            return;
        }
        if (gui instanceof GuiContainer)
        {
            boolean shouldSnapshot = false;
            boolean isPlayerInventory = gui instanceof GuiInventory;
            if (gui instanceof GuiChest)
            {
                if (allowChestSnapshot && lastClickedPos == null)
                {
                    BlockPos targetPos = getTargetedBlockPos();
                    if (targetPos != null)
                    {
                        lastClickedPos = targetPos;
                        lastClickedDim = Minecraft.getMinecraft().world.provider.getDimension();
                        lastClickedLabel = getChestLabel(Minecraft.getMinecraft().world, targetPos);
                        lastClickedMs = System.currentTimeMillis();
                    }
                }
                if (!pendingChestSnapshot && !lastClickedIsSign && lastClickedChest && allowChestSnapshot)
                {
                    pendingChestSnapshot = true;
                    pendingChestUntilMs = System.currentTimeMillis() + 5000L;
                }
            }
            if (pendingCacheKey != null && System.currentTimeMillis() - pendingCacheMs <= MENU_CACHE_ARM_MS)
            {
                shouldSnapshot = true;
            }
            if (captureCustomMenuArmed)
            {
                captureCustomMenuNow = true;
                shouldSnapshot = true;
            }
            if (!shouldSnapshot && editorModeActive && gui instanceof GuiChest && lastClickedChest && allowChestSnapshot)
            {
                shouldSnapshot = true;
            }
            if (!shouldSnapshot && lastClickedPos != null && lastClickedChest && allowChestSnapshot && !isPlayerInventory
                && System.currentTimeMillis() - lastClickedMs < 5000L)
            {
                shouldSnapshot = true;
            }
            awaitingCacheSnapshot = shouldSnapshot;
            if (shouldSnapshot)
            {
                cacheOpenTicks = 0;
            }
            if (editorModeActive && gui instanceof GuiChest)
            {
                scanChestEntries((GuiChest) gui);
            }
            if (gui instanceof GuiShulkerBox || gui.getClass().getSimpleName().toLowerCase(Locale.ROOT).contains("shulker"))
            {
                Minecraft mc = Minecraft.getMinecraft();
                if (mc != null && mc.player != null && mc.player.isSneaking())
                {
                    pendingShulkerEdit = true;
                    pendingShulkerUntilMs = System.currentTimeMillis() + 5000L;
                    if (pendingShulkerPos == null)
                    {
                        pendingShulkerPos = lastClickedPos;
                        pendingShulkerDim = lastClickedDim;
                    }
                }
                if (pendingShulkerEdit)
                {
                    handlePendingShulkerEdit((GuiContainer) gui);
                }
            }
        }
        else
        {
            awaitingCacheSnapshot = false;
        }
    }

    @SubscribeEvent
    public void onRightClickBlock(PlayerInteractEvent.RightClickBlock event)
    {
        if (!event.getWorld().isRemote)
        {
            return;
        }
        if (event.getHand() != EnumHand.MAIN_HAND)
        {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (mc != null && mc.player != null)
        {
            ItemStack heldEarly = mc.player.getHeldItemMainhand();
            ItemStack heldOff = mc.player.getHeldItemOffhand();
            if ((!heldEarly.isEmpty() && heldEarly.getItem() == Items.ARROW)
                || (!heldOff.isEmpty() && heldOff.getItem() == Items.ARROW))
            {
                pendingCacheKey = null;
                cachePathRoot = null;
                captureCustomMenuArmed = false;
                return;
            }
        }
        if (editorModeActive && event.getEntityPlayer() != null && event.getEntityPlayer().isSneaking())
        {
            Block block = event.getWorld().getBlockState(event.getPos()).getBlock();
            TileEntity tile = event.getWorld().getTileEntity(event.getPos());
            if (block instanceof BlockShulkerBox || tile instanceof TileEntityShulkerBox)
            {
                pendingShulkerEdit = true;
                pendingShulkerUntilMs = System.currentTimeMillis() + 5000L;
                pendingShulkerPos = event.getPos();
                pendingShulkerDim = event.getWorld().provider.getDimension();
                pendingShulkerColor = getShulkerColor(event.getWorld(), event.getPos(), tile);
                setActionBar(true, "&eShulker edit armed", 1500L);
            }
        }
        if (editorModeActive && event.getEntityPlayer() != null && event.getEntityPlayer().world != null)
        {
            ensureBlueGlassCached(event.getEntityPlayer().world);
            ItemStack held = event.getEntityPlayer().getHeldItem(event.getHand());
            if (!held.isEmpty() && held.getItem() instanceof net.minecraft.item.ItemBlock)
            {
                BlockPos placePos = event.getPos().offset(event.getFace());
                updateBlueGlassFromPlacement(event.getWorld(), placePos);
            }
        }
        lastClickedPos = event.getPos();
        lastClickedDim = event.getWorld().provider.getDimension();
        lastClickedMs = System.currentTimeMillis();
        Block clickedBlock = event.getWorld().getBlockState(event.getPos()).getBlock();
        TileEntity clickedTile = event.getWorld().getTileEntity(event.getPos());
        boolean isInventory = clickedTile instanceof net.minecraft.inventory.IInventory;
        lastClickedChest = isInventory || clickedBlock == Blocks.CHEST || clickedBlock == Blocks.TRAPPED_CHEST;
        allowChestSnapshot = lastClickedChest;
        if (allowChestSnapshot)
        {
            allowChestUntilMs = System.currentTimeMillis() + 5000L;
            String id = getScoreboardIdLine();
            if (id != null && !id.isEmpty())
            {
                String idKey = chestIdKey(id, lastClickedDim, lastClickedPos);
                ChestCache cached = idKey == null ? null : chestIdCaches.get(idKey);
                if (cached != null)
                {
                    List<ItemStack> copy = new ArrayList<>();
                    for (ItemStack stack : cached.items)
                    {
                        copy.add(stack.isEmpty() ? ItemStack.EMPTY : stack.copy());
                    }
                    chestCaches.put(chestKey(lastClickedDim, lastClickedPos),
                        new ChestCache(lastClickedDim, lastClickedPos, copy, System.currentTimeMillis(),
                            cached.label));
                }
            }
        }
        lastClickedLabel = getChestLabel(event.getWorld(), event.getPos());
        lastClickedIsSign = clickedTile instanceof TileEntitySign;
        if (lastClickedIsSign)
        {
            pendingChestSnapshot = false;
            lastClickedChest = false;
            allowChestSnapshot = false;
        }
        if (!lastClickedChest)
        {
            pendingChestSnapshot = false;
            allowChestSnapshot = false;
        }
        if (lastClickedChest)
        {
            pendingChestSnapshot = true;
            pendingChestUntilMs = System.currentTimeMillis() + 5000L;
        }
        if (!editorModeActive && chestCaches.isEmpty() && testChestHolos.isEmpty() && !testHoloActive)
        {
            return;
        }
        if (mc != null && mc.player != null && mc.playerController != null)
        {
            if (!mc.playerController.isInCreativeMode()
                || mc.playerController.getCurrentGameType() != GameType.CREATIVE)
            {
                return;
            }
            ItemStack held = mc.player.getHeldItemMainhand();
            if (!held.isEmpty() && (held.getItem() == Items.IRON_INGOT || held.getItem() == Items.GOLD_INGOT))
            {
                pendingChestSnapshot = false;
                lastClickedChest = false;
                allowChestSnapshot = false;
                lastClickedPos = null;
                lastClickedLabel = null;
            }
            if (!held.isEmpty() && held.getItem() == Items.REPEATER)
            {
                captureCustomMenuArmed = false;
                captureCustomMenuNow = false;
                pendingCacheKey = null;
                cachePathRoot = null;
                return;
            }
            if (!held.isEmpty() && held.getItem() == Items.IRON_INGOT)
            {
                captureCustomMenuArmed = true;
                setActionBar(true, "&eCache next menu...", 2000L);
                return;
            }
            if (!held.isEmpty() && held.getItem() == Items.GOLD_INGOT)
            {
                return;
            }
            if (!held.isEmpty() && held.getItem() == Items.ARROW)
            {
                pendingCacheKey = null;
                return;
            }
        }
        TileEntity tile = event.getWorld().getTileEntity(event.getPos());
        if (!(tile instanceof TileEntitySign))
        {
            return;
        }
        TileEntitySign sign = (TileEntitySign) tile;
        Minecraft mc2 = Minecraft.getMinecraft();
        boolean shift = mc2 != null && mc2.player != null && mc2.player.isSneaking();
        if (mc2 != null && mc2.player != null)
        {
            ItemStack held2 = mc2.player.getHeldItemMainhand();
            ItemStack heldOff2 = mc2.player.getHeldItemOffhand();
            if ((!held2.isEmpty() && held2.getItem() == Items.ARROW)
                || (!heldOff2.isEmpty() && heldOff2.getItem() == Items.ARROW))
            {
                pendingCacheKey = null;
                cachePathRoot = null;
                return;
            }
        }
        String key = shift ? "__SHIFT_SIGN__" : sign.signText[0].getUnformattedText();
        if (key == null)
        {
            return;
        }
        key = key.trim();
        if (key.isEmpty())
        {
            return;
        }
        cachePathRoot = key;
        pendingCacheKey = key;
        pendingCacheMs = System.currentTimeMillis();
        CachedMenu cached = menuCache.get(key);
        if (cached != null)
        {
            openFakeMenuFromCache(key, cached);
        }
    }

    @SubscribeEvent
    public void onRightClickItem(PlayerInteractEvent.RightClickItem event)
    {
        if (!event.getWorld().isRemote)
        {
            return;
        }
        if (event.getHand() != EnumHand.MAIN_HAND)
        {
            return;
        }
        if (!editorModeActive)
        {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (mc != null && mc.player != null && mc.playerController != null)
        {
            if (!mc.playerController.isInCreativeMode()
                || mc.playerController.getCurrentGameType() != GameType.CREATIVE)
            {
                return;
            }
            ItemStack held = mc.player.getHeldItemMainhand();
            if (!held.isEmpty() && held.getItem() == Items.IRON_INGOT)
            {
                captureCustomMenuArmed = true;
                setActionBar(true, "&eCache next menu...", 2000L);
            }
        }
    }

    @SubscribeEvent
    public void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event)
    {
        if (!event.getWorld().isRemote)
        {
            return;
        }
        BlockPos pos = event.getPos();
        clearChestCacheAt(pos);
        clearChestCacheAt(pos.up());
        clearChestCacheAt(pos.up().south());
        clearChestCacheAt(pos.up().north());
        clearChestCacheAt(pos.up().east());
        clearChestCacheAt(pos.up().west());
    }

    private void handleMenuCacheTick(Minecraft mc)
    {
        if (mc == null)
        {
            return;
        }
        if (mc.currentScreen == null)
        {
            if (fakeMenuActive)
            {
                fakeMenuActive = false;
                fakeMenuKey = null;
                queuedClicks.clear();
            }
            typePickerActive = false;
            if (codeMenuActive)
            {
                codeMenuActive = false;
                codeMenuInventory = null;
            }
            return;
        }
        if (!(mc.currentScreen instanceof GuiContainer))
        {
            if (fakeMenuActive)
            {
                fakeMenuActive = false;
                fakeMenuKey = null;
                queuedClicks.clear();
            }
            typePickerActive = false;
            if (codeMenuActive)
            {
                codeMenuActive = false;
                codeMenuInventory = null;
            }
            return;
        }
        if (awaitingCacheSnapshot && mc.currentScreen instanceof GuiContainer)
        {
            cacheOpenTicks++;
            if (cacheOpenTicks >= 2)
            {
                snapshotCurrentContainer((GuiContainer) mc.currentScreen);
                awaitingCacheSnapshot = false;
            }
        }
        if (codeMenuActive)
        {
            if (mc.currentScreen instanceof GuiChest)
            {
                IInventory inv = getChestInventory((GuiChest) mc.currentScreen);
                if (inv != codeMenuInventory)
                {
                    codeMenuActive = false;
                    codeMenuInventory = null;
                }
            }
            else
            {
                codeMenuActive = false;
                codeMenuInventory = null;
            }
        }
    }

    private void handleCodeMenuKeys(Minecraft mc)
    {
        if (mc == null || mc.player == null || mc.playerController == null)
        {
            return;
        }
        if (!editorModeActive || !mc.playerController.isInCreativeMode()
            || mc.playerController.getCurrentGameType() != GameType.CREATIVE)
        {
            return;
        }
        boolean down = (keyOpenCodeMenu != null && keyOpenCodeMenu.isKeyDown())
            || (keyOpenCodeMenuAlt != null && keyOpenCodeMenuAlt.isKeyDown());
        if (down && !codeMenuKeyDown)
        {
            openCodeMenu(mc);
        }
        codeMenuKeyDown = down;
    }

    private void handleTpForwardKey(Minecraft mc)
    {
        if (mc == null || mc.player == null || mc.playerController == null)
        {
            return;
        }
        boolean down = keyTpForward != null && keyTpForward.isKeyDown();
        if (down && !tpForwardKeyDown)
        {
            tpScrollSteps = 0;
        }
        if (down)
        {
            if (!editorModeActive || !mc.playerController.isInCreativeMode()
                || mc.playerController.getCurrentGameType() != GameType.CREATIVE)
            {
                setActionBar(false, "&cDev+Creative only.", 2000L);
            }
            else
            {
                int wheel = Mouse.getDWheel();
                if (wheel != 0)
                {
                    tpScrollSteps += wheel > 0 ? 1 : -1;
                }
                boolean plus = Keyboard.isKeyDown(Keyboard.KEY_ADD) || Keyboard.isKeyDown(Keyboard.KEY_EQUALS);
                boolean minus = Keyboard.isKeyDown(Keyboard.KEY_SUBTRACT) || Keyboard.isKeyDown(Keyboard.KEY_MINUS);
                if (plus)
                {
                    mc.player.setPosition(mc.player.posX, mc.player.posY + 10.0, mc.player.posZ);
                }
                else if (minus)
                {
                    mc.player.setPosition(mc.player.posX, mc.player.posY - 10.0, mc.player.posZ);
                }
            }
        }
        if (!down && tpForwardKeyDown)
        {
            if (!editorModeActive || !mc.playerController.isInCreativeMode()
                || mc.playerController.getCurrentGameType() != GameType.CREATIVE)
            {
                setActionBar(false, "&cDev+Creative only.", 2000L);
            }
            else
            {
                if (tpScrollSteps != 0)
                {
                    tpScrollDir = tpScrollSteps > 0 ? 1 : -1;
                    tpScrollQueue = Math.min(50, Math.abs(tpScrollSteps));
                    tpScrollNextMs = System.currentTimeMillis() + 300L;
                }
                else
                {
                    Vec3d look = mc.player.getLookVec();
                    double step = 0.5;
                    for (int i = 0; i < 9; i++)
                    {
                        mc.player.setPosition(
                            mc.player.posX + look.x * step,
                            mc.player.posY + look.y * step + 0.05,
                            mc.player.posZ + look.z * step
                        );
                    }
                }
                mc.player.motionX = 0.0;
                mc.player.motionY = 0.0;
                mc.player.motionZ = 0.0;
            }
            tpScrollSteps = 0;
        }
        tpForwardKeyDown = down;
    }

    private void handleTpScrollQueue(Minecraft mc)
    {
        if (tpScrollQueue <= 0)
        {
            return;
        }
        if (mc == null || mc.player == null || mc.playerController == null)
        {
            tpScrollQueue = 0;
            return;
        }
        if (!editorModeActive || !mc.playerController.isInCreativeMode()
            || mc.playerController.getCurrentGameType() != GameType.CREATIVE)
        {
            tpScrollQueue = 0;
            return;
        }
        long now = System.currentTimeMillis();
        if (now < tpScrollNextMs)
        {
            return;
        }
        double delta = tpScrollDir * 10.0;
        mc.player.setPosition(mc.player.posX, mc.player.posY + delta, mc.player.posZ);
        mc.player.motionX = 0.0;
        mc.player.motionY = 0.0;
        mc.player.motionZ = 0.0;
        tpScrollQueue--;
        tpScrollNextMs = now + 300L;
    }

    private void buildTpPathQueue(World world, double sx, double sy, double sz, double tx, double ty, double tz)
    {
        if (world == null)
        {
            return;
        }
        if (!isTpTargetValid(world, tx, ty, tz))
        {
            tpPathQueue.clear();
            setActionBar(true, "&cTarget outside world border/height", 2500L);
            return;
        }
        tpPathQueue.clear();
        double dx = tx - sx;
        double dy = ty - sy;
        double dz = tz - sz;
        int safety = 0;
        for (int axis = 0; axis < 3; axis++)
        {
            double delta = axis == 0 ? dx : axis == 1 ? dy : dz;
            while (Math.abs(delta) >= 0.001 && safety < 5000)
            {
                double step = Math.min(10.0, Math.abs(delta)) * Math.signum(delta);
                double[] move = new double[] {0.0, 0.0, 0.0};
                if (axis == 0)
                {
                    move[0] = step;
                    dx -= step;
                    delta = dx;
                }
                else if (axis == 1)
                {
                    move[1] = step;
                    dy -= step;
                    delta = dy;
                }
                else
                {
                    move[2] = step;
                    dz -= step;
                    delta = dz;
                }
                tpPathQueue.add(move);
                safety++;
            }
        }
        if (tpPathQueue.isEmpty())
        {
            setActionBar(true, "&eTP path empty", 1500L);
            return;
        }
        tpPathNextMs = System.currentTimeMillis() + 300L;
        setActionBar(true, "&aTP path steps=" + tpPathQueue.size(), 2000L);
    }

    private boolean isTpTargetValid(World world, double tx, double ty, double tz)
    {
        if (world == null)
        {
            return false;
        }
        if (ty < 0.0 || ty > 255.0)
        {
            return false;
        }
        BlockPos pos = new BlockPos((int) Math.floor(tx), (int) Math.floor(ty), (int) Math.floor(tz));
        return world.getWorldBorder() == null || world.getWorldBorder().contains(pos);
    }

    private void handleTpPathQueue(Minecraft mc)
    {
        if (tpPathQueue.isEmpty())
        {
            return;
        }
        if (mc == null || mc.player == null || mc.playerController == null)
        {
            tpPathQueue.clear();
            return;
        }
        if (!editorModeActive || !mc.playerController.isInCreativeMode()
            || mc.playerController.getCurrentGameType() != GameType.CREATIVE)
        {
            tpPathQueue.clear();
            return;
        }
        long now = System.currentTimeMillis();
        if (now < tpPathNextMs)
        {
            return;
        }
        double[] step = tpPathQueue.poll();
        if (step == null)
        {
            return;
        }
        mc.player.setPosition(mc.player.posX + step[0], mc.player.posY + step[1], mc.player.posZ + step[2]);
        mc.player.motionX = 0.0;
        mc.player.motionY = 0.0;
        mc.player.motionZ = 0.0;
        tpPathNextMs = now + 300L;
    }

    private void handleCopyPaste(GuiScreenEvent.KeyboardInputEvent.Pre event)
    {
        if (event == null || event.getGui() == null)
        {
            return;
        }
        if (!(event.getGui() instanceof GuiContainer))
        {
            return;
        }
        if (!Keyboard.getEventKeyState())
        {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.player == null || mc.playerController == null)
        {
            return;
        }
        if (!editorModeActive || !mc.playerController.isInCreativeMode()
            || mc.playerController.getCurrentGameType() != GameType.CREATIVE)
        {
            return;
        }
        boolean ctrl = Keyboard.isKeyDown(Keyboard.KEY_LCONTROL) || Keyboard.isKeyDown(Keyboard.KEY_RCONTROL);
        if (!ctrl)
        {
            return;
        }
        int key = Keyboard.getEventKey();
        GuiContainer gui = (GuiContainer) event.getGui();
        if (key == Keyboard.KEY_C)
        {
            copyFromContainer(gui);
            event.setCanceled(true);
        }
        else if (key == Keyboard.KEY_V)
        {
            pasteToContainer(gui);
            event.setCanceled(true);
        }
    }

    private void copyFromContainer(GuiContainer gui)
    {
        copiedSlots.clear();
        if (gui == null)
        {
            return;
        }
        Container container = gui.inventorySlots;
        for (Slot slot : container.inventorySlots)
        {
            if (slot == null || !slot.getHasStack())
            {
                continue;
            }
            ItemStack stack = slot.getStack();
            if (isCodeMenuItem(stack))
            {
                copiedSlots.add(new CopiedSlot(slot.slotNumber, stack.copy()));
            }
        }
        setActionBar(true, "&aCopied " + copiedSlots.size(), 1500L);
    }

    private void pasteToContainer(GuiContainer gui)
    {
        if (copiedSlots.isEmpty() || gui == null)
        {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.player == null || mc.playerController == null)
        {
            return;
        }
        Container container = gui.inventorySlots;
        for (CopiedSlot entry : copiedSlots)
        {
            Slot target = findSlotByNumber(container, entry.slotNumber);
            if (target != null && !target.getHasStack())
            {
                placeInContainerSlot(mc, container, target.slotNumber, entry.stack.copy());
                continue;
            }
            Slot empty = findFirstEmptySlot(container);
            if (empty != null)
            {
                placeInContainerSlot(mc, container, empty.slotNumber, entry.stack.copy());
            }
        }
        setActionBar(true, "&aPasted " + copiedSlots.size(), 1500L);
    }

    private void exportGuiToClipboard(GuiContainer gui, boolean includeRaw, boolean includeClean)
    {
        if (gui == null)
        {
            return;
        }
        ExportResult raw = includeRaw ? buildGuiExportText(gui, true) : null;
        ExportResult clean = includeClean ? buildGuiExportText(gui, false) : null;
        int count = raw != null ? raw.itemCount : (clean != null ? clean.itemCount : 0);
        StringBuilder out = new StringBuilder();
        if (raw != null && clean != null)
        {
            out.append("RAW:\n").append(raw.text).append("\n\nCLEAN:\n").append(clean.text);
        }
        else if (raw != null)
        {
            out.append(raw.text);
        }
        else if (clean != null)
        {
            out.append(clean.text);
        }
        GuiScreen.setClipboardString(out.toString());
        setActionBar(true, "&aGUI export: " + count + " items", 2000L);
    }

    private ExportResult buildGuiExportText(GuiContainer gui, boolean keepColors)
    {
        StringBuilder sb = new StringBuilder();
        int count = 0;
        if (gui == null || gui.inventorySlots == null)
        {
            return new ExportResult("", 0);
        }
        Container container = gui.inventorySlots;
        for (Slot slot : container.inventorySlots)
        {
            if (slot == null || !slot.getHasStack())
            {
                continue;
            }
            ItemStack stack = slot.getStack();
            if (stack.isEmpty())
            {
                continue;
            }
            String name = stack.getDisplayName();
            if (!keepColors)
            {
                name = TextFormatting.getTextWithoutFormattingCodes(name);
            }
            if (name == null)
            {
                name = "";
            }
            ResourceLocation id = stack.getItem() != null ? stack.getItem().getRegistryName() : null;
            String idText = id != null ? id.toString() : "unknown";
            sb.append("[(").append(idText).append(") ").append(name);
            if (stack.getCount() > 1)
            {
                sb.append(" x").append(stack.getCount());
            }
            sb.append("]\n");
            for (String loreLine : getStackLore(stack, keepColors))
            {
                sb.append(loreLine).append("\n");
            }
            sb.append("\n");
            count++;
        }
        return new ExportResult(sb.toString().trim(), count);
    }

    private List<String> getStackLore(ItemStack stack, boolean keepColors)
    {
        List<String> out = new ArrayList<>();
        if (stack == null || stack.isEmpty())
        {
            return out;
        }
        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null || !tag.hasKey("display", 10))
        {
            return out;
        }
        NBTTagCompound display = tag.getCompoundTag("display");
        if (!display.hasKey("Lore", 9))
        {
            return out;
        }
        NBTTagList lore = display.getTagList("Lore", 8);
        for (int i = 0; i < lore.tagCount(); i++)
        {
            String line = lore.getStringTagAt(i);
            if (!keepColors)
            {
                line = TextFormatting.getTextWithoutFormattingCodes(line);
            }
            out.add(line == null ? "" : line);
        }
        return out;
    }

    private Slot findFirstEmptySlot(Container container)
    {
        if (container == null)
        {
            return null;
        }
        for (Slot slot : container.inventorySlots)
        {
            if (slot != null && !slot.getHasStack())
            {
                return slot;
            }
        }
        return null;
    }

    private boolean isCodeMenuItem(ItemStack stack)
    {
        if (stack == null || stack.isEmpty())
        {
            return false;
        }
        return stack.getItem() == Items.BOOK
            || stack.getItem() == Items.SLIME_BALL
            || stack.getItem() == Items.MAGMA_CREAM
            || stack.getItem() == Items.ITEM_FRAME
            || stack.getItem() == Items.PAPER
            || stack.getItem() == Items.GLASS_BOTTLE
            || stack.getItem() == Items.APPLE
            || stack.getItem() == Items.NETHER_STAR
            || stack.getItem() == Items.SHULKER_SHELL
            || stack.getItem() == Items.PRISMARINE_SHARD;
    }

    private boolean isCodeMenuScreen(GuiScreen gui)
    {
        if (!(gui instanceof GuiChest))
        {
            return false;
        }
        if (!codeMenuActive || codeMenuInventory == null)
        {
            return false;
        }
        IInventory inv = getChestInventory((GuiChest) gui);
        return inv == codeMenuInventory;
    }

    private void handleCodeMenuClick(GuiContainer gui)
    {
        Slot hovered = getSlotUnderMouse(gui);
        if (hovered == null || !hovered.getHasStack())
        {
            return;
        }
        ItemStack stack = hovered.getStack();
        if (stack.isEmpty())
        {
            return;
        }

        if (stack.getItem() == Items.SLIME_BALL)
        {
            startGiveInput(stack.copy(), INPUT_MODE_NUMBER, "Number");
            return;
        }
        if (stack.getItem() == Items.MAGMA_CREAM)
        {
            startGiveInput(stack.copy(), INPUT_MODE_VARIABLE, "Variable");
            return;
        }
        if (stack.getItem() == Items.ITEM_FRAME)
        {
            startGiveInput(stack.copy(), INPUT_MODE_ARRAY, "Array");
            return;
        }
        if (stack.getItem() == Items.BOOK)
        {
            startGiveInput(stack.copy(), INPUT_MODE_TEXT, "Text");
            return;
        }
        if (stack.getItem() == Items.PAPER)
        {
            startGiveInput(stack.copy(), INPUT_MODE_LOCATION, "Location");
            return;
        }

        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.player == null || mc.playerController == null)
        {
            return;
        }
        if (!mc.playerController.isInCreativeMode() || mc.playerController.getCurrentGameType() != GameType.CREATIVE)
        {
            setActionBar(false, "&cCreative only.", 2000L);
            return;
        }
        ItemStack give = copyStackForGive(stack);
        if (give.isEmpty())
        {
            return;
        }
        giveItemToHotbar(mc, give);
    }

    private void openCodeMenu(Minecraft mc)
    {
        if (mc == null || mc.player == null)
        {
            return;
        }
        IInventory inv;
        if (customMenuCache != null)
        {
            InventoryBasic custom = new InventoryBasic(customMenuCache.title, true, customMenuCache.size);
            for (int i = 0; i < customMenuCache.size; i++)
            {
                ItemStack stack = customMenuCache.items.get(i);
                custom.setInventorySlotContents(i, stack.isEmpty() ? ItemStack.EMPTY : stack.copy());
            }
            inv = custom;
        }
        else
        {
            InventoryBasic base = new InventoryBasic(CODE_MENU_TITLE, true, 18);
            List<ItemStack> items = buildDefaultCodeMenuItems();
            for (int i = 0; i < items.size() && i < 18; i++)
            {
                base.setInventorySlotContents(i, items.get(i));
            }
            inv = base;
        }
        mc.displayGuiScreen(new GuiChest(mc.player.inventory, inv));
        codeMenuActive = true;
        codeMenuInventory = inv;
    }

    private List<ItemStack> buildDefaultCodeMenuItems()
    {
        List<ItemStack> items = new ArrayList<>();
        items.add(new ItemStack(Items.BOOK));
        items.add(new ItemStack(Items.SLIME_BALL));
        items.add(new ItemStack(Items.PAPER));
        items.add(new ItemStack(Items.GLASS_BOTTLE));
        items.add(new ItemStack(Items.MAGMA_CREAM));
        items.add(new ItemStack(Items.APPLE));
        items.add(new ItemStack(Items.NETHER_STAR));
        items.add(new ItemStack(Items.ITEM_FRAME));
        items.add(new ItemStack(Items.SHULKER_SHELL));
        items.add(new ItemStack(Items.PRISMARINE_SHARD));
        return items;
    }

    private void openFakeMenuFromCache(String key, CachedMenu cached)
    {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.player == null)
        {
            return;
        }
        InventoryBasic inv = new InventoryBasic(cached.title, true, cached.size);
        for (int i = 0; i < cached.size; i++)
        {
            ItemStack stack = cached.items.get(i);
            inv.setInventorySlotContents(i, stack.isEmpty() ? ItemStack.EMPTY : stack.copy());
        }
        mc.displayGuiScreen(new GuiChest(mc.player.inventory, inv));
        fakeMenuActive = true;
        fakeMenuKey = key;
        queuedClicks.clear();
    }

    private void snapshotCurrentMenu(GuiChest chest)
    {
        if (chest == null)
        {
            return;
        }
        IInventory inv = getChestInventory(chest);
        if (inv == null)
        {
            return;
        }
        int size = inv.getSizeInventory();
        List<ItemStack> items = new ArrayList<>();
        for (int i = 0; i < size; i++)
        {
            ItemStack stack = inv.getStackInSlot(i);
            items.add(stack.isEmpty() ? ItemStack.EMPTY : stack.copy());
        }
        String title = getGuiTitle(chest);
        String hash = buildMenuHash(items);
        CachedMenu menu = new CachedMenu(title, size, items, hash);
        if (pendingCacheKey != null)
        {
            menuCache.put(pendingCacheKey, menu);
            cachePathRoot = pendingCacheKey;
            if (debugUi)
            {
                setActionBar(true, "&aCached " + pendingCacheKey, 1500L);
            }
            pendingCacheKey = null;
            menuCacheDirty = true;
        }
        if (captureCustomMenuNow)
        {
            customMenuCache = menu;
            captureCustomMenuNow = false;
            captureCustomMenuArmed = false;
            setActionBar(true, "&aCode menu cached.", 2000L);
            menuCacheDirty = true;
        }
        if (inv instanceof TileEntityChest)
        {
            TileEntityChest te = (TileEntityChest) inv;
            cacheChestInventory(te, items);
            lastClickedPos = te.getPos();
            lastClickedDim = te.getWorld().provider.getDimension();
            lastClickedMs = System.currentTimeMillis();
        }
        else if (lastClickedPos != null && lastClickedChest && !lastClickedIsSign
            && System.currentTimeMillis() - lastClickedMs < 5000L)
        {
            cacheChestInventoryAt(lastClickedDim, lastClickedPos, items, lastClickedLabel);
        }

        if (fakeMenuActive && fakeMenuKey != null)
        {
            CachedMenu cached = menuCache.get(fakeMenuKey);
            if (cached != null && cached.hash.equals(hash))
            {
                replayQueuedClicks(Minecraft.getMinecraft());
            }
            queuedClicks.clear();
            fakeMenuActive = false;
            fakeMenuKey = null;
        }
    }

    private void snapshotCurrentContainer(GuiContainer gui)
    {
        if (gui == null)
        {
            return;
        }
        if (gui instanceof GuiInventory)
        {
            return;
        }
        if (gui instanceof GuiChest)
        {
            snapshotCurrentMenu((GuiChest) gui);
            return;
        }
        Container container = gui.inventorySlots;
        if (container == null)
        {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.player == null)
        {
            return;
        }
        if (mc.player.openContainer != null && mc.player.openContainer == mc.player.inventoryContainer)
        {
            return;
        }
        List<ItemStack> items = new ArrayList<>();
        int nonPlayerSlots = 0;
        for (Slot slot : container.inventorySlots)
        {
            if (slot == null)
            {
                continue;
            }
            if (slot.inventory == mc.player.inventory)
            {
                continue;
            }
            nonPlayerSlots++;
            ItemStack stack = slot.getStack();
            items.add(stack.isEmpty() ? ItemStack.EMPTY : stack.copy());
        }
        if (nonPlayerSlots == 0)
        {
            return;
        }
        String title = "Container";
        String hash = buildMenuHash(items);
        CachedMenu menu = new CachedMenu(title, items.size(), items, hash);
        if (pendingCacheKey != null)
        {
            menuCache.put(pendingCacheKey, menu);
            cachePathRoot = pendingCacheKey;
            if (debugUi)
            {
                setActionBar(true, "&aCached " + pendingCacheKey, 1500L);
            }
            pendingCacheKey = null;
            menuCacheDirty = true;
        }
        if (captureCustomMenuNow)
        {
            customMenuCache = menu;
            captureCustomMenuNow = false;
            captureCustomMenuArmed = false;
            setActionBar(true, "&aCode menu cached.", 2000L);
            menuCacheDirty = true;
        }
        if (lastClickedPos != null && lastClickedChest && System.currentTimeMillis() - lastClickedMs < 5000L)
        {
            cacheChestInventoryAt(lastClickedDim, lastClickedPos, items, lastClickedLabel);
        }
    }

    private void snapshotOpenContainer(Container container)
    {
        if (container == null)
        {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.player == null)
        {
            return;
        }
        if (container == mc.player.inventoryContainer)
        {
            return;
        }
        List<ItemStack> items = new ArrayList<>();
        int nonPlayerSlots = 0;
        for (Slot slot : container.inventorySlots)
        {
            if (slot == null)
            {
                continue;
            }
            if (slot.inventory == mc.player.inventory)
            {
                continue;
            }
            nonPlayerSlots++;
            ItemStack stack = slot.getStack();
            items.add(stack.isEmpty() ? ItemStack.EMPTY : stack.copy());
        }
        if (nonPlayerSlots == 0)
        {
            return;
        }
        if (lastClickedPos != null && lastClickedChest && System.currentTimeMillis() - lastClickedMs < 5000L)
        {
            cacheChestInventoryAt(lastClickedDim, lastClickedPos, items, lastClickedLabel);
        }
    }

    private void queueFakeMenuClick(GuiContainer gui)
    {
        if (gui == null)
        {
            return;
        }
        Slot hovered = getSlotUnderMouse(gui);
        if (hovered == null)
        {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (mc != null && mc.player != null && hovered.inventory == mc.player.inventory)
        {
            return;
        }
        int button = Mouse.getEventButton();
        ClickType type = (Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT))
            ? ClickType.QUICK_MOVE : ClickType.PICKUP;
        queuedClicks.add(new ClickAction(hovered.slotNumber, button, type));
    }

    private void replayQueuedClicks(Minecraft mc)
    {
        if (mc == null || mc.player == null || mc.playerController == null)
        {
            return;
        }
        Container container = mc.player.openContainer;
        if (container == null)
        {
            return;
        }
        queuedClicks.clear();
    }

    private void updateCachePathFromItem(ItemStack stack)
    {
        if (stack == null || stack.isEmpty() || cachePathRoot == null)
        {
            return;
        }
        String key = buildItemKey(stack);
        pendingCacheKey = cachePathRoot + ">" + key;
        pendingCacheMs = System.currentTimeMillis();
        awaitingCacheSnapshot = true;
        cacheOpenTicks = 0;
        CachedMenu cached = menuCache.get(pendingCacheKey);
        if (cached != null)
        {
            openFakeMenuFromCache(pendingCacheKey, cached);
        }
    }

    private void cacheChestInventory(TileEntityChest te, List<ItemStack> items)
    {
        if (te == null || te.getPos() == null || te.getWorld() == null)
        {
            return;
        }
        BlockPos pos = te.getPos();
        int dim = te.getWorld().provider.getDimension();
        String key = chestKey(dim, pos);
        String label = getChestLabel(te.getWorld(), pos);
        List<ItemStack> copy = new ArrayList<>();
        for (ItemStack stack : items)
        {
            copy.add(stack.isEmpty() ? ItemStack.EMPTY : stack.copy());
        }
        chestCaches.put(key, new ChestCache(dim, pos, copy, System.currentTimeMillis(), label));
        if (debugUi)
        {
            setActionBar(true, "&aChest cached " + pos.getX() + "," + pos.getY() + "," + pos.getZ(), 1500L);
        }
        updateChestIdCache(dim, pos, copy, label);
    }

    private void cacheChestInventoryAt(int dim, BlockPos pos, List<ItemStack> items, String labelOverride)
    {
        if (pos == null)
        {
            return;
        }
        String key = chestKey(dim, pos);
        String label = labelOverride;
        Minecraft mc = Minecraft.getMinecraft();
        if ((label == null || label.trim().isEmpty()) && mc != null && mc.world != null
            && mc.world.provider.getDimension() == dim)
        {
            label = getChestLabel(mc.world, pos);
        }
        List<ItemStack> copy = new ArrayList<>();
        for (ItemStack stack : items)
        {
            copy.add(stack.isEmpty() ? ItemStack.EMPTY : stack.copy());
        }
        chestCaches.put(key, new ChestCache(dim, pos, copy, System.currentTimeMillis(), label));
        if (debugUi)
        {
            setActionBar(true, "&aChest cached " + pos.getX() + "," + pos.getY() + "," + pos.getZ(), 1500L);
        }
        updateChestIdCache(dim, pos, copy, label);
    }

    private void updateChestIdCache(int dim, BlockPos pos, List<ItemStack> items, String label)
    {
        String id = getScoreboardIdLine();
        if (id == null || id.trim().isEmpty())
        {
            return;
        }
        String key = chestIdKey(id, dim, pos);
        if (key == null)
        {
            return;
        }
        List<ItemStack> copy = new ArrayList<>();
        for (ItemStack stack : items)
        {
            copy.add(stack.isEmpty() ? ItemStack.EMPTY : stack.copy());
        }
        ChestCache cached = new ChestCache(dim, pos, copy, System.currentTimeMillis(), label);
        chestIdCaches.put(key, cached);
        chestIdDirty = true;
        if (editorModeActive && dim == Minecraft.getMinecraft().world.provider.getDimension())
        {
            ensureChestCaches(dim);
        }
    }

    private void clearChestClickState()
    {
        pendingChestSnapshot = false;
        lastClickedChest = false;
        allowChestSnapshot = false;
        allowChestUntilMs = 0L;
        lastClickedPos = null;
        lastClickedLabel = null;
        lastClickedIsSign = false;
    }

    private String getChestLabel(World world, BlockPos chestPos)
    {
        if (world == null || chestPos == null)
        {
            return null;
        }
        BlockPos signPos = chestPos.add(0, -1, -1);
        TileEntity te = world.getTileEntity(signPos);
        if (!(te instanceof TileEntitySign))
        {
            return null;
        }
        TileEntitySign sign = (TileEntitySign) te;
        String line = sign.signText[1].getUnformattedText();
        if (line == null)
        {
            return null;
        }
        return line.trim();
    }

    private void clearChestCacheAt(BlockPos pos)
    {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.world == null || pos == null)
        {
            return;
        }
        String key = chestKey(mc.world.provider.getDimension(), pos);
        chestCaches.remove(key);
    }

    private String chestKey(int dim, BlockPos pos)
    {
        return dim + ":" + pos.getX() + ":" + pos.getY() + ":" + pos.getZ();
    }

    private String chestIdKey(String id, int dim, BlockPos pos)
    {
        if (id == null || id.trim().isEmpty() || pos == null)
        {
            return null;
        }
        return id.trim() + "|" + dim + ":" + pos.getX() + ":" + pos.getY() + ":" + pos.getZ();
    }

    private void ensureChestCaches(int dim)
    {
        String id = getScoreboardIdLine();
        if (id == null || id.trim().isEmpty())
        {
            return;
        }
        String idNeedle = id.trim();
        for (Map.Entry<String, ChestCache> entry : chestIdCaches.entrySet())
        {
            ChestCache cache = entry.getValue();
            if (cache == null || cache.pos == null || cache.dim != dim)
            {
                continue;
            }
            String keyId = entry.getKey();
            int pipe = keyId == null ? -1 : keyId.indexOf('|');
            if (pipe > 0)
            {
                String cacheId = keyId.substring(0, pipe);
                if (!idNeedle.contains(cacheId))
                {
                    continue;
                }
            }
            String key = chestKey(cache.dim, cache.pos);
            if (!chestCaches.containsKey(key))
            {
                List<ItemStack> copy = new ArrayList<>();
                for (ItemStack stack : cache.items)
                {
                    copy.add(stack.isEmpty() ? ItemStack.EMPTY : stack.copy());
                }
                chestCaches.put(key, new ChestCache(cache.dim, cache.pos, copy, System.currentTimeMillis(),
                    cache.label));
            }
        }
    }

    private String buildItemKey(ItemStack stack)
    {
        if (stack == null || stack.isEmpty())
        {
            return "empty";
        }
        ResourceLocation id = stack.getItem().getRegistryName();
        StringBuilder sb = new StringBuilder();
        sb.append(id == null ? "" : id.toString()).append(":");
        sb.append(stack.getMetadata()).append(":");
        sb.append(stack.getCount()).append(":");
        sb.append(escapeJson(stack.getDisplayName())).append(":");
        if (stack.hasTagCompound())
        {
            sb.append(stack.getTagCompound().toString());
        }
        return Integer.toHexString(sb.toString().hashCode());
    }

    private IInventory getChestInventory(GuiChest chest)
    {
        if (chest == null)
        {
            return null;
        }
        try
        {
            if (lowerChestField == null)
            {
                try
                {
                    lowerChestField = GuiChest.class.getDeclaredField("lowerChestInventory");
                }
                catch (NoSuchFieldException e)
                {
                    lowerChestField = GuiChest.class.getDeclaredField("field_147015_w");
                }
                lowerChestField.setAccessible(true);
            }
            Object value = lowerChestField.get(chest);
            return value instanceof IInventory ? (IInventory) value : null;
        }
        catch (Exception e)
        {
            return null;
        }
    }

    private String getGuiTitle(GuiChest chest)
    {
        IInventory inv = getChestInventory(chest);
        if (inv == null || inv.getDisplayName() == null)
        {
            return "";
        }
        return inv.getDisplayName().getUnformattedText();
    }

    private String buildMenuHash(List<ItemStack> items)
    {
        StringBuilder sb = new StringBuilder();
        for (ItemStack stack : items)
        {
            if (stack == null || stack.isEmpty())
            {
                sb.append("empty|");
                continue;
            }
            ResourceLocation id = stack.getItem().getRegistryName();
            sb.append(id == null ? "" : id.toString()).append(":");
            sb.append(stack.getMetadata()).append(":");
            sb.append(stack.getCount()).append(":");
            sb.append(escapeJson(stack.getDisplayName())).append(":");
            if (stack.hasTagCompound())
            {
                sb.append(stack.getTagCompound().toString());
            }
            sb.append("|");
        }
        return Integer.toHexString(sb.toString().hashCode());
    }

    private Slot getSlotUnderMouse(GuiContainer gui)
    {
        if (gui == null)
        {
            return null;
        }
        try
        {
            if (getSlotUnderMouseMethod == null)
            {
                getSlotUnderMouseMethod = GuiContainer.class.getDeclaredMethod("getSlotUnderMouse");
                getSlotUnderMouseMethod.setAccessible(true);
            }
            Object result = getSlotUnderMouseMethod.invoke(gui);
            return result instanceof Slot ? (Slot) result : null;
        }
        catch (Exception e)
        {
            return null;
        }
    }

    private Slot findSlotAbove(GuiContainer gui, Slot base)
    {
        if (gui == null || base == null)
        {
            return null;
        }
        Container container = gui.inventorySlots;
        int targetX = base.xPos;
        int targetY = base.yPos - 18;
        for (Slot slot : container.inventorySlots)
        {
            if (slot.xPos == targetX && slot.yPos == targetY)
            {
                return slot;
            }
        }
        return null;
    }

    private Slot resolveInputTarget(GuiContainer gui, Slot base)
    {
        return findSlotAbove(gui, base);
    }

    private String getModeTitle(int mode, String fallback)
    {
        if (mode == INPUT_MODE_NUMBER)
        {
            return "Number";
        }
        if (mode == INPUT_MODE_VARIABLE)
        {
            return "Variable";
        }
        if (mode == INPUT_MODE_ARRAY)
        {
            return "Array";
        }
        if (mode == INPUT_MODE_LOCATION)
        {
            return "Location";
        }
        if (mode == INPUT_MODE_TEXT)
        {
            return "Text";
        }
        return fallback == null || fallback.isEmpty() ? "Input" : fallback;
    }

    private boolean handleGlassClick(GuiContainer container, Slot hovered, ItemStack hoveredStack,
        net.minecraft.item.Item allowedItem, int fallbackMode)
    {
        Slot target = findCandidateSlot(container, hovered, allowedItem, fallbackMode);
        if (target == null)
        {
            return false;
        }
        int mode = fallbackMode;
        ItemStack targetStack = target.getStack();
        if (target.getHasStack())
        {
            int actualMode = getModeForItem(targetStack);
            if (actualMode >= 0)
            {
                mode = actualMode;
            }
        }
        ItemStack template = target.getHasStack() ? targetStack.copy() : templateForMode(mode);
        if (template.isEmpty())
        {
            template = templateForMode(mode);
        }
        String preset = target.getHasStack() ? extractEntryText(targetStack, mode) : "";
        String title = getModeTitle(mode, TextFormatting.getTextWithoutFormattingCodes(hoveredStack.getDisplayName()));
        startSlotInput(container, target, template, mode, preset, title);
        quickApplyWindowId = container.inventorySlots.windowId;
        quickApplySlotNumber = target.slotNumber;
        return true;
    }

    private Slot findCandidateSlot(GuiContainer gui, Slot base, net.minecraft.item.Item allowedItem, int mode)
    {
        if (gui == null || base == null)
        {
            return null;
        }
        int x = base.xPos;
        int y = base.yPos;
        int[][] offsets = new int[][]{
            {0, -18}, {0, 18}, {-18, 0}, {18, 0}
        };
        Slot empty = null;
        Slot fallback = null;
        for (int[] off : offsets)
        {
            Slot slot = findSlotAt(gui, x + off[0], y + off[1]);
            if (slot == null)
            {
                continue;
            }
            if (slot.getHasStack())
            {
                ItemStack st = slot.getStack();
                if (!isGlassPane(st))
                {
                    if (st.getItem() == allowedItem)
                    {
                        return slot;
                    }
                    if (fallback == null)
                    {
                        fallback = slot;
                    }
                }
            }
            else if (empty == null)
            {
                empty = slot;
            }
        }
        return fallback != null ? fallback : empty;
    }

    private Slot findSlotAt(GuiContainer gui, int x, int y)
    {
        if (gui == null)
        {
            return null;
        }
        Container container = gui.inventorySlots;
        for (Slot slot : container.inventorySlots)
        {
            if (slot.xPos == x && slot.yPos == y)
            {
                return slot;
            }
        }
        return null;
    }

    private Slot findEmptySlotAt(GuiContainer gui, int x, int y)
    {
        Slot slot = findSlotAt(gui, x, y);
        if (slot == null)
        {
            return null;
        }
        return slot.getHasStack() ? null : slot;
    }

    private boolean isTextGlassPane(ItemStack stack)
    {
        if (stack == null || stack.isEmpty())
        {
            return false;
        }
        if (stack.getItem() != net.minecraft.item.Item.getItemFromBlock(Blocks.STAINED_GLASS_PANE))
        {
            return false;
        }
        if (stack.getMetadata() != 3)
        {
            return false;
        }
        String name = stack.getDisplayName();
        if (name == null)
        {
            return false;
        }
        String clean = TextFormatting.getTextWithoutFormattingCodes(name);
        return clean != null && clean.startsWith("\u0422\u0435\u043a\u0441\u0442");
    }

    private boolean isNumberGlassPane(ItemStack stack)
    {
        if (stack == null || stack.isEmpty())
        {
            return false;
        }
        if (stack.getItem() != net.minecraft.item.Item.getItemFromBlock(Blocks.STAINED_GLASS_PANE))
        {
            return false;
        }
        return stack.getMetadata() == 14;
    }

    private boolean isVariableGlassPane(ItemStack stack)
    {
        if (stack == null || stack.isEmpty())
        {
            return false;
        }
        if (stack.getItem() != net.minecraft.item.Item.getItemFromBlock(Blocks.STAINED_GLASS_PANE))
        {
            return false;
        }
        return stack.getMetadata() == 1;
    }

    private boolean isArrayGlassPane(ItemStack stack)
    {
        if (stack == null || stack.isEmpty())
        {
            return false;
        }
        if (stack.getItem() != net.minecraft.item.Item.getItemFromBlock(Blocks.STAINED_GLASS_PANE))
        {
            return false;
        }
        if (stack.getMetadata() != 5)
        {
            return false;
        }
        return !isLocationGlassPane(stack);
    }

    private boolean isLocationGlassPane(ItemStack stack)
    {
        if (stack == null || stack.isEmpty())
        {
            return false;
        }
        if (stack.getItem() != net.minecraft.item.Item.getItemFromBlock(Blocks.STAINED_GLASS_PANE))
        {
            return false;
        }
        if (stack.getMetadata() != 5)
        {
            return false;
        }
        String name = stack.getDisplayName();
        if (name == null)
        {
            return false;
        }
        String clean = TextFormatting.getTextWithoutFormattingCodes(name);
        if (clean == null)
        {
            return false;
        }
        String lower = clean.toLowerCase(Locale.ROOT);
        return lower.contains("\u043c\u0435\u0441\u0442\u043e\u043f\u043e\u043b\u043e\u0436");
    }

    private boolean isGlassPane(ItemStack stack)
    {
        if (stack == null || stack.isEmpty())
        {
            return false;
        }
        return stack.getItem() == net.minecraft.item.Item.getItemFromBlock(Blocks.STAINED_GLASS_PANE);
    }

    private void setInputActive(boolean active)
    {
        inputActive = active;
        if (active)
        {
            if (!inputRepeatEnabled)
            {
                Keyboard.enableRepeatEvents(true);
                inputRepeatEnabled = true;
            }
            if (inputField != null)
            {
                inputField.setFocused(true);
            }
        }
        else
        {
            if (inputRepeatEnabled)
            {
                Keyboard.enableRepeatEvents(false);
                inputRepeatEnabled = false;
            }
            if (inputField != null)
            {
                inputField.setFocused(false);
            }
            inputTitle = "";
            inputContext = INPUT_CONTEXT_SLOT;
            inputMode = INPUT_MODE_TEXT;
            inputTargetWindowId = -1;
            inputTargetSlotNumber = -1;
            inputSaveVariable = false;
            shulkerEditActive = false;
            shulkerEditWindowId = -1;
            shulkerEditSlotNumber = -1;
            shulkerEditPos = null;
        }
    }

    private void ensureInputField()
    {
        if (inputField == null)
        {
            Minecraft mc = Minecraft.getMinecraft();
            if (mc == null)
            {
                return;
            }
            inputField = new GuiTextField(0, mc.fontRenderer, 0, 0, 200, 12);
            inputField.setMaxStringLength(256);
            inputField.setEnableBackgroundDrawing(false);
            inputField.setFocused(true);
        }
    }

    private String getInputText()
    {
        return inputField == null ? "" : inputField.getText();
    }

    private void setInputText(String text)
    {
        ensureInputField();
        if (inputField != null)
        {
            inputField.setText(text == null ? "" : text);
            inputField.setCursorPositionEnd();
        }
    }

    private void startSlotInput(GuiContainer container, Slot target, ItemStack template, int mode, String preset,
        String title)
    {
        setInputActive(true);
        setInputText(preset);
        inputTargetWindowId = container.inventorySlots.windowId;
        inputTargetSlotNumber = target.slotNumber;
        inputMode = mode;
        inputContext = INPUT_CONTEXT_SLOT;
        inputSlotTemplate = template == null ? ItemStack.EMPTY : template;
        inputTitle = title == null ? "" : title;
        quickApplyWindowId = container.inventorySlots.windowId;
        quickApplySlotNumber = target.slotNumber;
    }

    private void startGiveInput(ItemStack template, int mode, String title)
    {
        setInputActive(true);
        setInputText("");
        inputMode = mode;
        inputContext = INPUT_CONTEXT_GIVE;
        inputGiveTemplate = template == null ? ItemStack.EMPTY : template;
        inputTitle = title == null ? "" : title;
    }

    private boolean isShiftDown()
    {
        return Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT);
    }

    private void appendVariablePlaceholder(String name)
    {
        if (name == null || name.trim().isEmpty())
        {
            return;
        }
        String placeholder = "%var(" + name.trim() + ")%";
        String raw = getInputText();
        if (raw == null)
        {
            raw = "";
        }
        setInputText(raw + placeholder);
    }

    private void applyEntityPlaceholderButton(String token)
    {
        applyEntityPlaceholderButton(token, false);
    }

    private void applyEntityPlaceholderButton(String token, boolean forceAppend)
    {
        if (token == null || token.isEmpty())
        {
            return;
        }
        String placeholder = "%" + token + "%";
        String raw = getInputText();
        if (raw == null)
        {
            raw = "";
        }
        if (forceAppend)
        {
            setInputText(raw + placeholder);
            return;
        }
        boolean replaced = false;
        for (String label : ENTITY_BUTTON_LABELS)
        {
            String find = "%" + label + "%";
            if (raw.contains(find))
            {
                raw = raw.replace(find, placeholder);
                replaced = true;
            }
        }
        if (!replaced)
        {
            raw += placeholder;
        }
        setInputText(raw);
    }

    private void setVariableSavedFlag()
    {
        inputSaveVariable = true;
        setActionBar(true, "&aSaved variable", 1500L);
    }

    private void setVariableUnsavedFlag()
    {
        inputSaveVariable = false;
        setActionBar(true, "&cUnsaved variable", 1500L);
    }

    private boolean isCurrentVariableSaved()
    {
        if (inputMode != INPUT_MODE_VARIABLE)
        {
            return false;
        }
        String raw = normalizePlainName(getInputText());
        if (raw.isEmpty())
        {
            return inputSaveVariable;
        }
        return inputSaveVariable || savedVariableNames.contains(raw);
    }

    private void toggleVariableSavedState()
    {
        String raw = normalizePlainName(getInputText());
        boolean saved = isCurrentVariableSaved();
        if (saved)
        {
            if (!raw.isEmpty())
            {
                savedVariableNames.remove(raw);
            }
            setVariableUnsavedFlag();
        }
        else
        {
            if (!raw.isEmpty())
            {
                savedVariableNames.add(raw);
            }
            setVariableSavedFlag();
        }
    }

    private String normalizeEntryScopeId(String id)
    {
        if (id == null)
        {
            return "default";
        }
        String clean = id.trim();
        return clean.isEmpty() ? "default" : clean;
    }

    private String getCurrentEntryScopeId()
    {
        return normalizeEntryScopeId(getScoreboardIdLine());
    }

    private List<InputEntry> getRecentEntries()
    {
        String scope = getCurrentEntryScopeId();
        List<InputEntry> entries = getRecentEntriesForScope(scope);
        if (!"default".equals(scope) && entries.isEmpty())
        {
            return getRecentEntriesForScope("default");
        }
        return entries;
    }

    private Map<String, Integer> getEntryCounts()
    {
        String scope = getCurrentEntryScopeId();
        Map<String, Integer> counts = getEntryCountsForScope(scope);
        if (!"default".equals(scope) && counts.isEmpty())
        {
            return getEntryCountsForScope("default");
        }
        return counts;
    }

    private List<InputEntry> getRecentEntriesForScope(String scope)
    {
        String key = normalizeEntryScopeId(scope);
        List<InputEntry> entries = recentEntriesById.get(key);
        if (entries == null)
        {
            entries = new ArrayList<>();
            recentEntriesById.put(key, entries);
        }
        return entries;
    }

    private Map<String, Integer> getEntryCountsForScope(String scope)
    {
        String key = normalizeEntryScopeId(scope);
        Map<String, Integer> counts = entryCountsById.get(key);
        if (counts == null)
        {
            counts = new HashMap<>();
            entryCountsById.put(key, counts);
        }
        return counts;
    }

    private void clearEntryCaches()
    {
        recentEntriesById.clear();
        entryCountsById.clear();
    }

    private void recordEntry(int mode, String text)
    {
        if (text == null)
        {
            return;
        }
        String clean = text.trim();
        if (clean.isEmpty())
        {
            return;
        }
        String scope = getCurrentEntryScopeId();
        Map<String, Integer> entryCounts = getEntryCountsForScope(scope);
        List<InputEntry> recentEntries = getRecentEntriesForScope(scope);
        String key = mode + "|" + clean;
        entryCounts.put(key, entryCounts.getOrDefault(key, 0) + 1);
        if (entryCounts.size() > ENTRY_COUNT_MAX)
        {
            entryCounts.clear();
        }
        recentEntries.removeIf(e -> e.mode == mode && e.text.equals(clean));
        recentEntries.add(0, new InputEntry(mode, clean));
        while (recentEntries.size() > ENTRY_RECENT_LIMIT)
        {
            recentEntries.remove(recentEntries.size() - 1);
        }
        entriesDirty = true;
    }

    private List<InputEntry> getFrequentEntries()
    {
        List<InputEntry> entries = new ArrayList<>();
        Map<String, Integer> entryCounts = getEntryCounts();
        for (Map.Entry<String, Integer> entry : entryCounts.entrySet())
        {
            if (entry.getValue() < ENTRY_FREQUENT_MIN)
            {
                continue;
            }
            String key = entry.getKey();
            int sep = key.indexOf('|');
            if (sep <= 0)
            {
                continue;
            }
            try
            {
                int mode = Integer.parseInt(key.substring(0, sep));
                String text = key.substring(sep + 1);
                entries.add(new InputEntry(mode, text));
            }
            catch (NumberFormatException ignored)
            {
                // ignore
            }
        }
        entries.sort((a, b) -> {
            int ca = entryCounts.getOrDefault(a.mode + "|" + a.text, 0);
            int cb = entryCounts.getOrDefault(b.mode + "|" + b.text, 0);
            return Integer.compare(cb, ca);
        });
        return entries;
    }

    private void applySuggestion()
    {
        String raw = getInputText();
        if (raw.isEmpty())
        {
            return;
        }
        String suggestion = findSuggestion(raw, inputMode);
        if (suggestion == null || suggestion.length() <= raw.length())
        {
            return;
        }
        setInputText(suggestion);
    }

    private String findSuggestion(String raw, int mode)
    {
        if (raw == null || raw.isEmpty())
        {
            return null;
        }
        String lower = raw.toLowerCase(Locale.ROOT);
        for (InputEntry entry : getRecentEntries())
        {
            if (entry.mode == mode && entry.text.toLowerCase(Locale.ROOT).startsWith(lower))
            {
                return entry.text;
            }
        }
        for (InputEntry entry : getFrequentEntries())
        {
            if (entry.mode == mode && entry.text.toLowerCase(Locale.ROOT).startsWith(lower))
            {
                return entry.text;
            }
        }
        return null;
    }

    private void drawSuggestion(Minecraft mc, String raw, int x, int y)
    {
        if (mc == null || mc.fontRenderer == null)
        {
            return;
        }
        String suggestion = findSuggestion(raw, inputMode);
        if (suggestion == null || suggestion.length() <= raw.length())
        {
            return;
        }
        String suffix = suggestion.substring(raw.length());
        int w = mc.fontRenderer.getStringWidth(raw);
        mc.fontRenderer.drawStringWithShadow(suffix, x + w + 2, y, 0x66FFEE);
    }

    private String normalizePlainName(String raw)
    {
        if (raw == null)
        {
            return "";
        }
        String text = stripSectionCodes(raw);
        text = stripAmpersandCodes(text);
        text = TextFormatting.getTextWithoutFormattingCodes(text);
        return text == null ? "" : text.trim();
    }

    private String stripSectionCodes(String raw)
    {
        if (raw == null)
        {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < raw.length(); i++)
        {
            char c = raw.charAt(i);
            if (c == '\u00a7' && i + 1 < raw.length())
            {
                i++;
                continue;
            }
            sb.append(c);
        }
        return sb.toString();
    }

    private String stripAmpersandCodes(String raw)
    {
        if (raw == null)
        {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < raw.length(); i++)
        {
            char c = raw.charAt(i);
            if (c == '&' && i + 1 < raw.length())
            {
                char code = Character.toLowerCase(raw.charAt(i + 1));
                if ((code >= '0' && code <= '9') || (code >= 'a' && code <= 'f') || code == 'k' || code == 'l'
                    || code == 'm' || code == 'n' || code == 'o' || code == 'r')
                {
                    i++;
                    continue;
                }
            }
            sb.append(c);
        }
        return sb.toString();
    }

    private String getCodeGlassScopeKey(World world)
    {
        int dim = world == null ? 0 : world.provider.getDimension();
        return normalizeEntryScopeId(getScoreboardIdLine()) + ":" + dim;
    }

    private void ensureBlueGlassCached(World world)
    {
        if (world == null)
        {
            return;
        }
        String key = getCodeGlassScopeKey(world);
        if (codeBlueGlassById.containsKey(key))
        {
            return;
        }
        BlockPos pos = new BlockPos(219, 0, 219);
        if (isStainedGlassMeta(world, pos, 3))
        {
            codeBlueGlassById.put(key, pos);
            codeBlueGlassDirty = true;
            setActionBar(true, "&aCode glass cached", 1500L);
        }
    }

    private void updateBlueGlassFromPlacement(World world, BlockPos placePos)
    {
        if (world == null || placePos == null)
        {
            return;
        }
        BlockPos base = placePos.down();
        BlockPos found = findBlueGlassFromBase(world, base);
        if (found == null)
        {
            return;
        }
        String key = getCodeGlassScopeKey(world);
        BlockPos existing = codeBlueGlassById.get(key);
        if (existing == null || !existing.equals(found))
        {
            codeBlueGlassById.put(key, found);
            codeBlueGlassDirty = true;
            setActionBar(true, "&aCode glass found", 1500L);
        }
    }

    private BlockPos findBlueGlassFromBase(World world, BlockPos base)
    {
        if (world == null || base == null)
        {
            return null;
        }
        if (isStainedGlassMeta(world, base, 3))
        {
            return base;
        }
        if (!isStainedGlassMeta(world, base, 8))
        {
            return null;
        }
        BlockPos scan = base;
        for (int i = 0; i < 64; i++)
        {
            if (isStainedGlassMeta(world, scan, 3))
            {
                return scan;
            }
            if (!isStainedGlassMeta(world, scan, 8))
            {
                return null;
            }
            scan = scan.add(2, 0, 0);
        }
        return null;
    }

    private boolean isStainedGlassMeta(World world, BlockPos pos, int meta)
    {
        if (world == null || pos == null)
        {
            return false;
        }
        IBlockState state = world.getBlockState(pos);
        if (state.getBlock() != Blocks.STAINED_GLASS)
        {
            return false;
        }
        return state.getBlock().getMetaFromState(state) == meta;
    }

    private boolean signMatchesQuery(TileEntitySign sign, String queryLower)
    {
        if (sign == null || queryLower == null || queryLower.isEmpty())
        {
            return false;
        }
        for (int i = 0; i < sign.signText.length; i++)
        {
            String raw = sign.signText[i] == null ? "" : sign.signText[i].getUnformattedText();
            String clean = TextFormatting.getTextWithoutFormattingCodes(raw);
            if (clean != null && clean.toLowerCase(Locale.ROOT).contains(queryLower))
            {
                return true;
            }
        }
        return false;
    }

    private String filterNumber(String text)
    {
        if (text == null)
        {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        boolean dot = false;
        boolean minus = false;
        for (int i = 0; i < text.length(); i++)
        {
            char c = text.charAt(i);
            if (c >= '0' && c <= '9')
            {
                sb.append(c);
            }
            else if (c == '-' && !minus && sb.length() == 0)
            {
                sb.append(c);
                minus = true;
            }
            else if (c == '.' && !dot)
            {
                sb.append(c);
                dot = true;
            }
        }
        return sb.toString();
    }

    private void drawSaveButton(Minecraft mc, int x, int y)
    {
        if (mc == null || mc.fontRenderer == null)
        {
            return;
        }
        boolean saved = isCurrentVariableSaved();
        String label = saved ? "\u00a7c\u00a7lUnsave" : "\u00a7a\u00a7lSave";
        mc.fontRenderer.drawStringWithShadow(label, x, y, 0xFFFFFF);
    }

    private void drawVariableButtons(Minecraft mc, int x1, int y1, int boxWidth)
    {
        if (mc == null || mc.fontRenderer == null)
        {
            return;
        }
        int buttonW = 54;
        int buttonH = 10;
        int pad = 4;
        int cols = 5;
        int startX = x1 + 6;
        int startY = y1 + 44;
        for (int i = 0; i < ENTITY_BUTTON_LABELS.length; i++)
        {
            int row = i / cols;
            int col = i % cols;
            int bx = startX + col * (buttonW + pad);
            int by = startY + row * (buttonH + pad);
            Gui.drawRect(bx, by, bx + buttonW, by + buttonH, 0x88202020);
            mc.fontRenderer.drawStringWithShadow("\u00a7b" + ENTITY_BUTTON_LABELS[i], bx + 2, by + 1, 0xFFFFFF);
        }
    }

    private int getVariableButtonIndex(int mouseX, int mouseY, GuiScreen gui)
    {
        if (gui == null)
        {
            return -1;
        }
        int width = gui.width;
        int height = gui.height;
        int boxWidth = Math.min(300, width - 40);
        int x1 = (width - boxWidth) / 2;
        int y1 = height - 86;
        int buttonW = 54;
        int buttonH = 10;
        int pad = 4;
        int cols = 5;
        int startX = x1 + 6;
        int startY = y1 + 44;
        for (int i = 0; i < ENTITY_BUTTON_LABELS.length; i++)
        {
            int row = i / cols;
            int col = i % cols;
            int bx = startX + col * (buttonW + pad);
            int by = startY + row * (buttonH + pad);
            if (mouseX >= bx && mouseX <= bx + buttonW && mouseY >= by && mouseY <= by + buttonH)
            {
                return i;
            }
        }
        return -1;
    }

    private boolean handleVariableButtonClick(int mouseX, int mouseY, GuiContainer gui)
    {
        int idx = getVariableButtonIndex(mouseX, mouseY, gui);
        if (idx < 0 || idx >= ENTITY_BUTTON_LABELS.length)
        {
            return false;
        }
        applyEntityPlaceholderButton(ENTITY_BUTTON_LABELS[idx], isShiftDown());
        return true;
    }

    private boolean isSaveVariableButtonClicked(int mouseX, int mouseY, GuiScreen gui)
    {
        return isSaveButtonClicked(mouseX, mouseY, gui);
    }

    private boolean handleVariableInsertFromSlot(GuiContainer gui)
    {
        if (gui == null || !(inputMode == INPUT_MODE_VARIABLE || inputMode == INPUT_MODE_ARRAY))
        {
            return false;
        }
        Slot hovered = getSlotUnderMouse(gui);
        if (hovered == null || !hovered.getHasStack())
        {
            return false;
        }
        ItemStack stack = hovered.getStack();
        int mode = getModeForItem(stack);
        if (mode != INPUT_MODE_VARIABLE && mode != INPUT_MODE_ARRAY)
        {
            return false;
        }
        String name = normalizePlainName(stack.getDisplayName());
        if (name.isEmpty())
        {
            return false;
        }
        appendVariablePlaceholder(name);
        return true;
    }

    private boolean isSaveButtonClicked(int mouseX, int mouseY, GuiScreen gui)
    {
        if (gui == null)
        {
            return false;
        }
        int width = gui.width;
        int height = gui.height;
        int boxWidth = Math.min(300, width - 40);
        int x1 = (width - boxWidth) / 2;
        int y1 = height - 86;
        int x = x1 + boxWidth - 48;
        int y = y1 + 6;
        return mouseX >= x && mouseX <= x + 44 && mouseY >= y && mouseY <= y + 10;
    }

    private void appendArrayMark()
    {
        String raw = getInputText();
        setInputText(raw + ARRAY_MARK);
    }

    private void handlePendingShulkerEdit(GuiContainer gui)
    {
        if (gui == null)
        {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.player == null || mc.playerController == null)
        {
            return;
        }
        if (!mc.playerController.isInCreativeMode()
            || mc.playerController.getCurrentGameType() != GameType.CREATIVE)
        {
            setActionBar(true, "&cShulker edit needs Creative", 2000L);
            pendingShulkerEdit = false;
            pendingShulkerPos = null;
            return;
        }
        if (!pendingShulkerEdit || pendingShulkerPos == null)
        {
            return;
        }
        String guiName = gui.getClass().getSimpleName().toLowerCase(Locale.ROOT);
        if (!(gui instanceof GuiShulkerBox) && !guiName.contains("shulker"))
        {
            return;
        }
        Slot target = findShulkerTargetSlot(gui, mc.player);
        if (target == null)
        {
            if (debugUi)
            {
                setActionBar(false, "&cShulker edit: no slot", 1500L);
            }
            return;
        }
        ItemStack stack = target.getStack();
        if (!stack.isEmpty() && stack.getItem() != Items.BOOK)
        {
            setActionBar(true, "&cShulker slot not book/air", 2000L);
            pendingShulkerEdit = false;
            pendingShulkerPos = null;
            return;
        }
        String preset = stack.isEmpty() ? "" : toAmpersandCodes(stack.getDisplayName());
        ItemStack template = stack.isEmpty() ? new ItemStack(Items.BOOK, 1) : stack.copy();
        startSlotInput(gui, target, template, INPUT_MODE_TEXT, preset, "Shulker Text");
        shulkerEditActive = true;
        shulkerEditWindowId = gui.inventorySlots.windowId;
        shulkerEditSlotNumber = target.slotNumber;
        shulkerEditPos = pendingShulkerPos;
        shulkerEditDim = pendingShulkerDim;
        shulkerEditColor = pendingShulkerColor;
        pendingShulkerEdit = false;
        pendingShulkerPos = null;
        setActionBar(true, "&bEdit shulker text", 1500L);
        if (debugUi)
        {
            setActionBar(false, "&7Shulker slot=" + target.slotNumber + " win=" + gui.inventorySlots.windowId,
                1500L);
        }
    }

    private Slot findLastContainerSlot(GuiContainer gui, EntityPlayer player)
    {
        if (gui == null || player == null)
        {
            return null;
        }
        Slot best = null;
        int bestIndex = -1;
        for (Slot slot : gui.inventorySlots.inventorySlots)
        {
            if (slot.inventory == player.inventory)
            {
                continue;
            }
            int idx = slot.getSlotIndex();
            if (idx > bestIndex)
            {
                bestIndex = idx;
                best = slot;
            }
        }
        return best;
    }

    private Slot findFirstContainerSlot(GuiContainer gui, EntityPlayer player)
    {
        if (gui == null || player == null)
        {
            return null;
        }
        Slot best = null;
        int bestIndex = Integer.MAX_VALUE;
        for (Slot slot : gui.inventorySlots.inventorySlots)
        {
            if (slot.inventory == player.inventory)
            {
                continue;
            }
            int idx = slot.getSlotIndex();
            if (idx < bestIndex)
            {
                bestIndex = idx;
                best = slot;
            }
        }
        return best;
    }

    private Slot findShulkerTargetSlot(GuiContainer gui, EntityPlayer player)
    {
        if (gui == null || player == null)
        {
            return null;
        }
        Slot firstEmpty = null;
        Slot firstAny = null;
        for (Slot slot : gui.inventorySlots.inventorySlots)
        {
            if (slot.inventory == player.inventory)
            {
                continue;
            }
            if (firstAny == null)
            {
                firstAny = slot;
            }
            ItemStack stack = slot.getStack();
            if (!stack.isEmpty() && stack.getItem() == Items.BOOK)
            {
                return slot;
            }
            if (firstEmpty == null && (stack.isEmpty() || stack.getItem() == Items.AIR))
            {
                firstEmpty = slot;
            }
        }
        return firstEmpty != null ? firstEmpty : firstAny;
    }

    private int getShulkerColor(World world, BlockPos pos, TileEntity tile)
    {
        EnumDyeColor dye = null;
        if (tile instanceof TileEntityShulkerBox)
        {
            dye = ((TileEntityShulkerBox) tile).getColor();
        }
        if (dye == null && world != null)
        {
            IBlockState state = world.getBlockState(pos);
            Block block = state.getBlock();
            if (block instanceof BlockShulkerBox)
            {
                dye = ((BlockShulkerBox) block).getColor();
            }
        }
        if (dye == null)
        {
            return 0xFFFFFF;
        }
        return dye.getColorValue();
    }

    private void saveEntriesIfNeeded()
    {
        if (!entriesDirty || entriesFile == null)
        {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastEntriesSaveMs < 10000L)
        {
            return;
        }
        if (entriesSaveQueued.get())
        {
            return;
        }
        lastEntriesSaveMs = now;
        String payload = buildEntriesSnapshot();
        entriesDirty = false;
        entriesSaveQueued.set(true);
        final File target = entriesFile;
        ioExecutor.execute(() -> {
            try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(target), "UTF-8")))
            {
                writer.write(payload);
            }
            catch (IOException ignored)
            {
                // ignore
            }
            finally
            {
                entriesSaveQueued.set(false);
            }
        });
    }

    private void saveMenuCacheIfNeeded()
    {
        if (!menuCacheDirty || menuCacheFile == null)
        {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastMenuCacheSaveMs < 10000L)
        {
            return;
        }
        if (menuSaveQueued.get())
        {
            return;
        }
        lastMenuCacheSaveMs = now;
        final File target = menuCacheFile;
        final Map<String, CachedMenu> snapshot = new LinkedHashMap<>();
        for (Map.Entry<String, CachedMenu> entry : menuCache.entrySet())
        {
            String key = entry.getKey();
            CachedMenu menu = entry.getValue();
            if (key == null || menu == null)
            {
                continue;
            }
            snapshot.put(key, new CachedMenu(menu.title, menu.size, copyItemStackList(menu.items), menu.hash));
        }
        final CachedMenu customSnapshot = customMenuCache == null ? null
            : new CachedMenu(customMenuCache.title, customMenuCache.size, copyItemStackList(customMenuCache.items),
                customMenuCache.hash);
        menuCacheDirty = false;
        menuSaveQueued.set(true);
        ioExecutor.execute(() -> {
            try
            {
                saveMenuCacheSnapshot(target, snapshot, customSnapshot);
            }
            finally
            {
                menuSaveQueued.set(false);
            }
        });
    }

    private void checkConfigFileChanges()
    {
        if (config == null)
        {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastConfigCheckMs < 2000L)
        {
            return;
        }
        lastConfigCheckMs = now;
        File cfg = config.getConfigFile();
        if (cfg == null)
        {
            return;
        }
        long stamp = cfg.lastModified();
        if (stamp != lastConfigStamp)
        {
            lastConfigStamp = stamp;
            syncConfig(true);
            setActionBar(true, "&eConfig reload: hotbar=" + enableSecondHotbar + " holo=#"
                + String.format(Locale.ROOT, "%06X", chestHoloTextColor), 3000L);
        }
    }

    private void saveChestIdCachesIfNeeded()
    {
        if (!chestIdDirty || chestCacheFile == null)
        {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastChestIdSaveMs < 10000L)
        {
            return;
        }
        if (chestSaveQueued.get())
        {
            return;
        }
        lastChestIdSaveMs = now;
        final File target = chestCacheFile;
        final Map<String, ChestCache> snapshot = new LinkedHashMap<>();
        for (Map.Entry<String, ChestCache> entry : chestIdCaches.entrySet())
        {
            String id = entry.getKey();
            ChestCache cache = entry.getValue();
            if (id == null || cache == null)
            {
                continue;
            }
            snapshot.put(id, new ChestCache(cache.dim, cache.pos, copyItemStackList(cache.items),
                System.currentTimeMillis(), cache.label));
        }
        chestIdDirty = false;
        chestSaveQueued.set(true);
        ioExecutor.execute(() -> {
            try
            {
                saveChestIdCachesSnapshot(target, snapshot);
            }
            finally
            {
                chestSaveQueued.set(false);
            }
        });
    }

    private void saveShulkerHolosIfNeeded()
    {
        if (!shulkerHoloDirty || shulkerHoloFile == null)
        {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastShulkerHoloSaveMs < 10000L)
        {
            return;
        }
        if (shulkerHoloSaveQueued.get())
        {
            return;
        }
        lastShulkerHoloSaveMs = now;
        final File target = shulkerHoloFile;
        final Map<String, ShulkerHolo> snapshot = new LinkedHashMap<>();
        for (Map.Entry<String, ShulkerHolo> entry : shulkerHolos.entrySet())
        {
            String key = entry.getKey();
            ShulkerHolo holo = entry.getValue();
            if (key == null || holo == null || holo.pos == null)
            {
                continue;
            }
            snapshot.put(key, new ShulkerHolo(holo.dim, holo.pos, holo.text, holo.color));
        }
        shulkerHoloDirty = false;
        shulkerHoloSaveQueued.set(true);
        ioExecutor.execute(() -> {
            try
            {
                saveShulkerHoloSnapshot(target, snapshot);
            }
            finally
            {
                shulkerHoloSaveQueued.set(false);
            }
        });
    }

    private void saveCodeBlueGlassIfNeeded()
    {
        if (!codeBlueGlassDirty || codeBlueGlassFile == null)
        {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastCodeBlueGlassSaveMs < 10000L)
        {
            return;
        }
        if (codeBlueGlassSaveQueued.get())
        {
            return;
        }
        lastCodeBlueGlassSaveMs = now;
        final File target = codeBlueGlassFile;
        final Map<String, BlockPos> snapshot = new LinkedHashMap<>(codeBlueGlassById);
        codeBlueGlassDirty = false;
        codeBlueGlassSaveQueued.set(true);
        ioExecutor.execute(() -> {
            try
            {
                saveCodeBlueGlassSnapshot(target, snapshot);
            }
            finally
            {
                codeBlueGlassSaveQueued.set(false);
            }
        });
    }

    private void scanChestEntries(GuiChest chest)
    {
        if (chest == null)
        {
            return;
        }
        IInventory inv = getChestInventory(chest);
        if (inv == null)
        {
            return;
        }
        for (int i = 0; i < inv.getSizeInventory(); i++)
        {
            ItemStack stack = inv.getStackInSlot(i);
            if (stack == null || stack.isEmpty())
            {
                continue;
            }
            int mode = getModeForItem(stack);
            if (mode < 0)
            {
                continue;
            }
            String text = extractEntryText(stack, mode);
            if (text.isEmpty())
            {
                continue;
            }
            recordEntryFromChest(mode, text);
            if ((mode == INPUT_MODE_VARIABLE || mode == INPUT_MODE_ARRAY) && isSavedVariableStack(stack))
            {
                savedVariableNames.add(text);
            }
        }
    }

    private String extractEntryText(ItemStack stack, int mode)
    {
        if (stack == null || stack.isEmpty())
        {
            return "";
        }
        if (mode == INPUT_MODE_NUMBER)
        {
            return extractNumber(stack.getDisplayName());
        }
        if (mode == INPUT_MODE_VARIABLE || mode == INPUT_MODE_ARRAY)
        {
            return normalizePlainName(stack.getDisplayName());
        }
        if (mode == INPUT_MODE_LOCATION)
        {
            return toAmpersandCodes(stack.getDisplayName());
        }
        return toAmpersandCodes(stack.getDisplayName());
    }

    private void recordEntryFromChest(int mode, String text)
    {
        if (text == null)
        {
            return;
        }
        String clean = text.trim();
        if (clean.isEmpty())
        {
            return;
        }
        Map<String, Integer> entryCounts = getEntryCounts();
        List<InputEntry> recentEntries = getRecentEntries();
        String key = mode + "|" + clean;
        int count = entryCounts.getOrDefault(key, 0) + 1;
        entryCounts.put(key, count);
        if (entryCounts.size() > ENTRY_COUNT_MAX)
        {
            entryCounts.clear();
        }
        boolean exists = false;
        for (InputEntry entry : recentEntries)
        {
            if (entry.mode == mode && entry.text.equals(clean))
            {
                exists = true;
                break;
            }
        }
        if (!exists)
        {
            if (recentEntries.size() < ENTRY_RECENT_LIMIT)
            {
                recentEntries.add(0, new InputEntry(mode, clean));
            }
            else if (count >= 2)
            {
                int minIdx = -1;
                int minCount = Integer.MAX_VALUE;
                for (int i = 0; i < recentEntries.size(); i++)
                {
                    InputEntry entry = recentEntries.get(i);
                    String eKey = entry.mode + "|" + entry.text;
                    int eCount = entryCounts.getOrDefault(eKey, 0);
                    if (eCount < minCount)
                    {
                        minCount = eCount;
                        minIdx = i;
                    }
                }
                if (minIdx >= 0 && count > minCount)
                {
                    recentEntries.remove(minIdx);
                    recentEntries.add(0, new InputEntry(mode, clean));
                }
            }
        }
        entriesDirty = true;
    }

    private boolean isSavedVariableStack(ItemStack stack)
    {
        if (stack == null || stack.isEmpty() || !stack.hasTagCompound())
        {
            return false;
        }
        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null || !tag.hasKey("display"))
        {
            return false;
        }
        NBTTagCompound display = tag.getCompoundTag("display");
        if (display.hasKey("LocName") && "save".equalsIgnoreCase(display.getString("LocName")))
        {
            return true;
        }
        if (display.hasKey("Lore"))
        {
            NBTTagList lore = display.getTagList("Lore", 8);
            if (lore.tagCount() > 0)
            {
                String first = lore.getStringTagAt(0);
                return first != null && first.contains("\u00a7d\u0421\u041e\u0425\u0420\u0410\u041d\u0415\u041d\u041e");
            }
        }
        return false;
    }

    private void applySavedVariableTag(ItemStack stack)
    {
        if (stack == null || stack.isEmpty())
        {
            return;
        }
        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null)
        {
            tag = new NBTTagCompound();
        }
        NBTTagCompound display = tag.hasKey("display") ? tag.getCompoundTag("display") : new NBTTagCompound();
        display.setString("LocName", "save");
        NBTTagList lore = display.hasKey("Lore") ? display.getTagList("Lore", 8) : new NBTTagList();
        if (lore.tagCount() == 0)
        {
            lore.appendTag(new NBTTagString("\u00a7d\u0421\u041e\u0425\u0420\u0410\u041d\u0415\u041d\u041e"));
        }
        else
        {
            String first = lore.getStringTagAt(0);
            if (first == null || !first.contains("\u00a7d\u0421\u041e\u0425\u0420\u0410\u041d\u0415\u041d\u041e"))
            {
                NBTTagList newLore = new NBTTagList();
                newLore.appendTag(new NBTTagString("\u00a7d\u0421\u041e\u0425\u0420\u0410\u041d\u0415\u041d\u041e"));
                for (int i = 0; i < lore.tagCount(); i++)
                {
                    newLore.appendTag(lore.get(i));
                }
                lore = newLore;
            }
        }
        display.setTag("Lore", lore);
        tag.setTag("display", display);
        stack.setTagCompound(tag);
    }

    private void removeSavedVariableTag(ItemStack stack)
    {
        if (stack == null || stack.isEmpty() || !stack.hasTagCompound())
        {
            return;
        }
        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null || !tag.hasKey("display"))
        {
            return;
        }
        NBTTagCompound display = tag.getCompoundTag("display");
        if (display.hasKey("LocName") && "save".equalsIgnoreCase(display.getString("LocName")))
        {
            display.removeTag("LocName");
        }
        if (display.hasKey("Lore"))
        {
            NBTTagList lore = display.getTagList("Lore", 8);
            if (lore.tagCount() > 0)
            {
                String first = lore.getStringTagAt(0);
                if (first != null && first.contains("\u00a7d\u0421\u041e\u0425\u0420\u0410\u041d\u0415\u041d\u041e"))
                {
                    NBTTagList newLore = new NBTTagList();
                    for (int i = 1; i < lore.tagCount(); i++)
                    {
                        newLore.appendTag(lore.get(i));
                    }
                    lore = newLore;
                }
            }
            if (lore.tagCount() > 0)
            {
                display.setTag("Lore", lore);
            }
            else
            {
                display.removeTag("Lore");
            }
        }
        tag.setTag("display", display);
        stack.setTagCompound(tag);
    }

    private void saveNote()
    {
        if (noteFile == null)
        {
            return;
        }
        try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(noteFile), "UTF-8")))
        {
            writer.write(noteText == null ? "" : noteText);
        }
        catch (Exception e)
        {
            // ignore
        }
    }

    private String buildEntriesSnapshot()
    {
        StringBuilder sb = new StringBuilder();
        Set<String> scopes = new LinkedHashSet<>();
        scopes.addAll(recentEntriesById.keySet());
        scopes.addAll(entryCountsById.keySet());
        if (scopes.isEmpty())
        {
            scopes.add("default");
        }
        sb.append("{\"recent\":[");
        boolean firstRecent = true;
        for (String scope : scopes)
        {
            List<InputEntry> list = recentEntriesById.get(scope);
            if (list == null)
            {
                continue;
            }
            for (InputEntry e : list)
            {
                if (!firstRecent)
                {
                    sb.append(",");
                }
                firstRecent = false;
                sb.append("{\"id\":\"");
                sb.append(escapeJson(scope));
                sb.append("\",\"mode\":");
                sb.append(Integer.toString(e.mode));
                sb.append(",\"text\":\"");
                sb.append(escapeJson(e.text));
                sb.append("\"}");
            }
        }
        sb.append("],\"counts\":[");
        boolean firstCount = true;
        for (String scope : scopes)
        {
            Map<String, Integer> counts = entryCountsById.get(scope);
            if (counts == null)
            {
                continue;
            }
            for (Map.Entry<String, Integer> entry : counts.entrySet())
            {
                if (!firstCount)
                {
                    sb.append(",");
                }
                firstCount = false;
                sb.append("{\"id\":\"");
                sb.append(escapeJson(scope));
                sb.append("\",\"key\":\"");
                sb.append(escapeJson(entry.getKey()));
                sb.append("\",\"count\":");
                sb.append(Integer.toString(entry.getValue()));
                sb.append("}");
            }
        }
        sb.append("]}");
        return sb.toString();
    }

    private List<ItemStack> copyItemStackList(List<ItemStack> items)
    {
        List<ItemStack> copy = new ArrayList<>();
        if (items == null)
        {
            return copy;
        }
        for (ItemStack stack : items)
        {
            copy.add(stack == null || stack.isEmpty() ? ItemStack.EMPTY : stack.copy());
        }
        return copy;
    }

    private void saveChestIdCaches()
    {
        if (chestCacheFile == null)
        {
            return;
        }
        try
        {
            NBTTagCompound root = new NBTTagCompound();
            NBTTagList list = new NBTTagList();
            for (Map.Entry<String, ChestCache> entry : chestIdCaches.entrySet())
            {
                String id = entry.getKey();
                ChestCache cache = entry.getValue();
                if (id == null || cache == null)
                {
                    continue;
                }
                NBTTagCompound tag = new NBTTagCompound();
                tag.setString("Id", id);
                tag.setString("Label", cache.label == null ? "" : cache.label);
                tag.setInteger("Dim", cache.dim);
                tag.setInteger("X", cache.pos == null ? 0 : cache.pos.getX());
                tag.setInteger("Y", cache.pos == null ? 0 : cache.pos.getY());
                tag.setInteger("Z", cache.pos == null ? 0 : cache.pos.getZ());
                NBTTagList items = new NBTTagList();
                for (ItemStack stack : cache.items)
                {
                    NBTTagCompound itemTag = new NBTTagCompound();
                    if (!stack.isEmpty())
                    {
                        stack.writeToNBT(itemTag);
                    }
                    items.appendTag(itemTag);
                }
                tag.setTag("Items", items);
                list.appendTag(tag);
            }
            root.setTag("Caches", list);
            CompressedStreamTools.write(root, chestCacheFile);
        }
        catch (Exception e)
        {
            // ignore
        }
    }

    private void saveChestIdCachesSnapshot(File target, Map<String, ChestCache> snapshot)
    {
        if (target == null)
        {
            return;
        }
        try
        {
            NBTTagCompound root = new NBTTagCompound();
            NBTTagList list = new NBTTagList();
            for (Map.Entry<String, ChestCache> entry : snapshot.entrySet())
            {
                String id = entry.getKey();
                ChestCache cache = entry.getValue();
                if (id == null || cache == null)
                {
                    continue;
                }
                NBTTagCompound tag = new NBTTagCompound();
                tag.setString("Id", id);
                tag.setString("Label", cache.label == null ? "" : cache.label);
                tag.setInteger("Dim", cache.dim);
                tag.setInteger("X", cache.pos == null ? 0 : cache.pos.getX());
                tag.setInteger("Y", cache.pos == null ? 0 : cache.pos.getY());
                tag.setInteger("Z", cache.pos == null ? 0 : cache.pos.getZ());
                NBTTagList items = new NBTTagList();
                for (ItemStack stack : cache.items)
                {
                    NBTTagCompound itemTag = new NBTTagCompound();
                    if (stack != null && !stack.isEmpty())
                    {
                        stack.writeToNBT(itemTag);
                    }
                    items.appendTag(itemTag);
                }
                tag.setTag("Items", items);
                list.appendTag(tag);
            }
            root.setTag("Caches", list);
            CompressedStreamTools.write(root, target);
        }
        catch (Exception e)
        {
            // ignore
        }
    }

    private void loadChestIdCaches()
    {
        if (chestCacheFile == null || !chestCacheFile.exists())
        {
            return;
        }
        try
        {
            NBTTagCompound root = CompressedStreamTools.read(chestCacheFile);
            if (root == null || !root.hasKey("Caches", 9))
            {
                return;
            }
            NBTTagList list = root.getTagList("Caches", 10);
            chestIdCaches.clear();
            for (int i = 0; i < list.tagCount(); i++)
            {
                NBTTagCompound tag = list.getCompoundTagAt(i);
                String id = tag.getString("Id");
                String label = tag.getString("Label");
                int dim = tag.hasKey("Dim") ? tag.getInteger("Dim") : 0;
                int x = tag.hasKey("X") ? tag.getInteger("X") : 0;
                int y = tag.hasKey("Y") ? tag.getInteger("Y") : 0;
                int z = tag.hasKey("Z") ? tag.getInteger("Z") : 0;
                NBTTagList itemsTag = tag.getTagList("Items", 10);
                List<ItemStack> items = new ArrayList<>();
                for (int j = 0; j < itemsTag.tagCount(); j++)
                {
                    NBTTagCompound itemTag = itemsTag.getCompoundTagAt(j);
                    ItemStack stack = new ItemStack(itemTag);
                    items.add(stack.isEmpty() ? ItemStack.EMPTY : stack.copy());
                }
                if (id != null && !id.isEmpty())
                {
                    BlockPos pos = new BlockPos(x, y, z);
                    chestIdCaches.put(id, new ChestCache(dim, pos, items, System.currentTimeMillis(), label));
                }
            }
        }
        catch (Exception e)
        {
            // ignore
        }
    }

    private void saveMenuCache()
    {
        if (menuCacheFile == null)
        {
            return;
        }
        try
        {
            NBTTagCompound root = new NBTTagCompound();
            NBTTagList list = new NBTTagList();
            for (Map.Entry<String, CachedMenu> entry : menuCache.entrySet())
            {
                String key = entry.getKey();
                CachedMenu menu = entry.getValue();
                if (key == null || menu == null)
                {
                    continue;
                }
                NBTTagCompound tag = new NBTTagCompound();
                tag.setString("Key", key);
                tag.setString("Title", menu.title == null ? "" : menu.title);
                tag.setInteger("Size", menu.size);
                tag.setString("Hash", menu.hash == null ? "" : menu.hash);
                NBTTagList items = new NBTTagList();
                for (ItemStack stack : menu.items)
                {
                    NBTTagCompound itemTag = new NBTTagCompound();
                    if (!stack.isEmpty())
                    {
                        stack.writeToNBT(itemTag);
                    }
                    items.appendTag(itemTag);
                }
                tag.setTag("Items", items);
                list.appendTag(tag);
            }
            root.setTag("Menus", list);
            if (customMenuCache != null)
            {
                NBTTagCompound custom = new NBTTagCompound();
                custom.setString("Title", customMenuCache.title == null ? "" : customMenuCache.title);
                custom.setInteger("Size", customMenuCache.size);
                custom.setString("Hash", customMenuCache.hash == null ? "" : customMenuCache.hash);
                NBTTagList items = new NBTTagList();
                for (ItemStack stack : customMenuCache.items)
                {
                    NBTTagCompound itemTag = new NBTTagCompound();
                    if (!stack.isEmpty())
                    {
                        stack.writeToNBT(itemTag);
                    }
                    items.appendTag(itemTag);
                }
                custom.setTag("Items", items);
                root.setTag("Custom", custom);
            }
            CompressedStreamTools.write(root, menuCacheFile);
        }
        catch (Exception e)
        {
            // ignore
        }
    }

    private void saveMenuCacheSnapshot(File target, Map<String, CachedMenu> snapshot, CachedMenu customSnapshot)
    {
        if (target == null)
        {
            return;
        }
        try
        {
            NBTTagCompound root = new NBTTagCompound();
            NBTTagList list = new NBTTagList();
            for (Map.Entry<String, CachedMenu> entry : snapshot.entrySet())
            {
                String key = entry.getKey();
                CachedMenu menu = entry.getValue();
                if (key == null || menu == null)
                {
                    continue;
                }
                NBTTagCompound tag = new NBTTagCompound();
                tag.setString("Key", key);
                tag.setString("Title", menu.title == null ? "" : menu.title);
                tag.setInteger("Size", menu.size);
                tag.setString("Hash", menu.hash == null ? "" : menu.hash);
                NBTTagList items = new NBTTagList();
                for (ItemStack stack : menu.items)
                {
                    NBTTagCompound itemTag = new NBTTagCompound();
                    if (stack != null && !stack.isEmpty())
                    {
                        stack.writeToNBT(itemTag);
                    }
                    items.appendTag(itemTag);
                }
                tag.setTag("Items", items);
                list.appendTag(tag);
            }
            root.setTag("Menus", list);
            if (customSnapshot != null)
            {
                NBTTagCompound custom = new NBTTagCompound();
                custom.setString("Title", customSnapshot.title == null ? "" : customSnapshot.title);
                custom.setInteger("Size", customSnapshot.size);
                custom.setString("Hash", customSnapshot.hash == null ? "" : customSnapshot.hash);
                NBTTagList items = new NBTTagList();
                for (ItemStack stack : customSnapshot.items)
                {
                    NBTTagCompound itemTag = new NBTTagCompound();
                    if (stack != null && !stack.isEmpty())
                    {
                        stack.writeToNBT(itemTag);
                    }
                    items.appendTag(itemTag);
                }
                custom.setTag("Items", items);
                root.setTag("Custom", custom);
            }
            CompressedStreamTools.write(root, target);
        }
        catch (Exception e)
        {
            // ignore
        }
    }

    private void saveShulkerHoloSnapshot(File target, Map<String, ShulkerHolo> snapshot)
    {
        if (target == null)
        {
            return;
        }
        try
        {
            NBTTagCompound root = new NBTTagCompound();
            NBTTagList list = new NBTTagList();
            for (ShulkerHolo holo : snapshot.values())
            {
                if (holo == null || holo.pos == null)
                {
                    continue;
                }
                NBTTagCompound tag = new NBTTagCompound();
                tag.setInteger("Dim", holo.dim);
                tag.setInteger("X", holo.pos.getX());
                tag.setInteger("Y", holo.pos.getY());
                tag.setInteger("Z", holo.pos.getZ());
                tag.setInteger("Color", holo.color);
                tag.setString("Text", holo.text == null ? "" : holo.text);
                list.appendTag(tag);
            }
            root.setTag("Holos", list);
            CompressedStreamTools.write(root, target);
        }
        catch (Exception e)
        {
            // ignore
        }
    }

    private void saveCodeBlueGlassSnapshot(File target, Map<String, BlockPos> snapshot)
    {
        if (target == null)
        {
            return;
        }
        try
        {
            NBTTagCompound root = new NBTTagCompound();
            NBTTagList list = new NBTTagList();
            for (Map.Entry<String, BlockPos> entry : snapshot.entrySet())
            {
                String key = entry.getKey();
                BlockPos pos = entry.getValue();
                if (key == null || pos == null)
                {
                    continue;
                }
                NBTTagCompound tag = new NBTTagCompound();
                tag.setString("Key", key);
                tag.setInteger("X", pos.getX());
                tag.setInteger("Y", pos.getY());
                tag.setInteger("Z", pos.getZ());
                list.appendTag(tag);
            }
            root.setTag("Blue", list);
            CompressedStreamTools.write(root, target);
        }
        catch (Exception e)
        {
            // ignore
        }
    }

    private void loadShulkerHolos()
    {
        if (shulkerHoloFile == null || !shulkerHoloFile.exists())
        {
            return;
        }
        try
        {
            NBTTagCompound root = CompressedStreamTools.read(shulkerHoloFile);
            if (root == null || !root.hasKey("Holos", 9))
            {
                return;
            }
            NBTTagList list = root.getTagList("Holos", 10);
            shulkerHolos.clear();
            for (int i = 0; i < list.tagCount(); i++)
            {
                NBTTagCompound tag = list.getCompoundTagAt(i);
                int dim = tag.hasKey("Dim") ? tag.getInteger("Dim") : 0;
                int x = tag.hasKey("X") ? tag.getInteger("X") : 0;
                int y = tag.hasKey("Y") ? tag.getInteger("Y") : 0;
                int z = tag.hasKey("Z") ? tag.getInteger("Z") : 0;
                int color = tag.hasKey("Color") ? tag.getInteger("Color") : 0xFFFFFF;
                String text = tag.getString("Text");
                BlockPos pos = new BlockPos(x, y, z);
                String key = dim + ":" + pos.toString();
                shulkerHolos.put(key, new ShulkerHolo(dim, pos, text, color));
            }
        }
        catch (Exception e)
        {
            // ignore
        }
    }

    private void loadCodeBlueGlass()
    {
        if (codeBlueGlassFile == null || !codeBlueGlassFile.exists())
        {
            return;
        }
        try
        {
            NBTTagCompound root = CompressedStreamTools.read(codeBlueGlassFile);
            if (root == null || !root.hasKey("Blue", 9))
            {
                return;
            }
            NBTTagList list = root.getTagList("Blue", 10);
            codeBlueGlassById.clear();
            for (int i = 0; i < list.tagCount(); i++)
            {
                NBTTagCompound tag = list.getCompoundTagAt(i);
                String key = tag.getString("Key");
                int x = tag.hasKey("X") ? tag.getInteger("X") : 0;
                int y = tag.hasKey("Y") ? tag.getInteger("Y") : 0;
                int z = tag.hasKey("Z") ? tag.getInteger("Z") : 0;
                if (key != null && !key.isEmpty())
                {
                    codeBlueGlassById.put(key, new BlockPos(x, y, z));
                }
            }
        }
        catch (Exception e)
        {
            // ignore
        }
    }

    private void loadMenuCache()
    {
        if (menuCacheFile == null || !menuCacheFile.exists())
        {
            return;
        }
        try
        {
            NBTTagCompound root = CompressedStreamTools.read(menuCacheFile);
            if (root == null)
            {
                return;
            }
            if (root.hasKey("Menus", 9))
            {
                NBTTagList list = root.getTagList("Menus", 10);
                menuCache.clear();
                for (int i = 0; i < list.tagCount(); i++)
                {
                    NBTTagCompound tag = list.getCompoundTagAt(i);
                    String key = tag.getString("Key");
                    String title = tag.getString("Title");
                    int size = tag.getInteger("Size");
                    String hash = tag.getString("Hash");
                    NBTTagList itemsTag = tag.getTagList("Items", 10);
                    List<ItemStack> items = new ArrayList<>();
                    for (int j = 0; j < itemsTag.tagCount(); j++)
                    {
                        NBTTagCompound itemTag = itemsTag.getCompoundTagAt(j);
                        ItemStack stack = new ItemStack(itemTag);
                        items.add(stack.isEmpty() ? ItemStack.EMPTY : stack.copy());
                    }
                    if (key != null && !key.isEmpty())
                    {
                        menuCache.put(key, new CachedMenu(title, size, items, hash));
                    }
                }
            }
            if (root.hasKey("Custom", 10))
            {
                NBTTagCompound custom = root.getCompoundTag("Custom");
                String title = custom.getString("Title");
                int size = custom.getInteger("Size");
                String hash = custom.getString("Hash");
                NBTTagList itemsTag = custom.getTagList("Items", 10);
                List<ItemStack> items = new ArrayList<>();
                for (int j = 0; j < itemsTag.tagCount(); j++)
                {
                    NBTTagCompound itemTag = itemsTag.getCompoundTagAt(j);
                    ItemStack stack = new ItemStack(itemTag);
                    items.add(stack.isEmpty() ? ItemStack.EMPTY : stack.copy());
                }
                customMenuCache = new CachedMenu(title, size, items, hash);
            }
        }
        catch (Exception e)
        {
            // ignore
        }
    }

    private void loadNote()
    {
        if (noteFile == null || !noteFile.exists())
        {
            noteText = "";
            return;
        }
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(noteFile), "UTF-8")))
        {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null)
            {
                if (sb.length() > 0)
                {
                    sb.append('\n');
                }
                sb.append(line);
            }
            noteText = sb.toString();
        }
        catch (Exception e)
        {
            noteText = "";
        }
    }

    private void loadEntries()
    {
        if (entriesFile == null || !entriesFile.exists())
        {
            return;
        }
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(entriesFile), "UTF-8")))
        {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null)
            {
                sb.append(line);
            }
            String json = sb.toString();
            parseEntriesJson(json);
        }
        catch (IOException ignored)
        {
            // ignore
        }
    }

    private void parseEntriesJson(String json)
    {
        if (json == null || json.isEmpty())
        {
            return;
        }
        clearEntryCaches();
        parseEntryArray(json, "\"recent\":[", true);
        parseEntryArray(json, "\"counts\":[", false);
    }

    private void parseEntryArray(String json, String marker, boolean isRecent)
    {
        int start = json.indexOf(marker);
        if (start < 0)
        {
            return;
        }
        start += marker.length();
        int end = json.indexOf("]", start);
        if (end < 0)
        {
            return;
        }
        String body = json.substring(start, end);
        String[] parts = body.split("\\},\\{");
        for (String part : parts)
        {
            String p = part.replace("{", "").replace("}", "");
            String idStr = extractJsonField(p, "id");
            String modeStr = extractJsonField(p, "mode");
            String textStr = extractJsonField(p, "text");
            String keyStr = extractJsonField(p, "key");
            String countStr = extractJsonField(p, "count");
            String scope = normalizeEntryScopeId(idStr);
            if (isRecent)
            {
                if (modeStr != null && textStr != null)
                {
                    try
                    {
                        int mode = Integer.parseInt(modeStr.trim());
                        getRecentEntriesForScope(scope).add(new InputEntry(mode, textStr));
                    }
                    catch (NumberFormatException ignored)
                    {
                        // ignore
                    }
                }
            }
            else
            {
                if (keyStr != null && countStr != null)
                {
                    try
                    {
                        int count = Integer.parseInt(countStr.trim());
                        getEntryCountsForScope(scope).put(keyStr, count);
                    }
                    catch (NumberFormatException ignored)
                    {
                        // ignore
                    }
                }
            }
        }
    }

    private String extractJsonField(String text, String field)
    {
        String needle = "\"" + field + "\":";
        int idx = text.indexOf(needle);
        if (idx < 0)
        {
            return null;
        }
        idx += needle.length();
        if (idx >= text.length())
        {
            return null;
        }
        if (text.charAt(idx) == '"')
        {
            idx++;
            int end = text.indexOf("\"", idx);
            if (end < 0)
            {
                return null;
            }
            String raw = text.substring(idx, end);
            return raw.replace("\\\"", "\"").replace("\\\\", "\\");
        }
        int end = text.indexOf(",", idx);
        if (end < 0)
        {
            end = text.length();
        }
        return text.substring(idx, end);
    }

    private void drawSidePanels(GuiContainer gui, int mouseX, int mouseY)
    {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.fontRenderer == null)
        {
            return;
        }
        int left = getGuiLeft(gui);
        int top = getGuiTop(gui);
        int xSize = getGuiXSize(gui);
        int ySize = getGuiYSize(gui);

        int panelWidth = 120;
        int rightX = left + xSize + 6;
        int rightY = top;
        net.minecraft.client.gui.Gui.drawRect(rightX, rightY, rightX + panelWidth, rightY + ySize, 0x66000000);

        int y = rightY + 6;
        mc.fontRenderer.drawStringWithShadow("Recent", rightX + 4, y, 0xAAAAAA);
        y += 12;
        for (InputEntry entry : getRecentEntries())
        {
            String line = formatEntryLine(entry);
            mc.fontRenderer.drawStringWithShadow(line, rightX + 4, y, 0xFFFFFF);
            y += 10;
            if (y > rightY + ySize - 20)
            {
                break;
            }
        }

        y += 4;
        mc.fontRenderer.drawStringWithShadow("Frequent", rightX + 4, y, 0xAAAAAA);
        y += 12;
        for (InputEntry entry : getFrequentEntries())
        {
            String line = formatEntryLine(entry);
            mc.fontRenderer.drawStringWithShadow(line, rightX + 4, y, 0xFFFFFF);
            y += 10;
            if (y > rightY + ySize - 10)
            {
                break;
            }
        }

        int btnWidth = panelWidth - 8;
        int btnHeight = 12;
        int btnX = rightX + 4;
        int btnY2 = rightY + ySize - btnHeight - 4;
        int btnY1 = btnY2 - btnHeight - 2;
        net.minecraft.client.gui.Gui.drawRect(btnX, btnY1, btnX + btnWidth, btnY1 + btnHeight, 0x88000000);
        net.minecraft.client.gui.Gui.drawRect(btnX, btnY2, btnX + btnWidth, btnY2 + btnHeight, 0x88000000);
        mc.fontRenderer.drawStringWithShadow("Export Raw", btnX + 4, btnY1 + 2, 0xFFFFFF);
        mc.fontRenderer.drawStringWithShadow("Export Clean", btnX + 4, btnY2 + 2, 0xFFFFFF);

        if (typePickerActive)
        {
            int leftX = left - panelWidth - 6;
            if (leftX < 4)
            {
                leftX = 4;
            }
            int leftY = top;
            net.minecraft.client.gui.Gui.drawRect(leftX, leftY, leftX + panelWidth, leftY + 80, 0x66000000);
            int ty = leftY + 6;
            mc.fontRenderer.drawStringWithShadow("Type", leftX + 4, ty, 0xAAAAAA);
            ty += 12;
            mc.fontRenderer.drawStringWithShadow("[T] Text", leftX + 4, ty, 0xFFFFFF);
            ty += 10;
            mc.fontRenderer.drawStringWithShadow("[N] Number", leftX + 4, ty, 0xFFFFFF);
            ty += 10;
            mc.fontRenderer.drawStringWithShadow("[V] Variable", leftX + 4, ty, 0xFFFFFF);
            ty += 10;
            mc.fontRenderer.drawStringWithShadow("[A] Array", leftX + 4, ty, 0xFFFFFF);
        }
    }

    private boolean handleSidePanelClick(GuiContainer gui, int mouseX, int mouseY)
    {
        if (!editorModeActive)
        {
            return false;
        }
        if (codeMenuActive && isCodeMenuScreen(gui))
        {
            return false;
        }
        if (handleExportButtons(gui, mouseX, mouseY))
        {
            return true;
        }
        InputEntry entry = getEntryAt(gui, mouseX, mouseY);
        if (entry != null)
        {
            if (inputActive)
            {
                if (isShiftDown() && (entry.mode == INPUT_MODE_VARIABLE || entry.mode == INPUT_MODE_ARRAY))
                {
                    appendVariablePlaceholder(entry.text);
                    return true;
                }
                inputMode = entry.mode;
                inputContext = INPUT_CONTEXT_SLOT;
                inputSlotTemplate = templateForMode(entry.mode);
                setInputText(entry.text);
                submitInputText(false);
                return true;
            }
            if (quickApplyWindowId > 0 && quickApplySlotNumber >= 0
                && gui.inventorySlots.windowId == quickApplyWindowId)
            {
                applyEntryToSlot(entry, gui);
                typePickerActive = false;
                return true;
            }
        }

        if (typePickerActive)
        {
            int picked = getTypeOptionAt(gui, mouseX, mouseY);
            if (picked >= 0)
            {
                Slot slot = findSlotByNumber(gui.inventorySlots, typePickerSlotNumber);
                if (slot != null)
                {
                    ItemStack template = templateForMode(picked);
                    String title = picked == INPUT_MODE_NUMBER ? "Number"
                        : picked == INPUT_MODE_VARIABLE ? "Variable"
                        : picked == INPUT_MODE_ARRAY ? "Array" : "Text";
                    startSlotInput(gui, slot, template, picked, "", title);
                }
                typePickerActive = false;
                return true;
            }
        }
        return false;
    }

    private boolean handleExportButtons(GuiContainer gui, int mouseX, int mouseY)
    {
        if (gui == null)
        {
            return false;
        }
        int left = getGuiLeft(gui);
        int top = getGuiTop(gui);
        int xSize = getGuiXSize(gui);
        int ySize = getGuiYSize(gui);
        int panelWidth = 120;
        int rightX = left + xSize + 6;
        int rightY = top;
        int btnWidth = panelWidth - 8;
        int btnHeight = 12;
        int btnX = rightX + 4;
        int btnY2 = rightY + ySize - btnHeight - 4;
        int btnY1 = btnY2 - btnHeight - 2;
        if (mouseX >= btnX && mouseX <= btnX + btnWidth && mouseY >= btnY1 && mouseY <= btnY1 + btnHeight)
        {
            exportGuiToClipboard(gui, true, false);
            return true;
        }
        if (mouseX >= btnX && mouseX <= btnX + btnWidth && mouseY >= btnY2 && mouseY <= btnY2 + btnHeight)
        {
            exportGuiToClipboard(gui, false, true);
            return true;
        }
        return false;
    }

    private boolean handleShiftEditClick(GuiContainer gui)
    {
        Slot hovered = getSlotUnderMouse(gui);
        if (hovered == null)
        {
            return false;
        }
        if (!hovered.getHasStack())
        {
            typePickerActive = true;
            typePickerWindowId = gui.inventorySlots.windowId;
            typePickerSlotNumber = hovered.slotNumber;
            quickApplyWindowId = gui.inventorySlots.windowId;
            quickApplySlotNumber = hovered.slotNumber;
            return true;
        }

        ItemStack stack = hovered.getStack();
        int mode = getModeForItem(stack);
        if (mode < 0)
        {
            return false;
        }
        String preset;
        if (mode == INPUT_MODE_NUMBER)
        {
            preset = extractNumber(stack.getDisplayName());
        }
        else if (mode == INPUT_MODE_VARIABLE || mode == INPUT_MODE_ARRAY)
        {
            preset = normalizePlainName(stack.getDisplayName());
        }
        else
        {
            preset = toAmpersandCodes(stack.getDisplayName());
        }
        startSlotInput(gui, hovered, stack.copy(), mode, preset, "Edit");
        return true;
    }

    private void applyEntryToSlot(InputEntry entry, GuiContainer gui)
    {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.player == null || mc.playerController == null)
        {
            return;
        }
        if (!mc.playerController.isInCreativeMode() || mc.playerController.getCurrentGameType() != GameType.CREATIVE)
        {
            setActionBar(false, "&cCreative only.", 2000L);
            return;
        }
        Slot slot = findSlotByNumber(gui.inventorySlots, quickApplySlotNumber);
        if (slot == null)
        {
            return;
        }
        ItemStack template = templateForMode(entry.mode);
        ItemStack stack = template.isEmpty() ? new ItemStack(Items.BOOK, 1) : template.copy();
        stack.setCount(1);
        String display = buildEntryDisplay(entry);
        stack.setStackDisplayName(display);
        if (entry.mode == INPUT_MODE_VARIABLE && savedVariableNames.contains(entry.text))
        {
            applySavedVariableTag(stack);
        }
        placeInContainerSlot(mc, gui.inventorySlots, slot.slotNumber, stack);
        recordEntry(entry.mode, entry.text);
    }

    private void placeInContainerSlot(Minecraft mc, Container container, int slotNumber, ItemStack stack)
    {
        Integer tempHotbar = findEmptyHotbarSlot(mc);
        if (tempHotbar == null)
        {
            tempHotbar = mc.player.inventory.currentItem;
        }
        ItemStack original = mc.player.inventory.getStackInSlot(tempHotbar);
        mc.player.inventory.setInventorySlotContents(tempHotbar, stack);
        sendCreativeSlotUpdate(mc, tempHotbar, stack);

        Integer invSlotIndex = findContainerSlotForHotbar(container, tempHotbar);
        if (invSlotIndex != null)
        {
            mc.playerController.windowClick(container.windowId, invSlotIndex, 0, ClickType.PICKUP, mc.player);
            mc.playerController.windowClick(container.windowId, slotNumber, 0, ClickType.PICKUP, mc.player);
            if (!mc.player.inventory.getItemStack().isEmpty())
            {
                mc.playerController.windowClick(container.windowId, invSlotIndex, 0, ClickType.PICKUP, mc.player);
            }
        }

        mc.player.inventory.setInventorySlotContents(tempHotbar, original);
        sendCreativeSlotUpdate(mc, tempHotbar, original);
    }

    private int getModeForItem(ItemStack stack)
    {
        if (stack == null || stack.isEmpty())
        {
            return -1;
        }
        if (stack.getItem() == Items.SLIME_BALL)
        {
            return INPUT_MODE_NUMBER;
        }
        if (stack.getItem() == Items.MAGMA_CREAM)
        {
            return INPUT_MODE_VARIABLE;
        }
        if (stack.getItem() == Items.ITEM_FRAME)
        {
            return INPUT_MODE_ARRAY;
        }
        if (stack.getItem() == Items.BOOK)
        {
            return INPUT_MODE_TEXT;
        }
        if (stack.getItem() == Items.PAPER)
        {
            return INPUT_MODE_LOCATION;
        }
        return -1;
    }

    private ItemStack templateForMode(int mode)
    {
        if (mode == INPUT_MODE_NUMBER)
        {
            ItemStack cached = getCachedTemplateForItem(Items.SLIME_BALL);
            return cached.isEmpty() ? new ItemStack(Items.SLIME_BALL, 1) : cached;
        }
        if (mode == INPUT_MODE_VARIABLE)
        {
            ItemStack cached = getCachedTemplateForItem(Items.MAGMA_CREAM);
            return cached.isEmpty() ? new ItemStack(Items.MAGMA_CREAM, 1) : cached;
        }
        if (mode == INPUT_MODE_ARRAY)
        {
            ItemStack cached = getCachedTemplateForItem(Items.ITEM_FRAME);
            return cached.isEmpty() ? new ItemStack(Items.ITEM_FRAME, 1) : cached;
        }
        if (mode == INPUT_MODE_TEXT)
        {
            ItemStack cached = getCachedTemplateForItem(Items.BOOK);
            return cached.isEmpty() ? new ItemStack(Items.BOOK, 1) : cached;
        }
        if (mode == INPUT_MODE_LOCATION)
        {
            ItemStack cached = getCachedTemplateForItem(Items.PAPER);
            return cached.isEmpty() ? new ItemStack(Items.PAPER, 1) : cached;
        }
        return new ItemStack(Items.BOOK, 1);
    }

    private ItemStack getCachedTemplateForItem(Item item)
    {
        if (item == null)
        {
            return ItemStack.EMPTY;
        }
        if (customMenuCache != null)
        {
            for (ItemStack stack : customMenuCache.items)
            {
                if (!stack.isEmpty() && stack.getItem() == item)
                {
                    return stack.copy();
                }
            }
        }
        if (codeMenuInventory != null)
        {
            for (int i = 0; i < codeMenuInventory.getSizeInventory(); i++)
            {
                ItemStack stack = codeMenuInventory.getStackInSlot(i);
                if (!stack.isEmpty() && stack.getItem() == item)
                {
                    return stack.copy();
                }
            }
        }
        return ItemStack.EMPTY;
    }

    private String buildEntryDisplay(InputEntry entry)
    {
        if (entry.mode == INPUT_MODE_NUMBER)
        {
            return applyColorCodes("&c" + entry.text);
        }
        if (entry.mode == INPUT_MODE_LOCATION)
        {
            return applyColorCodes("&b" + entry.text);
        }
        if (entry.mode == INPUT_MODE_VARIABLE || entry.mode == INPUT_MODE_ARRAY)
        {
            return applyColorCodes("&r" + entry.text);
        }
        return normalizeTextName(entry.text);
    }

    private String formatEntryLine(InputEntry entry)
    {
        char label = entry.mode == INPUT_MODE_NUMBER ? 'N'
            : entry.mode == INPUT_MODE_VARIABLE ? 'V'
            : entry.mode == INPUT_MODE_ARRAY ? 'A'
            : entry.mode == INPUT_MODE_LOCATION ? 'L' : 'T';
        String display = buildEntryDisplay(entry);
        return "\u00a77[" + label + "]\u00a7r " + display;
    }

    private InputEntry getEntryAt(GuiContainer gui, int mouseX, int mouseY)
    {
        int left = getGuiLeft(gui);
        int top = getGuiTop(gui);
        int xSize = getGuiXSize(gui);
        int ySize = getGuiYSize(gui);
        int panelWidth = 120;
        int rightX = left + xSize + 6;
        int rightY = top;
        if (mouseX < rightX || mouseX > rightX + panelWidth || mouseY < rightY || mouseY > rightY + ySize)
        {
            return null;
        }
        int y = rightY + 6 + 12;
        for (InputEntry entry : getRecentEntries())
        {
            if (mouseY >= y && mouseY <= y + 10)
            {
                return entry;
            }
            y += 10;
        }
        y += 4 + 12;
        for (InputEntry entry : getFrequentEntries())
        {
            if (mouseY >= y && mouseY <= y + 10)
            {
                return entry;
            }
            y += 10;
        }
        return null;
    }

    private int getTypeOptionAt(GuiContainer gui, int mouseX, int mouseY)
    {
        if (!typePickerActive)
        {
            return -1;
        }
        int left = getGuiLeft(gui);
        int top = getGuiTop(gui);
        int panelWidth = 120;
        int leftX = left - panelWidth - 6;
        if (leftX < 4)
        {
            leftX = 4;
        }
        int leftY = top;
        if (mouseX < leftX || mouseX > leftX + panelWidth || mouseY < leftY || mouseY > leftY + 80)
        {
            return -1;
        }
        int y = leftY + 6 + 12;
        if (mouseY >= y && mouseY <= y + 10)
        {
            return INPUT_MODE_TEXT;
        }
        y += 10;
        if (mouseY >= y && mouseY <= y + 10)
        {
            return INPUT_MODE_NUMBER;
        }
        y += 10;
        if (mouseY >= y && mouseY <= y + 10)
        {
            return INPUT_MODE_VARIABLE;
        }
        y += 10;
        if (mouseY >= y && mouseY <= y + 10)
        {
            return INPUT_MODE_ARRAY;
        }
        return -1;
    }

    private int getScaledMouseX(Minecraft mc, GuiScreen gui)
    {
        if (mc == null || gui == null)
        {
            return 0;
        }
        return Mouse.getEventX() * gui.width / mc.displayWidth;
    }

    private int getScaledMouseY(Minecraft mc, GuiScreen gui)
    {
        if (mc == null || gui == null)
        {
            return 0;
        }
        return gui.height - Mouse.getEventY() * gui.height / mc.displayHeight - 1;
    }

    private int getGuiLeft(GuiContainer gui)
    {
        return getGuiIntField(gui, "guiLeft", "field_147003_i", guiLeftField);
    }

    private int getGuiTop(GuiContainer gui)
    {
        return getGuiIntField(gui, "guiTop", "field_147009_r", guiTopField);
    }

    private int getGuiXSize(GuiContainer gui)
    {
        return getGuiIntField(gui, "xSize", "field_146999_f", xSizeField);
    }

    private int getGuiYSize(GuiContainer gui)
    {
        return getGuiIntField(gui, "ySize", "field_147000_g", ySizeField);
    }

    private int getGuiIntField(GuiContainer gui, String name, String obf, java.lang.reflect.Field cache)
    {
        try
        {
            if (cache == null)
            {
                try
                {
                    cache = GuiContainer.class.getDeclaredField(name);
                }
                catch (NoSuchFieldException e)
                {
                    cache = GuiContainer.class.getDeclaredField(obf);
                }
                cache.setAccessible(true);
                if ("guiLeft".equals(name))
                {
                    guiLeftField = cache;
                }
                else if ("guiTop".equals(name))
                {
                    guiTopField = cache;
                }
                else if ("xSize".equals(name))
                {
                    xSizeField = cache;
                }
                else if ("ySize".equals(name))
                {
                    ySizeField = cache;
                }
            }
            Object value = cache.get(gui);
            return value instanceof Integer ? (Integer) value : 0;
        }
        catch (Exception e)
        {
            return 0;
        }
    }

    private void renderChestPreview(Minecraft mc, ChestCache cache, BlockPos pos)
    {
        if (mc == null || mc.fontRenderer == null || cache == null || cache.items == null)
        {
            return;
        }
        boolean noDepth = chestHoloUseGuiRender;
        if (isPlayerSouthOf(pos))
        {
            renderChestPreviewFace(mc, cache, pos, 0.0F, -0.06F, chestHoloScale, chestHoloTextWidth, noDepth);
        }
        else
        {
            renderChestPreviewFace(mc, cache, pos, 180.0F, 0.06F, chestHoloScale, chestHoloTextWidth, noDepth);
        }
    }

    private void renderChestPreviewFace(Minecraft mc, ChestCache cache, BlockPos pos, float yaw, float zOffset,
        float scale, int textWidth, boolean disableDepth)
    {
        int size = cache.items.size();
        int rows = Math.max(1, (int) Math.ceil(size / 9.0));
        rows = Math.min(rows, 6);
        int cols = 9;

        int pad = 4;
        int slot = 18;
        int guiWidth = cols * slot + pad * 2;
        int guiHeight = rows * slot + pad * 2 + 12;

        double x = pos.getX() + 0.5;
        double y = getChestYOffset(pos);
        double z = pos.getZ() + 0.5;
        double forward = 0.51;
        if (yaw == 0.0F)
        {
            z -= forward;
        }
        else
        {
            z += forward;
            z -= 1.0;
        }
        z += 0.5;
        z += 0.5;

        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, z);
        GlStateManager.rotate(yaw, 0.0F, 1.0F, 0.0F);
        GlStateManager.translate(0.0F, 0.0F, zOffset);
        float axisScale = Math.abs(scale);
        GlStateManager.scale(scale, -axisScale, axisScale);
        GlStateManager.disableLighting();
        if (disableDepth)
        {
            GlStateManager.disableDepth();
            GlStateManager.depthMask(false);
        }
        else
        {
            GlStateManager.enableDepth();
            GlStateManager.depthMask(true);
        }
        GlStateManager.enableBlend();
        GlStateManager.disableCull();
        GlStateManager.enablePolygonOffset();
        GlStateManager.doPolygonOffset(-1.0F, -10.0F);
        GlStateManager.color(1f, 1f, 1f, 1f);

        int left = -guiWidth / 2;
        int top = -guiHeight / 2;
        Gui.drawRect(left, top, left + guiWidth, top + guiHeight, 0xCC000000);
        Gui.drawRect(left, top, left + guiWidth, top + 12, 0xFF111111);
        String label = (cache.label == null || cache.label.trim().isEmpty()) ? CHEST_HOLO_LABEL : cache.label;
        mc.fontRenderer.drawStringWithShadow(label, left + 6, top + 2, chestHoloTextColor);

        int textMaxWidth = cols * slot;
        renderTextColumn(mc, cache.items, left + pad, top + 12, cols, slot, textMaxWidth, guiWidth, guiHeight);
        GlStateManager.disablePolygonOffset();
        GlStateManager.enableCull();
        GlStateManager.enableLighting();
        GlStateManager.disableBlend();
        if (disableDepth)
        {
            GlStateManager.enableDepth();
            GlStateManager.depthMask(true);
        }
        GlStateManager.popMatrix();
    }

    private void renderChestPreviewWithFbo(Minecraft mc, ChestCache cache, BlockPos pos)
    {
        if (mc == null || cache == null || pos == null)
        {
            return;
        }
        if (chestHoloUseGuiRender)
        {
            renderChestPreview(mc, cache, pos);
            if (debugUi)
            {
                lastHoloInfo = "holo key=" + cache.dim + ":" + pos.toString() + " mode=gui";
            }
            return;
        }
        String key = cache.dim + ":" + pos.toString();
        if (chestHoloUseTestPipeline)
        {
            TestChestHolo holo = chestTestHolos.get(key);
            if (holo == null || holo.cache != cache)
            {
                holo = new TestChestHolo(pos, cache, true, lastTestScale, lastTestTextWidth, lastTestTexSize);
                chestTestHolos.put(key, holo);
            }
            renderChestPreviewFbo(mc, holo);
            if (debugUi)
            {
                lastHoloInfo = "holo key=" + key + " tex=" + lastTestTexSize + " w=" + lastTestTextWidth
                    + " s=" + lastTestScale + " re=forced mode=test font="
                    + (chestHoloSmoothFont ? "linear" : "nearest");
            }
            return;
        }
        ChestPreviewFbo preview = chestPreviewFbos.get(key);
        if (preview == null)
        {
            preview = new ChestPreviewFbo();
            chestPreviewFbos.put(key, preview);
        }
        String hash = buildItemHash(cache.items);
        int size = chestHoloTexSize;
        int textWidth = chestHoloTextWidth;
        boolean rerender = false;
        if (preview.fbo == null || preview.fbo.framebufferTextureWidth != size)
        {
            if (preview.fbo != null)
            {
                preview.fbo.deleteFramebuffer();
            }
            preview.fbo = new Framebuffer(size, size, true);
            preview.lastHash = null;
            preview.lastTexSize = size;
            rerender = true;
        }
        if (chestHoloForceRerender)
        {
            renderChestPreviewToFramebuffer(mc, cache, preview.fbo, size, size, textWidth);
            preview.lastHash = hash;
            preview.lastTextWidth = textWidth;
            preview.lastTexSize = size;
            rerender = true;
        }
        else if (preview.lastHash == null || !preview.lastHash.equals(hash)
            || preview.lastTextWidth != textWidth || preview.lastTexSize != size)
        {
            renderChestPreviewToFramebuffer(mc, cache, preview.fbo, size, size, textWidth);
            preview.lastHash = hash;
            preview.lastTextWidth = textWidth;
            preview.lastTexSize = size;
            rerender = true;
        }
        renderFramebufferQuad(mc, pos, preview.fbo, size, size, chestHoloScale);
        if (debugUi)
        {
            lastHoloInfo = "holo key=" + key + " tex=" + size + " w=" + textWidth + " s=" + chestHoloScale
                + " re=" + rerender + " force=" + chestHoloForceRerender + " mode=cache font="
                + (chestHoloSmoothFont ? "linear" : "nearest");
        }
    }

    private static String toAmpersandCodes(String text)
    {
        if (text == null)
        {
            return "";
        }
        return text.replace("\u00a7", "&");
    }

    private static String extractDigits(String text)
    {
        if (text == null)
        {
            return "";
        }
        String clean = TextFormatting.getTextWithoutFormattingCodes(text);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < clean.length(); i++)
        {
            char c = clean.charAt(i);
            if (c >= '0' && c <= '9')
            {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static String extractNumber(String text)
    {
        if (text == null)
        {
            return "";
        }
        String clean = TextFormatting.getTextWithoutFormattingCodes(text);
        if (clean == null)
        {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        boolean dot = false;
        boolean minus = false;
        for (int i = 0; i < clean.length(); i++)
        {
            char c = clean.charAt(i);
            if (c >= '0' && c <= '9')
            {
                sb.append(c);
            }
            else if (c == '-' && !minus && sb.length() == 0)
            {
                sb.append(c);
                minus = true;
            }
            else if (c == '.' && !dot)
            {
                sb.append(c);
                dot = true;
            }
        }
        return sb.toString();
    }

    private void submitInputText(boolean giveExtra)
    {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.player == null || mc.playerController == null)
        {
            setInputActive(false);
            return;
        }
        if (!mc.playerController.isInCreativeMode() || mc.playerController.getCurrentGameType() != GameType.CREATIVE)
        {
            setActionBar(false, "&cCreative only.", 2000L);
            setInputActive(false);
            return;
        }
        String raw = getInputText().trim();
        if (raw.isEmpty())
        {
            setInputActive(false);
            return;
        }
        String finalRaw = raw;
        String display;
        if (inputMode == INPUT_MODE_NUMBER)
        {
            String number = extractNumber(raw);
            if (number.isEmpty())
            {
                setInputActive(false);
                return;
            }
            finalRaw = number;
            display = applyColorCodes("&c" + number);
        }
        else if (inputMode == INPUT_MODE_LOCATION)
        {
            finalRaw = raw.trim();
            if (finalRaw.isEmpty())
            {
                setInputActive(false);
                return;
            }
            display = finalRaw;
        }
        else if (inputMode == INPUT_MODE_VARIABLE || inputMode == INPUT_MODE_ARRAY)
        {
            finalRaw = normalizePlainName(raw);
            if (finalRaw.isEmpty())
            {
                setInputActive(false);
                return;
            }
            display = applyColorCodes("&r" + finalRaw);
        }
        else
        {
            display = normalizeTextName(finalRaw);
        }

        if (inputContext == INPUT_CONTEXT_GIVE)
        {
            ItemStack give = inputGiveTemplate.isEmpty() ? new ItemStack(Items.BOOK, 1) : inputGiveTemplate.copy();
            give.setCount(1);
            give.setStackDisplayName(display);
            if (inputMode == INPUT_MODE_VARIABLE && (inputSaveVariable || savedVariableNames.contains(finalRaw)))
            {
                applySavedVariableTag(give);
                if (inputSaveVariable)
                {
                    savedVariableNames.add(finalRaw);
                }
            }
            else if (inputMode == INPUT_MODE_VARIABLE)
            {
                removeSavedVariableTag(give);
            }
            giveItemToHotbar(mc, give);
            recordEntry(inputMode, finalRaw);
            if (giveExtra)
            {
                ItemStack extra = give.copy();
                extra.setCount(64);
                giveExtraIfMissing(mc, extra, display);
            }
            setInputActive(false);
            return;
        }

        if (mc.player.openContainer == null)
        {
            if (debugUi)
            {
                setActionBar(false, "&cInput: no container", 1500L);
            }
            setInputActive(false);
            return;
        }
        if (mc.player.openContainer.windowId != inputTargetWindowId)
        {
            if (shulkerEditActive)
            {
                inputTargetWindowId = mc.player.openContainer.windowId;
            }
            else
            {
                if (debugUi)
                {
                    setActionBar(false, "&cInput: windowId mismatch", 1500L);
                }
                setInputActive(false);
                return;
            }
        }

        Slot target = findSlotByNumber(mc.player.openContainer, inputTargetSlotNumber);
        if (target == null)
        {
            if (debugUi)
            {
                setActionBar(false, "&cInput: slot not found", 1500L);
            }
            setInputActive(false);
            return;
        }

        ItemStack placed = inputSlotTemplate.isEmpty() ? new ItemStack(Items.BOOK, 1) : inputSlotTemplate.copy();
        placed.setCount(1);
        placed.setStackDisplayName(display);
        if (inputMode == INPUT_MODE_VARIABLE && (inputSaveVariable || savedVariableNames.contains(finalRaw)))
        {
            applySavedVariableTag(placed);
            if (inputSaveVariable)
            {
                savedVariableNames.add(finalRaw);
            }
        }
        else if (inputMode == INPUT_MODE_VARIABLE)
        {
            removeSavedVariableTag(placed);
        }

        Integer tempHotbar = findEmptyHotbarSlot(mc);
        if (tempHotbar == null)
        {
            tempHotbar = mc.player.inventory.currentItem;
        }
        ItemStack original = mc.player.inventory.getStackInSlot(tempHotbar);

        mc.player.inventory.setInventorySlotContents(tempHotbar, placed);
        sendCreativeSlotUpdate(mc, tempHotbar, placed);

        Integer invSlotIndex = findContainerSlotForHotbar(mc.player.openContainer, tempHotbar);
        if (invSlotIndex != null)
        {
            mc.playerController.windowClick(mc.player.openContainer.windowId, invSlotIndex, 0, ClickType.PICKUP,
                mc.player);
            mc.playerController.windowClick(mc.player.openContainer.windowId, target.slotNumber, 0, ClickType.PICKUP,
                mc.player);
            if (!mc.player.inventory.getItemStack().isEmpty())
            {
                mc.playerController.windowClick(mc.player.openContainer.windowId, invSlotIndex, 0, ClickType.PICKUP,
                    mc.player);
            }
        }

        mc.player.inventory.setInventorySlotContents(tempHotbar, original);
        sendCreativeSlotUpdate(mc, tempHotbar, original);

        recordEntry(inputMode, finalRaw);
        if (giveExtra)
        {
            ItemStack extra = placed.copy();
            extra.setCount(64);
            giveExtraIfMissing(mc, extra, display);
        }

        if (shulkerEditPos != null)
        {
            putShulkerHolo(shulkerEditDim, shulkerEditPos, display, shulkerEditColor);
            shulkerEditActive = false;
            shulkerEditWindowId = -1;
            shulkerEditSlotNumber = -1;
            shulkerEditPos = null;
        }

        setInputActive(false);
    }

    private Slot findSlotByNumber(Container container, int slotNumber)
    {
        if (container == null)
        {
            return null;
        }
        for (Slot slot : container.inventorySlots)
        {
            if (slot.slotNumber == slotNumber)
            {
                return slot;
            }
        }
        return null;
    }

    private Integer findContainerSlotForHotbar(Container container, int hotbarIndex)
    {
        if (container == null)
        {
            return null;
        }
        for (Slot slot : container.inventorySlots)
        {
            if (slot.inventory == Minecraft.getMinecraft().player.inventory && slot.getSlotIndex() == hotbarIndex)
            {
                return slot.slotNumber;
            }
        }
        return null;
    }

    private Integer findEmptyHotbarSlot(Minecraft mc)
    {
        for (int i = 0; i < 9; i++)
        {
            ItemStack stack = mc.player.inventory.getStackInSlot(i);
            if (stack == null || stack.isEmpty())
            {
                return i;
            }
        }
        return null;
    }

    private Integer findEmptyInventorySlot(Minecraft mc)
    {
        int size = mc.player.inventory.getSizeInventory();
        for (int i = 9; i < size; i++)
        {
            ItemStack stack = mc.player.inventory.getStackInSlot(i);
            if (stack == null || stack.isEmpty())
            {
                return i;
            }
        }
        return null;
    }

    private void giveItemToHotbar(Minecraft mc, ItemStack stack)
    {
        Integer slot = findEmptyHotbarSlot(mc);
        if (slot == null)
        {
            slot = findEmptyInventorySlot(mc);
        }
        if (slot == null)
        {
            setActionBar(false, "&eNo empty inventory slot.", 2000L);
            return;
        }
        mc.player.inventory.setInventorySlotContents(slot, stack);
        sendCreativeSlotUpdate(mc, slot, stack);
    }

    private void giveExtraIfMissing(Minecraft mc, ItemStack stack, String display)
    {
        if (!inventoryHasDisplayName(mc, display))
        {
            Integer extraSlot = findEmptyHotbarSlot(mc);
            if (extraSlot == null)
            {
                extraSlot = findEmptyInventorySlot(mc);
            }
            if (extraSlot == null)
            {
                setActionBar(false, "&eNo empty inventory slot for x64.", 2000L);
                return;
            }
            mc.player.inventory.setInventorySlotContents(extraSlot, stack);
            sendCreativeSlotUpdate(mc, extraSlot, stack);
        }
    }

    private boolean inventoryHasDisplayName(Minecraft mc, String display)
    {
        for (int i = 0; i < mc.player.inventory.getSizeInventory(); i++)
        {
            ItemStack stack = mc.player.inventory.getStackInSlot(i);
            if (!stack.isEmpty() && display.equals(stack.getDisplayName()))
            {
                return true;
            }
        }
        return false;
    }

    private String normalizeTextName(String raw)
    {
        String text = raw.trim();
        if (!text.startsWith("&r") && !text.startsWith("\u00a7r"))
        {
            text = "&r" + text;
        }
        return applyColorCodes(text);
    }

    private CommandResult executeCommand(String cmdFinal, String asPlayer)
    {
        if (cmdFinal == null || cmdFinal.trim().isEmpty())
        {
            return new CommandResult(false, "none");
        }
        if (mcServer != null)
        {
            mcServer.addScheduledTask(() -> {
                ICommandSender sender = mcServer;
                if (asPlayer != null && mcServer.getPlayerList() != null)
                {
                    EntityPlayerMP player = mcServer.getPlayerList().getPlayerByUsername(asPlayer);
                    if (player != null)
                    {
                        sender = player;
                    }
                }
                mcServer.getCommandManager().executeCommand(sender, cmdFinal);
            });
            return new CommandResult(true, "server");
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (mc != null && mc.player != null)
        {
            String clientCmd = "/" + cmdFinal;
            mc.addScheduledTask(() -> mc.player.sendChatMessage(clientCmd));
            lastApiChatSentMs = System.currentTimeMillis();
            return new CommandResult(true, "client");
        }
        return new CommandResult(false, "none");
    }

    private boolean sendChatMessage(String text)
    {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc != null && mc.player != null)
        {
            mc.addScheduledTask(() -> mc.player.sendChatMessage(text));
            lastApiChatSentMs = System.currentTimeMillis();
            return true;
        }
        return false;
    }

    private boolean broadcastServerMessage(String text)
    {
        if (mcServer != null && mcServer.getPlayerList() != null)
        {
            mcServer.getPlayerList().sendMessage(new TextComponentString(text));
            return true;
        }
        return false;
    }

    @SubscribeEvent
    public void onRenderOverlay(RenderGameOverlayEvent.Text event)
    {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.fontRenderer == null)
        {
            return;
        }
        if (debugUi)
        {
            String dbg = "dev=" + editorModeActive + " creative=" + (mc.playerController != null
                && mc.playerController.isInCreativeMode())
                + " hotbar2=" + enableSecondHotbar
                + " input=" + inputActive
                + " codeMenu=" + codeMenuActive
                + " cache=" + (customMenuCache != null);
            mc.fontRenderer.drawStringWithShadow(dbg, 4, 4, 0xFFFFFF);
            String dbg2 = "cacheRoot=" + (cachePathRoot == null ? "-" : cachePathRoot)
                + " pending=" + (pendingCacheKey == null ? "-" : pendingCacheKey);
            mc.fontRenderer.drawStringWithShadow(dbg2, 4, 14, 0xFFFFFF);
            long sinceDev = System.currentTimeMillis() - lastDevCommandMs;
            mc.fontRenderer.drawStringWithShadow("devMs=" + sinceDev, 4, 24, 0xFFFFFF);
            String dbg3 = "pendingDev=" + pendingDev + " cacheSize=" + chestCaches.size();
            mc.fontRenderer.drawStringWithShadow(dbg3, 4, 34, 0xFFFFFF);
            String dbg4 = "awaitCache=" + awaitingCacheSnapshot + " ticks=" + cacheOpenTicks;
            mc.fontRenderer.drawStringWithShadow(dbg4, 4, 44, 0xFFFFFF);
            String dbg5 = "lastClick=" + (lastClickedPos == null ? "-" : lastClickedPos.toString());
            mc.fontRenderer.drawStringWithShadow(dbg5, 4, 54, 0xFFFFFF);
            String dbg6 = "pendingChest=" + pendingChestSnapshot + " chest=" + lastClickedChest;
            mc.fontRenderer.drawStringWithShadow(dbg6, 4, 64, 0xFFFFFF);
            String screenName = mc.currentScreen == null ? "-" : mc.currentScreen.getClass().getSimpleName();
            String contName = (mc.player == null || mc.player.openContainer == null) ? "-" :
                mc.player.openContainer.getClass().getSimpleName();
            mc.fontRenderer.drawStringWithShadow("screen=" + screenName, 4, 74, 0xFFFFFF);
            mc.fontRenderer.drawStringWithShadow("container=" + contName, 4, 84, 0xFFFFFF);
            mc.fontRenderer.drawStringWithShadow("snap=" + lastSnapshotInfo, 4, 94, 0xFFFFFF);
            mc.fontRenderer.drawStringWithShadow("devUntil=" + pendingDevUntilMs, 4, 104, 0xFFFFFF);
            mc.fontRenderer.drawStringWithShadow("sign=" + lastClickedIsSign, 4, 114, 0xFFFFFF);
            mc.fontRenderer.drawStringWithShadow("holo=" + lastHoloInfo, 4, 124, 0xFFFFFF);
            if (editorModeActive)
            {
                String sbLine = getScoreboardLine(12);
                if (sbLine != null && !sbLine.isEmpty())
                {
                    mc.fontRenderer.drawStringWithShadow("sb12=" + sbLine, 4, 134, 0xFFFFFF);
                }
            }
        }
        long now = System.currentTimeMillis();
        String text1 = now <= actionBarExpireMs ? actionBarText : "";
        String text2 = now <= actionBar2ExpireMs ? actionBar2Text : "";
        if (text1.isEmpty() && text2.isEmpty())
        {
            return;
        }

        ScaledResolution res = event.getResolution();
        int centerX = res.getScaledWidth() / 2;
        int baseY = res.getScaledHeight() - 59;
        int lineHeight = mc.fontRenderer.FONT_HEIGHT + 2;

        if (!text1.isEmpty())
        {
            int w1 = mc.fontRenderer.getStringWidth(text1);
            mc.fontRenderer.drawStringWithShadow(text1, centerX - (w1 / 2), baseY, 0xFFFFFF);
        }
        if (!text2.isEmpty())
        {
            int w2 = mc.fontRenderer.getStringWidth(text2);
            mc.fontRenderer.drawStringWithShadow(text2, centerX - (w2 / 2), baseY + lineHeight, 0xFFFFFF);
        }
    }

    @SubscribeEvent
    public void onRenderWorld(RenderWorldLastEvent event)
    {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.player == null || mc.world == null)
        {
            return;
        }
        double px = mc.player.lastTickPosX + (mc.player.posX - mc.player.lastTickPosX) * event.getPartialTicks();
        double py = mc.player.lastTickPosY + (mc.player.posY - mc.player.lastTickPosY) * event.getPartialTicks();
        double pz = mc.player.lastTickPosZ + (mc.player.posZ - mc.player.lastTickPosZ) * event.getPartialTicks();

        GlStateManager.pushMatrix();
        GlStateManager.translate(-px, -py, -pz);
        RenderHelper.disableStandardItemLighting();
        if (editorModeActive)
        {
            for (ChestCache cache : chestCaches.values())
            {
                if (cache.dim != mc.world.provider.getDimension())
                {
                    continue;
                }
                BlockPos pos = cache.pos;
                if (pos == null)
                {
                    continue;
                }
                if (mc.player.getDistanceSq(pos) > CHEST_PREVIEW_RANGE * CHEST_PREVIEW_RANGE)
                {
                    continue;
                }
                renderChestPreviewWithFbo(mc, cache, pos);
            }
            if (testHoloActive && testHoloPos != null)
            {
                renderTestHolo(mc, testHoloPos);
            }
            if (!testChestHolos.isEmpty())
            {
                for (TestChestHolo holo : testChestHolos)
                {
                    renderChestHolo(mc, holo);
                }
            }
        }
        if (!shulkerHolos.isEmpty())
        {
            int dim = mc.world == null ? 0 : mc.world.provider.getDimension();
            for (ShulkerHolo holo : shulkerHolos.values())
            {
                if (holo != null && holo.dim == dim)
                {
                    renderShulkerHolo(mc, holo);
                }
            }
        }
        renderSignSearchMarkers(mc);
        GlStateManager.popMatrix();
    }

    private void renderTestHolo(Minecraft mc, BlockPos pos)
    {
        if (mc == null || mc.getRenderItem() == null || mc.fontRenderer == null)
        {
            return;
        }
        ItemStack stack = new ItemStack(Blocks.DIRT, 1);
        double x = pos.getX() + 0.5;
        double y = pos.getY() + 0.8;
        double z = pos.getZ() + 0.5;

        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, z);
        GlStateManager.rotate(180.0F - mc.getRenderManager().playerViewY, 0.0F, 1.0F, 0.0F);
        GlStateManager.rotate(-mc.getRenderManager().playerViewX, 1.0F, 0.0F, 0.0F);
        GlStateManager.scale(0.5F, 0.5F, 0.5F);
        RenderHelper.enableStandardItemLighting();
        mc.getRenderItem().renderItem(stack, ItemCameraTransforms.TransformType.FIXED);
        RenderHelper.disableStandardItemLighting();
        GlStateManager.popMatrix();

        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y + 0.6, z);
        GlStateManager.rotate(180.0F - mc.getRenderManager().playerViewY, 0.0F, 1.0F, 0.0F);
        GlStateManager.rotate(-mc.getRenderManager().playerViewX, 1.0F, 0.0F, 0.0F);
        GlStateManager.scale(-0.025F, -0.025F, 0.025F);
        GlStateManager.disableLighting();
        GlStateManager.disableDepth();
        GlStateManager.enableBlend();
        mc.fontRenderer.drawStringWithShadow(HOLO_LABEL, -mc.fontRenderer.getStringWidth(HOLO_LABEL) / 2, 0, 0xFFFFFF);
        GlStateManager.enableDepth();
        GlStateManager.popMatrix();
    }

    private void putShulkerHolo(int dim, BlockPos pos, String text, int color)
    {
        if (pos == null)
        {
            return;
        }
        String key = dim + ":" + pos.toString();
        String safe = text == null ? "" : text;
        shulkerHolos.put(key, new ShulkerHolo(dim, pos, safe, color));
        shulkerHoloDirty = true;
        setActionBar(true, "&aShulker holo set", 1500L);
    }

    private void renderShulkerHolo(Minecraft mc, ShulkerHolo holo)
    {
        if (mc == null || mc.fontRenderer == null || holo == null || holo.pos == null)
        {
            return;
        }
        double x = holo.pos.getX() + 0.5;
        double y = holo.pos.getY() + shulkerHoloYOffset;
        double z = holo.pos.getZ() + 0.5 + shulkerHoloZOffset;
        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, z);
        if (shulkerHoloBillboard)
        {
            GlStateManager.rotate(180.0F - mc.getRenderManager().playerViewY, 0.0F, 1.0F, 0.0F);
            GlStateManager.rotate(-mc.getRenderManager().playerViewX, 1.0F, 0.0F, 0.0F);
        }
        else
        {
            GlStateManager.rotate(180.0F, 0.0F, 1.0F, 0.0F);
        }
        GlStateManager.scale(shulkerHoloScale, -shulkerHoloScale, shulkerHoloScale);
        GlStateManager.disableLighting();
        if (!shulkerHoloCull)
        {
            GlStateManager.disableCull();
        }
        if (!shulkerHoloDepth)
        {
            GlStateManager.disableDepth();
        }
        GlStateManager.enableBlend();
        GlStateManager.color(1f, 1f, 1f, 1f);
        String text = holo.text == null ? "" : holo.text;
        int width = mc.fontRenderer.getStringWidth(text);
        mc.fontRenderer.drawStringWithShadow(text, -width / 2, 0, holo.color);
        GlStateManager.enableDepth();
        if (!shulkerHoloCull)
        {
            GlStateManager.enableCull();
        }
        GlStateManager.popMatrix();
    }

    private void renderSignSearchMarkers(Minecraft mc)
    {
        if (mc == null || mc.fontRenderer == null || mc.world == null)
        {
            return;
        }
        if (signSearchQuery == null || signSearchMatches.isEmpty())
        {
            return;
        }
        int dim = mc.world.provider.getDimension();
        if (dim != signSearchDim)
        {
            return;
        }
        for (BlockPos pos : signSearchMatches)
        {
            if (pos == null)
            {
                continue;
            }
            if (mc.player.getDistanceSq(pos) > 256.0 * 256.0)
            {
                continue;
            }
            double x = pos.getX() + 0.5;
            double y = pos.getY() + 1.2;
            double z = pos.getZ() + 0.5;
            GlStateManager.pushMatrix();
            GlStateManager.translate(x, y, z);
            GlStateManager.rotate(180.0F - mc.getRenderManager().playerViewY, 0.0F, 1.0F, 0.0F);
            GlStateManager.rotate(-mc.getRenderManager().playerViewX, 1.0F, 0.0F, 0.0F);
            GlStateManager.scale(-0.02F, -0.02F, 0.02F);
            GlStateManager.disableLighting();
            GlStateManager.disableDepth();
            GlStateManager.enableBlend();
            String label = "SIGN";
            int width = mc.fontRenderer.getStringWidth(label);
            mc.fontRenderer.drawStringWithShadow(label, -width / 2, 0, 0x00FF00);
            GlStateManager.enableDepth();
            GlStateManager.popMatrix();
        }
    }

    private void renderChestHolo(Minecraft mc, TestChestHolo holo)
    {
        if (holo == null || holo.cache == null || holo.pos == null)
        {
            return;
        }
        if (!holo.useFbo)
        {
            float scale = holo.scale > 0 ? holo.scale : CHEST_HOLO_SCALE;
            int textWidth = holo.textWidth > 0 ? holo.textWidth : CHEST_HOLO_TEXT_WIDTH;
            renderChestPreviewFace(mc, holo.cache, holo.pos, 180.0F, 0.0F, scale, textWidth, true);
            return;
        }
        renderChestPreviewFbo(mc, holo);
    }

    private void renderChestPreviewFbo(Minecraft mc, TestChestHolo holo)
    {
        if (mc == null || mc.fontRenderer == null || holo.cache == null)
        {
            return;
        }
        int size = holo.texSize > 0 ? holo.texSize : CHEST_HOLO_TEX_SIZE;
        if (holo.fbo == null || holo.fbo.framebufferTextureWidth != size)
        {
            if (holo.fbo != null)
            {
                holo.fbo.deleteFramebuffer();
            }
            holo.fbo = new Framebuffer(size, size, true);
            holo.lastHash = null;
        }
        String hash = buildItemHash(holo.cache.items);
        if (holo.lastHash == null || !holo.lastHash.equals(hash))
        {
            int textWidth = holo.textWidth > 0 ? holo.textWidth : CHEST_HOLO_TEXT_WIDTH;
            renderChestPreviewToFramebuffer(mc, holo.cache, holo.fbo, size, size, textWidth);
            holo.lastHash = hash;
        }
        float scale = holo.scale > 0 ? holo.scale : CHEST_HOLO_SCALE;
        renderFramebufferQuad(mc, holo.pos, holo.fbo, size, size, scale);
    }

    private void renderChestPreviewToFramebuffer(Minecraft mc, ChestCache cache, Framebuffer fbo, int w, int h,
        int textWidth)
    {
        fbo.bindFramebuffer(true);
        GlStateManager.clearColor(0f, 0f, 0f, 0f);
        GlStateManager.clear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
        GlStateManager.disableDepth();
        GlStateManager.disableLighting();
        GlStateManager.enableBlend();

        GlStateManager.matrixMode(GL11.GL_PROJECTION);
        GlStateManager.pushMatrix();
        GlStateManager.loadIdentity();
        GlStateManager.ortho(0.0D, w, h, 0.0D, 1000.0D, 3000.0D);
        GlStateManager.matrixMode(GL11.GL_MODELVIEW);
        GlStateManager.pushMatrix();
        GlStateManager.loadIdentity();
        GlStateManager.translate(0.0F, 0.0F, -2000.0F);

        int size = cache.items.size();
        int rows = Math.max(1, (int) Math.ceil(size / 9.0));
        rows = Math.min(rows, 6);
        int cols = 9;
        int pad = 4;
        int slot = 18;
        int guiWidth = cols * slot + pad * 2;
        int guiHeight = rows * slot + pad * 2 + 12;
        int left = (w - guiWidth) / 2;
        int top = (h - guiHeight) / 2;

        Gui.drawRect(left, top, left + guiWidth, top + guiHeight, 0xCC000000);
        Gui.drawRect(left, top, left + guiWidth, top + 12, 0xFF111111);
        String label = (cache.label == null || cache.label.trim().isEmpty()) ? CHEST_HOLO_LABEL : cache.label;
        mc.fontRenderer.drawStringWithShadow(label, left + 6, top + 2, chestHoloTextColor);

        int textMaxWidth = cols * slot;
        renderTextColumnToFramebuffer(mc, cache.items, left + pad, top + 12, cols, slot, textMaxWidth, guiWidth, guiHeight);

        GlStateManager.matrixMode(GL11.GL_MODELVIEW);
        GlStateManager.popMatrix();
        GlStateManager.matrixMode(GL11.GL_PROJECTION);
        GlStateManager.popMatrix();
        GlStateManager.matrixMode(GL11.GL_MODELVIEW);

        fbo.unbindFramebuffer();
        mc.getFramebuffer().bindFramebuffer(true);
    }

    private void renderFramebufferQuad(Minecraft mc, BlockPos pos, Framebuffer fbo, int w, int h, float scale)
    {
        if (isPlayerSouthOf(pos))
        {
            renderFramebufferQuadFace(mc, pos, fbo, w, h, 0.0F, false, -0.51F, scale);
        }
        else
        {
            renderFramebufferQuadFace(mc, pos, fbo, w, h, 180.0F, false, 0.51F, -scale);
        }
    }

    private void renderFramebufferQuadFace(Minecraft mc, BlockPos pos, Framebuffer fbo, int w, int h, float yaw,
        boolean flipU, float zOffset, float scale)
    {
        double x = pos.getX() + 0.5;
        double y = getChestYOffset(pos);
        double z = pos.getZ() + 0.5;
        double forward = 0.51;
        if (yaw == 0.0F)
        {
            z -= forward;
        }
        else
        {
            z += forward;
            z -= 1.0;
        }

        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, z);
        GlStateManager.rotate(yaw, 0.0F, 1.0F, 0.0F);
        GlStateManager.translate(0.0F, 0.0F, zOffset);
        float axisScale = Math.abs(scale);
        GlStateManager.scale(scale, -axisScale, axisScale);
        GlStateManager.disableLighting();
        GlStateManager.enableDepth();
        GlStateManager.depthMask(true);
        GlStateManager.enableBlend();
        GlStateManager.disableCull();
        GlStateManager.enablePolygonOffset();
        GlStateManager.doPolygonOffset(-1.0F, -10.0F);

        float halfW = w / 2.0f;
        float halfH = h / 2.0f;
        GlStateManager.bindTexture(fbo.framebufferTexture);
        int fboFilter = chestHoloSmoothFont ? GL11.GL_LINEAR : GL11.GL_NEAREST;
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, fboFilter);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, fboFilter);
        Tessellator tess = Tessellator.getInstance();
        BufferBuilder buffer = tess.getBuffer();
        float u1 = flipU ? 1f : 0f;
        float u2 = flipU ? 0f : 1f;
        buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX);
        buffer.pos(-halfW, -halfH, 0).tex(u1, 1).endVertex();
        buffer.pos(halfW, -halfH, 0).tex(u2, 1).endVertex();
        buffer.pos(halfW, halfH, 0).tex(u2, 0).endVertex();
        buffer.pos(-halfW, halfH, 0).tex(u1, 0).endVertex();
        tess.draw();

        GlStateManager.disablePolygonOffset();
        GlStateManager.enableCull();
        GlStateManager.enableLighting();
        GlStateManager.disableBlend();
        GlStateManager.popMatrix();
    }

    private String buildItemHash(List<ItemStack> items)
    {
        if (items == null)
        {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (ItemStack stack : items)
        {
            if (stack == null || stack.isEmpty())
            {
                sb.append("_");
                continue;
            }
            sb.append(stack.getItem().getRegistryName()).append("#");
            sb.append(stack.getCount()).append(";");
            if (stack.hasTagCompound())
            {
                sb.append(stack.getTagCompound().toString());
            }
            sb.append("|");
        }
        return sb.toString();
    }


    private void renderTextColumn(Minecraft mc, List<ItemStack> items, int left, int top, int cols, int slot,
        int maxWidth, int guiWidth, int guiHeight)
    {
        if (mc == null || mc.fontRenderer == null || items == null)
        {
            return;
        }
        ResourceLocation fontTex = getFontTextureLocation(mc.fontRenderer);
        if (fontTex != null)
        {
            TextureManager tm = mc.getTextureManager();
            tm.bindTexture(fontTex);
            int filter = chestHoloSmoothFont ? GL11.GL_LINEAR : GL11.GL_NEAREST;
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, filter);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, filter);
        }
        int maxLines = 6;
        int fontHeight = mc.fontRenderer.FONT_HEIGHT;
        int count = items.size();
        int gridLeft = left;
        int gridTop = top;
        int gridRight = left + guiWidth - 1;
        int gridBottom = top + guiHeight - 1;
        List<LabelCandidate> candidates = new ArrayList<>();
        for (int idx = 0; idx < count; idx++)
        {
            int row = idx / cols;
            if (row >= maxLines)
            {
                break;
            }
            ItemStack stack = items.get(idx);
            if (stack == null || stack.isEmpty())
            {
                continue;
            }
            if (stack.getItem() == net.minecraft.item.Item.getItemFromBlock(Blocks.STAINED_GLASS_PANE))
            {
                continue;
            }
            String name = getPreviewName(stack);
            if (name == null || name.isEmpty())
            {
                continue;
            }
            String tag = getItemTagColored(stack);
            String lineText = tag + " " + name + TextFormatting.RESET;
            String clipped = mc.fontRenderer.trimStringToWidth(lineText, Math.max(maxWidth, 1024));
            int textWidth = mc.fontRenderer.getStringWidth(clipped);
            int col = idx % cols;
            int rowY = idx / cols;
            int baseX = left + col * slot + (slot - textWidth) / 2;
            int baseY = top + rowY * slot + (slot - fontHeight) / 2;
            candidates.add(new LabelCandidate(idx, clipped, textWidth, fontHeight, baseX, baseY));
        }

        LayoutResult pass1 = layoutCandidates(candidates, gridLeft, gridTop, gridRight, gridBottom);
        LayoutResult finalLayout = pass1;
        if (!pass1.overflow.isEmpty())
        {
            List<Rect> legendReserved = new ArrayList<>();
            for (Label label : pass1.overflow)
            {
                int col = label.slot % cols;
                int row = label.slot / cols;
                int cx = left + col * slot + slot / 2;
                int cy = top + row * slot + slot / 2;
                legendReserved.add(new Rect(cx - 4, cy - 4, 8, 8));
            }
            finalLayout = layoutCandidates(candidates, gridLeft, gridTop, gridRight, gridBottom, legendReserved);
        }

        for (PlacedLabel placed : finalLayout.placed)
        {
            mc.fontRenderer.drawStringWithShadow(placed.text, placed.x, placed.y, chestHoloTextColor);
        }
        if (!finalLayout.overflow.isEmpty())
        {
            renderOverflowLegend(mc, finalLayout.overflow, left, top, cols, slot, fontHeight, guiWidth);
        }
        if (fontTex != null)
        {
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        }
    }

    private String getItemTagColored(ItemStack stack)
    {
        if (stack == null || stack.isEmpty())
        {
            return TextFormatting.GRAY + "[-]";
        }
        if (stack.getItem() == Items.BOOK)
        {
            return TextFormatting.WHITE + "[T]";
        }
        if (stack.getItem() == Items.SLIME_BALL)
        {
            return TextFormatting.RED + "[N]";
        }
        if (stack.getItem() == Items.MAGMA_CREAM)
        {
            return TextFormatting.GOLD + "[V]";
        }
        if (stack.getItem() == Items.ITEM_FRAME)
        {
            return TextFormatting.GREEN + "[A]";
        }
        if (stack.getItem() == Items.PAPER)
        {
            return TextFormatting.WHITE + "[P]";
        }
        if (stack.getItem() == Items.GLASS_BOTTLE)
        {
            return TextFormatting.AQUA + "[B]";
        }
        if (stack.getItem() == Items.NETHER_STAR)
        {
            return TextFormatting.YELLOW + "[S]";
        }
        if (stack.getItem() == Items.SHULKER_SHELL)
        {
            return TextFormatting.LIGHT_PURPLE + "[K]";
        }
        if (stack.getItem() == Items.PRISMARINE_SHARD)
        {
            return TextFormatting.DARK_AQUA + "[R]";
        }
        return TextFormatting.GRAY + "[I]";
    }

    private String getPreviewName(ItemStack stack)
    {
        if (stack == null || stack.isEmpty())
        {
            return "";
        }
        String tag = getItemTagColored(stack);
        String genericTag = String.valueOf(TextFormatting.GRAY) + "[I]";
        if (genericTag.equals(tag))
        {
            Minecraft mc = Minecraft.getMinecraft();
            EntityPlayer player = mc == null ? null : mc.player;
            try
            {
                java.util.List<String> tooltip = stack.getTooltip(player, net.minecraft.client.util.ITooltipFlag.TooltipFlags.NORMAL);
                for (String line : tooltip)
                {
                    if (line == null)
                    {
                        continue;
                    }
                    String stripped = TextFormatting.getTextWithoutFormattingCodes(line);
                    if (stripped != null && stripped.indexOf(0x25cf) >= 0)
                    {
                        String cleaned = line.replace(String.valueOf((char)0x25cf), "");
                        String cleanedStripped = TextFormatting.getTextWithoutFormattingCodes(cleaned);
                        if (cleanedStripped != null)
                        {
                            cleanedStripped = cleanedStripped.trim();
                        }
                        if (cleanedStripped != null && !cleanedStripped.isEmpty())
                        {
                            return cleaned.trim();
                        }
                    }
                }
            }
            catch (Exception e)
            {
                // ignore tooltip failures
            }
        }
        return stack.getDisplayName();
    }

    private void renderTextColumnToFramebuffer(Minecraft mc, List<ItemStack> items, int left, int top, int cols,
        int slot, int maxWidth, int guiWidth, int guiHeight)
    {
        renderTextColumn(mc, items, left, top, cols, slot, maxWidth, guiWidth, guiHeight);
    }

    private Rect placeLabel(int x, int y, int w, int h, int minX, int minY, int maxX, int maxY, List<Rect> placed)
    {
        int[] steps = new int[]{0, 1, -1, 2, -2, 3, -3, 4, -4, 5, -5, 6, -6, 7, -7, 8, -8};
        for (int dy : steps)
        {
            for (int dx : steps)
            {
                int px = x + dx * 6;
                int py = y + dy * 6;
                Rect candidate = new Rect(px, py, w, h);
                if (candidate.x < minX || candidate.y < minY || candidate.right() > maxX || candidate.bottom() > maxY)
                {
                    continue;
                }
                boolean overlap = false;
                for (Rect rect : placed)
                {
                    if (rect.intersects(candidate))
                    {
                        overlap = true;
                        break;
                    }
                }
                if (!overlap)
                {
                    return candidate;
                }
            }
        }
        return null;
    }

    private LayoutResult layoutCandidates(List<LabelCandidate> candidates, int minX, int minY, int maxX, int maxY)
    {
        return layoutCandidates(candidates, minX, minY, maxX, maxY, new ArrayList<>());
    }

    private LayoutResult layoutCandidates(List<LabelCandidate> candidates, int minX, int minY, int maxX, int maxY,
        List<Rect> reserved)
    {
        List<PlacedLabel> placed = new ArrayList<>();
        List<Rect> rects = new ArrayList<>(reserved);
        List<Label> overflow = new ArrayList<>();
        for (LabelCandidate candidate : candidates)
        {
            int minAllowedX = minX;
            int maxAllowedX = maxX - candidate.w;
            if (maxAllowedX < minAllowedX)
            {
                overflow.add(new Label(candidate.slot, candidate.text));
                continue;
            }
            int baseX = candidate.x;
            if (baseX < minAllowedX)
            {
                baseX = minAllowedX;
            }
            else if (baseX > maxAllowedX)
            {
                baseX = maxAllowedX;
            }
            Rect placedRect = placeLabel(baseX, candidate.y, candidate.w, candidate.h, minX, minY, maxX, maxY, rects);
            if (placedRect == null)
            {
                overflow.add(new Label(candidate.slot, candidate.text));
            }
            else
            {
                rects.add(placedRect);
                placed.add(new PlacedLabel(candidate.text, placedRect.x, placedRect.y));
            }
        }
        return new LayoutResult(placed, overflow);
    }

    private static class LabelCandidate
    {
        final int slot;
        final String text;
        final int w;
        final int h;
        final int x;
        final int y;

        LabelCandidate(int slot, String text, int w, int h, int x, int y)
        {
            this.slot = slot;
            this.text = text;
            this.w = w;
            this.h = h;
            this.x = x;
            this.y = y;
        }
    }

    private static class PlacedLabel
    {
        final String text;
        final int x;
        final int y;

        PlacedLabel(String text, int x, int y)
        {
            this.text = text;
            this.x = x;
            this.y = y;
        }
    }

    private static class LayoutResult
    {
        final List<PlacedLabel> placed;
        final List<Label> overflow;

        LayoutResult(List<PlacedLabel> placed, List<Label> overflow)
        {
            this.placed = placed;
            this.overflow = overflow;
        }
    }

    private void renderOverflowLegend(Minecraft mc, List<Label> overflow, int left, int top, int cols, int slot,
        int fontHeight, int guiWidth)
    {
        int legendHeight = overflow.size() * (fontHeight + 2) + 4;
        int legendY = top - legendHeight;
        Gui.drawRect(left, legendY, left + guiWidth, legendY + legendHeight, 0xCC000000);
        Gui.drawRect(left, legendY, left + guiWidth, legendY + 1, 0xFF111111);
        int index = 1;
        for (Label label : overflow)
        {
            int row = label.slot / cols;
            int col = label.slot % cols;
            int cx = left + col * slot + slot / 2;
            int cy = top + row * slot + slot / 2;
            String mark = String.valueOf(index);
            int mw = mc.fontRenderer.getStringWidth(mark);
            mc.fontRenderer.drawStringWithShadow(mark, cx - mw / 2, cy - fontHeight / 2, chestHoloTextColor);

            String legend = index + ": " + label.text;
            mc.fontRenderer.drawStringWithShadow(legend, left, legendY + (index - 1) * (fontHeight + 2),
                chestHoloTextColor);
            index++;
        }
    }

    private static class Rect
    {
        final int x;
        final int y;
        final int w;
        final int h;

        Rect(int x, int y, int w, int h)
        {
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
        }

        int right()
        {
            return x + w;
        }

        int bottom()
        {
            return y + h;
        }

        boolean intersects(Rect other)
        {
            return x < other.right() && right() > other.x && y < other.bottom() && bottom() > other.y;
        }
    }

    private static class Label
    {
        final int slot;
        final String text;

        Label(int slot, String text)
        {
            this.slot = slot;
            this.text = text;
        }
    }

    private BlockPos getTargetedBlockPos()
    {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.objectMouseOver == null)
        {
            return null;
        }
        if (mc.objectMouseOver.getBlockPos() == null)
        {
            return null;
        }
        return mc.objectMouseOver.getBlockPos();
    }

    private boolean isPlayerSouthOf(BlockPos pos)
    {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.player == null || mc.world == null || pos == null)
        {
            return true;
        }
        String key = chestKey(mc.world.provider.getDimension(), pos);
        double dz = mc.player.posZ - pos.getZ();
        if (dz >= 1.25)
        {
            chestFaceSouth.put(key, true);
            return true;
        }
        if (dz <= -0.25)
        {
            chestFaceSouth.put(key, false);
            return false;
        }
        Boolean cached = chestFaceSouth.get(key);
        return cached != null ? cached : dz >= 0;
    }

    private double getChestYOffset(BlockPos pos)
    {
        if (pos == null)
        {
            return 1.25;
        }
        String key = pos.getX() + ":" + pos.getY() + ":" + pos.getZ();
        Double val = chestYOffset.get(key);
        if (val != null)
        {
            return val;
        }
        double base = 1.25;
        double extra = ((pos.getX() + pos.getZ()) & 1) == 0 ? 0.0 : 1.25;
        double y = pos.getY() + base + extra;
        chestYOffset.put(key, y);
        return y;
    }

    private ResourceLocation getFontTextureLocation(net.minecraft.client.gui.FontRenderer fontRenderer)
    {
        if (fontRenderer == null)
        {
            return null;
        }
        try
        {
            if (fontTextureField == null)
            {
                try
                {
                    fontTextureField = net.minecraft.client.gui.FontRenderer.class.getDeclaredField("locationFontTexture");
                }
                catch (NoSuchFieldException e)
                {
                    fontTextureField = net.minecraft.client.gui.FontRenderer.class.getDeclaredField("field_78288_b");
                }
                fontTextureField.setAccessible(true);
            }
            Object value = fontTextureField.get(fontRenderer);
            return value instanceof ResourceLocation ? (ResourceLocation) value : null;
        }
        catch (Exception e)
        {
        }
        return null;
    }

    private String getScoreboardLine(int lineNumber)
    {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.world == null)
        {
            return null;
        }
        Scoreboard sb = mc.world.getScoreboard();
        if (sb == null)
        {
            return null;
        }
        ScoreObjective obj = sb.getObjectiveInDisplaySlot(1);
        if (obj == null)
        {
            return null;
        }
        Collection<Score> scores = sb.getSortedScores(obj);
        List<Score> list = new ArrayList<>();
        for (Score score : scores)
        {
            String name = score.getPlayerName();
            if (name == null || name.startsWith("#"))
            {
                continue;
            }
            list.add(score);
        }
        int size = list.size();
        if (size > 15)
        {
            list = list.subList(size - 15, size);
        }
        int idx = list.size() - lineNumber;
        if (idx < 0 || idx >= list.size())
        {
            return null;
        }
        Score score = list.get(idx);
        ScorePlayerTeam team = sb.getPlayersTeam(score.getPlayerName());
        String line = ScorePlayerTeam.formatPlayerName(team, score.getPlayerName());
        if (line == null)
        {
            return null;
        }
        return TextFormatting.getTextWithoutFormattingCodes(line);
    }

    private String getScoreboardLineByScore(int scoreValue)
    {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.world == null)
        {
            return null;
        }
        Scoreboard sb = mc.world.getScoreboard();
        if (sb == null)
        {
            return null;
        }
        ScoreObjective obj = sb.getObjectiveInDisplaySlot(1);
        if (obj == null)
        {
            return null;
        }
        Collection<Score> scores = sb.getSortedScores(obj);
        List<Score> list = new ArrayList<>();
        for (Score score : scores)
        {
            String name = score.getPlayerName();
            if (name == null || name.startsWith("#"))
            {
                continue;
            }
            list.add(score);
        }
        int size = list.size();
        if (size > 15)
        {
            list = list.subList(size - 15, size);
        }
        for (Score score : list)
        {
            if (score.getScorePoints() != scoreValue)
            {
                continue;
            }
            ScorePlayerTeam team = sb.getPlayersTeam(score.getPlayerName());
            String line = ScorePlayerTeam.formatPlayerName(team, score.getPlayerName());
            if (line == null)
            {
                return null;
            }
            return TextFormatting.getTextWithoutFormattingCodes(line);
        }
        return null;
    }

    private String getScoreboardIdLine()
    {
        String line = getScoreboardLineByScore(12);
        if (line == null)
        {
            return null;
        }
        return line.contains("ID") ? line : null;
    }

    private String getScoreboardTitle()
    {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.world == null)
        {
            return null;
        }
        Scoreboard sb = mc.world.getScoreboard();
        if (sb == null)
        {
            return null;
        }
        ScoreObjective obj = sb.getObjectiveInDisplaySlot(1);
        if (obj == null)
        {
            return null;
        }
        String title = obj.getDisplayName();
        if (title == null)
        {
            return null;
        }
        return TextFormatting.getTextWithoutFormattingCodes(title);
    }

    private static void loadConfig(FMLPreInitializationEvent event)
    {
        config = new Configuration(event.getSuggestedConfigurationFile());
        syncConfig(true);
    }

    private void initEntriesFile(FMLPreInitializationEvent event)
    {
        if (event == null)
        {
            return;
        }
        File cfg = event.getSuggestedConfigurationFile();
        if (cfg == null)
        {
            return;
        }
        File dir = cfg.getParentFile();
        if (dir == null)
        {
            return;
        }
        entriesFile = new File(dir, "mcpythonapi_entries.json");
        loadEntries();
        noteFile = new File(dir, "mcpythonapi_note.txt");
        loadNote();
        chestCacheFile = new File(dir, "mcpythonapi_chest_cache.dat");
        loadChestIdCaches();
        menuCacheFile = new File(dir, "mcpythonapi_menu_cache.dat");
        loadMenuCache();
        shulkerHoloFile = new File(dir, "mcpythonapi_shulker_holos.dat");
        loadShulkerHolos();
        codeBlueGlassFile = new File(dir, "mcpythonapi_code_glass.dat");
        loadCodeBlueGlass();
    }

    private void initIoExecutor()
    {
        if (ioExecutor != null)
        {
            return;
        }
        ioExecutor = Executors.newSingleThreadExecutor(new ThreadFactory()
        {
            @Override
            public Thread newThread(Runnable r)
            {
                Thread t = new Thread(r, "MC-Python-IO");
                t.setDaemon(true);
                return t;
            }
        });
    }

    private static void syncConfig(boolean loadFromDisk)
    {
        if (config == null)
        {
            return;
        }
        try
        {
            if (loadFromDisk)
            {
                config.load();
            }
            enableSecondHotbar = config.getBoolean("enableSecondHotbar", "hotbar", true,
                "Enable the second hotbar swap feature.");
            String holoColor = config.getString("holoTextColor", "hologram", "FFFFFF",
                "Hex RGB text color for chest holograms (e.g. FFFFFF).");
            chestHoloTextColor = parseHexColor(holoColor, 0xFFFFFF);
        }
        finally
        {
            if (config.hasChanged())
            {
                config.save();
            }
        }
    }

    private static class CachedMenu
    {
        final String title;
        final int size;
        final List<ItemStack> items;
        final String hash;

        CachedMenu(String title, int size, List<ItemStack> items, String hash)
        {
            this.title = title == null ? "" : title;
            this.size = size;
            this.items = items;
            this.hash = hash == null ? "" : hash;
        }
    }

    private static class ClickAction
    {
        final int slotNumber;
        final int button;
        final ClickType type;

        ClickAction(int slotNumber, int button, ClickType type)
        {
            this.slotNumber = slotNumber;
            this.button = button;
            this.type = type;
        }
    }

    private static class InputEntry
    {
        final int mode;
        final String text;

        InputEntry(int mode, String text)
        {
            this.mode = mode;
            this.text = text == null ? "" : text;
        }
    }

    private static class ExportResult
    {
        final String text;
        final int itemCount;

        ExportResult(String text, int itemCount)
        {
            this.text = text == null ? "" : text;
            this.itemCount = itemCount;
        }
    }

    private static class CopiedSlot
    {
        final int slotNumber;
        final ItemStack stack;

        CopiedSlot(int slotNumber, ItemStack stack)
        {
            this.slotNumber = slotNumber;
            this.stack = stack;
        }
    }

    private static class ShulkerHolo
    {
        final int dim;
        final BlockPos pos;
        final String text;
        final int color;

        ShulkerHolo(int dim, BlockPos pos, String text, int color)
        {
            this.dim = dim;
            this.pos = pos;
            this.text = text == null ? "" : text;
            this.color = color;
        }
    }

    private static class ChestPreviewFbo
    {
        Framebuffer fbo;
        String lastHash;
        int lastTexSize;
        int lastTextWidth;
    }

    private static class ChestCache
    {
        final int dim;
        final BlockPos pos;
        final List<ItemStack> items;
        final long updatedMs;
        final String label;

        ChestCache(int dim, BlockPos pos, List<ItemStack> items, long updatedMs, String label)
        {
            this.dim = dim;
            this.pos = pos;
            this.items = items;
            this.updatedMs = updatedMs;
            this.label = label == null ? "" : label;
        }
    }

    private static class TestChestHolo
    {
        final BlockPos pos;
        final ChestCache cache;
        final boolean useFbo;
        final float scale;
        final int textWidth;
        final int texSize;
        Framebuffer fbo;
        String lastHash;

        TestChestHolo(BlockPos pos, ChestCache cache, boolean useFbo, float scale, int textWidth, int texSize)
        {
            this.pos = pos;
            this.cache = cache;
            this.useFbo = useFbo;
            this.scale = scale;
            this.textWidth = textWidth;
            this.texSize = texSize;
        }
    }

    private static class RegistryEntry
    {
        final String name;
        final int port;
        final String mode;
        long lastSeenMs;

        RegistryEntry(String name, int port, String mode)
        {
            this.name = name;
            this.port = port;
            this.mode = mode == null ? "" : mode;
        }
    }

    private static class CommandResult
    {
        final boolean executed;
        final String mode;

        CommandResult(boolean executed, String mode)
        {
            this.executed = executed;
            this.mode = mode;
        }
    }
}    
