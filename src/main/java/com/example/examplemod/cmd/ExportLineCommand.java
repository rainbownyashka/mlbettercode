package com.example.examplemod.cmd;

import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.util.function.Function;
import java.util.function.Supplier;

import net.minecraft.client.Minecraft;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class ExportLineCommand extends CommandBase
{
    private final ActionBarSink actionBarSink;
    private final Supplier<World> worldSupplier;
    private final Supplier<BlockPos> glassPosSupplier;
    private final Function<BlockPos, String> logicChainSupplier;

    public ExportLineCommand(ActionBarSink actionBarSink, Supplier<World> worldSupplier, Supplier<BlockPos> glassPosSupplier,
        Function<BlockPos, String> logicChainSupplier)
    {
        this.actionBarSink = actionBarSink;
        this.worldSupplier = worldSupplier;
        this.glassPosSupplier = glassPosSupplier;
        this.logicChainSupplier = logicChainSupplier;
    }

    @Override
    public String getName()
    {
        return "exportline";
    }

    @Override
    public String getUsage(ICommandSender sender)
    {
        return "/exportline - export logic chain from last glass position";
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args)
    {
        Minecraft mc = Minecraft.getMinecraft();
        World world = worldSupplier.get();
        if (mc == null || world == null)
        {
            actionBarSink.show(false, "&cNo world", 2000L);
            return;
        }

        BlockPos glassPos = glassPosSupplier.get();
        if (glassPos == null)
        {
            actionBarSink.show(false, "&cNo glass position found", 2000L);
            return;
        }

        String logicChain = logicChainSupplier.apply(glassPos);
        if (logicChain == null || logicChain.isEmpty())
        {
            actionBarSink.show(false, "&cNo sign found at (y+1,z-1)", 2000L);
            return;
        }

        try
        {
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(logicChain), null);
            String preview = logicChain.length() > 30 ? logicChain.substring(0, 27) + "..." : logicChain;
            actionBarSink.show(true, "&aCopied: " + preview, 3000L);
        }
        catch (Exception e)
        {
            actionBarSink.show(false, "&cFailed to copy: " + e.getMessage(), 2000L);
        }
    }

    @Override
    public int getRequiredPermissionLevel()
    {
        return 0;
    }
}

