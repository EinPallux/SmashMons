package com.pallux.smashmons.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.ChatColor;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ColorUtil {

    private static final Pattern HEX_PATTERN = Pattern.compile("&#([A-Fa-f0-9]{6})");
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    private ColorUtil() {}

    /**
     * Translates hex + legacy colour codes into a Component.
     * Used for chat messages — italic state is whatever the text specifies.
     */
    public static Component colorize(String text) {
        if (text == null) return Component.empty();
        return LEGACY.deserialize(translateHex(text));
    }

    /**
     * Same as {@link #colorize} but explicitly disables italic.
     * Use this for ALL item display names and lore lines so Minecraft's
     * default italic styling on custom items is suppressed.
     */
    public static Component colorizeItem(String text) {
        if (text == null) return Component.empty().decoration(TextDecoration.ITALIC, false);
        return LEGACY.deserialize(translateHex(text))
                .decoration(TextDecoration.ITALIC, false);
    }

    /**
     * Translates hex + legacy colour codes to a plain §-coded String.
     * Useful for legacy-API locations (scoreboard entries, boss bars).
     */
    public static String colorizeString(String text) {
        if (text == null) return "";
        return ChatColor.translateAlternateColorCodes('&', translateHex(text));
    }

    /** Converts &#RRGGBB → §x§R§R§G§G§B§B */
    private static String translateHex(String text) {
        Matcher matcher = HEX_PATTERN.matcher(text);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            String hex = matcher.group(1);
            StringBuilder replacement = new StringBuilder("§x");
            for (char c : hex.toCharArray()) replacement.append('§').append(c);
            matcher.appendReplacement(sb, replacement.toString());
        }
        matcher.appendTail(sb);
        return sb.toString();
    }
}
