package com.pallux.smashmons.game;

import com.pallux.smashmons.SmashMons;
import com.pallux.smashmons.arena.Arena;
import com.pallux.smashmons.arena.ArenaState;
import com.pallux.smashmons.augments.Augment;
import com.pallux.smashmons.gui.AugmentGui;
import com.pallux.smashmons.gui.KitSelectGui;
import com.pallux.smashmons.kits.Kit;
import com.pallux.smashmons.util.ColorUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.*;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

/**
 * One Game = one Arena's full match lifecycle.
 *
 * State flow:
 *   WAITING → (all kits chosen + min players) → STARTING (countdown)
 *   STARTING → IN_ROUND (round starts, players unfrozen)
 *   IN_ROUND → ROUND_ENDING (1 player left) → AUGMENT_SELECT → back to IN_ROUND
 *   After totalRounds → GAME_ENDING
 */
public class Game {

    private final SmashMons plugin;
    private final Arena arena;
    private final Map<UUID, GamePlayer> players = new LinkedHashMap<>();
    private GameState state = GameState.WAITING;
    private int currentRound = 0;
    private final int totalRounds;
    private BukkitTask countdownTask;
    private BukkitTask crystalTask;
    private BukkitTask energyRegenTask;
    private final List<EnderCrystal> spawnedCrystals = new ArrayList<>();
    private final Map<UUID, List<Augment>> pendingAugments = new HashMap<>();
    // Players who joined but haven't yet picked a kit
    private final Set<UUID> pendingKitSelect = new HashSet<>();

    public Game(SmashMons plugin, Arena arena) {
        this.plugin = plugin;
        this.arena = arena;
        this.totalRounds = plugin.getConfig().getInt("game.rounds", 4);
    }

    // ── Player Management ─────────────────────────────────────────────────────

    public boolean addPlayer(Player player) {
        if (players.size() >= arena.getMaxPlayers()) return false;
        if (players.containsKey(player.getUniqueId())) return false;

        players.put(player.getUniqueId(), new GamePlayer(player.getUniqueId()));
        pendingKitSelect.add(player.getUniqueId());

        player.teleport(getRandomSpawn());
        player.setGameMode(GameMode.ADVENTURE);
        player.getInventory().clear();
        player.setMaxHealth(20.0);
        player.setHealth(20.0);
        player.setFoodLevel(20);
        player.setSaturation(20f);
        freezePlayer(player);

        sendToAll("joined-arena", Map.of("player", player.getName(), "arena", arena.getDisplayName()));

        // Open kit GUI immediately on join
        KitSelectGui.open(plugin, player, this);
        plugin.getMessageManager().send(player, "choose-kit");
        plugin.getScoreboardManager().updateScoreboard(this);
        return true;
    }

    public void removePlayer(Player player) {
        GamePlayer gp = players.remove(player.getUniqueId());
        pendingKitSelect.remove(player.getUniqueId());
        if (gp == null) return;

        cleanupPlayerEffects(player, gp);
        player.teleport(getServerSpawn());
        player.setGameMode(GameMode.SURVIVAL);
        player.setMaxHealth(20.0);
        player.setHealth(20.0);
        player.setFoodLevel(20);
        player.getInventory().clear();
        unfreezePlayer(player);
        plugin.getScoreboardManager().removeScoreboard(player);
        plugin.getActionBarListener().removeHealthObjective(player);

        // If a round is active, check if it's now over (only 1 player left)
        if (state == GameState.IN_ROUND) checkRoundEnd();

        // If countdown was running and we now lack enough players or kits, cancel it
        if (state == GameState.STARTING) {
            int minPlayers = plugin.getConfig().getInt("game.min-players", 2);
            if (players.size() < minPlayers || !pendingKitSelect.isEmpty()) {
                cancelCountdown();
            }
        }

        if (players.isEmpty()) shutdown();
    }

    private void cleanupPlayerEffects(Player player, GamePlayer gp) {
        plugin.getDisguiseManager().undisguise(player);
        if (gp.getEnergyBar() != null) {
            gp.getEnergyBar().removePlayer(player);
            gp.getEnergyBar().setVisible(false);
        }
        player.clearActivePotionEffects();
        plugin.getScoreboardManager().removeScoreboard(player);
    }

