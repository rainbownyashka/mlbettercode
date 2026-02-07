package com.example.examplemod.feature.export;

import java.util.ArrayList;
import java.util.List;

public final class ExportScanCore
{
    private ExportScanCore() { }

    public interface DebugSink
    {
        void log(String msg);
    }

    public interface Access
    {
        boolean isLoaded(Pos p);

        String blockId(Pos p);

        String[] signAtEntry(Pos entry);

        String chestJsonAtEntry(Pos entry, boolean preferCache);

        String sideFacing(Pos side);
    }

    public static final class Pos
    {
        public final int x;
        public final int y;
        public final int z;

        public Pos(int x, int y, int z)
        {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        public Pos add(int dx, int dy, int dz)
        {
            return new Pos(x + dx, y + dy, z + dz);
        }

        public Pos up()
        {
            return add(0, 1, 0);
        }
    }

    public static final class BlockOut
    {
        public final Pos pos;
        public final String blockId;
        public final String[] sign;
        public final String facing;
        public final String chestJson;

        public BlockOut(Pos pos, String blockId, String[] sign, String facing, String chestJson)
        {
            this.pos = pos;
            this.blockId = blockId == null || blockId.isEmpty() ? "minecraft:air" : blockId;
            this.sign = sign;
            this.facing = facing == null ? "" : facing;
            this.chestJson = chestJson == null ? "" : chestJson;
        }
    }

    public static final class RowOut
    {
        public final int rowIndex;
        public final Pos glassPos;
        public final List<BlockOut> blocks;

        public RowOut(int rowIndex, Pos glassPos, List<BlockOut> blocks)
        {
            this.rowIndex = rowIndex;
            this.glassPos = glassPos;
            this.blocks = blocks == null ? new ArrayList<BlockOut>() : blocks;
        }
    }

    public static RowOut scanRow(
        Pos glassPos,
        int rowIndex,
        int maxSteps,
        boolean preferChestCache,
        Access access,
        DebugSink dbg
    )
    {
        if (glassPos == null || access == null)
        {
            return new RowOut(rowIndex, glassPos, new ArrayList<BlockOut>());
        }

        Pos start = glassPos.up();
        List<BlockOut> out = new ArrayList<BlockOut>();
        int emptyPairs = 0;
        log(dbg, "row[" + rowIndex + "] start glass=" + posStr(glassPos) + " start=" + posStr(start) + " maxSteps=" + maxSteps);

        for (int p = 0; p < maxSteps; p++)
        {
            Pos entry = start.add(-2 * p, 0, 0);
            Pos side = entry.add(-1, 0, 0);
            if (!access.isLoaded(entry) || !access.isLoaded(side))
            {
                log(dbg, "row[" + rowIndex + "] stop@p=" + p + " reason=unloaded entry=" + posStr(entry) + " side=" + posStr(side));
                break;
            }

            String entryBlock = nz(access.blockId(entry));
            String sideBlock = nz(access.blockId(side));
            String[] sign = access.signAtEntry(entry);
            boolean signEmpty = isSignEmpty(sign);

            boolean emptySlot = isAir(entryBlock) && isAir(sideBlock) && signEmpty;
            if (emptySlot)
            {
                emptyPairs++;
                log(dbg, "row[" + rowIndex + "] p=" + p + " emptySlot=true emptyPairs=" + emptyPairs
                    + " entry=" + posStr(entry) + " side=" + posStr(side));
                if (emptyPairs >= 2)
                {
                    log(dbg, "row[" + rowIndex + "] stop@p=" + p + " reason=two-empty-pairs");
                    break;
                }
                continue;
            }
            emptyPairs = 0;

            String chestJson = access.chestJsonAtEntry(entry, preferChestCache);
            boolean hasChest = chestJson != null && !chestJson.isEmpty();
            boolean hasEntryData = !isAir(entryBlock) || !signEmpty || hasChest;

            log(dbg, "row[" + rowIndex + "] p=" + p + " export entry=" + posStr(entry)
                + " entryBlock=" + entryBlock
                + " sideBlock=" + sideBlock
                + " sign=" + (sign == null ? "null" : signStr(sign)));
            log(dbg, "row[" + rowIndex + "] p=" + p + " chestDetected=" + hasChest);

            if (hasEntryData)
            {
                out.add(new BlockOut(entry, entryBlock, sign, "", chestJson));
            }

            if (isPiston(sideBlock))
            {
                String facing = nz(access.sideFacing(side)).toLowerCase();
                out.add(new BlockOut(side, sideBlock, null, facing, ""));
                log(dbg, "row[" + rowIndex + "] p=" + p + " export side piston side=" + posStr(side) + " facing=" + facing);
            }
        }

        log(dbg, "row[" + rowIndex + "] done");
        return new RowOut(rowIndex, glassPos, out);
    }

    private static String nz(String s)
    {
        return s == null ? "" : s;
    }

    private static boolean isAir(String blockId)
    {
        return "minecraft:air".equals(blockId == null ? "" : blockId);
    }

    private static boolean isPiston(String blockId)
    {
        return "minecraft:piston".equals(blockId) || "minecraft:sticky_piston".equals(blockId);
    }

    private static boolean isSignEmpty(String[] sign)
    {
        if (sign == null)
        {
            return true;
        }
        for (String s : sign)
        {
            if (s != null && !s.trim().isEmpty())
            {
                return false;
            }
        }
        return true;
    }

    private static String signStr(String[] sign)
    {
        if (sign == null)
        {
            return "null";
        }
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < sign.length; i++)
        {
            if (i > 0)
            {
                sb.append(", ");
            }
            sb.append(sign[i] == null ? "" : sign[i]);
        }
        sb.append("]");
        return sb.toString();
    }

    private static String posStr(Pos p)
    {
        if (p == null)
        {
            return "null";
        }
        return "BlockPos{x=" + p.x + ", y=" + p.y + ", z=" + p.z + "}";
    }

    private static void log(DebugSink sink, String msg)
    {
        if (sink != null)
        {
            sink.log(msg);
        }
    }
}
