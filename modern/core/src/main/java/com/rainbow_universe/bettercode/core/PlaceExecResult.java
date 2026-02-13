package com.rainbow_universe.bettercode.core;

public final class PlaceExecResult {
    private final boolean ok;
    private final int executed;
    private final int failedAt;
    private final String errorCode;
    private final String errorMessage;

    private PlaceExecResult(boolean ok, int executed, int failedAt, String errorCode, String errorMessage) {
        this.ok = ok;
        this.executed = executed;
        this.failedAt = failedAt;
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
    }

    public static PlaceExecResult ok(int executed) {
        return new PlaceExecResult(true, executed, -1, null, null);
    }

    public static PlaceExecResult fail(int executed, int failedAt, String errorCode, String errorMessage) {
        return new PlaceExecResult(false, executed, failedAt, errorCode, errorMessage);
    }

    public boolean ok() {
        return ok;
    }

    public int executed() {
        return executed;
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

