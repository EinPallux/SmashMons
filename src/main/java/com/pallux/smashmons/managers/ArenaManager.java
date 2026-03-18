package com.pallux.smashmons.managers;

import com.pallux.smashmons.SmashMons;
import com.pallux.smashmons.arena.Arena;
import com.pallux.smashmons.arena.ArenaRegion;
import com.pallux.smashmons.util.LocationUtil;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.bukkit.WorldEditPlugin;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.Region;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.logging.Level;

public class ArenaManager {

    private final SmashMons plugin;
    private final File arenasFile;
    private FileConfiguration arenasConfig;
    private final Map<String, Arena> arenas = new LinkedHashMap<>();

    public ArenaManager(SmashMons plugin) {
        this.plugin = plugin;
        this.arenasFile = new File(plugin.getDataFolder(), "settings/arenas.yml");
        load();
    }

    public void load() {
        arenas.clear();
        arenasConfig = YamlConfiguration.loadConfiguration(arenasFile);
        ConfigurationSection section = arenasConfig.getConfigurationSection("arenas");
        if (section == null) return;

        for (String id : section.getKeys(false)) {
            ConfigurationSection aSec = section.getConfigurationSection(id);
            Arena arena = new Arena(id);
            arena.setDisplayName(aSec.getString("display-name", id));

            // Load region
            if (aSec.contains("region")) {
                ConfigurationSection reg = aSec.getConfigurationSection("region");
                ArenaRegion region = new ArenaRegion(
                        reg.getString("world", "world"),
                        reg.getInt("minX"), reg.getInt("minY"), reg.getInt("minZ"),
                        reg.getInt("maxX"), reg.getInt("maxY"), reg.getInt("maxZ")
                );
                arena.setRegion(region);
            }

            // Load spawn points
            List<String> spawns = aSec.getStringList("spawns");
            for (String s : spawns) {
                Location loc = LocationUtil.deserialize(s);
                if (loc != null) arena.addSpawnPoint(loc);
            }

            // Load crystal spawn points
            List<String> crystals = aSec.getStringList("crystals");
            for (String c : crystals) {
                Location loc = LocationUtil.deserialize(c);
                if (loc != null) arena.addCrystalSpawn(loc);
            }

            arenas.put(id.toLowerCase(), arena);
        }
        plugin.getLogger().info("Loaded " + arenas.size() + " arenas.");
    }

    public void save() {
        arenasConfig.set("arenas", null);
        for (Arena arena : arenas.values()) {
            String path = "arenas." + arena.getId();
            arenasConfig.set(path + ".display-name", arena.getDisplayName());
            if (arena.getRegion() != null) {
                ArenaRegion r = arena.getRegion();
                arenasConfig.set(path + ".region.world", r.getWorldName());
                arenasConfig.set(path + ".region.minX", r.getMinX());
                arenasConfig.set(path + ".region.minY", r.getMinY());
                arenasConfig.set(path + ".region.minZ", r.getMinZ());
                arenasConfig.set(path + ".region.maxX", r.getMaxX());
                arenasConfig.set(path + ".region.maxY", r.getMaxY());
                arenasConfig.set(path + ".region.maxZ", r.getMaxZ());
            }
            List<String> spawns = new ArrayList<>();
            for (Location loc : arena.getSpawnPoints()) spawns.add(LocationUtil.serialize(loc));
            arenasConfig.set(path + ".spawns", spawns);

            List<String> crystals = new ArrayList<>();
            for (Location loc : arena.getCrystalSpawns()) crystals.add(LocationUtil.serialize(loc));
            arenasConfig.set(path + ".crystals", crystals);
        }
        try {
            arenasConfig.save(arenasFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save arenas.yml", e);
        }
    }

    public boolean createArena(Player player, String name) {
        String id = name.toLowerCase();
        if (arenas.containsKey(id)) return false;

        // Get WorldEdit selection
        try {
            WorldEditPlugin we = (WorldEditPlugin) plugin.getServer().getPluginManager().getPlugin("WorldEdit");
            if (we == null) {
                // Try FAWE
                we = (WorldEditPlugin) plugin.getServer().getPluginManager().getPlugin("FastAsyncWorldEdit");
            }
            if (we == null) {
                player.sendMessage("WorldEdit/FAWE not found.");
                return false;
            }
            com.sk89q.worldedit.LocalSession session = we.getWorldEdit().getSessionManager().get(BukkitAdapter.adapt(player));
            Region region = session.getSelection();
            if (region == null) return false;

            BlockVector3 min = region.getMinimumPoint();
            BlockVector3 max = region.getMaximumPoint();
            String worldName = player.getWorld().getName();

            Arena arena = new Arena(id);
            arena.setDisplayName(name);
            arena.setRegion(new ArenaRegion(worldName, min.x(), min.y(), min.z(), max.x(), max.y(), max.z()));
            arenas.put(id, arena);
            save();
            return true;
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Failed to get WorldEdit selection", e);
            return false;
        }
    }

    public boolean deleteArena(String name) {
        Arena arena = arenas.remove(name.toLowerCase());
        if (arena == null) return false;
        save();
        return true;
    }

    public boolean addSpawnPoint(String arenaName, Location loc) {
        Arena arena = getArena(arenaName);
        if (arena == null) return false;
        arena.addSpawnPoint(loc);
        save();
        return true;
    }

    public boolean addCrystalSpawn(String arenaName, Location loc) {
        Arena arena = getArena(arenaName);
        if (arena == null) return false;
        arena.addCrystalSpawn(loc);
        save();
        return true;
    }

    public Arena getArena(String name) { return arenas.get(name.toLowerCase()); }
    public Collection<Arena> getArenas() { return arenas.values(); }
    public List<String> getArenaNames() { return new ArrayList<>(arenas.keySet()); }
}
