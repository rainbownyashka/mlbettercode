package com.example.examplemod.cmd;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;

public class DelegatingCommand extends CommandBase
{
    private final String name;
    private final String usage;
    private final CommandRunner runner;

    public DelegatingCommand(String name, String usage, CommandRunner runner)
    {
        this.name = name;
        this.usage = usage;
        this.runner = runner;
    }

    @Override
    public String getName()
    {
        return name;
    }

    @Override
    public String getUsage(ICommandSender sender)
    {
        return usage;
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args)
    {
        runner.run(server, sender, args);
    }

    @Override
    public int getRequiredPermissionLevel()
    {
        return 0;
    }
}

