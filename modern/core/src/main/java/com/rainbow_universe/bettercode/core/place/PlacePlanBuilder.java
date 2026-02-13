package com.rainbow_universe.bettercode.core.place;

import com.rainbow_universe.bettercode.core.PlaceOp;
import com.rainbow_universe.bettercode.core.SignLineNormalizer;

import java.util.ArrayList;
import java.util.List;

public final class PlacePlanBuilder {
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
            String rawName = op.name() == null ? "" : op.name().trim();
            String sign1 = rawName;
            String sign2 = "";
            if (rawName.contains("||")) {
                String[] parts = rawName.split("\\Q||\\E", -1);
                sign1 = parts.length >= 1 ? safe(parts[0]) : "";
                sign2 = parts.length >= 2 ? safe(parts[1]) : "";
            }
            String argsRaw = op.args() == null ? "" : op.args().trim();
            List<PlaceArgSpec> args = PlaceArgsParser.parsePlaceAdvancedArgs(argsRaw, new PlaceArgsParser.Normalizer() {
                @Override
                public String normalizeForMatch(String value) {
                    return SignLineNormalizer.normalizeForMatch(value == null ? "" : value);
                }
            });
            out.add(PlaceEntrySpec.block(
                safe(op.blockId()),
                rawName,
                sign1,
                sign2,
                argsRaw,
                args
            ));
        }
        return out;
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}

