package com.pallux.smashmons.listeners;

import com.pallux.smashmons.SmashMons;
import com.pallux.smashmons.augments.Augment;
import com.pallux.smashmons.game.Game;
import com.pallux.smashmons.game.GamePlayer;
import com.pallux.smashmons.game.GameState;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public class AugmentListener implements Listener {

    private final SmashMons plugin;

    public AugmentListener(SmashMons plugin) {
        this.plugin = plugin;
    }

    /**
     * Phantom Step: grants brief invisibility after dealing damage (on-hit augment).
     */
    @EventHandler
    public void onHit(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker)) return;
        if (!(event.getEntity() instanceof Player)) return;
        if (event.isCancelled()) return;

        Game game = plugin.getGameManager().getGame(attacker.getUniqueId());
        if (game == null || game.getState() != GameState.IN_ROUND) return;

        GamePlayer gp = game.getGamePlayer(attacker.getUniqueId());
        if (gp == null) return;

        for (Augment aug : gp.getAugments()) {
            if (aug.getId().equals("phantom_step")) {
                int dur = aug.getInt("invis-duration-ticks", 40);
                attacker.addPotionEffect(new PotionEffect(
                        PotionEffectType.INVISIBILITY, dur, 0, true, false, false));
            }
        }
    }
}
