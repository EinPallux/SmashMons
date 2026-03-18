package com.pallux.smashmons.gui;

import com.pallux.smashmons.SmashMons;
import com.pallux.smashmons.arena.Arena;
import com.pallux.smashmons.arena.ArenaState;
import com.pallux.smashmons.game.Game;
import com.pallux.smashmons.util.ColorUtil;
import com.pallux.smashmons.util.ItemBuilder;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.*;

public class ArenaGui {

    private static final String TITLE_TAG = "§5§l⚔ SmashMons §8» §dArena Select";

    public static void open(SmashMons plugin, Player player) {
        Collection<Arena> arenas = plugin.getArenaManager().getArenas();

        // Always use 54 slots so there are always valid interior slots.
        // For 0 arenas show a "no arenas" placeholder instead of crashing.
        Inventory inv = Bukkit.createInventory(null, 54, ColorUtil.colorize(TITLE_TAG));

        // Fill entire inventory with border panes first
        ItemStack border = new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ").build();
        for (int i = 0; i < 54; i++) inv.setItem(i, border);

        if (arenas.isEmpty()) {
            inv.setItem(22, new ItemBuilder(Material.BARRIER)
                    .name("&#FF4444&lNo Arenas Available")
                    .lore("&#AAAAAA Ask an admin to create an arena.")
                    .build());
            player.openInventory(inv);
            return;
        }

        // Interior slots of a 54-slot GUI: rows 1-3 (indices 9-44), columns 1-7 (skip 0 and 8)
        // That gives slots: 10-16, 19-25, 28-34, 37-43  — 28 usable slots total
        List<Integer> interiorSlots = new ArrayList<>();
        for (int row = 1; row <= 4; row++) {
            for (int col = 1; col <= 7; col++) {
                interiorSlots.add(row * 9 + col);
            }
        }

        int idx = 0;
        for (Arena arena : arenas) {
            if (idx >= interiorSlots.size()) break;

            Game game = plugin.getGameManager().getGameByArena(arena.getId());
            int currentPlayers = game != null ? game.getPlayers().size() : 0;
            int maxPlayers = arena.getMaxPlayers();
            ArenaState state = arena.getState();

            Material icon = switch (state) {
                case AVAILABLE, WAITING -> Material.LIME_CONCRETE;
                case STARTING            -> Material.YELLOW_CONCRETE;
                case IN_GAME             -> Material.ORANGE_CONCRETE;
                case ENDING              -> Material.RED_CONCRETE;
            };

            String stateStr = switch (state) {
                case AVAILABLE, WAITING -> "&#44FF44Available";
                case STARTING           -> "&#FFFF44Starting...";
                case IN_GAME            -> "&#FF8800In Game";
                case ENDING             -> "&#FF4444Ending";
            };

            int crystalCount = arena.getCrystalSpawns().size();
            boolean joinable = state == ArenaState.AVAILABLE || state == ArenaState.WAITING;

            ItemStack arenaItem = new ItemBuilder(icon)
                    .name("&#FFFFFF&l" + arena.getDisplayName())
                    .lore(
                            "&#CCCCCC#CCCCCC ─────────────────── ",
                            "&#AAAAAA Map&#FFFFFF: &#FFD700" + arena.getId(),
                            "&#AAAAAA Players&#FFFFFF: &#44FF44" + currentPlayers + " &#FFFFFF/ &#44FF44" + maxPlayers,
                            "&#AAAAAA Crystal Spawns&#FFFFFF: &#FFD700" + crystalCount,
                            "&#AAAAAA Status&#FFFFFF: " + stateStr,
                            "&#CCCCCC#CCCCCC ─────────────────── ",
                            joinable ? "&#44FF44▶ Click to join!" : "&#FF4444✗ Cannot join right now."
                    )
                    .build();

            arenaItem.editMeta(meta -> meta.getPersistentDataContainer()
                    .set(new org.bukkit.NamespacedKey(plugin, "arena_id"),
                            org.bukkit.persistence.PersistentDataType.STRING, arena.getId()));

            inv.setItem(interiorSlots.get(idx), arenaItem);
            idx++;
        }

        player.openInventory(inv);
    }

    public static boolean isArenaGui(String title) {
        return title.equals(TITLE_TAG) || title.contains("Arena Select");
    }
}
