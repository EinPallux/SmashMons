package com.pallux.smashmons.managers;

import com.pallux.smashmons.SmashMons;
import com.pallux.smashmons.kits.Kit;
import com.pallux.smashmons.kits.KitAbility;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;

import java.io.File;
import java.util.*;

public class KitManager {

    private final SmashMons plugin;
    private final Map<String, Kit> kits = new LinkedHashMap<>();

    public KitManager(SmashMons plugin) {
        this.plugin = plugin;
        load();
    }

    public void load() {
        kits.clear();
        File file = new File(plugin.getDataFolder(), "settings/kits.yml");
        FileConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection kitsSection = cfg.getConfigurationSection("kits");
        if (kitsSection == null) return;

        for (String id : kitsSection.getKeys(false)) {
            try {
                ConfigurationSection ks = kitsSection.getConfigurationSection(id);
                String displayName = ks.getString("display-name", id);
                List<String> lore = ks.getStringList("lore");
                Material material = Material.matchMaterial(ks.getString("material", "DIRT"));
                if (material == null) material = Material.BARRIER;
                EntityType mobType = parseMob(ks.getString("mob", "PIG"));
                double health = ks.getDouble("health", 20.0);
                double speedBonus = ks.getDouble("speed-bonus", 0.0);
                boolean unlockedByDefault = ks.getBoolean("unlocked-by-default", true);
                long cost = ks.getLong("cost", 0);
                Kit.EnergyType energyType = ks.getString("type", "COOLDOWN").equalsIgnoreCase("ENERGY")
                        ? Kit.EnergyType.ENERGY : Kit.EnergyType.COOLDOWN;
                int energyRegen = ks.getInt("energy-regen-per-second", 10);

                ConfigurationSection abilitiesSection = ks.getConfigurationSection("abilities");
                KitAbility primary = loadAbility(abilitiesSection, "primary", KitAbility.AbilitySlot.PRIMARY);
                KitAbility secondary = loadAbility(abilitiesSection, "secondary", KitAbility.AbilitySlot.SECONDARY);
                KitAbility ultimate = loadAbility(abilitiesSection, "ultimate", KitAbility.AbilitySlot.ULTIMATE);

                kits.put(id.toLowerCase(), new Kit(id, displayName, lore, material, mobType,
                        health, speedBonus, unlockedByDefault, cost, energyType, energyRegen,
                        primary, secondary, ultimate));
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to load kit: " + id + " - " + e.getMessage());
            }
        }
        plugin.getLogger().info("Loaded " + kits.size() + " kits.");
    }

    private KitAbility loadAbility(ConfigurationSection section, String key, KitAbility.AbilitySlot slot) {
        if (section == null || !section.contains(key)) return null;
        ConfigurationSection as = section.getConfigurationSection(key);
        String name = as.getString("name", key);
        String desc = as.getString("description", "");
        String matStr = as.getString("material", "STICK");
        Material mat = Material.matchMaterial(matStr);
        if (mat == null) mat = Material.STICK;
        int cooldown = as.getInt("cooldown", 10);
        int energyCost = as.getInt("energy-cost", 0);
        return new KitAbility(name, desc, mat, cooldown, energyCost, slot, as);
    }

    private EntityType parseMob(String name) {
        try {
            return EntityType.valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            return EntityType.PIG;
        }
    }

    public Kit getKit(String id) { return kits.get(id.toLowerCase()); }
    public Collection<Kit> getKits() { return kits.values(); }
    public List<String> getKitIds() { return new ArrayList<>(kits.keySet()); }
}
