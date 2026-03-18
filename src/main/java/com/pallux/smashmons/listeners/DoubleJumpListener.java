package com.pallux.smashmons.listeners;

import com.pallux.smashmons.SmashMons;
import com.pallux.smashmons.game.Game;
import com.pallux.smashmons.game.GamePlayer;
import com.pallux.smashmons.game.GameState;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class DoubleJumpListener implements Listener {

    private final SmashMons plugin;
    private final Map<UUID, Long> jumpCooldowns = new HashMap<>();

    public DoubleJumpListener(SmashMons plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onFlightToggle(PlayerToggleFlightEvent event) {
        Player player = event.getPlayer();
        if (!plugin.getGameManager().isInGame(player.getUniqueId())) return;
        Game game = plugin.getGameManager().getGame(player.getUniqueId());
        if (game == null || game.getState() != GameState.IN_ROUND) return;
        GamePlayer gp = game.getGamePlayer(player.getUniqueId());
        if (gp == null || !gp.isAlive()) return;

        event.setCancelled(true);

        // Check cooldown
        int baseCooldownSec = plugin.getConfig().getInt("game.double-jump-cooldown-seconds", 3);
        double reduction = gp.getDoubleJumpCooldownReduction();
        long cooldownMs = (long) Math.max(0, (baseCooldownSec - reduction) * 1000L);

        long lastJump = jumpCooldowns.getOrDefault(player.getUniqueId(), 0L);
        long now = System.currentTimeMillis();
        if (now - lastJump < cooldownMs) {
            long remaining = (cooldownMs - (now - lastJump)) / 1000 + 1;
            plugin.getMessageManager().send(player, "double-jump-cooldown",
                    Map.of("seconds", String.valueOf(remaining)));
            return;
        }

        // Perform double jump
        jumpCooldowns.put(player.getUniqueId(), now);
        player.setAllowFlight(false);
        double velocity = plugin.getConfig().getDouble("game.double-jump-velocity", 0.8);
        player.setVelocity(player.getLocation().getDirection().multiply(0.5).setY(velocity));
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (!plugin.getGameManager().isInGame(player.getUniqueId())) return;
        Game game = plugin.getGameManager().getGame(player.getUniqueId());
        if (game == null || game.getState() != GameState.IN_ROUND) return;
        GamePlayer gp = game.getGamePlayer(player.getUniqueId());
        if (gp == null || !gp.isAlive()) return;

        // Re-arm double-jump when the player lands (only during active round)
        if (player.isOnGround() && !player.isFlying()) {
            player.setAllowFlight(true);
        }
    }
}
