package com.pallux.smashmons.listeners;

import com.pallux.smashmons.SmashMons;
import com.pallux.smashmons.arena.Arena;
import com.pallux.smashmons.augments.Augment;
import com.pallux.smashmons.data.PlayerData;
import com.pallux.smashmons.game.Game;
import com.pallux.smashmons.game.GamePlayer;
import com.pallux.smashmons.game.GameState;
import com.pallux.smashmons.gui.*;
import com.pallux.smashmons.kits.Kit;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.Map;

public class GuiListener implements Listener {

    private final SmashMons plugin;

    public GuiListener(SmashMons plugin) {
        this.plugin = plugin;
    }

    // ── Click handling ────────────────────────────────────────────────────────

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getClickedInventory() == null) return;
        String title = event.getView().title().toString();

        // Cancel ALL clicks inside any SmashMons GUI
        if (!isSmashMonsGui(title)) return;
        event.setCancelled(true);

        // Read-only GUIs — nothing to process
        if (AugmentInfoGui.isAugmentInfoGui(title)) return;

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType().isAir()) return;

        // ── Arena Select ───────────────────────────────────────────────────
        if (ArenaGui.isArenaGui(title)) {
            String arenaId = pdc(plugin, clicked, "arena_id");
            if (arenaId == null) return;
            Arena arena = plugin.getArenaManager().getArena(arenaId);
            if (arena == null) return;
            player.closeInventory();
            plugin.getGameManager().joinArena(player, arena);
            return;
        }

        // ── Kit Select (in-game, mandatory) ───────────────────────────────
        if (KitSelectGui.isKitSelectGui(title)) {
            String kitId = pdc(plugin, clicked, "kit_id");
            if (kitId == null) return;
            Kit kit = plugin.getKitManager().getKit(kitId);
            if (kit == null) return;
            Game game = plugin.getGameManager().getGame(player.getUniqueId());
            if (game == null) return;
            // Kit select is open both in WAITING and KIT_SELECT states
            game.assignKit(player.getUniqueId(), kit);
            return;
        }

        // ── Kit Overview / Shop ────────────────────────────────────────────
        if (KitsOverviewGui.isKitsOverviewGui(title)) {
            String kitId = pdc(plugin, clicked, "buy_kit_id");
            if (kitId == null) return;
            Kit kit = plugin.getKitManager().getKit(kitId);
            if (kit == null) return;
            PlayerData pd = plugin.getPlayerDataManager().get(player);
            if (pd.hasKit(kitId)) return;
            if (!pd.deductSmashCoins(kit.getCost())) {
                plugin.getMessageManager().send(player, "not-enough-coins", Map.of(
                        "cost", String.valueOf(kit.getCost()),
                        "balance", String.valueOf(pd.getSmashCoins())));
                return;
            }
            pd.unlockKit(kitId);
            plugin.getPlayerDataManager().saveAsync(player.getUniqueId());
            plugin.getMessageManager().send(player, "kit-purchased",
                    Map.of("kit", kit.getDisplayName(), "cost", String.valueOf(kit.getCost())));
            player.closeInventory();
            KitsOverviewGui.open(plugin, player);
            return;
        }

        // ── Augment Select (mandatory) ─────────────────────────────────────
        if (AugmentGui.isAugmentGui(title)) {
            String augId = pdc(plugin, clicked, "augment_id");
            if (augId == null) return;
            Augment augment = plugin.getAugmentManager().getAugment(augId);
            if (augment == null) return;
            Game game = plugin.getGameManager().getGame(player.getUniqueId());
            if (game == null) return;
            game.pickAugment(player, augment);
        }
    }

    // ── Prevent closing mandatory GUIs ────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        String title = event.getView().title().toString();

        // Kit select: mandatory until a kit is chosen or game starts
        if (KitSelectGui.isKitSelectGui(title)) {
            Game game = plugin.getGameManager().getGame(player.getUniqueId());
            if (game == null) return;
            GamePlayer gp = game.getGamePlayer(player.getUniqueId());
            // Only re-open if the player still hasn't chosen a kit
            if (gp != null && gp.getKit() == null
                    && (game.getState() == GameState.WAITING || game.getState() == GameState.STARTING)) {
                Bukkit.getScheduler().runTaskLater(plugin,
                        () -> KitSelectGui.open(plugin, player, game), 1L);
            }
            return;
        }

        // Augment select: mandatory until an augment is chosen or time expires
        if (AugmentGui.isAugmentGui(title)) {
            Game game = plugin.getGameManager().getGame(player.getUniqueId());
            if (game == null) return;
            // Re-open if this player still has pending augments
            if (game.getState() == GameState.AUGMENT_SELECT
                    && game.getPendingAugments().containsKey(player.getUniqueId())) {
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    java.util.List<Augment> choices =
                            game.getPendingAugments().get(player.getUniqueId());
                    if (choices != null && !choices.isEmpty()) {
                        AugmentGui.open(plugin, player, choices);
                    }
                }, 1L);
            }
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private boolean isSmashMonsGui(String title) {
        return ArenaGui.isArenaGui(title)
                || KitSelectGui.isKitSelectGui(title)
                || KitsOverviewGui.isKitsOverviewGui(title)
                || AugmentGui.isAugmentGui(title)
                || AugmentInfoGui.isAugmentInfoGui(title);
    }

    private String pdc(SmashMons plugin, ItemStack item, String key) {
        if (item.getItemMeta() == null) return null;
        PersistentDataContainer c = item.getItemMeta().getPersistentDataContainer();
        return c.get(new NamespacedKey(plugin, key), PersistentDataType.STRING);
    }

}
