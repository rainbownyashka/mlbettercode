package com.rainbow_universe.bettercode.core.place;

import java.io.PrintWriter;
import java.io.StringWriter;

public final class PlaceCommandBridgeUtil {
    private PlaceCommandBridgeUtil() {
    }

    public static String buildPlaceAdvancedCommand(PlaceRuntimeEntry entry) {
        if (entry == null || entry.isPause()) {
            return "placeadvanced air";
        }
        String block = sanitizeBlockId(entry.blockId());
        String name = sanitizeForCommandArg(entry.name());
        String args = sanitizeForCommandArg(entry.argsRaw());
        if (args.isEmpty()) {
            args = "no";
        }
        return "placeadvanced " + quote(block) + " " + quote(name) + " " + quote(args);
    }

    public static String sendServerCommand(Object networkHandler, String cmd) {
        if (networkHandler == null) {
            return "network_handler_missing";
        }
        if (cmd == null || cmd.trim().isEmpty()) {
            return "empty_command";
        }
        try {
            String noSlash = cmd.startsWith("/") ? cmd.substring(1) : cmd;
            Exception last = null;
            for (String method : new String[] {"sendChatCommand", "sendCommand", "sendChatMessage"}) {
                try {
                    String payload = "sendChatMessage".equals(method) ? ("/" + noSlash) : noSlash;
                    networkHandler.getClass().getMethod(method, String.class).invoke(networkHandler, payload);
                    return null;
                } catch (Exception ex) {
                    last = ex;
                }
            }
            return "no_command_method:" + (last == null ? "unknown" : throwableToString(last));
        } catch (Exception e) {
            return throwableToString(e);
        }
    }

    public static String throwableToString(Throwable err) {
        if (err == null) {
            return "null";
        }
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        err.printStackTrace(pw);
        pw.flush();
        return sw.toString();
    }

    private static String quote(String value) {
        String s = value == null ? "" : value.trim();
        if (s.isEmpty()) {
            return "\"\"";
        }
        if (s.contains("\"")) {
            s = s.replace("\"", "'");
        }
        if (s.contains(" ")) {
            return "\"" + s + "\"";
        }
        return s;
    }

    private static String sanitizeBlockId(String raw) {
        String s = raw == null ? "" : raw.trim().toLowerCase();
        if (s.isEmpty()) {
            return "air";
        }
        if (s.matches("[a-z0-9_:-]+")) {
            return s;
        }
        String cleaned = s.replaceAll("[^a-z0-9_:-]", "");
        return cleaned.isEmpty() ? "air" : cleaned;
    }

    private static String sanitizeForCommandArg(String raw) {
        String s = raw == null ? "" : raw;
        s = s.replaceAll("(?i)§[0-9A-FK-OR]", "");
        s = s.replace('\u00A7', ' ');
        s = s.replaceAll("[\\p{Cntrl}&&[^\r\n\t]]", " ");
        s = s.replace("\"", "'");
        s = s.replace('\n', ' ').replace('\r', ' ').replace('\t', ' ');
        s = s.trim();
        if (s.length() > 380) {
            s = s.substring(0, 380);
        }
        return s;
    }
}

