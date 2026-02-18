package com.rainbow_universe.bettercode.core.bridge;

public final class ClickResult {
    private final boolean accepted;
    private final AckState ackState;
    private final String reason;

    private ClickResult(boolean accepted, AckState ackState, String reason) {
        this.accepted = accepted;
        this.ackState = ackState == null ? AckState.NONE : ackState;
        this.reason = reason == null ? "" : reason;
    }

    public static ClickResult accepted(AckState ackState) {
        return new ClickResult(true, ackState, "");
    }

    public static ClickResult rejected(String reason, AckState ackState) {
        return new ClickResult(false, ackState, reason);
    }

    public static ClickResult unsupported(String reason) {
        return rejected(reason, AckState.NONE);
    }

    public boolean accepted() {
        return accepted;
    }

    public AckState ackState() {
        return ackState;
    }

    public String reason() {
        return reason;
    }
}
