package com.example.examplemod.cmd;

import net.minecraft.client.Minecraft;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;

public final class GenCommand extends CommandBase
{
    @Override
    public String getName()
    {
        return "gen";
    }

    @Override
    public String getUsage(ICommandSender sender)
    {
        return "/gen <name> <start-end>";
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args)
    {
        if (args.length < 2)
        {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.player == null)
        {
            return;
        }
        String base = args[0];
        int[] range = parseRange(args[1]);
        if (range == null)
        {
            return;
        }
        int start = range[0];
        int end = range[1];
        int count = Math.min(9, end - start + 1);
        for (int i = 0; i < count; i++)
        {
            int index = start + i;
            ItemStack stack = new ItemStack(Items.MAGMA_CREAM, 1);
            stack.setStackDisplayName(base + index);
            mc.player.inventory.setInventorySlotContents(i, stack);
        }
    }

    @Override
    public int getRequiredPermissionLevel()
    {
        return 0;
    }

    private static int[] parseRange(String text)
    {
        if (text == null)
        {
            return null;
        }
        String t = text.trim();
        if (t.isEmpty())
        {
            return null;
        }
        String[] parts = t.split("-");
        if (parts.length != 2)
        {
            return null;
        }
        try
        {
            int a = Integer.parseInt(parts[0].trim());
            int b = Integer.parseInt(parts[1].trim());
            if (a > b)
            {
                int tmp = a;
                a = b;
                b = tmp;
            }
            return new int[] { a, b };
        }
        catch (NumberFormatException e)
        {
            return null;
        }
    }
}

