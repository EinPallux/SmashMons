package com.pallux.smashmons.listeners;

import com.pallux.smashmons.SmashMons;
import com.pallux.smashmons.game.Game;
import com.pallux.smashmons.game.GameState;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.util.Vector;

public class FreezeListener implements Listener {

    private final SmashMons plugin;

    public FreezeListener(SmashMons plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (!plugin.getGameManager().isInGame(player.getUniqueId())) return;

        Game game = plugin.getGameManager().getGame(player.getUniqueId());
        if (game == null) return;

        GameState s = game.getState();
        // Allow movement only during an active round or end-of-game
        if (s == GameState.IN_ROUND || s == GameState.ROUND_ENDING
                || s == GameState.GAME_ENDING) return;

        Location from = event.getFrom();
        Location to   = event.getTo();
        if (to == null) return;

        // Compare exact doubles, not just block coords — this prevents the
        // tiny momentum "slide" that block-level comparison misses.
        boolean xzChanged = Math.abs(from.getX() - to.getX()) > 0.001
                         || Math.abs(from.getZ() - to.getZ()) > 0.001;
        // Allow falling (Y can change) but block horizontal drift
        if (xzChanged) {
            Location corrected = from.clone();
            corrected.setYaw(to.getYaw());
            corrected.setPitch(to.getPitch());
            // Also preserve Y so the player doesn't float mid-air if they jump
            corrected.setY(to.getY());
            event.setTo(corrected);
            // Zero out horizontal velocity to kill momentum immediately
            Vector v = player.getVelocity();
            player.setVelocity(new Vector(0, v.getY(), 0));
        }
    }
}
