package com.example.examplemod.cmd;

import java.util.function.IntSupplier;

import net.minecraft.client.Minecraft;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.TextComponentString;

public class ApiPortCommand extends CommandBase
{
    private final IntSupplier portSupplier;

    public ApiPortCommand(IntSupplier portSupplier)
    {
        this.portSupplier = portSupplier;
    }

    @Override
    public String getName()
    {
        return "apiport";
    }

    @Override
    public String getUsage(ICommandSender sender)
    {
        return "/apiport";
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args)
    {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc != null && mc.player != null)
        {
            mc.player.sendMessage(new TextComponentString("API port: " + portSupplier.getAsInt()));
        }
    }

    @Override
    public int getRequiredPermissionLevel()
    {
        return 0;
    }
}

