package com.pallux.smashmons.placeholders;

import com.pallux.smashmons.SmashMons;
import com.pallux.smashmons.data.PlayerData;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class SmashMonsExpansion extends PlaceholderExpansion {

    private final SmashMons plugin;

    public SmashMonsExpansion(SmashMons plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() { return "sm"; }

    @Override
    public @NotNull String getAuthor() { return "Pallux"; }

    @Override
    public @NotNull String getVersion() { return plugin.getDescription().getVersion(); }

    @Override
    public boolean persist() { return true; }

    @Override
    public @Nullable String onPlaceholderRequest(Player player, @NotNull String identifier) {
        if (player == null) return "";
        PlayerData pd = plugin.getPlayerDataManager().get(player);
        return switch (identifier.toLowerCase()) {
            case "smashcoins" -> String.valueOf(pd.getSmashCoins());
            case "kills"      -> String.valueOf(pd.getKills());
            case "deaths"     -> String.valueOf(pd.getDeaths());
            case "kd"         -> String.valueOf(pd.getKD());
            case "won"        -> String.valueOf(pd.getWins());
            default           -> null;
        };
    }
}
