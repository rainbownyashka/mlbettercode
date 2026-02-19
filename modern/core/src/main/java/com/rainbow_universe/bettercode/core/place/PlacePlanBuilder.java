package com.rainbow_universe.bettercode.core.place;

import com.rainbow_universe.bettercode.core.PlaceOp;
import com.rainbow_universe.bettercode.core.SignLineNormalizer;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class PlacePlanBuilder {
    private static final String NEGATED_NAME_PREFIX = "__MLDSL_NEGATED__::";

    private PlacePlanBuilder() {
    }

    public static List<PlaceEntrySpec> fromOps(List<PlaceOp> ops) {
        List<PlaceEntrySpec> out = new ArrayList<PlaceEntrySpec>();
        if (ops == null) {
            return out;
        }
        for (PlaceOp op : ops) {
            if (op == null || op.kind() == PlaceOp.Kind.AIR) {
                out.add(PlaceEntrySpec.pause());
                continue;
            }
            if (op.kind() == PlaceOp.Kind.SKIP) {
                out.add(PlaceEntrySpec.skip());
                continue;
            }
            String rawNameToken = op.name() == null ? "" : op.name().trim();
            boolean negated = isNegatedNameToken(rawNameToken);
            String rawName = stripNegatedNameToken(rawNameToken);
            String sign1 = rawName;
            String sign2 = "";
            if (rawName.contains("||")) {
                String[] parts = rawName.split("\\Q||\\E", -1);
                sign1 = parts.length >= 1 ? safe(parts[0]) : "";
                sign2 = parts.length >= 2 ? safe(parts[1]) : "";
            }
            String blockId = safe(op.blockId());
            String argsRaw = op.args() == null ? "" : op.args().trim();
            int postPlaceKind = PlaceRuntimeEntry.POST_PLACE_NONE;
            String postPlaceName = "";
            int postPlaceCycleTicks = -1;
            String blockKey = blockId.toLowerCase(Locale.ROOT);
            if (blockKey.endsWith(":lapis_block") || "lapis_block".equals(blockKey)) {
                postPlaceKind = PlaceRuntimeEntry.POST_PLACE_SIGN_NAME;
                postPlaceName = rawName;
            } else if (blockKey.endsWith(":emerald_block") || "emerald_block".equals(blockKey)) {
                postPlaceKind = PlaceRuntimeEntry.POST_PLACE_CYCLE;
                postPlaceName = rawName;
                postPlaceCycleTicks = parseCycleTicks(argsRaw);
            } else if (negated) {
                postPlaceKind = PlaceRuntimeEntry.POST_PLACE_NEGATE;
            }
            List<PlaceArgSpec> args = PlaceArgsParser.parsePlaceAdvancedArgs(argsRaw, new PlaceArgsParser.Normalizer() {
                @Override
                public String normalizeForMatch(String value) {
                    return SignLineNormalizer.normalizeForMatch(value == null ? "" : value);
                }
            });
            out.add(PlaceEntrySpec.block(
                blockId,
                rawName,
                sign1,
                sign2,
                argsRaw,
                args,
                negated,
                postPlaceKind,
                postPlaceName,
                postPlaceCycleTicks
            ));
        }
        return out;
    }

    private static boolean isNegatedNameToken(String raw) {
        return raw != null && raw.startsWith(NEGATED_NAME_PREFIX);
    }

    private static String stripNegatedNameToken(String raw) {
        if (!isNegatedNameToken(raw)) {
            return raw == null ? "" : raw.trim();
        }
        return raw.substring(NEGATED_NAME_PREFIX.length()).trim();
    }

    private static int parseCycleTicks(String raw) {
        if (raw == null) {
            return 5;
        }
        String t = raw.trim();
        if (t.isEmpty() || "no".equalsIgnoreCase(t)) {
            return 5;
        }
        String digits = t;
        int eq = t.indexOf('=');
        if (eq >= 0) {
            String key = t.substring(0, eq).trim().toLowerCase(Locale.ROOT);
            if (key.contains("tick")) {
                digits = t.substring(eq + 1).trim();
            }
        }
        digits = digits.replaceAll("[^0-9]", "");
        if (digits.isEmpty()) {
            return 5;
        }
        try {
            return Math.max(5, Integer.parseInt(digits));
        } catch (Exception ignore) {
            return 5;
        }
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
