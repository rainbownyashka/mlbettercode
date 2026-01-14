package com.example.examplemod.feature.place;

import com.example.examplemod.model.PlaceArg;

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

        String[] parts = raw.split(",");
        for (String part : parts)
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
            if (keyRaw.isEmpty() || expr.isEmpty())
            {
                continue;
            }

            Integer meta = null;
            String keyName = keyRaw;
            int hash = Math.max(keyRaw.lastIndexOf('#'), keyRaw.lastIndexOf('@'));
            if (hash > 0 && hash < keyRaw.length() - 1)
            {
                String tail = keyRaw.substring(hash + 1).trim();
                if (tail.matches("\\d{1,2}"))
                {
                    try
                    {
                        meta = Integer.parseInt(tail);
                        keyName = keyRaw.substring(0, hash).trim();
                    }
                    catch (Exception ignore) { }
                }
            }

            int mode = PlaceModule.INPUT_MODE_TEXT;
            String valueRaw = expr;

            String low = expr.toLowerCase(Locale.ROOT);
            if (low.startsWith("num(") && expr.endsWith(")"))
            {
                mode = PlaceModule.INPUT_MODE_NUMBER;
                valueRaw = expr.substring(4, expr.length() - 1);
            }
            else if (low.startsWith("var(") && expr.endsWith(")"))
            {
                mode = PlaceModule.INPUT_MODE_VARIABLE;
                valueRaw = expr.substring(4, expr.length() - 1);
            }
            else if (low.startsWith("text(") && expr.endsWith(")"))
            {
                mode = PlaceModule.INPUT_MODE_TEXT;
                valueRaw = expr.substring(5, expr.length() - 1);
            }
            else if (expr.matches("-?\\d+(?:\\.\\d+)?"))
            {
                mode = PlaceModule.INPUT_MODE_NUMBER;
                valueRaw = expr;
            }
            else if (expr.startsWith("%") && expr.endsWith("%"))
            {
                mode = PlaceModule.INPUT_MODE_VARIABLE;
                valueRaw = expr;
            }

            String keyNorm = host.normalizeForMatch(keyName);
            out.add(new PlaceArg(keyName, keyNorm, meta, mode, valueRaw));
        }
        return out;
    }
}

