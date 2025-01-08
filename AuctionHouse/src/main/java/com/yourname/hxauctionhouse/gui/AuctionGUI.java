package com.yourname.hxauctionhouse.gui;

import com.yourname.hxauctionhouse.HxAuctionHouse;
import com.yourname.hxauctionhouse.managers.AuctionManager;
import com.yourname.hxauctionhouse.managers.AuctionManager.AuctionItem;
import com.yourname.hxauctionhouse.utils.ItemBuilder;
import com.yourname.hxauctionhouse.utils.PriceUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.NamespacedKey;

import java.util.*;
import java.util.stream.Collectors;
import java.util.function.Consumer;

public class AuctionGUI implements Listener {
    private final HxAuctionHouse plugin;
    private final Map<UUID, Integer> playerPages = new HashMap<>();
    private final Map<UUID, UUID> confirmMenuAuctionIds = new HashMap<>();
    private final Map<UUID, String> currentFilter = new HashMap<>();
    private final Map<UUID, String> currentSort = new HashMap<>();
    private final Map<UUID, String> currentSearch = new HashMap<>();
    private final Map<UUID, Consumer<String>> chatCallbacks = new HashMap<>();
    
    private static final int GUI_SIZE = 54;
    private static final int ITEMS_PER_PAGE = 45;

