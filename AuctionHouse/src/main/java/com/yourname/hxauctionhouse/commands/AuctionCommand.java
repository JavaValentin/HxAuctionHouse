package com.yourname.hxauctionhouse.commands;

import com.yourname.hxauctionhouse.HxAuctionHouse;
import com.yourname.hxauctionhouse.utils.PriceUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class AuctionCommand implements CommandExecutor, TabCompleter {
    private final HxAuctionHouse plugin;
    private final List<String> SUBCOMMANDS = Arrays.asList("sell", "listings", "help");

    public AuctionCommand(HxAuctionHouse plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§c§lError §8» §7Only players can use this command!");
            return true;
        }

        if (args.length == 0) {
            plugin.getAuctionGUI().openBrowseMenu(player);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "sell":
                if (!player.hasPermission("auctionhouse.sell")) {
                    player.sendMessage("§c§lError §8» §7You don't have permission to sell items!");
                    return true;
                }

                if (args.length != 2) {
                    player.sendMessage("");
                    player.sendMessage("§e§lAuction House §8» §7Sell Command Usage:");
                    player.sendMessage("§8» §e/ah sell <price> §8- §7List held item for sale");
                    player.sendMessage("§8» §7Example: §f/ah sell 50k");
                    player.sendMessage("");
                    return true;
                }

                ItemStack item = player.getInventory().getItemInMainHand();
                if (item == null || item.getType().isAir()) {
                    player.sendMessage("§c§lError §8» §7You must hold an item to sell!");
                    return true;
                }

                double price = PriceUtil.parsePrice(args[1]);
                if (price <= 0) {
                    player.sendMessage("");
                    player.sendMessage("§c§lError §8» §7Invalid price format!");
                    player.sendMessage("§8» §7Use a number with optional K, M, or B suffix");
                    player.sendMessage("§8» §7Examples: §f50000§7, §f50k§7, §f1.5m§7, §f2b");
                    player.sendMessage("");
                    return true;
                }

                if (plugin.getAuctionManager().listItem(player, item, price)) {
                    player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
                } else {
                    player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                }
                return true;

            case "listings":
                if (!player.hasPermission("auctionhouse.listings")) {
                    player.sendMessage("§c§lError §8» §7You don't have permission to view your listings!");
                    return true;
                }
                plugin.getAuctionGUI().openYourListingsMenu(player);
                player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_CHEST_OPEN, 1.0f, 1.0f);
                return true;

            case "help":
                sendHelpMessage(player);
                return true;

            default:
                player.sendMessage("§c§lError §8» §7Unknown command. Type /ah help for help.");
                player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                return true;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player player)) {
            return new ArrayList<>();
        }

        if (args.length == 1) {
            return SUBCOMMANDS.stream()
                .filter(cmd -> player.hasPermission("auctionhouse." + cmd))
                .filter(cmd -> cmd.toLowerCase().startsWith(args[0].toLowerCase()))
                .collect(Collectors.toList());
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("sell")) {
            List<String> suggestions = new ArrayList<>();
            ItemStack item = ((Player) sender).getInventory().getItemInMainHand();
            if (!item.getType().isAir()) {
                suggestions.add("1k");
                suggestions.add("10k");
                suggestions.add("100k");
                suggestions.add("1m");
            }
            return suggestions;
        }

        return new ArrayList<>();
    }

    private void sendHelpMessage(Player player) {
        player.sendMessage("");
        player.sendMessage("§e§lAuction House §8- §7Help");
        player.sendMessage("");
        if (player.hasPermission("auctionhouse.use"))
            player.sendMessage("§8» §e/ah §7- Open the auction house");
        if (player.hasPermission("auctionhouse.sell"))
            player.sendMessage("§8» §e/ah sell <price> §7- Sell the item in your hand");
        if (player.hasPermission("auctionhouse.listings"))
            player.sendMessage("§8» §e/ah listings §7- View your listed items");
        player.sendMessage("§8» §e/ah help §7- Show this help message");
        player.sendMessage("");
        player.sendMessage("§7Price format: §f50000§7, §f50k§7, §f1.5m§7, §f2b");
        player.sendMessage("");
    }
}
