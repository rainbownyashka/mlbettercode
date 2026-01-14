package com.example.examplemod.cmd;

import java.util.function.Supplier;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.TextComponentString;

public final class ScoreTitleCommand extends CommandBase
{
    private final ActionBarSink actionBar;
    private final Supplier<String> getTitle;

    public ScoreTitleCommand(ActionBarSink actionBar, Supplier<String> getTitle)
    {
        this.actionBar = actionBar;
        this.getTitle = getTitle;
    }

    @Override
    public String getName()
    {
        return "scoretitle";
    }

    @Override
    public String getUsage(ICommandSender sender)
    {
        return "/scoretitle";
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args)
    {
        String title = getTitle == null ? null : getTitle.get();
        if (title == null || title.isEmpty())
        {
            if (actionBar != null)
            {
                actionBar.show(true, "&eNo scoreboard title.", 2000L);
            }
            return;
        }
        if (sender != null)
        {
            sender.sendMessage(new TextComponentString(title));
        }
    }

    @Override
    public int getRequiredPermissionLevel()
    {
        return 0;
    }
}

