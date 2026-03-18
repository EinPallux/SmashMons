package com.pallux.smashmons.gui;

import com.pallux.smashmons.SmashMons;
import com.pallux.smashmons.data.PlayerData;
import com.pallux.smashmons.game.Game;
import com.pallux.smashmons.kits.Kit;
import com.pallux.smashmons.kits.KitAbility;
import com.pallux.smashmons.util.ColorUtil;
import com.pallux.smashmons.util.ItemBuilder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class KitSelectGui {

    public static final String TITLE = "§5§l⚔ §dChoose Your Kit";

    public static void open(SmashMons plugin, Player player, Game game) {
        Collection<Kit> kits = plugin.getKitManager().getKits();
        int size = 54;
        Inventory inv = Bukkit.createInventory(null, size, ColorUtil.colorize(TITLE));

        // Borders: top row, bottom row, left and right columns
        ItemStack border = new ItemBuilder(Material.PURPLE_STAINED_GLASS_PANE).name(" ").build();
        for (int i = 0; i < 9; i++) inv.setItem(i, border);
        for (int i = 45; i < 54; i++) inv.setItem(i, border);
        for (int i = 9; i < 45; i += 9) inv.setItem(i, border);
        for (int i = 17; i < 45; i += 9) inv.setItem(i, border);

        PlayerData pd = plugin.getPlayerDataManager().get(player);
        int slot = 10;

        for (Kit kit : kits) {
            if (slot >= 44) break;
            boolean unlocked = kit.isUnlockedByDefault() || pd.hasKit(kit.getId());

            List<String> lore = new ArrayList<>(kit.getLore());
            lore.add("");
            lore.add("&#CCCCCC──── Abilities ────");

            if (kit.getPrimaryAbility() != null) {
                lore.add("&#FFD700[Primary] &#FFFFFF" + kit.getPrimaryAbility().getName());
                lore.add("  &#CCCCCC" + kit.getPrimaryAbility().getDescription());
                lore.add("  " + formatCooldownEnergy(kit, kit.getPrimaryAbility()));
            }
            if (kit.getSecondaryAbility() != null) {
                lore.add("&#FFD700[Secondary] &#FFFFFF" + kit.getSecondaryAbility().getName());
                lore.add("  &#CCCCCC" + kit.getSecondaryAbility().getDescription());
                lore.add("  " + formatCooldownEnergy(kit, kit.getSecondaryAbility()));
            }
            if (kit.getUltimateAbility() != null) {
                lore.add("&#FF44FF[Ultimate] &#FFFFFF" + kit.getUltimateAbility().getName());
                lore.add("  &#CCCCCC" + kit.getUltimateAbility().getDescription());
                lore.add("  &#FF44FFRequires: Ultimate Crystal");
            }
            lore.add("");
            if (unlocked) {
                lore.add("&#6BFF6B▶ Click to select this kit!");
            } else {
                lore.add("&#FF6B6B✗ Locked — &#FFD700" + kit.getCost() + " SmashCoins to unlock.");
            }

            Material mat = unlocked ? kit.getMaterial() : Material.BARRIER;
            ItemStack item = new ItemBuilder(mat)
                    .name((unlocked ? "&#FFFFFF&l" : "&#FF6B6B&l") + stripColor(kit.getDisplayName()))
                    .lore(lore)
                    .build();

            if (unlocked) {
                item.editMeta(meta -> meta.getPersistentDataContainer()
                        .set(new NamespacedKey(plugin, "kit_id"), PersistentDataType.STRING, kit.getId()));
            }
            inv.setItem(slot, item);
            slot++;
            if ((slot + 1) % 9 == 0) slot += 2;
        }

        player.openInventory(inv);
    }

    private static String formatCooldownEnergy(Kit kit, KitAbility ability) {
        if (kit.isEnergy()) return "&#D946EF" + ability.getEnergyCost() + " Energy";
        return "&#44DDFF" + ability.getCooldownSeconds() + "s cooldown";
    }

    private static String stripColor(String s) {
        return s.replaceAll("&#[A-Fa-f0-9]{6}", "").replaceAll("&[0-9a-fk-or]", "");
    }

    public static boolean isKitSelectGui(String title) {
        return title.contains("Choose Your Kit");
    }
}