package com.pallux.smashmons.commands;

import com.pallux.smashmons.SmashMons;
import com.pallux.smashmons.data.PlayerData;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.util.Map;

public class KdaCommand implements CommandExecutor {

    private final SmashMons plugin;

    public KdaCommand(SmashMons plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.getMessageManager().send(sender, "player-only");
            return true;
        }
        PlayerData pd = plugin.getPlayerDataManager().get(player);
        plugin.getMessageManager().send(player, "kda-message", Map.of(
                "kills", String.valueOf(pd.getKills()),
                "deaths", String.valueOf(pd.getDeaths()),
                "kd", String.valueOf(pd.getKD())));
        return true;
    }
}
