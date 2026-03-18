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

import java.util.*;

public class PigAbilities {

    private PigAbilities() {}

    public static boolean activate(SmashMons plugin, Player player, Game game,
                                   GamePlayer gp, KitAbility ability, String key) {
        return switch (key) {
            case "primary"   -> bouncyBacon(plugin, player, game, gp, ability);
            case "secondary" -> pigplosion(plugin, player, game, gp, ability);
            default -> false;
        };
    }

    public static boolean activateUltimate(SmashMons plugin, Player player, Game game,
                                           GamePlayer gp, KitAbility ability) {
        return baconBonanza(plugin, player, game, gp, ability);
    }

    // ── Bouncy Bacon ──────────────────────────────────────────────────────────
    private static boolean bouncyBacon(SmashMons plugin, Player player, Game game,
                                       GamePlayer gp, KitAbility ability) {
        int maxBounces   = ability.getInt("bounces", 5);
        double baseDmg   = ability.getDouble("damage", 4.0) * gp.getDamageMult();
        double bounceMult = ability.getDouble("bounce-damage-multiplier", 0.8);

        Item baconItem = player.getWorld().dropItem(
                player.getEyeLocation(),
                new org.bukkit.inventory.ItemStack(Material.PORKCHOP));
        baconItem.setPickupDelay(Integer.MAX_VALUE);
        baconItem.setVelocity(player.getLocation().getDirection().multiply(1.5));
        baconItem.setMetadata("bouncy_bacon",
                new FixedMetadataValue(plugin, player.getUniqueId().toString()));

        new BukkitRunnable() {
            int bounces = 0;
            double currentDamage = baseDmg;
            double prevVelY = baconItem.getVelocity().getY();
            int lifetime = 0;
            // Short cooldown so a single landing doesn't count as multiple bounces
            int bounceCooldown = 0;
            final Set<UUID> recentlyHit = new HashSet<>();
            int hitCooldown = 0;

            @Override
            public void run() {
                lifetime++;
                // Remove after 10 seconds or bounce limit
                if (!baconItem.isValid() || bounces >= maxBounces || lifetime > 200) {
                    if (baconItem.isValid()) baconItem.remove();
                    cancel();
                    return;
                }

                if (hitCooldown > 0) hitCooldown--;
                else recentlyHit.clear();
                if (bounceCooldown > 0) bounceCooldown--;

                Location loc = baconItem.getLocation();
                Vector vel   = baconItem.getVelocity();

                // ── Player hit ────────────────────────────────────────────
                for (UUID uuid : game.getPlayers().keySet()) {
                    if (uuid.equals(player.getUniqueId())) continue;
                    if (recentlyHit.contains(uuid)) continue;
                    GamePlayer targetGp = game.getGamePlayer(uuid);
                    if (targetGp == null || !targetGp.isAlive()) continue;
                    Player target = Bukkit.getPlayer(uuid);
                    if (target == null) continue;

                    if (target.getLocation().distance(loc) < 1.0) {
                        target.damage(currentDamage * targetGp.getDamageTakenMult(), player);
                        currentDamage *= bounceMult;
                        bounces++;
                        recentlyHit.add(uuid);
                        hitCooldown = 10;

                        // Reflect away from the hit target
                        Vector away = loc.clone().subtract(target.getLocation()).toVector();
                        if (away.lengthSquared() < 0.001) away = new Vector(1, 0, 0);
                        baconItem.setVelocity(away.normalize().multiply(1.2).setY(0.6));

                        loc.getWorld().spawnParticle(Particle.CRIT, loc, 10, 0.3, 0.3, 0.3, 0.05);
                        loc.getWorld().playSound(loc, Sound.ENTITY_PIG_HURT, 0.7f, 1.3f);
                        return;
                    }
                }

                // ── Ground bounce ─────────────────────────────────────────
                // Triggered when Y velocity was falling last tick and is now
                // near-zero or rising (item hit the floor and physics bounced it).
                double curVelY = vel.getY();
                if (bounceCooldown == 0 && prevVelY < -0.05 && curVelY > prevVelY + 0.1) {
                    bounces++;
                    currentDamage *= bounceMult;
                    bounceCooldown = 5; // 5 ticks grace so one landing = one bounce
                    loc.getWorld().spawnParticle(Particle.SMOKE, loc, 5, 0.15, 0.05, 0.15, 0.01);
                    loc.getWorld().playSound(loc, Sound.BLOCK_GRASS_STEP, 0.5f, 0.8f);
                }
                prevVelY = curVelY;
            }
        }.runTaskTimer(plugin, 1L, 1L);

        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_PIG_AMBIENT, 1f, 1.5f);
        return true;
    }

    // ── Pigplosion ────────────────────────────────────────────────────────────
    private static boolean pigplosion(SmashMons plugin, Player player, Game game,
                                      GamePlayer gp, KitAbility ability) {
        double explosionDmg = ability.getDouble("explosion-damage", 8.0) * gp.getDamageMult();
        int fuseSecs        = ability.getInt("fuse-seconds", 4);
        double pigSpeed     = ability.getDouble("pig-speed", 0.6);

        // Spawn slightly above the player so it doesn't get stuck in the ground
        Location spawnLoc = player.getLocation().clone().add(0, 0.1, 0);

        Entity raw = spawnLoc.getWorld().spawnEntity(spawnLoc, EntityType.PIG);
        if (!(raw instanceof Pig pig)) {
            // Spawn failed — silently return false so cooldown is NOT consumed
            return false;
        }

        // Make it a baby pig (Pig implements Ageable; negative age = baby, locked)
        pig.setAge(-24000);
        pig.setAgeLock(true);
        pig.setAI(true);
        pig.setInvulnerable(true);
        pig.setSilent(false);
        pig.setMetadata("smashmons_pigplosion",
                new FixedMetadataValue(plugin, player.getUniqueId().toString()));

        // Speed attribute
        var speedAttr = pig.getAttribute(org.bukkit.attribute.Attribute.MOVEMENT_SPEED);
        if (speedAttr != null) speedAttr.setBaseValue(pigSpeed);

        new BukkitRunnable() {
            int ticks = 0;
            final int fuseTicks = fuseSecs * 20;

            @Override
            public void run() {
                if (!pig.isValid()) { cancel(); return; }
                ticks++;

                // Chase nearest enemy every tick
                Player nearest = getNearestEnemy(player, game, pig.getLocation(), 30);
                if (nearest != null) {
                    Vector dir = nearest.getLocation().subtract(pig.getLocation())
                            .toVector();
                    if (dir.lengthSquared() > 0.001) {
                        dir = dir.normalize().multiply(pigSpeed);
                        pig.setVelocity(dir);
                    }
                }

                pig.getWorld().spawnParticle(Particle.SMOKE, pig.getLocation().add(0, 0.5, 0),
                        3, 0.1, 0.1, 0.1, 0.01);

                // Flash particle near detonation
                if (ticks >= fuseTicks - 10) {
                    pig.getWorld().spawnParticle(Particle.ELECTRIC_SPARK,
                            pig.getLocation().add(0, 0.5, 0), 4, 0.2, 0.2, 0.2, 0.05);
                }

                if (ticks >= fuseTicks) {
                    cancel();
                    Location loc = pig.getLocation().clone();
                    pig.remove();
                    for (UUID uuid : game.getPlayers().keySet()) {
                        if (uuid.equals(player.getUniqueId())) continue;
                        GamePlayer tGp = game.getGamePlayer(uuid);
                        if (tGp == null || !tGp.isAlive()) continue;
                        Player target = Bukkit.getPlayer(uuid);
                        if (target == null) continue;
                        if (target.getLocation().distance(loc) <= 4.0) {
                            target.damage(explosionDmg * tGp.getDamageTakenMult(), player);
                            Vector kb = target.getLocation().subtract(loc).toVector();
                            if (kb.lengthSquared() > 0.001)
                                target.setVelocity(kb.normalize().multiply(1.5).setY(0.8));
                        }
                    }
                    loc.getWorld().createExplosion(loc, 0f, false, false);
                    loc.getWorld().spawnParticle(Particle.EXPLOSION, loc, 5, 0.5, 0.5, 0.5, 0.1);
                    loc.getWorld().playSound(loc, Sound.ENTITY_CREEPER_HURT, 1f, 0.9f);
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);

        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_PIG_HURT, 1f, 0.8f);
        return true;
    }

    // ── Bacon Bonanza (Ultimate) ──────────────────────────────────────────────
    private static boolean baconBonanza(SmashMons plugin, Player player, Game game,
                                        GamePlayer gp, KitAbility ability) {
        int durationSecs    = ability.getInt("duration-seconds", 5);
        int radius          = ability.getInt("radius", 10);
        double damagePerHit = ability.getDouble("damage-per-hit", 6.0) * gp.getDamageMult();
        double explRadius   = ability.getDouble("explosion-radius", 2.5);
        int slowDur         = ability.getInt("slow-duration-ticks", 80);
        int slowAmp         = ability.getInt("slow-amplifier", 1);
        Location center     = player.getLocation().clone();
        List<Item> activeItems = Collections.synchronizedList(new ArrayList<>());

        new BukkitRunnable() {
            int ticks = 0;
            final int maxTicks = durationSecs * 20;
            final Random random = new Random();

            @Override
            public void run() {
                if (ticks >= maxTicks) {
                    // Final sweep — explode any remaining items
                    activeItems.forEach(i -> {
                        if (i.isValid()) {
                            explodeBacon(plugin, player, game, i.getLocation(), damagePerHit, explRadius, slowDur, slowAmp);
                            i.remove();
                        }
                    });
                    activeItems.clear();
                    cancel();
                    return;
                }
                ticks += 4;

                for (int i = 0; i < 3; i++) {
                    double x = center.getX() + (random.nextDouble() * 2 - 1) * radius;
                    double z = center.getZ() + (random.nextDouble() * 2 - 1) * radius;
                    Location spawnLoc = new Location(center.getWorld(), x, center.getY() + 12, z);

                    Item pork = center.getWorld().dropItem(spawnLoc,
                            new org.bukkit.inventory.ItemStack(Material.PORKCHOP));
                    pork.setPickupDelay(Integer.MAX_VALUE);
                    pork.setVelocity(new Vector(0, -1.8, 0));
                    pork.setMetadata("bacon_bonanza", new FixedMetadataValue(plugin, "true"));
                    activeItems.add(pork);

                    // Track each pork item — explode on ground impact or timeout
                    new BukkitRunnable() {
                        int porkLife = 0;
                        double prevVelY = -1.8;

                        @Override
                        public void run() {
                            porkLife += 2;
                            if (!pork.isValid()) { activeItems.remove(pork); cancel(); return; }

                            // Detect ground impact: Y velocity was negative, now near-zero or rising
                            double curVelY = pork.getVelocity().getY();
                            boolean hitGround = prevVelY < -0.05 && curVelY > prevVelY + 0.15;
                            prevVelY = curVelY;

                            // Also timeout at 3 seconds (60 ticks)
                            boolean timeout = porkLife >= 60;

                            if (hitGround || timeout) {
                                Location impactLoc = pork.getLocation().clone();
                                pork.remove();
                                activeItems.remove(pork);
                                cancel();
                                // Explode in area
                                explodeBacon(plugin, player, game, impactLoc,
                                        damagePerHit, explRadius, slowDur, slowAmp);
                            }
                        }
                    }.runTaskTimer(plugin, 1L, 2L);
                }

                center.getWorld().spawnParticle(Particle.ITEM, center.clone().add(0, 8, 0),
                        8, radius * 0.4, 1, radius * 0.4, 0.1,
                        new org.bukkit.inventory.ItemStack(Material.PORKCHOP));
                center.getWorld().playSound(center, Sound.ENTITY_PIG_AMBIENT, 0.5f, 1.2f);
            }
        }.runTaskTimer(plugin, 0L, 4L);

        return true;
    }

    /** Deals area damage + slowness + visual FX when a bacon porkchop impacts */
    private static void explodeBacon(SmashMons plugin, Player caster, Game game,
                                     Location loc, double damage, double radius,
                                     int slowDur, int slowAmp) {
        // Damage all enemies in radius
        for (UUID uuid : game.getPlayers().keySet()) {
            if (uuid.equals(caster.getUniqueId())) continue;
            GamePlayer tGp = game.getGamePlayer(uuid);
            if (tGp == null || !tGp.isAlive()) continue;
            Player target = Bukkit.getPlayer(uuid);
            if (target == null) continue;
            if (target.getLocation().distance(loc) <= radius) {
                target.damage(damage * tGp.getDamageTakenMult(), caster);
                target.addPotionEffect(new PotionEffect(
                        PotionEffectType.SLOWNESS, slowDur, slowAmp, true, true, true));
            }
        }
        // Visual: pink smoke + porkchop particles
        loc.getWorld().spawnParticle(Particle.ITEM, loc.clone().add(0, 0.3, 0),
                12, radius * 0.4, 0.3, radius * 0.4, 0.08,
                new org.bukkit.inventory.ItemStack(Material.PORKCHOP));
        loc.getWorld().spawnParticle(Particle.SMOKE, loc.clone().add(0, 0.2, 0),
                6, 0.3, 0.1, 0.3, 0.02);
        loc.getWorld().playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 0.6f, 1.4f);
    }

    // ── Utility ───────────────────────────────────────────────────────────────
    static Player getNearestEnemy(Player self, Game game, Location from, double maxDist) {
        Player nearest = null;
        double nearestDist = maxDist;
        for (UUID uuid : game.getPlayers().keySet()) {
            if (uuid.equals(self.getUniqueId())) continue;
            GamePlayer gp = game.getGamePlayer(uuid);
            if (gp == null || !gp.isAlive()) continue;
            Player p = Bukkit.getPlayer(uuid);
            if (p == null) continue;
            double dist = p.getLocation().distance(from);
            if (dist < nearestDist) { nearestDist = dist; nearest = p; }
        }
        return nearest;
    }
}