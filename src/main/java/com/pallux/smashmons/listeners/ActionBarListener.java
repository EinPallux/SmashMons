package com.pallux.smashmons.listeners;

import com.pallux.smashmons.SmashMons;
import com.pallux.smashmons.game.Game;
import com.pallux.smashmons.game.GamePlayer;
import com.pallux.smashmons.game.GameState;
import com.pallux.smashmons.kits.Kit;
import com.pallux.smashmons.kits.KitAbility;
import com.pallux.smashmons.util.ColorUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scoreboard.*;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

/**
 * Manages three HUD features for in-game players:
 *
 *  1. Action bar — cooldown / energy status for the held ability item.
 *  2. Slow health regen — 0.5 HP every 10 seconds (vanilla regen is blocked).
 *  3. Health bar BELOW NAME — visible to all players in the same game.
 *
 * HOW THE HEALTH BAR WORKS:
 *   Minecraft's BELOW_NAME display slot shows a number + label beneath every
 *   player's name tag that is visible to others. For Player B to see Player A's
 *   health, Player B's scoreboard must have a BELOW_NAME objective with an
 *   entry keyed to Player A's name. We therefore register the objective on
 *   EVERY in-game player's board and set the score for EVERY other in-game
 *   player on that board — updated every second.
 */
public class ActionBarListener implements Listener {

    private static final String HEALTH_OBJ = "sm_health";
    private final SmashMons plugin;
    private BukkitTask tickTask;

    public ActionBarListener(SmashMons plugin) {
        this.plugin = plugin;
        startTickTask();
    }

    // ── Main tick (every 1 second) ────────────────────────────────────────────

