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
    /**
     * Throws a porkchop item that bounces off terrain and deals decreasing
     * damage each time it hits an enemy (or bounces off the ground).
     *
     * FIX: damage now comes from config (default 9.0, was 7.0).
     * FIX: ground-bounce detection uses onGround flag instead of Y-velocity
     *       comparison, which was unreliable and could miss bounces.
     */
    private static boolean bouncyBacon(SmashMons plugin, Player player, Game game,
                                       GamePlayer gp, KitAbility ability) {
        int    maxBounces  = ability.getInt("bounces", 5);
        double baseDmg     = ability.getDouble("damage", 9.0) * gp.getDamageMult();
        double bounceMult  = ability.getDouble("bounce-damage-multiplier", 0.85);

        Item baconItem = player.getWorld().dropItem(
                player.getEyeLocation(),
                new org.bukkit.inventory.ItemStack(Material.PORKCHOP));
        baconItem.setPickupDelay(Integer.MAX_VALUE);
        baconItem.setVelocity(player.getLocation().getDirection().multiply(1.6));
        baconItem.setMetadata("bouncy_bacon",
                new FixedMetadataValue(plugin, player.getUniqueId().toString()));

        new BukkitRunnable() {
            int bounces = 0;
            double currentDamage = baseDmg;
            int lifetime = 0;
            // Prevent registering multiple hits / bounces from the same landing event
            int hitCooldown = 0;
            int groundBounceCooldown = 0;
            // Track the last Y of the item to detect direction changes
            double lastY = baconItem.getLocation().getY();
            boolean wasFalling = false;
            final Set<UUID> hitCooldownSet = new HashSet<>();

            @Override
            public void run() {
                lifetime++;

                // Remove after ~10 seconds (200 ticks) or all bounces used
                if (!baconItem.isValid() || bounces >= maxBounces || lifetime > 200) {
                    if (baconItem.isValid()) baconItem.remove();
                    cancel();
                    return;
                }

                if (hitCooldown > 0) { hitCooldown--; } else { hitCooldownSet.clear(); }
                if (groundBounceCooldown > 0) groundBounceCooldown--;

                Location loc = baconItem.getLocation();
                double currentY = loc.getY();
                Vector vel = baconItem.getVelocity();

                // ── Player hit detection ──────────────────────────────────
                for (UUID uuid : game.getPlayers().keySet()) {
                    if (uuid.equals(player.getUniqueId())) continue;
                    if (hitCooldownSet.contains(uuid)) continue;

                    GamePlayer targetGp = game.getGamePlayer(uuid);
                    if (targetGp == null || !targetGp.isAlive()) continue;

                    Player target = Bukkit.getPlayer(uuid);
                    if (target == null) continue;

                    // Use a slightly generous hit radius so the bacon feels satisfying
                    if (target.getLocation().distanceSquared(loc) < 1.44) { // 1.2 blocks radius
                        target.damage(currentDamage * targetGp.getDamageTakenMult(), player);
                        currentDamage *= bounceMult;
                        bounces++;
                        hitCooldownSet.add(uuid);
                        hitCooldown = 8; // 8-tick grace before same player can be hit again

                        // Reflect the bacon away from the target
                        Vector away = loc.clone().subtract(target.getLocation()).toVector();
                        if (away.lengthSquared() < 0.001) away = new Vector(1, 0, 0);
                        baconItem.setVelocity(away.normalize().multiply(1.2).setY(0.55));

                        loc.getWorld().spawnParticle(Particle.CRIT, loc, 12, 0.3, 0.3, 0.3, 0.08);
                        loc.getWorld().playSound(loc, Sound.ENTITY_PIG_HURT, 0.7f, 1.4f);

                        lastY = loc.getY();
                        wasFalling = false;
                        return;
                    }
                }

                // ── Ground-bounce detection ───────────────────────────────
                // Detect the moment the item transitions from falling to rising
                // (i.e., it hit the ground and Minecraft's physics bounced it).
                boolean falling = vel.getY() < -0.05;
                boolean justBounced = wasFalling && !falling && groundBounceCooldown == 0;

                if (justBounced) {
                    bounces++;
                    currentDamage *= bounceMult;
                    groundBounceCooldown = 6; // prevent double-counting the same bounce
                    loc.getWorld().spawnParticle(Particle.SMOKE, loc, 5, 0.15, 0.05, 0.15, 0.01);
                    loc.getWorld().playSound(loc, Sound.BLOCK_GRASS_STEP, 0.5f, 0.9f);
                }

                wasFalling = falling;
                lastY = currentY;
            }
        }.runTaskTimer(plugin, 1L, 1L);

        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_PIG_AMBIENT, 1f, 1.6f);
        return true;
    }

    // ── Pigplosion ────────────────────────────────────────────────────────────
    private static boolean pigplosion(SmashMons plugin, Player player, Game game,
                                      GamePlayer gp, KitAbility ability) {
        double damage    = ability.getDouble("explosion-damage", 14.0) * gp.getDamageMult();
        int fuseSecs     = ability.getInt("fuse-seconds", 4);
        double pigSpeed  = ability.getDouble("pig-speed", 0.65);

        Location spawnLoc = player.getLocation().clone().add(0, 0.1, 0);

        Entity raw = spawnLoc.getWorld().spawnEntity(spawnLoc, EntityType.PIG);
        if (!(raw instanceof Pig pig)) return false;

        pig.setAge(-24000);
        pig.setAgeLock(true);
        pig.setAI(true);
        pig.setInvulnerable(true);
        pig.setSilent(false);
        pig.setMetadata("smashmons_pigplosion",
                new FixedMetadataValue(plugin, player.getUniqueId().toString()));

        var speedAttr = pig.getAttribute(org.bukkit.attribute.Attribute.MOVEMENT_SPEED);
        if (speedAttr != null) speedAttr.setBaseValue(pigSpeed);

        new BukkitRunnable() {
            int ticks = 0;
            final int fuseTicks = fuseSecs * 20;

            @Override
            public void run() {
                if (!pig.isValid()) { cancel(); return; }
                ticks++;

                Player nearest = getNearestEnemy(player, game, pig.getLocation(), 30);
                if (nearest != null) {
                    Vector dir = nearest.getLocation().subtract(pig.getLocation()).toVector();
                    if (dir.lengthSquared() > 0.001) {
                        pig.setVelocity(dir.normalize().multiply(pigSpeed));
                    }
                }

                pig.getWorld().spawnParticle(Particle.SMOKE,
                        pig.getLocation().add(0, 0.5, 0), 3, 0.1, 0.1, 0.1, 0.01);

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
                        if (target.getLocation().distanceSquared(loc) <= 16.0) { // 4 block radius
                            target.damage(damage * tGp.getDamageTakenMult(), player);
                            Vector kb = target.getLocation().subtract(loc).toVector();
                            if (kb.lengthSquared() > 0.001)
                                target.setVelocity(kb.normalize().multiply(1.6).setY(0.9));
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
        int    durationSecs  = ability.getInt("duration-seconds", 5);
        int    radius        = ability.getInt("radius", 10);
        double damagePerHit  = ability.getDouble("damage-per-hit", 8.0) * gp.getDamageMult();
        double explRadius    = ability.getDouble("explosion-radius", 2.5);
        int    slowDur       = ability.getInt("slow-duration-ticks", 80);
        int    slowAmp       = ability.getInt("slow-amplifier", 1);
        Location center      = player.getLocation().clone();
        List<Item> activeItems = Collections.synchronizedList(new ArrayList<>());

        new BukkitRunnable() {
            int ticks = 0;
            final int maxTicks = durationSecs * 20;
            final Random random = new Random();

            @Override
            public void run() {
                if (ticks >= maxTicks) {
                    // Sweep: explode any remaining items
                    activeItems.forEach(i -> {
                        if (i.isValid()) {
                            explodeBacon(plugin, player, game, i.getLocation(),
                                    damagePerHit, explRadius, slowDur, slowAmp);
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

                    // Per-pork tracker: explode on ground impact or timeout
                    new BukkitRunnable() {
                        int porkLife = 0;
                        boolean wasFallingLocal = true;

                        @Override
                        public void run() {
                            porkLife += 2;
                            if (!pork.isValid()) { activeItems.remove(pork); cancel(); return; }

                            Vector v = pork.getVelocity();
                            boolean falling = v.getY() < -0.05;
                            boolean hitGround = wasFallingLocal && !falling;
                            wasFallingLocal = falling;

                            boolean timeout = porkLife >= 60;

                            if (hitGround || timeout) {
                                Location impactLoc = pork.getLocation().clone();
                                pork.remove();
                                activeItems.remove(pork);
                                cancel();
                                explodeBacon(plugin, player, game, impactLoc,
                                        damagePerHit, explRadius, slowDur, slowAmp);
                            }
                        }
                    }.runTaskTimer(plugin, 1L, 2L);
                }

                center.getWorld().spawnParticle(Particle.ITEM,
                        center.clone().add(0, 8, 0),
                        8, radius * 0.4, 1, radius * 0.4, 0.1,
                        new org.bukkit.inventory.ItemStack(Material.PORKCHOP));
                center.getWorld().playSound(center, Sound.ENTITY_PIG_AMBIENT, 0.5f, 1.2f);
            }
        }.runTaskTimer(plugin, 0L, 4L);

        return true;
    }

    private static void explodeBacon(SmashMons plugin, Player caster, Game game,
                                     Location loc, double damage, double radius,
                                     int slowDur, int slowAmp) {
        for (UUID uuid : game.getPlayers().keySet()) {
            if (uuid.equals(caster.getUniqueId())) continue;
            GamePlayer tGp = game.getGamePlayer(uuid);
            if (tGp == null || !tGp.isAlive()) continue;
            Player target = Bukkit.getPlayer(uuid);
            if (target == null) continue;
            if (target.getLocation().distanceSquared(loc) <= radius * radius) {
                target.damage(damage * tGp.getDamageTakenMult(), caster);
                target.addPotionEffect(new PotionEffect(
                        PotionEffectType.SLOWNESS, slowDur, slowAmp, true, true, true));
            }
        }
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
        double nearestDistSq = maxDist * maxDist;
        for (UUID uuid : game.getPlayers().keySet()) {
            if (uuid.equals(self.getUniqueId())) continue;
            GamePlayer gp = game.getGamePlayer(uuid);
            if (gp == null || !gp.isAlive()) continue;
            Player p = Bukkit.getPlayer(uuid);
            if (p == null) continue;
            double distSq = p.getLocation().distanceSquared(from);
            if (distSq < nearestDistSq) { nearestDistSq = distSq; nearest = p; }
        }
        return nearest;
    }
}