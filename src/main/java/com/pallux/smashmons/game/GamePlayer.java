package com.pallux.smashmons.game;

import com.pallux.smashmons.augments.Augment;
import com.pallux.smashmons.kits.Kit;
import org.bukkit.boss.BossBar;

import java.util.*;

/**
 * Tracks per-player state during an active game session.
 */
public class GamePlayer {

    private final UUID uuid;
    private Kit kit;
    private int points = 0;
    private int roundKills = 0;
    private boolean alive = true;
    private boolean hasUltimateCrystal = false;
    private final List<Augment> augments = new ArrayList<>();
    private final Map<String, Long> cooldowns = new HashMap<>();
    private double energy = 100.0;
    private boolean finishedRound = false;

    // Augment stacking values computed each round
    private double damageMult = 1.0;
    private double damageTakenMult = 1.0;
    private double knockbackReduction = 0.0;
    private double cooldownReduction = 0.0;
    private double energyRegenBonus = 0.0;
    private double extraHealth = 0.0;
    private double doubleJumpCooldownReduction = 0.0;
    private boolean finalStandTriggered = false;
    private int berserkerKills = 0;

    // Boss bar for energy kits
    private BossBar energyBar;

    public GamePlayer(UUID uuid) {
        this.uuid = uuid;
    }

    // ── Getters / Setters ──────────────────────────────────────────────────────
    public UUID getUuid() { return uuid; }
    public Kit getKit() { return kit; }
    public void setKit(Kit kit) { this.kit = kit; }

    public int getPoints() { return points; }
    public void addPoints(int p) { points += p; }

    public int getRoundKills() { return roundKills; }
    public void addRoundKill() { roundKills++; berserkerKills++; }
    public void resetRoundKills() { roundKills = 0; }

    public boolean isAlive() { return alive; }
    public void setAlive(boolean alive) { this.alive = alive; }

    public boolean hasUltimateCrystal() { return hasUltimateCrystal; }
    public void setHasUltimateCrystal(boolean val) { this.hasUltimateCrystal = val; }

    public List<Augment> getAugments() { return augments; }
    public void addAugment(Augment augment) { augments.add(augment); }
    public boolean hasAugment(String id) {
        return augments.stream().anyMatch(a -> a.getId().equalsIgnoreCase(id));
    }
    public Set<String> getAugmentIds() {
        Set<String> ids = new HashSet<>();
        for (Augment a : augments) ids.add(a.getId());
        return ids;
    }

    // ── Cooldowns ──────────────────────────────────────────────────────────────
    public boolean isOnCooldown(String abilityKey) {
        Long ready = cooldowns.get(abilityKey);
        return ready != null && System.currentTimeMillis() < ready;
    }
    public long getRemainingCooldown(String abilityKey) {
        Long ready = cooldowns.get(abilityKey);
        if (ready == null) return 0;
        return Math.max(0, ready - System.currentTimeMillis());
    }
    public void setCooldown(String abilityKey, int seconds) {
        double reduction = Math.min(0.8, cooldownReduction); // max 80% reduction
        long ms = (long) (seconds * 1000L * (1.0 - reduction));
        cooldowns.put(abilityKey, System.currentTimeMillis() + ms);
    }
    public void clearCooldowns() { cooldowns.clear(); }

    // ── Energy ─────────────────────────────────────────────────────────────────
    public double getEnergy() { return energy; }
    public void setEnergy(double energy) { this.energy = Math.max(0, Math.min(100, energy)); }
    public boolean consumeEnergy(double amount) {
        if (energy < amount) return false;
        energy -= amount;
        return true;
    }

    // ── Energy BossBar ─────────────────────────────────────────────────────────
    public BossBar getEnergyBar() { return energyBar; }
    public void setEnergyBar(BossBar energyBar) { this.energyBar = energyBar; }

    // ── Round state ─────────────────────────────────────────────────────────────
    public boolean isFinishedRound() { return finishedRound; }
    public void setFinishedRound(boolean finishedRound) { this.finishedRound = finishedRound; }

    // ── Augment stats ──────────────────────────────────────────────────────────
    public void recomputeAugmentStats() {
        damageMult = 1.0;
        damageTakenMult = 1.0;
        knockbackReduction = 0.0;
        cooldownReduction = 0.0;
        energyRegenBonus = 0.0;
        extraHealth = 0.0;
        doubleJumpCooldownReduction = 0.0;

        for (Augment aug : augments) {
            switch (aug.getId()) {
                case "iron_will" -> extraHealth += aug.getDouble("extra-health", 4.0);
                case "swift_strikes" -> damageMult *= aug.getDouble("damage-multiplier", 1.15);
                case "glass_cannon" -> {
                    damageMult *= aug.getDouble("damage-multiplier", 1.30);
                    damageTakenMult *= aug.getDouble("damage-taken-multiplier", 1.15);
                }
                case "wind_resistance" -> knockbackReduction += aug.getDouble("knockback-reduction", 0.25);
                case "lightning_reflexes" -> cooldownReduction += aug.getDouble("cooldown-reduction", 0.20);
                case "energy_surge" -> energyRegenBonus += aug.getDouble("energy-regen-bonus", 0.25);
                case "double_jump_master" -> doubleJumpCooldownReduction += aug.getDouble("double-jump-cooldown-reduction", 1.0);
                case "berserker" -> damageMult *= (1.0 + aug.getDouble("damage-bonus-per-kill", 0.05) * berserkerKills);
            }
        }
    }

    public double getDamageMult() { return damageMult; }
    public double getDamageTakenMult() { return damageTakenMult; }
    public double getKnockbackReduction() { return knockbackReduction; }
    public double getCooldownReduction() { return cooldownReduction; }
    public double getEnergyRegenBonus() { return energyRegenBonus; }
    public double getExtraHealth() { return extraHealth; }
    public double getDoubleJumpCooldownReduction() { return doubleJumpCooldownReduction; }
    public boolean isFinalStandTriggered() { return finalStandTriggered; }
    public void setFinalStandTriggered(boolean finalStandTriggered) { this.finalStandTriggered = finalStandTriggered; }
}
