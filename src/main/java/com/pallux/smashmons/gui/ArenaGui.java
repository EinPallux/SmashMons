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

        Inventory inv = Bukkit.createInventory(null, 54, ColorUtil.colorize(TITLE_TAG));

        // Border panes
        ItemStack border = new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ").build();
        for (int i = 0; i < 54; i++) inv.setItem(i, border);

        if (arenas.isEmpty()) {
            inv.setItem(22, new ItemBuilder(Material.BARRIER)
                    .name("&#FF6B6B&lNo Arenas Available")
                    .lore("&#CCCCCCAsk an admin to set up an arena.")
                    .build());
            player.openInventory(inv);
            return;
        }

        // Interior slots: rows 1-4, columns 1-7 (skip border columns 0 and 8)
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
                case STARTING           -> Material.YELLOW_CONCRETE;
                case IN_GAME            -> Material.ORANGE_CONCRETE;
                case ENDING             -> Material.RED_CONCRETE;
            };

            String stateStr = switch (state) {
                case AVAILABLE, WAITING -> "&#6BFF6BOpen";
                case STARTING           -> "&#FFD700Starting...";
                case IN_GAME            -> "&#FF8844In Progress";
                case ENDING             -> "&#FF6B6BEnding";
            };

            boolean joinable = state == ArenaState.AVAILABLE || state == ArenaState.WAITING;

            ItemStack arenaItem = new ItemBuilder(icon)
                    .name("&#FFFFFF&l" + arena.getDisplayName())
                    .lore(
                            "&#CCCCCC────────────────────",
                            "&#CCCCCCMap&#FFFFFF:     &#FFD700" + arena.getId(),
                            "&#CCCCCCPlayers&#FFFFFF:  &#6BFF6B" + currentPlayers + " &#CCCCCC/ &#6BFF6B" + maxPlayers,
                            "&#CCCCCCCrystals&#FFFFFF: &#FFD700" + arena.getCrystalSpawns().size(),
                            "&#CCCCCCStatus&#FFFFFF:  " + stateStr,
                            "&#CCCCCC────────────────────",
                            joinable ? "&#6BFF6B▶ Click to join!" : "&#FF6B6B✗ Cannot join right now."
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