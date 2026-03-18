package com.pallux.smashmons.managers;

import com.pallux.smashmons.SmashMons;
import com.pallux.smashmons.game.Game;
import com.pallux.smashmons.game.GamePlayer;
import com.pallux.smashmons.game.GameState;
import com.pallux.smashmons.util.ColorUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.*;

import java.util.*;

/**
 * Scoreboard manager using the Paper Adventure API.
 *
 * Each line is rendered as a Scoreboard Team prefix, which supports full
 * Component/Adventure coloring with no character-limit truncation.
 * This avoids the §x hex color truncation bug that plagued the old
 * string-entry approach.
 */
public class ScoreboardManager {

    private final SmashMons plugin;
    private final Map<UUID, Scoreboard> playerBoards = new HashMap<>();

    // Team names for each line slot (max 15 lines)
    private static final int MAX_LINES = 15;

    public ScoreboardManager(SmashMons plugin) {
        this.plugin = plugin;
    }

    public void updateScoreboard(Game game) {
        for (UUID uuid : game.getPlayers().keySet()) {
            Player p = Bukkit.getPlayer(uuid);
            if (p == null) continue;
            GamePlayer gp = game.getGamePlayer(uuid);
            if (gp == null) continue;

            Scoreboard board = getOrCreate(uuid);
            ensureObjective(board);

            List<Component> lines = buildLines(game, gp);
            applyLines(board, lines);

            p.setScoreboard(board);
        }
    }

    // ── Line builders ─────────────────────────────────────────────────────────

    private List<Component> buildLines(Game game, GamePlayer gp) {
        if (game.getState() == GameState.WAITING || game.getState() == GameState.STARTING) {
            return buildWaitingLines(game);
        } else {
            return buildIngameLines(game, gp);
        }
    }

    private List<Component> buildWaitingLines(Game game) {
        List<Component> lines = new ArrayList<>();
        lines.add(sep());
        lines.add(label("Arena").append(val(game.getArena().getDisplayName())));
        lines.add(label("Players")
                .append(bright(String.valueOf(game.getPlayers().size())))
                .append(dim(" / "))
                .append(bright(String.valueOf(game.getArena().getMaxPlayers()))));
        lines.add(Component.empty());
        lines.add(gold("Waiting for players..."));
        lines.add(sep());
        lines.add(purple("play.yourserver.net"));
        return lines;
    }

    private List<Component> buildIngameLines(Game game, GamePlayer gp) {
        List<GamePlayer> sorted = game.getSortedPlayers();
        List<Component> lines = new ArrayList<>();

        lines.add(sep());
        lines.add(label("Round")
                .append(gold(String.valueOf(game.getCurrentRound())))
                .append(dim(" / "))
                .append(gold(String.valueOf(game.getTotalRounds()))));

        String rawKitName = gp.getKit() != null ? gp.getKit().getDisplayName() : "None";
        lines.add(label("Kit").append(ColorUtil.colorize(rawKitName)));

        lines.add(Component.empty());
        lines.add(white("Top Players").decorate(TextDecoration.BOLD));

        for (int i = 0; i < 3; i++) {
            String rank = "#" + (i + 1) + " ";
            if (i < sorted.size()) {
                Player tp = Bukkit.getPlayer(sorted.get(i).getUuid());
                String name = tp != null ? tp.getName() : "?";
                String pts  = String.valueOf(sorted.get(i).getPoints());
                Component rankComp = i == 0 ? gold(rank) : dim(rank);
                lines.add(rankComp.append(white(name)).append(dim("  ")).append(bright(pts)));
            } else {
                lines.add(dim(rank + "---  ").append(dim("0")));
            }
        }

        lines.add(Component.empty());
        lines.add(label("Points").append(green(String.valueOf(gp.getPoints()))));
        lines.add(sep());
        lines.add(purple("play.yourserver.net"));

        return lines;
    }

    // ── Apply lines via Teams ─────────────────────────────────────────────────

