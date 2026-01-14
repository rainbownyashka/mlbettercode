package com.example.examplemod.cmd;

import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;

public final class GuiExportCommand extends CommandBase
{
    private final ActionBarSink actionBar;
    private final GuiExportSink sink;

    public GuiExportCommand(ActionBarSink actionBar, GuiExportSink sink)
    {
        this.actionBar = actionBar;
        this.sink = sink;
    }

    @Override
    public String getName()
    {
        return "guiexport";
    }

    @Override
    public String getUsage(ICommandSender sender)
    {
        return "/guiexport [raw|clean|both]";
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args)
    {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || !(mc.currentScreen instanceof GuiContainer))
        {
            if (actionBar != null)
            {
                actionBar.show(true, "&eOpen a GUI container first.", 2000L);
            }
            return;
        }
        String mode = args.length > 0 ? args[0].toLowerCase(Locale.ROOT) : "both";
        boolean wantRaw = "raw".equals(mode) || "both".equals(mode);
        boolean wantClean = "clean".equals(mode) || "both".equals(mode);
        if (!wantRaw && !wantClean)
        {
            if (actionBar != null)
            {
                actionBar.show(true, "&e" + getUsage(sender), 3000L);
            }
            return;
        }
        if (sink != null)
        {
            sink.export((GuiContainer) mc.currentScreen, wantRaw, wantClean);
        }
    }

    @Override
    public int getRequiredPermissionLevel()
    {
        return 0;
    }
}

