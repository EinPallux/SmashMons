package com.pallux.smashmons.managers;

import com.pallux.smashmons.SmashMons;
import com.pallux.smashmons.data.PlayerData;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public class PlayerDataManager {

    private final SmashMons plugin;
    private final File dataDir;
    private final Map<UUID, PlayerData> cache = new ConcurrentHashMap<>();

    public PlayerDataManager(SmashMons plugin) {
        this.plugin = plugin;
        this.dataDir = new File(plugin.getDataFolder(), "playerdata");
        dataDir.mkdirs();
    }

    public PlayerData get(Player player) {
        return cache.computeIfAbsent(player.getUniqueId(), uuid -> loadFromDisk(uuid));
    }

    public PlayerData get(UUID uuid) {
        return cache.computeIfAbsent(uuid, this::loadFromDisk);
    }

    public void loadAsync(Player player) {
        new BukkitRunnable() {
            @Override
            public void run() {
                PlayerData data = loadFromDisk(player.getUniqueId());
                cache.put(player.getUniqueId(), data);
            }
        }.runTaskAsynchronously(plugin);
    }

    public void saveAsync(UUID uuid) {
        PlayerData data = cache.get(uuid);
        if (data == null) return;
        new BukkitRunnable() {
            @Override
            public void run() {
                saveToDisk(data);
            }
        }.runTaskAsynchronously(plugin);
    }

    public void unload(UUID uuid) {
        saveAsync(uuid);
        cache.remove(uuid);
    }

    public void saveAll() {
        for (PlayerData data : cache.values()) {
            saveToDisk(data);
        }
    }

    private File getFile(UUID uuid) {
        return new File(dataDir, uuid + ".yml");
    }

    private PlayerData loadFromDisk(UUID uuid) {
        File file = getFile(uuid);
        PlayerData data = new PlayerData(uuid);
        if (!file.exists()) return data;
        try {
            YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
            data.setSmashCoins(cfg.getLong("smashcoins", 0));
            long kills = cfg.getLong("kills", 0);
            for (long i = 0; i < kills; i++) data.addKill();
            long deaths = cfg.getLong("deaths", 0);
            for (long i = 0; i < deaths; i++) data.addDeath();
            long wins = cfg.getLong("wins", 0);
            for (long i = 0; i < wins; i++) data.addWin();
            for (String kit : cfg.getStringList("unlocked-kits")) {
                data.unlockKit(kit);
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Failed to load player data for " + uuid, e);
        }
        return data;
    }

    private void saveToDisk(PlayerData data) {
        File file = getFile(data.getUuid());
        YamlConfiguration cfg = new YamlConfiguration();
        cfg.set("smashcoins", data.getSmashCoins());
        cfg.set("kills", data.getKills());
        cfg.set("deaths", data.getDeaths());
        cfg.set("wins", data.getWins());
        cfg.set("unlocked-kits", data.getUnlockedKits().stream().toList());
        try {
            cfg.save(file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to save player data for " + data.getUuid(), e);
        }
    }

    public Collection<PlayerData> getCachedData() { return cache.values(); }
}
