package com.pallux.smashmons.listeners;

import com.pallux.smashmons.SmashMons;
import com.pallux.smashmons.game.Game;
import com.pallux.smashmons.game.GamePlayer;
import com.pallux.smashmons.game.GameState;
import org.bukkit.Bukkit;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;

import java.util.Map;
import java.util.UUID;

public class CrystalListener implements Listener {

    private final SmashMons plugin;

    public CrystalListener(SmashMons plugin) {
        this.plugin = plugin;
    }

    /**
     * Player right-clicks the End Crystal to claim the ultimate.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onCrystalInteract(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (!(event.getRightClicked() instanceof EnderCrystal crystal)) return;
        if (!crystal.hasMetadata("smashmons_crystal")) return;

        event.setCancelled(true); // prevent default crystal interaction

        Player player = event.getPlayer();
        Game game = plugin.getGameManager().getGame(player.getUniqueId());
        if (game == null || game.getState() != GameState.IN_ROUND) return;

        GamePlayer gp = game.getGamePlayer(player.getUniqueId());
        if (gp == null || !gp.isAlive()) return;

        // Grant crystal, remove entity
        gp.setHasUltimateCrystal(true);
        game.getSpawnedCrystals().remove(crystal);
        crystal.remove();

        // Broadcast to all players in the game
        for (UUID uuid : game.getPlayers().keySet()) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) {
                plugin.getMessageManager().send(p, "crystal-picked-up",
                        Map.of("player", player.getName()));
            }
        }
    }

    /**
     * Prevent End Crystals used as SmashMons crystals from exploding
     * when hit by players or projectiles.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onCrystalDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof EnderCrystal crystal)) return;
        if (!crystal.hasMetadata("smashmons_crystal")) return;
        event.setCancelled(true);
    }

    /**
     * Also cancel the explosion event just in case something else triggers it.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onCrystalExplode(EntityExplodeEvent event) {
        if (!(event.getEntity() instanceof EnderCrystal crystal)) return;
        if (!crystal.hasMetadata("smashmons_crystal")) return;
        event.setCancelled(true);
    }
}
