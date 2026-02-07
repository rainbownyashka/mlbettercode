package com.example.examplemod;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
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
import net.minecraft.client.multiplayer.ChunkProviderClient;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.RenderGlobal;
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
import net.minecraft.network.play.client.CPacketHeldItemChange;
import net.minecraft.network.play.client.CPacketPlayer;
import net.minecraft.network.play.client.CPacketPlayerDigging;
import net.minecraft.network.play.client.CPacketPlayerTryUseItemOnBlock;
import net.minecraft.network.play.client.CPacketCreativeInventoryAction;
import net.minecraft.network.play.client.CPacketCustomPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.tileentity.TileEntityShulkerBox;
import com.example.examplemod.model.CachedMenu;
import com.example.examplemod.model.ChestCache;
import com.example.examplemod.model.ChestPreviewFbo;
import com.example.examplemod.model.ClickAction;
import com.example.examplemod.model.CopiedSlot;
import com.example.examplemod.model.CommandResult;
import com.example.examplemod.model.ExportResult;
import com.example.examplemod.model.InputEntry;
import com.example.examplemod.model.Label;
import com.example.examplemod.model.LabelCandidate;
import com.example.examplemod.model.LayoutResult;
import com.example.examplemod.model.MenuStep;
import com.example.examplemod.model.PlaceArg;
import com.example.examplemod.model.PlaceEntry;
import com.example.examplemod.model.PlacedLabel;
import com.example.examplemod.model.Rect;
import com.example.examplemod.model.RegistryEntry;
import com.example.examplemod.model.ShulkerHolo;
import com.example.examplemod.model.TestChestHolo;
import com.example.examplemod.io.ChestIdCacheIO;
import com.example.examplemod.io.ClickMenuIO;
import com.example.examplemod.io.CodeBlueGlassIO;
import com.example.examplemod.io.CodeCacheIO;
import com.example.examplemod.io.MenuCacheIO;
import com.example.examplemod.io.ShulkerHoloIO;
import com.example.examplemod.cmd.ActionBarSink;
import com.example.examplemod.cmd.CBarCommand;
import com.example.examplemod.cmd.GenCommand;
import com.example.examplemod.cmd.GuiExportCommand;
import com.example.examplemod.cmd.GuiExportSink;
import com.example.examplemod.cmd.ScoreLineCommand;
import com.example.examplemod.cmd.ScoreTitleCommand;
import com.example.examplemod.util.ItemStackUtils;
import com.example.examplemod.feature.codemap.BlueGlassCodeMap;
import com.example.examplemod.feature.export.ExportCodeCore;
import com.example.examplemod.feature.place.PlaceModule;
import com.example.examplemod.feature.place.PlaceModuleHost;
import com.example.examplemod.feature.copy.CopyCodeModule;
import com.example.examplemod.feature.regallactions.RegAllActionsHost;
import com.example.examplemod.feature.regallactions.RegAllActionsModule;
import net.minecraft.tileentity.TileEntitySign;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.GameType;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
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
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.BindException;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Collection;
import java.util.Comparator;
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
public class ExampleMod implements PlaceModuleHost, RegAllActionsHost, com.example.examplemod.feature.mldsl.MlDslHost,
    CopyCodeModule.Host
{
    public static final String MODID = "bettercode";
    public static final String NAME = "Creative+ BetterCode";
    public static final String VERSION = "1.0.9";
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
    private static final int INPUT_MODE_APPLE = 5;
    private static final int INPUT_MODE_ITEM = 6;
    private static final int INPUT_CONTEXT_SLOT = 0;
    private static final int INPUT_CONTEXT_GIVE = 1;
    private static final int ENTRY_RECENT_LIMIT = 10;
    private static final int ENTRY_FREQUENT_MIN = 3;
    private static final String ARRAY_MARK = "\u2398";
    private static final String CODE_SELECTOR_TAG = "mldsl_code_selector";
    private static final String CODE_SELECTOR_TITLE = "\u00a7bCode Selector";
    private static final String DEV_UTILS_MENU_TITLE = "\u0423\u0442\u0438\u043b\u0438\u0442\u044b \u0440\u0430\u0437\u0440\u0430\u0431\u043e\u0442\u0447\u0438\u043a\u0430";
    private static final long CODE_SELECTOR_TOGGLE_COOLDOWN_MS = 150L;
    private static final long CODE_SELECTOR_ABORT_COOLDOWN_MS = 120L;
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
    // --- Auto chest cache settings (config) ---
    private static boolean autoCacheEnabled = false;
    private static int autoCacheRadius = 6;
    private static boolean autoCacheTrappedOnly = true;
    // --- Place/MLDSL timings (config) ---
    private static int placeSpeedPercent = 100;
    private static int placeMaxPlaceAttempts = 6;
    private static int placeBlockRetryDelayMs = 1000;
    private static int placeParamsChestAutoOpenDelayMs = 1500;
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
    private Boolean prevPauseOnLostFocus = null;
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
    private KeyBinding keyConfirmLoad;
    private KeyBinding keyFinishCodeSelect;
    private boolean codeMenuKeyDown = false;
    private boolean tpForwardKeyDown = false;
    private boolean codeSelectKeyDown = false;

    // Code selector: set of selected blue-glass anchors by dimension (used by /exportcode selection mode).
    private final Map<Integer, Set<BlockPos>> codeSelectedGlassesByDim = new HashMap<>();
    private long lastCodeSelectorToggleMs = 0L;
    private long lastCodeSelectorAbortMs = 0L;
    private final Map<Integer, Integer> exportSelectedFloorYByDim = new HashMap<>();
    private File lastExportCodeFile = null;
    private int tpScrollSteps = 0;
    private int tpScrollQueue = 0;
    private int tpScrollDir = 0;
    private long tpScrollNextMs = 0L;
    private final Deque<double[]> tpPathQueue = new ArrayDeque<>();
    private long tpPathNextMs = 0L;
    private File codeBlueGlassFile = null;
    private File codeCacheFile = null;
    private boolean codeBlueGlassDirty = false;
    private long lastCodeBlueGlassSaveMs = 0L;
    private final AtomicBoolean codeBlueGlassSaveQueued = new AtomicBoolean(false);
    private final Map<String, BlockPos> codeBlueGlassById = new HashMap<>();
    private final Map<String, String[]> signLinesCache = new HashMap<>();
    private final Map<String, String[]> signLinesCacheByDimPos = new HashMap<>();
    private long lastSignCacheScanMs = 0L;

    // Cache blocks/sign references by scope (scoreboard id + dim) so skip-check can work after chunks unload.
    private final Map<String, String> placedBlockCacheByScopePos = new HashMap<>();
    private final Map<String, String> placedBlockCacheByDimPos = new HashMap<>();
    private final Map<String, Long> entryToSignPosByScopeEntry = new HashMap<>();

    private boolean codeCacheDirty = false;
    private long lastCodeCacheSaveMs = 0L;
    private final AtomicBoolean codeCacheSaveQueued = new AtomicBoolean(false);
    private long lastCodeCacheScanMs = 0L;

    private static boolean autoCodeCacheEnabled = true;
    private static int autoCodeCacheMaxSteps = 256;
    private String signSearchQuery = null;
    private int signSearchDim = 0;
    private final List<BlockPos> signSearchMatches = new ArrayList<>();
    private boolean debugUi = false;
    private boolean exportCodeDebug = false;
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
    private File clickMenuFile = null;
    private File shulkerHoloFile = null;
    private boolean entriesDirty = false;
    private long lastEntriesSaveMs = 0L;
    private boolean clickMenuDirty = false;
    private long lastClickMenuSaveMs = 0L;
    private final AtomicBoolean clickMenuSaveQueued = new AtomicBoolean(false);
    private String noteText = "";
    private BlockPos lastClickedPos = null;
    private int lastClickedDim = 0;
    private long lastClickedMs = 0L;
    private boolean lastClickedChest = false;
    private String lastClickedLabel = null;
    private boolean lastClickedIsSign = false;

    private BlockPos lastGlassPos = null;
    private int lastGlassDim = 0;
    // Click-to-menu recording
    private ItemStack lastClickedSlotStack = null;
    private int lastClickedSlotNumber = -1;
    private long lastClickedSlotMs = 0L;
    private String lastClickedGuiClass = null;
    private String lastClickedGuiTitle = null;
    // history of last two clicked item keys (non-ignored clicks)
    private String[] lastClickedKeyHistory = new String[] { null, null };
    // last observed container items (most recent snapshot while a container was open)
    private List<ItemStack> lastObservedContainerItems = null;
    private long lastObservedContainerMs = 0L;
    private String previousScreenClass = null;
    private String previousScreenTitle = null;
    private final Map<String, List<ItemStack>> clickMenuMap = new HashMap<>();
    private final Map<String, String> clickMenuLocation = new HashMap<>();
    private final Map<String, String> clickFunctionMap = new HashMap<>();
    // Place blocks (/place, /placeadvanced) module
    private final PlaceModule placeModule = new PlaceModule(this);
    // MVP plan runner (/mldsl run plan.json) that drives PlaceModule
    private final com.example.examplemod.feature.mldsl.MlDslModule mlDslModule =
        new com.example.examplemod.feature.mldsl.MlDslModule(this, placeModule);
    // Download modules from MLDSL Hub (/loadmodule <postId> [file])
    private final com.example.examplemod.feature.hub.HubModule hubModule =
        new com.example.examplemod.feature.hub.HubModule(this);
    // Discover/crawl menus into clickMenuMap
    private final RegAllActionsModule regAllActionsModule = new RegAllActionsModule(this);
    // Copy code between plots (/copycode, /cancelcopy)
    private final CopyCodeModule copyCodeModule = new CopyCodeModule(this);
    // Legacy state (kept temporarily while migrating code out of this class).
    private final Deque<PlaceEntry> placeBlocksQueue = new ArrayDeque<>();
    private PlaceEntry placeBlocksCurrent = null;
    private boolean placeBlocksActive = false;
    private boolean pendingChestSnapshot = false;
    private long pendingChestUntilMs = 0L;
    private String lastSnapshotInfo = "";
    private final Map<String, Boolean> chestFaceSouth = new HashMap<>();
    private final Map<String, Double> chestYOffset = new HashMap<>();
    private boolean allowChestSnapshot = false;
    private long allowChestUntilMs = 0L;
    private long lastChestSnapshotMs = 0L;
    private long lastChestSnapshotTick = -1L;
    // Multi-page chest snapshot state (export/publish warmup).
    private String chestPageScanKey = null;
    private int chestPageScanSize = 0;
    private int chestPageScanIndex = 0;
    private String chestPageLastHash = "";
    private boolean chestPageAwaitCursorClear = false;
    private long chestPageAwaitStartMs = 0L;
    private long chestPageNextActionMs = 0L;
    private int chestPageRetryCount = 0;
    private static final int CHEST_PAGE_MAX_RETRIES = 5;
    private static final long CHEST_PAGE_WAIT_TIMEOUT_MS = 1500L;

    // --- Auto chest cache runtime state ---
    private long nextAutoCacheScanMs = 0L;
    private boolean autoCacheInProgress = false;
    private long autoCacheStartMs = 0L;
    private BlockPos autoCacheTargetPos = null;
    private int autoCacheTargetDim = 0;
    private boolean cacheAllActive = false;
    private final Deque<BlockPos> cacheAllQueue = new ArrayDeque<>();
    private BlockPos cacheAllCurrentTarget = null;
    private int cacheAllCurrentDim = 0;
    // /module publish nocache warmup state:
    // preload selected row chests via tp path + open chest before export.
    private boolean modulePublishWarmupActive = false;
    private final Deque<BlockPos> modulePublishWarmupQueue = new ArrayDeque<>();
    private final LinkedHashSet<BlockPos> modulePublishWarmupAllChests = new LinkedHashSet<>();
    private int modulePublishWarmupPass = 0;
    private BlockPos modulePublishWarmupCurrent = null;
    private int modulePublishWarmupDim = 0;
    private long modulePublishWarmupStartMs = 0L;
    private File modulePublishWarmupDir = null;
    private String modulePublishWarmupName = null;
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
        try
        {
            if (ExampleMod.class.getProtectionDomain() != null
                && ExampleMod.class.getProtectionDomain().getCodeSource() != null
                && ExampleMod.class.getProtectionDomain().getCodeSource().getLocation() != null)
            {
                logger.info("Loaded from {}", ExampleMod.class.getProtectionDomain().getCodeSource().getLocation());
            }
        }
        catch (Exception ignore) { }
        logDuplicateClassSources();
        initHotbarStorage();
        registerKeybinds();
        ensureRenderModelClassesLoaded();
        startHttpServer();
    }

    @Override
    public boolean isEditorModeActive()
    {
        return editorModeActive;
    }

    @Override
    public boolean isDebugUi()
    {
        return debugUi;
    }

    @Override
    public boolean isInputActive()
    {
        return inputActive;
    }

    @Override
    public long placeDelayMs(long baseMs)
    {
        long base = Math.max(0L, baseMs);
        int pct = Math.max(10, Math.min(500, placeSpeedPercent));
        return (base * 100L) / pct;
    }

    @Override
    public int placeMaxPlaceAttempts()
    {
        return Math.max(1, Math.min(30, placeMaxPlaceAttempts));
    }

    @Override
    public long placeBlockRetryDelayMs()
    {
        return Math.max(50L, (long) placeBlockRetryDelayMs);
    }

    @Override
    public long placeParamsChestAutoOpenDelayMs()
    {
        return Math.max(200L, (long) placeParamsChestAutoOpenDelayMs);
    }

    @Override
    public void debugChat(String text)
    {
        if (!debugUi)
        {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.player == null)
        {
            return;
        }
        mc.player.sendMessage(new TextComponentString("[BetterCode] " + (text == null ? "" : text)));
    }

    @Override
    public void queueClick(ClickAction action)
    {
        if (action == null)
        {
            return;
        }
        queuedClicks.add(action);
    }

    @Override
    public void clearQueuedClicks()
    {
        queuedClicks.clear();
    }

    @Override
    public boolean tpPathQueueIsEmpty()
    {
        return tpPathQueue.isEmpty();
    }

    @Override
    public int tpPathQueueSize()
    {
        return tpPathQueue.size();
    }

    @Override
    public void clearTpPathQueue()
    {
        tpPathQueue.clear();
    }

    @Override
    public void closeCurrentScreen()
    {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null)
        {
            return;
        }
        if (mc.player != null)
        {
            mc.player.closeScreen();
        }
        else
        {
            mc.displayGuiScreen(null);
        }
    }

    @Override
    public BlockPos getLastGlassPos()
    {
        return lastGlassPos;
    }

    @Override
    public int getLastGlassDim()
    {
        return lastGlassDim;
    }

    @Override
    public void setLastGlassPos(BlockPos pos, int dim)
    {
        lastGlassPos = pos;
        lastGlassDim = dim;
    }

    @Override
    public Map<String, BlockPos> getCodeBlueGlassById()
    {
        return codeBlueGlassById;
    }

    @Override
    public Map<String, List<ItemStack>> getClickMenuMap()
    {
        return clickMenuMap;
    }

    @Override
    public BlockPos getLastClickedPos()
    {
        return lastClickedPos;
    }

    @Override
    public boolean isLastClickedSign()
    {
        return lastClickedIsSign;
    }

    @Override
    public void cacheClickMenuSnapshot(String key, List<ItemStack> items, String location)
    {
        if (key == null || key.trim().isEmpty() || items == null)
        {
            return;
        }
        List<ItemStack> copy = new ArrayList<>();
        for (ItemStack st : items)
        {
            copy.add(st == null || st.isEmpty() ? ItemStack.EMPTY : st.copy());
        }
        clickMenuMap.put(key, copy);
        if (location != null)
        {
            clickMenuLocation.put(key, location);
        }
        clickMenuDirty = true;
    }

    @Override
    public boolean isPlayerInventorySlot(GuiContainer gui, Slot slot)
    {
        if (slot == null)
        {
            return false;
        }
        Minecraft mc = Minecraft.getMinecraft();
        return mc != null && mc.player != null && slot.inventory == mc.player.inventory;
    }

    private void logDuplicateClassSources()
    {
        try
        {
            Minecraft mc = Minecraft.getMinecraft();
            if (mc == null || mc.mcDataDir == null)
            {
                return;
            }
            File modsDir = new File(mc.mcDataDir, "mods");
            if (!modsDir.isDirectory())
            {
                return;
            }
            List<File> jars = new ArrayList<>();
            Deque<File> stack = new ArrayDeque<>();
            stack.push(modsDir);
            while (!stack.isEmpty())
            {
                File dir = stack.pop();
                File[] children = dir.listFiles();
                if (children == null)
                {
                    continue;
                }
                for (File f : children)
                {
                    if (f.isDirectory())
                    {
                        stack.push(f);
                    }
                    else if (f.isFile() && f.getName().toLowerCase(Locale.ROOT).endsWith(".jar"))
                    {
                        jars.add(f);
                    }
                }
            }
            List<String> hits = new ArrayList<>();
            for (File jar : jars)
            {
                try (java.util.zip.ZipFile zip = new java.util.zip.ZipFile(jar))
                {
                    if (zip.getEntry("com/example/examplemod/ExampleMod.class") != null)
                    {
                        hits.add(jar.getAbsolutePath());
                    }
                }
                catch (Exception ignore) { }
            }
            if (hits.size() > 1)
            {
                logger.warn("Multiple jars contain com/example/examplemod/ExampleMod.class (this can cause NoClassDefFoundError):");
                for (String p : hits)
                {
                    logger.warn(" - {}", p);
                }
            }
        }
        catch (Exception ignore) { }
    }

    private void ensureRenderModelClassesLoaded()
    {
        // Some classloader/OptiFine setups have thrown NPEs during lazy class loading inside render code paths.
        // Load required model classes early to avoid NoClassDefFoundError during RenderWorldLastEvent.
        String[] required =
            {
                "com.example.examplemod.model.LabelCandidate",
                "com.example.examplemod.model.LayoutResult",
                "com.example.examplemod.model.Rect",
                "com.example.examplemod.model.Label",
                "com.example.examplemod.model.PlacedLabel",
                "com.example.examplemod.model.ChestPreviewFbo",
            };
        for (String name : required)
        {
            try
            {
                Class.forName(name);
            }
            catch (Throwable t)
            {
                try
                {
                    logger.error("Failed to load required class {} (disabling chest-holo rendering)", name, t);
                }
                catch (Exception ignore) { }
                renderDisabledDueToError = true;
                break;
            }
        }
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
            // World/server changed: drop stale selector state from previous world.
            clearAllCodeSelections();
            lastWorldRef = mc.world;
            if (mc.world != null)
            {
                initHotbarsForWorld(mc);
            }
        }
        // Screen change triggers: detect when current GUI changes and report
        GuiScreen currentScreen = mc == null ? null : mc.currentScreen;
        String currentScreenClass = currentScreen == null ? null : currentScreen.getClass().getSimpleName();
        String currentScreenTitle = null;
        if (currentScreen instanceof GuiChest)
        {
            currentScreenTitle = getGuiTitle((GuiChest) currentScreen);
        }
        else if (currentScreen != null)
        {
            currentScreenTitle = currentScreen.getClass().getSimpleName();
        }
        // Compare with previous
        if ((previousScreenClass == null && currentScreenClass != null)
            || (previousScreenClass != null && currentScreenClass == null)
            || (previousScreenClass != null && currentScreenClass != null && !previousScreenClass.equals(currentScreenClass))
            || (previousScreenTitle != null && currentScreenTitle != null && !previousScreenTitle.equals(currentScreenTitle)))
        {
            // Determine trigger type
            String trigger = "ScreenChanged";
            if (previousScreenClass == null && currentScreenClass != null)
            {
                trigger = "OpenedMenu";
            }
            else if (previousScreenClass != null && currentScreenClass == null)
            {
                trigger = "ClosedMenu";
            }
            else if (previousScreenTitle != null && currentScreenTitle != null && !previousScreenTitle.equals(currentScreenTitle))
            {
                trigger = "TitleChanged";
            }
            else if (previousScreenClass != null && currentScreenClass != null && !previousScreenClass.equals(currentScreenClass))
            {
                trigger = "ClassChanged";
            }
            if (debugUi && mc != null && mc.player != null)
            {
                String prev = previousScreenTitle == null ? "-" : previousScreenTitle;
                String cur = currentScreenTitle == null ? "-" : currentScreenTitle;
                mc.player.sendMessage(new TextComponentString("Trigger=" + trigger + " prev='" + prev + "' cur='" + cur + "'"));
            }
            // If a menu was just opened, clear pending click/place queues to avoid interference
            if ("OpenedMenu".equals(trigger))
            {
                // During post-place sign configuration (cycle/function naming), the server may open unrelated container GUIs.
                // Ignore them so they don't interfere with cached menu detection or stall the printer.
                try
                {
                    if (placeModule != null && placeModule.isPostPlaceActive())
                    {
                        pendingMenuDetect = false;
                        pendingMenuDetectWindowId = -1;
                        pendingMenuDetectStartMs = 0L;
                        closeCurrentScreen();
                        if (debugUi && mc != null && mc.player != null)
                        {
                            mc.player.sendMessage(new TextComponentString("Ignored OpenedMenu during post-place"));
                        }
                    }
                    else
                    {
                        // Schedule content-change detection after a short delay so the server can populate the container.
                        if (mc.player != null && mc.player.openContainer != null)
                        {
                            pendingMenuDetect = true;
                            pendingMenuDetectStartMs = System.currentTimeMillis();
                            pendingMenuDetectWindowId = mc.player.openContainer.windowId;
                            if (debugUi && mc.player != null)
                            {
                                mc.player.sendMessage(new TextComponentString("Scheduled menu-content detection for window " + pendingMenuDetectWindowId));
                            }
                        }
                    }
                }
                catch (Exception ex)
                {
                    // ignore scheduling errors
                }

                if (!placeModule.isActive())
                {
                    queuedClicks.clear();
                    placeModule.reset();
                }
                lastClickedSlotStack = null;
                lastClickedGuiClass = null;
                lastClickedGuiTitle = null;
            }

            // If title changed and we have a recent clicked slot, assign the current title as the
            // function for the clicked item and report it in chat.
            if ("TitleChanged".equals(trigger))
            {
                if (lastClickedSlotStack != null && System.currentTimeMillis() - lastClickedSlotMs < 5000L)
                {
                    String key = getItemNameKey(lastClickedSlotStack);
                    String func = currentScreenTitle == null ? "-" : currentScreenTitle;
                    clickFunctionMap.put(key, func);
                if (debugUi && mc != null && mc.player != null)
                {
                    String itemName = lastClickedSlotStack.isEmpty() ? "empty" : lastClickedSlotStack.getDisplayName();
                    mc.player.sendMessage(new TextComponentString("Assigned function '" + func + "' to item " + itemName + " (key=" + key + ")"));
                }
                    lastClickedSlotStack = null;
                    lastClickedGuiClass = null;
                    lastClickedGuiTitle = null;
                }
            }
            if ("ClosedMenu".equals(trigger))
            {
                boolean wasChat = previousScreenClass != null && previousScreenClass.contains("GuiChat");
                if (!wasChat && lastObservedContainerItems != null && !lastObservedContainerItems.isEmpty())
                {
                    for (String key : lastClickedKeyHistory)
                    {
                        if (key == null || "empty".equalsIgnoreCase(key))
                        {
                            continue;
                        }
                        if (clickMenuMap.containsKey(key))
                        {
                            continue;
                        }
                        List<ItemStack> copy = new ArrayList<>();
                        for (ItemStack st : lastObservedContainerItems)
                        {
                            copy.add(st.isEmpty() ? ItemStack.EMPTY : st.copy());
                        }
                        clickMenuMap.put(key, copy);
                        clickMenuLocation.put(key, "closedMenuFallback");
                        clickMenuDirty = true;
                    }
                }
            }
            // If screen closed, clear queues to avoid false positives
            if (currentScreen == null)
            {
                if (!placeModule.isActive())
                {
                    queuedClicks.clear();
                    placeModule.reset();
                }
                lastClickedSlotStack = null;
            }
        }
        previousScreenClass = currentScreenClass;
        previousScreenTitle = currentScreenTitle;
        // If there are queued clicks waiting for a server container, try to replay them now
        if (mc != null && mc.player != null && !queuedClicks.isEmpty() && mc.player.openContainer != null)
        {
            replayQueuedClicks(mc);
        }
        if (mc != null && mc.currentScreen instanceof GuiContainer)
        {
            placeModule.onGuiTick((GuiContainer) mc.currentScreen, System.currentTimeMillis());
            regAllActionsModule.onGuiTick((GuiContainer) mc.currentScreen, System.currentTimeMillis());
        }

        // Cache sign texts from currently-loaded tile entities so skip-check can work even after chunks unload.
        // This runs continuously (throttled) in editor mode because signs can unload when you fly away.
        try
        {
            if (mc != null && mc.world != null && editorModeActive)
            {
                long nowMs = System.currentTimeMillis();
                if (nowMs - lastSignCacheScanMs > 1000L)
                {
                    lastSignCacheScanMs = nowMs;
                    for (TileEntity te : new ArrayList<>(mc.world.loadedTileEntityList))
                    {
                        if (!(te instanceof TileEntitySign))
                        {
                            continue;
                        }
                        TileEntitySign sign = (TileEntitySign) te;
                        cacheSignLines(mc.world, sign);
                    }
                }
            }
        }
        catch (Exception ignore) { }

        // Cache code blocks/sign positions from loaded chunks so /mldsl skip-check can work even after you fly away.
        // Only records what is currently loaded ("what you see while flying around in code").
        try
        {
            if (mc != null && mc.world != null && editorModeActive)
            {
                handleAutoCodeCacheTick(mc, System.currentTimeMillis());
            }
        }
        catch (Exception e)
        {
            if (debugUi && mc != null && mc.player != null)
            {
                mc.player.sendMessage(new TextComponentString("[BetterCode] code-cache tick error: " + e));
            }
        }

        // While printing/running plans, force-disable vanilla "pause on lost focus" so minimizing/alt-tabbing
        // doesn't pause the client tick loop.
        try
        {
            if (mc != null && mc.gameSettings != null)
            {
                boolean shouldForce = placeModule != null && placeModule.isActive();
                if (shouldForce)
                {
                    if (prevPauseOnLostFocus == null)
                    {
                        prevPauseOnLostFocus = mc.gameSettings.pauseOnLostFocus;
                    }
                    mc.gameSettings.pauseOnLostFocus = false;
                }
                else if (prevPauseOnLostFocus != null)
                {
                    mc.gameSettings.pauseOnLostFocus = prevPauseOnLostFocus;
                    prevPauseOnLostFocus = null;
                }
            }
        }
        catch (Exception ignore) { }
        
        // If a menu-open detection was scheduled, run it after a short delay so the server has time
        // to populate the container. Timeout if it takes too long.
        if (pendingMenuDetect)
        {
            long nowMs = System.currentTimeMillis();
            // wait at least 250ms before running detection
            if (nowMs - pendingMenuDetectStartMs >= 250L)
            {
                try
                {
                    if (mc != null && mc.player != null && mc.player.openContainer != null
                        && mc.player.openContainer.windowId == pendingMenuDetectWindowId)
                    {
                        // perform the same detection that used to run immediately on OpenedMenu
                        if (lastClickedSlotStack != null && nowMs - lastClickedSlotMs < 5000L)
                        {
                            List<ItemStack> observed = getOpenContainerItems(mc.player.openContainer);
                            int bestDiff = Integer.MAX_VALUE;
                            int bestFirst = -1;
                            String bestKey = null;
                            for (Map.Entry<String, CachedMenu> e : menuCache.entrySet())
                            {
                                List<ItemStack> cachedItems = e.getValue() == null ? null : e.getValue().items;
                                int[] res = compareMenuDiffWithFirst(observed, cachedItems);
                                if (res[0] < bestDiff)
                                {
                                    bestDiff = res[0];
                                    bestFirst = res[1];
                                    bestKey = e.getKey();
                                }
                            }
                            boolean meaningful = false;
                            if (bestDiff == Integer.MAX_VALUE)
                            {
                                meaningful = !observed.isEmpty();
                            }
                            else if (bestDiff > 1)
                            {
                                meaningful = true;
                            }
                            else if (bestDiff == 1)
                            {
                                if (bestFirst != lastClickedSlotNumber)
                                {
                                    meaningful = true;
                                }
                            }
                            if (meaningful)
                            {
                                String key = getItemNameKey(lastClickedSlotStack);
                                String func = (currentScreenTitle == null ? "menu_changed" : currentScreenTitle);
                                clickFunctionMap.put(key, func);
                                if (debugUi && mc.player != null)
                                {
                                    mc.player.sendMessage(new TextComponentString("Assigned function '" + func + "' to item "
                                        + (lastClickedSlotStack == null ? "empty" : lastClickedSlotStack.getDisplayName())
                                        + " (key=" + key + ") due to menu content change (delayed)"));
                                }
                                lastClickedSlotStack = null;
                                lastClickedGuiClass = null;
                                lastClickedGuiTitle = null;
                            }
                        }
                    }
                }
                catch (Exception ex)
                {
                    // ignore
                }
                // clear the pending flag once we've attempted detection
                pendingMenuDetect = false;
                pendingMenuDetectStartMs = 0L;
                pendingMenuDetectWindowId = -1;
            }
            else if (nowMs - pendingMenuDetectStartMs > 5000L)
            {
                // give up after 5s
                pendingMenuDetect = false;
                pendingMenuDetectStartMs = 0L;
                pendingMenuDetectWindowId = -1;
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
                        GuiContainer screen = (GuiContainer) mc.currentScreen;
                        if (screen != null)
                        {
                            snapshotCurrentContainer(screen);
                            lastChestSnapshotTick = worldTick;
                        }
                    }
                    if (mc.currentScreen != null)
                    {
                        pendingChestSnapshot = false;
                        lastSnapshotInfo = "snap:screen=" + mc.currentScreen.getClass().getSimpleName()
                            + " pos=" + (lastClickedPos == null ? "-" : lastClickedPos.toString());
                    }
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
        handleConfirmLoadKey(mc);
        handleFinishCodeSelectKey(mc);
        handleCodeSelectorAttackSuppression(mc);
        handleTpScrollQueue(mc);
        handleTpPathQueue(mc);
        handleHotbarSwap(mc);
        handleCacheAllTick(mc, now);
        handleModulePublishWarmupTick(mc, now);
        copyCodeModule.onClientTick(mc, now);
        placeModule.onClientTick(mc, now);
        regAllActionsModule.onClientTick(mc, now);
        handleAutoChestCacheTick(mc, now);
        checkConfigFileChanges();
        saveEntriesIfNeeded();
        saveMenuCacheIfNeeded();
        saveClickMenuIfNeeded();
        saveChestIdCachesIfNeeded();
        saveShulkerHolosIfNeeded();
        saveCodeBlueGlassIfNeeded();
        saveCodeCachesIfNeeded();
    }

    private void handleConfirmLoadKey(Minecraft mc)
    {
        if (mc == null || keyConfirmLoad == null)
        {
            return;
        }
        try
        {
            if (keyConfirmLoad.isPressed())
            {
                hubModule.runConfirmCommand(null, null, new String[0]);
            }
        }
        catch (Exception ignore)
        {
            // ignore key errors
        }
    }

    private void handleFinishCodeSelectKey(Minecraft mc)
    {
        if (mc == null || mc.player == null || keyFinishCodeSelect == null)
        {
            return;
        }
        boolean down = keyFinishCodeSelect.isKeyDown();
        if (down && !codeSelectKeyDown)
        {
            try
            {
                ItemStack held = mc.player.getHeldItemMainhand();
                if (isCodeSelectorItem(held))
                {
                    int slot = mc.player.inventory.currentItem;
                    mc.player.inventory.setInventorySlotContents(slot, ItemStack.EMPTY);
                    sendCreativeSlotUpdate(mc, slot, ItemStack.EMPTY);
                    setActionBar(true, "&aВыделение завершено. Строк выбрано: " + getSelectedRowCount(mc.world), 3000L);
                }
            }
            catch (Exception e)
            {
                setActionBar(false, "&cОшибка завершения выделения: " + e.getMessage(), 3000L);
            }
        }
        codeSelectKeyDown = down;
    }

    private void handleCodeSelectorAttackSuppression(Minecraft mc)
    {
        if (mc == null || mc.player == null || mc.world == null || mc.gameSettings == null)
        {
            return;
        }
        ItemStack held = mc.player.getHeldItemMainhand();
        if (!isCodeSelectorItem(held))
        {
            return;
        }
        if (!Mouse.isButtonDown(0) && !mc.gameSettings.keyBindAttack.isKeyDown())
        {
            return;
        }
        try
        {
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindAttack.getKeyCode(), false);
        }
        catch (Exception ignore) { }

        long now = System.currentTimeMillis();
        if (now - lastCodeSelectorAbortMs < CODE_SELECTOR_ABORT_COOLDOWN_MS)
        {
            return;
        }
        lastCodeSelectorAbortMs = now;

        try
        {
            if (mc.objectMouseOver != null && mc.objectMouseOver.getBlockPos() != null)
            {
                BlockPos pos = mc.objectMouseOver.getBlockPos();
                EnumFacing face = mc.objectMouseOver.sideHit == null ? EnumFacing.UP : mc.objectMouseOver.sideHit;
                mc.player.connection.sendPacket(
                    new CPacketPlayerDigging(CPacketPlayerDigging.Action.ABORT_DESTROY_BLOCK, pos, face));
            }
        }
        catch (Exception ignore) { }
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
        ActionBarSink actionBarSink = (primary, text, ms) -> setActionBar(primary, text, ms);
        GuiExportSink guiExportSink = (gui, wantRaw, wantClean) -> exportGuiToClipboard(gui, wantRaw, wantClean);
        ClientCommandHandler.instance.registerCommand(new CBarCommand("cbar", true, BAR_DEFAULT_MS, actionBarSink));
        ClientCommandHandler.instance.registerCommand(new CBarCommand("cbar2", false, BAR_DEFAULT_MS, actionBarSink));
        ClientCommandHandler.instance.registerCommand(new GenCommand());
        ClientCommandHandler.instance.registerCommand(
            new com.example.examplemod.cmd.DelegatingCommand("mpdebug", "/mpdebug", this::runDebugCommand));
        ClientCommandHandler.instance.registerCommand(new com.example.examplemod.cmd.ConfigDebugCommand(actionBarSink,
            () -> config == null ? null : config.getConfigFile(), () -> enableSecondHotbar, () -> chestHoloTextColor));
        ClientCommandHandler.instance.registerCommand(
            new com.example.examplemod.cmd.DelegatingCommand("testholo", "/testholo [on|off]", this::runTestHoloCommand));
        ClientCommandHandler.instance.registerCommand(new com.example.examplemod.cmd.DelegatingCommand("testchestholo",
            "/testchestholo add [gui|fbo] [s=0.016] [w=40] [tex=1024] | set [s=0.016] [w=40] [tex=1024] [mode=cache|test|gui] [font=linear|nearest] | clear",
            this::runTestChestHoloCommand));
        ClientCommandHandler.instance.registerCommand(new com.example.examplemod.cmd.DelegatingCommand("testshulkerholo",
            "/testshulkerholo set [s=0.03] [y=1.15] [z=0.0] | clear | me <text> | mefront <text> | mode [billboard|fixed] | depth [on|off] | cull [on|off] | info",
            this::runTestShulkerHoloCommand));
        ClientCommandHandler.instance.registerCommand(new com.example.examplemod.cmd.NoteCommand(actionBarSink, () -> noteText,
            s -> noteText = s, this::saveNote));
        ClientCommandHandler.instance.registerCommand(new ScoreLineCommand(actionBarSink, this::getScoreboardLineByScore));
        ClientCommandHandler.instance.registerCommand(new ScoreTitleCommand(actionBarSink, this::getScoreboardTitle));
        ClientCommandHandler.instance.registerCommand(new com.example.examplemod.cmd.ApiPortCommand(() -> apiPort));
        ClientCommandHandler.instance.registerCommand(new GuiExportCommand(actionBarSink, guiExportSink));
        ClientCommandHandler.instance.registerCommand(new com.example.examplemod.cmd.TestTpLocalCommand(actionBarSink));
        ClientCommandHandler.instance.registerCommand(
            new com.example.examplemod.cmd.TpPathCommand(actionBarSink, () -> editorModeActive, this::buildTpPathQueue));
        ClientCommandHandler.instance.registerCommand(new com.example.examplemod.cmd.SignSearchCommand(actionBarSink, () -> {
            signSearchQuery = null;
            signSearchMatches.clear();
        }, (query) -> {
            Minecraft mc = Minecraft.getMinecraft();
            if (mc == null || mc.world == null)
            {
                return 0;
            }
            String q = query == null ? "" : query.trim();
            if (q.isEmpty())
            {
                return 0;
            }
            signSearchQuery = q.toLowerCase(Locale.ROOT);
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
            return signSearchMatches.size();
        }));
        ClientCommandHandler.instance.registerCommand(new com.example.examplemod.cmd.ExportLineCommand(actionBarSink, () -> {
            Minecraft mc = Minecraft.getMinecraft();
            return mc == null ? null : mc.world;
        }, () -> {
            Minecraft mc = Minecraft.getMinecraft();
            if (mc == null || mc.world == null)
            {
                return null;
            }
            return resolveExportGlassPos(mc.world);
        }, (pos) -> {
            Minecraft mc = Minecraft.getMinecraft();
            if (mc == null || mc.world == null)
            {
                return null;
            }
            return parseLogicChain(mc.world, pos);
        }));
        ClientCommandHandler.instance.registerCommand(
            new com.example.examplemod.cmd.DelegatingCommand("autocache", "/autocache [info]", this::runAutoCacheCommand));
        ClientCommandHandler.instance.registerCommand(new com.example.examplemod.cmd.DelegatingCommand("cacheallchests",
            "/cacheallchests [stop]", this::runCacheAllChestsCommand));
        ClientCommandHandler.instance.registerCommand(new com.example.examplemod.cmd.DelegatingCommand("place",
            "/place <block1> [block2] [block3]... - place blocks relative to last blue glass", placeModule::runPlaceBlocksCommand));
        ClientCommandHandler.instance.registerCommand(new com.example.examplemod.cmd.DelegatingCommand("placeadvanced",
            "/placeadvanced <block> <name> <args|no> [<block> <name> <args|no> ...]", placeModule::runPlaceAdvancedCommand));
        ClientCommandHandler.instance.registerCommand(new com.example.examplemod.cmd.DelegatingCommand("mldsl",
            "/mldsl run [path] [--start N] - run plan.json via placeadvanced", mlDslModule::runCommand));
        ClientCommandHandler.instance.registerCommand(new com.example.examplemod.cmd.DelegatingCommand("loadmodule",
            "/loadmodule <postId> [file] - download from MLDSL Hub", hubModule::runCommand));
        ClientCommandHandler.instance.registerCommand(new com.example.examplemod.cmd.DelegatingCommand("confirmload",
            "/confirmload - confirm printing downloaded plan.json", hubModule::runConfirmCommand));
        ClientCommandHandler.instance.registerCommand(new com.example.examplemod.cmd.DelegatingCommand("copycode",
            "/copycode <id1> <id2> <yoffset> <floorsCSV> - copy code blocks by floor", this::runCopyCodeCommand));
        ClientCommandHandler.instance.registerCommand(new com.example.examplemod.cmd.DelegatingCommand("cancelcopy",
            "/cancelcopy - stop /copycode", this::runCancelCopyCommand));
        ClientCommandHandler.instance.registerCommand(new com.example.examplemod.cmd.DelegatingCommand("exportcode",
            "/exportcode [floorsCSV] [name] - экспорт загруженных этажей кода в exportcode_*.json", this::runExportCodeCommand));
        ClientCommandHandler.instance.registerCommand(new com.example.examplemod.cmd.DelegatingCommand("exportcodedebug",
            "/exportcodedebug [on|off] - подробная диагностика exportcode в чат/лог", this::runExportCodeDebugCommand));
        ClientCommandHandler.instance.registerCommand(new com.example.examplemod.cmd.DelegatingCommand("codeselector",
            "/codeselector - выдать инструмент выделения строк (ПКМ: вкл/выкл, ЛКМ: очистить, F: завершить)", this::runCodeSelectorCommand));
        ClientCommandHandler.instance.registerCommand(new com.example.examplemod.cmd.DelegatingCommand("selectfloor",
            "/selectfloor <1..20> - выбрать этаж по умолчанию для /exportcode (Y = N*10-10)", this::runSelectFloorCommand));
        ClientCommandHandler.instance.registerCommand(new com.example.examplemod.cmd.DelegatingCommand("module",
            "/module publish [name] [nocache] - создать папку публикации (plan/export) и открыть Hub", this::runModuleCommand));
        ClientCommandHandler.instance.registerCommand(new com.example.examplemod.cmd.DelegatingCommand("modbc",
            "/modbc update - download and install latest BetterCode on game exit", this::runModBcCommand));
        ClientCommandHandler.instance.registerCommand(new com.example.examplemod.cmd.DelegatingCommand("testplace",
            "/testplace <method 1-10> [block]", this::runTestPlaceCommand));
        ClientCommandHandler.instance.registerCommand(new com.example.examplemod.cmd.DelegatingCommand("regallactions",
            "/regallactions [stop] - crawl sign menus and cache them", regAllActionsModule::runCommand));
        ClientCommandHandler.instance.registerCommand(new com.example.examplemod.cmd.DelegatingCommand("regallactionsdebug",
            "/regallactionsdebug [on/off] - slow + actionbar debug for regallactions", regAllActionsModule::runDebugCommand));
        ClientCommandHandler.instance.registerCommand(
            new com.example.examplemod.cmd.ShowFuncsCommand(() -> clickFunctionMap, () -> clickMenuLocation, () -> clickMenuMap));
    }

    private void runCopyCodeCommand(MinecraftServer server, ICommandSender sender, String[] args)
    {
        copyCodeModule.runCopyCommand(sender, args);
    }

    private void runCancelCopyCommand(MinecraftServer server, ICommandSender sender, String[] args)
    {
        copyCodeModule.cancel("manual");
    }

    private void runExportCodeCommand(MinecraftServer server, ICommandSender sender, String[] args)
    {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.world == null || mc.player == null)
        {
            setActionBar(false, "&cНет мира или игрока", 2000L);
            return;
        }
        if (!editorModeActive || !isDevCreativeScoreboard(mc))
        {
            setActionBar(false, "&cТолько DEV-режим (креатив + scoreboard)", 3000L);
            return;
        }

        // By default, if there is an active Code Selector selection, export it instead of scanning floors.
        ExportSelection selection = getValidSelectedRows(mc.world);
        exportCodeDbg(mc, "runExportCodeCommand: args=" + Arrays.toString(args)
            + " selected.total=" + selection.totalSelected
            + " selected.valid=" + selection.valid.size()
            + " skipped(unloaded=" + selection.skippedUnloaded + ", empty=" + selection.skippedEmpty + ")");
        if (selection.totalSelected > 0)
        {
            if (selection.valid.isEmpty())
            {
                setActionBar(false, "&cВыбранные строки пустые/не загружены. Подлети ближе и выдели заново.", 4000L);
                mc.player.sendMessage(new TextComponentString(TextFormatting.RED
                    + "exportcode: нет валидных выбранных строк (unloaded=" + selection.skippedUnloaded + " empty=" + selection.skippedEmpty + ")"));
                return;
            }

            String name = sanitizeExportFilenameToken(extractExportNameFromArgs(args));
            if (name.isEmpty())
            {
                name = sanitizeExportFilenameToken(normalizeEntryScopeId(getScoreboardIdLine()));
            }
            if (name.isEmpty())
            {
                name = "code";
            }

            selection.valid.sort(Comparator.<BlockPos>comparingInt(BlockPos::getY)
                .thenComparingInt(BlockPos::getZ)
                .thenComparingInt(BlockPos::getX));
            exportCodeDbg(mc, "selected rows(sorted)=" + summarizeRows(selection.valid, 24));

            String json = buildExportCodeJson(mc.world, selection.valid, autoCodeCacheMaxSteps);
            if (json == null || json.isEmpty())
            {
                setActionBar(false, "&cExport failed", 2500L);
                return;
            }

            File out = new File(mc.mcDataDir, "exportcode_" + name + "_" + System.currentTimeMillis() + ".json");
            try
            {
                java.nio.file.Files.write(out.toPath(), json.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                lastExportCodeFile = out;
                setActionBar(true, "&aЭкспортировано выделенных строк: " + selection.valid.size(), 3500L);
                mc.player.sendMessage(new TextComponentString(TextFormatting.GREEN + "exportcode сохранен: " + out.getAbsolutePath()));
                if (selection.skippedUnloaded > 0 || selection.skippedEmpty > 0)
                {
                    mc.player.sendMessage(new TextComponentString(TextFormatting.YELLOW
                        + "Пропущено выделенных строк: unloaded=" + selection.skippedUnloaded + " empty=" + selection.skippedEmpty));
                }
                mc.player.sendMessage(new TextComponentString(TextFormatting.YELLOW
                    + "Экспортировано строк: " + selection.valid.size() + " (снимки сундуков добавлены, где найдены)"));
            }
            catch (Exception e)
            {
                setActionBar(false, "&cОшибка экспорта: " + e.getMessage(), 3500L);
            }
            return;
        }

        if (args == null || args.length == 0 || args[0] == null || args[0].trim().isEmpty())
        {
            setActionBar(false, "&cНет выделения. Используй Code Selector или передай этажи: /exportcode <floorsCSV> [name]", 4500L);
            mc.player.sendMessage(new TextComponentString(TextFormatting.RED
                + "exportcode: по умолчанию требуется выделение. "
                + "Используй Code Selector (рекомендуется) или укажи этажи явно, например: /exportcode 0,10,20 myname"));
            return;
        }

        List<Integer> floors = parseFloorArgsToY(args.length > 0 ? args[0] : null);
        if (floors.isEmpty())
        {
            setActionBar(false, "&cНекорректные этажи. Пример: /exportcode 0,10,20 [name]", 3500L);
            return;
        }
        String name = args.length > 1 ? sanitizeExportFilenameToken(args[1]) : "";
        if (name.isEmpty())
        {
            name = sanitizeExportFilenameToken(normalizeEntryScopeId(getScoreboardIdLine()));
        }
        if (name.isEmpty())
        {
            name = "code";
        }

        List<BlockPos> glasses = collectLoadedBlueGlassesOnFloors(mc.world, floors);
        exportCodeDbg(mc, "floors=" + floors + " loadedBlueGlasses=" + glasses.size() + " (no fallback scan)");
        if (glasses.isEmpty())
        {
            setActionBar(false, "&cНа указанных этажах нет загруженного синего стекла", 3000L);
            return;
        }

        glasses.sort(Comparator.<BlockPos>comparingInt(BlockPos::getY)
            .thenComparingInt(BlockPos::getZ)
            .thenComparingInt(BlockPos::getX));

        String json = buildExportCodeJson(mc.world, glasses, autoCodeCacheMaxSteps);
        if (json == null || json.isEmpty())
        {
            setActionBar(false, "&cОшибка экспорта", 2500L);
            return;
        }

        File out = new File(mc.mcDataDir, "exportcode_" + name + "_" + System.currentTimeMillis() + ".json");
        try
        {
            java.nio.file.Files.write(out.toPath(), json.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            lastExportCodeFile = out;
            setActionBar(true, "&aЭкспортировано: " + out.getName(), 3500L);
            mc.player.sendMessage(new TextComponentString(TextFormatting.GREEN + "exportcode сохранен: " + out.getAbsolutePath()));
            mc.player.sendMessage(new TextComponentString(TextFormatting.YELLOW
                + "Экспортировано строк: " + glasses.size()));
            mc.player.sendMessage(new TextComponentString(TextFormatting.YELLOW
                + "Снимки сундуков добавлены, если рядом был обнаружен сундук параметров."));
        }
        catch (Exception e)
        {
            setActionBar(false, "&cОшибка экспорта: " + e.getMessage(), 3500L);
        }
    }

    private void runSelectFloorCommand(MinecraftServer server, ICommandSender sender, String[] args)
    {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.world == null || mc.player == null)
        {
            return;
        }
        if (!editorModeActive || !isDevCreativeScoreboard(mc))
        {
            setActionBar(false, "&cТолько DEV-режим (креатив + scoreboard)", 3000L);
            return;
        }
        if (args == null || args.length == 0)
        {
            setActionBar(false, "&cИспользование: /selectfloor <1..20>", 3000L);
            return;
        }
        String raw = args[0] == null ? "" : args[0].trim();
        int n;
        try
        {
            n = Integer.parseInt(raw);
        }
        catch (Exception e)
        {
            setActionBar(false, "&cИспользование: /selectfloor <1..20>", 3000L);
            return;
        }
        if (n < 1 || n > 20)
        {
            setActionBar(false, "&cЭтаж должен быть в диапазоне 1..20", 3000L);
            return;
        }
        int y = (n * 10) - 10;
        exportSelectedFloorYByDim.put(mc.world.provider.getDimension(), y);
        // Select all loaded code rows on this floor by default, so /exportcode can be one-step.
        List<BlockPos> glasses = collectLoadedBlueGlassesOnFloors(mc.world, Collections.singletonList(y));
        Set<BlockPos> selected = codeSelectedGlassesByDim.computeIfAbsent(mc.world.provider.getDimension(), k -> new LinkedHashSet<>());
        selected.clear();
        int withBlock = 0;
        for (BlockPos p : glasses)
        {
            if (p == null)
            {
                continue;
            }
            if (!mc.world.isBlockLoaded(p, false))
            {
                continue;
            }
            if (mc.world.getBlockState(p.up()).getBlock() == Blocks.AIR)
            {
                continue;
            }
            selected.add(p.toImmutable());
            withBlock++;
        }

        setActionBar(true, "&aВыбран этаж " + n + " (Y=" + y + "), строк=" + withBlock, 4000L);
        mc.player.sendMessage(new TextComponentString(TextFormatting.GREEN + "Этаж экспорта по умолчанию: " + n + " (Y=" + y + ")"));
        mc.player.sendMessage(new TextComponentString(TextFormatting.YELLOW + "Загружено выделенных строк на этом этаже: " + withBlock));
    }

    private void runModuleCommand(MinecraftServer server, ICommandSender sender, String[] args)
    {
        if (args == null || args.length == 0)
        {
            setActionBar(false, "&cИспользование: /module publish [name] [nocache]", 3500L);
            return;
        }
        String sub = args[0] == null ? "" : args[0].trim().toLowerCase(Locale.ROOT);
        if ("publish".equals(sub))
        {
            String name = "";
            boolean noCache = false;
            for (int i = 1; i < args.length; i++)
            {
                String tok = args[i] == null ? "" : args[i].trim();
                if (tok.isEmpty())
                {
                    continue;
                }
                if ("nocache".equalsIgnoreCase(tok))
                {
                    noCache = true;
                    continue;
                }
                if (name.isEmpty())
                {
                    name = tok;
                }
            }
            runModulePublishCommand(name, noCache);
            return;
        }
        setActionBar(false, "&cНеизвестная подкоманда: " + sub, 3500L);
    }

    private void runModBcCommand(MinecraftServer server, ICommandSender sender, String[] args)
    {
        if (args == null || args.length == 0)
        {
            setActionBar(false, "&cИспользование: /modbc update", 3500L);
            return;
        }
        String sub = args[0] == null ? "" : args[0].trim().toLowerCase(Locale.ROOT);
        if (!"update".equals(sub))
        {
            setActionBar(false, "&cИспользование: /modbc update", 3500L);
            return;
        }
        runModBcUpdate();
    }

    private static final class ReleaseAssetInfo
    {
        final String tag;
        final String name;
        final String url;

        ReleaseAssetInfo(String tag, String name, String url)
        {
            this.tag = tag == null ? "" : tag;
            this.name = name == null ? "" : name;
            this.url = url == null ? "" : url;
        }
    }

    private void runModBcUpdate()
    {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.mcDataDir == null || mc.player == null)
        {
            return;
        }
        setActionBar(true, "&e/modbc: checking latest release...", 3000L);

        new Thread(() -> {
            try
            {
                ReleaseAssetInfo asset = fetchLatestModReleaseAsset();
                if (asset == null || asset.url.isEmpty() || asset.name.isEmpty())
                {
                    scheduleMainChat("&c/modbc update: no release asset found.");
                    return;
                }

                File currentJar = detectCurrentModJar();
                if (currentJar != null && currentJar.getName().equalsIgnoreCase(asset.name))
                {
                    scheduleMainChat("&a/modbc update: already latest (&f" + asset.name + "&a).");
                    return;
                }

                File modsDir = resolveModsDir(mc);
                if (modsDir == null || (!modsDir.exists() && !modsDir.mkdirs()))
                {
                    scheduleMainChat("&c/modbc update: can't access mods dir.");
                    return;
                }
                File tmpDir = new File(mc.mcDataDir, "bettercode_update");
                if (!tmpDir.exists())
                {
                    //noinspection ResultOfMethodCallIgnored
                    tmpDir.mkdirs();
                }
                File downloaded = new File(tmpDir, asset.name);
                boolean ok = downloadBinary(asset.url, downloaded, 20L * 1024L * 1024L);
                if (!ok || !downloaded.isFile())
                {
                    scheduleMainChat("&c/modbc update: download failed.");
                    return;
                }

                File target = new File(modsDir, asset.name);
                File oldJar = currentJar != null && currentJar.isFile() ? currentJar : null;
                File updaterBat = new File(tmpDir, "bettercode_apply_update.bat");
                if (!writeUpdaterScript(updaterBat, downloaded, target, oldJar))
                {
                    scheduleMainChat("&c/modbc update: can't create updater script.");
                    return;
                }

                ProcessBuilder pb = new ProcessBuilder("cmd", "/c", "start", "\"\"", updaterBat.getAbsolutePath());
                pb.start();

                scheduleMainChat("&a/modbc update: downloaded &f" + asset.name + "&a, applying on exit...");
                try
                {
                    Thread.sleep(500L);
                }
                catch (InterruptedException ignore) { }
                mc.addScheduledTask(() -> {
                    try
                    {
                        mc.shutdown();
                    }
                    catch (Exception e)
                    {
                        try
                        {
                            mc.shutdownMinecraftApplet();
                        }
                        catch (Exception ignore) { }
                    }
                });
            }
            catch (Exception e)
            {
                scheduleMainChat("&c/modbc update: " + e.getClass().getSimpleName());
            }
        }, "bettercode-update").start();
    }

    private void runModulePublishCommand(String name, boolean noCache)
    {
        runModulePublishCommandWithDir(name, noCache, null);
    }

    private void runModulePublishCommandWithDir(String name, boolean noCache, File predefinedDir)
    {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.mcDataDir == null || mc.player == null)
        {
            return;
        }

        if (name == null || name.trim().isEmpty())
        {
            name = "bundle";
        }
        name = name.replaceAll("[^a-zA-Z0-9_\\-\\.]+", "_");
        if (name.isEmpty())
        {
            name = "bundle";
        }

        File dir = predefinedDir;
        if (dir == null)
        {
            File publishRoot = new File(mc.mcDataDir, "mldsl_publish");
            dir = new File(publishRoot, name + "_" + System.currentTimeMillis());
            if (!dir.exists() && !dir.mkdirs())
            {
                setActionBar(false, "&cНе удалось создать папку: " + dir.getAbsolutePath(), 4000L);
                return;
            }
        }

        if (noCache)
        {
            List<BlockPos> glasses = collectPublishGlasses(mc.world);
            if (glasses == null || glasses.isEmpty())
            {
                setActionBar(false, "&c/module publish: нет выбранных/видимых строк", 3500L);
                mc.player.sendMessage(new TextComponentString(TextFormatting.RED
                    + "/module publish nocache: нет выбранных/видимых строк."));
                return;
            }
            LinkedHashSet<BlockPos> chests = collectExportRowChests(mc.world, glasses, autoCodeCacheMaxSteps);
            int dim = mc.world.provider.getDimension();
            ensureChestCaches(dim);
            for (BlockPos p : chests)
            {
                String key = chestKey(dim, p);
                if (key != null)
                {
                    chestCaches.remove(key);
                }
            }
            chestIdDirty = true;

            modulePublishWarmupActive = true;
            modulePublishWarmupQueue.clear();
            modulePublishWarmupQueue.addAll(chests);
            modulePublishWarmupAllChests.clear();
            modulePublishWarmupAllChests.addAll(chests);
            modulePublishWarmupPass = 0;
            modulePublishWarmupCurrent = null;
            modulePublishWarmupDim = dim;
            modulePublishWarmupStartMs = System.currentTimeMillis();
            modulePublishWarmupDir = dir;
            modulePublishWarmupName = name;

            mc.player.sendMessage(new TextComponentString(TextFormatting.YELLOW
                + "/module publish nocache: прогрев сундуков (tp/open) перед экспортом: " + chests.size()));
            setActionBar(true, "&e/module publish nocache: прогрев " + chests.size() + " сундуков...", 3500L);
            return;
        }

        // Cache mode: if some row chests are missing in cache, warm only missing ones first.
        // Do this only on first call (predefinedDir == null) to avoid warmup loops.
        if (!noCache && predefinedDir == null)
        {
            List<BlockPos> glasses = collectPublishGlasses(mc.world);
            if (glasses != null && !glasses.isEmpty())
            {
                LinkedHashSet<BlockPos> chests = collectExportRowChests(mc.world, glasses, autoCodeCacheMaxSteps);
                int dim = mc.world.provider.getDimension();
                ensureChestCaches(dim);
                LinkedHashSet<BlockPos> missing = new LinkedHashSet<>();
                int pagedRefreshCount = 0;
                for (BlockPos p : chests)
                {
                    if (p == null || !isTargetChestBlock(mc.world, p))
                    {
                        continue;
                    }
                    boolean notCached = !isChestCached(dim, p);
                    boolean pagedNeedsRefresh = !notCached && shouldWarmupPagedChestForPublish(mc.world, dim, p);
                    if (notCached || pagedNeedsRefresh)
                    {
                        missing.add(p);
                        if (pagedNeedsRefresh)
                        {
                            pagedRefreshCount++;
                        }
                    }
                }
                if (!missing.isEmpty())
                {
                    modulePublishWarmupActive = true;
                    modulePublishWarmupQueue.clear();
                    modulePublishWarmupQueue.addAll(missing);
                    modulePublishWarmupAllChests.clear();
                    modulePublishWarmupAllChests.addAll(missing);
                    modulePublishWarmupPass = 0;
                    modulePublishWarmupCurrent = null;
                    modulePublishWarmupDim = dim;
                    modulePublishWarmupStartMs = System.currentTimeMillis();
                    modulePublishWarmupDir = dir;
                    modulePublishWarmupName = name;

                    mc.player.sendMessage(new TextComponentString(TextFormatting.YELLOW
                        + "/module publish: прогрев сундуков (tp/open): " + missing.size()
                        + (pagedRefreshCount > 0 ? (" (paged refresh: " + pagedRefreshCount + ")") : "")));
                    setActionBar(true, "&e/module publish: прогрев " + missing.size() + " сундуков...", 3500L);
                    return;
                }
            }
        }

        int copied = 0;

        // 1) Always generate a fresh exportcode from current selection/floor context.
        GeneratedExport generated = generateExportCodeNow(name, !noCache);
        if (generated == null || generated.file == null || !generated.file.isFile())
        {
            setActionBar(false, "&c/module publish: ошибка экспорта", 4500L);
            mc.player.sendMessage(new TextComponentString(TextFormatting.RED
                + "/module publish: не удалось сгенерировать exportcode из текущего выделения/этажа."));
            return;
        }
        File exportFile = generated.file;
        if (copyFileSafe(exportFile, new File(dir, exportFile.getName())))
        {
            copied++;
        }

        // 2) External toolchain via mldsl.exe:
        //    exportcode_*.json -> module.mldsl via `mldsl exportcode`
        //    module.mldsl -> plan.json via `mldsl compile`
        File moduleFile = new File(dir, "module.mldsl");
        File planFile = new File(dir, "plan.json");

        String compiler = resolveMlDslCompilerPath();
        if (compiler == null || compiler.trim().isEmpty())
        {
            setActionBar(false, "&c/module publish: компилятор mldsl не найден", 4500L);
            mc.player.sendMessage(new TextComponentString(TextFormatting.RED
                + "Компилятор mldsl не найден. Ожидается: PATH (mldsl) или %LOCALAPPDATA%\\MLDSL\\mldsl.exe"));
            return;
        }

        ExecResult conv = runMlDslExportToMldsl(compiler, exportFile, moduleFile);
        if (!conv.ok || !moduleFile.isFile())
        {
            setActionBar(false, "&c/module publish: ошибка конвертации export->mldsl", 4500L);
            mc.player.sendMessage(new TextComponentString(TextFormatting.RED
                + "Конвертер завершился с ошибкой (code=" + conv.exitCode + "): " + trimForChat(conv.stderr)));
            mc.player.sendMessage(new TextComponentString(TextFormatting.YELLOW
                + "Проверь, что MLDSL обновлен и поддерживает: mldsl exportcode <file> -o <out>"));
            spamExecResultToChat(mc, "converter", conv);
            return;
        }
        copied++;

        ExecResult comp = runMlDslCompileToPlan(compiler, moduleFile, planFile);
        if (!comp.ok || !planFile.isFile())
        {
            setActionBar(false, "&c/module publish: ошибка компиляции mldsl", 4500L);
            mc.player.sendMessage(new TextComponentString(TextFormatting.RED
                + "Компиляция завершилась с ошибкой (code=" + comp.exitCode + "): " + trimForChat(comp.stderr)));
            mc.player.sendMessage(new TextComponentString(TextFormatting.YELLOW
                + "Используемый компилятор: " + compiler));
            spamExecResultToChat(mc, "compile", comp);
            return;
        }
        copied++;

        // 3) small README for user
        try
        {
            StringBuilder sb = new StringBuilder();
            sb.append("MLDSL Hub publish bundle\n");
            sb.append("\n");
            sb.append("Files in this folder were prepared by BetterCode /module publish.\n");
            sb.append("\n");
            sb.append("- module.mldsl (generated from export)\n");
            sb.append("- plan.json (compiled by mldsl compiler)\n");
            sb.append("- exportcode_*.json (raw export)\n");
            java.nio.file.Files.write(new File(dir, "README.txt").toPath(),
                sb.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
        catch (Exception ignore) { }

        String url = "https://mldsl-hub.pages.dev/publish";
        setActionBar(true, "&aПакет готов (" + copied + " файлов). Открываю папку и Hub...", 4500L);
        mc.player.sendMessage(new TextComponentString(TextFormatting.GREEN + "Папка публикации: " + dir.getAbsolutePath()));
        mc.player.sendMessage(new TextComponentString(TextFormatting.YELLOW
            + "Экспортировано строк: " + generated.rows + " | MLDSL + plan сгенерированы."));
        if (noCache)
        {
            mc.player.sendMessage(new TextComponentString(TextFormatting.YELLOW
                + "Режим публикации: nocache (fallback кэша сундуков отключен)."));
        }

        openFolderAndUrl(dir, url);
    }

    private static final class GeneratedExport
    {
        final File file;
        final int rows;

        GeneratedExport(File file, int rows)
        {
            this.file = file;
            this.rows = rows;
        }
    }

    private List<BlockPos> collectPublishGlasses(World world)
    {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || world == null)
        {
            return Collections.emptyList();
        }
        List<BlockPos> glasses = new ArrayList<>();
        ExportSelection selection = getValidSelectedRows(world);
        if (!selection.valid.isEmpty())
        {
            glasses.addAll(selection.valid);
        }
        else
        {
            BlockPos seed = resolveExportGlassPos(world);
            if (seed == null || !world.isBlockLoaded(seed))
            {
                return Collections.emptyList();
            }
            Integer forced = exportSelectedFloorYByDim.get(world.provider.getDimension());
            List<Integer> floors = new ArrayList<>();
            floors.add(forced == null ? seed.getY() : forced);
            glasses.addAll(collectLoadedBlueGlassesOnFloors(world, floors));
        }
        glasses.sort(Comparator.<BlockPos>comparingInt(BlockPos::getY)
            .thenComparingInt(BlockPos::getZ)
            .thenComparingInt(BlockPos::getX));
        return glasses;
    }

    private LinkedHashSet<BlockPos> collectExportRowChests(World world, List<BlockPos> glasses, int maxSteps)
    {
        LinkedHashSet<BlockPos> out = new LinkedHashSet<>();
        if (world == null || glasses == null || glasses.isEmpty())
        {
            return out;
        }
        int steps = Math.max(32, maxSteps);
        for (BlockPos glassPos : glasses)
        {
            if (glassPos == null || !world.isBlockLoaded(glassPos))
            {
                continue;
            }
            BlockPos start = glassPos.up();
            int emptyPairs = 0;
            for (int p = 0; p < steps; p++)
            {
                BlockPos entry = start.add(-2 * p, 0, 0);
                BlockPos side = entry.add(-1, 0, 0);
                if (!world.isBlockLoaded(entry, false) || !world.isBlockLoaded(side, false))
                {
                    break;
                }
                IBlockState entryState = world.getBlockState(entry);
                IBlockState sideState = world.getBlockState(side);
                Block entryBlock = entryState == null ? Blocks.AIR : entryState.getBlock();
                Block sideBlock = sideState == null ? Blocks.AIR : sideState.getBlock();
                boolean sidePiston = sideBlock == Blocks.PISTON || sideBlock == Blocks.STICKY_PISTON;

                boolean entryHasSign = findSignAtZMinus1(world, entry) != null;
                boolean emptySlot = entryBlock == Blocks.AIR && !sidePiston && !entryHasSign;
                if (emptySlot)
                {
                    emptyPairs++;
                    if (emptyPairs >= 2)
                    {
                        break;
                    }
                    continue;
                }
                emptyPairs = 0;

                BlockPos chestPos = findNearbyExportChestPos(world, entry, null);
                if (chestPos != null)
                {
                    out.add(chestPos);
                }
            }
        }
        return out;
    }

    private GeneratedExport generateExportCodeNow(String name, boolean preferChestCache)
    {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.world == null || mc.player == null)
        {
            return null;
        }
        String outName = sanitizeExportFilenameToken(name);
        if (outName.isEmpty())
        {
            outName = sanitizeExportFilenameToken(normalizeEntryScopeId(getScoreboardIdLine()));
        }
        if (outName.isEmpty())
        {
            outName = "code";
        }

        List<BlockPos> glasses = collectPublishGlasses(mc.world);
        if (glasses.isEmpty())
        {
            return null;
        }
        String json = buildExportCodeJson(mc.world, glasses, autoCodeCacheMaxSteps, preferChestCache);
        if (json == null || json.trim().isEmpty())
        {
            return null;
        }
        File out = new File(mc.mcDataDir, "exportcode_" + outName + "_" + System.currentTimeMillis() + ".json");
        try
        {
            java.nio.file.Files.write(out.toPath(), json.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            lastExportCodeFile = out;
            return new GeneratedExport(out, glasses.size());
        }
        catch (Exception e)
        {
            return null;
        }
    }

    private File findLatestExportFile(File dir, String prefix)
    {
        if (dir == null || prefix == null)
        {
            return null;
        }
        File[] files;
        try
        {
            files = dir.listFiles();
        }
        catch (Exception e)
        {
            return null;
        }
        if (files == null || files.length == 0)
        {
            return null;
        }
        File best = null;
        long bestTs = -1L;
        for (File f : files)
        {
            if (f == null || !f.isFile())
            {
                continue;
            }
            String n = f.getName();
            if (n == null || !n.startsWith(prefix) || !n.endsWith(".json"))
            {
                continue;
            }
            long ts = f.lastModified();
            if (ts > bestTs)
            {
                bestTs = ts;
                best = f;
            }
        }
        return best;
    }

    private boolean copyFileSafe(File src, File dst)
    {
        if (src == null || dst == null)
        {
            return false;
        }
        try
        {
            java.nio.file.Files.copy(src.toPath(), dst.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            return true;
        }
        catch (Exception e)
        {
            Minecraft mc = Minecraft.getMinecraft();
            if (mc != null && mc.player != null)
            {
                mc.player.sendMessage(new TextComponentString(TextFormatting.RED
                    + "Copy failed: " + src.getName() + " -> " + dst.getName() + " (" + e.getMessage() + ")"));
            }
            return false;
        }
    }

    private void openFolderAndUrl(File folder, String url)
    {
        try
        {
            if (java.awt.Desktop.isDesktopSupported())
            {
                java.awt.Desktop desk = java.awt.Desktop.getDesktop();
                try
                {
                    if (folder != null && folder.isDirectory())
                    {
                        desk.open(folder);
                    }
                }
                catch (Exception ignore) { }
                try
                {
                    if (url != null && !url.trim().isEmpty())
                    {
                        desk.browse(new java.net.URI(url.trim()));
                    }
                }
                catch (Exception ignore) { }
                return;
            }
        }
        catch (Exception ignore) { }

        Minecraft mc = Minecraft.getMinecraft();
        if (mc != null && mc.player != null)
        {
            mc.player.sendMessage(new TextComponentString(TextFormatting.YELLOW + "Открой вручную: " + url));
        }
    }

    private void scheduleMainChat(String msg)
    {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null)
        {
            return;
        }
        mc.addScheduledTask(() -> {
            if (mc.player != null)
            {
                mc.player.sendMessage(new TextComponentString("[BetterCode] " + (msg == null ? "" : msg.replace('&', '\u00a7'))));
            }
        });
    }

    private File resolveModsDir(Minecraft mc)
    {
        if (mc == null || mc.mcDataDir == null)
        {
            return null;
        }
        File mods = new File(mc.mcDataDir, "mods");
        if (mods.isDirectory())
        {
            return mods;
        }
        File parent = mc.mcDataDir.getParentFile();
        if (parent != null)
        {
            File alt = new File(parent, "mods");
            if (alt.isDirectory())
            {
                return alt;
            }
        }
        return mods;
    }

    private File detectCurrentModJar()
    {
        try
        {
            java.security.CodeSource src = ExampleMod.class.getProtectionDomain().getCodeSource();
            if (src == null || src.getLocation() == null)
            {
                return null;
            }
            java.net.URI uri = src.getLocation().toURI();
            File f = new File(uri);
            if (f.isFile() && f.getName().toLowerCase(Locale.ROOT).endsWith(".jar"))
            {
                return f;
            }
        }
        catch (Exception ignore) { }
        return null;
    }

    private ReleaseAssetInfo fetchLatestModReleaseAsset() throws Exception
    {
        HttpURLConnection conn = null;
        try
        {
            URL u = new URL("https://api.github.com/repos/rainbownyashka/mlbettercode/releases/latest");
            conn = (HttpURLConnection) u.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(15000);
            conn.setRequestProperty("Accept", "application/vnd.github+json");
            conn.setRequestProperty("User-Agent", "BetterCode-Updater");
            int code = conn.getResponseCode();
            if (code != 200)
            {
                return null;
            }
            String body;
            try (InputStream is = conn.getInputStream())
            {
                body = readAllUtf8(is);
            }
            JsonElement root = new JsonParser().parse(body);
            if (root == null || !root.isJsonObject())
            {
                return null;
            }
            JsonObject obj = root.getAsJsonObject();
            String tag = obj.has("tag_name") && !obj.get("tag_name").isJsonNull() ? obj.get("tag_name").getAsString() : "";
            JsonArray assets = obj.has("assets") && obj.get("assets").isJsonArray() ? obj.getAsJsonArray("assets") : null;
            if (assets == null)
            {
                return null;
            }
            for (JsonElement ae : assets)
            {
                if (ae == null || !ae.isJsonObject())
                {
                    continue;
                }
                JsonObject a = ae.getAsJsonObject();
                String name = a.has("name") && !a.get("name").isJsonNull() ? a.get("name").getAsString() : "";
                String url = a.has("browser_download_url") && !a.get("browser_download_url").isJsonNull()
                    ? a.get("browser_download_url").getAsString() : "";
                if (name.toLowerCase(Locale.ROOT).startsWith("bettercode-") && name.toLowerCase(Locale.ROOT).endsWith(".jar"))
                {
                    return new ReleaseAssetInfo(tag, name, url);
                }
            }
            return null;
        }
        finally
        {
            if (conn != null)
            {
                conn.disconnect();
            }
        }
    }

    private static String readAllUtf8(InputStream is) throws IOException
    {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        while (true)
        {
            int n = is.read(buf);
            if (n < 0)
            {
                break;
            }
            out.write(buf, 0, n);
        }
        return out.toString("UTF-8");
    }

    private boolean downloadBinary(String url, File dst, long maxBytes)
    {
        HttpURLConnection conn = null;
        try
        {
            conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(12000);
            conn.setReadTimeout(30000);
            conn.setRequestProperty("User-Agent", "BetterCode-Updater");
            int code = conn.getResponseCode();
            if (code != 200)
            {
                return false;
            }
            long total = 0L;
            File parent = dst.getParentFile();
            if (parent != null && !parent.exists())
            {
                //noinspection ResultOfMethodCallIgnored
                parent.mkdirs();
            }
            try (InputStream in = conn.getInputStream(); FileOutputStream fos = new FileOutputStream(dst))
            {
                byte[] buf = new byte[8192];
                while (true)
                {
                    int n = in.read(buf);
                    if (n < 0)
                    {
                        break;
                    }
                    total += n;
                    if (total > maxBytes)
                    {
                        return false;
                    }
                    fos.write(buf, 0, n);
                }
            }
            return dst.isFile() && dst.length() > 0L;
        }
        catch (Exception e)
        {
            return false;
        }
        finally
        {
            if (conn != null)
            {
                conn.disconnect();
            }
        }
    }

    private boolean writeUpdaterScript(File scriptFile, File downloaded, File target, File oldJar)
    {
        try
        {
            String src = downloaded.getAbsolutePath().replace("\"", "");
            String dst = target.getAbsolutePath().replace("\"", "");
            String old = oldJar == null ? "" : oldJar.getAbsolutePath().replace("\"", "");
            String self = scriptFile.getAbsolutePath().replace("\"", "");
            StringBuilder sb = new StringBuilder();
            sb.append("@echo off\r\n");
            sb.append("setlocal\r\n");
            sb.append("set SRC=").append(src).append("\r\n");
            sb.append("set DST=").append(dst).append("\r\n");
            if (!old.isEmpty())
            {
                sb.append("set OLD=").append(old).append("\r\n");
            }
            sb.append("set SELF=").append(self).append("\r\n");
            sb.append("timeout /t 3 /nobreak >nul\r\n");
            sb.append(":waitjava\r\n");
            sb.append("tasklist /FI \"IMAGENAME eq javaw.exe\" | find /I \"javaw.exe\" >nul\r\n");
            sb.append("if %ERRORLEVEL%==0 (timeout /t 1 /nobreak >nul & goto waitjava)\r\n");
            if (!old.isEmpty())
            {
                sb.append("if /I not \"%OLD%\"==\"%DST%\" (del /F /Q \"%OLD%\" >nul 2>nul)\r\n");
            }
            sb.append("copy /Y \"%SRC%\" \"%DST%\" >nul\r\n");
            sb.append("del /F /Q \"%SRC%\" >nul 2>nul\r\n");
            sb.append("del /F /Q \"%SELF%\" >nul 2>nul\r\n");
            java.nio.file.Files.write(scriptFile.toPath(), sb.toString().getBytes(StandardCharsets.UTF_8));
            return scriptFile.isFile();
        }
        catch (Exception e)
        {
            return false;
        }
    }

    private static final class ExecResult
    {
        final boolean ok;
        final int exitCode;
        final String stdout;
        final String stderr;

        ExecResult(boolean ok, int exitCode, String stdout, String stderr)
        {
            this.ok = ok;
            this.exitCode = exitCode;
            this.stdout = stdout == null ? "" : stdout;
            this.stderr = stderr == null ? "" : stderr;
        }
    }

    private static String trimForChat(String s)
    {
        if (s == null)
        {
            return "";
        }
        String t = s.replace('\r', ' ').replace('\n', ' ').trim();
        if (t.length() > 180)
        {
            return t.substring(0, 180) + "...";
        }
        return t;
    }

    private void spamExecResultToChat(Minecraft mc, String stage, ExecResult r)
    {
        if (mc == null || mc.player == null || r == null)
        {
            return;
        }
        String hdr = "[BetterCode] " + (stage == null ? "process" : stage) + " full log:";
        mc.player.sendMessage(new TextComponentString(TextFormatting.YELLOW + hdr));
        spamTextLines(mc, TextFormatting.RED, "stderr", r.stderr);
        spamTextLines(mc, TextFormatting.GRAY, "stdout", r.stdout);
    }

    private void spamTextLines(Minecraft mc, TextFormatting color, String label, String text)
    {
        if (mc == null || mc.player == null)
        {
            return;
        }
        String src = text == null ? "" : text.replace("\r\n", "\n").replace('\r', '\n');
        if (src.trim().isEmpty())
        {
            mc.player.sendMessage(new TextComponentString(color + label + ": <empty>"));
            return;
        }
        String[] lines = src.split("\n");
        int sent = 0;
        for (String ln : lines)
        {
            if (ln == null)
            {
                continue;
            }
            String t = ln.trim();
            if (t.isEmpty())
            {
                continue;
            }
            // Keep each chat message small enough for client/server chat pipeline.
            int max = 220;
            for (int i = 0; i < t.length(); i += max)
            {
                String part = t.substring(i, Math.min(t.length(), i + max));
                mc.player.sendMessage(new TextComponentString(color + label + ": " + part));
                sent++;
                if (sent >= 120)
                {
                    mc.player.sendMessage(new TextComponentString(color + label + ": <truncated>"));
                    return;
                }
            }
        }
    }

    private String resolveMlDslCompilerPath()
    {
        String local = System.getenv("LOCALAPPDATA");
        if (local != null && !local.trim().isEmpty())
        {
            File[] candidates = new File[]{
                new File(local, "MLDSL\\mldsl.exe"),
                new File(local, "Programs\\MLDSL\\mldsl.exe"),
                new File(local, "MLDSL\\mldsl.py"),
                new File(local, "Programs\\MLDSL\\mldsl.py")
            };
            for (File c : candidates)
            {
                if (c.isFile())
                {
                    return c.getAbsolutePath();
                }
            }
        }
        if (isCommandAvailable("mldsl"))
        {
            return "mldsl";
        }
        return null;
    }

    private void runExportCodeDebugCommand(MinecraftServer server, ICommandSender sender, String[] args)
    {
        if (args != null && args.length > 0)
        {
            String v = args[0] == null ? "" : args[0].trim().toLowerCase(Locale.ROOT);
            if ("on".equals(v) || "1".equals(v) || "true".equals(v))
            {
                exportCodeDebug = true;
            }
            else if ("off".equals(v) || "0".equals(v) || "false".equals(v))
            {
                exportCodeDebug = false;
            }
            else
            {
                setActionBar(false, "&cUsage: /exportcodedebug [on|off]", 3000L);
                return;
            }
        }
        else
        {
            exportCodeDebug = !exportCodeDebug;
        }
        setActionBar(true, exportCodeDebug ? "&aExportCode debug ON" : "&cExportCode debug OFF", 3000L);
        Minecraft mc = Minecraft.getMinecraft();
        if (mc != null && mc.player != null)
        {
            mc.player.sendMessage(new TextComponentString(TextFormatting.YELLOW
                + "exportcodedebug=" + exportCodeDebug + " (details in chat + latest.log)"));
        }
    }

    private File resolveMlDslApiAliasesPath(String compilerPath)
    {
        try
        {
            ExecResult paths = runMlDslCommand(compilerPath, 6000L, "paths");
            if (paths.ok && paths.stdout != null)
            {
                for (String line : paths.stdout.split("\\r?\\n"))
                {
                    if (line == null)
                    {
                        continue;
                    }
                    String t = line.trim();
                    if (!t.startsWith("api_aliases="))
                    {
                        continue;
                    }
                    String p = t.substring("api_aliases=".length()).trim();
                    if (!p.isEmpty())
                    {
                        File f = new File(p);
                        if (f.isFile())
                        {
                            return f;
                        }
                    }
                }
            }
        }
        catch (Exception ignore) { }

        String local = System.getenv("LOCALAPPDATA");
        if (local != null && !local.trim().isEmpty())
        {
            File[] candidates = new File[]{
                new File(local, "Programs\\MLDSL\\app\\out\\api_aliases.json"),
                new File(local, "Programs\\MLDSL\\seed_out\\api_aliases.json"),
                new File(local, "MLDSL\\app\\out\\api_aliases.json"),
                new File(local, "MLDSL\\seed_out\\api_aliases.json")
            };
            for (File c : candidates)
            {
                if (c.isFile())
                {
                    return c;
                }
            }
        }
        return null;
    }

    private static boolean isCommandAvailable(String cmd)
    {
        try
        {
            ProcessBuilder pb = new ProcessBuilder("cmd", "/c", "where", cmd);
            pb.redirectErrorStream(true);
            Process p = pb.start();
            int code = p.waitFor();
            return code == 0;
        }
        catch (Exception e)
        {
            return false;
        }
    }

    private ExecResult runMlDslExportToMldsl(String compilerPath, File exportFile, File outMldsl)
    {
        File apiAliases = resolveMlDslApiAliasesPath(compilerPath);
        if (apiAliases == null || !apiAliases.isFile())
        {
            return new ExecResult(false, 2, "",
                "api_aliases.json not found; conversion without --api is disabled to prevent wrong action mapping.");
        }
        return runMlDslCommand(compilerPath, 60_000L,
            "exportcode",
            exportFile.getAbsolutePath(),
            "--api",
            apiAliases.getAbsolutePath(),
            "-o",
            outMldsl.getAbsolutePath()
        );
    }

    private ExecResult runMlDslCompileToPlan(String compilerPath, File mldslFile, File planFile)
    {
        return runMlDslCommand(compilerPath, 60_000L,
            "compile",
            mldslFile.getAbsolutePath(),
            "--plan",
            planFile.getAbsolutePath()
        );
    }

    private ExecResult runMlDslCommand(String compilerPath, long timeoutMs, String... args)
    {
        List<String[]> attempts = new ArrayList<>();
        String cp = compilerPath == null ? "" : compilerPath.trim();
        if (cp.isEmpty())
        {
            return new ExecResult(false, -1, "", "compiler path is empty");
        }

        boolean isPy = cp.toLowerCase(Locale.ROOT).endsWith(".py");
        if (isPy)
        {
            attempts.add(concatCmd(new String[]{"py", "-3", cp}, args));
            attempts.add(concatCmd(new String[]{"python", cp}, args));
            attempts.add(concatCmd(new String[]{cp}, args));
        }
        else
        {
            attempts.add(concatCmd(new String[]{cp}, args));
        }

        ExecResult last = new ExecResult(false, -1, "", "no attempts");
        for (String[] cmd : attempts)
        {
            ExecResult r = runProcess(cmd, timeoutMs);
            if (r.ok)
            {
                return r;
            }
            last = r;
            String msg = (r.stderr == null ? "" : r.stderr).toLowerCase(Locale.ROOT);
            // If launcher is missing, try next attempt.
            if (msg.contains("cannot find the file") || msg.contains("is not recognized")
                || msg.contains("filenotfoundexception") || msg.contains("no such file"))
            {
                continue;
            }
            // For real command/runtime errors, stop and return immediately.
            break;
        }
        return last;
    }

    private static String[] concatCmd(String[] prefix, String[] args)
    {
        int n1 = prefix == null ? 0 : prefix.length;
        int n2 = args == null ? 0 : args.length;
        String[] out = new String[n1 + n2];
        int k = 0;
        if (prefix != null)
        {
            for (String s : prefix)
            {
                out[k++] = s;
            }
        }
        if (args != null)
        {
            for (String s : args)
            {
                out[k++] = s;
            }
        }
        return out;
    }

    private ExecResult runProcess(String[] cmd, long timeoutMs)
    {
        try
        {
            ProcessBuilder pb = new ProcessBuilder(cmd);
            Map<String, String> env = pb.environment();
            // Force UTF-8 process I/O to avoid mojibake in stderr/stdout on Windows.
            env.put("PYTHONUTF8", "1");
            env.put("PYTHONIOENCODING", "UTF-8");
            env.put("LC_ALL", "C.UTF-8");
            env.put("LANG", "C.UTF-8");
            pb.redirectErrorStream(false);
            Process p = pb.start();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ByteArrayOutputStream err = new ByteArrayOutputStream();
            Thread tOut = new Thread(() -> pumpStream(p.getInputStream(), out), "proc-out");
            Thread tErr = new Thread(() -> pumpStream(p.getErrorStream(), err), "proc-err");
            tOut.setDaemon(true);
            tErr.setDaemon(true);
            tOut.start();
            tErr.start();

            boolean done = p.waitFor(Math.max(1000L, timeoutMs), TimeUnit.MILLISECONDS);
            if (!done)
            {
                try
                {
                    p.destroyForcibly();
                }
                catch (Exception ignore) { }
                return new ExecResult(false, -1, out.toString(StandardCharsets.UTF_8.name()), "timeout");
            }
            try { tOut.join(200L); } catch (Exception ignore) { }
            try { tErr.join(200L); } catch (Exception ignore) { }
            int code = p.exitValue();
            return new ExecResult(code == 0, code,
                out.toString(StandardCharsets.UTF_8.name()),
                err.toString(StandardCharsets.UTF_8.name()));
        }
        catch (Exception e)
        {
            return new ExecResult(false, -1, "", e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    private static void pumpStream(InputStream in, OutputStream out)
    {
        if (in == null || out == null)
        {
            return;
        }
        byte[] buf = new byte[8192];
        try
        {
            while (true)
            {
                int n = in.read(buf);
                if (n < 0)
                {
                    break;
                }
                out.write(buf, 0, n);
            }
        }
        catch (Exception ignore) { }
    }

    private static String extractExportNameFromArgs(String[] args)
    {
        if (args == null || args.length == 0)
        {
            return "";
        }
        String raw = args[args.length - 1];
        return raw == null ? "" : raw.trim();
    }

    private ExportSelection getValidSelectedRows(World world)
    {
        ExportSelection out = new ExportSelection();
        if (world == null)
        {
            return out;
        }
        int dim = world.provider.getDimension();
        Set<BlockPos> set = codeSelectedGlassesByDim.get(dim);
        if (set == null || set.isEmpty())
        {
            return out;
        }
        out.totalSelected = set.size();
        for (BlockPos p : set)
        {
            if (p == null)
            {
                continue;
            }
            if (!world.isBlockLoaded(p))
            {
                out.skippedUnloaded++;
                continue;
            }
            if (world.getBlockState(p.up()).getBlock() == Blocks.AIR)
            {
                out.skippedEmpty++;
                continue;
            }
            out.valid.add(p);
        }
        return out;
    }

    private static class ExportSelection
    {
        int totalSelected = 0;
        int skippedUnloaded = 0;
        int skippedEmpty = 0;
        final List<BlockPos> valid = new ArrayList<>();
    }

    private void runCodeSelectorCommand(MinecraftServer server, ICommandSender sender, String[] args)
    {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.player == null || mc.playerController == null)
        {
            return;
        }
        if (!mc.playerController.isInCreativeMode() || mc.playerController.getCurrentGameType() != GameType.CREATIVE)
        {
            setActionBar(false, "&c\u041d\u0443\u0436\u0435\u043d Creative", 2500L);
            mc.player.sendMessage(new TextComponentString(TextFormatting.RED
                + "Code Selector: \u0442\u0440\u0435\u0431\u0443\u0435\u0442\u0441\u044f Creative \u0440\u0435\u0436\u0438\u043c."));
            return;
        }
        int slot = giveItemToHotbarSlot(mc, buildCodeSelectorItem());
        try
        {
            mc.player.inventory.currentItem = slot;
            mc.player.connection.sendPacket(new CPacketHeldItemChange(slot));
        }
        catch (Exception ignore) { }
        setActionBar(true,
            "&aCode Selector \u0432\u044b\u0434\u0430\u043d. "
                + "\u041f\u041a\u041c: \u0432\u044b\u0431\u0440\u0430\u0442\u044c/\u0443\u0431\u0440\u0430\u0442\u044c \u0441\u0442\u0440\u043e\u043a\u0443, "
                + "\u041b\u041a\u041c: \u043e\u0447\u0438\u0441\u0442\u0438\u0442\u044c, F: \u0437\u0430\u043a\u043e\u043d\u0447\u0438\u0442\u044c. "
                + "\u0417\u0430\u0442\u0435\u043c: /exportcode [name]",
            6500L);
    }

    private static List<Integer> parseCsvInts(String raw)
    {
        List<Integer> out = new ArrayList<>();
        if (raw == null)
        {
            return out;
        }
        String s = raw.trim();
        if (s.isEmpty())
        {
            return out;
        }
        for (String part : s.split(","))
        {
            String t = part == null ? "" : part.trim();
            if (t.isEmpty())
            {
                continue;
            }
            try
            {
                out.add(Integer.parseInt(t));
            }
            catch (Exception ignore) { }
        }
        return out;
    }

    private static List<Integer> parseFloorArgsToY(String raw)
    {
        List<Integer> nums = parseCsvInts(raw);
        List<Integer> out = new ArrayList<>();
        for (Integer n : nums)
        {
            if (n == null)
            {
                continue;
            }
            int v = n;
            // Support floor indexes (1..20) and direct Y values for backward compatibility.
            if (v >= 1 && v <= 20)
            {
                v = (v * 10) - 10;
            }
            out.add(v);
        }
        return out;
    }

    private static String sanitizeExportFilenameToken(String raw)
    {
        if (raw == null)
        {
            return "";
        }
        String s = raw.trim();
        if (s.isEmpty())
        {
            return "";
        }
        s = s.replaceAll("[\\\\/:*?\"<>|]", "_");
        s = s.replaceAll("[\\p{Cntrl}]+", "_");
        s = s.replaceAll("\\s+", "_");
        s = s.replaceAll("_+", "_");
        s = s.replaceAll("^[_\\.]+", "");
        if (s.length() > 64)
        {
            s = s.substring(0, 64);
        }
        return s;
    }

    private List<BlockPos> collectLoadedBlueGlassesOnFloors(World world, Collection<Integer> floors)
    {
        if (world == null || floors == null || floors.isEmpty())
        {
            return Collections.emptyList();
        }
        Set<Integer> ys = new HashSet<>();
        ys.addAll(floors);
        if (ys.isEmpty())
        {
            return Collections.emptyList();
        }
        Set<BlockPos> out = new LinkedHashSet<>();
        try
        {
            if (!(world.getChunkProvider() instanceof ChunkProviderClient))
            {
                return Collections.emptyList();
            }
            ChunkProviderClient cpc = (ChunkProviderClient) world.getChunkProvider();
            Field mapField;
            try
            {
                mapField = ChunkProviderClient.class.getDeclaredField("chunkMapping");
            }
            catch (NoSuchFieldException nsf)
            {
                mapField = ChunkProviderClient.class.getDeclaredField("field_73236_b");
            }
            mapField.setAccessible(true);
            Object mapObj = mapField.get(cpc);
            if (mapObj == null)
            {
                return Collections.emptyList();
            }
            Collection<?> chunks;
            if (mapObj instanceof Map)
            {
                chunks = ((Map<?, ?>) mapObj).values();
            }
            else
            {
                // fastutil Long2ObjectMap in 1.12
                Method valuesMethod = mapObj.getClass().getMethod("values");
                Object valuesObj = valuesMethod.invoke(mapObj);
                if (!(valuesObj instanceof Collection))
                {
                    return Collections.emptyList();
                }
                chunks = (Collection<?>) valuesObj;
            }
            for (Object o : chunks)
            {
                if (!(o instanceof Chunk))
                {
                    continue;
                }
                Chunk ch = (Chunk) o;
                int baseX = ch.x * 16;
                int baseZ = ch.z * 16;
                for (Integer y : ys)
                {
                    if (y == null || y < 0 || y > 255)
                    {
                        continue;
                    }
                    for (int dz = 0; dz < 16; dz++)
                    {
                        for (int dx = 0; dx < 16; dx++)
                        {
                            BlockPos p = new BlockPos(baseX + dx, y, baseZ + dz);
                            if (!world.isBlockLoaded(p, false))
                            {
                                continue;
                            }
                            if (BlueGlassCodeMap.isBlueGlass(world, p))
                            {
                                out.add(p.toImmutable());
                            }
                        }
                    }
                }
            }
        }
        catch (Exception ignore)
        {
            return Collections.emptyList();
        }
        List<BlockPos> list = new ArrayList<>(out);
        list.sort(Comparator.<BlockPos>comparingInt(BlockPos::getY)
            .thenComparingInt(BlockPos::getZ)
            .thenComparingInt(BlockPos::getX));
        return list;
    }

    private String buildExportCodeJson(World world, List<BlockPos> glasses, int maxSteps)
    {
        return buildExportCodeJson(world, glasses, maxSteps, true);
    }

    private String buildExportCodeJson(World world, List<BlockPos> glasses, int maxSteps, boolean preferChestCache)
    {
        Minecraft mc = Minecraft.getMinecraft();
        exportCodeDbg(mc, "buildExportCodeJson: glasses=" + (glasses == null ? 0 : glasses.size())
            + " maxSteps=" + maxSteps);
        String scope = getCodeGlassScopeKey(world);
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"version\":2,");
        sb.append("\"scopeKey\":\"").append(escapeJson(scope)).append("\",");
        sb.append("\"exportedAt\":").append(System.currentTimeMillis()).append(",");
        sb.append("\"rows\":[");

        boolean firstRow = true;
        int rowIndex = 0;
        for (BlockPos glassPos : glasses)
        {
            if (glassPos == null)
            {
                continue;
            }
            String rowJson = buildExportRowJson(world, glassPos, Math.max(32, maxSteps), rowIndex++, preferChestCache);
            if (rowJson == null || rowJson.isEmpty())
            {
                exportCodeDbg(mc, "row skipped: glass=" + glassPos + " rowJson empty");
                continue;
            }
            if (!firstRow)
            {
                sb.append(",");
            }
            firstRow = false;
            sb.append(rowJson);
        }
        sb.append("]}");
        return sb.toString();
    }

    private String buildExportRowJson(World world, BlockPos glassPos, int maxSteps, int rowIndex, boolean preferChestCache)
    {
        Minecraft mc = Minecraft.getMinecraft();
        if (world == null || glassPos == null || !world.isBlockLoaded(glassPos))
        {
            exportCodeDbg(mc, "row[" + rowIndex + "] abort: glass null/unloaded glass=" + glassPos);
            return null;
        }

        ExportCodeCore.RowContext ctx = new ExportCodeCore.RowContext()
        {
            @Override
            public boolean isLoaded(ExportCodeCore.Pos pos)
            {
                return world.isBlockLoaded(new BlockPos(pos.x, pos.y, pos.z), false);
            }

            @Override
            public String getBlockId(ExportCodeCore.Pos pos)
            {
                IBlockState st = world.getBlockState(new BlockPos(pos.x, pos.y, pos.z));
                Block b = st == null ? Blocks.AIR : st.getBlock();
                ResourceLocation id = b == null ? null : b.getRegistryName();
                return id == null ? "minecraft:air" : id.toString();
            }

            @Override
            public String[] getSignLinesAtEntry(ExportCodeCore.Pos entryPos)
            {
                BlockPos entry = new BlockPos(entryPos.x, entryPos.y, entryPos.z);
                BlockPos signPos = findSignAtZMinus1(world, entry);
                if (signPos == null)
                {
                    return null;
                }
                TileEntity te = world.getTileEntity(signPos);
                if (!(te instanceof TileEntitySign))
                {
                    return null;
                }
                TileEntitySign sign = (TileEntitySign) te;
                String[] out = new String[]{"", "", "", ""};
                for (int i = 0; i < 4 && i < sign.signText.length; i++)
                {
                    String raw = sign.signText[i] == null ? "" : sign.signText[i].getUnformattedText();
                    raw = TextFormatting.getTextWithoutFormattingCodes(raw);
                    out[i] = raw == null ? "" : raw;
                }
                return out;
            }

            @Override
            public String getChestJsonAtEntry(ExportCodeCore.Pos entryPos, boolean preferChestCacheInner)
            {
                BlockPos entry = new BlockPos(entryPos.x, entryPos.y, entryPos.z);
                BlockPos signPos = findSignAtZMinus1(world, entry);
                return buildNearbyExportChestJson(world, entry, signPos, preferChestCacheInner);
            }

            @Override
            public String getFacing(ExportCodeCore.Pos pos)
            {
                try
                {
                    IBlockState st = world.getBlockState(new BlockPos(pos.x, pos.y, pos.z));
                    EnumFacing f = st == null ? null : st.getValue(BlockPistonBase.FACING);
                    return f == null ? "" : f.getName().toLowerCase(Locale.ROOT);
                }
                catch (Exception ignore)
                {
                    return "";
                }
            }
        };

        return ExportCodeCore.buildRowJson(
            ctx,
            new ExportCodeCore.Pos(glassPos.getX(), glassPos.getY(), glassPos.getZ()),
            Math.max(32, maxSteps),
            rowIndex,
            preferChestCache,
            msg -> exportCodeDbg(mc, msg)
        );
    }

    private String summarizeRows(List<BlockPos> rows, int max)
    {
        if (rows == null || rows.isEmpty())
        {
            return "[]";
        }
        StringBuilder sb = new StringBuilder("[");
        int n = Math.min(max, rows.size());
        for (int i = 0; i < n; i++)
        {
            if (i > 0)
            {
                sb.append(", ");
            }
            sb.append(rows.get(i));
        }
        if (rows.size() > max)
        {
            sb.append(", ... +").append(rows.size() - max);
        }
        sb.append("]");
        return sb.toString();
    }

    private String blockNameSafe(Block b)
    {
        if (b == null)
        {
            return "null";
        }
        ResourceLocation id = b.getRegistryName();
        return id == null ? b.toString() : id.toString();
    }

    private void exportCodeDbg(Minecraft mc, String msg)
    {
        if (!exportCodeDebug)
        {
            return;
        }
        String line = "[BetterCode] exportcode-debug: " + msg;
        if (logger != null)
        {
            logger.info(line);
        }
        if (mc != null && mc.player != null)
        {
            mc.player.sendMessage(new TextComponentString(TextFormatting.GRAY + line));
        }
    }

    private static boolean allEmpty(String[] lines)
    {
        if (lines == null)
        {
            return true;
        }
        for (String s : lines)
        {
            if (s != null && !s.trim().isEmpty())
            {
                return false;
            }
        }
        return true;
    }

    private static String posJson(BlockPos pos)
    {
        if (pos == null)
        {
            return "{\"x\":0,\"y\":0,\"z\":0}";
        }
        return "{\"x\":" + pos.getX() + ",\"y\":" + pos.getY() + ",\"z\":" + pos.getZ() + "}";
    }

    private String blockJson(BlockPos pos, IBlockState state, String[] signLines, String facing, String chestJson)
    {
        Block b = state == null ? Blocks.AIR : state.getBlock();
        ResourceLocation id = b == null ? null : b.getRegistryName();
        String rid = id == null ? "minecraft:air" : id.toString();

        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"block\":\"").append(escapeJson(rid)).append("\",");
        sb.append("\"pos\":").append(posJson(pos));

        if (facing != null && !facing.isEmpty())
        {
            sb.append(",\"facing\":\"").append(escapeJson(facing)).append("\"");
        }
        if (signLines != null)
        {
            sb.append(",\"sign\":[");
            for (int i = 0; i < 4; i++)
            {
                if (i > 0) sb.append(",");
                String v = i < signLines.length ? signLines[i] : "";
                sb.append("\"").append(escapeJson(v == null ? "" : v)).append("\"");
            }
            sb.append("]");
        }
        if (chestJson != null && !chestJson.isEmpty())
        {
            sb.append(",\"chest\":").append(chestJson);
        }
        sb.append("}");
        return sb.toString();
    }

    private String buildNearbyExportChestJson(World world, BlockPos entryPos, BlockPos signPos, boolean preferChestCache)
    {
        BlockPos chestPos = findNearbyExportChestPos(world, entryPos, signPos);
        if (chestPos == null)
        {
            return null;
        }
        List<ItemStack> liveItems = new ArrayList<>();
        String title = "";
        int size = 27;
        TileEntity te = world.getTileEntity(chestPos);
        if (te instanceof IInventory)
        {
            IInventory inv = (IInventory) te;
            try
            {
                size = Math.max(1, inv.getSizeInventory());
            }
            catch (Exception ignore) { }
            try
            {
                title = inv.getDisplayName() == null ? "" : inv.getDisplayName().getUnformattedText();
            }
            catch (Exception ignore) { }
            for (int i = 0; i < size; i++)
            {
                try
                {
                    ItemStack st = inv.getStackInSlot(i);
                    liveItems.add(st == null ? ItemStack.EMPTY : st);
                }
                catch (Exception e)
                {
                    liveItems.add(ItemStack.EMPTY);
                }
            }
        }

        List<ItemStack> bestItems = liveItems;
        int dim = world.provider.getDimension();
        if (preferChestCache)
        {
            ensureChestCaches(dim);
            String key = chestKey(dim, chestPos);
            ChestCache cached = chestCaches.get(key);
            boolean liveHasAny = containsAnyNonEmpty(liveItems);
            if (!liveHasAny && cached != null && cached.items != null && containsAnyNonEmpty(cached.items))
            {
                bestItems = cached.items;
                if ((title == null || title.trim().isEmpty()) && cached.label != null)
                {
                    title = cached.label;
                }
                if (size <= 0)
                {
                    size = Math.max(1, cached.items.size());
                }
            }
        }

        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"pos\":").append(posJson(chestPos)).append(",");
        sb.append("\"title\":\"").append(escapeJson(title == null ? "" : title)).append("\",");
        sb.append("\"size\":").append(Math.max(1, size)).append(",");
        sb.append("\"slots\":[");
        boolean first = true;
        for (int i = 0; i < bestItems.size(); i++)
        {
            ItemStack st = bestItems.get(i);
            if (st == null || st.isEmpty())
            {
                continue;
            }
            if (!first)
            {
                sb.append(",");
            }
            first = false;
            sb.append(exportChestSlotJson(i, st));
        }
        sb.append("]}");
        return sb.toString();
    }

    private static boolean containsAnyNonEmpty(List<ItemStack> items)
    {
        if (items == null || items.isEmpty())
        {
            return false;
        }
        for (ItemStack st : items)
        {
            if (st != null && !st.isEmpty())
            {
                return true;
            }
        }
        return false;
    }

    private BlockPos findNearbyExportChestPos(World world, BlockPos entryPos, BlockPos signPos)
    {
        if (world == null || entryPos == null)
        {
            return null;
        }
        // Root-fix: bind chest strictly to current action block geometry.
        // Server layout for code actions is chest at +Y from the action block.
        // Broad probing can pick neighboring action chests and corrupt export.
        BlockPos chestPos = entryPos.up();
        if (!world.isBlockLoaded(chestPos, false))
        {
            return null;
        }
        Block b = world.getBlockState(chestPos).getBlock();
        return isExportChestBlock(b) ? chestPos : null;
    }

    private static boolean isExportChestBlock(Block b)
    {
        return b == Blocks.CHEST || b == Blocks.TRAPPED_CHEST || b == Blocks.ENDER_CHEST
            || b == Blocks.WHITE_SHULKER_BOX || b == Blocks.ORANGE_SHULKER_BOX || b == Blocks.MAGENTA_SHULKER_BOX
            || b == Blocks.LIGHT_BLUE_SHULKER_BOX || b == Blocks.YELLOW_SHULKER_BOX || b == Blocks.LIME_SHULKER_BOX
            || b == Blocks.PINK_SHULKER_BOX || b == Blocks.GRAY_SHULKER_BOX || b == Blocks.SILVER_SHULKER_BOX
            || b == Blocks.CYAN_SHULKER_BOX || b == Blocks.PURPLE_SHULKER_BOX || b == Blocks.BLUE_SHULKER_BOX
            || b == Blocks.BROWN_SHULKER_BOX || b == Blocks.GREEN_SHULKER_BOX || b == Blocks.RED_SHULKER_BOX
            || b == Blocks.BLACK_SHULKER_BOX;
    }

    private String exportChestSlotJson(int slot, ItemStack st)
    {
        String reg = "unknown";
        int meta = 0;
        int count = 1;
        String display = "";
        String displayClean = "";
        String nbt = "";
        try
        {
            ResourceLocation id = st.getItem() == null ? null : st.getItem().getRegistryName();
            reg = id == null ? "unknown" : id.toString();
            meta = st.getMetadata();
            count = st.getCount();
            display = st.getDisplayName() == null ? "" : st.getDisplayName();
            String clean = TextFormatting.getTextWithoutFormattingCodes(display);
            displayClean = clean == null ? "" : clean;
            NBTTagCompound t = new NBTTagCompound();
            st.writeToNBT(t);
            nbt = t.toString();
        }
        catch (Exception ignore) { }

        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"slot\":").append(slot).append(",");
        sb.append("\"registry\":\"").append(escapeJson(reg)).append("\",");
        sb.append("\"meta\":").append(meta).append(",");
        sb.append("\"count\":").append(count).append(",");
        sb.append("\"display\":\"").append(escapeJson(display)).append("\",");
        sb.append("\"displayClean\":\"").append(escapeJson(displayClean)).append("\",");
        sb.append("\"lore\":[");
        List<String> lore = getStackLore(st, true);
        for (int i = 0; i < lore.size(); i++)
        {
            if (i > 0)
            {
                sb.append(",");
            }
            sb.append("\"").append(escapeJson(lore.get(i) == null ? "" : lore.get(i))).append("\"");
        }
        sb.append("],");
        sb.append("\"nbt\":\"").append(escapeJson(nbt)).append("\"");
        sb.append("}");
        return sb.toString();
    }

    private BlockPos resolveExportGlassPos(World world)
    {
        if (world == null)
        {
            return null;
        }
        BlockPos glassPos = null;
        if (lastGlassPos != null && lastGlassDim == world.provider.getDimension())
        {
            glassPos = lastGlassPos;
        }
        if (glassPos == null)
        {
            String glassKey = getCodeGlassScopeKey(world);
            if (glassKey != null)
            {
                glassPos = codeBlueGlassById.get(glassKey);
            }
        }
        return glassPos;
    }


    private void registerKeybinds()
    {
        keyOpenCodeMenu = new KeyBinding("key.mcpythonapi.codemenu", Keyboard.KEY_R, "key.categories.mcpythonapi");
        keyOpenCodeMenuAlt = new KeyBinding("key.mcpythonapi.codemenu_alt", Keyboard.KEY_RMENU,
            "key.categories.mcpythonapi");
        keyTpForward = new KeyBinding("key.mcpythonapi.tpfwd", Keyboard.KEY_G, "key.categories.mcpythonapi");
        keyConfirmLoad = new KeyBinding("key.mcpythonapi.confirmload", Keyboard.KEY_NONE, "key.categories.mcpythonapi");
        keyFinishCodeSelect = new KeyBinding("key.mcpythonapi.codeselect_finish", Keyboard.KEY_F, "key.categories.mcpythonapi");
        ClientRegistry.registerKeyBinding(keyOpenCodeMenu);
        ClientRegistry.registerKeyBinding(keyOpenCodeMenuAlt);
        ClientRegistry.registerKeyBinding(keyTpForward);
        ClientRegistry.registerKeyBinding(keyConfirmLoad);
        ClientRegistry.registerKeyBinding(keyFinishCodeSelect);
        hubModule.setConfirmKeyHint(org.lwjgl.input.Keyboard.getKeyName(keyConfirmLoad.getKeyCode()));
    }

    private void runDebugCommand(MinecraftServer server, ICommandSender sender, String[] args)
    {
        debugUi = !debugUi;
        setActionBar(true, debugUi ? "&aDebug ON" : "&cDebug OFF", 2000L);
        Minecraft mc = Minecraft.getMinecraft();
        GuiScreen screen = mc == null ? null : mc.currentScreen;
        String screenInfo = "screen = ";
        if (screen == null)
        {
            screenInfo += "-";
            // clear queues when screen closed
            queuedClicks.clear();
            placeModule.reset();
        }
        else
        {
            String cls = screen.getClass().getSimpleName();
            String title = (screen instanceof GuiChest) ? getGuiTitle((GuiChest) screen) : cls;
            screenInfo += cls + " title='" + title + "'";
        }
        if (mc != null && mc.player != null)
        {
            mc.player.sendMessage(new TextComponentString(screenInfo));
        }
    }

    private void runTestHoloCommand(MinecraftServer server, ICommandSender sender, String[] args)
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

    private void runTestChestHoloCommand(MinecraftServer server, ICommandSender sender, String[] args)
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

    private void runTestShulkerHoloCommand(MinecraftServer server, ICommandSender sender, String[] args)
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

    @Override
    public void setActionBar(boolean primary, String text, long durationMs)
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
        // Record clicked slot for click->menu mapping
        if (Mouse.getEventButtonState())
        {
            try
            {
                Slot hovered = getSlotUnderMouse((GuiContainer) gui);
                if (hovered != null)
                {
                    ItemStack s = hovered.getStack();
                    if (s != null && !s.isEmpty())
                    {
                        // ignore clicks on stained_glass_pane and treat null/empty as air
                        try
                        {
                            ResourceLocation rn = s.getItem().getRegistryName();
                            if (rn != null && "stained_glass_pane".equals(rn.getResourcePath()))
                            {
                                // ignore this click
                            }
                            else
                            {
                                // push history (keep last two keys)
                                String key = getItemNameKey(s);
                                lastClickedKeyHistory[0] = lastClickedKeyHistory[1];
                                lastClickedKeyHistory[1] = key;
                                lastClickedSlotStack = s.copy();
                                lastClickedSlotNumber = hovered.slotNumber;
                                lastClickedSlotMs = System.currentTimeMillis();
                                lastClickedGuiClass = gui.getClass().getSimpleName();
                                if (gui instanceof GuiChest)
                                {
                                    lastClickedGuiTitle = getGuiTitle((GuiChest) gui);
                                }
                                else
                                {
                                    lastClickedGuiTitle = "";
                                }
                                // Immediately snapshot the current GUI contents so ClosedMenu fallback has fresh data
                                try
                                {
                                    Minecraft mm = Minecraft.getMinecraft();
                                    if (gui instanceof GuiContainer && mm != null && mm.player != null)
                                    {
                                        Container c = ((GuiContainer) gui).inventorySlots;
                                        List<ItemStack> snap = new ArrayList<>();
                                        for (Slot slot : c.inventorySlots)
                                        {
                                            if (slot == null) continue;
                                            try { if (slot.inventory == mm.player.inventory) continue; } catch (Exception ignore) {}
                                            if (!slot.getHasStack()) continue;
                                            ItemStack st = slot.getStack();
                                            if (st == null || st.isEmpty()) continue;
                                            snap.add(st.copy());
                                        }
                                        if (!snap.isEmpty())
                                        {
                                            lastObservedContainerItems = snap;
                                            lastObservedContainerMs = System.currentTimeMillis();
                                        }
                                    }
                                }
                                catch (Exception ignore) { }
                            }
                        }
                        catch (Exception ignore)
                        {
                        }
                    }
                }
            }
            catch (Exception ignored) { }
        }
        if (Mouse.getEventButtonState() && Mouse.getEventButton() == 0 && handleDevUtilsSelectorClick((GuiContainer) gui))
        {
            event.setCanceled(true);
            return;
        }

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
        ensureDevUtilsSelectorSlot((GuiContainer) gui);

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
            saveClickMenuIfNeeded();
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
            ensureDevUtilsSelectorSlot((GuiContainer) gui);
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
            ItemStack heldSelector = mc.player.getHeldItemMainhand();
            if (isCodeSelectorItem(heldSelector))
            {
                handleCodeSelectorRightClick(mc, event);
                return;
            }
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
        if (isStainedGlassMeta(event.getWorld(), event.getPos(), 3))
        {
            lastGlassPos = event.getPos();
            lastGlassDim = lastClickedDim;
        }
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
        Minecraft mc = Minecraft.getMinecraft();
        if (mc != null && mc.player != null)
        {
            ItemStack held = mc.player.getHeldItemMainhand();
            if (isCodeSelectorItem(held))
            {
                clearCodeSelectionForWorld(mc.world);
                setActionBar(true, "&eВыделение очищено", 1500L);
                event.setCanceled(true);
                return;
            }
        }
        BlockPos pos = event.getPos();
        clearChestCacheAt(pos);
        clearChestCacheAt(pos.up());
        clearChestCacheAt(pos.up().south());
        clearChestCacheAt(pos.up().north());
        clearChestCacheAt(pos.up().east());
        clearChestCacheAt(pos.up().west());
    }

    @SubscribeEvent
    public void onLeftClickBlockSelectorRowSync(PlayerInteractEvent.LeftClickBlock event)
    {
        if (event == null || !event.getWorld().isRemote)
        {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.world == null)
        {
            return;
        }
        BlockPos pos = event.getPos();
        if (pos == null)
        {
            return;
        }
        BlockPos glassPos = resolveCodeSelectorGlassPos(mc.world, pos);
        if (glassPos == null)
        {
            return;
        }
        int dim = mc.world.provider.getDimension();
        Set<BlockPos> selected = codeSelectedGlassesByDim.get(dim);
        if (selected != null && selected.remove(glassPos))
        {
            setActionBar(true, "&eCode Selector: строка убрана (" + selected.size() + ")", 1500L);
        }
    }

    private void handleCodeSelectorRightClick(Minecraft mc, PlayerInteractEvent.RightClickBlock event)
    {
        if (mc == null || mc.player == null || event == null || event.getWorld() == null)
        {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastCodeSelectorToggleMs < CODE_SELECTOR_TOGGLE_COOLDOWN_MS)
        {
            event.setCanceled(true);
            return;
        }
        lastCodeSelectorToggleMs = now;

        World world = event.getWorld();
        BlockPos clicked = event.getPos();
        BlockPos glassPos = resolveCodeSelectorGlassPos(world, clicked);
        if (glassPos == null)
        {
            setActionBar(false, "&cКликни по синему стеклу или по блоку над ним", 3000L);
            event.setCanceled(true);
            return;
        }
        if (!world.isBlockLoaded(glassPos))
        {
            setActionBar(false, "&cСтекло не загружено (подлети ближе)", 3000L);
            event.setCanceled(true);
            return;
        }
        BlockPos start = glassPos.up();
        if (!world.isBlockLoaded(start, false))
        {
            setActionBar(false, "&cСтрока не загружена (подлети ближе)", 3000L);
            event.setCanceled(true);
            return;
        }
        if (world.getBlockState(start).getBlock() == Blocks.AIR)
        {
            setActionBar(false, "&cПустая строка: над стеклом воздух", 3000L);
            event.setCanceled(true);
            return;
        }

        int dim = world.provider.getDimension();
        Set<BlockPos> set = codeSelectedGlassesByDim.get(dim);
        if (set == null)
        {
            set = new LinkedHashSet<>();
            codeSelectedGlassesByDim.put(dim, set);
        }
        boolean added;
        if (set.contains(glassPos))
        {
            set.remove(glassPos);
            added = false;
        }
        else
        {
            set.add(glassPos);
            added = true;
        }
        setActionBar(true, (added ? "&aВыбрано" : "&eУбрано") + " строк: " + set.size(), 1800L);
        event.setCanceled(true);
    }

    private BlockPos resolveCodeSelectorGlassPos(World world, BlockPos clicked)
    {
        if (world == null || clicked == null)
        {
            return null;
        }
        if (isStainedGlassMeta(world, clicked, 3))
        {
            return clicked;
        }
        BlockPos down = clicked.down();
        if (isStainedGlassMeta(world, down, 3))
        {
            return down;
        }
        return null;
    }

    private void clearCodeSelectionForWorld(World world)
    {
        if (world == null)
        {
            return;
        }
        int dim = world.provider.getDimension();
        Set<BlockPos> set = codeSelectedGlassesByDim.get(dim);
        if (set != null)
        {
            set.clear();
        }
    }

    private void clearAllCodeSelections()
    {
        codeSelectedGlassesByDim.clear();
    }

    private int getSelectedRowCount(World world)
    {
        if (world == null)
        {
            return 0;
        }
        int dim = world.provider.getDimension();
        Set<BlockPos> set = codeSelectedGlassesByDim.get(dim);
        return set == null ? 0 : set.size();
    }

    private boolean isCodeSelectorItem(ItemStack stack)
    {
        if (stack == null || stack.isEmpty())
        {
            return false;
        }
        try
        {
            NBTTagCompound tag = stack.getTagCompound();
            return tag != null && tag.hasKey(CODE_SELECTOR_TAG) && tag.getBoolean(CODE_SELECTOR_TAG);
        }
        catch (Exception ignore)
        {
            return false;
        }
    }

    private boolean isDevUtilsMenu(GuiContainer gui)
    {
        if (!(gui instanceof GuiChest))
        {
            return false;
        }
        String title = getGuiTitle((GuiChest) gui);
        if (title == null)
        {
            return false;
        }
        String clean = TextFormatting.getTextWithoutFormattingCodes(title);
        if (clean == null)
        {
            clean = title;
        }
        clean = clean.trim().toLowerCase(Locale.ROOT);
        return clean.contains(DEV_UTILS_MENU_TITLE.toLowerCase(Locale.ROOT));
    }

    private void ensureDevUtilsSelectorSlot(GuiContainer gui)
    {
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
        if (!isDevUtilsMenu(gui))
        {
            return;
        }
        for (Slot slot : gui.inventorySlots.inventorySlots)
        {
            if (slot == null || slot.inventory == mc.player.inventory)
            {
                continue;
            }
            if (slot.getSlotIndex() == 4 || slot.slotNumber == 4 || slot.slotNumber == 5)
            {
                ItemStack cur = slot.getStack();
                if (!isCodeSelectorItem(cur))
                {
                    slot.putStack(buildCodeSelectorItem());
                }
                break;
            }
        }
    }

    private boolean handleDevUtilsSelectorClick(GuiContainer gui)
    {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.player == null || mc.playerController == null)
        {
            return false;
        }
        if (!editorModeActive || !mc.playerController.isInCreativeMode()
            || mc.playerController.getCurrentGameType() != GameType.CREATIVE)
        {
            return false;
        }
        if (!isDevUtilsMenu(gui))
        {
            return false;
        }
        Slot hovered = getSlotUnderMouse(gui);
        if (hovered == null)
        {
            return false;
        }
        if (!(hovered.getSlotIndex() == 4 || hovered.slotNumber == 4 || hovered.slotNumber == 5))
        {
            return false;
        }
        int slot = giveItemToHotbarSlot(mc, buildCodeSelectorItem());
        try
        {
            mc.player.inventory.currentItem = slot;
            mc.player.connection.sendPacket(new CPacketHeldItemChange(slot));
        }
        catch (Exception ignore) { }
        setActionBar(true,
            "&aCode Selector \u0432\u044b\u0434\u0430\u043d \u043b\u043e\u043a\u0430\u043b\u044c\u043d\u043e (dev utils)",
            1800L);
        return true;
    }

    private ItemStack buildCodeSelectorItem()
    {
        ItemStack stack = new ItemStack(Items.BLAZE_ROD);
        try
        {
            stack.setStackDisplayName(CODE_SELECTOR_TITLE);
            NBTTagCompound tag = stack.getTagCompound();
            if (tag == null)
            {
                tag = new NBTTagCompound();
            }
            tag.setBoolean(CODE_SELECTOR_TAG, true);
            stack.setTagCompound(tag);
        }
        catch (Exception ignore) { }
        return stack;
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
        try
        {
            mc.player.setPositionAndUpdate(mc.player.posX, mc.player.posY + delta, mc.player.posZ);
        }
        catch (Exception ignore)
        {
            mc.player.setPosition(mc.player.posX, mc.player.posY + delta, mc.player.posZ);
        }
        mc.player.motionX = 0.0;
        mc.player.motionY = 0.0;
        mc.player.motionZ = 0.0;
        tpScrollQueue--;
        tpScrollNextMs = now + 300L;
    }

    @Override
    public void buildTpPathQueue(World world, double sx, double sy, double sz, double tx, double ty, double tz)
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
        
        // Enable flight for smooth TP
        Minecraft mc = Minecraft.getMinecraft();
        if (mc != null && mc.player != null)
        {
            mc.player.capabilities.allowFlying = true;
            mc.player.capabilities.isFlying = true;
            mc.player.sendPlayerAbilities();
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
        // Allow the first step quickly; subsequent steps are throttled in handleTpPathQueue.
        tpPathNextMs = System.currentTimeMillis();
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
        // Ensure flight stays enabled during TP path to avoid falling/rubberband.
        try
        {
            mc.player.capabilities.allowFlying = true;
            mc.player.capabilities.isFlying = true;
            mc.player.sendPlayerAbilities();
        }
        catch (Exception ignore) { }
        double[] step = tpPathQueue.poll();
        if (step == null)
        {
            return;
        }
        double nx = mc.player.posX + step[0];
        double ny = mc.player.posY + step[1];
        double nz = mc.player.posZ + step[2];
        try
        {
            mc.player.setPositionAndUpdate(nx, ny, nz);
        }
        catch (Exception ignore)
        {
            mc.player.setPosition(nx, ny, nz);
        }
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
        if (stack.getItem() == Items.APPLE)
        {
            startGiveInput(stack.copy(), INPUT_MODE_APPLE, "GameValue");
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
        // remember these observed items for potential ClosedMenu fallback
        lastObservedContainerItems = new ArrayList<>();
        for (ItemStack st : items)
        {
            lastObservedContainerItems.add(st.isEmpty() ? ItemStack.EMPTY : st.copy());
        }
        long now = System.currentTimeMillis();
        lastObservedContainerMs = now;
        String title = getGuiTitle(chest);
        String hash = buildMenuHash(items);
        CachedMenu menu = new CachedMenu(title, size, items, hash);
        Minecraft mc = Minecraft.getMinecraft();

        // Resolve current chest position/dim for paging-aware snapshots.
        BlockPos chestPos = null;
        int chestDim = 0;
        String chestLabel = lastClickedLabel;
        if (inv instanceof TileEntityChest)
        {
            TileEntityChest te = (TileEntityChest) inv;
            chestPos = te.getPos();
            chestDim = te.getWorld().provider.getDimension();
            chestLabel = getChestLabel(te.getWorld(), chestPos);
            lastClickedPos = chestPos;
            lastClickedDim = chestDim;
            lastClickedMs = now;
        }
        else if (lastClickedPos != null && lastClickedChest && !lastClickedIsSign && now - lastClickedMs < 5000L)
        {
            chestPos = lastClickedPos;
            chestDim = lastClickedDim;
        }

        // Multi-page chest capture:
        // save pages into one absolute-slots cache (slot = local + page*size).
        // IMPORTANT: page auto-clicking is allowed only for explicit flows
        // (regallactions/module publish warmup), never for generic chest snapshots.
        boolean pageTurnRequested = false;
        boolean pageTurnAllowed = modulePublishWarmupActive || regAllActionsModule.isActive();
        if (lastClickedChest && allowChestSnapshot && chestPos != null && size > 0)
        {
            String key = chestKey(chestDim, chestPos);
            if (chestPageScanKey == null || !chestPageScanKey.equals(key) || chestPageScanSize != size)
            {
                resetChestPageScanState();
                chestPageScanKey = key;
                chestPageScanSize = size;
            }
            if (!pageTurnAllowed)
            {
                chestPageAwaitCursorClear = false;
                chestPageAwaitStartMs = 0L;
                chestPageRetryCount = 0;
                chestPageNextActionMs = now;
            }

            if (chestPageAwaitCursorClear)
            {
                boolean cursorEmpty = true;
                try
                {
                    ItemStack carried = mc == null || mc.player == null ? ItemStack.EMPTY : mc.player.inventory.getItemStack();
                    cursorEmpty = carried == null || carried.isEmpty();
                }
                catch (Exception ignore) { }

                boolean changed = chestPageLastHash != null && !chestPageLastHash.equals(hash);
                if (cursorEmpty && changed)
                {
                    chestPageScanIndex++;
                    chestPageAwaitCursorClear = false;
                    chestPageAwaitStartMs = 0L;
                    chestPageNextActionMs = now + 150L;
                    if (logger != null) logger.info("CHEST_PAGE_CLICK switched key={} page={}", chestPageScanKey, chestPageScanIndex + 1);
                    exportCodeDbg(mc, "chest-page: switched to page " + (chestPageScanIndex + 1) + " key=" + chestPageScanKey);
                }
                else if (now - chestPageAwaitStartMs > CHEST_PAGE_WAIT_TIMEOUT_MS)
                {
                    chestPageAwaitCursorClear = false;
                    chestPageAwaitStartMs = 0L;
                    if (logger != null) logger.info("CHEST_PAGE_CLICK wait_timeout key={} retry={}", chestPageScanKey, chestPageRetryCount);
                    exportCodeDbg(mc, "chest-page: wait timeout key=" + chestPageScanKey + " retry=" + chestPageRetryCount);
                }
            }

            if (!chestPageAwaitCursorClear)
            {
                mergeChestPageToCache(chestDim, chestPos, size, chestPageScanIndex, items, chestLabel);

                ItemStack lastSlot = items.isEmpty() ? ItemStack.EMPTY : items.get(size - 1);
                boolean hasNextPage = isNextPageArrow(lastSlot);
                if (modulePublishWarmupActive && logger != null)
                {
                    String reg = "null";
                    String disp = "";
                    try
                    {
                        if (lastSlot != null && !lastSlot.isEmpty())
                        {
                            ResourceLocation rid = lastSlot.getItem() == null ? null : lastSlot.getItem().getRegistryName();
                            reg = rid == null ? "null" : rid.toString();
                            disp = lastSlot.getDisplayName();
                        }
                    }
                    catch (Exception ignore) { }
                    logger.info("CHEST_PAGE_DETECT key={} page={} hasNext={} lastSlotReg={} lastSlotName={}",
                        key, chestPageScanIndex + 1, hasNextPage, reg, disp);
                }
                if (hasNextPage && !pageTurnAllowed)
                {
                    if (logger != null) logger.info("CHEST_PAGE_CLICK skip key={} reason=not_allowed mode=normal_snapshot", key);
                }
                if (hasNextPage && pageTurnAllowed && now >= chestPageNextActionMs)
                {
                    if (chestPageRetryCount < CHEST_PAGE_MAX_RETRIES && mc != null && mc.player != null && mc.playerController != null)
                    {
                        int slotNumber = findNonPlayerSlotNumber(chest, size - 1);
                        if (slotNumber >= 0)
                        {
                            try
                            {
                                mc.playerController.windowClick(chest.inventorySlots.windowId, slotNumber, 0, ClickType.PICKUP, mc.player);
                                chestPageLastHash = hash;
                                chestPageAwaitCursorClear = true;
                                chestPageAwaitStartMs = now;
                                chestPageRetryCount++;
                                chestPageNextActionMs = now + 250L;
                                pageTurnRequested = true;
                                if (logger != null) logger.info("CHEST_PAGE_CLICK click_next key={} page={} try={} windowSlot={}",
                                    chestPageScanKey, chestPageScanIndex + 1, chestPageRetryCount, slotNumber);
                                exportCodeDbg(mc, "chest-page: click next page key=" + chestPageScanKey
                                    + " page=" + (chestPageScanIndex + 1) + " try=" + chestPageRetryCount);
                            }
                            catch (Exception e)
                            {
                                if (logger != null) logger.info("CHEST_PAGE_CLICK click_failed key={} err={}", chestPageScanKey, e.getClass().getSimpleName());
                                exportCodeDbg(mc, "chest-page: click failed key=" + chestPageScanKey + " err=" + e.getClass().getSimpleName());
                            }
                        }
                    }
                    else if (chestPageRetryCount >= CHEST_PAGE_MAX_RETRIES)
                    {
                        if (logger != null) logger.info("CHEST_PAGE_CLICK retries_exhausted key={} pages={}", chestPageScanKey, chestPageScanIndex + 1);
                        exportCodeDbg(mc, "chest-page: retries exhausted key=" + chestPageScanKey + " pages=" + (chestPageScanIndex + 1));
                    }
                }
            }
        }
        else
        {
            resetChestPageScanState();
        }

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
        if (chestPos != null)
        {
            if (!lastClickedChest || !allowChestSnapshot)
            {
                cacheChestInventoryAt(chestDim, chestPos, items, chestLabel);
            }
        }

        // If user clicked a slot that opened this menu, record mapping from clicked item -> available items
        if (lastClickedSlotStack != null && System.currentTimeMillis() - lastClickedSlotMs < 5000L)
        {
            String currentGuiClass = chest.getClass().getSimpleName();
            // If the click happened in the same GUI class/title, skip (not an open-from-click)
            if (lastClickedGuiClass != null && lastClickedGuiClass.equals(currentGuiClass)
                && lastClickedGuiTitle != null && lastClickedGuiTitle.equals(title))
            {
                lastClickedSlotStack = null;
                lastClickedGuiClass = null;
                lastClickedGuiTitle = null;
            }
            
            
            else
            {
                String key = getItemNameKey(lastClickedSlotStack);
                if (!clickMenuMap.containsKey(key))
                {
                    List<ItemStack> copy = new ArrayList<>();
                    for (ItemStack st : items)
                    {
                        copy.add(st.isEmpty() ? ItemStack.EMPTY : st.copy());
                    }
                    clickMenuMap.put(key, copy);
                    clickMenuLocation.put(key, "slot:" + lastClickedSlotNumber + " title:" + title);
                    clickMenuDirty = true;
                    if (mc != null && mc.player != null)
                    {
                        StringBuilder dbg = new StringBuilder();
                        dbg.append("Recorded click ").append(lastClickedSlotNumber).append(" -> ");
                        int show = Math.min(6, copy.size());
                        for (int i = 0; i < show; i++)
                        {
                            ItemStack s = copy.get(i);
                            dbg.append((i==0?"":" ,") + (s.isEmpty()?"empty":s.getDisplayName()));
                        }
                        mc.player.sendMessage(new TextComponentString("Recorded key=" + key + " -> " + dbg.toString()));
                    }
                }
                lastClickedSlotStack = null;
                lastClickedGuiClass = null;
                lastClickedGuiTitle = null;
            }
        }

        // --- Auto-cache: close chest after snapshot ---
        // Guard against premature close on first tick when server has not sent full chest contents yet.
        if (autoCacheInProgress && !pageTurnRequested && !chestPageAwaitCursorClear)
        {
            long ageMs = System.currentTimeMillis() - autoCacheStartMs;
            int nonEmptyCount = 0;
            for (ItemStack st : items)
            {
                if (st != null && !st.isEmpty())
                {
                    nonEmptyCount++;
                }
            }
            // Wait a bit before force-close:
            // - first 350ms always wait;
            // - for large menus (54) with very sparse initial snapshot wait up to 1.2s.
            if (ageMs < 350L || (size >= 54 && nonEmptyCount <= 2 && ageMs < 1200L))
            {
                return;
            }
            if (mc != null && mc.player != null)
            {
                mc.player.closeScreen();
            }
            autoCacheInProgress = false;
            autoCacheTargetPos = null;
            autoCacheStartMs = 0L;
            nextAutoCacheScanMs = System.currentTimeMillis() + 250L;
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
        // store last observed container items for potential ClosedMenu fallback
        lastObservedContainerItems = new ArrayList<>();
        for (ItemStack st : items)
        {
            lastObservedContainerItems.add(st.isEmpty() ? ItemStack.EMPTY : st.copy());
        }
        lastObservedContainerMs = System.currentTimeMillis();
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

        // Also handle openContainer snapshots (same behavior)
        if (lastClickedSlotStack != null && System.currentTimeMillis() - lastClickedSlotMs < 5000L)
        {
            String currentGuiClass = "container:" + menu.title; // approximate class/title
            if (lastClickedGuiClass != null && lastClickedGuiClass.equals(currentGuiClass))
            {
                lastClickedSlotStack = null;
                lastClickedGuiClass = null;
                lastClickedGuiTitle = null;
            }
            else
            {
                String key = getItemNameKey(lastClickedSlotStack);
                if (!clickMenuMap.containsKey(key))
                {
                    List<ItemStack> copy = new ArrayList<>();
                    for (ItemStack st : items)
                    {
                        copy.add(st.isEmpty() ? ItemStack.EMPTY : st.copy());
                    }
                    clickMenuMap.put(key, copy);
                    clickMenuLocation.put(key, "slot:" + lastClickedSlotNumber + " container");
                    clickMenuDirty = true;
                    if (mc != null && mc.player != null)
                    {
                        StringBuilder dbg = new StringBuilder();
                        dbg.append("Recorded click ").append(lastClickedSlotNumber).append(" -> ");
                        int show = Math.min(6, copy.size());
                        for (int i = 0; i < show; i++)
                        {
                            ItemStack s = copy.get(i);
                            dbg.append((i==0?"":" ,") + (s.isEmpty()?"empty":s.getDisplayName()));
                        }
                        mc.player.sendMessage(new TextComponentString("Recorded key=" + key + " -> " + dbg.toString()));
                    }
                }
                lastClickedSlotStack = null;
                lastClickedGuiClass = null;
                lastClickedGuiTitle = null;
            }
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

    private void resetChestPageScanState()
    {
        chestPageScanKey = null;
        chestPageScanSize = 0;
        chestPageScanIndex = 0;
        chestPageLastHash = "";
        chestPageAwaitCursorClear = false;
        chestPageAwaitStartMs = 0L;
        chestPageNextActionMs = 0L;
        chestPageRetryCount = 0;
    }

    private static boolean isNextPageArrow(ItemStack st)
    {
        if (st == null || st.isEmpty() || st.getItem() != Items.ARROW)
        {
            return false;
        }
        List<String> lore = ItemStackUtils.getLore(st);
        if (lore == null || lore.isEmpty())
        {
            return false;
        }
        boolean hasOpen = false;
        boolean hasNext = false;
        for (String ln : lore)
        {
            String n = normalizePageLoreLine(ln);
            if (n.contains("нажми чтобы открыть"))
            {
                hasOpen = true;
            }
            if (n.contains("следующую страницу"))
            {
                hasNext = true;
            }
        }
        return hasOpen && hasNext;
    }

    private static String normalizePageLoreLine(String line)
    {
        String n = line == null ? "" : TextFormatting.getTextWithoutFormattingCodes(line);
        if (n == null)
        {
            n = "";
        }
        n = n.replace('\u00A0', ' ').toLowerCase(Locale.ROOT);
        // ignore punctuation and style symbols, keep letters/digits/spaces
        n = n.replaceAll("[^\\p{L}\\p{N}\\s]+", " ");
        n = n.replaceAll("\\s+", " ").trim();
        return n;
    }

    private void mergeChestPageToCache(int dim, BlockPos pos, int pageSize, int pageIndex, List<ItemStack> pageItems, String label)
    {
        if (pos == null || pageSize <= 0 || pageIndex < 0 || pageItems == null)
        {
            return;
        }
        String key = chestKey(dim, pos);
        if (key == null)
        {
            return;
        }
        ChestCache prev = chestCaches.get(key);
        List<ItemStack> merged = new ArrayList<>();
        if (prev != null && prev.items != null)
        {
            for (ItemStack st : prev.items)
            {
                merged.add(st == null || st.isEmpty() ? ItemStack.EMPTY : st.copy());
            }
        }
        int offset = pageIndex * pageSize;
        while (merged.size() < offset + pageSize)
        {
            merged.add(ItemStack.EMPTY);
        }
        for (int i = 0; i < pageSize && i < pageItems.size(); i++)
        {
            ItemStack st = pageItems.get(i);
            merged.set(offset + i, st == null || st.isEmpty() ? ItemStack.EMPTY : st.copy());
        }
        chestCaches.put(key, new ChestCache(dim, pos, merged, System.currentTimeMillis(), label));
        updateChestIdCache(dim, pos, merged, label);
    }

    private int findNonPlayerSlotNumber(GuiContainer gui, int nonPlayerIndex)
    {
        if (gui == null || gui.inventorySlots == null || nonPlayerIndex < 0)
        {
            return -1;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.player == null)
        {
            return -1;
        }
        int idx = 0;
        for (Slot slot : gui.inventorySlots.inventorySlots)
        {
            if (slot == null || slot.inventory == mc.player.inventory)
            {
                continue;
            }
            if (idx == nonPlayerIndex)
            {
                return slot.slotNumber;
            }
            idx++;
        }
        return -1;
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
        // Execute queued clicks against the current open container
        try
        {
            for (ClickAction act : new ArrayList<>(queuedClicks))
            {
                if (act == null)
                {
                    continue;
                }
                try
                {
                    int windowId = container.windowId;
                    int slot = act.slotNumber;
                    int button = act.button;
                    ClickType type = act.type == null ? ClickType.PICKUP : act.type;
                    mc.playerController.windowClick(windowId, slot, button, type, mc.player);
                    mc.playerController.updateController();
                }
                catch (Exception e)
                {
                    // ignore individual click failures
                }
            }
        }
        finally
        {
            queuedClicks.clear();
        }
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
        resetChestPageScanState();
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

    @Override
    public String getItemNameKey(ItemStack stack)
    {
        if (stack == null || stack.isEmpty())
        {
            return "empty";
        }
        String name = stack.getDisplayName();
        if (name == null)
        {
            name = "";
        }
        String cleaned = net.minecraft.util.text.TextFormatting.getTextWithoutFormattingCodes(name);
        if (cleaned == null)
        {
            cleaned = "";
        }
        cleaned = cleaned.trim().toLowerCase(Locale.ROOT);
        if (cleaned.isEmpty())
        {
            List<String> lore = getStackLore(stack, false);
            for (String line : lore)
            {
                if (line == null)
                {
                    continue;
                }
                String loreClean = line.trim().toLowerCase(Locale.ROOT);
                if (!loreClean.isEmpty())
                {
                    cleaned = loreClean;
                    break;
                }
            }
        }
        return cleaned.isEmpty() ? "empty" : cleaned;
    }

    @Override
    public String normalizeForMatch(String s)
    {
        if (s == null)
        {
            return "";
        }
        String without = net.minecraft.util.text.TextFormatting.getTextWithoutFormattingCodes(s);
        if (without == null)
        {
            without = s;
        }
        // normalize whitespace and lower-case
        without = without.replace('\u00A0', ' ');
        without = without.replaceAll("\\s+", " ").trim().toLowerCase(Locale.ROOT);
        return without;
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

    @Override
    public String getGuiTitle(GuiChest chest)
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

    private List<ItemStack> getOpenContainerItems(Container container)
    {
        List<ItemStack> items = new ArrayList<>();
        if (container == null)
        {
            return items;
        }
        for (Slot slot : container.inventorySlots)
        {
            if (slot == null)
            {
                continue;
            }
            if (slot.inventory == null)
            {
                continue;
            }
            // skip player inventory slots
            try
            {
                if (slot.inventory == null || slot.inventory == Minecraft.getMinecraft().player.inventory)
                {
                    continue;
                }
            }
            catch (Exception e)
            {
                // ignore
            }
            ItemStack s = slot.getStack();
            items.add(s == null || s.isEmpty() ? ItemStack.EMPTY : s.copy());
        }
        return items;
    }

    private int compareMenuDiff(List<ItemStack> a, List<ItemStack> b)
    {
        if (a == null) a = new ArrayList<>();
        if (b == null) b = new ArrayList<>();
        int diff = 0;
        int n = Math.max(a.size(), b.size());
        for (int i = 0; i < n; i++)
        {
            String ak = i < a.size() && a.get(i) != null && !a.get(i).isEmpty() ? normalizeForMatch(getItemNameKey(a.get(i))) : "";
            String bk = i < b.size() && b.get(i) != null && !b.get(i).isEmpty() ? normalizeForMatch(getItemNameKey(b.get(i))) : "";
            if (!ak.equals(bk)) diff++;
        }
        return diff;
    }

    private int[] compareMenuDiffWithFirst(List<ItemStack> a, List<ItemStack> b)
    {
        if (a == null) a = new ArrayList<>();
        if (b == null) b = new ArrayList<>();
        int diff = 0;
        int first = -1;
        int n = Math.max(a.size(), b.size());
        for (int i = 0; i < n; i++)
        {
            String ak = i < a.size() && a.get(i) != null && !a.get(i).isEmpty() ? normalizeForMatch(getItemNameKey(a.get(i))) : "";
            String bk = i < b.size() && b.get(i) != null && !b.get(i).isEmpty() ? normalizeForMatch(getItemNameKey(b.get(i))) : "";
            if (!ak.equals(bk))
            {
                diff++;
                if (first == -1) first = i;
            }
        }
        return new int[]{diff, first};
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
            {0, 18}, {-18, 0}, {18, 0}, {0, -18}
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

    @Override
    public boolean isGlassPane(ItemStack stack)
    {
        if (stack == null || stack.isEmpty())
        {
            return false;
        }
        return stack.getItem() == net.minecraft.item.Item.getItemFromBlock(Blocks.STAINED_GLASS_PANE);
    }

    @Override
    public void setInputActive(boolean active)
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

    @Override
    public void setInputSaveVariable(boolean save)
    {
        inputSaveVariable = save;
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

    @Override
    public void setInputText(String text)
    {
        ensureInputField();
        if (inputField != null)
        {
            inputField.setText(text == null ? "" : text);
            inputField.setCursorPositionEnd();
        }
    }

    @Override
    public void startSlotInput(GuiContainer container, Slot target, ItemStack template, int mode, String preset,
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

    @Override
    public String getCodeGlassScopeKey(World world)
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
        // Remember the last glass position for exportline command
        lastGlassPos = found;
        lastGlassDim = world.provider.getDimension();
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
            snapshot.put(key, new CachedMenu(menu.title, menu.size, ItemStackUtils.copyItemStackList(menu.items), menu.hash));
        }
        final CachedMenu customSnapshot = customMenuCache == null ? null
            : new CachedMenu(customMenuCache.title, customMenuCache.size, ItemStackUtils.copyItemStackList(customMenuCache.items),
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

    private void saveClickMenuIfNeeded()
    {
        if (!clickMenuDirty || clickMenuFile == null)
        {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastClickMenuSaveMs < 10000L)
        {
            return;
        }
        if (clickMenuSaveQueued.get())
        {
            return;
        }
        lastClickMenuSaveMs = now;
        final File target = clickMenuFile;
        final Map<String, List<ItemStack>> snapshot = new LinkedHashMap<>();
        final Map<String, String> locSnapshot = new LinkedHashMap<>();
        for (Map.Entry<String, List<ItemStack>> entry : clickMenuMap.entrySet())
        {
            String key = entry.getKey();
            List<ItemStack> items = entry.getValue();
            if (key == null || items == null)
            {
                continue;
            }
            snapshot.put(key, ItemStackUtils.copyItemStackList(items));
            String loc = clickMenuLocation.get(key);
            if (loc != null && !loc.isEmpty())
            {
                locSnapshot.put(key, loc);
            }
        }
        clickMenuDirty = false;
        clickMenuSaveQueued.set(true);
        ioExecutor.execute(() -> {
            try
            {
                saveClickMenuSnapshot(target, snapshot, locSnapshot);
            }
            finally
            {
                clickMenuSaveQueued.set(false);
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
            snapshot.put(id, new ChestCache(cache.dim, cache.pos, ItemStackUtils.copyItemStackList(cache.items),
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

    private void saveCodeCachesIfNeeded()
    {
        if (!codeCacheDirty || codeCacheFile == null)
        {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastCodeCacheSaveMs < 10000L)
        {
            return;
        }
        if (codeCacheSaveQueued.get())
        {
            return;
        }
        lastCodeCacheSaveMs = now;
        final File target = codeCacheFile;
        final Map<String, String> blocksSnapshot = new LinkedHashMap<>(placedBlockCacheByScopePos);
        final Map<String, String[]> signsSnapshot = new LinkedHashMap<>(signLinesCache);
        final Map<String, Long> entrySignSnapshot = new LinkedHashMap<>(entryToSignPosByScopeEntry);
        codeCacheDirty = false;
        codeCacheSaveQueued.set(true);
        ioExecutor.execute(() -> {
            try
            {
                saveCodeCacheSnapshot(target, blocksSnapshot, signsSnapshot, entrySignSnapshot);
            }
            finally
            {
                codeCacheSaveQueued.set(false);
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

    @Override
    public String extractEntryText(ItemStack stack, int mode)
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
        if (mode == INPUT_MODE_APPLE)
        {
            String loc = getAppleLocName(stack);
            return toAmpersandCodes(loc);
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

    private void applyAppleTag(ItemStack stack, String displayText)
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
        display.setString("LocName", displayText == null ? "" : displayText);
        tag.setTag("display", display);
        stack.setTagCompound(tag);
    }

    private String getAppleLocName(ItemStack stack)
    {
        if (stack == null || stack.isEmpty())
        {
            return "";
        }
        if (stack.hasTagCompound())
        {
            NBTTagCompound tag = stack.getTagCompound();
            if (tag != null && tag.hasKey("display"))
            {
                NBTTagCompound display = tag.getCompoundTag("display");
                if (display.hasKey("LocName"))
                {
                    return display.getString("LocName");
                }
            }
        }
        return stack.getDisplayName();
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
        MenuCacheIO.save(target, snapshot, customSnapshot);
    }

    private void saveClickMenuSnapshot(File target, Map<String, List<ItemStack>> snapshot, Map<String, String> locSnapshot)
    {
        ClickMenuIO.save(target, snapshot, locSnapshot);
    }

    private void saveShulkerHoloSnapshot(File target, Map<String, ShulkerHolo> snapshot)
    {
        ShulkerHoloIO.save(target, snapshot);
    }

    private void saveCodeBlueGlassSnapshot(File target, Map<String, BlockPos> snapshot)
    {
        CodeBlueGlassIO.save(target, snapshot);
    }

    private void saveCodeCacheSnapshot(File target, Map<String, String> blocksByKey, Map<String, String[]> signsByKey,
        Map<String, Long> entryToSignByKey)
    {
        CodeCacheIO.save(target, blocksByKey, signsByKey, entryToSignByKey);
    }

    private void loadShulkerHolos()
    {
        shulkerHolos.clear();
        shulkerHolos.putAll(ShulkerHoloIO.load(shulkerHoloFile));
    }

    private void loadCodeBlueGlass()
    {
        codeBlueGlassById.clear();
        codeBlueGlassById.putAll(CodeBlueGlassIO.load(codeBlueGlassFile));
    }

    private void loadCodeCaches()
    {
        placedBlockCacheByScopePos.clear();
        entryToSignPosByScopeEntry.clear();

        // signLinesCache already stores scope-keyed sign texts; keep runtime updates and merge loaded snapshot.
        CodeCacheIO.Loaded loaded = CodeCacheIO.load(codeCacheFile);
        placedBlockCacheByScopePos.putAll(loaded.blocksByKey);
        entryToSignPosByScopeEntry.putAll(loaded.entryToSignByKey);
        signLinesCache.putAll(loaded.signsByKey);
    }

    private void loadMenuCache()
    {
        MenuCacheIO.Loaded loaded = MenuCacheIO.load(menuCacheFile);
        menuCache.clear();
        menuCache.putAll(loaded.menus);
        customMenuCache = loaded.custom;
    }

    private void loadClickMenu()
    {
        ClickMenuIO.Loaded loaded = ClickMenuIO.load(clickMenuFile);
        clickMenuMap.clear();
        clickMenuLocation.clear();
        clickMenuMap.putAll(loaded.menus);
        clickMenuLocation.putAll(loaded.locs);

        try
        {
            Minecraft mc = Minecraft.getMinecraft();
            if (mc != null && mc.mcDataDir != null)
            {
                File export = new File(mc.mcDataDir, "regallactions_export.txt");
                int added = importClickMenuFromRegAllExport(export);
                if (added > 0)
                {
                    debugChat("&aRegAll export -> clickMenuMap: +" + added);
                }
            }
        }
        catch (Exception ignore) { }
    }

    private static String parseExportDisplayName(String raw)
    {
        if (raw == null)
        {
            return "";
        }
        String s = raw;
        int rb = s.indexOf(']');
        if (rb >= 0)
        {
            s = s.substring(rb + 1);
        }
        int pipe = s.indexOf('|');
        if (pipe >= 0)
        {
            s = s.substring(0, pipe);
        }
        s = net.minecraft.util.text.TextFormatting.getTextWithoutFormattingCodes(s);
        return s == null ? "" : s.trim();
    }

    private static ItemStack parseExportItemStack(String raw)
    {
        if (raw == null || raw.isEmpty())
        {
            return ItemStack.EMPTY;
        }
        String s = raw.trim();
        ItemStack stack = ItemStack.EMPTY;
        try
        {
            if (s.startsWith("[") && s.contains(" meta="))
            {
                int end = s.indexOf(']');
                String head = end > 0 ? s.substring(1, end) : s.substring(1);
                String[] parts = head.split("\\s+meta=");
                String idStr = parts.length >= 1 ? parts[0].trim() : "";
                int meta = 0;
                if (parts.length >= 2)
                {
                    try
                    {
                        meta = Integer.parseInt(parts[1].trim());
                    }
                    catch (Exception ignore) { }
                }
                Item it = Item.getByNameOrId(idStr);
                if (it != null)
                {
                    stack = new ItemStack(it, 1, meta);
                }
            }
        }
        catch (Exception ignore) { }
        if (stack.isEmpty())
        {
            stack = new ItemStack(Items.PAPER);
        }
        String name = parseExportDisplayName(s);
        if (!name.isEmpty())
        {
            stack.setStackDisplayName(name);
        }
        return stack;
    }

    private int importClickMenuFromRegAllExport(File exportFile)
    {
        if (exportFile == null || !exportFile.exists())
        {
            return 0;
        }
        int added = 0;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(exportFile), "UTF-8")))
        {
            String path = "";
            String category = null;
            String subitem = null;
            String line;
            while ((line = reader.readLine()) != null)
            {
                if (line.startsWith("# record"))
                {
                    if (category != null && subitem != null)
                    {
                        added += importClickMenuFromRecord(path, category, subitem);
                    }
                    path = "";
                    category = null;
                    subitem = null;
                    continue;
                }
                if (line.startsWith("path="))
                {
                    path = line.substring("path=".length());
                }
                if (line.startsWith("category="))
                {
                    category = line.substring("category=".length());
                }
                else if (line.startsWith("subitem="))
                {
                    subitem = line.substring("subitem=".length());
                }
            }
            if (category != null && subitem != null)
            {
                added += importClickMenuFromRecord(path, category, subitem);
            }
        }
        catch (Exception ignore)
        {
            return 0;
        }
        if (added > 0)
        {
            clickMenuDirty = true;
        }
        return added;
    }

    private static String lastPathKey(String path)
    {
        if (path == null)
        {
            return null;
        }
        String p = path.trim();
        if (p.isEmpty())
        {
            return null;
        }
        int idx = p.lastIndexOf('>');
        String key = idx >= 0 ? p.substring(idx + 1) : p;
        key = key == null ? null : key.trim().toLowerCase(java.util.Locale.ROOT);
        return key == null || key.isEmpty() ? null : key;
    }

    private int importClickMenuFromRecord(String path, String categoryRaw, String subitemRaw)
    {
        ItemStack categoryStack = parseExportItemStack(categoryRaw);
        ItemStack subStack = parseExportItemStack(subitemRaw);
        if (categoryStack.isEmpty() || subStack.isEmpty())
        {
            return 0;
        }
        String categoryKey = getItemNameKey(categoryStack);
        if (categoryKey == null || categoryKey.isEmpty() || "empty".equals(categoryKey))
        {
            return 0;
        }
        int added = 0;

        // 1) Link: openerKey -> categoryStack (so we can navigate nested categories)
        String openerKey = lastPathKey(path);
        if (openerKey != null && !"empty".equals(openerKey))
        {
            added += addToClickMenuList(openerKey, categoryStack);
        }

        // 2) Link: categoryKey -> subStack (so we can find the action within the category)
        added += addToClickMenuList(categoryKey, subStack);
        return added;
    }

    private int addToClickMenuList(String key, ItemStack stack)
    {
        if (key == null || key.isEmpty() || stack == null || stack.isEmpty())
        {
            return 0;
        }
        List<ItemStack> list = clickMenuMap.get(key);
        if (list == null)
        {
            list = new ArrayList<>();
            clickMenuMap.put(key, list);
        }
        String want = getItemNameKey(stack);
        for (ItemStack it : list)
        {
            if (it == null || it.isEmpty())
            {
                continue;
            }
            if (want.equals(getItemNameKey(it)))
            {
                return 0;
            }
        }
        list.add(stack);
        return 1;
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
        int btnY0 = btnY1 - btnHeight - 2;
        net.minecraft.client.gui.Gui.drawRect(btnX, btnY0, btnX + btnWidth, btnY0 + btnHeight, 0x88000000);
        net.minecraft.client.gui.Gui.drawRect(btnX, btnY1, btnX + btnWidth, btnY1 + btnHeight, 0x88000000);
        net.minecraft.client.gui.Gui.drawRect(btnX, btnY2, btnX + btnWidth, btnY2 + btnHeight, 0x88000000);
        mc.fontRenderer.drawStringWithShadow("Export All", btnX + 4, btnY0 + 2, 0xFFFFFF);
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
                        : picked == INPUT_MODE_ARRAY ? "Array"
                        : picked == INPUT_MODE_LOCATION ? "Location"
                        : picked == INPUT_MODE_APPLE ? "GameValue" : "Text";
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
        int btnY0 = btnY1 - btnHeight - 2;
        if (mouseX >= btnX && mouseX <= btnX + btnWidth && mouseY >= btnY0 && mouseY <= btnY0 + btnHeight)
        {
            exportGuiToClipboard(gui, true, true);
            return true;
        }
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
        else if (mode == INPUT_MODE_APPLE)
        {
            preset = toAmpersandCodes(getAppleLocName(stack));
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
        if (entry.mode == INPUT_MODE_APPLE)
        {
            applyAppleTag(stack, display);
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
        if (stack.getItem() == Items.APPLE)
        {
            return INPUT_MODE_APPLE;
        }
        return -1;
    }

    @Override
    public ItemStack templateForMode(int mode)
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
        if (mode == INPUT_MODE_APPLE)
        {
            ItemStack cached = getCachedTemplateForItem(Items.APPLE);
            return cached.isEmpty() ? new ItemStack(Items.APPLE, 1) : cached;
        }
        if (mode == INPUT_MODE_ITEM)
        {
            return new ItemStack(Blocks.STONE, 1);
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
        if (entry.mode == INPUT_MODE_APPLE)
        {
            return applyColorCodes(entry.text);
        }
        return normalizeTextName(entry.text);
    }

    private String formatEntryLine(InputEntry entry)
    {
        char label = entry.mode == INPUT_MODE_NUMBER ? 'N'
            : entry.mode == INPUT_MODE_VARIABLE ? 'V'
            : entry.mode == INPUT_MODE_ARRAY ? 'A'
            : entry.mode == INPUT_MODE_LOCATION ? 'L'
            : entry.mode == INPUT_MODE_APPLE ? 'G' : 'T';
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
        y += 10;
        if (mouseY >= y && mouseY <= y + 10)
        {
            return INPUT_MODE_APPLE;
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

    @Override
    public void submitInputText(boolean giveExtra)
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
        else if (inputMode == INPUT_MODE_APPLE)
        {
            finalRaw = raw.trim();
            if (finalRaw.isEmpty())
            {
                setInputActive(false);
                return;
            }
            display = applyColorCodes(finalRaw);
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
            if (inputMode == INPUT_MODE_APPLE)
            {
                applyAppleTag(give, display);
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
        if (inputMode == INPUT_MODE_APPLE)
        {
            applyAppleTag(placed, display);
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

    private int findHotbarSlotForBlock(Minecraft mc, Block block)
    {
        if (mc == null || mc.player == null || block == null)
        {
            return -1;
        }
        Item target = Item.getItemFromBlock(block);
        if (target == null)
        {
            return -1;
        }
        for (int i = 0; i < 9; i++)
        {
            ItemStack stack = mc.player.inventory.getStackInSlot(i);
            if (!stack.isEmpty() && stack.getItem() == target)
            {
                return i;
            }
        }
        return -1;
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
            // Fallback: overwrite current hotbar slot (best-effort). Some flows rely on being able to "give" anyway.
            slot = mc == null || mc.player == null ? null : mc.player.inventory.currentItem;
            if (slot == null)
            {
                slot = 0;
            }
        }
        mc.player.inventory.setInventorySlotContents(slot, stack);
        sendCreativeSlotUpdate(mc, slot, stack);
    }

    private int giveItemToHotbarSlot(Minecraft mc, ItemStack stack)
    {
        Integer slot = findEmptyHotbarSlot(mc);
        if (slot == null)
        {
            // fallback: overwrite the currently held hotbar slot
            slot = mc == null || mc.player == null ? null : mc.player.inventory.currentItem;
        }
        if (slot == null)
        {
            slot = 0;
        }
        // If we are about to overwrite a hotbar slot, try to move the old item into inventory first.
        try
        {
            if (mc != null && mc.player != null && slot >= 0 && slot < 9)
            {
                ItemStack old = mc.player.inventory.getStackInSlot(slot);
                if (old != null && !old.isEmpty())
                {
                    Integer inv = findEmptyInventorySlot(mc);
                    if (inv != null)
                    {
                        mc.player.inventory.setInventorySlotContents(inv, old);
                        sendCreativeSlotUpdate(mc, inv, old);
                    }
                }
            }
        }
        catch (Exception ignore) { }
        mc.player.inventory.setInventorySlotContents(slot, stack);
        sendCreativeSlotUpdate(mc, slot, stack);
        return slot;
    }

    @Override
    public int giveQuickInputItemToHotbar(int mode, String raw, boolean saveVar)
    {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.player == null || mc.playerController == null)
        {
            return -1;
        }
        if (!mc.playerController.isInCreativeMode() || mc.playerController.getCurrentGameType() != GameType.CREATIVE)
        {
            return -1;
        }
        if (raw == null)
        {
            return -1;
        }
        String trimmed = raw.trim();
        if (trimmed.isEmpty())
        {
            return -1;
        }

        String finalRaw = trimmed;
        String display;
        if (mode == INPUT_MODE_NUMBER)
        {
            String number = extractNumber(trimmed);
            if (number.isEmpty())
            {
                return -1;
            }
            finalRaw = number;
            display = applyColorCodes("&c" + number);
        }
        else if (mode == INPUT_MODE_LOCATION)
        {
            display = finalRaw;
        }
        else if (mode == INPUT_MODE_VARIABLE || mode == INPUT_MODE_ARRAY)
        {
            finalRaw = normalizePlainName(trimmed);
            if (finalRaw.isEmpty())
            {
                return -1;
            }
            boolean isSaved = saveVar;
            if (mode == INPUT_MODE_VARIABLE)
            {
                isSaved = saveVar || savedVariableNames.contains(finalRaw);
            }
            display = applyColorCodes("&r" + finalRaw + ((mode == INPUT_MODE_ARRAY && isSaved) ? " \\u2398" : ""));
        }
        else if (mode == INPUT_MODE_APPLE)
        {
            display = applyColorCodes(finalRaw);
        }
        else
        {
            // text
            display = normalizeTextName(finalRaw);
        }

        ItemStack template = templateForMode(mode);
        ItemStack give = template == null || template.isEmpty() ? new ItemStack(Items.BOOK, 1) : template.copy();
        give.setCount(1);
        give.setStackDisplayName(display);
        if (mode == INPUT_MODE_VARIABLE || mode == INPUT_MODE_ARRAY)
        {
            boolean shouldSave = saveVar;
            if (mode == INPUT_MODE_VARIABLE)
            {
                shouldSave = saveVar || savedVariableNames.contains(finalRaw);
            }
            if (shouldSave)
            {
                applySavedVariableTag(give);
                if (saveVar && mode == INPUT_MODE_VARIABLE)
                {
                    savedVariableNames.add(finalRaw);
                }
            }
            else
            {
                removeSavedVariableTag(give);
            }
        }
        if (mode == INPUT_MODE_APPLE)
        {
            applyAppleTag(give, display);
        }

        return giveItemToHotbarSlot(mc, give);
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
        if (renderDisabledDueToError)
        {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.player == null || mc.world == null)
        {
            return;
        }
        try
        {
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
            renderCodeSelectionMarkers(mc);
            GlStateManager.popMatrix();
        }
        catch (Throwable t)
        {
            renderDisabledDueToError = true;
            logger.error("Render error, disabling chest-holo rendering", t);
            try
            {
                if (mc != null && mc.player != null)
                {
                    mc.player.sendMessage(new TextComponentString("[BetterCode] Rendering disabled due to error: " + t.getClass().getSimpleName()));
                }
            }
            catch (Throwable t2)
            {
                // ignore messaging failures
            }
        }
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

    private void renderCodeSelectionMarkers(Minecraft mc)
    {
        if (mc == null || mc.world == null || mc.player == null)
        {
            return;
        }
        int dim = mc.world.provider.getDimension();
        Set<BlockPos> set = codeSelectedGlassesByDim.get(dim);
        if (set == null || set.isEmpty())
        {
            return;
        }
        GlStateManager.disableTexture2D();
        GlStateManager.disableDepth();
        GlStateManager.disableLighting();
        GlStateManager.glLineWidth(2.0F);
        try
        {
            for (BlockPos glass : set)
            {
                if (glass == null || !mc.world.isBlockLoaded(glass))
                {
                    continue;
                }
                if (mc.player.getDistanceSq(glass) > 256.0 * 256.0)
                {
                    continue;
                }
                BlockPos start = glass.up();
                if (!mc.world.isBlockLoaded(start, false))
                {
                    continue;
                }
                AxisAlignedBB bb = new AxisAlignedBB(start).grow(0.002D);
                RenderGlobal.drawSelectionBoundingBox(bb, 0.20F, 0.85F, 1.0F, 0.55F);
            }
        }
        catch (Exception ignore)
        {
            // render is best-effort
        }
        finally
        {
            GlStateManager.enableLighting();
            GlStateManager.enableDepth();
            GlStateManager.enableTexture2D();
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

    // Pending menu detection state (delay to allow server to populate container)
    private boolean pendingMenuDetect = false;
    private long pendingMenuDetectStartMs = 0L;
    private int pendingMenuDetectWindowId = -1;

    // If rendering hits an unexpected error (NoClassDefFoundError / stack overflow), disable
    // the chest-holo rendering to avoid repeated GL errors and restore GUI responsiveness.
    private volatile boolean renderDisabledDueToError = false;

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
        clickMenuFile = new File(dir, "mcpythonapi_click_menu.dat");
        loadClickMenu();
        shulkerHoloFile = new File(dir, "mcpythonapi_shulker_holos.dat");
        loadShulkerHolos();
        codeBlueGlassFile = new File(dir, "mcpythonapi_code_glass.dat");
        loadCodeBlueGlass();
        codeCacheFile = new File(dir, "mcpythonapi_code_cache.dat");
        loadCodeCaches();
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

            autoCodeCacheEnabled = config.getBoolean("autoCodeCacheEnabled", "code", true,
                "CODE CACHE: Continuously scan loaded blue-glass rows and cache placed blocks/signs.\n"
                    + "This makes /mldsl skip-check work even after chunks unload (as long as you flew by at least once).");
            autoCodeCacheMaxSteps = config.getInt("autoCodeCacheMaxSteps", "code", 256, 32, 1024,
                "CODE CACHE: Max blocks to scan per row (x step = -2). Stops earlier on 2 empty slots.");

            autoCacheEnabled = config.getBoolean("autoCacheEnabled", "chest", false,
                "AUTO-CACHE CHESTS: Automatically open and cache nearby trapped chests in DEV mode.\n"
                    + "Only works in creative mode with special scoreboard. Check with /autocache command.\n"
                    + "Blocked while holding IRON_INGOT or GOLD_INGOT.");
            autoCacheRadius = config.getInt("autoCacheRadius", "chest", 6, 2, 16,
                "Radius (blocks) around player to scan for chests to auto-cache.");
            autoCacheTrappedOnly = config.getBoolean("autoCacheTrappedOnly", "chest", true,
                "If true: only TRAPPED_CHEST blocks. If false: TRAPPED_CHEST + CHEST blocks.");

            placeSpeedPercent = config.getInt("placeSpeedPercent", "place", 100, 10, 500,
                "PLACE/MLDSL: speed multiplier in percent.\n"
                    + "100 = default timings, 50 = slower (more stable), 200 = faster.");
            placeMaxPlaceAttempts = config.getInt("placeMaxPlaceAttempts", "place", 6, 1, 30,
                "PLACE/MLDSL: max retries for block placement if server cancels it.");
            placeBlockRetryDelayMs = config.getInt("placeBlockRetryDelayMs", "place", 1000, 50, 5000,
                "PLACE/MLDSL: delay (ms) between block placement retries.");
            placeParamsChestAutoOpenDelayMs = config.getInt("placeParamsChestAutoOpenDelayMs", "place", 1500, 200, 8000,
                "PLACE/MLDSL: if after selecting an action the params chest does not open automatically,\n"
                    + "close the menu and try to open it after this delay (ms).");
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


    // ----------------------------
    // Auto-cache trapped chests
    // ----------------------------

    @Override
    public boolean isHoldingIronOrGoldIngot(Minecraft mc)
    {
        if (mc == null || mc.player == null)
        {
            return false;
        }
        ItemStack main = mc.player.getHeldItemMainhand();
        ItemStack off = mc.player.getHeldItemOffhand();
        Item m = main.isEmpty() ? null : main.getItem();
        Item o = off.isEmpty() ? null : off.getItem();
        return m == Items.IRON_INGOT || m == Items.GOLD_INGOT
            || o == Items.IRON_INGOT || o == Items.GOLD_INGOT;
    }

    @Override
    public boolean isDevCreativeScoreboard(Minecraft mc)
    {
        if (mc == null || mc.playerController == null)
        {
            return false;
        }
        if (!mc.playerController.isInCreativeMode()
            || mc.playerController.getCurrentGameType() != GameType.CREATIVE)
        {
            return false;
        }
        String title = getScoreboardTitle();
        return "\u0420\u0415\u0414\u0410\u041a\u0422\u041e\u0420 \u0418\u0413\u0420\u042b".equals(title);
    }

    private boolean isChestCached(int dim, BlockPos pos)
    {
        if (pos == null)
        {
            return true;
        }
        String key = chestKey(dim, pos);
        return key != null && chestCaches.containsKey(key);
    }

    private int resolveChestPageSize(World world, BlockPos pos)
    {
        if (world != null && pos != null)
        {
            try
            {
                TileEntity te = world.getTileEntity(pos);
                if (te instanceof IInventory)
                {
                    int sz = ((IInventory) te).getSizeInventory();
                    if (sz > 0)
                    {
                        return sz;
                    }
                }
            }
            catch (Exception ignore) { }
        }
        return 27;
    }

    // True means this chest should be re-warmed for publish in cache mode:
    // if any cached page-end slot still has "next page" arrow, cache is likely paged/incomplete.
    private boolean shouldWarmupPagedChestForPublish(World world, int dim, BlockPos chestPos)
    {
        if (world == null || chestPos == null)
        {
            return false;
        }
        String key = chestKey(dim, chestPos);
        if (key == null)
        {
            return false;
        }
        ChestCache cached = chestCaches.get(key);
        if (cached == null || cached.items == null || cached.items.isEmpty())
        {
            return false;
        }
        int pageSize = Math.max(1, resolveChestPageSize(world, chestPos));
        for (int idx = pageSize - 1; idx < cached.items.size(); idx += pageSize)
        {
            ItemStack st = cached.items.get(idx);
            if (isNextPageArrow(st))
            {
                return true;
            }
        }
        return false;
    }

    private boolean isTargetChestBlock(World world, BlockPos pos)
    {
        if (world == null || pos == null)
        {
            return false;
        }
        Block b = world.getBlockState(pos).getBlock();
        if (autoCacheTrappedOnly)
        {
            return b == Blocks.TRAPPED_CHEST;
        }
        return b == Blocks.TRAPPED_CHEST || b == Blocks.CHEST;
    }

    private BlockPos findNearbyChestToCache(Minecraft mc, int radius)
    {
        if (mc == null || mc.world == null || mc.player == null)
        {
            return null;
        }
        BlockPos base = new BlockPos(mc.player.posX, mc.player.posY, mc.player.posZ);
        int dim = mc.world.provider.getDimension();

        int r = Math.max(2, Math.min(16, radius));
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;

        for (int dx = -r; dx <= r; dx++)
        {
            for (int dy = -2; dy <= 2; dy++)
            {
                for (int dz = -r; dz <= r; dz++)
                {
                    BlockPos p = base.add(dx, dy, dz);
                    if (!isTargetChestBlock(mc.world, p))
                    {
                        continue;
                    }
                    if (isChestCached(dim, p))
                    {
                        continue;
                    }
                    double d = mc.player.getDistanceSqToCenter(p);
                    if (d < bestDist)
                    {
                        bestDist = d;
                        best = p;
                    }
                }
            }
        }
        return best;
    }

    private void beginAutoCacheChest(Minecraft mc, BlockPos pos, long now)
    {
        if (mc == null || mc.world == null || mc.player == null
            || mc.playerController == null || pos == null)
        {
            return;
        }

        int dim = mc.world.provider.getDimension();
        autoCacheInProgress = true;
        autoCacheStartMs = now;
        autoCacheTargetPos = pos;
        autoCacheTargetDim = dim;

        lastClickedPos = pos;
        lastClickedDim = dim;
        lastClickedMs = now;
        lastClickedIsSign = false;
        lastClickedChest = true;
        allowChestSnapshot = true;
        allowChestUntilMs = now + 5000L;
        pendingChestSnapshot = true;
        pendingChestUntilMs = now + 5000L;
        lastClickedLabel = getChestLabel(mc.world, pos);

        mc.playerController.processRightClickBlock(
            mc.player, mc.world, pos,
            EnumFacing.UP, new Vec3d(0.5, 0.5, 0.5),
            EnumHand.MAIN_HAND
        );
        mc.player.swingArm(EnumHand.MAIN_HAND);
    }

    private void handleAutoChestCacheTick(Minecraft mc, long now)
    {
        if (!autoCacheEnabled || !editorModeActive || !isDevCreativeScoreboard(mc))
        {
            return;
        }
        if (mc == null || mc.world == null || mc.player == null)
        {
            return;
        }
        if (isHoldingIronOrGoldIngot(mc))
        {
            return;
        }
        if (mc.currentScreen != null)
        {
            // If a non-container GUI (chat/custom) is open, close it so placing can proceed.
            if (!(mc.currentScreen instanceof GuiContainer))
            {
                try
                {
                    mc.player.closeScreen();
                }
                catch (Exception ignore) { }
                return;
            }
            // If a container GUI is open, don't proceed.
            return;
        }
        if (autoCacheInProgress)
        {
            if (now - autoCacheStartMs > 2500L)
            {
                autoCacheInProgress = false;
                autoCacheTargetPos = null;
                autoCacheStartMs = 0L;
                nextAutoCacheScanMs = now + 500L;
            }
            return;
        }
        if (now < nextAutoCacheScanMs)
        {
            return;
        }
        nextAutoCacheScanMs = now + 700L;

        BlockPos target = findNearbyChestToCache(mc, autoCacheRadius);
        if (target != null)
        {
            beginAutoCacheChest(mc, target, now);
        }
    }

    private void cacheEntrySignPos(World world, BlockPos entryPos, BlockPos signPos)
    {
        if (world == null || entryPos == null || signPos == null)
        {
            return;
        }
        String scopeKey = getCodeGlassScopeKey(world);
        if (scopeKey == null)
        {
            return;
        }
        String k = scopeKey + ":" + entryPos.toLong();
        long v = signPos.toLong();
        Long prev = entryToSignPosByScopeEntry.get(k);
        if (prev == null || prev.longValue() != v)
        {
            entryToSignPosByScopeEntry.put(k, v);
            codeCacheDirty = true;
        }
    }

    private void handleAutoCodeCacheTick(Minecraft mc, long nowMs)
    {
        if (!autoCodeCacheEnabled || mc == null || mc.world == null || mc.player == null)
        {
            return;
        }
        if (!editorModeActive || !isDevCreativeScoreboard(mc))
        {
            return;
        }
        if (nowMs - lastCodeCacheScanMs < 1000L)
        {
            return;
        }
        lastCodeCacheScanMs = nowMs;

        BlockPos seed = resolveExportGlassPos(mc.world);
        if (seed == null || !mc.world.isBlockLoaded(seed))
        {
            return;
        }

        List<BlockPos> seeds = new ArrayList<>();
        seeds.add(seed);
        List<BlockPos> glasses = BlueGlassCodeMap.scan(mc.world, seeds);
        if (glasses == null || glasses.isEmpty())
        {
            return;
        }

        int maxSteps = Math.max(32, autoCodeCacheMaxSteps);
        for (BlockPos glassPos : glasses)
        {
            if (glassPos == null || !mc.world.isBlockLoaded(glassPos))
            {
                continue;
            }
            cacheRowFromBlueGlass(mc.world, glassPos, maxSteps);
        }
    }

    private void cacheRowFromBlueGlass(World world, BlockPos glassPos, int maxSteps)
    {
        if (world == null || glassPos == null)
        {
            return;
        }
        BlockPos start = glassPos.up();
        int emptyPairs = 0;

        for (int p = 0; p < maxSteps; p++)
        {
            BlockPos entryPos = start.add(-2 * p, 0, 0);
            BlockPos sidePos = entryPos.add(-1, 0, 0);

            if (!world.isBlockLoaded(entryPos, false) || !world.isBlockLoaded(sidePos, false))
            {
                break;
            }

            IBlockState entryState = world.getBlockState(entryPos);
            IBlockState sideState = world.getBlockState(sidePos);
            Block entryBlock = entryState == null ? Blocks.AIR : entryState.getBlock();
            Block sideBlock = sideState == null ? Blocks.AIR : sideState.getBlock();

            if (entryBlock != null && entryBlock != Blocks.AIR)
            {
                cachePlacedBlock(world, entryPos, entryBlock);
            }
            if (sideBlock != null && sideBlock != Blocks.AIR)
            {
                cachePlacedBlock(world, sidePos, sideBlock);
            }

            BlockPos signPos = findSignAtZMinus1(world, entryPos);
            if (signPos != null)
            {
                cacheEntrySignPos(world, entryPos, signPos);
                TileEntity te = world.getTileEntity(signPos);
                if (te instanceof TileEntitySign)
                {
                    cacheSignLines(world, (TileEntitySign) te);
                }
            }

            boolean emptySlot = (entryBlock == null || entryBlock == Blocks.AIR)
                && (sideBlock == null || sideBlock == Blocks.AIR)
                && signPos == null;

            if (emptySlot)
            {
                emptyPairs++;
                if (emptyPairs >= 2)
                {
                    break;
                }
            }
            else
            {
                emptyPairs = 0;
            }
        }
    }

    private void handleCacheAllTick(Minecraft mc, long now)
    {
        if (!cacheAllActive)
        {
            return;
        }
        if (mc == null || mc.world == null || mc.player == null)
        {
            cacheAllActive = false;
            cacheAllQueue.clear();
            cacheAllCurrentTarget = null;
            return;
        }
        if (!editorModeActive || !isDevCreativeScoreboard(mc))
        {
            return;
        }
        if (isHoldingIronOrGoldIngot(mc) || autoCacheInProgress || mc.currentScreen != null)
        {
            return;
        }
        if (!tpPathQueue.isEmpty())
        {
            return;
        }

        int dim = mc.world.provider.getDimension();

        if (cacheAllCurrentTarget == null)
        {
            while (!cacheAllQueue.isEmpty())
            {
                BlockPos p = cacheAllQueue.poll();
                if (p == null || !isTargetChestBlock(mc.world, p) || isChestCached(dim, p))
                {
                    continue;
                }
                cacheAllCurrentTarget = p;
                break;
            }
            if (cacheAllCurrentTarget == null)
            {
                cacheAllActive = false;
                setActionBar(true, "&aCacheAll done", 2000L);
                return;
            }
            buildTpPathQueue(
                mc.world,
                mc.player.posX, mc.player.posY, mc.player.posZ,
                cacheAllCurrentTarget.getX() + 0.5,
                cacheAllCurrentTarget.getY() + 0.5,
                cacheAllCurrentTarget.getZ() + 0.5
            );
            return;
        }

        if (mc.player.getDistanceSqToCenter(cacheAllCurrentTarget) <= 9.0)
        {
            beginAutoCacheChest(mc, cacheAllCurrentTarget, now);
            cacheAllCurrentTarget = null;
        }
        else
        {
            buildTpPathQueue(
                mc.world,
                mc.player.posX, mc.player.posY, mc.player.posZ,
                cacheAllCurrentTarget.getX() + 0.5,
                cacheAllCurrentTarget.getY() + 0.5,
                cacheAllCurrentTarget.getZ() + 0.5
            );
        }
    }

    private void handleModulePublishWarmupTick(Minecraft mc, long now)
    {
        if (!modulePublishWarmupActive)
        {
            return;
        }
        if (mc == null || mc.world == null || mc.player == null)
        {
            modulePublishWarmupActive = false;
            modulePublishWarmupQueue.clear();
            modulePublishWarmupAllChests.clear();
            modulePublishWarmupPass = 0;
            modulePublishWarmupCurrent = null;
            modulePublishWarmupDir = null;
            modulePublishWarmupName = null;
            return;
        }
        if (mc.world.provider.getDimension() != modulePublishWarmupDim)
        {
            return;
        }
        if (!editorModeActive || !isDevCreativeScoreboard(mc))
        {
            return;
        }
        if (isHoldingIronOrGoldIngot(mc) || autoCacheInProgress || mc.currentScreen != null)
        {
            return;
        }
        if (!tpPathQueue.isEmpty())
        {
            return;
        }
        // Hard timeout safety.
        if (now - modulePublishWarmupStartMs > 120000L)
        {
            modulePublishWarmupActive = false;
            modulePublishWarmupQueue.clear();
            modulePublishWarmupAllChests.clear();
            modulePublishWarmupPass = 0;
            modulePublishWarmupCurrent = null;
            setActionBar(false, "&c/module publish: nocache warmup timeout", 3500L);
            return;
        }

        if (modulePublishWarmupCurrent == null)
        {
            while (!modulePublishWarmupQueue.isEmpty())
            {
                BlockPos p = modulePublishWarmupQueue.poll();
                if (p == null || !isTargetChestBlock(mc.world, p))
                {
                    continue;
                }
                modulePublishWarmupCurrent = p;
                break;
            }
            if (modulePublishWarmupCurrent == null)
            {
                if (modulePublishWarmupPass == 0 && !modulePublishWarmupAllChests.isEmpty())
                {
                    LinkedHashSet<BlockPos> retry = new LinkedHashSet<>();
                    for (BlockPos p : modulePublishWarmupAllChests)
                    {
                        if (p == null || !isTargetChestBlock(mc.world, p))
                        {
                            continue;
                        }
                        if (shouldWarmupPagedChestForPublish(mc.world, modulePublishWarmupDim, p))
                        {
                            retry.add(p);
                        }
                    }
                    if (!retry.isEmpty())
                    {
                        modulePublishWarmupQueue.addAll(retry);
                        modulePublishWarmupPass = 1;
                        if (mc.player != null)
                        {
                            mc.player.sendMessage(new TextComponentString(TextFormatting.YELLOW
                                + "/module publish: дополнительный прогрев страниц: " + retry.size()));
                        }
                        setActionBar(true, "&e/module publish: доп.прогрев страниц " + retry.size(), 3000L);
                        return;
                    }
                }
                // Warmup completed: export+convert using freshly built caches.
                File dir = modulePublishWarmupDir;
                String name = modulePublishWarmupName;
                modulePublishWarmupActive = false;
                modulePublishWarmupAllChests.clear();
                modulePublishWarmupPass = 0;
                modulePublishWarmupDir = null;
                modulePublishWarmupName = null;
                runModulePublishCommandWithDir(name, false, dir);
                return;
            }
            // Nocache warmup movement rule:
            // TP/path goes to -2Z from the action entry block (entry = chest.down()).
            BlockPos entryPos = modulePublishWarmupCurrent.down();
            double targetX = entryPos.getX() + 0.5;
            double targetY = entryPos.getY() + 0.5;
            double targetZ = entryPos.getZ() - 2 + 0.5;
            buildTpPathQueue(
                mc.world,
                mc.player.posX, mc.player.posY, mc.player.posZ,
                targetX,
                targetY,
                targetZ
            );
            return;
        }

        if (mc.player.getDistanceSqToCenter(modulePublishWarmupCurrent) <= 9.0)
        {
            beginAutoCacheChest(mc, modulePublishWarmupCurrent, now);
            modulePublishWarmupCurrent = null;
        }
        else
        {
            BlockPos entryPos = modulePublishWarmupCurrent.down();
            double targetX = entryPos.getX() + 0.5;
            double targetY = entryPos.getY() + 0.5;
            double targetZ = entryPos.getZ() - 2 + 0.5;
            buildTpPathQueue(
                mc.world,
                mc.player.posX, mc.player.posY, mc.player.posZ,
                targetX,
                targetY,
                targetZ
            );
        }
    }

    private void handlePlaceBlocksTick(Minecraft mc, long now)
    {
        if (!placeBlocksActive)
        {
            return;
        }
        if (mc == null || mc.world == null || mc.player == null)
        {
            placeBlocksActive = false;
            placeBlocksQueue.clear();
            placeBlocksCurrent = null;
            return;
        }
        if (!editorModeActive || !isDevCreativeScoreboard(mc))
        {
            return;
        }
        if (isHoldingIronOrGoldIngot(mc))
        {
            return;
        }
        if (mc.currentScreen != null)
        {
            // If a non-container GUI is open (chat, custom screens), close it so placing can proceed.
            if (!(mc.currentScreen instanceof GuiContainer))
            {
                try
                {
                    mc.displayGuiScreen(null);
                    if (debugUi && mc.player != null)
                    {
                        mc.player.sendMessage(new TextComponentString("/place: closed non-container GUI to continue"));
                    }
                }
                catch (Exception ignore) { }
            }
            // In either case (container or non-container), wait for next tick to proceed.
            return;
        }
        if (!tpPathQueue.isEmpty())
        {
            return;
        }

        if (placeBlocksCurrent != null && placeBlocksCurrent.placedBlock)
        {
            // /placeadvanced: after choosing the menu item, some servers spawn a (trap) chest above the block
            // and require clicking it to open a second GUI with parameters.
            if (placeBlocksCurrent.awaitingParamsChest)
            {
                if (placeBlocksCurrent.advancedArgs == null || placeBlocksCurrent.advancedArgs.isEmpty())
                {
                    placeBlocksCurrent.awaitingParamsChest = false;
                    placeBlocksCurrent = null;
                    return;
                }

                if (placeBlocksCurrent.paramsStartMs <= 0L)
                {
                    placeBlocksCurrent.paramsStartMs = now;
                }
                if (now - placeBlocksCurrent.paramsStartMs > 15000L)
                {
                    setActionBar(false, "&c/placeadvanced: params chest timeout", 2500L);
                    placeBlocksCurrent = null;
                    return;
                }
                if (placeBlocksCurrent.paramsOpenAttempts > 80)
                {
                    setActionBar(false, "&c/placeadvanced: couldn't open params chest", 2500L);
                    placeBlocksCurrent = null;
                    return;
                }
                if (now < placeBlocksCurrent.nextParamsActionMs)
                {
                    return;
                }
                placeBlocksCurrent.nextParamsActionMs = now + 250L;

                try
                {
                    BlockPos base = placeBlocksCurrent.pos;
                    if (base == null)
                    {
                        placeBlocksCurrent = null;
                        return;
                    }

                    BlockPos[] candidates = new BlockPos[]{
                        base.up(),
                        base.up().south(),
                        base.up().north(),
                        base.up().east(),
                        base.up().west()
                    };

                    BlockPos chestPos = null;
                    for (BlockPos p : candidates)
                    {
                        if (p == null)
                        {
                            continue;
                        }
                        Block b = mc.world.getBlockState(p).getBlock();
                        if (b == Blocks.TRAPPED_CHEST || b == Blocks.CHEST)
                        {
                            chestPos = p;
                            break;
                        }
                    }

                    if (chestPos == null)
                    {
                        placeBlocksCurrent.paramsOpenAttempts++;
                        return;
                    }

                    if (mc.player.getDistanceSqToCenter(chestPos) > 25.0)
                    {
                        placeBlocksCurrent.paramsOpenAttempts++;
                        return;
                    }

                    if (debugUi && mc.player != null)
                    {
                        mc.player.sendMessage(new TextComponentString(
                            "/placeadvanced: opening params chest attempt=" + (placeBlocksCurrent.paramsOpenAttempts + 1)));
                    }

                    placeBlocksCurrent.paramsOpenAttempts++;
                    placeBlocksCurrent.lastMenuClickMs = now;
                    mc.playerController.processRightClickBlock(
                        mc.player, mc.world, chestPos, EnumFacing.UP, new Vec3d(0.5, 0.5, 0.5), EnumHand.MAIN_HAND
                    );
                    mc.player.swingArm(EnumHand.MAIN_HAND);
                }
                catch (Exception ignore)
                {
                    placeBlocksCurrent.paramsOpenAttempts++;
                }
                return;
            }

            // If we are waiting for menu but no GUI is open (plugin closed it, lag, etc),
            // try to re-open the sign menu. This also enables the random fallback search.
            if (placeBlocksCurrent.awaitingMenu)
            {
                long since = now - placeBlocksCurrent.menuStartMs;
                // Give the server a short grace period to actually open the GUI.
                if (since < 700L)
                {
                    return;
                }
                // If menu didn't open for a while, try reopening the sign menu.
                if (since > 10000L)
                {
                    placeBlocksCurrent.needOpenMenu = true;
                }
                else
                {
                    placeBlocksCurrent.needOpenMenu = true;
                }
            }

            if (placeBlocksCurrent.needOpenMenu)
            {
                if (placeBlocksCurrent.menuOpenAttempts > 40)
                {
                    setActionBar(false, "&c/place: unable to open menu (too many attempts)", 2500L);
                    placeBlocksCurrent = null;
                    return;
                }
                if (now < placeBlocksCurrent.nextMenuActionMs)
                {
                    return;
                }
                placeBlocksCurrent.nextMenuActionMs = now + 450L;

                try
                {
                    BlockPos signPos = findSignAtZMinus1(mc.world, placeBlocksCurrent.pos);
                    if (signPos != null && mc.player.getDistanceSqToCenter(signPos) <= 16.0)
                    {
                        if (debugUi && mc.player != null)
                        {
                            mc.player.sendMessage(new TextComponentString(
                                "/place: reopening menu attempt=" + (placeBlocksCurrent.menuOpenAttempts + 1)));
                        }
                        mc.playerController.processRightClickBlock(
                            mc.player, mc.world, signPos, EnumFacing.UP, new Vec3d(0.5, 0.5, 0.5), EnumHand.MAIN_HAND
                        );
                        mc.player.swingArm(EnumHand.MAIN_HAND);
                        placeBlocksCurrent.menuStartMs = now;
                        placeBlocksCurrent.awaitingMenu = true;
                        placeBlocksCurrent.needOpenMenu = false;
                        placeBlocksCurrent.menuOpenAttempts++;
                        placeBlocksCurrent.menuClicksSinceOpen = 0;
                        placeBlocksCurrent.triedSlots.clear();
                        placeBlocksCurrent.triedWindowId = -1;
                    }
                }
                catch (Exception ignore) { }
            }

            return;
        }

        if (placeBlocksCurrent == null)
        {
            while (!placeBlocksQueue.isEmpty())
            {
                PlaceEntry e = placeBlocksQueue.poll();
                if (e == null)
                {
                    continue;
                }
                placeBlocksCurrent = e;
                break;
            }
            if (placeBlocksCurrent == null)
            {
                placeBlocksActive = false;
                setActionBar(true, "&aPlace done", 2000L);
                return;
            }
            // "air" entry: just a delay/cooldown step between actions (no placement, no GUI, no movement).
            if (placeBlocksCurrent.block == Blocks.AIR
                && (placeBlocksCurrent.searchKey == null || placeBlocksCurrent.searchKey.isEmpty())
                && (placeBlocksCurrent.advancedArgs == null || placeBlocksCurrent.advancedArgs.isEmpty()))
            {
                placeBlocksCurrent.menuStartMs = now;
                return;
            }
            // Approach position: -2 on Z from target, Y same as target
            double ax = placeBlocksCurrent.pos.getX() + 0.5;
            double ay = placeBlocksCurrent.pos.getY();
            double az = placeBlocksCurrent.pos.getZ() - 2.0 + 0.5;
            buildTpPathQueue(mc.world, mc.player.posX, mc.player.posY, mc.player.posZ, ax, ay, az);
            return;
        }

        // "air" entry: wait then skip.
        if (placeBlocksCurrent.block == Blocks.AIR
            && (placeBlocksCurrent.searchKey == null || placeBlocksCurrent.searchKey.isEmpty())
            && (placeBlocksCurrent.advancedArgs == null || placeBlocksCurrent.advancedArgs.isEmpty()))
        {
            if (placeBlocksCurrent.menuStartMs <= 0L)
            {
                placeBlocksCurrent.menuStartMs = now;
            }
            if (now - placeBlocksCurrent.menuStartMs < 450L)
            {
                return;
            }
            placeBlocksCurrent = null;
            return;
        }

        if (mc.player.getDistanceSqToCenter(placeBlocksCurrent.pos) <= 9.0)
        {
            // Place block: set held item and right-click on block below target
            try
            {
                Block b = placeBlocksCurrent.block;
                if (b == null)
                {
                    setActionBar(false, "&cBlock not found", 2000L);
                    abortPlaceBlocks("block_not_found");
                    return;
                }
                else
                {
                    // Prefer existing hotbar item
                    Item item = Item.getItemFromBlock(b);
                    int foundHotbar = -1;
                    for (int h = 0; h < 9; h++)
                    {
                        ItemStack s = mc.player.inventory.getStackInSlot(h);
                        if (!s.isEmpty() && s.getItem() == item)
                        {
                            foundHotbar = h;
                            break;
                        }
                    }

                    if (foundHotbar == -1)
                    {
                        // Try to find in main inventory
                        int invIndex = -1;
                        int size = mc.player.inventory.getSizeInventory();
                        for (int i = 9; i < size; i++)
                        {
                            ItemStack s = mc.player.inventory.getStackInSlot(i);
                            if (!s.isEmpty() && s.getItem() == item)
                            {
                                invIndex = i;
                                break;
                            }
                        }
                        if (invIndex != -1)
                        {
                            // Move from inventory slot to empty hotbar slot (creative-safe)
                            Integer empty = findEmptyHotbarSlot(mc);
                            if (empty == null)
                            {
                                empty = mc.player.inventory.currentItem;
                            }
                            ItemStack toMove = mc.player.inventory.getStackInSlot(invIndex);
                            mc.player.inventory.setInventorySlotContents(empty, toMove);
                            sendCreativeSlotUpdate(mc, empty, toMove);
                            foundHotbar = empty;
                        }
                        else
                        {
                            // Not present: create via creative give into hotbar
                            giveItemToHotbar(mc, new ItemStack(b));
                            // search again
                            for (int h = 0; h < 9; h++)
                            {
                                ItemStack s = mc.player.inventory.getStackInSlot(h);
                                if (!s.isEmpty() && s.getItem() == item)
                                {
                                    foundHotbar = h;
                                    break;
                                }
                            }
                            if (debugUi && mc.player != null)
                            {
                                mc.player.sendMessage(new TextComponentString("/place: after give attempt, foundHotbar=" + foundHotbar));
                            }
                        }
                    }

                    if (foundHotbar != -1)
                    {
                        if (debugUi && mc.player != null)
                        {
                            mc.player.sendMessage(new TextComponentString("/place: using hotbar slot=" + foundHotbar));
                        }
                        mc.player.inventory.currentItem = foundHotbar;
                        mc.playerController.updateController();
                        if (mc.player != null && mc.player.connection != null)
                        {
                            mc.player.connection.sendPacket(new CPacketHeldItemChange(foundHotbar));
                        }
                        try
                        {
                            Vec3d eyes = mc.player.getPositionEyes(1.0F);
                            BlockPos target = placeBlocksCurrent.pos.down();
                            Vec3d center = new Vec3d(target.getX() + 0.5, target.getY() + 0.5, target.getZ() + 0.5);
                            double dx = center.x - eyes.x;
                            double dy = center.y - eyes.y;
                            double dz = center.z - eyes.z;
                            double dist = Math.sqrt(dx * dx + dz * dz);
                            if (dist > 0.0001)
                            {
                                float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
                                float pitch = (float) (-Math.toDegrees(Math.atan2(dy, dist)));
                                mc.player.connection.sendPacket(new CPacketPlayer.Rotation(yaw, pitch, mc.player.onGround));
                            }
                        }
                        catch (Exception ignore) { }
                        mc.playerController.processRightClickBlock(
                            mc.player, mc.world, placeBlocksCurrent.pos.down(), EnumFacing.UP,
                            new Vec3d(0.5, 0.5, 0.5), EnumHand.MAIN_HAND
                        );
                        mc.player.swingArm(EnumHand.MAIN_HAND);
                    }
                    else
                    {
                        setActionBar(false, "&cCould not put block into hotbar", 2000L);
                        abortPlaceBlocks("no_hotbar_slot");
                        return;
                    }
                }
            }
            catch (Exception ex)
            {
                setActionBar(false, "&cPlace failed: " + ex.getMessage(), 2000L);
                abortPlaceBlocks("place_exception");
                return;
            }
            // After placing block, open the action menu (usually via sign at z-1).
            try
            {
                BlockPos placedPos = placeBlocksCurrent.pos;
                BlockPos signPos = placedPos == null ? null : findSignAtZMinus1(mc.world, placedPos);
                if (signPos != null)
                {
                    mc.playerController.processRightClickBlock(
                        mc.player, mc.world, signPos, EnumFacing.UP, new Vec3d(0.5, 0.5, 0.5), EnumHand.MAIN_HAND
                    );
                    mc.player.swingArm(EnumHand.MAIN_HAND);
                }
                else if (placedPos != null)
                {
                    mc.playerController.processRightClickBlock(
                        mc.player, mc.world, placedPos, EnumFacing.UP, new Vec3d(0.5, 0.5, 0.5), EnumHand.MAIN_HAND
                    );
                    mc.player.swingArm(EnumHand.MAIN_HAND);
                }

                placeBlocksCurrent.placedBlock = true;
                placeBlocksCurrent.awaitingMenu = true;
                placeBlocksCurrent.awaitingParamsChest = false;
                placeBlocksCurrent.menuStartMs = System.currentTimeMillis();
                placeBlocksCurrent.needOpenMenu = false;
                placeBlocksCurrent.menuOpenAttempts = 0;
                placeBlocksCurrent.nextMenuActionMs = System.currentTimeMillis() + 250L;
                placeBlocksCurrent.menuClicksSinceOpen = 0;
                placeBlocksCurrent.triedSlots.clear();
                placeBlocksCurrent.triedWindowId = -1;
            }
            catch (Exception ex2)
            {
                // ignore sign-click failures
            }
            return;
        }
        else
        {
            double ax = placeBlocksCurrent.pos.getX() + 0.5;
            double ay = placeBlocksCurrent.pos.getY();
            double az = placeBlocksCurrent.pos.getZ() - 2.0 + 0.5;
            buildTpPathQueue(mc.world, mc.player.posX, mc.player.posY, mc.player.posZ, ax, ay, az);
        }
    }

    private void handlePlaceMenuNavigation(GuiContainer gui, long nowMs)
    {
        if (!placeBlocksActive || placeBlocksCurrent == null)
        {
            return;
        }
        if (placeBlocksCurrent.awaitingParamsChest)
        {
            // Wait for a new container to open (should be the params GUI), then start filling args.
            int windowId = gui.inventorySlots == null ? -1 : gui.inventorySlots.windowId;
            if (windowId != -1
                && placeBlocksCurrent.lastMenuWindowId != -1
                && windowId != placeBlocksCurrent.lastMenuWindowId
                && nowMs - placeBlocksCurrent.lastMenuClickMs > 450L)
            {
                placeBlocksCurrent.awaitingParamsChest = false;
                placeBlocksCurrent.awaitingArgs = true;
                placeBlocksCurrent.advancedArgIndex = 0;
                placeBlocksCurrent.argsStartMs = nowMs;
                placeBlocksCurrent.lastArgsActionMs = 0L;
                placeBlocksCurrent.argsMisses = 0;
                placeBlocksCurrent.usedArgSlots.clear();
                handlePlaceAdvancedArgs(gui, nowMs);
            }
            return;
        }
        if (placeBlocksCurrent.awaitingArgs)
        {
            handlePlaceAdvancedArgs(gui, nowMs);
            return;
        }
        if (!placeBlocksCurrent.awaitingMenu)
        {
            return;
        }
        // If the menu is actually open, stop any pending reopen attempts.
        placeBlocksCurrent.needOpenMenu = false;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.player == null || mc.player.openContainer == null)
        {
            return;
        }
        // Wait for the server/client to clear the carried stack after previous interactions.
        // With ping, clicking again while carrying something often causes mis-clicks or skipped items.
        try
        {
            ItemStack carried = mc.player.inventory.getItemStack();
            if (carried != null && !carried.isEmpty())
            {
                return;
            }
        }
        catch (Exception ignore) { }
        // Wait a bit after opening/reopening so the server has time to populate items.
        if (nowMs - placeBlocksCurrent.menuStartMs < 300L)
        {
            return;
        }
        // If the container is still empty (not yet populated), don't start random clicking.
        int nonEmpty = 0;
        for (Slot slot : gui.inventorySlots.inventorySlots)
        {
            if (slot == null || slot.inventory == mc.player.inventory)
            {
                continue;
            }
            ItemStack st = slot.getStack();
            if (st != null && !st.isEmpty())
            {
                nonEmpty++;
                break;
            }
        }
        if (nonEmpty == 0)
        {
            return;
        }
        if (placeBlocksCurrent.searchKey == null || placeBlocksCurrent.searchKey.trim().isEmpty())
        {
            placeBlocksCurrent.awaitingMenu = false;
            placeBlocksCurrent = null;
            return;
        }
        if (nowMs - placeBlocksCurrent.menuStartMs > 10000L)
        {
            setActionBar(false, "&c/place: menu timeout", 2000L);
            abortPlaceBlocks("menu_timeout");
            return;
        }
        int windowId = mc.player.openContainer.windowId;
        // Slot numbers are not stable across different GUI windows; reset per-window slot tracking.
        if (placeBlocksCurrent.triedWindowId != windowId)
        {
            placeBlocksCurrent.triedSlots.clear();
            placeBlocksCurrent.triedWindowId = windowId;
            placeBlocksCurrent.menuClicksSinceOpen = 0;
        }
        if (placeBlocksCurrent.lastMenuWindowId == windowId && nowMs - placeBlocksCurrent.lastMenuClickMs < 300L)
        {
            return;
        }
        MenuStep step = findMenuStep(gui, placeBlocksCurrent.searchKey);
        if (step == null)
        {
            // Random exploration fallback: click unseen items until the target appears.
            if (placeBlocksCurrent.randomClicks > 250)
            {
                setActionBar(false, "&c/place: random search exhausted", 2500L);
                abortPlaceBlocks("random_exhausted");
                return;
            }
            if (nowMs < placeBlocksCurrent.nextMenuActionMs)
            {
                return;
            }
            placeBlocksCurrent.nextMenuActionMs = nowMs + 220L;

            MenuStep rnd = findRandomMenuStep(gui, placeBlocksCurrent);
            if (rnd == null)
            {
                // No more items to try in this menu.
                // If we didn't click anything since opening this window, it's likely a true dead-end.
                // Otherwise, we probably exhausted a sub-menu: close and re-open the root menu to continue searching.
                if (placeBlocksCurrent.menuClicksSinceOpen == 0)
                {
                    if (debugUi && mc.player != null)
                    {
                        mc.player.sendMessage(new TextComponentString("/place: random: no candidates in menu, aborting"));
                    }
                    setActionBar(false, "&c/place: no path found in GUI", 2000L);
                    abortPlaceBlocks("no_path_gui");
                    return;
                }

                if (debugUi && mc.player != null)
                {
                    mc.player.sendMessage(new TextComponentString("/place: random: exhausted sub-menu, reopening root"));
                }
                try
                {
                    mc.displayGuiScreen(null);
                }
                catch (Exception ignore) { }
                placeBlocksCurrent.needOpenMenu = true;
                placeBlocksCurrent.awaitingMenu = true;
                placeBlocksCurrent.menuStartMs = nowMs;
                placeBlocksCurrent.nextMenuActionMs = nowMs + 450L;
                placeBlocksCurrent.menuClicksSinceOpen = 0;
                placeBlocksCurrent.triedSlots.clear();
                placeBlocksCurrent.triedWindowId = -1;
                return;
            }
            if (debugUi && mc.player != null)
            {
                mc.player.sendMessage(new TextComponentString("/place: random click slot=" + rnd.slotNumber));
            }
            try
            {
                Slot s = gui.inventorySlots.getSlot(rnd.slotNumber);
                if (s != null)
                {
                    ItemStack st = s.getStack();
                    if (st != null && !st.isEmpty())
                    {
                        lastClickedSlotStack = st.copy();
                        lastClickedSlotNumber = rnd.slotNumber;
                        lastClickedSlotMs = System.currentTimeMillis();
                        lastClickedGuiClass = gui.getClass().getSimpleName();
                        lastClickedGuiTitle = (gui instanceof GuiChest) ? getGuiTitle((GuiChest) gui) : "";
                    }
                }
            }
            catch (Exception ignore) { }
            queuedClicks.add(new ClickAction(rnd.slotNumber, 0, ClickType.PICKUP));
            // Mark as tried only after we actually queued the click.
            placeBlocksCurrent.triedSlots.add(rnd.slotNumber);
            try
            {
                Slot s = gui.inventorySlots.getSlot(rnd.slotNumber);
                if (s != null)
                {
                    ItemStack st = s.getStack();
                    if (st != null && !st.isEmpty())
                    {
                        String k = normalizeForMatch(getItemNameKey(st));
                        if (!k.isEmpty())
                        {
                            placeBlocksCurrent.triedItemKeys.add(k);
                        }
                    }
                }
            }
            catch (Exception ignore) { }
            placeBlocksCurrent.lastMenuClickMs = nowMs;
            placeBlocksCurrent.lastMenuWindowId = windowId;
            placeBlocksCurrent.randomClicks++;
            placeBlocksCurrent.menuClicksSinceOpen++;
            placeBlocksCurrent.menuStartMs = nowMs;
            return;
        }
        try
        {
            Slot s = gui.inventorySlots.getSlot(step.slotNumber);
            if (s != null)
            {
                ItemStack st = s.getStack();
                if (st != null && !st.isEmpty())
                {
                    lastClickedSlotStack = st.copy();
                    lastClickedSlotNumber = step.slotNumber;
                    lastClickedSlotMs = System.currentTimeMillis();
                    lastClickedGuiClass = gui.getClass().getSimpleName();
                    lastClickedGuiTitle = (gui instanceof GuiChest) ? getGuiTitle((GuiChest) gui) : "";
                }
            }
        }
        catch (Exception ignore) { }
        queuedClicks.add(new ClickAction(step.slotNumber, 0, ClickType.PICKUP));
        placeBlocksCurrent.triedSlots.add(step.slotNumber);
        try
        {
            Slot s = gui.inventorySlots.getSlot(step.slotNumber);
            if (s != null)
            {
                ItemStack st = s.getStack();
                if (st != null && !st.isEmpty())
                {
                    String k = normalizeForMatch(getItemNameKey(st));
                    if (!k.isEmpty())
                    {
                        placeBlocksCurrent.triedItemKeys.add(k);
                    }
                }
            }
        }
        catch (Exception ignore) { }
        placeBlocksCurrent.menuClicksSinceOpen++;
        placeBlocksCurrent.lastMenuClickMs = nowMs;
        placeBlocksCurrent.lastMenuWindowId = windowId;
        if (step.directHit)
        {
            if (placeBlocksCurrent.advancedArgs != null && !placeBlocksCurrent.advancedArgs.isEmpty())
            {
                placeBlocksCurrent.awaitingMenu = false;
                placeBlocksCurrent.awaitingArgs = false;
                placeBlocksCurrent.awaitingParamsChest = true;
                placeBlocksCurrent.advancedArgIndex = 0;
                placeBlocksCurrent.argsStartMs = 0L;
                placeBlocksCurrent.lastArgsActionMs = 0L;
                placeBlocksCurrent.argsMisses = 0;
                placeBlocksCurrent.usedArgSlots.clear();
                placeBlocksCurrent.paramsStartMs = nowMs;
                placeBlocksCurrent.nextParamsActionMs = nowMs + 250L;
                placeBlocksCurrent.paramsOpenAttempts = 0;
            }
            else
            {
                placeBlocksCurrent.awaitingMenu = false;
                placeBlocksCurrent = null;
            }
        }
        else
        {
            placeBlocksCurrent.menuStartMs = nowMs;
        }
    }

    private void handlePlaceAdvancedArgs(GuiContainer gui, long nowMs)
    {
        if (!placeBlocksActive || placeBlocksCurrent == null || !placeBlocksCurrent.awaitingArgs)
        {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.player == null)
        {
            return;
        }
        if (placeBlocksCurrent.advancedArgs == null || placeBlocksCurrent.advancedArgs.isEmpty())
        {
            placeBlocksCurrent.awaitingArgs = false;
            placeBlocksCurrent = null;
            return;
        }
        if (placeBlocksCurrent.advancedArgIndex >= placeBlocksCurrent.advancedArgs.size())
        {
            placeBlocksCurrent.awaitingArgs = false;
            try
            {
                mc.displayGuiScreen(null);
            }
            catch (Exception ignore) { }
            placeBlocksCurrent = null;
            return;
        }
        if (nowMs - placeBlocksCurrent.argsStartMs > 12000L)
        {
            setActionBar(false, "&c/placeadvanced: args timeout", 2500L);
            abortPlaceBlocks("args_timeout");
            return;
        }
        if (inputActive)
        {
            return;
        }
        if (placeBlocksCurrent.lastArgsActionMs > 0 && nowMs - placeBlocksCurrent.lastArgsActionMs < 180L)
        {
            return;
        }

        PlaceArg arg = placeBlocksCurrent.advancedArgs.get(placeBlocksCurrent.advancedArgIndex);
        Slot target = findArgTargetSlot(gui, arg, placeBlocksCurrent.usedArgSlots);
        if (target == null)
        {
            placeBlocksCurrent.argsMisses++;
            if (placeBlocksCurrent.argsMisses > 60)
            {
                if (debugUi && mc.player != null)
                {
                    mc.player.sendMessage(new TextComponentString(
                        "/placeadvanced: no matching glass for '" + arg.keyRaw + "' (giving up)"));
                }
                setActionBar(false, "&c/placeadvanced: no matching args slot", 2500L);
                abortPlaceBlocks("no_args_slot");
            }
            return;
        }

        ItemStack presetStack = target.getStack();
        String preset = presetStack == null || presetStack.isEmpty() ? "" : extractEntryText(presetStack, arg.mode);
        ItemStack template = templateForMode(arg.mode);
        if (template.isEmpty())
        {
            template = new ItemStack(Items.BOOK, 1);
        }
        startSlotInput(gui, target, template, arg.mode, preset, "Arg: " + arg.keyRaw);
        setInputText(arg.valueRaw);
        submitInputText(false);
        placeBlocksCurrent.usedArgSlots.add(target.slotNumber);
        placeBlocksCurrent.advancedArgIndex++;
        placeBlocksCurrent.argsMisses = 0;
        placeBlocksCurrent.lastArgsActionMs = nowMs;
        if (placeBlocksCurrent.advancedArgIndex >= placeBlocksCurrent.advancedArgs.size())
        {
            placeBlocksCurrent.awaitingArgs = false;
            try
            {
                mc.displayGuiScreen(null);
            }
            catch (Exception ignore) { }
            placeBlocksCurrent = null;
        }
    }

    private Slot findArgTargetSlot(GuiContainer gui, PlaceArg arg, Set<Integer> used)
    {
        if (gui == null || arg == null)
        {
            return null;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.player == null)
        {
            return null;
        }
        if (arg.keyNorm == null || arg.keyNorm.isEmpty())
        {
            return null;
        }

        List<Slot> bases = new ArrayList<>();
        for (Slot slot : gui.inventorySlots.inventorySlots)
        {
            if (slot == null || slot.inventory == mc.player.inventory)
            {
                continue;
            }
            ItemStack st = slot.getStack();
            if (st == null || st.isEmpty() || !isGlassPane(st))
            {
                continue;
            }
            if (arg.glassMetaFilter != null && st.getMetadata() != arg.glassMetaFilter.intValue())
            {
                continue;
            }
            String name = normalizeForMatch(TextFormatting.getTextWithoutFormattingCodes(st.getDisplayName()));
            if (name.isEmpty() || !name.contains(arg.keyNorm))
            {
                continue;
            }
            bases.add(slot);
        }
        if (bases.isEmpty())
        {
            return null;
        }
        bases.sort(Comparator.comparingInt(a -> a.slotNumber));

        for (Slot base : bases)
        {
            Slot candidate = findCandidateSlotForArg(gui, base, arg.mode);
            if (candidate == null)
            {
                continue;
            }
            if (used != null && used.contains(candidate.slotNumber))
            {
                continue;
            }
            return candidate;
        }
        return null;
    }

    private Slot findCandidateSlotForArg(GuiContainer gui, Slot base, int mode)
    {
        if (gui == null || base == null)
        {
            return null;
        }
        net.minecraft.item.Item allowed = itemForMode(mode);
        int x = base.xPos;
        int y = base.yPos;
        int[][] offsets = new int[][]{
            {0, 18}, {-18, 0}, {18, 0}, {0, -18}
        };

        Slot bestEmpty = null;
        for (int[] off : offsets)
        {
            Slot slot = findSlotAt(gui, x + off[0], y + off[1]);
            if (slot == null)
            {
                continue;
            }
            if (!slot.getHasStack())
            {
                if (bestEmpty == null)
                {
                    bestEmpty = slot;
                }
                continue;
            }
            ItemStack st = slot.getStack();
            if (st == null || st.isEmpty())
            {
                continue;
            }
            if (isGlassPane(st))
            {
                continue;
            }
            if (allowed != null && st.getItem() == allowed)
            {
                return slot;
            }
            int actualMode = getModeForItem(st);
            if (actualMode == mode)
            {
                return slot;
            }
        }
        return bestEmpty;
    }

    @Override
    public net.minecraft.item.Item itemForMode(int mode)
    {
        if (mode == INPUT_MODE_NUMBER)
        {
            return Items.SLIME_BALL;
        }
        if (mode == INPUT_MODE_VARIABLE)
        {
            return Items.MAGMA_CREAM;
        }
        if (mode == INPUT_MODE_ARRAY)
        {
            return Items.ITEM_FRAME;
        }
        if (mode == INPUT_MODE_LOCATION)
        {
            return Items.PAPER;
        }
        if (mode == INPUT_MODE_TEXT)
        {
            return Items.BOOK;
        }
        if (mode == INPUT_MODE_ITEM)
        {
            return null;
        }
        return Items.BOOK;
    }

    private MenuStep findRandomMenuStep(GuiContainer gui, PlaceEntry entry)
    {
        if (gui == null || entry == null)
        {
            return null;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.player == null)
        {
            return null;
        }

        List<Slot> candidates = new ArrayList<>();
        for (Slot slot : gui.inventorySlots.inventorySlots)
        {
            if (slot == null || slot.inventory == mc.player.inventory)
            {
                continue;
            }
            if (entry.triedSlots.contains(slot.slotNumber))
            {
                continue;
            }
            ItemStack stack = slot.getStack();
            if (stack == null || stack.isEmpty())
            {
                continue;
            }
            String key = normalizeForMatch(getItemNameKey(stack));
            if (!key.isEmpty() && entry.triedItemKeys.contains(key))
            {
                continue;
            }
            candidates.add(slot);
        }

        if (candidates.isEmpty())
        {
            return null;
        }

        int pick = (int) (Math.random() * candidates.size());
        if (pick < 0)
        {
            pick = 0;
        }
        if (pick >= candidates.size())
        {
            pick = candidates.size() - 1;
        }
        Slot chosen = candidates.get(pick);
        // Do not mutate tried sets here. Marking happens only after the click is actually queued.
        return new MenuStep(chosen.slotNumber, false);
    }

    private MenuStep findMenuStep(GuiContainer gui, String searchKey)
    {
        if (gui == null || searchKey == null)
        {
            return null;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.player == null)
        {
            return null;
        }
        String normSearch = normalizeForMatch(searchKey);
        MenuStep direct = findDirectMenuMatch(gui, mc.player, normSearch);
        if (direct != null)
        {
            return direct;
        }
        for (Slot slot : gui.inventorySlots.inventorySlots)
        {
            if (slot == null || slot.inventory == mc.player.inventory)
            {
                continue;
            }
            ItemStack stack = slot.getStack();
            if (stack == null || stack.isEmpty())
            {
                continue;
            }
            String key = getItemNameKey(stack);
            List<ItemStack> sub = clickMenuMap.get(key);
            if (menuContainsSearch(sub, normSearch))
            {
                return new MenuStep(slot.slotNumber, false);
            }
        }
        return null;
    }

    private MenuStep findDirectMenuMatch(GuiContainer gui, EntityPlayer player, String normSearch)
    {
        for (Slot slot : gui.inventorySlots.inventorySlots)
        {
            if (slot == null || slot.inventory == player.inventory)
            {
                continue;
            }
            ItemStack stack = slot.getStack();
            if (stack == null || stack.isEmpty())
            {
                continue;
            }
            String key = normalizeForMatch(getItemNameKey(stack));
            if (!key.isEmpty() && key.contains(normSearch))
            {
                return new MenuStep(slot.slotNumber, true);
            }
        }
        return null;
    }

    private boolean menuContainsSearch(List<ItemStack> items, String normSearch)
    {
        if (items == null || normSearch == null || normSearch.isEmpty())
        {
            return false;
        }
        for (ItemStack stack : items)
        {
            if (stack == null || stack.isEmpty())
            {
                continue;
            }
            String key = normalizeForMatch(getItemNameKey(stack));
            if (!key.isEmpty() && key.contains(normSearch))
            {
                return true;
            }
        }
        return false;
    }

    private String parseLogicChain(World world, BlockPos glassPos)
    {
        // Start at y+1 from glass (glass is at y, we search at y+1)
        BlockPos startPos = glassPos.add(0, 1, 0);
        
        // Check for sign at z-1 from start position (try multiple Y levels)
        BlockPos firstSignPos = findSignAtZMinus1(world, startPos);
        if (firstSignPos == null)
        {
            return null;
        }

        TileEntity firstTile = world.getTileEntity(firstSignPos);
        if (!(firstTile instanceof TileEntitySign))
        {
            return null;
        }

        StringBuilder sb = new StringBuilder();
        TileEntitySign sign = (TileEntitySign) firstTile;
        String firstText = getSignText(sign);
        if (!firstText.isEmpty())
        {
            // Start without leading arrow; first element is raw text
            sb.append(firstText);
        }

        // Now traverse left (x-1) from the start position
        String nested = parseNestingLevel(world, startPos);
        if (!nested.isEmpty())
        {
            sb.append(nested);
        }

        return sb.toString();
    }
    
    private String getSignText(TileEntitySign sign)
    {
        // Get text from line 2 if available, otherwise line 1
        String text = sign.signText[1].getUnformattedText().trim();
        if (text.isEmpty())
        {
            text = sign.signText[0].getUnformattedText().trim();
        }
        
        // Append line 3 if it exists, separated by space
        String line3 = sign.signText[2].getUnformattedText().trim();
        if (!line3.isEmpty())
        {
            text = text + " " + line3;
        }
        
        return text;
    }
    
    @Override
    public BlockPos findSignAtZMinus1(World world, BlockPos basePos)
    {
        if (world == null || basePos == null)
        {
            return null;
        }

        // If the chunk is not loaded, use cached entry->sign mapping (so skip-check can work from cache).
        if (!world.isBlockLoaded(basePos, false))
        {
            String scopeKey = getCodeGlassScopeKey(world);
            if (scopeKey != null)
            {
                String k = scopeKey + ":" + basePos.toLong();
                Long hit = entryToSignPosByScopeEntry.get(k);
                if (hit != null)
                {
                    return BlockPos.fromLong(hit.longValue());
                }
            }
            return null;
        }

        // Try to find sign at z-1 from basePos at various Y levels
        for (int dy = -2; dy <= 0; dy++)
        {
            BlockPos checkPos = basePos.add(0, dy, -1);
            TileEntity tile = world.getTileEntity(checkPos);
            if (tile instanceof TileEntitySign)
            {
                return checkPos;
            }
        }
        return null;
    }

    @Override
    public String[] getCachedSignLines(World world, BlockPos signPos)
    {
        if (world == null || signPos == null)
        {
            return null;
        }
        String key1 = getCodeGlassScopeKey(world) + ":" + signPos.toLong();
        String[] hit = signLinesCache.get(key1);
        if (hit != null)
        {
            return hit;
        }
        String key2 = world.provider.getDimension() + ":" + signPos.toLong();
        return signLinesCacheByDimPos.get(key2);
    }

    @Override
    public void cachePlacedBlock(World world, BlockPos entryPos, Block block)
    {
        if (world == null || entryPos == null || block == null)
        {
            return;
        }

        // Scope-keyed cache (preferred): avoids collisions across different plots with the same coordinates.
        try
        {
            String scopeKey = getCodeGlassScopeKey(world);
            if (scopeKey != null)
            {
                String k = scopeKey + ":" + entryPos.toLong();
                ResourceLocation id = block.getRegistryName();
                String v = id == null ? "" : id.toString();
                if (!v.isEmpty())
                {
                    placedBlockCacheByScopePos.put(k, v);
                    codeCacheDirty = true;
                }
            }
        }
        catch (Exception e)
        {
            if (debugUi && Minecraft.getMinecraft() != null && Minecraft.getMinecraft().player != null)
            {
                Minecraft.getMinecraft().player.sendMessage(new TextComponentString("[BetterCode] cachePlacedBlock err: " + e));
            }
        }

        String key = world.provider.getDimension() + ":" + entryPos.toLong();
        ResourceLocation id = block.getRegistryName();
        placedBlockCacheByDimPos.put(key, id == null ? "" : id.toString());
    }

    @Override
    public Block getCachedPlacedBlock(World world, BlockPos entryPos)
    {
        if (world == null || entryPos == null)
        {
            return null;
        }

        try
        {
            String scopeKey = getCodeGlassScopeKey(world);
            if (scopeKey != null)
            {
                String k = scopeKey + ":" + entryPos.toLong();
                String sid = placedBlockCacheByScopePos.get(k);
                if (sid != null && !sid.trim().isEmpty())
                {
                    return Block.getBlockFromName(sid);
                }
            }
        }
        catch (Exception e)
        {
            if (debugUi && Minecraft.getMinecraft() != null && Minecraft.getMinecraft().player != null)
            {
                Minecraft.getMinecraft().player.sendMessage(new TextComponentString("[BetterCode] getCachedPlacedBlock err: " + e));
            }
        }

        String key = world.provider.getDimension() + ":" + entryPos.toLong();
        String id = placedBlockCacheByDimPos.get(key);
        if (id == null || id.trim().isEmpty())
        {
            return null;
        }
        return Block.getBlockFromName(id);
    }

    private void cacheSignLines(World world, TileEntitySign sign)
    {
        if (world == null || sign == null || sign.getPos() == null)
        {
            return;
        }
        BlockPos pos = sign.getPos();
        String[] lines = new String[]{"", "", "", ""};
        for (int i = 0; i < 4 && i < sign.signText.length; i++)
        {
            String raw = sign.signText[i] == null ? "" : sign.signText[i].getUnformattedText();
            raw = net.minecraft.util.text.TextFormatting.getTextWithoutFormattingCodes(raw);
            lines[i] = raw == null ? "" : raw;
        }
        String key1 = getCodeGlassScopeKey(world) + ":" + pos.toLong();
        signLinesCache.put(key1, lines);
        String key2 = world.provider.getDimension() + ":" + pos.toLong();
        signLinesCacheByDimPos.put(key2, lines);
        codeCacheDirty = true;
    }

    private String parseNestingLevel(World world, BlockPos pos)
    {
        StringBuilder sb = new StringBuilder();
        
        // Scan along the x-axis going left (x-1, x-2, x-3...)
        BlockPos current = pos;
        int airCount = 0;  // Count consecutive air blocks
        final int MAX_AIR = 10;  // Stop after 10 air blocks in a row
        
        for (int i = 0; i < 128; i++)
        {
            current = current.add(-1, 0, 0);
            
            // Check for block at current position
            IBlockState state = world.getBlockState(current);
            Block block = state.getBlock();
            
            // Check for sign at z-1 from current position (try multiple Y levels)
            BlockPos signPos = findSignAtZMinus1(world, current);
            if (signPos != null)
            {
                TileEntity signTile = world.getTileEntity(signPos);
                if (signTile instanceof TileEntitySign)
                {
                    TileEntitySign sign = (TileEntitySign) signTile;
                    String text = getSignText(sign);
                    if (!text.isEmpty())
                    {
                        // Decide separator based on previous content
                        if (sb.length() == 0)
                        {
                            sb.append(text);
                        }
                        else
                        {
                            char last = sb.charAt(sb.length() - 1);
                            if (last == ']')
                            {
                                // If previous token ended with a closing bracket, separate with single space
                                sb.append(' ').append(text);
                            }
                            else
                            {
                                sb.append(" > ").append(text);
                            }
                        }
                    }
                }
            }
            
            // Check for piston at current position
            if (block == Blocks.PISTON || block == Blocks.STICKY_PISTON)
            {
                EnumFacing facing = state.getValue(BlockPistonBase.FACING);
                if (facing == EnumFacing.WEST)
                {
                    // Remove trailing separator if present before opening bracket
                    if (sb.length() >= 3)
                    {
                        String tail = sb.substring(sb.length() - 3);
                        if (" > ".equals(tail))
                        {
                            sb.setLength(sb.length() - 3);
                        }
                    }
                    sb.append(" [ ");
                    airCount = 0;  // Reset air count
                    continue;
                }
                else if (facing == EnumFacing.EAST)
                {
                    sb.append(" ]");
                    return sb.toString();
                }
            }
            
            // If block is air, count it
            if (block == Blocks.AIR)
            {
                airCount++;
                if (airCount >= MAX_AIR)
                {
                    // Stop after 10 consecutive air blocks
                    return sb.toString();
                }
                continue;
            }
            
            // Any other block resets air count
            airCount = 0;
        }
        
        return sb.toString();
    }

    private void runAutoCacheCommand(MinecraftServer server, ICommandSender sender, String[] args)
    {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.world == null || mc.player == null)
        {
            return;
        }

        // Show status from config
        if (autoCacheEnabled)
        {
            setActionBar(true, "&aAutoCache: &eON &8(radius=" + autoCacheRadius + " trapped=" + autoCacheTrappedOnly + ")", 3000L);
        }
        else
        {
            setActionBar(true, "&cAutoCache: OFF &8(enable in mod config)", 3000L);
        }
    }

    private void runCacheAllChestsCommand(MinecraftServer server, ICommandSender sender, String[] args)
    {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.world == null || mc.player == null)
        {
            return;
        }
        if (args.length > 0 && "stop".equalsIgnoreCase(args[0]))
        {
            cacheAllActive = false;
            cacheAllQueue.clear();
            cacheAllCurrentTarget = null;
            setActionBar(true, "&eCacheAll stopped", 2000L);
            return;
        }
        if (!editorModeActive || !isDevCreativeScoreboard(mc))
        {
            setActionBar(false, "&cDEV mode only (creative + scoreboard)", 2500L);
            return;
        }

        int dim = mc.world.provider.getDimension();
        LinkedHashSet<BlockPos> found = new LinkedHashSet<>();

        for (TileEntity te : mc.world.loadedTileEntityList)
        {
            if (!(te instanceof TileEntityChest))
            {
                continue;
            }
            BlockPos p = te.getPos();
            if (p == null || isChestCached(dim, p) || !isTargetChestBlock(mc.world, p))
            {
                continue;
            }
            found.add(p);
        }

        cacheAllQueue.clear();
        cacheAllQueue.addAll(found);
        cacheAllCurrentTarget = null;
        cacheAllActive = !cacheAllQueue.isEmpty();
        setActionBar(true, "&aCacheAll queued=" + cacheAllQueue.size(), 2500L);
    }

    private void runPlaceAdvancedCommand(MinecraftServer server, ICommandSender sender, String[] args)
    {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.world == null || mc.player == null)
        {
            setActionBar(false, "&cNo world/player", 2000L);
            return;
        }

        if (args == null || args.length == 0)
        {
            setActionBar(false, "&cUsage: /placeadvanced <block> <name> <args|no> ...", 3500L);
            return;
        }

        if (lastGlassPos == null)
        {
            String key = getCodeGlassScopeKey(mc.world);
            if (key != null)
            {
                lastGlassPos = codeBlueGlassById.get(key);
                if (lastGlassPos != null)
                {
                    lastGlassDim = mc.world.provider.getDimension();
                }
            }
            if (lastGlassPos == null)
            {
                setActionBar(false, "&cNo blue glass position recorded", 2000L);
                return;
            }
        }

        List<String> tokens = splitArgsPreserveQuotes(String.join(" ", args));

        BlockPos glass = lastGlassPos;
        int p = 0;
        int i = 0;
        while (i < tokens.size())
        {
            String blockTok = tokens.get(i);
            if ("air".equalsIgnoreCase(blockTok) || "minecraft:air".equalsIgnoreCase(blockTok))
            {
                // Delay-only entry (cooldown between actions).
                PlaceEntry pause = new PlaceEntry(mc.player.getPosition(), Blocks.AIR, "");
                pause.searchKey = "";
                placeBlocksQueue.add(pause);
                i++;
                continue;
            }
            if (i + 2 >= tokens.size())
            {
                setActionBar(false,
                    "&cUsage: /placeadvanced <block> <name> <args|no> ... (you can also insert 'air' as a pause)",
                    4500L);
                abortPlaceBlocks("usage");
                return;
            }

            String nameTok = tokens.get(i + 1);
            String argsTok = tokens.get(i + 2);
            i += 3;

            String blockName = blockTok.contains(":") ? blockTok : ("minecraft:" + blockTok);
            Block b = Block.getBlockFromName(blockName);
            if (b == null)
            {
                if (debugUi && mc.player != null)
                {
                    mc.player.sendMessage(new TextComponentString("/placeadvanced: unknown block '" + blockTok + "'"));
                }
                setActionBar(false, "&cUnknown block: " + blockTok, 2500L);
                abortPlaceBlocks("unknown_block");
                return;
            }
            BlockPos target = glass.add(-2 * p, 1, 0);
            String search = nameTok == null ? "" : nameTok.trim();
            String norm = normalizeForMatch(search);

            PlaceEntry entry = new PlaceEntry(target, b, norm);
            entry.searchKey = norm;
            entry.desiredSlotIndex = -1;

            if (argsTok != null)
            {
                String rawArgs = argsTok.trim();
                if (!rawArgs.isEmpty() && !"no".equalsIgnoreCase(rawArgs))
                {
                    entry.advancedArgsRaw = rawArgs;
                    entry.advancedArgs = parsePlaceAdvancedArgs(rawArgs);
                }
            }

            placeBlocksQueue.add(entry);
            p++;
        }
        placeBlocksActive = !placeBlocksQueue.isEmpty();
        setActionBar(true, "&aPlaced queue created: " + placeBlocksQueue.size() + " entries", 2000L);
    }

    private void runTestPlaceCommand(MinecraftServer server, ICommandSender sender, String[] args)
    {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.world == null || mc.player == null || mc.playerController == null)
        {
            setActionBar(false, "&cNo world/player", 2000L);
            return;
        }
        if (!editorModeActive || !isDevCreativeScoreboard(mc))
        {
            setActionBar(false, "&cTestPlace: only in dev/creative", 2500L);
            return;
        }

        int method = 1;
        String blockTok = "diamond_block";
        if (args != null && args.length > 0)
        {
            try
            {
                method = Integer.parseInt(args[0]);
                if (args.length > 1)
                {
                    blockTok = args[1];
                }
            }
            catch (Exception ignore)
            {
                blockTok = args[0];
            }
        }
        if (method < 1 || method > 10)
        {
            method = 1;
        }

        BlockPos glassPos = lastGlassPos;
        if (glassPos == null || lastGlassDim != mc.world.provider.getDimension())
        {
            String key = getCodeGlassScopeKey(mc.world);
            if (key != null)
            {
                glassPos = codeBlueGlassById.get(key);
                if (glassPos != null)
                {
                    lastGlassPos = glassPos;
                    lastGlassDim = mc.world.provider.getDimension();
                }
            }
        }
        if (glassPos == null)
        {
            setActionBar(false, "&cTestPlace: no blue glass recorded", 2500L);
            return;
        }

        String blockName = blockTok.contains(":") ? blockTok : ("minecraft:" + blockTok);
        Block block = Block.getBlockFromName(blockName);
        if (block == null)
        {
            setActionBar(false, "&cTestPlace: unknown block " + blockTok, 2500L);
            return;
        }

        BlockPos placePos = glassPos.up();
        BlockPos clickPos = placePos.down();

        int slot = findHotbarSlotForBlock(mc, block);
        if (slot == -1)
        {
            giveItemToHotbar(mc, new ItemStack(block));
            slot = findHotbarSlotForBlock(mc, block);
        }
        if (slot == -1)
        {
            setActionBar(false, "&cTestPlace: block not in hotbar", 2500L);
            return;
        }

        mc.player.inventory.currentItem = slot;
        mc.playerController.updateController();
        if (mc.player.connection != null)
        {
            mc.player.connection.sendPacket(new CPacketHeldItemChange(slot));
        }

        Vec3d hitA = new Vec3d(0.5, 0.5, 0.5);
        Vec3d hitB = new Vec3d(0.5, 1.0, 0.5);
        Vec3d hitC = new Vec3d(0.1, 0.9, 0.1);

        try
        {
            switch (method)
            {
                case 1:
                    mc.playerController.processRightClickBlock(mc.player, mc.world, clickPos, EnumFacing.UP, hitA, EnumHand.MAIN_HAND);
                    break;
                case 2:
                    sendLookPacket(mc, clickPos);
                    mc.playerController.processRightClickBlock(mc.player, mc.world, clickPos, EnumFacing.UP, hitA, EnumHand.MAIN_HAND);
                    break;
                case 3:
                    sendLookPacket(mc, clickPos);
                    sendTryUsePacket(mc, clickPos, hitA);
                    break;
                case 4:
                    sendTryUsePacket(mc, clickPos, hitA);
                    break;
                case 5:
                    sendLookPacket(mc, clickPos);
                    sendTryUsePacket(mc, clickPos, hitA);
                    mc.playerController.processRightClickBlock(mc.player, mc.world, clickPos, EnumFacing.UP, hitA, EnumHand.MAIN_HAND);
                    break;
                case 6:
                    mc.playerController.processRightClickBlock(mc.player, mc.world, clickPos, EnumFacing.UP, hitB, EnumHand.MAIN_HAND);
                    break;
                case 7:
                    sendLookPacket(mc, clickPos);
                    mc.playerController.processRightClickBlock(mc.player, mc.world, clickPos, EnumFacing.UP, hitB, EnumHand.MAIN_HAND);
                    break;
                case 8:
                    mc.playerController.processRightClickBlock(mc.player, mc.world, clickPos, EnumFacing.UP, hitC, EnumHand.MAIN_HAND);
                    break;
                case 9:
                    sendLookPacket(mc, clickPos);
                    sendTryUsePacket(mc, clickPos, hitC);
                    break;
                case 10:
                    sendTryUsePacket(mc, clickPos, hitC);
                    mc.playerController.processRightClickBlock(mc.player, mc.world, clickPos, EnumFacing.UP, hitC, EnumHand.MAIN_HAND);
                    break;
                default:
                    mc.playerController.processRightClickBlock(mc.player, mc.world, clickPos, EnumFacing.UP, hitA, EnumHand.MAIN_HAND);
                    break;
            }
        }
        catch (Exception ignore) { }

        mc.player.swingArm(EnumHand.MAIN_HAND);
        setActionBar(true, "&aTestPlace method=" + method + " block=" + block.getRegistryName(), 2000L);
    }

    private void sendLookPacket(Minecraft mc, BlockPos target)
    {
        if (mc == null || mc.player == null || mc.player.connection == null || target == null)
        {
            return;
        }
        Vec3d eyes = mc.player.getPositionEyes(1.0F);
        Vec3d center = new Vec3d(target.getX() + 0.5, target.getY() + 0.5, target.getZ() + 0.5);
        double dx = center.x - eyes.x;
        double dy = center.y - eyes.y;
        double dz = center.z - eyes.z;
        double dist = Math.sqrt(dx * dx + dz * dz);
        if (dist < 0.0001)
        {
            return;
        }
        float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        float pitch = (float) (-Math.toDegrees(Math.atan2(dy, dist)));
        mc.player.connection.sendPacket(new CPacketPlayer.Rotation(yaw, pitch, mc.player.onGround));
    }

    private void sendTryUsePacket(Minecraft mc, BlockPos target, Vec3d hit)
    {
        if (mc == null || mc.player == null || mc.player.connection == null || target == null || hit == null)
        {
            return;
        }
        mc.player.connection.sendPacket(new CPacketPlayerTryUseItemOnBlock(
            target, EnumFacing.UP, EnumHand.MAIN_HAND, (float) hit.x, (float) hit.y, (float) hit.z
        ));
    }

    private void abortPlaceBlocks(String reason)
    {
        try
        {
            Minecraft mc = Minecraft.getMinecraft();
            if (mc != null)
            {
                mc.displayGuiScreen(null);
            }
        }
        catch (Exception ignore) { }

        placeBlocksActive = false;
        placeBlocksQueue.clear();
        placeBlocksCurrent = null;
        queuedClicks.clear();
        tpPathQueue.clear();
        setInputActive(false);
        if (debugUi)
        {
            setActionBar(false, "&c/place aborted: " + reason, 2000L);
        }
    }

    private List<String> splitArgsPreserveQuotes(String raw)
    {
        if (raw == null)
        {
            return new ArrayList<>();
        }
        raw = raw.trim();
        List<String> tokens = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inQuote = false;
        for (int i = 0; i < raw.length(); i++)
        {
            char c = raw.charAt(i);
            if (c == '\"')
            {
                inQuote = !inQuote;
                continue;
            }
            if (Character.isWhitespace(c) && !inQuote)
            {
                if (cur.length() > 0)
                {
                    tokens.add(cur.toString());
                    cur.setLength(0);
                }
            }
            else
            {
                cur.append(c);
            }
        }
        if (cur.length() > 0)
        {
            tokens.add(cur.toString());
        }
        return tokens;
    }

    private List<PlaceArg> parsePlaceAdvancedArgs(String raw)
    {
        List<PlaceArg> out = new ArrayList<>();
        if (raw == null)
        {
            return out;
        }
        String s = raw.trim();
        if (s.isEmpty())
        {
            return out;
        }
        // Split on commas, but keep parentheses blocks intact (simple depth counter).
        List<String> parts = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        int depth = 0;
        for (int i = 0; i < s.length(); i++)
        {
            char c = s.charAt(i);
            if (c == '(')
            {
                depth++;
            }
            else if (c == ')' && depth > 0)
            {
                depth--;
            }
            if (c == ',' && depth == 0)
            {
                parts.add(cur.toString());
                cur.setLength(0);
            }
            else
            {
                cur.append(c);
            }
        }
        if (cur.length() > 0)
        {
            parts.add(cur.toString());
        }

        for (String part : parts)
        {
            if (part == null)
            {
                continue;
            }
            String item = part.trim();
            if (item.isEmpty())
            {
                continue;
            }
            int eq = item.indexOf('=');
            if (eq <= 0 || eq == item.length() - 1)
            {
                continue;
            }
            String keyRaw = item.substring(0, eq).trim();
            String expr = item.substring(eq + 1).trim();
            if (expr.isEmpty())
            {
                continue;
            }

            Integer meta = null;
            String keyName = stripQuotes(keyRaw);
            Integer slotIndex = null;
            boolean clickOnly = false;
            int forcedClicks = -1;
            boolean slotGuiIndex = false;
            String lowKey = keyName.toLowerCase(Locale.ROOT);
            if (lowKey.startsWith("slot"))
            {
                slotGuiIndex = true;
                if (lowKey.startsWith("slotraw") || lowKey.startsWith("rawslot"))
                {
                    slotGuiIndex = false;
                }
                String digits = lowKey.replaceAll("[^0-9]", "");
                if (digits.matches("\\d+"))
                {
                    try
                    {
                        slotIndex = Integer.parseInt(digits);
                        keyName = "";
                    }
                    catch (Exception ignore) { }
                }
            }
            if (lowKey.startsWith("clicks(") && lowKey.endsWith(")"))
            {
                String inner = lowKey.substring(7, lowKey.length() - 1);
                String[] vals = inner.split(",");
                if (vals.length >= 1)
                {
                    String slotRaw = vals[0].trim().toLowerCase(Locale.ROOT);
                    String digits = slotRaw.replaceAll("[^0-9]", "");
                    if (digits.matches("\\d+"))
                    {
                        try
                        {
                            slotIndex = Integer.parseInt(digits);
                            slotGuiIndex = !slotRaw.startsWith("raw");
                        }
                        catch (Exception ignore) { }
                    }
                }
                if (vals.length >= 2 && vals[1].trim().matches("\\d+"))
                {
                    try
                    {
                        forcedClicks = Integer.parseInt(vals[1].trim());
                    }
                    catch (Exception ignore) { }
                }
                clickOnly = true;
                keyName = "";
            }
            int hash = Math.max(keyRaw.lastIndexOf('#'), keyRaw.lastIndexOf('@'));
            if (hash > 0 && hash < keyRaw.length() - 1)
            {
                String tail = keyRaw.substring(hash + 1).trim();
                if (tail.matches("\\d{1,2}"))
                {
                    try
                    {
                        meta = Integer.parseInt(tail);
                        keyName = stripQuotes(keyRaw.substring(0, hash).trim());
                    }
                    catch (Exception ignore) { }
                }
            }

            int clicks = 0;
            String valueExpr = expr;
            int semi = expr.indexOf(';');
            if (semi > -1)
            {
                valueExpr = expr.substring(0, semi).trim();
                String tail = expr.substring(semi + 1).trim();
                for (String partTail : tail.split(";"))
                {
                    String t = partTail.trim();
                    if (t.startsWith("clicks=") || t.startsWith("click="))
                    {
                        String num = t.substring(t.indexOf('=') + 1).trim();
                        if (num.matches("\\d+"))
                        {
                            try
                            {
                                clicks = Integer.parseInt(num);
                            }
                            catch (Exception ignore) { }
                        }
                    }
                    else if (t.startsWith("slot=") || t.startsWith("slotid="))
                    {
                        String num = t.substring(t.indexOf('=') + 1).trim();
                        if (num.matches("\\d+"))
                        {
                            try
                            {
                                slotIndex = Integer.parseInt(num);
                            }
                            catch (Exception ignore) { }
                        }
                    }
                }
            }
            if (forcedClicks > -1)
            {
                clicks = forcedClicks;
            }

            int mode = INPUT_MODE_TEXT;
            String valueRaw = valueExpr;
            boolean saveVariable = false;

            String low = valueExpr.toLowerCase(Locale.ROOT);
            if (low.startsWith("num(") && valueExpr.endsWith(")"))
            {
                mode = INPUT_MODE_NUMBER;
                valueRaw = valueExpr.substring(4, valueExpr.length() - 1);
            }
            else if (low.startsWith("var_save(") && valueExpr.endsWith(")"))
            {
                mode = INPUT_MODE_VARIABLE;
                valueRaw = valueExpr.substring(9, valueExpr.length() - 1);
                saveVariable = true;
            }
            else if (low.startsWith("var(") && valueExpr.endsWith(")"))
            {
                mode = INPUT_MODE_VARIABLE;
                valueRaw = valueExpr.substring(4, valueExpr.length() - 1);
            }
            else if (low.startsWith("text(") && valueExpr.endsWith(")"))
            {
                mode = INPUT_MODE_TEXT;
                valueRaw = valueExpr.substring(5, valueExpr.length() - 1);
            }
            else if (low.startsWith("arr_save(") && valueExpr.endsWith(")"))
            {
                mode = INPUT_MODE_ARRAY;
                valueRaw = valueExpr.substring(9, valueExpr.length() - 1);
                saveVariable = true;
                if ((valueRaw.startsWith("\"") && valueRaw.endsWith("\"")) || (valueRaw.startsWith("'") && valueRaw.endsWith("'")))
                {
                    valueRaw = valueRaw.substring(1, valueRaw.length() - 1);
                }
            }
            else if (low.startsWith("arr(") && valueExpr.endsWith(")"))
            {
                mode = INPUT_MODE_ARRAY;
                valueRaw = valueExpr.substring(4, valueExpr.length() - 1);
            }
            else if (low.startsWith("array(") && valueExpr.endsWith(")"))
            {
                mode = INPUT_MODE_ARRAY;
                valueRaw = valueExpr.substring(6, valueExpr.length() - 1);
            }
            else if (low.startsWith("apple(") && valueExpr.endsWith(")"))
            {
                mode = INPUT_MODE_APPLE;
                valueRaw = valueExpr.substring(6, valueExpr.length() - 1);
            }
            else if (valueExpr.matches("-?\\d+(?:\\.\\d+)?"))
            {
                mode = INPUT_MODE_NUMBER;
                valueRaw = valueExpr;
            }
            else if (valueExpr.startsWith("%") && valueExpr.endsWith("%"))
            {
                mode = INPUT_MODE_VARIABLE;
                valueRaw = valueExpr;
            }

            String keyNorm = normalizeForMatch(keyName);
            out.add(new PlaceArg(keyName, keyNorm, meta, mode, valueRaw, clicks, saveVariable, slotIndex, clickOnly,
                slotGuiIndex));
        }
        return out;
    }

    private static String stripQuotes(String raw)
    {
        if (raw == null)
        {
            return "";
        }
        String s = raw.trim();
        if (s.length() >= 2 && s.startsWith("\"") && s.endsWith("\""))
        {
            return s.substring(1, s.length() - 1);
        }
        return s;
    }

    private void runPlaceBlocksCommand(MinecraftServer server, ICommandSender sender, String[] args)
    {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.world == null || mc.player == null)
        {
            setActionBar(false, "&cNo world/player", 2000L);
            return;
        }

        if (args == null || args.length == 0)
        {
            setActionBar(false, "&cUsage: /place <block1> [block2]...", 3000L);
            return;
        }

        if (lastGlassPos == null)
        {
            String key = getCodeGlassScopeKey(mc.world);
            if (key != null)
            {
                lastGlassPos = codeBlueGlassById.get(key);
                if (lastGlassPos != null)
                {
                    lastGlassDim = mc.world.provider.getDimension();
                }
            }
            if (lastGlassPos == null)
            {
                setActionBar(false, "&cNo blue glass position recorded", 2000L);
                return;
            }
        }

        String raw = String.join(" ", args).trim();
        List<String> tokens = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inQuote = false;
        for (int i = 0; i < raw.length(); i++)
        {
            char c = raw.charAt(i);
            if (c == '\"')
            {
                inQuote = !inQuote;
                continue;
            }
            if (Character.isWhitespace(c) && !inQuote)
            {
                if (cur.length() > 0)
                {
                    tokens.add(cur.toString());
                    cur.setLength(0);
                }
            }
            else
            {
                cur.append(c);
            }
        }
        if (cur.length() > 0)
        {
            tokens.add(cur.toString());
        }

        if (tokens.size() % 2 != 0)
        {
            setActionBar(false, "&cUsage: /place <block> <name> [<block> <name> ...]", 4000L);
            return;
        }

        BlockPos glass = lastGlassPos;
        if (glass == null)
        {
            setActionBar(false, "&cNo blue glass position recorded", 2000L);
            return;
        }

        int pairs = tokens.size() / 2;
        for (int p = 0; p < pairs; p++)
        {
            String blockTok = tokens.get(p * 2);
            String nameTok = tokens.get(p * 2 + 1);
            String blockName = blockTok.contains(":") ? blockTok : ("minecraft:" + blockTok);
            Block b = Block.getBlockFromName(blockName);
            if (b == null)
            {
                if (debugUi && mc.player != null)
                {
                    mc.player.sendMessage(new TextComponentString("/place: unknown block '" + blockTok + "'"));
                }
                continue;
            }
            BlockPos target = glass.add(-2 * p, 1, 0);
            String search = nameTok == null ? "" : nameTok.trim();
            String norm = normalizeForMatch(search);
            boolean cachedMatch = false;
            if (!norm.isEmpty())
            {
                for (List<ItemStack> items : clickMenuMap.values())
                {
                    if (menuContainsSearch(items, norm))
                    {
                        cachedMatch = true;
                        break;
                    }
                }
            }

            PlaceEntry entry = new PlaceEntry(target, b, norm);
            entry.searchKey = norm;
            entry.desiredSlotIndex = -1;
            if (!cachedMatch && debugUi && mc.player != null)
            {
                mc.player.sendMessage(new TextComponentString(
                    "/place: no cached menu item matching '" + nameTok + "' (will try live)"));
            }
            placeBlocksQueue.add(entry);
        }
        placeBlocksActive = !placeBlocksQueue.isEmpty();
        setActionBar(true, "&aPlaced queue created: " + placeBlocksQueue.size() + " entries", 2000L);
    }
}    



