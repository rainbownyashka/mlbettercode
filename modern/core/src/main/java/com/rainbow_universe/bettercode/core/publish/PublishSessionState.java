package com.rainbow_universe.bettercode.core.publish;

import com.rainbow_universe.bettercode.core.bridge.SelectedRow;

import java.io.File;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

public final class PublishSessionState {
    public static final int MAX_NEXT_PAGE_RETRIES = 5;
    public static final long WARMUP_TIMEOUT_MS = 120_000L;
    public static final long WARMUP_SETTLE_MS = 1_400L;

    public final String sessionId;
    public final long startedAtMs;
    public final String dimension;
    public final boolean cacheMode;

    public final Deque<SelectedRow> warmupQueue = new ArrayDeque<SelectedRow>();
    public final Deque<SelectedRow> warmupRetryQueue = new ArrayDeque<SelectedRow>();
    public final List<SelectedRow> selectedRows = new ArrayList<SelectedRow>();

    public boolean warmupActive;
    public int warmupPass;
    public SelectedRow currentChest;
    public long settleUntilMs;
    public long nextActionMs;
    public long timeoutAtMs;
    public int nextPageRetryCount;
    public String blockedReason;
    public String scopeCacheKey;
    public final PublishCacheView cacheView = new PublishCacheView();

    public final List<File> sourceFiles = new ArrayList<File>();
    public final String postId;
    public final String config;
    public File bundleDir;

    public PublishSessionState(
        String sessionId,
        long startedAtMs,
        String dimension,
        boolean cacheMode,
        String postId,
        String config
    ) {
        this.sessionId = sessionId == null ? "" : sessionId;
        this.startedAtMs = startedAtMs;
        this.dimension = dimension == null ? "" : dimension;
        this.cacheMode = cacheMode;
        this.postId = postId == null ? "selection" : postId;
        this.config = config == null ? "default" : config;
        this.warmupActive = false;
        this.warmupPass = 0;
        this.currentChest = null;
        this.settleUntilMs = 0L;
        this.nextActionMs = 0L;
        this.timeoutAtMs = startedAtMs + WARMUP_TIMEOUT_MS;
        this.nextPageRetryCount = 0;
        this.blockedReason = "";
        this.scopeCacheKey = "default";
    }
}
