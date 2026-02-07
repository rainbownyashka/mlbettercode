package com.example.examplemod.feature.export;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;

public class ExportCodeCoreTest {

    private static final class Ctx implements ExportCodeCore.RowContext {
        private final Map<String, String> blocks = new HashMap<>();
        private final Map<String, String[]> signs = new HashMap<>();
        private final Map<String, String> facing = new HashMap<>();

        private static String key(ExportCodeCore.Pos p) {
            return p.x + ":" + p.y + ":" + p.z;
        }

        Ctx block(int x, int y, int z, String id) {
            blocks.put(x + ":" + y + ":" + z, id);
            return this;
        }

        Ctx sign(int x, int y, int z, String l1, String l2, String l3, String l4) {
            signs.put(x + ":" + y + ":" + z, new String[]{l1, l2, l3, l4});
            return this;
        }

        Ctx face(int x, int y, int z, String dir) {
            facing.put(x + ":" + y + ":" + z, dir);
            return this;
        }

        @Override
        public boolean isLoaded(ExportCodeCore.Pos pos) {
            return true;
        }

        @Override
        public String getBlockId(ExportCodeCore.Pos pos) {
            return blocks.getOrDefault(key(pos), "minecraft:air");
        }

        @Override
        public String[] getSignLinesAtEntry(ExportCodeCore.Pos entryPos) {
            return signs.get(key(entryPos));
        }

        @Override
        public String getChestJsonAtEntry(ExportCodeCore.Pos entryPos, boolean preferChestCache) {
            return "";
        }

        @Override
        public String getFacing(ExportCodeCore.Pos pos) {
            return facing.getOrDefault(key(pos), "");
        }
    }

    @Test
    public void exportsEntryAndPistonsInStrictGeometryOrder() {
        Ctx ctx = new Ctx()
            .block(10, 1, 0, "minecraft:diamond_block")
            .sign(10, 1, 0, "Событие игрока", "Вход", "", "")
            .block(8, 1, 0, "minecraft:planks")
            .sign(8, 1, 0, "Если игрок", "Имеет право", "", "")
            .block(7, 1, 0, "minecraft:piston")
            .face(7, 1, 0, "west")
            .block(6, 1, 0, "minecraft:cobblestone")
            .sign(6, 1, 0, "Действие игрока", "Сообщение", "", "")
            .block(5, 1, 0, "minecraft:piston")
            .face(5, 1, 0, "east");

        String json = ExportCodeCore.buildRowJson(ctx, new ExportCodeCore.Pos(10, 0, 0), 16, 0, true, null);
        assertNotNull(json);

        int iEvent = json.indexOf("\"x\":10");
        int iIf = json.indexOf("\"x\":8");
        int iOpen = json.indexOf("\"x\":7");
        int iAction = json.indexOf("\"x\":6");
        int iClose = json.indexOf("\"x\":5");

        assertTrue(iEvent >= 0);
        assertTrue(iIf > iEvent);
        assertTrue(iOpen > iIf);
        assertTrue(iAction > iOpen);
        assertTrue(iClose > iAction);
        assertTrue(json.contains("\"facing\":\"west\""));
        assertTrue(json.contains("\"facing\":\"east\""));
    }

    @Test
    public void exportsSidePistonEvenWhenEntryIsAir() {
        Ctx ctx = new Ctx()
            .block(10, 1, 0, "minecraft:diamond_block")
            .sign(10, 1, 0, "Событие игрока", "Вход", "", "")
            .block(7, 1, 0, "minecraft:piston")
            .face(7, 1, 0, "west");

        String json = ExportCodeCore.buildRowJson(ctx, new ExportCodeCore.Pos(10, 0, 0), 8, 0, true, null);
        assertNotNull(json);
        assertTrue(json.contains("\"x\":7"));
        assertTrue(json.contains("\"facing\":\"west\""));
    }
}
