package com.rainbow_universe.bettercode.core.place;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class PlaceArgsParser {
    public interface Normalizer {
        String normalizeForMatch(String value);
    }

    private PlaceArgsParser() {
    }

    public static List<String> splitArgsPreserveQuotes(String raw) {
        List<String> out = new ArrayList<String>();
        if (raw == null) {
            return out;
        }
        StringBuilder cur = new StringBuilder();
        boolean inQuote = false;
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c == '"') {
                inQuote = !inQuote;
                continue;
            }
            if (Character.isWhitespace(c) && !inQuote) {
                if (cur.length() > 0) {
                    out.add(cur.toString());
                    cur.setLength(0);
                }
            } else {
                cur.append(c);
            }
        }
        if (cur.length() > 0) {
            out.add(cur.toString());
        }
        return out;
    }

    public static List<PlaceArgSpec> parsePlaceAdvancedArgs(String raw, Normalizer normalizer) {
        List<PlaceArgSpec> out = new ArrayList<PlaceArgSpec>();
        if (raw == null || raw.trim().isEmpty() || normalizer == null) {
            return out;
        }
        for (String part : splitArgsByComma(raw)) {
            if (part == null) {
                continue;
            }
            String item = part.trim();
            if (item.isEmpty()) {
                continue;
            }
            int eq = item.indexOf('=');
            if (eq < 0) {
                continue;
            }
            String keyRaw = item.substring(0, eq).trim();
            String expr = item.substring(eq + 1).trim();
            if (expr.isEmpty()) {
                continue;
            }

            Integer meta = null;
            String keyName = stripQuotes(keyRaw);
            Integer slotIndex = null;
            boolean clickOnly = false;
            int forcedClicks = -1;
            boolean slotGuiIndex = false;
            String lowKey = keyName.toLowerCase(Locale.ROOT);
            if (lowKey.startsWith("slot")) {
                slotGuiIndex = true;
                if (lowKey.startsWith("slotraw") || lowKey.startsWith("rawslot")) {
                    slotGuiIndex = false;
                }
                String digits = lowKey.replaceAll("[^0-9]", "");
                if (digits.matches("\\d+")) {
                    try {
                        slotIndex = Integer.parseInt(digits);
                        keyName = "";
                    } catch (Exception ignore) {
                    }
                }
            }
            if (lowKey.startsWith("clicks(") && lowKey.endsWith(")")) {
                String inner = lowKey.substring(7, lowKey.length() - 1);
                String[] vals = inner.split(",");
                if (vals.length >= 1) {
                    String slotRaw = vals[0].trim().toLowerCase(Locale.ROOT);
                    String digits = slotRaw.replaceAll("[^0-9]", "");
                    if (digits.matches("\\d+")) {
                        try {
                            slotIndex = Integer.parseInt(digits);
                            slotGuiIndex = !slotRaw.startsWith("raw");
                        } catch (Exception ignore) {
                        }
                    }
                }
                if (vals.length >= 2 && vals[1].trim().matches("\\d+")) {
                    try {
                        forcedClicks = Integer.parseInt(vals[1].trim());
                    } catch (Exception ignore) {
                    }
                }
                clickOnly = true;
                keyName = "";
            }
            int hash = Math.max(keyRaw.lastIndexOf('#'), keyRaw.lastIndexOf('@'));
            if (hash > 0 && hash < keyRaw.length() - 1) {
                String tail = keyRaw.substring(hash + 1).trim();
                if (tail.matches("\\d{1,2}")) {
                    try {
                        meta = Integer.parseInt(tail);
                        keyName = stripQuotes(keyRaw.substring(0, hash).trim());
                    } catch (Exception ignore) {
                    }
                }
            }

            int clicks = 0;
            String valueExpr = expr;
            int semi = expr.indexOf(';');
            if (semi > -1) {
                valueExpr = expr.substring(0, semi).trim();
                String tail = expr.substring(semi + 1).trim();
                for (String partTail : tail.split(";")) {
                    String t = partTail.trim();
                    if (t.startsWith("clicks=") || t.startsWith("click=")) {
                        String num = t.substring(t.indexOf('=') + 1).trim();
                        if (num.matches("\\d+")) {
                            try {
                                clicks = Integer.parseInt(num);
                            } catch (Exception ignore) {
                            }
                        }
                    } else if (t.startsWith("slot=") || t.startsWith("slotid=")) {
                        String num = t.substring(t.indexOf('=') + 1).trim();
                        if (num.matches("\\d+")) {
                            try {
                                slotIndex = Integer.parseInt(num);
                            } catch (Exception ignore) {
                            }
                        }
                    }
                }
            }
            if (forcedClicks > -1) {
                clicks = forcedClicks;
            }

            int mode = PlaceInputMode.TEXT;
            String valueRaw = valueExpr;
            boolean saveVariable = false;
            ItemSpec itemSpec = null;
            String low = valueExpr.toLowerCase(Locale.ROOT);

            if (low.startsWith("num(") && valueExpr.endsWith(")")) {
                mode = PlaceInputMode.NUMBER;
                valueRaw = valueExpr.substring(4, valueExpr.length() - 1);
            } else if (low.startsWith("var_save(") && valueExpr.endsWith(")")) {
                mode = PlaceInputMode.VARIABLE;
                valueRaw = valueExpr.substring(9, valueExpr.length() - 1);
                saveVariable = true;
            } else if (low.startsWith("var(") && valueExpr.endsWith(")")) {
                mode = PlaceInputMode.VARIABLE;
                valueRaw = valueExpr.substring(4, valueExpr.length() - 1);
            } else if (low.startsWith("text(") && valueExpr.endsWith(")")) {
                mode = PlaceInputMode.TEXT;
                valueRaw = valueExpr.substring(5, valueExpr.length() - 1);
            } else if (low.startsWith("arr_save(") && valueExpr.endsWith(")")) {
                mode = PlaceInputMode.ARRAY;
                valueRaw = valueExpr.substring(9, valueExpr.length() - 1);
                saveVariable = true;
                if ((valueRaw.startsWith("\"") && valueRaw.endsWith("\""))
                    || (valueRaw.startsWith("'") && valueRaw.endsWith("'"))) {
                    valueRaw = valueRaw.substring(1, valueRaw.length() - 1);
                }
            } else if (low.startsWith("arr(") && valueExpr.endsWith(")")) {
                mode = PlaceInputMode.ARRAY;
                valueRaw = valueExpr.substring(4, valueExpr.length() - 1);
            } else if (low.startsWith("array(") && valueExpr.endsWith(")")) {
                mode = PlaceInputMode.ARRAY;
                valueRaw = valueExpr.substring(6, valueExpr.length() - 1);
            } else if (low.startsWith("apple(") && valueExpr.endsWith(")")) {
                mode = PlaceInputMode.APPLE;
                valueRaw = valueExpr.substring(6, valueExpr.length() - 1);
            } else if (low.startsWith("item(") && valueExpr.endsWith(")")) {
                mode = PlaceInputMode.ITEM;
                valueRaw = valueExpr.substring(5, valueExpr.length() - 1);
                itemSpec = parseItemSpec(valueRaw);
            } else if (valueExpr.matches("-?\\d+(?:\\.\\d+)?")) {
                mode = PlaceInputMode.NUMBER;
                valueRaw = valueExpr;
            } else if (valueExpr.startsWith("%") && valueExpr.endsWith("%")) {
                mode = PlaceInputMode.VARIABLE;
                valueRaw = valueExpr;
            }

            String keyNorm = safeNormalize(normalizer, keyName);
            out.add(new PlaceArgSpec(
                keyName, keyNorm, meta, mode, valueRaw, clicks, saveVariable, slotIndex, clickOnly, slotGuiIndex, itemSpec
            ));
        }
        return out;
    }

    private static ItemSpec parseItemSpec(String raw) {
        if (raw == null) {
            return null;
        }
        String src = raw.trim();
        if (src.isEmpty()) {
            return null;
        }
        List<String> parts = splitTopLevel(src);
        if (parts.isEmpty()) {
            return null;
        }
        String itemId = stripQuotes(parts.get(0).trim());
        if (itemId.isEmpty()) {
            return null;
        }
        if (!itemId.contains(":")) {
            itemId = "minecraft:" + itemId;
        }

        int count = 1;
        int meta = 0;
        String name = "";
        List<String> lore = new ArrayList<String>();
        String nbtRaw = "";

        for (int i = 1; i < parts.size(); i++) {
            String t = parts.get(i);
            if (t == null) {
                continue;
            }
            String s = t.trim();
            if (s.isEmpty()) {
                continue;
            }
            int eq = s.indexOf('=');
            if (eq < 0) {
                continue;
            }
            String k = s.substring(0, eq).trim().toLowerCase(Locale.ROOT);
            String v = stripQuotes(s.substring(eq + 1).trim());
            if ((k.equals("count") || k.equals("c") || k.equals("amount")) && v.matches("\\d+")) {
                try {
                    count = Integer.parseInt(v);
                } catch (Exception ignore) {
                }
            } else if ((k.equals("meta") || k.equals("damage") || k.equals("data")) && v.matches("\\d+")) {
                try {
                    meta = Integer.parseInt(v);
                } catch (Exception ignore) {
                }
            } else if (k.equals("name") || k.equals("display") || k.equals("title")) {
                name = v;
            } else if (k.equals("description") || k.equals("desc") || k.equals("lore")) {
                for (String line : v.split("\\\\n|\\|\\|")) {
                    String ln = line == null ? "" : line.trim();
                    if (!ln.isEmpty()) {
                        lore.add(ln);
                    }
                }
            } else if (k.equals("nbt")) {
                nbtRaw = v;
            }
        }
        if (count < 1) {
            count = 1;
        }
        return new ItemSpec(itemId, count, meta, name, lore, nbtRaw);
    }

    private static String safeNormalize(Normalizer normalizer, String value) {
        try {
            String out = normalizer.normalizeForMatch(value);
            return out == null ? "" : out;
        } catch (Exception ignore) {
            return value == null ? "" : value.toLowerCase(Locale.ROOT).trim();
        }
    }

    private static List<String> splitArgsByComma(String raw) {
        List<String> out = new ArrayList<String>();
        if (raw == null) {
            return out;
        }
        StringBuilder cur = new StringBuilder();
        int depth = 0;
        boolean inQuote = false;
        char quote = 0;
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if ((c == '"' || c == '\'') && (i == 0 || raw.charAt(i - 1) != '\\')) {
                if (inQuote && c == quote) {
                    inQuote = false;
                } else if (!inQuote) {
                    inQuote = true;
                    quote = c;
                }
                cur.append(c);
                continue;
            }
            if (!inQuote) {
                if (c == '(') depth++;
                else if (c == ')' && depth > 0) depth--;
                else if (c == ',' && depth == 0) {
                    out.add(cur.toString());
                    cur.setLength(0);
                    continue;
                }
            }
            cur.append(c);
        }
        if (cur.length() > 0) {
            out.add(cur.toString());
        }
        return out;
    }

    private static List<String> splitTopLevel(String raw) {
        List<String> out = new ArrayList<String>();
        if (raw == null) {
            return out;
        }
        StringBuilder cur = new StringBuilder();
        int depthParen = 0;
        int depthBrace = 0;
        int depthBracket = 0;
        boolean inQuote = false;
        char quote = 0;
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if ((c == '"' || c == '\'') && (i == 0 || raw.charAt(i - 1) != '\\')) {
                if (inQuote && c == quote) {
                    inQuote = false;
                } else if (!inQuote) {
                    inQuote = true;
                    quote = c;
                }
                cur.append(c);
                continue;
            }
            if (!inQuote) {
                if (c == '(') depthParen++;
                else if (c == ')' && depthParen > 0) depthParen--;
                else if (c == '{') depthBrace++;
                else if (c == '}' && depthBrace > 0) depthBrace--;
                else if (c == '[') depthBracket++;
                else if (c == ']' && depthBracket > 0) depthBracket--;
                else if (c == ',' && depthParen == 0 && depthBrace == 0 && depthBracket == 0) {
                    out.add(cur.toString());
                    cur.setLength(0);
                    continue;
                }
            }
            cur.append(c);
        }
        if (cur.length() > 0) {
            out.add(cur.toString());
        }
        return out;
    }

    private static String stripQuotes(String s) {
        if (s == null) {
            return "";
        }
        String t = s.trim();
        if (t.length() >= 2) {
            char a = t.charAt(0);
            char b = t.charAt(t.length() - 1);
            if ((a == '"' && b == '"') || (a == '\'' && b == '\'')) {
                return t.substring(1, t.length() - 1);
            }
        }
        return t;
    }
}
