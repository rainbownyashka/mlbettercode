package com.rainbow_universe.bettercode.core;

public final class RuntimeResult {
    private final boolean ok;
    private final RuntimeErrorCode errorCode;
    private final String message;

    public RuntimeResult(boolean ok, RuntimeErrorCode errorCode, String message) {
        this.ok = ok;
        this.errorCode = errorCode;
        this.message = message;
    }

    public static RuntimeResult ok(String message) {
        return new RuntimeResult(true, null, message);
    }

    public static RuntimeResult fail(RuntimeErrorCode errorCode, String message) {
        return new RuntimeResult(false, errorCode, message);
    }

    public boolean ok() {
        return ok;
    }

    public RuntimeErrorCode errorCode() {
        return errorCode;
    }

    public String message() {
        return message;
    }
}
