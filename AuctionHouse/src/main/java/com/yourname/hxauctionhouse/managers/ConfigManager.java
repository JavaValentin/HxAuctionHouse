package com.yourname.hxauctionhouse.managers;

import com.yourname.hxauctionhouse.HxAuctionHouse;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;

public class ConfigManager {
    private final HxAuctionHouse plugin;
    private FileConfiguration config;
    private final File configFile;

    public ConfigManager(HxAuctionHouse plugin) {
        this.plugin = plugin;
        this.configFile = new File(plugin.getDataFolder(), "config.yml");
        loadConfig();
    }

    private void loadConfig() {
        if (!configFile.exists()) {
            plugin.saveResource("config.yml", false);
        }
        config = YamlConfiguration.loadConfiguration(configFile);
        setDefaults();
    }

    private void setDefaults() {
        config.addDefault("auction.maxListingTime", 172800); // 48 hours in seconds
        config.addDefault("auction.minPrice", 0.0);
        config.addDefault("auction.maxPrice", 1000000.0);
        config.addDefault("auction.listingFee", 10.0);
        config.addDefault("auction.maxListingsPerPlayer", 10);
        config.options().copyDefaults(true);
        saveConfig();
    }

    public void saveConfig() {
        try {
            config.save(configFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save config to " + configFile);
            e.printStackTrace();
        }
    }

    public void reloadConfig() {
        config = YamlConfiguration.loadConfiguration(configFile);
    }

    public double getMinPrice() {
        return config.getDouble("auction.minPrice");
    }

    public double getMaxPrice() {
        return config.getDouble("auction.maxPrice");
    }

    public double getListingFee() {
        return config.getDouble("auction.listingFee");
    }

    public int getMaxListingsPerPlayer() {
        return config.getInt("auction.maxListingsPerPlayer");
    }

    public long getMaxListingTime() {
        return config.getLong("auction.maxListingTime");
    }
}
