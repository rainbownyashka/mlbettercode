package com.example.examplemod.cmd;

import java.io.File;
import java.util.Locale;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;

public class ConfigDebugCommand extends CommandBase
{
    private final ActionBarSink actionBarSink;
    private final Supplier<File> configFileSupplier;
    private final Supplier<Boolean> secondHotbarEnabledSupplier;
    private final IntSupplier holoTextColorSupplier;

    public ConfigDebugCommand(ActionBarSink actionBarSink, Supplier<File> configFileSupplier,
        Supplier<Boolean> secondHotbarEnabledSupplier, IntSupplier holoTextColorSupplier)
    {
        this.actionBarSink = actionBarSink;
        this.configFileSupplier = configFileSupplier;
        this.secondHotbarEnabledSupplier = secondHotbarEnabledSupplier;
        this.holoTextColorSupplier = holoTextColorSupplier;
    }

    @Override
    public String getName()
    {
        return "mpcfg";
    }

    @Override
    public String getUsage(ICommandSender sender)
    {
        return "/mpcfg";
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args)
    {
        File cfg = configFileSupplier.get();
        String path = cfg == null ? "null" : cfg.getAbsolutePath();
        long stamp = cfg == null ? 0L : cfg.lastModified();
        boolean hotbar = Boolean.TRUE.equals(secondHotbarEnabledSupplier.get());
        int color = holoTextColorSupplier.getAsInt();
        actionBarSink.show(true, "&eCfg file: " + path, 4000L);
        actionBarSink.show(false,
            "&eCfg hotbar=" + hotbar + " holo=#" + String.format(Locale.ROOT, "%06X", color) + " t=" + stamp, 4000L);
    }

    @Override
    public int getRequiredPermissionLevel()
    {
        return 0;
    }
}

