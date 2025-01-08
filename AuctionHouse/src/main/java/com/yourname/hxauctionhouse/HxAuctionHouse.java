package com.yourname.hxauctionhouse;

import com.yourname.hxauctionhouse.commands.AuctionCommand;
import com.yourname.hxauctionhouse.gui.AuctionGUI;
import com.yourname.hxauctionhouse.managers.AuctionManager;
import com.yourname.hxauctionhouse.managers.ConfigManager;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public class HxAuctionHouse extends JavaPlugin {
    private AuctionManager auctionManager;
    private ConfigManager configManager;
    private AuctionGUI auctionGUI;
    private FileConfiguration config;

    @Override
    public void onEnable() {
        // Save default config
        saveDefaultConfig();
        this.config = getConfig();
        
        // Initialize managers
        this.configManager = new ConfigManager(this);
        this.auctionManager = new AuctionManager(this);
        
        // Initialize GUI
        this.auctionGUI = new AuctionGUI(this);
        
        // Register commands
        getCommand("ah").setExecutor(new AuctionCommand(this));
        
        getLogger().info("HxAuctionHouse has been enabled!");
    }

    @Override
    public void onDisable() {
        // Save data
        if (auctionManager != null) {
            auctionManager.saveData();
        }
        
        getLogger().info("HxAuctionHouse has been disabled!");
    }

    public AuctionManager getAuctionManager() {
        return auctionManager;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public AuctionGUI getAuctionGUI() {
        return auctionGUI;
    }

    @Override
    public FileConfiguration getConfig() {
        return this.config == null ? super.getConfig() : this.config;
    }
}
