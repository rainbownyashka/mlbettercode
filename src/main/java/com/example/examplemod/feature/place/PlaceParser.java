package com.example.examplemod.feature.place;

import com.example.examplemod.model.PlaceArg;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.JsonToNBT;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.util.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class PlaceParser
{
    private PlaceParser() {}

    static List<String> splitArgsPreserveQuotes(String raw)
    {
        List<String> out = new ArrayList<>();
        if (raw == null)
        {
            return out;
        }
        StringBuilder cur = new StringBuilder();
        boolean inQuote = false;
        for (int i = 0; i < raw.length(); i++)
        {
            char c = raw.charAt(i);
            if (c == '\"')
            {
                inQuote = !inQuote;
                continue;
            }
            if (Character.isWhitespace(c) && !inQuote)
            {
                if (cur.length() > 0)
                {
                    out.add(cur.toString());
                    cur.setLength(0);
                }
            }
            else
            {
                cur.append(c);
            }
        }
        if (cur.length() > 0)
        {
            out.add(cur.toString());
        }
        return out;
    }

    static List<PlaceArg> parsePlaceAdvancedArgs(String raw, PlaceModuleHost host)
    {
        List<PlaceArg> out = new ArrayList<>();
        if (raw == null || raw.trim().isEmpty() || host == null)
        {
            return out;
        }

        for (String part : splitArgsByComma(raw))
        {
            if (part == null)
            {
                continue;
            }
            String item = part.trim();
            if (item.isEmpty())
            {
                continue;
            }
            int eq = item.indexOf('=');
            if (eq < 0)
            {
                continue;
            }
            String keyRaw = item.substring(0, eq).trim();
            String expr = item.substring(eq + 1).trim();
            if (expr.isEmpty())
            {
                continue;
            }

            Integer meta = null;
            String keyName = stripQuotes(keyRaw);
            Integer slotIndex = null;
            boolean clickOnly = false;
            int forcedClicks = -1;
            boolean slotGuiIndex = false;
            String lowKey = keyName.toLowerCase(Locale.ROOT);
            if (lowKey.startsWith("slot"))
            {
                slotGuiIndex = true;
                if (lowKey.startsWith("slotraw") || lowKey.startsWith("rawslot"))
                {
                    slotGuiIndex = false;
                }
                String digits = lowKey.replaceAll("[^0-9]", "");
                if (digits.matches("\\d+"))
                {
                    try
                    {
                        slotIndex = Integer.parseInt(digits);
                        keyName = "";
                    }
                    catch (Exception ignore) { }
                }
            }
            if (lowKey.startsWith("clicks(") && lowKey.endsWith(")"))
            {
                String inner = lowKey.substring(7, lowKey.length() - 1);
                String[] vals = inner.split(",");
                if (vals.length >= 1)
                {
                    String slotRaw = vals[0].trim().toLowerCase(Locale.ROOT);
                    String digits = slotRaw.replaceAll("[^0-9]", "");
                    if (digits.matches("\\d+"))
                    {
                        try
                        {
                            slotIndex = Integer.parseInt(digits);
                            slotGuiIndex = !slotRaw.startsWith("raw");
                        }
                        catch (Exception ignore) { }
                    }
                }
                if (vals.length >= 2 && vals[1].trim().matches("\\d+"))
                {
                    try
                    {
                        forcedClicks = Integer.parseInt(vals[1].trim());
                    }
                    catch (Exception ignore) { }
                }
                clickOnly = true;
                keyName = "";
            }
            int hash = Math.max(keyRaw.lastIndexOf('#'), keyRaw.lastIndexOf('@'));
            if (hash > 0 && hash < keyRaw.length() - 1)
            {
                String tail = keyRaw.substring(hash + 1).trim();
                if (tail.matches("\\d{1,2}"))
                {
                    try
                    {
                        meta = Integer.parseInt(tail);
                        keyName = stripQuotes(keyRaw.substring(0, hash).trim());
                    }
                    catch (Exception ignore) { }
                }
            }

            int clicks = 0;
            String valueExpr = expr;
            int semi = expr.indexOf(';');
            if (semi > -1)
            {
                valueExpr = expr.substring(0, semi).trim();
                String tail = expr.substring(semi + 1).trim();
                for (String partTail : tail.split(";"))
                {
                    String t = partTail.trim();
                    if (t.startsWith("clicks=") || t.startsWith("click="))
                    {
                        String num = t.substring(t.indexOf('=') + 1).trim();
                        if (num.matches("\\d+"))
                        {
                            try
                            {
                                clicks = Integer.parseInt(num);
                            }
                            catch (Exception ignore) { }
                        }
                    }
                    else if (t.startsWith("slot=") || t.startsWith("slotid="))
                    {
                        String num = t.substring(t.indexOf('=') + 1).trim();
                        if (num.matches("\\d+"))
                        {
                            try
                            {
                                slotIndex = Integer.parseInt(num);
                            }
                            catch (Exception ignore) { }
                        }
                    }
                }
            }
            if (forcedClicks > -1)
            {
                clicks = forcedClicks;
            }

            int mode = PlaceModule.INPUT_MODE_TEXT;
            String valueRaw = valueExpr;
            boolean saveVariable = false;

            String low = valueExpr.toLowerCase(Locale.ROOT);
            if (low.startsWith("num(") && valueExpr.endsWith(")"))
            {
                mode = PlaceModule.INPUT_MODE_NUMBER;
                valueRaw = valueExpr.substring(4, valueExpr.length() - 1);
            }
            else if (low.startsWith("var_save(") && valueExpr.endsWith(")"))
            {
                mode = PlaceModule.INPUT_MODE_VARIABLE;
                valueRaw = valueExpr.substring(9, valueExpr.length() - 1);
                saveVariable = true;
            }
            else if (low.startsWith("var(") && valueExpr.endsWith(")"))
            {
                mode = PlaceModule.INPUT_MODE_VARIABLE;
                valueRaw = valueExpr.substring(4, valueExpr.length() - 1);
            }
            else if (low.startsWith("text(") && valueExpr.endsWith(")"))
            {
                mode = PlaceModule.INPUT_MODE_TEXT;
                valueRaw = valueExpr.substring(5, valueExpr.length() - 1);
            }
            else if (low.startsWith("arr_save(") && valueExpr.endsWith(")"))
            {
                mode = PlaceModule.INPUT_MODE_ARRAY;
                valueRaw = valueExpr.substring(9, valueExpr.length() - 1);
                saveVariable = true;
                if ((valueRaw.startsWith("\"") && valueRaw.endsWith("\"")) || (valueRaw.startsWith("'") && valueRaw.endsWith("'")))
                {
                    valueRaw = valueRaw.substring(1, valueRaw.length() - 1);
                }
            }
            else if (low.startsWith("arr(") && valueExpr.endsWith(")"))
            {
                mode = PlaceModule.INPUT_MODE_ARRAY;
                valueRaw = valueExpr.substring(4, valueExpr.length() - 1);
            }
            else if (low.startsWith("array(") && valueExpr.endsWith(")"))
            {
                mode = PlaceModule.INPUT_MODE_ARRAY;
                valueRaw = valueExpr.substring(6, valueExpr.length() - 1);
            }
            else if (low.startsWith("apple(") && valueExpr.endsWith(")"))
            {
                mode = PlaceModule.INPUT_MODE_APPLE;
                valueRaw = valueExpr.substring(6, valueExpr.length() - 1);
            }
            else if (low.startsWith("item(") && valueExpr.endsWith(")"))
            {
                mode = PlaceModule.INPUT_MODE_ITEM;
                valueRaw = valueExpr.substring(5, valueExpr.length() - 1);
            }
            else if (valueExpr.matches("-?\\d+(?:\\.\\d+)?"))
            {
                mode = PlaceModule.INPUT_MODE_NUMBER;
                valueRaw = valueExpr;
            }
            else if (valueExpr.startsWith("%") && valueExpr.endsWith("%"))
            {
                mode = PlaceModule.INPUT_MODE_VARIABLE;
                valueRaw = valueExpr;
            }

            String keyNorm = host.normalizeForMatch(keyName);
            out.add(new PlaceArg(keyName, keyNorm, meta, mode, valueRaw, clicks, saveVariable, slotIndex, clickOnly,
                slotGuiIndex));
        }
        return out;
    }

    static ItemStack parseItemSpec(String raw)
    {
        if (raw == null)
        {
            return ItemStack.EMPTY;
        }
        String src = raw.trim();
        if (src.isEmpty())
        {
            return ItemStack.EMPTY;
        }

        List<String> parts = splitTopLevel(src);
        if (parts.isEmpty())
        {
            return ItemStack.EMPTY;
        }

        String idRaw = stripQuotes(parts.get(0).trim());
        if (idRaw.isEmpty())
        {
            return ItemStack.EMPTY;
        }
        if (!idRaw.contains(":"))
        {
            idRaw = "minecraft:" + idRaw;
        }
        Item item = Item.REGISTRY.getObject(new ResourceLocation(idRaw));
        if (item == null)
        {
            return ItemStack.EMPTY;
        }

        int count = 1;
        int meta = 0;
        String name = null;
        String description = null;
        String nbtRaw = null;

        for (int i = 1; i < parts.size(); i++)
        {
            String t = parts.get(i);
            if (t == null)
            {
                continue;
            }
            String s = t.trim();
            if (s.isEmpty())
            {
                continue;
            }
            int eq = s.indexOf('=');
            if (eq < 0)
            {
                continue;
            }
            String k = s.substring(0, eq).trim().toLowerCase(Locale.ROOT);
            String v = stripQuotes(s.substring(eq + 1).trim());
            if (k.equals("count") || k.equals("c") || k.equals("amount"))
            {
                if (v.matches("\\d+"))
                {
                    try
                    {
                        count = Integer.parseInt(v);
                    }
                    catch (Exception ignore) { }
                }
            }
            else if (k.equals("meta") || k.equals("damage") || k.equals("data"))
            {
                if (v.matches("\\d+"))
                {
                    try
                    {
                        meta = Integer.parseInt(v);
                    }
                    catch (Exception ignore) { }
                }
            }
            else if (k.equals("name") || k.equals("display") || k.equals("title"))
            {
                name = v;
            }
            else if (k.equals("description") || k.equals("desc") || k.equals("lore"))
            {
                description = v;
            }
            else if (k.equals("nbt"))
            {
                nbtRaw = v;
            }
        }

        if (count < 1)
        {
            count = 1;
        }
        if (count > 64)
        {
            count = 64;
        }

        ItemStack stack = new ItemStack(item, count, meta);

        NBTTagCompound tag = null;
        if (nbtRaw != null && !nbtRaw.trim().isEmpty())
        {
            String n = nbtRaw.trim();
            try
            {
                NBTBase parsed = JsonToNBT.getTagFromJson(n);
                if (parsed instanceof NBTTagCompound)
                {
                    tag = (NBTTagCompound) parsed;
                }
            }
            catch (Exception ignore) { }
        }
        if (tag != null)
        {
            stack.setTagCompound(tag);
        }

        if (name != null || description != null)
        {
            NBTTagCompound root = stack.getTagCompound();
            if (root == null)
            {
                root = new NBTTagCompound();
                stack.setTagCompound(root);
            }
            NBTTagCompound display = root.getCompoundTag("display");
            if (display == null)
            {
                display = new NBTTagCompound();
            }
            if (name != null)
            {
                display.setString("Name", applyColorCodes(name));
            }
            if (description != null)
            {
                NBTTagList lore = new NBTTagList();
                for (String line : splitLore(description))
                {
                    lore.appendTag(new NBTTagString(applyColorCodes(line)));
                }
                display.setTag("Lore", lore);
            }
            root.setTag("display", display);
        }

        return stack;
    }

    private static List<String> splitTopLevel(String raw)
    {
        List<String> out = new ArrayList<>();
        if (raw == null)
        {
            return out;
        }
        StringBuilder cur = new StringBuilder();
        boolean inQuote = false;
        int depth = 0;
        for (int i = 0; i < raw.length(); i++)
        {
            char c = raw.charAt(i);
            if (c == '\"')
            {
                inQuote = !inQuote;
                cur.append(c);
                continue;
            }
            if (!inQuote)
            {
                if (c == '{' || c == '[' || c == '(')
                {
                    depth++;
                }
                else if (c == '}' || c == ']' || c == ')')
                {
                    if (depth > 0)
                    {
                        depth--;
                    }
                }
                if (c == ',' && depth == 0)
                {
                    out.add(cur.toString().trim());
                    cur.setLength(0);
                    continue;
                }
            }
            cur.append(c);
        }
        if (cur.length() > 0)
        {
            out.add(cur.toString().trim());
        }
        return out;
    }

    private static List<String> splitLore(String raw)
    {
        List<String> out = new ArrayList<>();
        if (raw == null)
        {
            return out;
        }
        String s = raw.replace("\\n", "\n");
        for (String p : s.split("\n"))
        {
            out.add(p);
        }
        return out;
    }

    private static String applyColorCodes(String raw)
    {
        if (raw == null)
        {
            return "";
        }
        return raw.replace('&', '\u00a7');
    }

    private static List<String> splitArgsByComma(String raw)
    {
        List<String> out = new ArrayList<>();
        if (raw == null)
        {
            return out;
        }
        StringBuilder cur = new StringBuilder();
        boolean inQuote = false;
        int depth = 0;
        for (int i = 0; i < raw.length(); i++)
        {
            char c = raw.charAt(i);
            if (c == '\"')
            {
                inQuote = !inQuote;
                cur.append(c);
                continue;
            }
            if (!inQuote)
            {
                if (c == '(')
                {
                    depth++;
                }
                else if (c == ')' && depth > 0)
                {
                    depth--;
                }
                if (c == ',' && depth == 0)
                {
                    out.add(cur.toString());
                    cur.setLength(0);
                    continue;
                }
            }
            cur.append(c);
        }
        if (cur.length() > 0)
        {
            out.add(cur.toString());
        }
        return out;
    }

    private static String stripQuotes(String raw)
    {
        if (raw == null)
        {
            return "";
        }
        String s = raw.trim();
        if (s.length() >= 2
            && ((s.startsWith("\"") && s.endsWith("\"")) || (s.startsWith("'") && s.endsWith("'"))))
        {
            return s.substring(1, s.length() - 1);
        }
        return s;
    }
}
