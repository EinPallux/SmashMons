package com.pallux.smashmons.commands;

import com.pallux.smashmons.SmashMons;
import com.pallux.smashmons.gui.AugmentInfoGui;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.List;

public class AugmentsCommand implements CommandExecutor, TabCompleter {

    private final SmashMons plugin;

    public AugmentsCommand(SmashMons plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.getMessageManager().send(sender, "player-only");
            return true;
        }
        AugmentInfoGui.open(plugin, player);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        return Collections.emptyList();
    }
}
