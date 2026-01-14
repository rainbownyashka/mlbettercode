package com.example.examplemod.cmd;

import java.util.function.IntFunction;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.TextComponentString;

public final class ScoreLineCommand extends CommandBase
{
    private final ActionBarSink actionBar;
    private final IntFunction<String> getLineByScore;

    public ScoreLineCommand(ActionBarSink actionBar, IntFunction<String> getLineByScore)
    {
        this.actionBar = actionBar;
        this.getLineByScore = getLineByScore;
    }

    @Override
    public String getName()
    {
        return "scoreline";
    }

    @Override
    public String getUsage(ICommandSender sender)
    {
        return "/scoreline <score>";
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args)
    {
        if (args.length < 1)
        {
            if (actionBar != null)
            {
                actionBar.show(true, "&e" + getUsage(sender), 3000L);
            }
            return;
        }
        int scoreValue;
        try
        {
            scoreValue = Integer.parseInt(args[0]);
        }
        catch (NumberFormatException e)
        {
            if (actionBar != null)
            {
                actionBar.show(true, "&cInvalid score.", 2000L);
            }
            return;
        }
        String line = getLineByScore == null ? null : getLineByScore.apply(scoreValue);
        if (line == null || line.isEmpty())
        {
            if (actionBar != null)
            {
                actionBar.show(true, "&eNo line for score " + scoreValue, 2000L);
            }
            return;
        }
        if (sender != null)
        {
            sender.sendMessage(new TextComponentString(line));
        }
    }

    @Override
    public int getRequiredPermissionLevel()
    {
        return 0;
    }
}

