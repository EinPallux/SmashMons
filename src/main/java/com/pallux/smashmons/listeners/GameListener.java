package com.pallux.smashmons.listeners;

import com.pallux.smashmons.SmashMons;
import com.pallux.smashmons.augments.Augment;
import com.pallux.smashmons.game.Game;
import com.pallux.smashmons.game.GamePlayer;
import com.pallux.smashmons.game.GameState;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerVelocityEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

public class GameListener implements Listener {

    private final SmashMons plugin;

    public GameListener(SmashMons plugin) {
        this.plugin = plugin;
    }

    /**
     * Central damage handler for all player-vs-player damage.
     * Covers melee (damager=Player) and projectiles (damager=Projectile with Player shooter).
     * Applies augment multipliers once.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;

        Player attacker = resolveAttacker(event.getDamager());

        Game victimGame = plugin.getGameManager().getGame(victim.getUniqueId());
        if (victimGame == null) return;

        // Cancel all PvP damage outside active round
        if (victimGame.getState() != GameState.IN_ROUND) {
            event.setCancelled(true);
            return;
        }

        GamePlayer victimGp = victimGame.getGamePlayer(victim.getUniqueId());
        if (victimGp == null || !victimGp.isAlive()) {
            event.setCancelled(true);
            return;
        }

        // Self-damage from own projectile → cancel
        if (attacker != null && attacker.getUniqueId().equals(victim.getUniqueId())) {
            event.setCancelled(true);
            return;
        }

        // Attacker not in the same game → cancel stray projectiles
        if (attacker == null || !victimGame.containsPlayer(attacker.getUniqueId())) {
            event.setCancelled(true);
            return;
        }

        GamePlayer attackerGp = victimGame.getGamePlayer(attacker.getUniqueId());
        if (attackerGp == null) { event.setCancelled(true); return; }

        // Apply augment damage multipliers
        double dmg = event.getFinalDamage();
        dmg *= attackerGp.getDamageMult();
        dmg *= victimGp.getDamageTakenMult();
        event.setDamage(dmg);

        // Spike Aura — reflect portion back to attacker
        for (Augment aug : victimGp.getAugments()) {
            if (aug.getId().equals("spike_aura")) {
                double reflect = aug.getDouble("reflect-percent", 0.20);
                attacker.damage(dmg * reflect, victim);
            }
        }

        // Vampiric Touch — heal attacker for portion of damage
        for (Augment aug : attackerGp.getAugments()) {
            if (aug.getId().equals("vampiric_touch")) {
                double maxHp = attacker.getAttribute(
                        org.bukkit.attribute.Attribute.MAX_HEALTH).getValue();
                attacker.setHealth(Math.min(maxHp,
                        attacker.getHealth() + dmg * aug.getDouble("heal-per-damage", 0.5)));
            }
        }

        // Final Stand — one-time strength burst when health drops low
        if (!victimGp.isFinalStandTriggered() && victimGp.hasAugment("final_stand")) {
            double threshold = victimGp.getAugments().stream()
                    .filter(a -> a.getId().equals("final_stand"))
                    .mapToDouble(a -> a.getDouble("threshold-health", 4.0))
                    .findFirst().orElse(4.0);
            if (victim.getHealth() - dmg <= threshold) {
                victimGp.getAugments().stream()
                        .filter(a -> a.getId().equals("final_stand")).findFirst()
                        .ifPresent(fs -> {
                            victim.addPotionEffect(new PotionEffect(
                                    PotionEffectType.STRENGTH,
                                    fs.getInt("strength-duration-ticks", 100),
                                    fs.getInt("strength-amplifier", 0), true, true, true));
                            victimGp.setFinalStandTriggered(true);
                        });
            }
        }
    }

    /**
     * Handle Blaze fireball hits — read smashmons_fireball metadata and apply damage + fire.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onProjectileHit(ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof SmallFireball fireball)) return;
        if (!fireball.hasMetadata("smashmons_fireball")) return;

        // Cancel default explosion damage — we handle it ourselves
        event.setCancelled(true);

        if (!(event.getHitEntity() instanceof Player victim)) return;

        // Metadata format: "shooterUUID,damage,fireTicks"
        String meta = fireball.getMetadata("smashmons_fireball").get(0).asString();
        String[] parts = meta.split(",");
        if (parts.length < 3) return;

        try {
            java.util.UUID shooterUuid = java.util.UUID.fromString(parts[0]);
            double damage = Double.parseDouble(parts[1]);
            int fireTicks = Integer.parseInt(parts[2]);

            // Self-hit guard
            if (shooterUuid.equals(victim.getUniqueId())) return;

            Player shooter = org.bukkit.Bukkit.getPlayer(shooterUuid);

            Game game = plugin.getGameManager().getGame(victim.getUniqueId());
            if (game == null || game.getState() != GameState.IN_ROUND) return;

            GamePlayer victimGp = game.getGamePlayer(victim.getUniqueId());
            if (victimGp == null || !victimGp.isAlive()) return;

            GamePlayer shooterGp = shooter != null ? game.getGamePlayer(shooterUuid) : null;
            double finalDmg = damage
                    * (shooterGp != null ? shooterGp.getDamageMult() : 1.0)
                    * victimGp.getDamageTakenMult();

            victim.damage(finalDmg, shooter);
            victim.setFireTicks(fireTicks);
        } catch (Exception ignored) {}
    }

    /**
     * Apply Wind Resistance augment knockback reduction.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onVelocity(PlayerVelocityEvent event) {
        Player player = event.getPlayer();
        Game game = plugin.getGameManager().getGame(player.getUniqueId());
        if (game == null || game.getState() != GameState.IN_ROUND) return;

        GamePlayer gp = game.getGamePlayer(player.getUniqueId());
        if (gp == null || gp.getKnockbackReduction() <= 0) return;

        double reduction = Math.min(0.9, gp.getKnockbackReduction());
        Vector vel = event.getVelocity();
        // Only reduce horizontal knockback, not vertical (to keep jump/flight working)
        double newX = vel.getX() * (1.0 - reduction);
        double newZ = vel.getZ() * (1.0 - reduction);
        event.setVelocity(new Vector(newX, vel.getY(), newZ));
    }

    /**
     * Block all non-PvP environmental damage for in-game players.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onEnvironmentDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (event instanceof EntityDamageByEntityEvent) return;

        Game game = plugin.getGameManager().getGame(player.getUniqueId());
        if (game == null) return;
        if (game.getState() != GameState.IN_ROUND) {
            event.setCancelled(true);
            return;
        }
        GamePlayer gp = game.getGamePlayer(player.getUniqueId());
        if (gp == null || !gp.isAlive()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Game game = plugin.getGameManager().getGame(victim.getUniqueId());
        if (game == null) return;

        event.setCancelled(true);
        event.getDrops().clear();
        event.setDroppedExp(0);

        Player killer = null;
        if (victim.getLastDamageCause() instanceof EntityDamageByEntityEvent edbe) {
            killer = resolveAttacker(edbe.getDamager());
            if (killer != null && killer.getUniqueId().equals(victim.getUniqueId())) killer = null;
        }
        if (killer == null) killer = victim.getKiller();

        game.handleDeath(victim, killer);
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {}

    /**
     * Cancel ALL vanilla health regeneration for in-game players.
     * ActionBarListener provides the custom 0.5 HP / 10s regen instead.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onRegen(EntityRegainHealthEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        // Only block regen sources from natural healing / saturation
        if (event.getRegainReason() == EntityRegainHealthEvent.RegainReason.SATIATED
                || event.getRegainReason() == EntityRegainHealthEvent.RegainReason.REGEN) {
            if (plugin.getGameManager().isInGame(player.getUniqueId())) {
                event.setCancelled(true);
            }
        }
    }

    private Player resolveAttacker(Entity damager) {
        if (damager instanceof Player p) return p;
        if (damager instanceof Projectile proj && proj.getShooter() instanceof Player p) return p;
        if (damager instanceof TNTPrimed tnt && tnt.getSource() instanceof Player p) return p;
        return null;
    }
}