    // ── Freeze / Unfreeze ─────────────────────────────────────────────────────

    private void freezePlayer(Player player) {
        // Invisible Slowness 128 + JumpBoost 128 locks movement and jumping
        player.addPotionEffect(new PotionEffect(
                PotionEffectType.SLOWNESS, Integer.MAX_VALUE, 127, false, false, false));
        player.addPotionEffect(new PotionEffect(
                PotionEffectType.JUMP_BOOST, Integer.MAX_VALUE, 128, false, false, false));
        player.setAllowFlight(false);
    }

    private void unfreezePlayer(Player player) {
        player.removePotionEffect(PotionEffectType.SLOWNESS);
        player.removePotionEffect(PotionEffectType.JUMP_BOOST);
    }

    private void unfreezeAll() {
        for (UUID uuid : players.keySet()) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) unfreezePlayer(p);
        }
    }

    // ── Kit Assignment ────────────────────────────────────────────────────────

    public void assignKit(UUID uuid, Kit kit) {
        GamePlayer gp = players.get(uuid);
        if (gp == null) return;
        gp.setKit(kit);
        pendingKitSelect.remove(uuid);

        Player p = Bukkit.getPlayer(uuid);
        if (p != null) {
            p.closeInventory();
            plugin.getMessageManager().send(p, "kit-chosen",
                    Map.of("kit", kit.getDisplayName()));
        }

        tryStartCountdown();
        plugin.getScoreboardManager().updateScoreboard(this);
    }

    private void tryStartCountdown() {
        int minPlayers = plugin.getConfig().getInt("game.min-players", 2);
        if (players.size() >= minPlayers
                && pendingKitSelect.isEmpty()
                && state == GameState.WAITING) {
            startCountdown();
        }
    }

    // ── Countdown ─────────────────────────────────────────────────────────────

    private void startCountdown() {
        if (state != GameState.WAITING) return;
        state = GameState.STARTING;
        arena.setState(ArenaState.STARTING);
        int countdownSec = plugin.getConfig().getInt("game.countdown-seconds", 60);
        plugin.getScoreboardManager().updateScoreboard(this);

        countdownTask = new BukkitRunnable() {
            int sec = countdownSec;

            @Override
            public void run() {
                int minPlayers = plugin.getConfig().getInt("game.min-players", 2);
                if (players.size() < minPlayers || !pendingKitSelect.isEmpty()) {
                    cancelCountdown();
                    return;
                }
                if (sec <= 0) { cancel(); startRound(); return; }

                if (sec <= 5 || sec == 10 || sec == 30 || sec == 60) {
                    sendToAllRaw("game-countdown", Map.of(
                            "seconds", String.valueOf(sec),
                            "players", String.valueOf(players.size()),
                            "max", String.valueOf(arena.getMaxPlayers())));
                    if (sec <= 5) broadcastTitle("&#FFD700&l" + sec, "&#AAAAAA Get ready...", 2, 16, 2);
                }
                sec--;
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    private void cancelCountdown() {
        if (countdownTask != null) { countdownTask.cancel(); countdownTask = null; }
        state = GameState.WAITING;
        arena.setState(ArenaState.WAITING);
        plugin.getScoreboardManager().updateScoreboard(this);
    }

    // ── Round Logic ──────────────────────────────────────────────────────────

    private void startRound() {
        currentRound++;
        state = GameState.IN_ROUND;
        arena.setState(ArenaState.IN_GAME);

        // Reset per-round state for every player
        for (GamePlayer gp : players.values()) {
            gp.setAlive(true);
            gp.setFinishedRound(false);
            gp.setHasUltimateCrystal(false);
            gp.clearCooldowns();
            gp.setEnergy(100.0);
            gp.resetRoundKills();
            gp.setFinalStandTriggered(false);
            gp.recomputeAugmentStats();
        }

        // Teleport all players to random (deduplicated) spawn points
        List<Location> spawns = new ArrayList<>(arena.getSpawnPoints());
        Collections.shuffle(spawns);
        int i = 0;
        for (Map.Entry<UUID, GamePlayer> entry : players.entrySet()) {
            Player p = Bukkit.getPlayer(entry.getKey());
            if (p == null) continue;
            GamePlayer gp = entry.getValue();
            Kit kit = gp.getKit();

            Location spawnLoc = spawns.get(i % spawns.size());
            p.teleport(spawnLoc);
            p.setGameMode(GameMode.ADVENTURE);
            p.clearActivePotionEffects();

            double maxHp = kit != null ? kit.getHealth() + gp.getExtraHealth() : 20.0;
            p.setMaxHealth(maxHp);
            p.setHealth(maxHp);
            p.setFoodLevel(20);
            p.setSaturation(0f);        // 0 saturation = no vanilla natural regen
            p.setExhaustion(0f);

            // Speed bonus
            if (kit != null && kit.getSpeedBonus() > 0) {
                int amp = Math.max(0, (int) (kit.getSpeedBonus() / 0.05) - 1);
                p.addPotionEffect(new PotionEffect(
                        PotionEffectType.SPEED, Integer.MAX_VALUE, amp, true, false, false));
            }
            // Blaze fire immunity
            if (kit != null && kit.getMobType() == EntityType.BLAZE) {
                p.addPotionEffect(new PotionEffect(
                        PotionEffectType.FIRE_RESISTANCE, Integer.MAX_VALUE, 0, true, false, false));
            }

            applyPassiveAugments(p, gp);
            setupInventory(p, gp);
            if (kit != null) plugin.getDisguiseManager().disguise(p, kit.getMobType());
            if (kit != null && kit.isEnergy()) setupEnergyBar(p, gp);

            // Unfreeze and enable double-jump
            unfreezePlayer(p);
            p.setAllowFlight(true);
            i++;
        }

        startCrystalSpawning();
        startEnergyRegen();

        broadcastTitle("&#FFD700&lRound " + currentRound, "&#AAAAAA of " + totalRounds, 5, 30, 10);
        sendToAllRaw("game-round-start", Map.of(
                "round", String.valueOf(currentRound),
                "total", String.valueOf(totalRounds)));
        plugin.getScoreboardManager().updateScoreboard(this);
    }

    // ── Inventory / Ability Items ─────────────────────────────────────────────

    private void setupInventory(Player p, GamePlayer gp) {
        p.getInventory().clear();
        Kit kit = gp.getKit();
        if (kit == null) return;
        if (kit.getPrimaryAbility()   != null) p.getInventory().setItem(0, buildAbilityItem(kit.getPrimaryAbility()));
        if (kit.getSecondaryAbility() != null) p.getInventory().setItem(1, buildAbilityItem(kit.getSecondaryAbility()));
        if (kit.getUltimateAbility()  != null) p.getInventory().setItem(8, buildAbilityItem(kit.getUltimateAbility()));
        p.getInventory().setHeldItemSlot(0);
    }

    private ItemStack buildAbilityItem(com.pallux.smashmons.kits.KitAbility ability) {
        boolean isEnergy = ability.getEnergyCost() > 0;
        String statLine = isEnergy
                ? "&#DD44FF⚡ " + ability.getEnergyCost() + " Energy"
                : "&#44DDFF⏱ " + ability.getCooldownSeconds() + "s Cooldown";
        return new com.pallux.smashmons.util.ItemBuilder(ability.getMaterial())
                .name("&#FFFFFF&l" + ability.getName())
                .lore("&#AAAAAA" + ability.getDescription(), "", statLine)
                .unbreakable()
                .hideFlags()
                .build();
    }

    // ── Energy Bar ────────────────────────────────────────────────────────────

    private void setupEnergyBar(Player p, GamePlayer gp) {
        if (gp.getEnergyBar() != null) gp.getEnergyBar().removePlayer(p);
        BossBar bar = Bukkit.createBossBar(
                ColorUtil.colorizeString("&#D946EF⚡ Energy  100"),
                BarColor.PURPLE, BarStyle.SEGMENTED_10);
        bar.setProgress(1.0);
        bar.addPlayer(p);
        gp.setEnergyBar(bar);
    }

    // ── Passive Augments ──────────────────────────────────────────────────────

    private void applyPassiveAugments(Player p, GamePlayer gp) {
        for (Augment aug : gp.getAugments()) {
            if (aug.getType() != Augment.AugmentType.PASSIVE) continue;
            String effectName = aug.getString("effect", "");
            int amp = aug.getInt("amplifier", 0);
            try {
                PotionEffectType pet = PotionEffectType.getByName(effectName);
                if (pet != null)
                    p.addPotionEffect(new PotionEffect(pet, Integer.MAX_VALUE, amp, true, false, false));
            } catch (Exception ignored) {}
        }
    }

    // ── Energy Regen ─────────────────────────────────────────────────────────

    private void startEnergyRegen() {
        if (energyRegenTask != null) energyRegenTask.cancel();
        energyRegenTask = new BukkitRunnable() {
            @Override public void run() {
                if (state != GameState.IN_ROUND) { cancel(); return; }
                for (GamePlayer gp : players.values()) {
                    if (!gp.isAlive()) continue;
                    Kit kit = gp.getKit();
                    if (kit == null || !kit.isEnergy()) continue;
                    double regen = kit.getEnergyRegenPerSecond() * (1.0 + gp.getEnergyRegenBonus());
                    gp.setEnergy(gp.getEnergy() + regen);
                    if (gp.getEnergyBar() != null) {
                        gp.getEnergyBar().setProgress(Math.min(1.0, gp.getEnergy() / 100.0));
                        gp.getEnergyBar().setTitle(ColorUtil.colorizeString(
                                "&#D946EF⚡ Energy  " + (int) gp.getEnergy()));
                    }
                }
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    // ── Crystal Spawning ──────────────────────────────────────────────────────

    private void startCrystalSpawning() {
        clearCrystals();
        if (crystalTask != null) crystalTask.cancel();
        int intervalSec = plugin.getConfig().getInt("game.crystal-spawn-interval-seconds", 60);
        // First crystal after 10 seconds, then on interval
        new BukkitRunnable() {
            @Override public void run() { if (state == GameState.IN_ROUND) spawnCrystal(); }
        }.runTaskLater(plugin, 200L);
        crystalTask = new BukkitRunnable() {
            @Override public void run() {
                if (state != GameState.IN_ROUND) { cancel(); return; }
                spawnCrystal();
            }
        }.runTaskTimer(plugin, intervalSec * 20L, intervalSec * 20L);
    }

    private void spawnCrystal() {
        List<Location> locs = arena.getCrystalSpawns();
        if (locs.isEmpty()) return;
        Location loc = locs.get(new Random().nextInt(locs.size())).clone();
        loc = loc.getWorld().getBlockAt(loc).getLocation().add(0.5, 0.5, 0.5);

        EnderCrystal crystal = (EnderCrystal) loc.getWorld().spawnEntity(loc, EntityType.END_CRYSTAL);
        crystal.setShowingBottom(false);
        crystal.setInvulnerable(true);
        crystal.setBeamTarget(loc.clone().add(0, 256, 0));
        crystal.setMetadata("smashmons_crystal",
                new org.bukkit.metadata.FixedMetadataValue(plugin, arena.getId()));
        spawnedCrystals.add(crystal);
        sendToAllRaw("crystal-spawned", Map.of());
    }

    private void clearCrystals() {
        for (EnderCrystal c : spawnedCrystals) if (c != null && c.isValid()) c.remove();
        spawnedCrystals.clear();
    }

    // ── Death Handling ────────────────────────────────────────────────────────

    public void handleDeath(Player victim, Player killer) {
        GamePlayer victimGp = players.get(victim.getUniqueId());
        if (victimGp == null || !victimGp.isAlive()) return;

        victimGp.setAlive(false);
        victimGp.setFinishedRound(true);
        victimGp.setHasUltimateCrystal(false);

        plugin.getPlayerDataManager().get(victim).addDeath();
        plugin.getDisguiseManager().undisguise(victim);
        if (victimGp.getEnergyBar() != null) victimGp.getEnergyBar().removePlayer(victim);

        sendToPlayer(victim, "game-killed",
                Map.of("killer", killer != null ? killer.getName() : "the void"));

        if (killer != null && !killer.equals(victim)) {
            GamePlayer killerGp = players.get(killer.getUniqueId());
            if (killerGp != null) {
                int pts = plugin.getConfig().getInt("game.points-per-kill", 2);
                killerGp.addPoints(pts);
                killerGp.addRoundKill();
                killerGp.recomputeAugmentStats(); // recalculate berserker stacks
                plugin.getPlayerDataManager().get(killer).addKill();
                sendToPlayer(killer, "game-kill",
                        Map.of("victim", victim.getName(), "points", String.valueOf(pts)));

                // Adrenaline augment: speed on kill
                killerGp.getAugments().stream()
                        .filter(a -> a.getId().equals("adrenaline")).findFirst()
                        .ifPresent(aug -> killer.addPotionEffect(new PotionEffect(
                                PotionEffectType.SPEED,
                                aug.getInt("speed-duration-ticks", 80),
                                aug.getInt("speed-amplifier", 1), true, true, true)));
            }
        }

        victim.setGameMode(GameMode.SPECTATOR);
        plugin.getScoreboardManager().updateScoreboard(this);
        checkRoundEnd();
    }

    private void checkRoundEnd() {
        // Only end the round when we're actually IN a round
        if (state != GameState.IN_ROUND) return;
        long alive = players.values().stream().filter(GamePlayer::isAlive).count();
        if (alive <= 1) {
            state = GameState.ROUND_ENDING;
            new BukkitRunnable() {
                @Override public void run() { endRound(); }
            }.runTaskLater(plugin, 60L);
        }
    }

    private void endRound() {
        clearCrystals();
        if (crystalTask    != null) crystalTask.cancel();
        if (energyRegenTask != null) energyRegenTask.cancel();

        // Announce round winner
        players.values().stream().filter(GamePlayer::isAlive).findFirst().ifPresent(rw -> {
            Player rwp = Bukkit.getPlayer(rw.getUuid());
            sendToAllRaw("game-round-over",
                    Map.of("player", rwp != null ? rwp.getName() : "Unknown"));
        });

        if (currentRound >= totalRounds) endGame();
        else startAugmentSelect();
    }

    // ── Augment Select ────────────────────────────────────────────────────────

    private void startAugmentSelect() {
        state = GameState.AUGMENT_SELECT;
        pendingAugments.clear();
        int pickSecs = plugin.getConfig().getInt("game.augment-pick-time-seconds", 20);

        for (Map.Entry<UUID, GamePlayer> entry : players.entrySet()) {
            UUID uuid = entry.getKey();
            GamePlayer gp = entry.getValue();
            List<Augment> choices =
                    plugin.getAugmentManager().getRandomAugments(gp.getAugmentIds());
            pendingAugments.put(uuid, new ArrayList<>(choices)); // defensive copy
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) {
                p.setGameMode(GameMode.ADVENTURE);
                p.teleport(getRandomSpawn());
                p.clearActivePotionEffects();
                freezePlayer(p); // freeze during augment pick too
                AugmentGui.open(plugin, p, choices);
                sendToPlayer(p, "game-pick-augment",
                        Map.of("seconds", String.valueOf(pickSecs)));
            }
        }

        // Auto-pick for anyone who doesn't choose in time
        new BukkitRunnable() {
            @Override public void run() {
                for (Map.Entry<UUID, List<Augment>> entry :
                        new HashMap<>(pendingAugments).entrySet()) {
                    GamePlayer gp = players.get(entry.getKey());
                    if (gp == null) continue;
                    Player p = Bukkit.getPlayer(entry.getKey());
                    if (p != null) p.closeInventory();
                    List<Augment> choices = entry.getValue();
                    if (!choices.isEmpty()) {
                        Augment random = choices.get(new Random().nextInt(choices.size()));
                        gp.addAugment(random);
                        broadcastAugmentPick(p != null ? p.getName() : "?", random);
                        if (p != null) sendToPlayer(p, "augment-random", Map.of());
                    }
                }
                pendingAugments.clear();
                broadcastAugmentSummary();
                startRound();
            }
        }.runTaskLater(plugin, pickSecs * 20L);
    }

    public boolean pickAugment(Player player, Augment augment) {
        UUID uuid = player.getUniqueId();
        List<Augment> choices = pendingAugments.get(uuid);
        if (choices == null || !choices.contains(augment)) return false;
        GamePlayer gp = players.get(uuid);
        if (gp == null) return false;
        gp.addAugment(augment);
        pendingAugments.remove(uuid);
        player.closeInventory();
        sendToPlayer(player, "augment-chosen",
                Map.of("augment", augment.getDisplayName()));
        broadcastAugmentPick(player.getName(), augment);
        return true;
    }

    // ── Augment Broadcasts ────────────────────────────────────────────────────

    private void broadcastAugmentPick(String name, Augment aug) {
        Component msg = ColorUtil.colorize(plugin.getMessageManager()
                .format("augment-picked-broadcast",
                        Map.of("player", name, "augment", aug.getDisplayName())));
        players.keySet().forEach(u -> {
            Player p = Bukkit.getPlayer(u);
            if (p != null) p.sendMessage(msg);
        });
    }

    private void broadcastAugmentSummary() {
        Component header = ColorUtil.colorize(plugin.getMessageManager()
                .format("augment-summary-header",
                        Map.of("round", String.valueOf(currentRound))));
        for (UUID uuid : players.keySet()) {
            Player p = Bukkit.getPlayer(uuid);
            if (p == null) continue;
            p.sendMessage(header);
            for (Map.Entry<UUID, GamePlayer> entry : players.entrySet()) {
                Player tp = Bukkit.getPlayer(entry.getKey());
                String tName = tp != null ? tp.getName() : "?";
                List<Augment> augs = entry.getValue().getAugments();
                String latest = augs.isEmpty() ? "None" : augs.get(augs.size() - 1).getDisplayName();
                p.sendMessage(ColorUtil.colorize(plugin.getMessageManager()
                        .format("augment-summary-line",
                                Map.of("player", tName, "augment", latest))));
            }
        }
    }

    // ── Game End ──────────────────────────────────────────────────────────────

    private void endGame() {
        state = GameState.GAME_ENDING;
        arena.setState(ArenaState.ENDING);

        GamePlayer winner = players.values().stream()
                .max(Comparator.comparingInt(GamePlayer::getPoints)).orElse(null);
        Player winnerPlayer = winner != null ? Bukkit.getPlayer(winner.getUuid()) : null;
        String winnerName = winnerPlayer != null ? winnerPlayer.getName() : "Nobody";
        int winnerPoints  = winner != null ? winner.getPoints() : 0;

        if (winner != null) plugin.getPlayerDataManager().get(winner.getUuid()).addWin();

        int mult = plugin.getConfig().getInt("game.smashcoins-multiplier", 10);
        for (Map.Entry<UUID, GamePlayer> entry : players.entrySet()) {
            long coins = (long) entry.getValue().getPoints() * mult;
            plugin.getPlayerDataManager().get(entry.getKey()).addSmashCoins(coins);
            Player p = Bukkit.getPlayer(entry.getKey());
            if (p != null)
                sendToPlayer(p, "game-smashcoins-earned",
                        Map.of("coins", String.valueOf(coins)));
        }

        sendToAllRaw("game-over", Map.of());
        sendToAllRaw("game-winner",
                Map.of("player", winnerName, "points", String.valueOf(winnerPoints)));
        broadcastTitle(
                "&#FFD700&l" + winnerName + " Wins!",
                "&#AAAAAA" + winnerPoints + " points",
                10, 80, 20);

        new BukkitRunnable() {
            @Override public void run() {
                Location serverSpawn = getServerSpawn();
                for (UUID uuid : new HashSet<>(players.keySet())) {
                    Player p = Bukkit.getPlayer(uuid);
                    if (p != null) {
                        GamePlayer gp = players.get(uuid);
                        plugin.getDisguiseManager().undisguise(p);
                        if (gp != null && gp.getEnergyBar() != null)
                            gp.getEnergyBar().removePlayer(p);
                        p.clearActivePotionEffects();
                        unfreezePlayer(p);
                        p.setAllowFlight(false);
                        p.setGameMode(GameMode.SURVIVAL);
                        p.setMaxHealth(20.0);
                        p.setHealth(20.0);
                        p.setFoodLevel(20);
                        p.getInventory().clear();
                        p.teleport(serverSpawn);
                        plugin.getScoreboardManager().removeScoreboard(p);
                        plugin.getActionBarListener().removeHealthObjective(p);
                        plugin.getPlayerDataManager().saveAsync(uuid);
                    }
                }
                players.clear();
                pendingKitSelect.clear();
                pendingAugments.clear();
                arena.setState(ArenaState.AVAILABLE);
                plugin.getGameManager().removeGame(arena.getId());
            }
        }.runTaskLater(plugin, 100L);
    }

    // ── Shutdown (emergency / server stop) ───────────────────────────────────

    public void shutdown() {
        clearCrystals();
        if (countdownTask    != null) countdownTask.cancel();
        if (crystalTask      != null) crystalTask.cancel();
        if (energyRegenTask  != null) energyRegenTask.cancel();
        for (UUID uuid : new HashSet<>(players.keySet())) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) removePlayer(p);
        }
        arena.setState(ArenaState.AVAILABLE);
        plugin.getGameManager().removeGame(arena.getId());
    }

    // ── Utility ───────────────────────────────────────────────────────────────

    private Location getRandomSpawn() {
        List<Location> spawns = arena.getSpawnPoints();
        if (spawns.isEmpty()) {
            World w = Bukkit.getWorld(arena.getRegion().getWorldName());
            return arena.getRegion().getCenter(w != null ? w : Bukkit.getWorlds().get(0));
        }
        return spawns.get(new Random().nextInt(spawns.size()));
    }

    private Location getServerSpawn() {
        var sec = plugin.getConfig().getConfigurationSection("server-spawn");
        if (sec == null) return Bukkit.getWorlds().get(0).getSpawnLocation();
        World w = Bukkit.getWorld(sec.getString("world", "world"));
        if (w == null) w = Bukkit.getWorlds().get(0);
        return new Location(w,
                sec.getDouble("x"), sec.getDouble("y"), sec.getDouble("z"),
                (float) sec.getDouble("yaw"), (float) sec.getDouble("pitch"));
    }

    private void sendToAll(String key, Map<String, String> placeholders) {
        players.keySet().forEach(u -> {
            Player p = Bukkit.getPlayer(u);
            if (p != null) plugin.getMessageManager().send(p, key, placeholders);
        });
    }

    private void sendToAllRaw(String key, Map<String, String> placeholders) {
        sendToAll(key, placeholders);
    }

    private void sendToPlayer(Player p, String key, Map<String, String> placeholders) {
        plugin.getMessageManager().send(p, key, placeholders);
    }

    private void broadcastTitle(String title, String subtitle,
                                int fadeIn, int stay, int fadeOut) {
        Title t = Title.title(
                ColorUtil.colorize(title),
                ColorUtil.colorize(subtitle),
                Title.Times.times(
                        Duration.ofMillis(fadeIn * 50L),
                        Duration.ofMillis(stay * 50L),
                        Duration.ofMillis(fadeOut * 50L)));
        players.keySet().forEach(u -> {
            Player p = Bukkit.getPlayer(u);
            if (p != null) p.showTitle(t);
        });
    }

    // ── Accessors ─────────────────────────────────────────────────────────────

    public Arena              getArena()              { return arena; }
    public Map<UUID, GamePlayer> getPlayers()         { return players; }
    public GamePlayer         getGamePlayer(UUID u)   { return players.get(u); }
    public GameState          getState()              { return state; }
    public int                getCurrentRound()       { return currentRound; }
    public int                getTotalRounds()        { return totalRounds; }
    public boolean            containsPlayer(UUID u)  { return players.containsKey(u); }
    public List<EnderCrystal> getSpawnedCrystals()    { return spawnedCrystals; }
    public Map<UUID, List<Augment>> getPendingAugments() { return pendingAugments; }
    public Set<UUID>          getPendingKitSelect()   { return pendingKitSelect; }

    public List<GamePlayer> getSortedPlayers() {
        return players.values().stream()
                .sorted(Comparator.comparingInt(GamePlayer::getPoints).reversed())
                .collect(Collectors.toList());
    }
}