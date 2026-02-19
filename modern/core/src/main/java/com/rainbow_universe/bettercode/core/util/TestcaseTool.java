package com.rainbow_universe.bettercode.core.util;

import com.rainbow_universe.bettercode.core.GameBridge;
import com.rainbow_universe.bettercode.core.bridge.AckState;
import com.rainbow_universe.bettercode.core.bridge.ClickResult;

public final class TestcaseTool {
    private static volatile Marker marker;

    private TestcaseTool() {
    }

    public static synchronized Result setPos(String dim, int x, int y, int z) {
        String d = dim == null || dim.trim().isEmpty() ? "unknown" : dim.trim();
        marker = new Marker(d, x, y, z);
        return Result.ok("testcase setpos dim=" + d + " pos=" + x + "," + y + "," + z);
    }

    public static Result rightClick(GameBridge bridge) {
        if (bridge == null) {
            return Result.fail("testcase rightclick failed: bridge unavailable");
        }
        Marker m = marker;
        if (m == null) {
            return Result.fail("testcase rightclick failed: no saved position, use /testcase setpos");
        }
        String curDim = safe(bridge.currentDimension());
        if (!curDim.equals(m.dimension)) {
            return Result.fail("testcase rightclick failed: dimension mismatch current=" + curDim + " saved=" + m.dimension);
        }
        ClickResult click = bridge.clickBlockLegacy(m.x, m.y, m.z, "testcase_rightclick", true);
        if (click != null && click.accepted()) {
            return Result.ok("testcase rightclick ok pos=" + m.x + "," + m.y + "," + m.z);
        }
        boolean used = bridge.useBlockAt(m.x, m.y, m.z, "testcase_rightclick_fallback");
        if (used) {
            return Result.ok("testcase rightclick ok(fallback) pos=" + m.x + "," + m.y + "," + m.z);
        }
        String reason = click == null ? "click=null" : safe(click.reason()) + " ack=" + ack(click.ackState());
        return Result.fail("testcase rightclick failed pos=" + m.x + "," + m.y + "," + m.z + " reason=" + reason);
    }

    public static Result tp(GameBridge bridge) {
        if (bridge == null) {
            return Result.fail("testcase tp failed: bridge unavailable");
        }
        Marker m = marker;
        if (m == null) {
            return Result.fail("testcase tp failed: no saved position, use /testcase setpos");
        }
        String curDim = safe(bridge.currentDimension());
        if (!curDim.equals(m.dimension)) {
            return Result.fail("testcase tp failed: dimension mismatch current=" + curDim + " saved=" + m.dimension);
        }
        boolean queued = bridge.enqueueTpPath(m.x, m.y, m.z);
        if (queued) {
            return Result.ok("testcase tp queued pos=" + m.x + "," + m.y + "," + m.z);
        }
        return Result.fail("testcase tp failed pos=" + m.x + "," + m.y + "," + m.z);
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }

    private static String ack(AckState state) {
        return state == null ? "null" : state.name();
    }

    private static final class Marker {
        final String dimension;
        final int x;
        final int y;
        final int z;

        Marker(String dimension, int x, int y, int z) {
            this.dimension = dimension;
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }

    public static final class Result {
        private final boolean ok;
        private final String message;

        private Result(boolean ok, String message) {
            this.ok = ok;
            this.message = message == null ? "" : message;
        }

        public static Result ok(String message) {
            return new Result(true, message);
        }

        public static Result fail(String message) {
            return new Result(false, message);
        }

        public boolean ok() {
            return ok;
        }

        public String message() {
            return message;
        }
    }
}
