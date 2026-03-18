package com.pallux.smashmons.commands;

import com.pallux.smashmons.SmashMons;
import com.pallux.smashmons.arena.Arena;
import com.pallux.smashmons.data.PlayerData;
import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.util.*;

public class SmCommand implements CommandExecutor, TabCompleter {

    private final SmashMons plugin;

    public SmCommand(SmashMons plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!sender.hasPermission("sm.admin")) {
            plugin.getMessageManager().send(sender, "no-permission");
            return true;
        }
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {

            // /sm create <arenaname>
            case "create" -> {
                if (!(sender instanceof Player player)) { plugin.getMessageManager().send(sender, "player-only"); return true; }
                if (args.length < 2) { sender.sendMessage("/sm create <arenaname>"); return true; }
                String name = args[1];
                if (plugin.getArenaManager().getArena(name) != null) {
                    plugin.getMessageManager().send(sender, "arena-already-exists", Map.of("arena", name));
                    return true;
                }
                boolean created = plugin.getArenaManager().createArena(player, name);
                if (created) plugin.getMessageManager().send(sender, "arena-created", Map.of("arena", name));
                else plugin.getMessageManager().send(sender, "arena-no-selection");
            }

            // /sm delete <arenaname>
            case "delete" -> {
                if (args.length < 2) { sender.sendMessage("/sm delete <arenaname>"); return true; }
                String name = args[1];
                Arena arena = plugin.getArenaManager().getArena(name);
                if (arena == null) { plugin.getMessageManager().send(sender, "arena-not-found", Map.of("arena", name)); return true; }
                if (!arena.isAvailable()) { plugin.getMessageManager().send(sender, "arena-in-use"); return true; }
                plugin.getArenaManager().deleteArena(name);
                plugin.getMessageManager().send(sender, "arena-deleted", Map.of("arena", name));
            }

            // /sm setspawn <arenaname>
            case "setspawn" -> {
                if (!(sender instanceof Player player)) { plugin.getMessageManager().send(sender, "player-only"); return true; }
                if (args.length < 2) { sender.sendMessage("/sm setspawn <arenaname>"); return true; }
                String name = args[1];
                Arena arena = plugin.getArenaManager().getArena(name);
                if (arena == null) { plugin.getMessageManager().send(sender, "arena-not-found", Map.of("arena", name)); return true; }
                plugin.getArenaManager().addSpawnPoint(name, player.getLocation());
                int num = arena.getSpawnPoints().size();
                plugin.getMessageManager().send(sender, "spawn-set", Map.of("number", String.valueOf(num), "arena", name));
            }

            // /sm setcrystal <arenaname>
            case "setcrystal" -> {
                if (!(sender instanceof Player player)) { plugin.getMessageManager().send(sender, "player-only"); return true; }
                if (args.length < 2) { sender.sendMessage("/sm setcrystal <arenaname>"); return true; }
                String name = args[1];
                Arena arena = plugin.getArenaManager().getArena(name);
                if (arena == null) { plugin.getMessageManager().send(sender, "arena-not-found", Map.of("arena", name)); return true; }
                plugin.getArenaManager().addCrystalSpawn(name, player.getLocation());
                int num = arena.getCrystalSpawns().size();
                plugin.getMessageManager().send(sender, "crystal-set", Map.of("number", String.valueOf(num), "arena", name));
            }

            // /sm remove <crystal|spawnpoint> <arena> <number>
            case "remove" -> {
                if (args.length < 4) { sender.sendMessage("/sm remove <crystal|spawnpoint> <arena> <number>"); return true; }
                String type = args[1].toLowerCase();
                String arenaName = args[2];
                Arena arena = plugin.getArenaManager().getArena(arenaName);
                if (arena == null) { plugin.getMessageManager().send(sender, "arena-not-found", Map.of("arena", arenaName)); return true; }
                int index;
                try { index = Integer.parseInt(args[3]) - 1; } catch (NumberFormatException e) {
                    plugin.getMessageManager().send(sender, "invalid-number"); return true;
                }
                if (type.equals("spawnpoint") || type.equals("spawn")) {
                    boolean removed = arena.removeSpawnPoint(index);
                    if (removed) {
                        plugin.getArenaManager().save();
                        plugin.getMessageManager().send(sender, "removed-spawnpoint",
                                Map.of("number", args[3], "arena", arenaName));
                    } else {
                        plugin.getMessageManager().send(sender, "remove-invalid-number");
                    }
                } else if (type.equals("crystal")) {
                    boolean removed = arena.removeCrystalSpawn(index);
                    if (removed) {
                        plugin.getArenaManager().save();
                        plugin.getMessageManager().send(sender, "removed-crystal",
                                Map.of("number", args[3], "arena", arenaName));
                    } else {
                        plugin.getMessageManager().send(sender, "remove-invalid-number");
                    }
                }
            }

            // /sm setserverspawn
            case "setserverspawn" -> {
                if (!(sender instanceof Player player)) { plugin.getMessageManager().send(sender, "player-only"); return true; }
                Location loc = player.getLocation();
                plugin.getConfig().set("server-spawn.world", loc.getWorld().getName());
                plugin.getConfig().set("server-spawn.x", loc.getX());
                plugin.getConfig().set("server-spawn.y", loc.getY());
                plugin.getConfig().set("server-spawn.z", loc.getZ());
                plugin.getConfig().set("server-spawn.yaw", loc.getYaw());
                plugin.getConfig().set("server-spawn.pitch", loc.getPitch());
                plugin.saveConfig();
                plugin.getMessageManager().send(sender, "server-spawn-set");
            }

            // /sm smashcoins set <player> <amount>
            case "smashcoins" -> {
                if (args.length < 4 || !args[1].equalsIgnoreCase("set")) {
                    sender.sendMessage("/sm smashcoins set <player> <amount>");
                    return true;
                }
                Player target = Bukkit.getPlayer(args[2]);
                if (target == null) { plugin.getMessageManager().send(sender, "player-not-found", Map.of("player", args[2])); return true; }
                long amount;
                try { amount = Long.parseLong(args[3]); } catch (NumberFormatException e) {
                    plugin.getMessageManager().send(sender, "invalid-number"); return true;
                }
                PlayerData pd = plugin.getPlayerDataManager().get(target);
                pd.setSmashCoins(amount);
                plugin.getPlayerDataManager().saveAsync(target.getUniqueId());
                plugin.getMessageManager().send(sender, "smashcoins-set",
                        Map.of("player", target.getName(), "amount", String.valueOf(amount)));
            }

            // /sm reload
            case "reload" -> {
                plugin.reloadConfig();
                plugin.getMessageManager().reload();
                plugin.getArenaManager().load();
                plugin.getKitManager().load();
                plugin.getAugmentManager().load();
                plugin.getMessageManager().send(sender, "reload-success");
            }

            default -> sendHelp(sender);
        }
        return true;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.DARK_PURPLE + "" + ChatColor.BOLD + "=== SmashMons Admin Commands ===");
        sender.sendMessage(ChatColor.GOLD + "/sm create <name>" + ChatColor.GRAY + " - Create arena from WE selection");
        sender.sendMessage(ChatColor.GOLD + "/sm delete <name>" + ChatColor.GRAY + " - Delete an arena");
        sender.sendMessage(ChatColor.GOLD + "/sm setspawn <name>" + ChatColor.GRAY + " - Add a spawn point");
        sender.sendMessage(ChatColor.GOLD + "/sm setcrystal <name>" + ChatColor.GRAY + " - Add a crystal spawn");
        sender.sendMessage(ChatColor.GOLD + "/sm remove <crystal|spawnpoint> <arena> <number>" + ChatColor.GRAY + " - Remove a point");
        sender.sendMessage(ChatColor.GOLD + "/sm setserverspawn" + ChatColor.GRAY + " - Set server spawn");
        sender.sendMessage(ChatColor.GOLD + "/sm smashcoins set <player> <amount>" + ChatColor.GRAY + " - Set SmashCoins");
        sender.sendMessage(ChatColor.GOLD + "/sm reload" + ChatColor.GRAY + " - Reload plugin configs");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        if (!sender.hasPermission("sm.admin")) return Collections.emptyList();
        if (args.length == 1) {
            return filterStart(args[0], List.of("create", "delete", "setspawn", "setcrystal",
                    "remove", "setserverspawn", "smashcoins", "reload"));
        }
        if (args.length == 2) {
            return switch (args[0].toLowerCase()) {
                case "delete", "setspawn", "setcrystal" -> filterStart(args[1], plugin.getArenaManager().getArenaNames());
                case "remove" -> filterStart(args[1], List.of("crystal", "spawnpoint"));
                case "smashcoins" -> filterStart(args[1], List.of("set"));
                default -> Collections.emptyList();
            };
        }
        if (args.length == 3) {
            return switch (args[0].toLowerCase()) {
                case "remove" -> filterStart(args[2], plugin.getArenaManager().getArenaNames());
                case "smashcoins" -> filterStart(args[2], Bukkit.getOnlinePlayers().stream().map(Player::getName).toList());
                default -> Collections.emptyList();
            };
        }
        return Collections.emptyList();
    }

    private List<String> filterStart(String input, List<String> options) {
        return options.stream().filter(s -> s.toLowerCase().startsWith(input.toLowerCase())).toList();
    }
}
