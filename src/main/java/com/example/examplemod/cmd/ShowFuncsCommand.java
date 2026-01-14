package com.example.examplemod.cmd;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import net.minecraft.client.Minecraft;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.TextComponentString;

public class ShowFuncsCommand extends CommandBase
{
    private final Supplier<Map<String, String>> clickFunctionMapSupplier;
    private final Supplier<Map<String, String>> clickMenuLocationSupplier;
    private final Supplier<Map<String, List<ItemStack>>> clickMenuMapSupplier;

    public ShowFuncsCommand(Supplier<Map<String, String>> clickFunctionMapSupplier, Supplier<Map<String, String>> clickMenuLocationSupplier,
        Supplier<Map<String, List<ItemStack>>> clickMenuMapSupplier)
    {
        this.clickFunctionMapSupplier = clickFunctionMapSupplier;
        this.clickMenuLocationSupplier = clickMenuLocationSupplier;
        this.clickMenuMapSupplier = clickMenuMapSupplier;
    }

    @Override
    public String getName()
    {
        return "showfuncs";
    }

    @Override
    public String getUsage(ICommandSender sender)
    {
        return "/showfuncs - show assigned functions and recorded GUI items";
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args)
    {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.player == null)
        {
            return;
        }

        Map<String, String> clickFunctionMap = clickFunctionMapSupplier.get();
        Map<String, String> clickMenuLocation = clickMenuLocationSupplier.get();
        Map<String, List<ItemStack>> clickMenuMap = clickMenuMapSupplier.get();

        if (clickFunctionMap == null || clickFunctionMap.isEmpty())
        {
            mc.player.sendMessage(new TextComponentString("No functions assigned."));
            return;
        }
        int shown = 0;
        for (Map.Entry<String, String> e : clickFunctionMap.entrySet())
        {
            String key = e.getKey();
            String func = e.getValue();
            String loc = (clickMenuLocation != null && clickMenuLocation.containsKey(key)) ? (" " + clickMenuLocation.get(key)) : "";
            StringBuilder sb = new StringBuilder();
            sb.append(key).append(" -> ").append(func).append(loc).append(" : ");
            List<ItemStack> items = clickMenuMap == null ? null : clickMenuMap.get(key);
            if (items == null || items.isEmpty())
            {
                sb.append("(no items)");
            }
            else
            {
                int show = Math.min(6, items.size());
                for (int i = 0; i < show; i++)
                {
                    ItemStack s = items.get(i);
                    sb.append(i == 0 ? "" : " ,").append(s.isEmpty() ? "empty" : s.getDisplayName());
                }
                if (items.size() > show)
                {
                    sb.append(" (+").append(items.size() - show).append(" more)");
                }
            }
            mc.player.sendMessage(new TextComponentString(sb.toString()));
            shown++;
            if (shown >= 50)
            {
                mc.player.sendMessage(new TextComponentString("(truncated, too many entries)"));
                break;
            }
        }
    }

    @Override
    public int getRequiredPermissionLevel()
    {
        return 0;
    }
}

