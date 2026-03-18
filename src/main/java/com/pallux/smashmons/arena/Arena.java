package com.pallux.smashmons.arena;

import org.bukkit.Location;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a configured SmashMons arena.
 */
public class Arena {

    private final String id;
    private String displayName;
    private ArenaRegion region;
    private final List<Location> spawnPoints = new ArrayList<>();
    private final List<Location> crystalSpawns = new ArrayList<>();
    private ArenaState state = ArenaState.AVAILABLE;

    public Arena(String id) {
        this.id = id;
        this.displayName = id;
    }

    public String getId() { return id; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public ArenaRegion getRegion() { return region; }
    public void setRegion(ArenaRegion region) { this.region = region; }

    public List<Location> getSpawnPoints() { return spawnPoints; }
    public void addSpawnPoint(Location loc) { spawnPoints.add(loc.clone()); }
    public boolean removeSpawnPoint(int index) {
        if (index < 0 || index >= spawnPoints.size()) return false;
        spawnPoints.remove(index);
        return true;
    }

    public List<Location> getCrystalSpawns() { return crystalSpawns; }
    public void addCrystalSpawn(Location loc) { crystalSpawns.add(loc.clone()); }
    public boolean removeCrystalSpawn(int index) {
        if (index < 0 || index >= crystalSpawns.size()) return false;
        crystalSpawns.remove(index);
        return true;
    }

    public int getMaxPlayers() { return spawnPoints.size(); }

    public ArenaState getState() { return state; }
    public void setState(ArenaState state) { this.state = state; }

    public boolean isAvailable() { return state == ArenaState.AVAILABLE; }
}
