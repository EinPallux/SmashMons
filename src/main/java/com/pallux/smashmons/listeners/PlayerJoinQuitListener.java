package com.pallux.smashmons.listeners;

import com.pallux.smashmons.SmashMons;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class PlayerJoinQuitListener implements Listener {

    private final SmashMons plugin;

    public PlayerJoinQuitListener(SmashMons plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        plugin.getPlayerDataManager().loadAsync(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        // Remove from game if in one
        if (plugin.getGameManager().isInGame(event.getPlayer().getUniqueId())) {
            plugin.getGameManager().leaveArena(event.getPlayer());
        }
        plugin.getPlayerDataManager().unload(event.getPlayer().getUniqueId());
        plugin.getScoreboardManager().removeScoreboard(event.getPlayer());
    }
}
