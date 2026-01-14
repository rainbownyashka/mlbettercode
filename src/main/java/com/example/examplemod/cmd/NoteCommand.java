package com.example.examplemod.cmd;

import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Supplier;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.TextComponentString;

public class NoteCommand extends CommandBase
{
    private final ActionBarSink actionBarSink;
    private final Supplier<String> noteGetter;
    private final Consumer<String> noteSetter;
    private final Runnable noteSave;

    public NoteCommand(ActionBarSink actionBarSink, Supplier<String> noteGetter, Consumer<String> noteSetter, Runnable noteSave)
    {
        this.actionBarSink = actionBarSink;
        this.noteGetter = noteGetter;
        this.noteSetter = noteSetter;
        this.noteSave = noteSave;
    }

    @Override
    public String getName()
    {
        return "note";
    }

    @Override
    public String getUsage(ICommandSender sender)
    {
        return "/note save <text> | /note read";
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args)
    {
        if (args.length == 0)
        {
            actionBarSink.show(true, "&e" + getUsage(sender), 3000L);
            return;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if ("read".equals(sub))
        {
            String text = noteGetter.get();
            if (text == null)
            {
                text = "";
            }
            if (text.isEmpty())
            {
                actionBarSink.show(true, "&eNote is empty.", 2000L);
            }
            else if (sender != null)
            {
                sender.sendMessage(new TextComponentString(text));
            }
            return;
        }
        if ("save".equals(sub))
        {
            if (args.length < 2)
            {
                actionBarSink.show(true, "&eNote text missing.", 2000L);
                return;
            }
            StringBuilder sb = new StringBuilder();
            for (int i = 1; i < args.length; i++)
            {
                if (i > 1)
                {
                    sb.append(' ');
                }
                sb.append(args[i]);
            }
            noteSetter.accept(sb.toString());
            noteSave.run();
            actionBarSink.show(true, "&aNote saved.", 1500L);
            return;
        }
        actionBarSink.show(true, "&e" + getUsage(sender), 3000L);
    }

    @Override
    public int getRequiredPermissionLevel()
    {
        return 0;
    }
}

