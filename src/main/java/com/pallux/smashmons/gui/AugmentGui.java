package com.pallux.smashmons.gui;

import com.pallux.smashmons.SmashMons;
import com.pallux.smashmons.augments.Augment;
import com.pallux.smashmons.util.ColorUtil;
import com.pallux.smashmons.util.ItemBuilder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

public class AugmentGui {

    public static final String TITLE = "§5§l★ §dChoose an Augment";

    public static void open(SmashMons plugin, Player player, List<Augment> choices) {
        Inventory inv = Bukkit.createInventory(null, 27, ColorUtil.colorize(TITLE));

        // Decorative borders
        ItemStack border = new ItemBuilder(Material.MAGENTA_STAINED_GLASS_PANE).name(" ").build();
        for (int i = 0; i < 27; i++) inv.setItem(i, border);

        // Place 3 augment choices in slots 11, 13, 15
        int[] slots = {11, 13, 15};
        for (int i = 0; i < choices.size() && i < 3; i++) {
            Augment aug = choices.get(i);
            ItemStack item = new ItemBuilder(aug.getMaterial())
                    .name(aug.getDisplayName())
                    .lore(
                            "&#CCCCCC#CCCCCC ────────────────── ",
                            "&#AAAAAA" + aug.getDescription(),
                            "&#CCCCCC#CCCCCC ────────────────── ",
                            "&#AAAAAA Type&#FFFFFF: " + formatType(aug.getType()),
                            "",
                            "&#44FF44▶ Click to choose!"
                    )
                    .glowing()
                    .build();
            item.editMeta(meta -> meta.getPersistentDataContainer()
                    .set(new NamespacedKey(plugin, "augment_id"),
                            PersistentDataType.STRING, aug.getId()));
            inv.setItem(slots[i], item);
        }
        player.openInventory(inv);
    }

    private static String formatType(Augment.AugmentType type) {
        return switch (type) {
            case STAT_BOOST -> "&#44FFFFStat Boost";
            case AURA -> "&#FF44FFAura";
            case ON_HIT -> "&#FF4444On Hit";
            case ON_KILL -> "&#FFAA00On Kill";
            case ON_DEATH -> "&#CCCCCC#CCCCCCOn Death";
            case PASSIVE -> "&#44FF44Passive";
        };
    }

    public static boolean isAugmentGui(String title) {
        return title.contains("Choose an Augment");
    }
}
