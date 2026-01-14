package com.example.examplemod.cmd;

import net.minecraft.client.Minecraft;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.GameType;

public class TestTpLocalCommand extends CommandBase
{
    private final ActionBarSink actionBarSink;

    public TestTpLocalCommand(ActionBarSink actionBarSink)
    {
        this.actionBarSink = actionBarSink;
    }

    @Override
    public String getName()
    {
        return "testtplocal";
    }

    @Override
    public String getUsage(ICommandSender sender)
    {
        return "/testtplocal <dx> <dy> <dz>";
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args)
    {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.player == null || mc.playerController == null)
        {
            return;
        }
        if (!mc.playerController.isInCreativeMode() || mc.playerController.getCurrentGameType() != GameType.CREATIVE)
        {
            actionBarSink.show(false, "&cCreative only.", 2000L);
            return;
        }
        if (args.length < 3)
        {
            actionBarSink.show(true, "&e" + getUsage(sender), 3000L);
            return;
        }
        double dx;
        double dy;
        double dz;
        try
        {
            dx = Double.parseDouble(args[0]);
            dy = Double.parseDouble(args[1]);
            dz = Double.parseDouble(args[2]);
        }
        catch (NumberFormatException e)
        {
            actionBarSink.show(true, "&cInvalid offset.", 2000L);
            return;
        }
        double nx = mc.player.posX + dx;
        double ny = mc.player.posY + dy;
        double nz = mc.player.posZ + dz;
        mc.player.setPosition(nx, ny, nz);
        mc.player.motionX = 0.0;
        mc.player.motionY = 0.0;
        mc.player.motionZ = 0.0;
        actionBarSink.show(true, "&aLocal TP: " + (int) dx + " " + (int) dy + " " + (int) dz, 1500L);
    }

    @Override
    public int getRequiredPermissionLevel()
    {
        return 0;
    }
}

