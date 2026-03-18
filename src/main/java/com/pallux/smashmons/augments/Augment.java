package com.pallux.smashmons.augments;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

import java.util.HashMap;
import java.util.Map;

/**
 * Immutable data model for a SmashMons Augment loaded from augments.yml.
 */
public class Augment {

    public enum AugmentType {
        STAT_BOOST, AURA, ON_HIT, ON_KILL, ON_DEATH, PASSIVE
    }

    private final String id;
    private final String displayName;
    private final String description;
    private final Material material;
    private final AugmentType type;
    private final Map<String, Object> data = new HashMap<>();

    public Augment(String id, String displayName, String description,
                   Material material, AugmentType type, ConfigurationSection section) {
        this.id = id;
        this.displayName = displayName;
        this.description = description;
        this.material = material;
        this.type = type;
        if (section != null) {
            for (String key : section.getKeys(false)) {
                data.put(key, section.get(key));
            }
        }
    }

    public String getId() { return id; }
    public String getDisplayName() { return displayName; }
    public String getDescription() { return description; }
    public Material getMaterial() { return material; }
    public AugmentType getType() { return type; }

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
