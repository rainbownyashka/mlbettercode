package com.example.examplemod.cmd;

import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;

@FunctionalInterface
public interface CommandRunner
{
    void run(MinecraftServer server, ICommandSender sender, String[] args);
}

