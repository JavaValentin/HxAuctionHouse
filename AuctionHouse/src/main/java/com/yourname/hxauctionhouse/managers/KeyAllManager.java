package com.yourname.hxauctionhouse.managers;

import com.yourname.hxauctionhouse.HxAuctionHouse;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitRunnable;
import org.jetbrains.annotations.NotNull;

public class KeyAllManager extends PlaceholderExpansion {
    private final HxAuctionHouse plugin;
    private long nextKeyAll;
    private final long INTERVAL = 3600000; // 1 hour in milliseconds

    public KeyAllManager(HxAuctionHouse plugin) {
        this.plugin = plugin;
        this.nextKeyAll = System.currentTimeMillis() + INTERVAL;
        startKeyAllTask();
    }

    private void startKeyAllTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                giveKeyAll();
                nextKeyAll = System.currentTimeMillis() + INTERVAL;
            }
        }.runTaskTimer(plugin, 20L * 60L, 20L * 60L * 60L); // Run every hour
    }

    private void giveKeyAll() {
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "crates key giveall normal 1");
        Bukkit.broadcastMessage("§8[§6KeyAll§8] §eEveryone received §61 Normal Key§e!");
    }

    public String getTimeUntilNextKeyAll() {
        long timeLeft = nextKeyAll - System.currentTimeMillis();
        if (timeLeft < 0) return "Soon";

        long minutes = (timeLeft / 1000) / 60;
        long hours = minutes / 60;
        minutes = minutes % 60;

        return String.format("%02d:%02d", hours, minutes);
    }

    @Override
    public @NotNull String getIdentifier() {
        return "keyall";
    }

    @Override
    public @NotNull String getAuthor() {
        return plugin.getDescription().getAuthors().get(0);
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onPlaceholderRequest(org.bukkit.entity.Player player, String identifier) {
        if (identifier.equals("time")) {
            return getTimeUntilNextKeyAll();
        }
        return null;
    }
}
