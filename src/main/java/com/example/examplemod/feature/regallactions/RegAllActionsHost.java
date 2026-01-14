package com.example.examplemod.feature.regallactions;

import com.example.examplemod.model.ClickAction;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.inventory.GuiChest;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;

import java.util.List;

public interface RegAllActionsHost
{
    boolean isEditorModeActive();
    boolean isDebugUi();
    boolean isInputActive();

    boolean isDevCreativeScoreboard(Minecraft mc);
    boolean isHoldingIronOrGoldIngot(Minecraft mc);

    void setActionBar(boolean ok, String text, long timeMs);
    void debugChat(String text);

    BlockPos getLastClickedPos();
    boolean isLastClickedSign();

    void closeCurrentScreen();
    void queueClick(ClickAction action);

    String getItemNameKey(ItemStack stack);
    String getGuiTitle(GuiChest gui);

    void cacheClickMenuSnapshot(String key, List<ItemStack> items, String location);

    boolean isPlayerInventorySlot(GuiContainer gui, Slot slot);
}

