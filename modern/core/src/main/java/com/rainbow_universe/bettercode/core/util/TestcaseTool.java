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

    public static synchronized void clearMarker() {
        marker = null;
    }

    public static MarkerView markerView() {
        Marker m = marker;
        if (m == null) {
            return null;
        }
        return new MarkerView(m.dimension, m.x, m.y, m.z);
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

    public static Result checkTrapChest(GameBridge bridge) {
        if (bridge == null) {
            return Result.fail("testcase trapcheck failed: bridge unavailable");
        }
        Marker m = marker;
        if (m == null) {
            return Result.fail("testcase trapcheck failed: no saved position, use /testcase setpos");
        }
        String curDim = safe(bridge.currentDimension());
        if (!curDim.equals(m.dimension)) {
            return Result.fail("testcase trapcheck failed: dimension mismatch current=" + curDim + " saved=" + m.dimension);
        }
        int[][] offsets = new int[][] {
            {0, 1, 0},
            {0, 2, 0},
            {0, 0, 0},
            {0, -1, 0}
        };
        for (int i = 0; i < offsets.length; i++) {
            int[] off = offsets[i];
            int x = m.x + off[0];
            int y = m.y + off[1];
            int z = m.z + off[2];
            if (bridge.isBlockAt(x, y, z, "minecraft:trapped_chest")) {
                return Result.ok("testcase trapcheck found trapped_chest at " + x + "," + y + "," + z + " probe=" + i);
            }
        }
        boolean chest = false;
        for (int[] off : offsets) {
            if (bridge.isBlockAt(m.x + off[0], m.y + off[1], m.z + off[2], "minecraft:chest")) {
                chest = true;
                break;
            }
        }
        return Result.fail("testcase trapcheck not_found base=" + m.x + "," + m.y + "," + m.z + " regularChest=" + chest);
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

    public static final class MarkerView {
        private final String dimension;
        private final int x;
        private final int y;
        private final int z;

        private MarkerView(String dimension, int x, int y, int z) {
            this.dimension = dimension;
            this.x = x;
            this.y = y;
            this.z = z;
        }

        public String dimension() {
            return dimension;
        }

        public int x() {
            return x;
        }

        public int y() {
            return y;
        }

        public int z() {
            return z;
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
