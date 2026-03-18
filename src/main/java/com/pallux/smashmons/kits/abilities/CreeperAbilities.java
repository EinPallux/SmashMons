package com.pallux.smashmons.kits.abilities;

import com.pallux.smashmons.SmashMons;
import com.pallux.smashmons.game.Game;
import com.pallux.smashmons.game.GamePlayer;
import com.pallux.smashmons.kits.KitAbility;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.UUID;

public class CreeperAbilities {

    private CreeperAbilities() {}

    public static boolean activate(SmashMons plugin, Player player, Game game,
                                   GamePlayer gp, KitAbility ability, String key) {
        return switch (key) {
            case "primary"   -> shockCharge(plugin, player, game, gp, ability);
            case "secondary" -> proximityMine(plugin, player, game, gp, ability);
            default -> false;
        };
    }

    public static boolean activateUltimate(SmashMons plugin, Player player, Game game,
                                           GamePlayer gp, KitAbility ability) {
        return supercharge(plugin, player, game, gp, ability);
    }

    // ── Shock Charge ──────────────────────────────────────────────────────────
    private static boolean shockCharge(SmashMons plugin, Player player, Game game,
                                       GamePlayer gp, KitAbility ability) {
        double damage   = ability.getDouble("damage", 5.0) * gp.getDamageMult();
        double dashVel  = ability.getDouble("dash-velocity", 1.4);
        int stunTicks   = ability.getInt("stun-ticks", 30);

        Vector dashDir = player.getLocation().getDirection().setY(0.3).normalize().multiply(dashVel);
        player.setVelocity(dashDir);

        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                ticks++;
                for (UUID uuid : game.getPlayers().keySet()) {
                    if (uuid.equals(player.getUniqueId())) continue;
                    GamePlayer tGp = game.getGamePlayer(uuid);
                    if (tGp == null || !tGp.isAlive()) continue;
                    Player target = Bukkit.getPlayer(uuid);
                    if (target == null) continue;
                    if (target.getLocation().distance(player.getLocation()) < 1.5) {
                        target.damage(damage * tGp.getDamageTakenMult(), player);
                        target.addPotionEffect(new PotionEffect(
                                PotionEffectType.SLOWNESS, stunTicks, 5, false, true, true));
                        target.addPotionEffect(new PotionEffect(
                                PotionEffectType.MINING_FATIGUE, stunTicks, 2, false, true, true));
                        target.setVelocity(dashDir.clone().multiply(0.5).setY(0.5));
                        target.getWorld().spawnParticle(Particle.CRIT, target.getLocation(),
                                15, 0.3, 0.3, 0.3, 0.1);
                        cancel();
                        return;
                    }
                }
                if (ticks > 10) cancel();
            }
        }.runTaskTimer(plugin, 2L, 1L);

        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_CREEPER_PRIMED, 1f, 1.5f);
        player.getWorld().spawnParticle(Particle.SMOKE, player.getLocation(), 10, 0.3, 0.1, 0.3, 0.05);
        return true;
    }

    // ── Proximity Mine ────────────────────────────────────────────────────────
    private static boolean proximityMine(SmashMons plugin, Player player, Game game,
                                         GamePlayer gp, KitAbility ability) {
        double damage        = ability.getDouble("damage", 7.0) * gp.getDamageMult();
        double triggerRadius = ability.getDouble("trigger-radius", 1.5);
        int lifetimeSecs     = ability.getInt("mine-lifetime-seconds", 30);

        Location mineLoc = player.getLocation().clone();

        new BukkitRunnable() {
            int ticks = 0;
            final int maxTicks = lifetimeSecs * 20;
            boolean triggered = false;

            @Override
            public void run() {
                if (ticks >= maxTicks || triggered) { cancel(); return; }
                ticks += 4;

                mineLoc.getWorld().spawnParticle(Particle.DUST,
                        mineLoc.clone().add(0, 0.05, 0), 3, 0.2, 0.02, 0.2, 0,
                        new Particle.DustOptions(Color.fromRGB(80, 200, 80), 0.8f));

                for (UUID uuid : game.getPlayers().keySet()) {
                    if (uuid.equals(player.getUniqueId())) continue;
                    GamePlayer tGp = game.getGamePlayer(uuid);
                    if (tGp == null || !tGp.isAlive()) continue;
                    Player target = Bukkit.getPlayer(uuid);
                    if (target == null) continue;
                    if (target.getLocation().distance(mineLoc) <= triggerRadius) {
                        triggered = true;
                        target.damage(damage * tGp.getDamageTakenMult(), player);
                        target.setVelocity(new Vector(0, 1.2, 0));
                        mineLoc.getWorld().createExplosion(mineLoc, 0f, false, false);
                        mineLoc.getWorld().spawnParticle(Particle.EXPLOSION, mineLoc,
                                3, 0.3, 0.3, 0.3, 0.1);
                        mineLoc.getWorld().playSound(mineLoc, Sound.ENTITY_CREEPER_HURT, 1f, 0.9f);
                        cancel();
                        return;
                    }
                }
            }
        }.runTaskTimer(plugin, 20L, 4L);

        // Use plugin message system instead of raw ChatColor
        plugin.getMessageManager().send(player, "mine-planted");
        player.getWorld().playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 0.5f);
        return true;
    }

    // ── Supercharge (Ultimate) ────────────────────────────────────────────────
    private static boolean supercharge(SmashMons plugin, Player player, Game game,
                                       GamePlayer gp, KitAbility ability) {
        int fuseTicks     = ability.getInt("fuse-ticks", 40);
        double damage     = ability.getDouble("damage", 14.0) * gp.getDamageMult();
        double explRadius = ability.getInt("explosion-radius", 6);
        double selfFactor = ability.getDouble("self-damage-factor", 0.2);

        player.addPotionEffect(new PotionEffect(
                PotionEffectType.GLOWING, fuseTicks, 0, false, false, false));
        player.addPotionEffect(new PotionEffect(
                PotionEffectType.SLOWNESS, fuseTicks, 3, false, true, true));

        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                ticks += 4;
                if (!player.isOnline() || game.getState() != com.pallux.smashmons.game.GameState.IN_ROUND) {
                    cancel(); return;
                }
                player.getWorld().spawnParticle(Particle.ELECTRIC_SPARK,
                        player.getLocation().add(0, 1, 0), 8, 0.4, 0.4, 0.4, 0.05);

                if (ticks >= fuseTicks) {
                    cancel();
                    Location loc = player.getLocation();

                    for (UUID uuid : game.getPlayers().keySet()) {
                        if (uuid.equals(player.getUniqueId())) continue;
                        GamePlayer tGp = game.getGamePlayer(uuid);
                        if (tGp == null || !tGp.isAlive()) continue;
                        Player target = Bukkit.getPlayer(uuid);
                        if (target == null) continue;
                        double dist = target.getLocation().distance(loc);
                        if (dist <= explRadius) {
                            double falloff = 1.0 - (dist / explRadius) * 0.5;
                            target.damage(damage * falloff * tGp.getDamageTakenMult(), player);
                            target.setVelocity(target.getLocation().subtract(loc).toVector()
                                    .normalize().multiply(2.0).setY(1.0));
                        }
                    }

                    // Self-damage: bypass GameListener by marking the player as the source
                    // GameListener blocks attacker==victim for projectiles, but player.damage(x)
                    // with no entity source still goes through onEnvironmentDamage.
                    // We mark them temporarily immune to let it through.
                    double selfDmg = damage * selfFactor;
                    if (selfDmg > 0 && player.getHealth() > selfDmg) {
                        player.setHealth(Math.max(0.5, player.getHealth() - selfDmg));
                    }

                    loc.getWorld().createExplosion(loc, 0f, false, false);
                    loc.getWorld().spawnParticle(Particle.EXPLOSION, loc, 8, 1, 1, 1, 0.2);
                    loc.getWorld().playSound(loc, Sound.ENTITY_CREEPER_DEATH, 1f, 0.6f);
                }
            }
        }.runTaskTimer(plugin, 0L, 4L);

        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_CREEPER_PRIMED, 1f, 0.6f);
        return true;
    }
}
