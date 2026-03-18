package com.pallux.smashmons.util;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;

public final class LocationUtil {

    private LocationUtil() {}

    public static void save(ConfigurationSection section, String key, Location loc) {
        ConfigurationSection s = section.createSection(key);
        s.set("world", loc.getWorld().getName());
        s.set("x", loc.getX());
        s.set("y", loc.getY());
        s.set("z", loc.getZ());
        s.set("yaw", loc.getYaw());
        s.set("pitch", loc.getPitch());
    }

    public static Location load(ConfigurationSection section, String key) {
        ConfigurationSection s = section.getConfigurationSection(key);
        if (s == null) return null;
        String worldName = s.getString("world", "world");
        var world = Bukkit.getWorld(worldName);
        if (world == null) return null;
        return new Location(
                world,
                s.getDouble("x"),
                s.getDouble("y"),
                s.getDouble("z"),
                (float) s.getDouble("yaw"),
                (float) s.getDouble("pitch")
        );
    }

    public static String serialize(Location loc) {
        return loc.getWorld().getName() + "," + loc.getX() + "," + loc.getY() + "," + loc.getZ()
                + "," + loc.getYaw() + "," + loc.getPitch();
    }

    public static Location deserialize(String s) {
        String[] p = s.split(",");
        var world = Bukkit.getWorld(p[0]);
        if (world == null) return null;
        return new Location(world, Double.parseDouble(p[1]), Double.parseDouble(p[2]),
                Double.parseDouble(p[3]), Float.parseFloat(p[4]), Float.parseFloat(p[5]));
    }
}
