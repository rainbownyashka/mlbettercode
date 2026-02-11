package com.example.examplemod;

import net.minecraft.client.gui.GuiScreen;
import net.minecraftforge.common.config.ConfigElement;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.fml.client.config.GuiConfig;
import net.minecraftforge.fml.client.config.IConfigElement;

import java.util.Collections;
import java.util.List;

public class ModConfigGui extends GuiConfig
{
    public ModConfigGui(GuiScreen parent)
    {
        super(parent, getElements(), ExampleMod.MODID, false, false, "MC Python Api");
    }

    private static List<IConfigElement> getElements()
    {
        Configuration cfg = ExampleMod.getConfig();
        if (cfg == null)
        {
            return Collections.emptyList();
        }
        return java.util.Arrays.asList(
            new ConfigElement(cfg.getCategory("hotbar")),
            new ConfigElement(cfg.getCategory("hologram")),
            new ConfigElement(cfg.getCategory("code")),
            new ConfigElement(cfg.getCategory("chest")),
            new ConfigElement(cfg.getCategory("place")),
            new ConfigElement(cfg.getCategory("hub"))
        );
    }
}
