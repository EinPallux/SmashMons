package com.pallux.smashmons.managers;

import com.pallux.smashmons.SmashMons;
import com.pallux.smashmons.augments.Augment;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.*;

public class AugmentManager {

    private final SmashMons plugin;
    private final Map<String, Augment> augments = new LinkedHashMap<>();

    public AugmentManager(SmashMons plugin) {
        this.plugin = plugin;
        load();
    }

    public void load() {
        augments.clear();
        File file = new File(plugin.getDataFolder(), "settings/augments.yml");
        FileConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection section = cfg.getConfigurationSection("augments");
        if (section == null) return;

        for (String id : section.getKeys(false)) {
            try {
                ConfigurationSection as = section.getConfigurationSection(id);
                String displayName = as.getString("display-name", id);
                String description = as.getString("description", "");
                Material material = Material.matchMaterial(as.getString("material", "PAPER"));
                if (material == null) material = Material.PAPER;
                Augment.AugmentType type;
                try {
                    type = Augment.AugmentType.valueOf(as.getString("type", "PASSIVE").toUpperCase());
                } catch (Exception e) {
                    type = Augment.AugmentType.PASSIVE;
                }
                augments.put(id.toLowerCase(), new Augment(id, displayName, description, material, type, as));
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to load augment: " + id + " - " + e.getMessage());
            }
        }
        plugin.getLogger().info("Loaded " + augments.size() + " augments.");
    }

    public Augment getAugment(String id) { return augments.get(id.toLowerCase()); }
    public Collection<Augment> getAugments() { return augments.values(); }

    /**
     * Returns 3 random augments from the pool, excluding ones already held by this player this game.
     */
    public List<Augment> getRandomAugments(Collection<String> exclude) {
        List<Augment> pool = new ArrayList<>(augments.values());
        pool.removeIf(a -> exclude.contains(a.getId()));
        Collections.shuffle(pool);
        // Return a proper new ArrayList, NOT a subList view (subList shares backing array)
        return new ArrayList<>(pool.subList(0, Math.min(3, pool.size())));
    }
}
