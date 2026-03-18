package com.pallux.smashmons.kits.abilities;

import com.pallux.smashmons.SmashMons;
import com.pallux.smashmons.game.Game;
import com.pallux.smashmons.game.GamePlayer;
import com.pallux.smashmons.kits.KitAbility;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.UUID;

public class BlazeAbilities {

    private BlazeAbilities() {}

    public static boolean activate(SmashMons plugin, Player player, Game game,
                                   GamePlayer gp, KitAbility ability, String key) {
        return switch (key) {
            case "primary"   -> fireballBarrage(plugin, player, game, gp, ability);
            case "secondary" -> flameSurge(plugin, player, game, gp, ability);
            default -> false;
        };
    }

    public static boolean activateUltimate(SmashMons plugin, Player player, Game game,
                                           GamePlayer gp, KitAbility ability) {
        return inferno(plugin, player, game, gp, ability);
    }

    // ── Fireball Barrage ──────────────────────────────────────────────────────
    // Each fireball carries its own damage+fireTicks in metadata.
    // GameListener.onProjectileHit reads the metadata and applies the damage.
    private static boolean fireballBarrage(SmashMons plugin, Player player, Game game,
                                           GamePlayer gp, KitAbility ability) {
        int count    = ability.getInt("fireball-count", 3);
        double damage = ability.getDouble("damage", 4.0) * gp.getDamageMult();
        int fireTicks = ability.getInt("fire-ticks", 60);
        int delay     = ability.getInt("delay-between-ticks", 5);

        for (int i = 0; i < count; i++) {
            final int fi = i;
            new BukkitRunnable() {
                @Override public void run() {
                    if (!player.isOnline()) return;
                    SmallFireball fb = player.launchProjectile(SmallFireball.class);
                    fb.setShooter(player);
                    fb.setIsIncendiary(false); // we handle fire ourselves
                    fb.setYield(0);
                    fb.setVelocity(player.getLocation().getDirection().multiply(2.0));
                    fb.setMetadata("smashmons_fireball", new FixedMetadataValue(plugin,
                            player.getUniqueId() + "," + damage + "," + fireTicks));
                }
            }.runTaskLater(plugin, (long) fi * delay);
        }

        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_BLAZE_SHOOT, 1f, 1f);
        return true;
    }

    // ── Flame Surge ───────────────────────────────────────────────────────────
    private static boolean flameSurge(SmashMons plugin, Player player, Game game,
                                      GamePlayer gp, KitAbility ability) {
        double damage     = ability.getDouble("damage", 5.0) * gp.getDamageMult();
        double launch     = ability.getDouble("launch-velocity", 1.0);
        double burnRadius = ability.getDouble("burn-radius", 3.0);
        int    fireTicks  = ability.getInt("fire-ticks", 40);

        Vector vel = player.getLocation().getDirection().multiply(0.5);
        vel.setY(launch);
        player.setVelocity(vel);

        for (UUID uuid : game.getPlayers().keySet()) {
            if (uuid.equals(player.getUniqueId())) continue;
            GamePlayer tGp = game.getGamePlayer(uuid);
            if (tGp == null || !tGp.isAlive()) continue;
            Player target = Bukkit.getPlayer(uuid);
            if (target == null) continue;
            if (target.getLocation().distance(player.getLocation()) <= burnRadius) {
                target.damage(damage * tGp.getDamageTakenMult(), player);
                target.setFireTicks(fireTicks);
            }
        }

        player.getWorld().spawnParticle(Particle.FLAME, player.getLocation(), 30, 0.5, 0.3, 0.5, 0.1);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_BLAZE_HURT, 1f, 0.8f);
        return true;
    }

    // ── Inferno (Ultimate) ────────────────────────────────────────────────────
    private static boolean inferno(SmashMons plugin, Player player, Game game,
                                   GamePlayer gp, KitAbility ability) {
        int    count     = ability.getInt("fireball-count", 12);
        double damage    = ability.getDouble("damage", 6.0) * gp.getDamageMult();
        int    fireTicks = ability.getInt("fire-ticks", 80);
        Location center  = player.getLocation().clone();

        for (int i = 0; i < count; i++) {
            double angle = (2 * Math.PI / count) * i;
            final Vector dir = new Vector(Math.cos(angle) * 1.5, 0.2, Math.sin(angle) * 1.5);

            new BukkitRunnable() {
                @Override public void run() {
                    SmallFireball fb = (SmallFireball) center.getWorld()
                            .spawnEntity(center.clone().add(0, 1, 0), EntityType.SMALL_FIREBALL);
                    fb.setShooter(player);
                    fb.setIsIncendiary(false);
                    fb.setYield(0);
                    fb.setVelocity(dir);
                    fb.setMetadata("smashmons_fireball", new FixedMetadataValue(plugin,
                            player.getUniqueId() + "," + damage + "," + fireTicks));
                }
            }.runTaskLater(plugin, (long) i * 2);
        }

        center.getWorld().spawnParticle(Particle.EXPLOSION, center, 3, 0.5, 0.5, 0.5, 0.1);
        center.getWorld().playSound(center, Sound.ENTITY_BLAZE_DEATH, 1f, 0.7f);
        return true;
    }
}
