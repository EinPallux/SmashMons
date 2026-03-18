package com.pallux.smashmons.commands;

import com.pallux.smashmons.SmashMons;
import com.pallux.smashmons.gui.KitsOverviewGui;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.List;

public class KitsCommand implements CommandExecutor, TabCompleter {

    private final SmashMons plugin;

    public KitsCommand(SmashMons plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.getMessageManager().send(sender, "player-only");
            return true;
        }
        KitsOverviewGui.open(plugin, player);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        return Collections.emptyList();
    }
}
