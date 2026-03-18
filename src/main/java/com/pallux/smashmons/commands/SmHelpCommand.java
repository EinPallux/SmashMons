package com.pallux.smashmons.commands;

import com.pallux.smashmons.SmashMons;
import com.pallux.smashmons.util.ColorUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public class SmHelpCommand implements CommandExecutor {

    private final SmashMons plugin;

    public SmHelpCommand(SmashMons plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        sender.sendMessage(Component.empty());
        sender.sendMessage(ColorUtil.colorize("&#D946EF&l     SmashMons — Commands"));
        sender.sendMessage(ColorUtil.colorize("&#CCCCCC     ──────────────────────────────"));

        line(sender, "/smashmons",  "Open the arena browser and join a game.");
        line(sender, "/kits",       "Browse all kits, view abilities & unlock with SmashCoins.");
        line(sender, "/augments",   "View the augment compendium — all passive bonuses explained.");
        line(sender, "/smashcoins", "Check your current SmashCoins balance.");
        line(sender, "/kda",        "See your all-time kills, deaths and K/D ratio.");
        line(sender, "/smhelp",     "Show this help menu.");

        sender.sendMessage(ColorUtil.colorize("&#CCCCCC     ──────────────────────────────"));
        sender.sendMessage(Component.empty());
        return true;
    }

    private void line(CommandSender sender, String command, String description) {
        sender.sendMessage(ColorUtil.colorize(
                "  &#FFD700" + command + " &#CCCCCC— &#FFFFFF" + description));
    }
}