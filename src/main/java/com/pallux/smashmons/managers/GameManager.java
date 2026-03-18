package com.pallux.smashmons.managers;

import com.pallux.smashmons.SmashMons;
import com.pallux.smashmons.arena.Arena;
import com.pallux.smashmons.arena.ArenaState;
import com.pallux.smashmons.game.Game;
import org.bukkit.entity.Player;

import java.util.*;

public class GameManager {

    private final SmashMons plugin;
    private final Map<String, Game> activeGames = new HashMap<>();
    // Maps player UUID -> arena ID for quick lookup
    private final Map<UUID, String> playerArenaMap = new HashMap<>();

    public GameManager(SmashMons plugin) {
        this.plugin = plugin;
    }

    public boolean joinArena(Player player, Arena arena) {
        if (playerArenaMap.containsKey(player.getUniqueId())) {
            plugin.getMessageManager().send(player, "already-in-game");
            return false;
        }
        if (!arena.isAvailable() && arena.getState() != ArenaState.WAITING && arena.getState() != ArenaState.STARTING) {
            plugin.getMessageManager().send(player, "arena-not-available");
            return false;
        }

        Game game = activeGames.computeIfAbsent(arena.getId(), id -> {
            arena.setState(ArenaState.WAITING);
            return new Game(plugin, arena);
        });

        if (game.getPlayers().size() >= arena.getMaxPlayers()) {
            plugin.getMessageManager().send(player, "arena-full");
            return false;
        }

        boolean added = game.addPlayer(player);
        if (added) {
            playerArenaMap.put(player.getUniqueId(), arena.getId());
        }
        return added;
    }

    public void leaveArena(Player player) {
        String arenaId = playerArenaMap.remove(player.getUniqueId());
        if (arenaId == null) return;
        Game game = activeGames.get(arenaId);
        if (game != null) {
            game.removePlayer(player);
        }
    }

    public Game getGame(UUID playerUuid) {
        String arenaId = playerArenaMap.get(playerUuid);
        if (arenaId == null) return null;
        return activeGames.get(arenaId);
    }

    public Game getGameByArena(String arenaId) {
        return activeGames.get(arenaId);
    }

    public boolean isInGame(UUID uuid) {
        return playerArenaMap.containsKey(uuid);
    }

    public void removeGame(String arenaId) {
        Game game = activeGames.remove(arenaId);
        if (game != null) {
            game.getPlayers().keySet().forEach(playerArenaMap::remove);
        }
    }

    public void removePlayerFromMap(UUID uuid) {
        playerArenaMap.remove(uuid);
    }

    public void shutdownAll() {
        for (Game game : new ArrayList<>(activeGames.values())) {
            game.shutdown();
        }
        activeGames.clear();
        playerArenaMap.clear();
    }

    public Collection<Game> getActiveGames() { return activeGames.values(); }
}
