package com.pallux.smashmons.kits;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

import java.util.HashMap;
import java.util.Map;

/**
 * Data class for a single kit ability loaded from kits.yml.
 * Extra config values are stored in a generic map for kit-specific handlers.
 */
public class KitAbility {

    public enum AbilitySlot { PRIMARY, SECONDARY, ULTIMATE }

    private final String name;
    private final String description;
    private final Material material;
    private final int cooldownSeconds;
    private final int energyCost;
    private final AbilitySlot slot;
    private final Map<String, Object> data = new HashMap<>();

    public KitAbility(String name, String description, Material material,
                      int cooldownSeconds, int energyCost, AbilitySlot slot,
                      ConfigurationSection section) {
        this.name = name;
        this.description = description;
        this.material = material;
        this.cooldownSeconds = cooldownSeconds;
        this.energyCost = energyCost;
        this.slot = slot;

        // Load all extra keys generically
        if (section != null) {
            for (String key : section.getKeys(false)) {
                data.put(key, section.get(key));
            }
        }
    }

    public String getName() { return name; }
    public String getDescription() { return description; }
    public Material getMaterial() { return material; }
    public int getCooldownSeconds() { return cooldownSeconds; }
    public int getEnergyCost() { return energyCost; }
    public AbilitySlot getSlot() { return slot; }

    public double getDouble(String key, double def) {
        Object val = data.get(key);
        if (val instanceof Number n) return n.doubleValue();
        return def;
    }

    public int getInt(String key, int def) {
        Object val = data.get(key);
        if (val instanceof Number n) return n.intValue();
        return def;
    }

    public boolean getBoolean(String key, boolean def) {
        Object val = data.get(key);
        if (val instanceof Boolean b) return b;
        return def;
    }

    public String getString(String key, String def) {
        Object val = data.get(key);
        if (val instanceof String s) return s;
        return def;
    }
}
