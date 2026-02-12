package com.rainbow_universe.bettercode.core;

public final class RuntimeCore {
    private final CoreLogger logger;

    public RuntimeCore(CoreLogger logger) {
        this.logger = logger;
    }

    public RuntimeResult handleRun(String postId, String configKey, GameBridge bridge) {
        String cfg = configKey == null || configKey.trim().isEmpty() ? "default" : configKey.trim();
        logger.info("printer-debug", "run requested postId=" + postId + " config=" + cfg + " dim=" + bridge.currentDimension());
        return RuntimeResult.fail(
            RuntimeErrorCode.UNIMPLEMENTED_PLATFORM_OPERATION,
            "Run pipeline parity is not wired for this modern adapter yet."
        );
    }

    public RuntimeResult handleConfirmLoad(GameBridge bridge) {
        logger.info("confirmload-debug", "confirm requested scoreboardLines=" + bridge.scoreboardLines().size());
        return RuntimeResult.fail(
            RuntimeErrorCode.UNIMPLEMENTED_PLATFORM_OPERATION,
            "Confirm load pipeline parity is not wired for this modern adapter yet."
        );
    }

    public RuntimeResult handlePublish(GameBridge bridge) {
        logger.info("publish-debug", "publish requested scoreboardLines=" + bridge.scoreboardLines().size());
        return RuntimeResult.fail(
            RuntimeErrorCode.UNIMPLEMENTED_PLATFORM_OPERATION,
            "Publish pipeline parity is not wired for this modern adapter yet."
        );
    }
}
