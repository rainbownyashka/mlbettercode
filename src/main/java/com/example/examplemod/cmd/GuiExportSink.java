package com.example.examplemod.cmd;

import net.minecraft.client.gui.inventory.GuiContainer;

@FunctionalInterface
public interface GuiExportSink
{
    void export(GuiContainer gui, boolean wantRaw, boolean wantClean);
}

