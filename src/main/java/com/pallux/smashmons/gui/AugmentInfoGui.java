package com.pallux.smashmons.gui;

import com.pallux.smashmons.SmashMons;
import com.pallux.smashmons.augments.Augment;
import com.pallux.smashmons.util.ColorUtil;
import com.pallux.smashmons.util.ItemBuilder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Read-only overview of every augment — accessible from hub and in-game via /augments.
 */
public class AugmentInfoGui {

    public static final String TITLE = "§5§l★ §dAugment Compendium";

    public static void open(SmashMons plugin, Player player) {
        Collection<Augment> augments = plugin.getAugmentManager().getAugments();

        // Always use 54 slots; fill all with border panes first
        Inventory inv = Bukkit.createInventory(null, 54, ColorUtil.colorize(TITLE));

        ItemStack border = new ItemBuilder(Material.PURPLE_STAINED_GLASS_PANE).name(" ").build();
        for (int i = 0; i < 54; i++) inv.setItem(i, border);

        // Header info item in top-centre
        inv.setItem(4, new ItemBuilder(Material.ENCHANTED_BOOK)
                .name("&#DD44FF&lAugment Compendium")
                .lore(
                        "&#AAAAAA Augments are passive bonuses",
                        "&#AAAAAA picked between rounds.",
                        "",
                        "&#FFD700 Each player picks 1 augment",
                        "&#FFD700 per round from 3 random choices.",
                        "",
                        "&#CCCCCC#CCCCCC ─────────────────── "
                )
                .build());

        // Interior slots (same pattern as ArenaGui — rows 1-4, cols 1-7)
        List<Integer> interiorSlots = new ArrayList<>();
        for (int row = 1; row <= 4; row++) {
            for (int col = 1; col <= 7; col++) {
                interiorSlots.add(row * 9 + col);
            }
        }

        int idx = 0;
        for (Augment aug : augments) {
            if (idx >= interiorSlots.size()) break;

            String typeLabel = formatType(aug.getType());
            String typeColor = typeColor(aug.getType());

            ItemStack item = new ItemBuilder(aug.getMaterial())
                    .name(aug.getDisplayName())
                    .lore(
                            "&#CCCCCC#CCCCCC ────────────────── ",
                            "&#FFFFFF" + aug.getDescription(),
                            "&#CCCCCC#CCCCCC ────────────────── ",
                            "&#AAAAAA Type&#FFFFFF: " + typeColor + typeLabel,
                            buildStatLine(aug)
                    )
                    .glowing()
                    .build();

            inv.setItem(interiorSlots.get(idx), item);
            idx++;
        }

        if (augments.isEmpty()) {
            inv.setItem(22, new ItemBuilder(Material.BARRIER)
                    .name("&#FF4444&lNo Augments Configured")
                    .lore("&#AAAAAA Ask an admin to add augments.")
                    .build());
        }

        player.openInventory(inv);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static String formatType(Augment.AugmentType type) {
        return switch (type) {
            case STAT_BOOST -> "Stat Boost";
            case AURA       -> "Aura";
            case ON_HIT     -> "On Hit";
            case ON_KILL    -> "On Kill";
            case ON_DEATH   -> "On Death";
            case PASSIVE    -> "Passive";
        };
    }

    private static String typeColor(Augment.AugmentType type) {
        return switch (type) {
            case STAT_BOOST -> "&#44FFFF";
            case AURA       -> "&#FF44FF";
            case ON_HIT     -> "&#FF4444";
            case ON_KILL    -> "&#FFAA00";
            case ON_DEATH   -> "&#CCCCCC#CCCCCC";
            case PASSIVE    -> "&#44FF44";
        };
    }

    /**
     * Builds a short human-readable stat line from the augment's key data values.
     * Falls back to an empty string for augments with no obvious numeric stat.
     */
    private static String buildStatLine(Augment aug) {
        return switch (aug.getId()) {
            case "iron_will"              -> "&#44FF44+" + (int) aug.getDouble("extra-health", 4) + " Max HP";
            case "swift_strikes"          -> "&#FFD700+" + pct(aug.getDouble("damage-multiplier", 1.15)) + "% Damage";
            case "wind_resistance"        -> "&#44FFFF-" + pct2(aug.getDouble("knockback-reduction", 0.25)) + "% Knockback";
            case "glass_cannon"           -> "&#FFD700+" + pct(aug.getDouble("damage-multiplier", 1.30)) + "% Dmg  &#FF4444+" + pct2(aug.getDouble("damage-taken-multiplier", 1.15)) + "% Dmg Taken";
            case "spike_aura"             -> "&#FF44FF" + pct2(aug.getDouble("reflect-percent", 0.20)) + "% Damage Reflected";
            case "vampiric_touch"         -> "&#FF4444Heal " + aug.getDouble("heal-per-damage", 0.5) + "HP per damage dealt";
            case "lightning_reflexes"     -> "&#44FFFF-" + pct2(aug.getDouble("cooldown-reduction", 0.20)) + "% Cooldowns";
            case "adrenaline"             -> "&#FFD700Speed II for " + (aug.getInt("speed-duration-ticks", 80) / 20) + "s on kill";
            case "stone_skin"             -> "&#AAAAAA Permanent Resistance I";
            case "double_jump_master"     -> "&#44FFFF-" + (int) aug.getDouble("double-jump-cooldown-reduction", 1) + "s Double Jump CD";
            case "energy_surge"           -> "&#DD44FF+" + pct2(aug.getDouble("energy-regen-bonus", 0.25)) + "% Energy Regen";
            case "final_stand"            -> "&#FF2222Strength I when below " + (int) aug.getDouble("threshold-health", 4) + " HP";
            case "phantom_step"           -> "&#AAAAAA" + (aug.getInt("invis-duration-ticks", 40) / 20) + "s Invisibility after using ability";
            case "berserker"              -> "&#CC2200+" + pct2(aug.getDouble("damage-bonus-per-kill", 0.05)) + "% Damage per kill this round";
            default                       -> "";
        };
    }

    /** Converts a multiplier like 1.15 → "15" */
    private static String pct(double multiplier) {
        return String.valueOf((int) Math.round((multiplier - 1.0) * 100));
    }

    /** Converts a fraction like 0.25 → "25" */
    private static String pct2(double fraction) {
        return String.valueOf((int) Math.round(fraction * 100));
    }

    public static boolean isAugmentInfoGui(String title) {
        return title.contains("Augment Compendium");
    }
}