    /**
     * Each scoreboard line is a Team whose prefix IS the line content.
     * The team's "entry" is a unique invisible string (§0§r§0§r... pattern).
     * The Objective score controls vertical position.
     */
    private void applyLines(Scoreboard board, List<Component> lines) {
        Objective obj = board.getObjective("smash");
        if (obj == null) return;

        int total = Math.min(lines.size(), MAX_LINES);
        for (int i = 0; i < total; i++) {
            String teamName = "sm_line_" + i;
            String entry    = lineEntry(i); // unique invisible entry string

            Team team = board.getTeam(teamName);
            if (team == null) {
                team = board.registerNewTeam(teamName);
                team.addEntry(entry);
            }
            // Set the full colored Component as the prefix — no char limit
            team.prefix(lines.get(i));
            team.suffix(Component.empty());

            obj.getScore(entry).setScore(total - i);
        }

        // Remove any extra teams from previous renders with more lines
        for (int i = total; i < MAX_LINES; i++) {
            String entry = lineEntry(i);
            board.resetScores(entry);
            Team old = board.getTeam("sm_line_" + i);
            if (old != null) old.unregister();
        }
    }

    /**
     * Produces a unique invisible string for slot i using color codes that
     * render as empty. Uses combinations of §r§0 to guarantee uniqueness.
     */
    private String lineEntry(int i) {
        // §0 = black, §r = reset — these render as nothing visible
        // Each index gets a unique combination
        String base = "§" + Integer.toHexString(i % 16);
        return base + "§r".repeat((i / 16) + 1);
    }

    // ── Scoreboard setup ──────────────────────────────────────────────────────

    private void ensureObjective(Scoreboard board) {
        if (board.getObjective("smash") != null) return;
        Objective obj = board.registerNewObjective("smash", Criteria.DUMMY,
                ColorUtil.colorize(plugin.getMessageManager().getRaw("scoreboard-title")));
        obj.setDisplaySlot(DisplaySlot.SIDEBAR);
    }

    public void removeScoreboard(Player player) {
        Scoreboard board = playerBoards.remove(player.getUniqueId());
        if (board != null) {
            // Clean up all teams
            for (int i = 0; i < MAX_LINES; i++) {
                Team t = board.getTeam("sm_line_" + i);
                if (t != null) t.unregister();
            }
        }
        player.setScoreboard(Bukkit.getScoreboardManager().getNewScoreboard());
    }

    private Scoreboard getOrCreate(UUID uuid) {
        return playerBoards.computeIfAbsent(uuid,
                k -> Bukkit.getScoreboardManager().getNewScoreboard());
    }

    // ── Component helpers ─────────────────────────────────────────────────────

    /** Light grey label with a trailing space */
    private Component label(String text) {
        return Component.text(text + "  ", TextColor.color(0xCCCCCC))
                .decoration(TextDecoration.ITALIC, false);
    }
    /** Bright white value */
    private Component bright(String text) {
        return Component.text(text, NamedTextColor.WHITE)
                .decoration(TextDecoration.ITALIC, false);
    }
    private Component white(String text) {
        return Component.text(text, NamedTextColor.WHITE)
                .decoration(TextDecoration.ITALIC, false);
    }
    /** Dim light grey */
    private Component dim(String text) {
        return Component.text(text, TextColor.color(0xCCCCCC))
                .decoration(TextDecoration.ITALIC, false);
    }
    private Component gold(String text) {
        return Component.text(text, TextColor.color(0xFFD700))
                .decoration(TextDecoration.ITALIC, false);
    }
    private Component green(String text) {
        return Component.text(text, TextColor.color(0x6BFF6B))
                .decoration(TextDecoration.ITALIC, false);
    }
    private Component purple(String text) {
        return Component.text(text, TextColor.color(0xD946EF))
                .decoration(TextDecoration.ITALIC, false);
    }
    private Component val(String text) {
        return Component.text(text, NamedTextColor.WHITE)
                .decoration(TextDecoration.ITALIC, false);
    }
    private Component sep() {
        return Component.text("─────────────── ", TextColor.color(0xCCCCCC))
                .decoration(TextDecoration.ITALIC, false);
    }
}