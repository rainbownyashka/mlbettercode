package com.example.examplemod;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraftforge.fml.client.IModGuiFactory;

import java.util.Collections;
import java.util.Set;

public class ModGuiFactory implements IModGuiFactory
{
    private static final boolean HAS_CONFIG_GUI = true;
    private static final Set<RuntimeOptionCategoryElement> EMPTY_CATEGORIES = Collections.emptySet();

    @Override
    public void initialize(Minecraft minecraftInstance)
    {
        // No initialization needed.
    }

    @Override
    public boolean hasConfigGui()
    {
        return HAS_CONFIG_GUI;
    }

    @Override
    public GuiScreen createConfigGui(GuiScreen parentScreen)
    {
        return new ModConfigGui(parentScreen);
    }

    @Override
    public Set<RuntimeOptionCategoryElement> runtimeGuiCategories()
    {
        return EMPTY_CATEGORIES;
    }
}
