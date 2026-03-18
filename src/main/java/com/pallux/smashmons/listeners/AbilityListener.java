package com.pallux.smashmons.listeners;

import com.pallux.smashmons.SmashMons;
import com.pallux.smashmons.game.Game;
import com.pallux.smashmons.game.GamePlayer;
import com.pallux.smashmons.game.GameState;
import com.pallux.smashmons.kits.Kit;
import com.pallux.smashmons.kits.KitAbility;
import com.pallux.smashmons.kits.abilities.*;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

import java.util.Map;

public class AbilityListener implements Listener {

    private final SmashMons plugin;

    public AbilityListener(SmashMons plugin) {
        this.plugin = plugin;
    }

    // ── Right-click → fire ability ────────────────────────────────────────────
    @EventHandler
    public void onRightClick(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        Player player = event.getPlayer();
        if (!plugin.getGameManager().isInGame(player.getUniqueId())) return;

        Game game = plugin.getGameManager().getGame(player.getUniqueId());
        if (game == null || game.getState() != GameState.IN_ROUND) return;

        GamePlayer gp = game.getGamePlayer(player.getUniqueId());
        if (gp == null || !gp.isAlive()) return;

        Kit kit = gp.getKit();
        if (kit == null) return;

        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return;

        event.setCancelled(true);
        int slot = player.getInventory().getHeldItemSlot();

        if (slot == 0 && kit.getPrimaryAbility() != null) {
            handleAbility(player, game, gp, kit, kit.getPrimaryAbility(), "primary");
        } else if (slot == 1 && kit.getSecondaryAbility() != null) {
            handleAbility(player, game, gp, kit, kit.getSecondaryAbility(), "secondary");
        } else if (slot == 8 && kit.getUltimateAbility() != null) {
            handleUltimate(player, game, gp, kit, kit.getUltimateAbility());
        }
    }

    /**
     * Melee left-click: set a fixed base damage.
     * GameListener.onDamage will then apply augment multipliers on top.
     * Run at LOW priority so GameListener (HIGH) can still apply multipliers
     * without conflict.
     */
    @EventHandler(priority = EventPriority.LOW)
    public void onMelee(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker)) return;
        if (!(event.getEntity() instanceof Player)) return;
        if (event.isCancelled()) return;

        Game game = plugin.getGameManager().getGame(attacker.getUniqueId());
        if (game == null || game.getState() != GameState.IN_ROUND) return;

        // Set weak fixed base damage; GameListener multiplies it afterwards
        event.setDamage(2.0);
    }

    // ── Dispatch ──────────────────────────────────────────────────────────────

    private void handleAbility(Player player, Game game, GamePlayer gp,
                               Kit kit, KitAbility ability, String key) {
        String cooldownKey = kit.getId() + "_" + key;

        if (kit.isEnergy()) {
            int cost = ability.getEnergyCost();
            if (gp.getEnergy() < cost) {
                plugin.getMessageManager().send(player, "ability-no-energy", Map.of(
                        "cost", String.valueOf(cost),
                        "current", String.valueOf((int) gp.getEnergy())));
                return;
            }
        } else {
            if (gp.isOnCooldown(cooldownKey)) {
                long remaining = (gp.getRemainingCooldown(cooldownKey) / 1000) + 1;
                plugin.getMessageManager().send(player, "ability-cooldown", Map.of(
                        "ability", ability.getName(),
                        "seconds", String.valueOf(remaining)));
                return;
            }
        }

        boolean used = switch (kit.getId().toLowerCase()) {
            case "pig"      -> PigAbilities.activate(plugin, player, game, gp, ability, key);
            case "skeleton" -> SkeletonAbilities.activate(plugin, player, game, gp, ability, key);
            case "blaze"    -> BlazeAbilities.activate(plugin, player, game, gp, ability, key);
            case "creeper"  -> CreeperAbilities.activate(plugin, player, game, gp, ability, key);
            default -> false;
        };

        if (used) {
            if (kit.isEnergy()) gp.consumeEnergy(ability.getEnergyCost());
            else gp.setCooldown(cooldownKey, ability.getCooldownSeconds());
        }
    }

    private void handleUltimate(Player player, Game game, GamePlayer gp,
                                Kit kit, KitAbility ultimate) {
        if (!gp.hasUltimateCrystal()) {
            plugin.getMessageManager().send(player, "ultimate-no-crystal");
            return;
        }

        boolean used = switch (kit.getId().toLowerCase()) {
            case "pig"      -> PigAbilities.activateUltimate(plugin, player, game, gp, ultimate);
            case "skeleton" -> SkeletonAbilities.activateUltimate(plugin, player, game, gp, ultimate);
            case "blaze"    -> BlazeAbilities.activateUltimate(plugin, player, game, gp, ultimate);
            case "creeper"  -> CreeperAbilities.activateUltimate(plugin, player, game, gp, ultimate);
            default -> false;
        };

        if (used) {
            gp.setHasUltimateCrystal(false);
            plugin.getMessageManager().send(player, "ultimate-used");
        }
    }
}
