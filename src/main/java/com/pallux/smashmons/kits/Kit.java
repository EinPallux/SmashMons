package com.pallux.smashmons.kits;

import org.bukkit.Material;
import org.bukkit.entity.EntityType;

import java.util.List;

/**
 * Immutable data model for a SmashMons Kit loaded from kits.yml.
 */
public class Kit {

    public enum EnergyType { COOLDOWN, ENERGY }

    private final String id;
    private final String displayName;
    private final List<String> lore;
    private final Material material;
    private final EntityType mobType;
    private final double health;
    private final double speedBonus;
    private final boolean unlockedByDefault;
    private final long cost;
    private final EnergyType energyType;
    private final int energyRegenPerSecond;

    // Abilities
    private final KitAbility primaryAbility;
    private final KitAbility secondaryAbility;
    private final KitAbility ultimateAbility;

    public Kit(String id, String displayName, List<String> lore, Material material, EntityType mobType,
               double health, double speedBonus, boolean unlockedByDefault, long cost,
               EnergyType energyType, int energyRegenPerSecond,
               KitAbility primaryAbility, KitAbility secondaryAbility, KitAbility ultimateAbility) {
        this.id = id;
        this.displayName = displayName;
        this.lore = lore;
        this.material = material;
        this.mobType = mobType;
        this.health = health;
        this.speedBonus = speedBonus;
        this.unlockedByDefault = unlockedByDefault;
        this.cost = cost;
        this.energyType = energyType;
        this.energyRegenPerSecond = energyRegenPerSecond;
        this.primaryAbility = primaryAbility;
        this.secondaryAbility = secondaryAbility;
        this.ultimateAbility = ultimateAbility;
    }

    public String getId() { return id; }
    public String getDisplayName() { return displayName; }
    public List<String> getLore() { return lore; }
    public Material getMaterial() { return material; }
    public EntityType getMobType() { return mobType; }
    public double getHealth() { return health; }
    public double getSpeedBonus() { return speedBonus; }
    public boolean isUnlockedByDefault() { return unlockedByDefault; }
    public long getCost() { return cost; }
    public EnergyType getEnergyType() { return energyType; }
    public int getEnergyRegenPerSecond() { return energyRegenPerSecond; }
    public KitAbility getPrimaryAbility() { return primaryAbility; }
    public KitAbility getSecondaryAbility() { return secondaryAbility; }
    public KitAbility getUltimateAbility() { return ultimateAbility; }
    public boolean isEnergy() { return energyType == EnergyType.ENERGY; }
}
