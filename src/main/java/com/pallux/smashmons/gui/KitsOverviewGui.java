package com.pallux.smashmons.gui;

import com.pallux.smashmons.SmashMons;
import com.pallux.smashmons.data.PlayerData;
import com.pallux.smashmons.kits.Kit;
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

public class KitsOverviewGui {

    public static final String TITLE = "§5§l⚔ §dKit Shop & Overview";

    public static void open(SmashMons plugin, Player player) {
        Collection<Kit> kits = plugin.getKitManager().getKits();
        Inventory inv = Bukkit.createInventory(null, 54, ColorUtil.colorize(TITLE));

        // Borders
        ItemStack border = new ItemBuilder(Material.PURPLE_STAINED_GLASS_PANE).name(" ").build();
        for (int i = 0; i < 9; i++) inv.setItem(i, border);
        for (int i = 45; i < 54; i++) inv.setItem(i, border);
        for (int i = 9; i < 45; i += 9) inv.setItem(i, border);
        for (int i = 17; i < 45; i += 9) inv.setItem(i, border);

        PlayerData pd = plugin.getPlayerDataManager().get(player);
        long balance = pd.getSmashCoins();

        // Balance display (top center)
        inv.setItem(4, new ItemBuilder(Material.SUNFLOWER)
                .name("&#FFD700&lYour Balance")
                .lore("&#FFD700&l" + balance + " &#FFFFFFSmashCoins &#FFD700✦")
                .build());

        int slot = 10;
        for (Kit kit : kits) {
            if (slot >= 44) break;
            boolean unlocked = kit.isUnlockedByDefault() || pd.hasKit(kit.getId());
            boolean canBuy = !unlocked && balance >= kit.getCost();

            List<String> lore = new ArrayList<>(kit.getLore());
            lore.add("");
            lore.add("&#AAAAAA ──── Abilities ──── ");
            if (kit.getPrimaryAbility() != null) {
                lore.add("&#FFD700▸ &#FFFFFF" + kit.getPrimaryAbility().getName());
                lore.add("  &#AAAAAA" + kit.getPrimaryAbility().getDescription());
                lore.add("  " + formatCooldownEnergy(kit, kit.getPrimaryAbility()));
            }
            if (kit.getSecondaryAbility() != null) {
                lore.add("&#FFD700▸ &#FFFFFF" + kit.getSecondaryAbility().getName());
                lore.add("  &#AAAAAA" + kit.getSecondaryAbility().getDescription());
                lore.add("  " + formatCooldownEnergy(kit, kit.getSecondaryAbility()));
            }
            if (kit.getUltimateAbility() != null) {
                lore.add("&#FF44FF▸ &#FFFFFF" + kit.getUltimateAbility().getName() + " &#FF44FF(Ultimate)");
                lore.add("  &#AAAAAA" + kit.getUltimateAbility().getDescription());
            }
            lore.add("");
            if (unlocked) {
                lore.add("&#44FF44✔ Unlocked");
            } else {
                lore.add("&#FF4444✗ Locked — &#FFD700" + kit.getCost() + " SmashCoins");
                if (canBuy) lore.add("&#44FF44▶ Click to purchase!");
                else lore.add("&#FF8888You need &#FFD700" + (kit.getCost() - balance) + "&#FF8888 more SmashCoins.");
            }

            Material mat = unlocked ? kit.getMaterial() : (canBuy ? kit.getMaterial() : Material.BARRIER);
            ItemStack item = new ItemBuilder(mat)
                    .name((unlocked ? "&#44FF44&l" : (canBuy ? "&#FFD700&l" : "&#FF4444&l")) + stripColor(kit.getDisplayName()))
                    .lore(lore)
                    .build();

            if (!unlocked && canBuy) {
                item.editMeta(meta -> meta.getPersistentDataContainer()
                        .set(new NamespacedKey(plugin, "buy_kit_id"), PersistentDataType.STRING, kit.getId()));
            }
            inv.setItem(slot, item);
            slot++;
            if ((slot + 1) % 9 == 0) slot += 2;
        }
        player.openInventory(inv);
    }

    private static String formatCooldownEnergy(Kit kit, com.pallux.smashmons.kits.KitAbility ability) {
        if (kit.isEnergy()) return "  &#DD44FFEnergy: " + ability.getEnergyCost();
        return "  &#44DDFFCooldown: " + ability.getCooldownSeconds() + "s";
    }

    private static String stripColor(String s) {
        return s.replaceAll("&#[A-Fa-f0-9]{6}", "").replaceAll("&[0-9a-fk-or]", "");
    }

    public static boolean isKitsOverviewGui(String title) {
        return title.contains("Kit Shop");
    }
}
