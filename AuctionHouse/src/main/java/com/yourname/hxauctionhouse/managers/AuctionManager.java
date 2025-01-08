package com.yourname.hxauctionhouse.managers;

import com.yourname.hxauctionhouse.HxAuctionHouse;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class AuctionManager {
    private final HxAuctionHouse plugin;
    private final Map<UUID, AuctionItem> activeAuctions;
    private final Economy economy;
    private final File auctionsFile;
    private final YamlConfiguration auctionsConfig;

    public AuctionManager(HxAuctionHouse plugin) {
        this.plugin = plugin;
        this.activeAuctions = new ConcurrentHashMap<>();
        this.auctionsFile = new File(plugin.getDataFolder(), "auctions.yml");
        this.auctionsConfig = YamlConfiguration.loadConfiguration(auctionsFile);
        this.economy = setupEconomy();
        loadAuctions();
    }

    private Economy setupEconomy() {
        if (plugin.getServer().getPluginManager().getPlugin("Vault") == null) {
            return null;
        }
        RegisteredServiceProvider<Economy> rsp = plugin.getServer().getServicesManager().getRegistration(Economy.class);
        return rsp == null ? null : rsp.getProvider();
    }

    public void saveData() {
        try {
            // Clear existing auctions
            for (String key : auctionsConfig.getKeys(false)) {
                auctionsConfig.set(key, null);
            }

            // Save active auctions
            for (AuctionItem auction : activeAuctions.values()) {
                Map<String, Object> map = auction.serialize();
                for (Map.Entry<String, Object> entry : map.entrySet()) {
                    auctionsConfig.set(entry.getKey(), entry.getValue());
                }
            }

            auctionsConfig.save(auctionsFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save auctions: " + e.getMessage());
        }
    }

    private void loadAuctions() {
        for (String key : auctionsConfig.getKeys(false)) {
            try {
                Map<String, Object> map = new HashMap<>();
                map.put("id", key);
                map.put("seller", auctionsConfig.getString(key + ".seller"));
                map.put("price", auctionsConfig.getDouble(key + ".price"));
                map.put("item", auctionsConfig.getItemStack(key + ".item"));
                map.put("listedTime", auctionsConfig.getLong(key + ".listedTime"));
                map.put("expirationHours", auctionsConfig.getInt(key + ".expirationHours", 48));

                AuctionItem auction = AuctionItem.deserialize(map);
                activeAuctions.put(auction.getId(), auction);
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to load auction " + key + ": " + e.getMessage());
            }
        }
    }

    public Collection<AuctionItem> getActiveAuctions() {
        return activeAuctions.values();
    }

    public AuctionItem getAuction(UUID id) {
        return activeAuctions.get(id);
    }

    public boolean buyItem(Player buyer, UUID auctionId) {
        AuctionItem auction = activeAuctions.get(auctionId);
        if (auction == null) {
            buyer.sendMessage("§c§lError §8» §7This auction no longer exists!");
            return false;
        }

        if (auction.getSeller().equals(buyer.getUniqueId())) {
            buyer.sendMessage("§c§lError §8» §7You cannot buy your own items!");
            return false;
        }

        if (auction.isExpired()) {
            buyer.sendMessage("§c§lError §8» §7This auction has expired!");
            return false;
        }

        if (economy == null) {
            buyer.sendMessage("§c§lError §8» §7Economy system is not available!");
            return false;
        }

        double price = auction.getPrice();
        if (!economy.has(buyer, price)) {
            buyer.sendMessage("§c§lError §8» §7You cannot afford this item! Need: §e" + price);
            return false;
        }

        // Process the transaction
        economy.withdrawPlayer(buyer, price);
        economy.depositPlayer(Bukkit.getOfflinePlayer(auction.getSeller()), price);
        
        // Give item to buyer
        Map<Integer, ItemStack> overflow = buyer.getInventory().addItem(auction.getItemStack());
        if (!overflow.isEmpty()) {
            buyer.getWorld().dropItem(buyer.getLocation(), auction.getItemStack());
            buyer.sendMessage("§e§lAuction House §8» §7Your inventory was full, the item was dropped at your feet!");
        }

        // Remove auction
        activeAuctions.remove(auctionId);
        
        // Notify players
        buyer.sendMessage("§e§lAuction House §8» §7Successfully purchased item for §e" + price + " coins§7!");
        Player seller = Bukkit.getPlayer(auction.getSeller());
        if (seller != null) {
            seller.sendMessage("§e§lAuction House §8» §7Your item was sold for §e" + price + " coins§7!");
        }

        return true;
    }

    public boolean listItem(Player seller, ItemStack item, double price) {
        // Check if price is valid
        if (price <= 0) {
            seller.sendMessage("§c§lError §8» §7Price must be greater than 0!");
            return false;
        }

        // Check if item is valid
        if (item == null || item.getType().isAir()) {
            seller.sendMessage("§c§lError §8» §7You must hold an item to sell!");
            return false;
        }

        // Check if player has reached their listing limit
        int maxListings = plugin.getConfig().getInt("settings.max-listings", 10);
        long currentListings = activeAuctions.values().stream()
            .filter(auction -> auction.getSeller().equals(seller.getUniqueId()))
            .count();

        if (currentListings >= maxListings) {
            seller.sendMessage("§c§lError §8» §7You can only have " + maxListings + " active listings!");
            return false;
        }

        // Create the auction
        UUID auctionId = UUID.randomUUID();
        AuctionItem auction = new AuctionItem(auctionId, seller.getUniqueId(), price, item.clone(), Instant.now());
        activeAuctions.put(auctionId, auction);

        // Remove the item from the player's inventory
        item.setAmount(0);

        // Send confirmation message
        seller.sendMessage("§e§lAuction House §8» §7Listed item for §e" + price + " coins§7!");
        return true;
    }

    public boolean removeAuction(UUID auctionId) {
        AuctionItem auction = activeAuctions.remove(auctionId);
        if (auction != null) {
            saveData();
            return true;
        }
        return false;
    }

    public static class AuctionItem {
        private final UUID id;
        private final UUID seller;
        private final double price;
        private final ItemStack item;
        private final Instant listedTime;
        private final long expirationHours;

        public AuctionItem(UUID id, UUID seller, double price, ItemStack item, Instant listedTime) {
            this.id = id;
            this.seller = seller;
            this.price = price;
            this.item = item;
            this.listedTime = listedTime;
            this.expirationHours = 48; // 48 hours default expiration
        }

        public UUID getId() {
            return id;
        }

        public UUID getSeller() {
            return seller;
        }

        public double getPrice() {
            return price;
        }

        public ItemStack getItemStack() {
            return item.clone(); // Always return a clone to prevent modifications
        }

        public Instant getListedTime() {
            return listedTime;
        }

        public boolean isExpired() {
            return Instant.now().isAfter(listedTime.plusSeconds(expirationHours * 3600));
        }

        public String getTimeLeft() {
            long secondsLeft = expirationHours * 3600 - Duration.between(listedTime, Instant.now()).getSeconds();
            if (secondsLeft <= 0) return "Expired";

            long hours = secondsLeft / 3600;
            long minutes = (secondsLeft % 3600) / 60;

            if (hours > 0) {
                return hours + "h " + minutes + "m";
            } else {
                return minutes + "m";
            }
        }

        // Save to config
        public Map<String, Object> serialize() {
            Map<String, Object> map = new HashMap<>();
            map.put("id", id.toString());
            map.put("seller", seller.toString());
            map.put("price", price);
            map.put("item", item);
            map.put("listedTime", listedTime.getEpochSecond());
            map.put("expirationHours", expirationHours);
            return map;
        }

        // Load from config
        public static AuctionItem deserialize(Map<String, Object> map) {
            UUID id = UUID.fromString((String) map.get("id"));
            UUID seller = UUID.fromString((String) map.get("seller"));
            double price = ((Number) map.get("price")).doubleValue();
            ItemStack item = (ItemStack) map.get("item");
            Instant listedTime = Instant.ofEpochSecond(((Number) map.get("listedTime")).longValue());
            return new AuctionItem(id, seller, price, item, listedTime);
        }
    }
}
