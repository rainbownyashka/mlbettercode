package com.rainbow_universe.bettercode.core;

public final class PlaceExecResult {
    private final boolean ok;
    private final boolean inProgress;
    private final int executed;
    private final int failedAt;
    private final String errorCode;
    private final String errorMessage;

    private PlaceExecResult(boolean ok, boolean inProgress, int executed, int failedAt, String errorCode, String errorMessage) {
        this.ok = ok;
        this.inProgress = inProgress;
        this.executed = executed;
        this.failedAt = failedAt;
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
    }

    public static PlaceExecResult ok(int executed) {
        return new PlaceExecResult(true, false, executed, -1, null, null);
    }

    public static PlaceExecResult inProgress(int executed, String message) {
        return new PlaceExecResult(true, true, executed, -1, "IN_PROGRESS", message);
    }

    public static PlaceExecResult fail(int executed, int failedAt, String errorCode, String errorMessage) {
        return new PlaceExecResult(false, false, executed, failedAt, errorCode, errorMessage);
    }

    public boolean ok() {
        return ok;
    }

    public int executed() {
        return executed;
    }

    public boolean inProgress() {
        return inProgress;
    }

    public int failedAt() {
        return failedAt;
    }

    public String errorCode() {
        return errorCode;
    }

    public String errorMessage() {
        return errorMessage;
    }
}
