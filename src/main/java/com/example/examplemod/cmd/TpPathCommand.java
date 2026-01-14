package com.example.examplemod.cmd;

import net.minecraft.client.Minecraft;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.GameType;
import net.minecraft.world.World;

public class TpPathCommand extends CommandBase
{
    public interface QueueBuilder
    {
        void build(World world, double sx, double sy, double sz, double tx, double ty, double tz);
    }

    private final ActionBarSink actionBarSink;
    private final BooleanSupplier editorModeSupplier;
    private final QueueBuilder queueBuilder;

    public TpPathCommand(ActionBarSink actionBarSink, BooleanSupplier editorModeSupplier, QueueBuilder queueBuilder)
    {
        this.actionBarSink = actionBarSink;
        this.editorModeSupplier = editorModeSupplier;
        this.queueBuilder = queueBuilder;
    }

    @Override
    public String getName()
    {
        return "tppath";
    }

    @Override
    public String getUsage(ICommandSender sender)
    {
        return "/tppath <x> <y> <z>";
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args)
    {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.player == null || mc.playerController == null)
        {
            return;
        }
        if (!editorModeSupplier.getAsBoolean() || !mc.playerController.isInCreativeMode()
            || mc.playerController.getCurrentGameType() != GameType.CREATIVE)
        {
            actionBarSink.show(false, "&cCreative+code only.", 2000L);
            return;
        }
        if (args.length < 3)
        {
            actionBarSink.show(true, "&e" + getUsage(sender), 3000L);
            return;
        }
        double tx;
        double ty;
        double tz;
        try
        {
            tx = Double.parseDouble(args[0]);
            ty = Double.parseDouble(args[1]);
            tz = Double.parseDouble(args[2]);
        }
        catch (NumberFormatException e)
        {
            actionBarSink.show(true, "&cInvalid coords.", 2000L);
            return;
        }
        if (mc.world == null)
        {
            return;
        }
        queueBuilder.build(mc.world, mc.player.posX, mc.player.posY, mc.player.posZ, tx, ty, tz);
    }

    @Override
    public int getRequiredPermissionLevel()
    {
        return 0;
    }

    public interface BooleanSupplier
    {
        boolean getAsBoolean();
    }
}

