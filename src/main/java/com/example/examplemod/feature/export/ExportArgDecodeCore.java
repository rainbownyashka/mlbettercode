package com.example.examplemod.feature.export;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ExportArgDecodeCore {
    private ExportArgDecodeCore() {}

    public static final class SlotData {
        public int slot;
        public String registry = "";
        public String display = "";
        public String displayClean = "";
        public String nbt = "";
        public int count = 1;
        public List<String> lore = new ArrayList<>();
    }

    public static final class DecodedArg {
        public final String key;
        public final String value;

        public DecodedArg(String key, String value) {
            this.key = key;
            this.value = value;
        }

        @Override
        public String toString() {
            return key + "=" + value;
        }
    }

    public static final class EnumState {
        public final int selectedIndex;
        public final String selectedText;
        public final List<String> options;

        EnumState(int selectedIndex, String selectedText, List<String> options) {
            this.selectedIndex = selectedIndex;
            this.selectedText = selectedText == null ? "" : selectedText;
            this.options = options == null ? new ArrayList<String>() : options;
        }
    }

    public static EnumState readEnum(SlotData slot) {
        if (slot == null || slot.lore == null || slot.lore.isEmpty()) {
            return null;
        }
        List<String> options = new ArrayList<>();
        int selected = -1;
        for (String line : slot.lore) {
            String t = line == null ? "" : line;
            boolean hasFilled = t.contains("●") || t.contains("\u25cf");
            boolean hasEmpty = t.contains("○") || t.contains("\u25cb");
            if (!hasFilled && !hasEmpty) {
                continue;
            }
            String cleaned = stripColorCodes(t)
                .replace("●", "")
                .replace("○", "")
                .replace("\u25cf", "")
                .replace("\u25cb", "")
                .trim();
            options.add(cleaned);
            if (hasFilled) {
                selected = options.size() - 1;
            }
        }
        if (options.isEmpty() || selected < 0 || selected >= options.size()) {
            return null;
        }
        return new EnumState(selected, options.get(selected), options);
    }

    public static String inferValue(SlotData s) {
        String reg = lower(s.registry);
        String disp = safe(s.displayClean).trim();
        if (disp.isEmpty()) {
            disp = safe(s.display).trim();
        }

        if ("minecraft:book".equals(reg) || "minecraft:writable_book".equals(reg) || "minecraft:written_book".equals(reg)) {
            return "text(\"" + esc(disp.isEmpty() ? "text" : disp) + "\")";
        }
        if ("minecraft:slime_ball".equals(reg)) {
            Matcher m = Pattern.compile("-?\\d+(?:\\.\\d+)?").matcher(stripColorCodes(disp));
            return "num(" + (m.find() ? m.group() : "0") + ")";
        }
        if ("minecraft:magma_cream".equals(reg)) {
            return "var(\"" + esc(disp.isEmpty() ? "varname" : disp) + "\")";
        }
        if ("minecraft:item_frame".equals(reg)) {
            return "arr(\"" + esc(disp.isEmpty() ? "arr" : disp) + "\")";
        }
        if ("minecraft:paper".equals(reg)) {
            return "loc(\"" + esc(disp.isEmpty() ? "loc" : disp) + "\")";
        }
        if ("minecraft:apple".equals(reg)) {
            String gv = extractNbtString(s.nbt, "locname");
            if (gv.isEmpty()) {
                gv = disp.isEmpty() ? "gameval" : disp;
            }
            return "apple(\"" + esc(gv) + "\")";
        }

        StringBuilder out = new StringBuilder();
        out.append("item(\"").append(esc(reg.isEmpty() ? "minecraft:stone" : reg)).append("\"");
        if (s.count > 1) {
            out.append(", count=").append(s.count);
        }
        if (!disp.isEmpty()) {
            out.append(", name=\"").append(esc(disp)).append("\"");
        }
        if (!safe(s.nbt).trim().isEmpty() && !"{}".equals(s.nbt.trim())) {
            out.append(", nbt=\"").append(esc(s.nbt)).append("\"");
        }
        out.append(")");
        return out.toString();
    }

    public static List<DecodedArg> decodeRaw(List<SlotData> slots) {
        List<DecodedArg> out = new ArrayList<>();
        if (slots == null) {
            return out;
        }
        for (SlotData s : slots) {
            if (s == null) {
                continue;
            }
            out.add(new DecodedArg("slotraw(" + s.slot + ")", inferValue(s)));
            EnumState es = readEnum(s);
            if (es != null && es.selectedIndex > 0) {
                out.add(new DecodedArg("clicks(" + s.slot + "," + es.selectedIndex + ")", "0"));
            }
        }
        return out;
    }

    public static String decodeEnumSelectedText(List<SlotData> slots, int preferredSlot) {
        if (slots == null || slots.isEmpty()) {
            return "";
        }
        SlotData primary = null;
        for (SlotData s : slots) {
            if (s != null && s.slot == preferredSlot) {
                primary = s;
                break;
            }
        }
        if (primary != null) {
            EnumState es = readEnum(primary);
            if (es != null) return es.selectedText;
        }
        for (SlotData s : slots) {
            EnumState es = readEnum(s);
            if (es != null) return es.selectedText;
        }
        return "";
    }

    private static String extractNbtString(String nbt, String key) {
        if (nbt == null) {
            return "";
        }
        Pattern p = Pattern.compile(key + "\\\"?:\\\"([^\\\"]+)\\\"");
        Matcher m = p.matcher(nbt);
        return m.find() ? m.group(1) : "";
    }

    private static String stripColorCodes(String s) {
        if (s == null) return "";
        return s.replaceAll("[\\u00A7&][0-9A-FK-ORa-fk-or]", "");
    }

    private static String safe(String s) { return s == null ? "" : s; }
    private static String lower(String s) { return safe(s).toLowerCase(Locale.ROOT); }
    private static String esc(String s) { return safe(s).replace("\\", "\\\\").replace("\"", "\\\""); }
}
