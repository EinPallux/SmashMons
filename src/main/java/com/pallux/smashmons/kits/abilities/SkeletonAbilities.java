package com.pallux.smashmons.kits.abilities;

import com.pallux.smashmons.SmashMons;
import com.pallux.smashmons.game.Game;
import com.pallux.smashmons.game.GamePlayer;
import com.pallux.smashmons.kits.KitAbility;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.Random;
import java.util.UUID;

public class SkeletonAbilities {

    private SkeletonAbilities() {}

    public static boolean activate(SmashMons plugin, Player player, Game game,
                                   GamePlayer gp, KitAbility ability, String key) {
        return switch (key) {
            case "primary" -> volley(plugin, player, game, gp, ability);
            case "secondary" -> boneShield(plugin, player, game, gp, ability);
            default -> false;
        };
    }

    public static boolean activateUltimate(SmashMons plugin, Player player, Game game,
                                           GamePlayer gp, KitAbility ability) {
        return rainOfBones(plugin, player, game, gp, ability);
    }

    // ── Volley ────────────────────────────────────────────────────────────────
    private static boolean volley(SmashMons plugin, Player player, Game game,
                                  GamePlayer gp, KitAbility ability) {
        int count = ability.getInt("arrow-count", 5);
        double damage = ability.getDouble("damage", 3.5) * gp.getDamageMult();
        double spread = ability.getDouble("spread", 0.15);
        Random rand = new Random();

        for (int i = 0; i < count; i++) {
            final int fi = i;
            new BukkitRunnable() {
                @Override
                public void run() {
                    Arrow arrow = player.launchProjectile(Arrow.class);
                    arrow.setDamage(damage);
                    arrow.setShooter(player);
                    Vector dir = player.getLocation().getDirection().clone();
                    dir.add(new Vector(
                            (rand.nextDouble() - 0.5) * spread,
                            (rand.nextDouble() - 0.5) * spread,
                            (rand.nextDouble() - 0.5) * spread)).normalize();
                    arrow.setVelocity(dir.multiply(2.5));
                    arrow.setPickupStatus(Arrow.PickupStatus.DISALLOWED);
                    arrow.setMetadata("smashmons_arrow", new org.bukkit.metadata.FixedMetadataValue(plugin, player.getUniqueId().toString()));
                }
            }.runTaskLater(plugin, fi * 2L);
        }

        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ARROW_SHOOT, 1f, 1f);
        return true;
    }

    // ── Bone Shield ───────────────────────────────────────────────────────────
    private static boolean boneShield(SmashMons plugin, Player player, Game game,
                                      GamePlayer gp, KitAbility ability) {
        int absorption = ability.getInt("absorption-hearts", 6);
        int duration = ability.getInt("duration-ticks", 100);

        player.addPotionEffect(new PotionEffect(
                PotionEffectType.ABSORPTION, duration, absorption / 4, false, true, true));

        player.getWorld().spawnParticle(Particle.CRIT, player.getLocation().add(0, 1, 0),
                20, 0.5, 0.5, 0.5, 0.1);
        player.getWorld().playSound(player.getLocation(), Sound.ITEM_SHIELD_BLOCK, 1f, 0.8f);
        return true;
    }

    // ── Rain of Bones (Ultimate) ──────────────────────────────────────────────
    private static boolean rainOfBones(SmashMons plugin, Player player, Game game,
                                       GamePlayer gp, KitAbility ability) {
        int durationSecs = ability.getInt("duration-seconds", 4);
        int radius = ability.getInt("radius", 8);
        int arrowsPerTick = ability.getInt("arrows-per-tick", 2);
        double damage = ability.getDouble("damage", 3.0) * gp.getDamageMult();
        Location center = player.getLocation();
        Random rand = new Random();

        new BukkitRunnable() {
            int ticks = 0;
            final int maxTicks = durationSecs * 20;

            @Override
            public void run() {
                if (ticks >= maxTicks) { cancel(); return; }
                ticks += 5;

                for (int i = 0; i < arrowsPerTick; i++) {
                    double x = center.getX() + (rand.nextDouble() * 2 - 1) * radius;
                    double z = center.getZ() + (rand.nextDouble() * 2 - 1) * radius;
                    Location spawnLoc = new Location(center.getWorld(), x, center.getY() + 15, z);

                    Entity spawned = center.getWorld().spawnEntity(spawnLoc, EntityType.ARROW);
                    if (!(spawned instanceof Arrow arrow)) continue;
                    arrow.setDamage(damage);
                    arrow.setShooter(player);
                    arrow.setVelocity(new Vector(0, -3, 0));
                    arrow.setPickupStatus(Arrow.PickupStatus.DISALLOWED);
                }

                center.getWorld().playSound(center, Sound.ENTITY_ARROW_SHOOT, 0.5f, 0.7f);
            }
        }.runTaskTimer(plugin, 0L, 5L);

        return true;
    }
}
