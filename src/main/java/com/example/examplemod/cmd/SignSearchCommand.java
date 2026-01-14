package com.example.examplemod.cmd;

import java.util.Locale;
import java.util.function.Function;

import net.minecraft.client.Minecraft;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;

public class SignSearchCommand extends CommandBase
{
    private final ActionBarSink actionBarSink;
    private final Runnable clearAction;
    private final Function<String, Integer> searchAction;

    public SignSearchCommand(ActionBarSink actionBarSink, Runnable clearAction, Function<String, Integer> searchAction)
    {
        this.actionBarSink = actionBarSink;
        this.clearAction = clearAction;
        this.searchAction = searchAction;
    }

    @Override
    public String getName()
    {
        return "tsearch";
    }

    @Override
    public String getUsage(ICommandSender sender)
    {
        return "/tsearch <text> | /tsearch clear";
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args)
    {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.world == null)
        {
            return;
        }
        if (args.length == 0)
        {
            actionBarSink.show(true, "&e" + getUsage(sender), 3000L);
            return;
        }
        String first = args[0].toLowerCase(Locale.ROOT);
        if ("clear".equals(first))
        {
            clearAction.run();
            actionBarSink.show(true, "&eSign search cleared", 2000L);
            return;
        }
        String query = String.join(" ", args).trim();
        if (query.isEmpty())
        {
            actionBarSink.show(true, "&cText required", 2000L);
            return;
        }
        int count = searchAction.apply(query);
        actionBarSink.show(true, "&aSigns: " + count, 2000L);
    }

    @Override
    public int getRequiredPermissionLevel()
    {
        return 0;
    }
}

