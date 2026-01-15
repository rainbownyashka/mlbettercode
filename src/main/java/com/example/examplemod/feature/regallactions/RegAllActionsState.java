package com.example.examplemod.feature.regallactions;

import net.minecraft.util.math.BlockPos;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Map;

final class RegAllActionsState
{
    boolean active = false;

    BlockPos signPos = null;

    final Deque<List<String>> queue = new ArrayDeque<>();
    final Set<String> seenPaths = new HashSet<>();

    List<String> currentPath = null;
    int currentIndex = 0;
    boolean scannedCurrentMenu = false;

    long nextActionMs = 0L;
    long lastClickMs = 0L;
    boolean waitingForCache = false;
    long cacheAtMs = 0L;
    long cacheDeadlineMs = 0L;
    String pendingKeyToCache = null;
    int pendingWindowId = -1;
    String pendingGuiClass = null;
    long menuReadySinceMs = 0L;
    int menuReadyWindowId = -1;

    int menuLevel = 0; // 0 = category menu, 1 = subitem menu
    String currentCategoryKey = null;
    String currentSubItemKey = null;
    net.minecraft.item.ItemStack currentCategoryStack = net.minecraft.item.ItemStack.EMPTY;
    net.minecraft.item.ItemStack currentSubItemStack = net.minecraft.item.ItemStack.EMPTY;
    final Set<String> doneCategories = new HashSet<>();
    final Map<String, Set<String>> doneSubItemsByCategory = new java.util.HashMap<>();
    final List<String> menuPathKeys = new ArrayList<>();
    final List<net.minecraft.item.ItemStack> menuPathStacks = new ArrayList<>();
    final Map<String, Set<String>> doneByPath = new java.util.HashMap<>();
    boolean replaying = false;
    int replayIndex = 0;
    boolean pendingClick = false;
    boolean pendingExpectCategory = false;
    long pendingClickMs = 0L;
    String pendingClickKey = null;
    net.minecraft.item.ItemStack pendingClickStack = net.minecraft.item.ItemStack.EMPTY;
    boolean pendingIsReplay = false;
    String pendingGuiTitle = "";
    net.minecraft.item.ItemStack currentActionStack = net.minecraft.item.ItemStack.EMPTY;
    String currentActionGuiTitle = "";
    boolean waitingForChest = false;
    boolean openingChest = false;
    long chestWaitStartMs = 0L;
    long chestOpenDeadlineMs = 0L;
    long chestReadySinceMs = 0L;
    int chestReadyWindowId = -1;
    boolean awaitingSubClickClose = false;
    long subClickMs = 0L;
    boolean openingSign = false;
    long signClickMs = 0L;
    boolean debugSlow = false;
    boolean waitingForCursorClear = false;
    long cursorClearSinceMs = 0L;
    boolean importLoaded = false;
    final Set<String> importedRecordKeys = new HashSet<>();
    final Set<String> importedLooseKeys = new HashSet<>();
    final Set<String> importedVeryLooseKeys = new HashSet<>();
    final List<RegAllRecord> records = new ArrayList<>();

    int cachedCount = 0;
    int pathCount = 0;
    int clickCount = 0;

    int maxDepth = 6;
    int maxPaths = 5000;

    void reset()
    {
        active = false;
        signPos = null;
        queue.clear();
        seenPaths.clear();
        currentPath = null;
        currentIndex = 0;
        scannedCurrentMenu = false;
        nextActionMs = 0L;
        lastClickMs = 0L;
        waitingForCache = false;
        cacheAtMs = 0L;
        cacheDeadlineMs = 0L;
        pendingKeyToCache = null;
        pendingWindowId = -1;
        pendingGuiClass = null;
        menuReadySinceMs = 0L;
        menuReadyWindowId = -1;
        menuLevel = 0;
        currentCategoryKey = null;
        currentSubItemKey = null;
        currentCategoryStack = net.minecraft.item.ItemStack.EMPTY;
        currentSubItemStack = net.minecraft.item.ItemStack.EMPTY;
        doneCategories.clear();
        doneSubItemsByCategory.clear();
        menuPathKeys.clear();
        menuPathStacks.clear();
        doneByPath.clear();
        replaying = false;
        replayIndex = 0;
        pendingClick = false;
        pendingExpectCategory = false;
        pendingClickMs = 0L;
        pendingClickKey = null;
        pendingClickStack = net.minecraft.item.ItemStack.EMPTY;
        pendingIsReplay = false;
        pendingGuiTitle = "";
        currentActionStack = net.minecraft.item.ItemStack.EMPTY;
        currentActionGuiTitle = "";
        waitingForChest = false;
        openingChest = false;
        chestWaitStartMs = 0L;
        chestOpenDeadlineMs = 0L;
        chestReadySinceMs = 0L;
        chestReadyWindowId = -1;
        awaitingSubClickClose = false;
        subClickMs = 0L;
        openingSign = false;
        signClickMs = 0L;
        debugSlow = false;
        waitingForCursorClear = false;
        cursorClearSinceMs = 0L;
        records.clear();
        cachedCount = 0;
        pathCount = 0;
        clickCount = 0;
    }

    static String pathKey(List<String> path)
    {
        if (path == null || path.isEmpty())
        {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < path.size(); i++)
        {
            if (i > 0) sb.append(">");
            sb.append(path.get(i));
        }
        return sb.toString();
    }

    static List<String> extend(List<String> base, String next)
    {
        List<String> out = new ArrayList<>(base == null ? 1 : base.size() + 1);
        if (base != null)
        {
            out.addAll(base);
        }
        out.add(next);
        return out;
    }
}
