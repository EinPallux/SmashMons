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
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
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
 *  1. Action bar  — cooldown / energy status for the held ability item.
 *  2. Slow health regen — 1 HP (half a heart) every 10 seconds.
 *  3. Health display BELOW NAME — shows each player's current HP to enemies.
 *
 * HEALTH-BELOW-NAME APPROACH:
 *   We register a BELOW_NAME objective on every in-game player's personal
 *   scoreboard and manually set the integer score for every other in-game
 *   player's name on that board.  This way Player B sees Player A's HP tag
 *   without relying on Criteria.HEALTH auto-tracking (which can conflict with
 *   the sidebar objective registered by ScoreboardManager).
 *
 * REGEN FIX:
 *   The task runs every 20 ticks (1 real second).  A local counter increments
 *   each second; when it reaches 10 we apply +1 HP (= half a heart) and reset.
 *   We use setAttribute / setHealth directly — no Bukkit regen event is fired
 *   so the EventHandler in GameListener cannot block it.
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
            /** Counts elapsed seconds; regen fires every 10. */
            int regenCounter = 0;

            @Override
            public void run() {
                regenCounter++;
                boolean doRegen = regenCounter >= 10;
                if (doRegen) regenCounter = 0;

                Collection<Game> games = plugin.getGameManager().getActiveGames();

                for (Game game : games) {
                    if (game.getState() != GameState.IN_ROUND) continue;

                    // ── Snapshot health of every alive player in this game ──────
                    // We store ceil(health) as the integer shown below the name.
                    Map<String, Integer> healthMap = new HashMap<>();
                    for (UUID uuid : game.getPlayers().keySet()) {
                        Player p = Bukkit.getPlayer(uuid);
                        GamePlayer gp = game.getGamePlayer(uuid);
                        if (p == null || gp == null || !gp.isAlive()) continue;
                        // Display half-hearts: 20 HP = 10 ♥, show as integer HP value
                        int displayHp = (int) Math.ceil(p.getHealth());
                        healthMap.put(p.getName(), displayHp);
                    }

                    // ── Per-viewer updates ─────────────────────────────────────
                    for (UUID viewerUuid : game.getPlayers().keySet()) {
                        Player viewer = Bukkit.getPlayer(viewerUuid);
                        if (viewer == null) continue;

                        GamePlayer viewerGp = game.getGamePlayer(viewerUuid);
                        if (viewerGp == null) continue;

                        // ── Health-below-name ──────────────────────────────────
                        ensureHealthObjective(viewer);
                        Scoreboard board = viewer.getScoreboard();
                        if (board != null) {
                            Objective obj = board.getObjective(HEALTH_OBJ);
                            if (obj != null) {
                                for (Map.Entry<String, Integer> entry : healthMap.entrySet()) {
                                    obj.getScore(entry.getKey()).setScore(entry.getValue());
                                }
                            }
                        }

                        // ── Action bar (alive players only) ───────────────────
                        if (viewerGp.isAlive()) {
                            sendActionBar(viewer, game, viewerGp);
                        }

                        // ── Health regen (alive players only) ─────────────────
                        if (doRegen && viewerGp.isAlive()) {
                            applyRegen(viewer);
                        }
                    }
                }

                // Players not in any game → clear action bar
                for (Player online : Bukkit.getOnlinePlayers()) {
                    if (!plugin.getGameManager().isInGame(online.getUniqueId())) {
                        online.sendActionBar(Component.empty());
                    }
                }
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    // ── Regen helper ──────────────────────────────────────────────────────────

    /**
     * Adds exactly 1.0 HP (half a heart) up to the player's current max health.
     * Retrieves max health from the attribute to respect kit-specific values and
     * augment-based extra health. Falls back to 20.0 if the attribute is null.
     */
    private void applyRegen(Player player) {
        try {
            AttributeInstance maxHpAttr = player.getAttribute(Attribute.MAX_HEALTH);
            double maxHp = (maxHpAttr != null) ? maxHpAttr.getValue() : 20.0;
            double current = player.getHealth();
            if (current < maxHp) {
                // +1.0 internal HP = half a heart displayed
                player.setHealth(Math.min(maxHp, current + 1.0));
            }
        } catch (Exception ignored) {
            // Silently swallow any edge-case exception (e.g. player dying at same tick)
        }
    }

    // ── Health objective setup ────────────────────────────────────────────────

    /**
     * Ensures the BELOW_NAME health objective exists on this player's scoreboard.
     * We use a DUMMY criterion and update the scores manually each second so that
     * the displayed integer (HP) does not conflict with auto-tracking.
     */
    private void ensureHealthObjective(Player player) {
        try {
            Scoreboard board = player.getScoreboard();
            if (board == null) return;

            Objective obj = board.getObjective(HEALTH_OBJ);
            if (obj == null) {
                obj = board.registerNewObjective(
                        HEALTH_OBJ,
                        Criteria.DUMMY,
                        Component.text("❤", TextColor.color(0xFF6B6B)));
            }
            if (obj.getDisplaySlot() != DisplaySlot.BELOW_NAME) {
                obj.setDisplaySlot(DisplaySlot.BELOW_NAME);
            }
        } catch (Exception ignored) {
            // May throw if scoreboard is being rebuilt concurrently
        }
    }

    /** Removes the health objective from a player's board (called on game leave). */
    public void removeHealthObjective(Player player) {
        try {
            Scoreboard board = player.getScoreboard();
            if (board != null) {
                Objective obj = board.getObjective(HEALTH_OBJ);
                if (obj != null) obj.unregister();
            }
        } catch (Exception ignored) {}
        player.sendActionBar(Component.empty());
    }

    // ── Action bar ────────────────────────────────────────────────────────────

    private void sendActionBar(Player player, Game game, GamePlayer gp) {
        Kit kit = gp.getKit();
        if (kit == null) return;

        int slot = player.getInventory().getHeldItemSlot();

        // Slot 8 = ultimate item
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

        // Energy kits: show current energy vs cost
        if (kit.isEnergy()) {
            int cost    = ability.getEnergyCost();
            int current = (int) gp.getEnergy();
            String msg  = current >= cost
                    ? "&#D946EF⚡ &#FFFFFF" + ability.getName() + " &#CCCCCC— &#6BFF6BReady &#CCCCCC(" + current + "/100)"
                    : "&#D946EF⚡ &#FFFFFF" + ability.getName() + " &#CCCCCC— &#FF6B6BNeed " + cost + " energy &#CCCCCC(" + current + "/100)";
            player.sendActionBar(ColorUtil.colorize(msg));
            return;
        }

        // Cooldown kits
        if (gp.isOnCooldown(abilityKey)) {
            long ms   = gp.getRemainingCooldown(abilityKey);
            long secs = (ms / 1000) + 1;
            String bar = buildCooldownBar(ms, ability.getCooldownSeconds() * 1000L);
            player.sendActionBar(ColorUtil.colorize(
                    "&#FFFFFF" + ability.getName() + " &#CCCCCC— &#FF6B6B" + secs + "s  " + bar));
        } else {
            player.sendActionBar(ColorUtil.colorize(
                    "&#FFFFFF" + ability.getName() + " &#CCCCCC— &#6BFF6B✔ Ready!"));
        }
    }

    private String buildCooldownBar(long remainingMs, long totalMs) {
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
        // Remove any leftover objective from a previous session
        removeHealthObjective(event.getPlayer());
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    public void cancel() {
        if (tickTask != null) tickTask.cancel();
    }
}