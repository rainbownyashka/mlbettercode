package com.example.examplemod.feature.place;

import com.example.examplemod.model.ClickAction;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.inventory.GuiChest;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.inventory.Slot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.List;
import java.util.Map;

public interface PlaceModuleHost
{
    boolean isEditorModeActive();
    boolean isDebugUi();
    boolean isInputActive();

    void setActionBar(boolean ok, String text, long timeMs);
    void debugChat(String text);

    boolean isDevCreativeScoreboard(Minecraft mc);
    boolean isHoldingIronOrGoldIngot(Minecraft mc);
    boolean tpPathQueueIsEmpty();
    void buildTpPathQueue(World world, double fromX, double fromY, double fromZ, double toX, double toY, double toZ);

    void queueClick(ClickAction action);
    void clearQueuedClicks();
    void clearTpPathQueue();

    void closeCurrentScreen();
    void setInputActive(boolean active);

    void startSlotInput(GuiContainer container, Slot target, ItemStack template, int mode, String preset, String title);
    void setInputText(String text);
    void submitInputText(boolean giveExtra);

    boolean isGlassPane(ItemStack stack);
    Item itemForMode(int mode);
    ItemStack templateForMode(int mode);
    String extractEntryText(ItemStack stack, int mode);

    String normalizeForMatch(String s);
    String getItemNameKey(ItemStack stack);
    String getGuiTitle(GuiChest gui);

    BlockPos findSignAtZMinus1(World world, BlockPos basePos);

    BlockPos getLastGlassPos();
    int getLastGlassDim();
    void setLastGlassPos(BlockPos pos, int dim);
    String getCodeGlassScopeKey(World world);
    Map<String, BlockPos> getCodeBlueGlassById();

    Map<String, List<ItemStack>> getClickMenuMap();
}

