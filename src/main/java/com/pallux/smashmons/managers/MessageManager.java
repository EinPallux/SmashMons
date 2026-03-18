package com.pallux.smashmons.managers;

import com.pallux.smashmons.SmashMons;
import com.pallux.smashmons.util.ColorUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.Map;

public class MessageManager {

    private final SmashMons plugin;
    private FileConfiguration messages;
    private String prefix;

    public MessageManager(SmashMons plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        File file = new File(plugin.getDataFolder(), "lang/messages.yml");
        messages = YamlConfiguration.loadConfiguration(file);
        prefix = messages.getString("prefix", "&8[&dSmashMons&8] ");
    }

    public String getRaw(String key) {
        return messages.getString(key, "&cMissing message: " + key);
    }

    public String format(String key, Map<String, String> placeholders) {
        String msg = getRaw(key).replace("%prefix%", prefix);
        for (Map.Entry<String, String> e : placeholders.entrySet()) {
            msg = msg.replace("%" + e.getKey() + "%", e.getValue());
        }
        return msg;
    }

    public String format(String key) {
        return format(key, Map.of());
    }

    public Component component(String key) {
        return ColorUtil.colorize(format(key));
    }

    public Component component(String key, Map<String, String> placeholders) {
        return ColorUtil.colorize(format(key, placeholders));
    }

    public void send(CommandSender sender, String key) {
        sender.sendMessage(component(key));
    }

    public void send(CommandSender sender, String key, Map<String, String> placeholders) {
        sender.sendMessage(component(key, placeholders));
    }

    public void broadcast(String key, Map<String, String> placeholders) {
        Component msg = component(key, placeholders);
        plugin.getServer().getOnlinePlayers().forEach(p -> p.sendMessage(msg));
    }

    public FileConfiguration getMessages() { return messages; }
    public String getPrefix() { return prefix; }
}