    private void startTickTask() {
        tickTask = new BukkitRunnable() {
            int regenTick = 0;

            @Override
            public void run() {
                regenTick++;
                boolean doRegen = regenTick >= 10;
                if (doRegen) regenTick = 0;

                // Collect all active in-game rounds
                Collection<Game> games = plugin.getGameManager().getActiveGames();

                // For each active game, update health bars, action bar, regen
                for (Game game : games) {
                    if (game.getState() != GameState.IN_ROUND) continue;

                    // Snapshot current health of every alive player in this game
                    Map<String, Integer> healthMap = new HashMap<>();
                    for (UUID uuid : game.getPlayers().keySet()) {
                        Player p = Bukkit.getPlayer(uuid);
                        GamePlayer gp = game.getGamePlayer(uuid);
                        if (p == null || gp == null || !gp.isAlive()) continue;
                        healthMap.put(p.getName(), (int) Math.ceil(p.getHealth()));
                    }

                    // Update every viewer's board
                    for (UUID viewerUuid : game.getPlayers().keySet()) {
                        Player viewer = Bukkit.getPlayer(viewerUuid);
                        if (viewer == null) continue;
                        GamePlayer viewerGp = game.getGamePlayer(viewerUuid);
                        if (viewerGp == null) continue;

                        // ── Health bar ─────────────────────────────────────
                        ensureHealthObjective(viewer);
                        Scoreboard board = viewer.getScoreboard();
                        if (board != null) {
                            Objective obj = board.getObjective(HEALTH_OBJ);
                            if (obj != null) {
                                // Set the health score for every player in the game
                                // so this viewer sees all health bars
                                for (Map.Entry<String, Integer> entry : healthMap.entrySet()) {
                                    obj.getScore(entry.getKey()).setScore(entry.getValue());
                                }
                            }
                        }

                        // ── Action bar (alive players only) ────────────────
                        if (viewerGp.isAlive()) {
                            sendActionBar(viewer, game, viewerGp);
                        }

                        // ── Regen ──────────────────────────────────────────
                        if (doRegen && viewerGp.isAlive()) {
                            double maxHp = viewer.getAttribute(
                                    org.bukkit.attribute.Attribute.MAX_HEALTH).getValue();
                            if (viewer.getHealth() < maxHp) {
                                viewer.setHealth(Math.min(maxHp, viewer.getHealth() + 1.0));
                            }
                        }
                    }
                }

                // Players no longer in a game — clear action bar
                for (Player online : Bukkit.getOnlinePlayers()) {
                    if (!plugin.getGameManager().isInGame(online.getUniqueId())) {
                        online.sendActionBar(Component.empty());
                    }
                }
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    // ── Health objective setup ────────────────────────────────────────────────

    /**
     * Ensures the BELOW_NAME health objective exists on this player's scoreboard.
     * Called every tick so it survives ScoreboardManager rebuilds.
     */
    private void ensureHealthObjective(Player player) {
        try {
            Scoreboard board = player.getScoreboard();
            if (board == null) return;

            Objective obj = board.getObjective(HEALTH_OBJ);
            if (obj == null) {
                obj = board.registerNewObjective(
                        HEALTH_OBJ,
                        Criteria.HEALTH,
                        Component.text("❤", TextColor.color(0xFF6B6B)));
            }
            // Always (re-)set display slot in case a scoreboard rebuild cleared it
            if (obj.getDisplaySlot() != DisplaySlot.BELOW_NAME) {
                obj.setDisplaySlot(DisplaySlot.BELOW_NAME);
            }
        } catch (Exception e) {
            // Silently ignore — may throw if objective was just unregistered
        }
    }

    /** Removes the health objective from a player's board (called on game leave). */
    public void removeHealthObjective(Player player) {
        try {
            Scoreboard board = player.getScoreboard();
            if (board == null) return;
            Objective obj = board.getObjective(HEALTH_OBJ);
            if (obj != null) obj.unregister();
        } catch (Exception ignored) {}
        player.sendActionBar(Component.empty());
    }

    // ── Action bar ────────────────────────────────────────────────────────────

    private void sendActionBar(Player player, Game game, GamePlayer gp) {
        Kit kit = gp.getKit();
        if (kit == null) return;

        int slot = player.getInventory().getHeldItemSlot();

        // Slot 8 = ultimate
        if (slot == 8 && kit.getUltimateAbility() != null) {
            String msg = gp.hasUltimateCrystal()
                    ? "&#FFD700&l✦ Ultimate Ready &#FFD700— Right-click to unleash!"
                    : "&#CCCCCC✦ Ultimate &#FF6B6B— Collect a Crystal to activate";
            player.sendActionBar(ColorUtil.colorize(msg));
            return;
        }

        KitAbility ability = null;
        String abilityKey  = null;

        if (slot == 0 && kit.getPrimaryAbility() != null) {
            ability    = kit.getPrimaryAbility();
            abilityKey = kit.getId() + "_primary";
        } else if (slot == 1 && kit.getSecondaryAbility() != null) {
            ability    = kit.getSecondaryAbility();
            abilityKey = kit.getId() + "_secondary";
        }

        if (ability == null) {
            player.sendActionBar(Component.empty());
            return;
        }

        // Energy kits: show energy status
        if (kit.isEnergy()) {
            int cost    = ability.getEnergyCost();
            int current = (int) gp.getEnergy();
            String msg  = current >= cost
                    ? "&#D946EF⚡ &#FFFFFF" + ability.getName() + " &#CCCCCC— &#6BFF6BReady &#CCCCCC(" + current + "/100)"
                    : "&#D946EF⚡ &#FFFFFF" + ability.getName() + " &#CCCCCC— &#FF6B6BNeed " + cost + " &#CCCCCC(" + current + "/100)";
            player.sendActionBar(ColorUtil.colorize(msg));
            return;
        }

        // Cooldown kits
        if (gp.isOnCooldown(abilityKey)) {
            long ms   = gp.getRemainingCooldown(abilityKey);
            long secs = (ms / 1000) + 1;
            String bar = buildBar(ms, ability.getCooldownSeconds() * 1000L);
            player.sendActionBar(ColorUtil.colorize(
                    "&#FFFFFF" + ability.getName() + " &#CCCCCC— &#FF6B6B" + secs + "s  " + bar));
        } else {
            player.sendActionBar(ColorUtil.colorize(
                    "&#FFFFFF" + ability.getName() + " &#CCCCCC— &#6BFF6B✔ Ready!"));
        }
    }

    private String buildBar(long remainingMs, long totalMs) {
        if (totalMs <= 0) return "";
        int filled = (int) Math.ceil(10.0 * remainingMs / totalMs);
        filled = Math.max(0, Math.min(10, filled));
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 10; i++) {
            sb.append(i < filled ? "&#FF6B6B█" : "&#6BFF6B█");
        }
        return sb.toString();
    }

    // ── Events ────────────────────────────────────────────────────────────────

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        removeHealthObjective(event.getPlayer());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        // Clean slate — remove any leftover objective from a previous session
        removeHealthObjective(event.getPlayer());
    }

    // ── Lifecycle ────────────────────────────────────────────────────────────

    public void cancel() {
        if (tickTask != null) tickTask.cancel();
    }
}