    public AuctionGUI(HxAuctionHouse plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void openBrowseMenu(Player player) {
        openBrowseMenu(player, 1);
    }

    public void openBrowseMenu(Player player, int page) {
        List<AuctionItem> items = getFilteredAndSortedItems(player);
        // Remove expired items
        items.removeIf(AuctionItem::isExpired);
        
        int maxPages = (int) Math.ceil(items.size() / (double) ITEMS_PER_PAGE);
        if (maxPages == 0) maxPages = 1;
        
        playerPages.put(player.getUniqueId(), page);
        Inventory inv = Bukkit.createInventory(null, GUI_SIZE, "§8§lAuction House §8(§7" + page + "§8/§7" + maxPages + "§8)");

        // Display items
        int startIndex = (page - 1) * ITEMS_PER_PAGE;
        int endIndex = Math.min(startIndex + ITEMS_PER_PAGE, items.size());

        for (int i = startIndex; i < endIndex; i++) {
            AuctionItem auctionItem = items.get(i);
            ItemStack displayItem = auctionItem.getItemStack().clone();
            updateItemLore(displayItem, auctionItem, player);
            inv.setItem(i - startIndex, displayItem);
        }

        // Fill empty slots
        for (int i = endIndex - startIndex; i < 45; i++) {
            inv.setItem(i, new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name("§8").build());
        }

        // Navigation items
        addNavigationItems(inv, page, items.size());
        player.openInventory(inv);
    }

    private void updateItemLore(ItemStack displayItem, AuctionItem auction, Player viewer) {
        ItemMeta meta = displayItem.getItemMeta();
        if (meta != null) {
            List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
            
            // Add a unique identifier to the item name for duplicates
            String itemName = meta.getDisplayName();
            if (itemName.isEmpty()) {
                itemName = "§f" + displayItem.getType().toString().toLowerCase().replace("_", " ");
            }
            meta.setDisplayName(itemName + " §8(#" + auction.getId().toString().substring(0, 4) + ")");
            
            // Add auction info to lore
            lore.add("");
            lore.add("§8§m                              ");
            lore.add("§8» §7Price: §e" + PriceUtil.formatPrice(auction.getPrice()));
            
            // Get seller name safely
            String sellerName = "Unknown";
            try {
                Player seller = Bukkit.getPlayer(auction.getSeller());
                if (seller != null) {
                    sellerName = seller.getName();
                } else {
                    sellerName = Bukkit.getOfflinePlayer(auction.getSeller()).getName();
                    if (sellerName == null) {
                        sellerName = "Unknown";
                    }
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to get seller name for auction " + auction.getId());
            }
            
            lore.add("§8» §7Seller: §f" + sellerName);
            lore.add("§8» §7Time Left: §f" + auction.getTimeLeft());
            lore.add("§8§m                              ");
            lore.add("");
            
            // Check if this is the seller viewing their own item
            if (viewer != null && auction.getSeller().equals(viewer.getUniqueId())) {
                lore.add("§cRight-click to cancel listing");
            } else {
                lore.add("§eClick to purchase!");
            }
            
            lore.add("§8ID: " + auction.getId().toString());
            
            meta.setLore(lore);
            displayItem.setItemMeta(meta);
        }
    }

    public void openFilterMenu(Player player) {
        Inventory inv = Bukkit.createInventory(null, 27, "§8§lFilter Items");
        String currentFilter = this.currentFilter.getOrDefault(player.getUniqueId(), "All");

        String[] categories = {"All", "Weapons", "Tools", "Armor", "Blocks", "Food", "Potions", "Other"};
        for (int i = 0; i < categories.length; i++) {
            boolean selected = categories[i].equals(currentFilter);
            inv.setItem(10 + i, new ItemBuilder(getCategoryMaterial(categories[i]))
                .name((selected ? "§6§l" : "§e§l") + categories[i])
                .lore("§7Click to filter by " + categories[i].toLowerCase(),
                      selected ? "§aCurrently selected" : "§7Click to select")
                .glow(selected)
                .build());
        }

        player.openInventory(inv);
    }

    public void openSortMenu(Player player) {
        Inventory inv = Bukkit.createInventory(null, 27, "§8§lSort Items");
        String currentSort = this.currentSort.getOrDefault(player.getUniqueId(), "newest");

        Map<String, Material> sortOptions = new LinkedHashMap<>();
        sortOptions.put("highest_price", Material.DIAMOND);
        sortOptions.put("lowest_price", Material.EMERALD);
        sortOptions.put("newest", Material.CLOCK);
        sortOptions.put("oldest", Material.COMPASS);

        int slot = 10;
        for (Map.Entry<String, Material> entry : sortOptions.entrySet()) {
            boolean selected = entry.getKey().equals(currentSort);
            String displayName = switch (entry.getKey()) {
                case "highest_price" -> "Highest Price";
                case "lowest_price" -> "Lowest Price";
                case "newest" -> "Recently Listed";
                case "oldest" -> "Last Listed";
                default -> entry.getKey();
            };

            inv.setItem(slot++, new ItemBuilder(entry.getValue())
                .name((selected ? "§6§l" : "§e§l") + displayName)
                .lore("§7Click to sort by " + displayName.toLowerCase(),
                      selected ? "§aCurrently selected" : "§7Click to select")
                .glow(selected)
                .build());
        }

        player.openInventory(inv);
    }

    public void openConfirmPurchaseMenu(Player player, UUID auctionId) {
        AuctionItem auction = plugin.getAuctionManager().getAuction(auctionId);
        if (auction == null) {
            player.sendMessage("§c§lError §8» §7This item is no longer available!");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            openBrowseMenu(player);
            return;
        }

        Inventory inv = Bukkit.createInventory(null, 27, "§8§lConfirm Purchase");

        // Display item
        ItemStack displayItem = auction.getItemStack().clone();
        updateItemLore(displayItem, auction, player);
        inv.setItem(13, displayItem);

        // Confirm button
        inv.setItem(11, new ItemBuilder(Material.LIME_STAINED_GLASS_PANE)
            .name("§a§lConfirm Purchase")
            .lore("§7Click to buy this item for",
                  "§e" + PriceUtil.formatPrice(auction.getPrice()) + " coins")
            .build());

        // Cancel button
        inv.setItem(15, new ItemBuilder(Material.RED_STAINED_GLASS_PANE)
            .name("§c§lCancel")
            .lore("§7Click to cancel")
            .build());

        // Fill empty slots
        for (int i = 0; i < 27; i++) {
            if (inv.getItem(i) == null) {
                inv.setItem(i, new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name("§8").build());
            }
        }

        player.openInventory(inv);
        confirmMenuAuctionIds.put(player.getUniqueId(), auctionId);
    }

    public void openYourListingsMenu(Player player) {
        Inventory inv = Bukkit.createInventory(null, GUI_SIZE, "§8§lYour Listings");
        List<AuctionItem> yourItems = plugin.getAuctionManager().getActiveAuctions().stream()
            .filter(item -> item.getSeller().equals(player.getUniqueId()))
            .filter(item -> !item.isExpired())
            .collect(Collectors.toList());

        for (int i = 0; i < Math.min(yourItems.size(), 45); i++) {
            AuctionItem item = yourItems.get(i);
            ItemStack display = item.getItemStack().clone();
            updateItemLore(display, item, player);
            inv.setItem(i, display);
        }

        // Fill empty slots
        for (int i = Math.min(yourItems.size(), 45); i < 45; i++) {
            inv.setItem(i, new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name("§8").build());
        }

        // Back button
        inv.setItem(49, new ItemBuilder(Material.BARRIER)
            .name("§c§lBack")
            .lore("§7Click to go back")
            .build());

        player.openInventory(inv);
    }

    public void openTransactionsMenu(Player player) {
        Inventory inv = Bukkit.createInventory(null, GUI_SIZE, "§8§lTransaction History");
        
        // Back button
        inv.setItem(49, new ItemBuilder(Material.BARRIER)
            .name("§c§lBack")
            .lore("§7Click to go back")
            .build());

        player.openInventory(inv);
    }

    private List<AuctionItem> getFilteredAndSortedItems(Player player) {
        List<AuctionItem> items = new ArrayList<>(plugin.getAuctionManager().getActiveAuctions());
        
        // Apply search filter
        String search = currentSearch.get(player.getUniqueId());
        if (search != null && !search.isEmpty()) {
            items = items.stream()
                .filter(item -> {
                    ItemStack stack = item.getItemStack();
                    if (!stack.hasItemMeta()) return false;
                    ItemMeta meta = stack.getItemMeta();
                    if (!meta.hasDisplayName()) return false;
                    return meta.getDisplayName().toLowerCase().contains(search.toLowerCase());
                })
                .collect(Collectors.toList());
        }

        // Apply category filter
        String filter = currentFilter.get(player.getUniqueId());
        if (filter != null && !filter.equals("All")) {
            items = items.stream()
                .filter(item -> getItemCategory(item.getItemStack()).equals(filter))
                .collect(Collectors.toList());
        }

        // Apply sort
        String sort = currentSort.get(player.getUniqueId());
        if (sort != null) {
            switch (sort.toLowerCase()) {
                case "highest_price" -> items.sort((a, b) -> Double.compare(b.getPrice(), a.getPrice()));
                case "lowest_price" -> items.sort((a, b) -> Double.compare(a.getPrice(), b.getPrice()));
                case "newest" -> items.sort((a, b) -> b.getListedTime().compareTo(a.getListedTime()));
                case "oldest" -> items.sort((a, b) -> a.getListedTime().compareTo(b.getListedTime()));
            }
        }

        return items;
    }

    private String getItemCategory(ItemStack item) {
        Material type = item.getType();
        if (type.name().endsWith("_SWORD") || type.name().endsWith("_AXE") || type == Material.BOW || type == Material.CROSSBOW) {
            return "Weapons";
        } else if (type.name().endsWith("_PICKAXE") || type.name().endsWith("_SHOVEL") || type.name().endsWith("_HOE")) {
            return "Tools";
        } else if (type.name().endsWith("_HELMET") || type.name().endsWith("_CHESTPLATE") || 
                   type.name().endsWith("_LEGGINGS") || type.name().endsWith("_BOOTS")) {
            return "Armor";
        } else if (type.isBlock()) {
            return "Blocks";
        } else if (type.isEdible()) {
            return "Food";
        } else if (type == Material.POTION || type == Material.SPLASH_POTION || type == Material.LINGERING_POTION) {
            return "Potions";
        } else {
            return "Other";
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getView().getTitle().startsWith("§8§l")) {
            event.setCancelled(true);

            if (event.getCurrentItem() == null) return;
            ItemStack clicked = event.getCurrentItem();
            ItemMeta meta = clicked.getItemMeta();
            if (meta == null) return;

            // Handle back button
            if (clicked.getType() == Material.BARRIER && meta.getDisplayName().equals("§c§lBack")) {
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
                openBrowseMenu(player);
                return;
            }

            // Handle navigation items
            if (handleNavigationClick(event)) return;

            // Get auction ID from lore
            if (!meta.hasLore()) return;
            String idLine = meta.getLore().stream()
                .filter(line -> line.startsWith("§8ID: "))
                .findFirst()
                .orElse(null);
            if (idLine == null) return;

            UUID auctionId;
            try {
                auctionId = UUID.fromString(idLine.substring(6));
            } catch (IllegalArgumentException e) {
                return;
            }

            // Handle your listings menu
            if (event.getView().getTitle().equals("§8§lYour Listings")) {
                if (event.getClick() == ClickType.RIGHT) {
                    AuctionItem auction = plugin.getAuctionManager().getAuction(auctionId);
                    if (auction != null && auction.getSeller().equals(player.getUniqueId())) {
                        plugin.getAuctionManager().removeAuction(auctionId);
                        player.getInventory().addItem(auction.getItemStack());
                        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
                        player.sendMessage("§e§lAuction House §8» §7Successfully cancelled your listing!");
                        openYourListingsMenu(player);
                    }
                }
                return;
            }

            // Handle browse menu
            if (event.getView().getTitle().contains("§8§lAuction House")) {
                openConfirmPurchaseMenu(player, auctionId);
            }
        }
    }

    private boolean handleNavigationClick(InventoryClickEvent event) {
        Player player = (Player) event.getWhoClicked();
        ItemStack clicked = event.getCurrentItem();
        ItemMeta meta = clicked.getItemMeta();
        if (meta == null) return false;

        switch (event.getSlot()) {
            case 45: // Previous Page
                if (meta.getDisplayName().equals("§e§lPrevious Page")) {
                    int currentPage = playerPages.getOrDefault(player.getUniqueId(), 1);
                    if (currentPage > 1) {
                        openBrowseMenu(player, currentPage - 1);
                    }
                }
                return true;
            case 46: // Search
                if (meta.getDisplayName().equals("§e§lSearch")) {
                    promptSearch(player);
                }
                return true;
            case 47: // Filter
                if (meta.getDisplayName().equals("§e§lFilter")) {
                    openFilterMenu(player);
                }
                return true;
            case 48: // Sort
                if (meta.getDisplayName().equals("§e§lSort")) {
                    openSortMenu(player);
                }
                return true;
            case 49: // Your Items
                if (meta.getDisplayName().equals("§e§lYour Listings")) {
                    openYourListingsMenu(player);
                }
                return true;
            case 50: // Transactions
                if (meta.getDisplayName().equals("§e§lTransaction History")) {
                    openTransactionsMenu(player);
                }
                return true;
            case 52: // Refresh
                if (meta.getDisplayName().equals("§e§lRefresh")) {
                    openBrowseMenu(player, playerPages.getOrDefault(player.getUniqueId(), 1));
                }
                return true;
            case 53: // Next Page
                if (meta.getDisplayName().equals("§e§lNext Page")) {
                    List<AuctionItem> items = getFilteredAndSortedItems(player);
                    int maxPages = (int) Math.ceil(items.size() / (double) ITEMS_PER_PAGE);
                    int currentPage = playerPages.getOrDefault(player.getUniqueId(), 1);
                    if (currentPage < maxPages) {
                        openBrowseMenu(player, currentPage + 1);
                    }
                }
                return true;
            default:
                return false;
        }
    }

    @EventHandler
    public void onAsyncPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        Consumer<String> callback = chatCallbacks.remove(player.getUniqueId());
        
        if (callback != null) {
            event.setCancelled(true);
            String message = event.getMessage();
            
            if (message.equalsIgnoreCase("cancel")) {
                player.sendMessage("§c§lCancelled §8» §7Operation cancelled.");
                Bukkit.getScheduler().runTask(plugin, () -> openBrowseMenu(player));
                return;
            }
            
            callback.accept(message);
        }
    }

    private void promptSearch(Player player) {
        chatCallbacks.put(player.getUniqueId(), input -> {
            currentSearch.put(player.getUniqueId(), input);
            Bukkit.getScheduler().runTask(plugin, () -> openBrowseMenu(player));
            player.sendMessage("§e§lSearch §8» §7Searching for: §f" + input);
        });
        
        player.closeInventory();
        player.sendMessage("");
        player.sendMessage("§e§lSearch §8» §7Enter your search term in chat");
        player.sendMessage("§8» §7Type §fcancel §7to cancel");
        player.sendMessage("");
    }

    private Material getCategoryMaterial(String category) {
        return switch (category) {
            case "Weapons" -> Material.DIAMOND_SWORD;
            case "Tools" -> Material.DIAMOND_PICKAXE;
            case "Armor" -> Material.DIAMOND_CHESTPLATE;
            case "Blocks" -> Material.GRASS_BLOCK;
            case "Food" -> Material.COOKED_BEEF;
            case "Potions" -> Material.POTION;
            case "Other" -> Material.CHEST;
            default -> Material.BARRIER;
        };
    }

    private void addNavigationItems(Inventory inv, int currentPage, int totalItems) {
        int maxPages = (int) Math.ceil(totalItems / (double) ITEMS_PER_PAGE);

        // Previous page
        inv.setItem(45, new ItemBuilder(Material.ARROW)
            .name("§e§lPrevious Page")
            .lore("§7Click to go to the previous page")
            .glow(currentPage > 1)
            .build());

        // Search
        inv.setItem(46, new ItemBuilder(Material.COMPASS)
            .name("§e§lSearch")
            .lore("§7Click to search for items")
            .build());

        // Filter
        inv.setItem(47, new ItemBuilder(Material.HOPPER)
            .name("§e§lFilter")
            .lore("§7Click to filter items")
            .build());

        // Sort
        inv.setItem(48, new ItemBuilder(Material.COMPARATOR)
            .name("§e§lSort")
            .lore("§7Click to sort items")
            .build());

        // Your listings
        inv.setItem(49, new ItemBuilder(Material.CHEST)
            .name("§e§lYour Listings")
            .lore("§7Click to view your listings")
            .build());

        // Transactions
        inv.setItem(50, new ItemBuilder(Material.BOOK)
            .name("§e§lTransaction History")
            .lore("§7Click to view your transactions")
            .build());

        // Refresh
        inv.setItem(52, new ItemBuilder(Material.SUNFLOWER)
            .name("§e§lRefresh")
            .lore("§7Click to refresh the menu")
            .build());

        // Next page
        inv.setItem(53, new ItemBuilder(Material.ARROW)
            .name("§e§lNext Page")
            .lore("§7Click to go to the next page")
            .glow(currentPage < maxPages)
            .build());
    }
}
