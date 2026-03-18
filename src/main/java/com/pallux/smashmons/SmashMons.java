package com.pallux.smashmons;

import com.pallux.smashmons.commands.KitsCommand;
import com.pallux.smashmons.commands.KdaCommand;
import com.pallux.smashmons.commands.SmashCoinsCommand;
import com.pallux.smashmons.commands.SmashMonsCommand;
import com.pallux.smashmons.commands.AugmentsCommand;
import com.pallux.smashmons.commands.SmHelpCommand;
import com.pallux.smashmons.commands.SmCommand;
import com.pallux.smashmons.listeners.*;
import com.pallux.smashmons.managers.*;
import com.pallux.smashmons.placeholders.SmashMonsExpansion;
import org.bukkit.plugin.java.JavaPlugin;

public final class SmashMons extends JavaPlugin {

    private static SmashMons instance;

    private MessageManager messageManager;
    private ArenaManager arenaManager;
    private KitManager kitManager;
    private AugmentManager augmentManager;
    private PlayerDataManager playerDataManager;
    private GameManager gameManager;
    private DisguiseManager disguiseManager;
    private ScoreboardManager scoreboardManager;
    private ActionBarListener actionBarListener;

    @Override
    public void onEnable() {
        instance = this;

        // Save default config files
        saveDefaultConfig();
        saveResource("lang/messages.yml", false);
        saveResource("settings/arenas.yml", false);
        saveResource("settings/kits.yml", false);
        saveResource("settings/augments.yml", false);

        // Init managers in order
        this.messageManager   = new MessageManager(this);
        this.playerDataManager = new PlayerDataManager(this);
        this.arenaManager     = new ArenaManager(this);
        this.kitManager       = new KitManager(this);
        this.augmentManager   = new AugmentManager(this);
        this.disguiseManager  = new DisguiseManager(this);
        this.scoreboardManager = new ScoreboardManager(this);
        this.gameManager      = new GameManager(this);

        // Register commands
        getCommand("smashmons").setExecutor(new SmashMonsCommand(this));
        getCommand("smashmons").setTabCompleter(new SmashMonsCommand(this));
        getCommand("kits").setExecutor(new KitsCommand(this));
        getCommand("kits").setTabCompleter(new KitsCommand(this));
        getCommand("smashcoins").setExecutor(new SmashCoinsCommand(this));
        getCommand("kda").setExecutor(new KdaCommand(this));
        AugmentsCommand augmentsCmd = new AugmentsCommand(this);
        getCommand("augments").setExecutor(augmentsCmd);
        getCommand("augments").setTabCompleter(augmentsCmd);
        getCommand("smhelp").setExecutor(new SmHelpCommand(this));
        SmCommand smCmd = new SmCommand(this);
        getCommand("sm").setExecutor(smCmd);
        getCommand("sm").setTabCompleter(smCmd);

        // Register listeners
        getServer().getPluginManager().registerEvents(new PlayerJoinQuitListener(this), this);
        getServer().getPluginManager().registerEvents(new GameListener(this), this);
        getServer().getPluginManager().registerEvents(new DoubleJumpListener(this), this);
        getServer().getPluginManager().registerEvents(new AbilityListener(this), this);
        getServer().getPluginManager().registerEvents(new CrystalListener(this), this);
        getServer().getPluginManager().registerEvents(new AugmentListener(this), this);
        getServer().getPluginManager().registerEvents(new GuiListener(this), this);
        getServer().getPluginManager().registerEvents(new FreezeListener(this), this);
        this.actionBarListener = new ActionBarListener(this);
        getServer().getPluginManager().registerEvents(actionBarListener, this);

        // PlaceholderAPI hook
        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new SmashMonsExpansion(this).register();
            getLogger().info("Hooked into PlaceholderAPI.");
        }

        getLogger().info("SmashMons v" + getDescription().getVersion() + " enabled!");
    }

    @Override
    public void onDisable() {
        if (actionBarListener != null) actionBarListener.cancel();
        if (gameManager != null) gameManager.shutdownAll();
        if (playerDataManager != null) playerDataManager.saveAll();
        getLogger().info("SmashMons disabled. All data saved.");
    }

    public static SmashMons getInstance() { return instance; }
    public MessageManager getMessageManager() { return messageManager; }
    public ArenaManager getArenaManager() { return arenaManager; }
    public KitManager getKitManager() { return kitManager; }
    public AugmentManager getAugmentManager() { return augmentManager; }
    public PlayerDataManager getPlayerDataManager() { return playerDataManager; }
    public GameManager getGameManager() { return gameManager; }
    public DisguiseManager getDisguiseManager() { return disguiseManager; }
    public ScoreboardManager getScoreboardManager() { return scoreboardManager; }
    public ActionBarListener getActionBarListener() { return actionBarListener; }
}