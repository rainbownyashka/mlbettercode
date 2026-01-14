package com.example.examplemod.cmd;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;

public final class CBarCommand extends CommandBase
{
    private final String name;
    private final boolean primary;
    private final long defaultDurationMs;
    private final ActionBarSink actionBar;

    public CBarCommand(String name, boolean primary, long defaultDurationMs, ActionBarSink actionBar)
    {
        this.name = name;
        this.primary = primary;
        this.defaultDurationMs = defaultDurationMs;
        this.actionBar = actionBar;
    }

    @Override
    public String getName()
    {
        return name;
    }

    @Override
    public String getUsage(ICommandSender sender)
    {
        return "/" + name + " <text>";
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args)
    {
        if (args.length == 0)
        {
            return;
        }
        String text = String.join(" ", args);
        if (actionBar != null)
        {
            actionBar.show(primary, text, defaultDurationMs);
        }
    }

    @Override
    public int getRequiredPermissionLevel()
    {
        return 0;
    }
}

