package com.pallux.smashmons.managers;

import com.pallux.smashmons.SmashMons;
import me.libraryaddict.disguise.DisguiseAPI;
import me.libraryaddict.disguise.disguisetypes.MobDisguise;
import me.libraryaddict.disguise.disguisetypes.DisguiseType;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;

import java.util.logging.Level;

public class DisguiseManager {

    private final SmashMons plugin;
    private boolean libsAvailable = false;

    public DisguiseManager(SmashMons plugin) {
        this.plugin = plugin;
        if (plugin.getServer().getPluginManager().getPlugin("LibsDisguises") != null) {
            libsAvailable = true;
            plugin.getLogger().info("Hooked into LibsDisguises.");
        } else {
            plugin.getLogger().warning("LibsDisguises not found! Mob disguises will be disabled.");
        }
    }

    public void disguise(Player player, EntityType entityType) {
        if (!libsAvailable) return;
        try {
            DisguiseType disguiseType = DisguiseType.getType(entityType);
            if (disguiseType == null) return;
            MobDisguise disguise = new MobDisguise(disguiseType);
            // Set baby for certain mobs to make them look like mini versions
            DisguiseAPI.disguiseToAll(player, disguise);
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Failed to disguise " + player.getName() + " as " + entityType, e);
        }
    }

    public void undisguise(Player player) {
        if (!libsAvailable) return;
        try {
            if (DisguiseAPI.isDisguised(player)) {
                DisguiseAPI.undisguiseToAll(player);
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Failed to undisguise " + player.getName(), e);
        }
    }

    public boolean isLibsAvailable() { return libsAvailable; }
}
