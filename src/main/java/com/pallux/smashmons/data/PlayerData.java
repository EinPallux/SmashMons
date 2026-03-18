package com.pallux.smashmons.data;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Persistent data for a single player.
 */
public class PlayerData {

    private final UUID uuid;
    private long smashCoins;
    private long kills;
    private long deaths;
    private long wins;
    private final Set<String> unlockedKits = new HashSet<>();

    public PlayerData(UUID uuid) {
        this.uuid = uuid;
        this.smashCoins = 0;
        this.kills = 0;
        this.deaths = 0;
        this.wins = 0;
    }

    public UUID getUuid() { return uuid; }

    public long getSmashCoins() { return smashCoins; }
    public void setSmashCoins(long smashCoins) { this.smashCoins = Math.max(0, smashCoins); }
    public void addSmashCoins(long amount) { this.smashCoins += amount; }
    public boolean deductSmashCoins(long amount) {
        if (smashCoins < amount) return false;
        smashCoins -= amount;
        return true;
    }

    public long getKills() { return kills; }
    public void addKill() { kills++; }

    public long getDeaths() { return deaths; }
    public void addDeath() { deaths++; }

    public long getWins() { return wins; }
    public void addWin() { wins++; }

    public double getKD() {
        if (deaths == 0) return kills;
        return Math.round((double) kills / deaths * 100.0) / 100.0;
    }

    public Set<String> getUnlockedKits() { return unlockedKits; }
    public boolean hasKit(String kitId) { return unlockedKits.contains(kitId.toLowerCase()); }
    public void unlockKit(String kitId) { unlockedKits.add(kitId.toLowerCase()); }
}
