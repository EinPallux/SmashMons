package com.pallux.smashmons.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * Fluent builder for ItemStacks.
 * All display names and lore are automatically de-italicised — Minecraft applies
 * italic by default to any custom component text; we override that globally here.
 */
public final class ItemBuilder {

    private final ItemStack item;
    private final ItemMeta meta;

    public ItemBuilder(Material material) {
        this.item = new ItemStack(material);
        this.meta = item.getItemMeta();
    }

    /** Sets the display name (non-italic). */
    public ItemBuilder name(String name) {
        meta.displayName(ColorUtil.colorizeItem(name));
        return this;
    }

    /** Sets the display name from a pre-built Component (caller is responsible for italic). */
    public ItemBuilder name(Component component) {
        meta.displayName(component.decoration(TextDecoration.ITALIC, false));
        return this;
    }

    /** Sets lore lines (each non-italic). */
    public ItemBuilder lore(String... lines) {
        List<Component> loreList = new ArrayList<>();
        for (String line : lines) loreList.add(ColorUtil.colorizeItem(line));
        meta.lore(loreList);
        return this;
    }

    /** Sets lore lines from a List (each non-italic). */
    public ItemBuilder lore(List<String> lines) {
        List<Component> loreList = new ArrayList<>();
        for (String line : lines) loreList.add(ColorUtil.colorizeItem(line));
        meta.lore(loreList);
        return this;
    }

    public ItemBuilder amount(int amount) {
        item.setAmount(amount);
        return this;
    }

    public ItemBuilder glowing() {
        meta.addEnchant(Enchantment.UNBREAKING, 1, true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        return this;
    }

    public ItemBuilder hideFlags() {
        meta.addItemFlags(ItemFlag.values());
        return this;
    }

    public ItemBuilder unbreakable() {
        meta.setUnbreakable(true);
        meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE);
        return this;
    }

    public ItemStack build() {
        item.setItemMeta(meta);
        return item;
    }
}
