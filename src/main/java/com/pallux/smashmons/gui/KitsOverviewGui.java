package com.pallux.smashmons.gui;

import com.pallux.smashmons.SmashMons;
import com.pallux.smashmons.data.PlayerData;
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

        // Balance display in top centre
        inv.setItem(4, new ItemBuilder(Material.SUNFLOWER)
                .name("&#FFD700&lYour Balance")
                .lore(
                        "&#FFD700" + balance + " SmashCoins",
                        "",
                        "&#CCCCCCSpend coins to unlock kits!"
                )
                .build());

        int slot = 10;
        for (Kit kit : kits) {
            if (slot >= 44) break;
            boolean unlocked = kit.isUnlockedByDefault() || pd.hasKit(kit.getId());
            boolean canBuy   = !unlocked && balance >= kit.getCost();

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
                lore.add("&#6BFF6B✔ Already Unlocked");
            } else {
                lore.add("&#FF6B6B✗ Locked — &#FFD700" + kit.getCost() + " SmashCoins");
                if (canBuy) {
                    lore.add("&#6BFF6B▶ Click to purchase!");
                } else {
                    long needed = kit.getCost() - balance;
                    lore.add("&#FF6B6BNeed &#FFD700" + needed + " &#FF6B6Bmore SmashCoins.");
                }
            }

            Material mat = unlocked ? kit.getMaterial() : (canBuy ? kit.getMaterial() : Material.BARRIER);
            String nameColor = unlocked ? "&#6BFF6B&l" : (canBuy ? "&#FFD700&l" : "&#FF6B6B&l");

            ItemStack item = new ItemBuilder(mat)
                    .name(nameColor + stripColor(kit.getDisplayName()))
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

    private static String formatCooldownEnergy(Kit kit, KitAbility ability) {
        if (kit.isEnergy()) return "&#D946EFEnergy: " + ability.getEnergyCost();
        return "&#44DDFFCooldown: " + ability.getCooldownSeconds() + "s";
    }

    private static String stripColor(String s) {
        return s.replaceAll("&#[A-Fa-f0-9]{6}", "").replaceAll("&[0-9a-fk-or]", "");
    }

    public static boolean isKitsOverviewGui(String title) {
        return title.contains("Kit Shop");
    }
}