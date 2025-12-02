package io.github.banditdevdk.moneyprinterplugin;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.HashMap;
import java.util.Map;

/**
 * Handles all printer-related events
 */
public class PrinterEvents implements Listener {
    private final MoneyPrinterPlugin plugin;

    public PrinterEvents(MoneyPrinterPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * When a player head is placed, check if it's a printer
     */
    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        Block block = event.getBlockPlaced();

        if (block.getType() != Material.PLAYER_HEAD) {
            return;
        }

        ItemStack item = event.getItemInHand();
        if (item == null || !item.hasItemMeta()) {
            return;
        }

        ItemMeta meta = item.getItemMeta();
        String displayName = meta.getDisplayName();

        // Check if it's a money printer
        if (displayName == null || !displayName.contains("Money Printer")) {
            return;
        }

        Player player = event.getPlayer();
        ConfigManager config = plugin.getConfigManager();

        // Check if player has permission
        if (!player.hasPermission("printer.use")) {
            event.setCancelled(true);
            player.sendMessage("§cYou don't have permission to place printers.");
            return;
        }

        // Check world restrictions
        if (config.getDisabledWorlds().contains(player.getWorld().getName())) {
            event.setCancelled(true);
            player.sendMessage("§cPrinters are disabled in this world.");
            return;
        }

        // Check printer limit
        int maxPrinters = config.getMaxPrintersPerPlayer();
        int currentPrinters = plugin.getPrinterData().countPrintersByOwner(player.getUniqueId());

        if (currentPrinters >= maxPrinters && !player.hasPermission("printer.admin")) {
            event.setCancelled(true);
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("max", String.valueOf(maxPrinters));
            player.sendMessage(config.getMessage("max-printers", placeholders));
            return;
        }

        Location loc = block.getLocation();

        // Parse tier from item name
        int tier = 1;
        for (int t : config.getTiers().keySet()) {
            ConfigManager.TierConfig tierConfig = config.getTier(t);
            if (displayName.contains(tierConfig.getName())) {
                tier = t;
                break;
            }
        }

        // Check tier permission
        if (!player.hasPermission("printer.tier." + tier) && !player.hasPermission("printer.admin")) {
            event.setCancelled(true);
            player.sendMessage("§cYou don't have permission to place this tier of printer.");
            return;
        }

        // Register the printer
        plugin.getPrinterData().registerPrinter(loc, player.getUniqueId(), tier);

        // Send messages
        ConfigManager.TierConfig tierConfig = config.getTier(tier);
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("tier", tierConfig.getName());

        player.sendMessage(config.getMessage("placed", placeholders));
        player.sendMessage(config.getMessage("placed-instructions", placeholders));
    }

    /**
     * Prevent breaking printers directly
     */
    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();

        if (block.getType() != Material.PLAYER_HEAD) {
            return;
        }

        Location loc = block.getLocation();

        if (plugin.getPrinterData().isPrinter(loc)) {
            event.setCancelled(true);
            Player player = event.getPlayer();
            ConfigManager config = plugin.getConfigManager();

            player.sendMessage(config.getMessage("cannot-break"));
            player.sendMessage(config.getMessage("use-command"));
        }
    }

    /**
     * Right-click on player head opens GUI
     */
    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        Block block = event.getClickedBlock();
        if (block == null || block.getType() != Material.PLAYER_HEAD) {
            return;
        }

        Location loc = block.getLocation();
        PrinterData.PrinterInfo printer = plugin.getPrinterData().getPrinter(loc);

        if (printer != null) {
            event.setCancelled(true);
            Player player = event.getPlayer();

            // Check if player can access this printer
            if (!printer.canAccess(player.getUniqueId()) && !player.hasPermission("printer.admin")) {
                player.sendMessage(plugin.getConfigManager().getMessage("not-owner"));
                return;
            }

            // Open GUI
            plugin.getPrinterGUI().openGUI(player, loc);
        }
    }

    /**
     * Handle GUI clicks
     */
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        String title = event.getView().getTitle();

        // Check if it's our GUI (strip color codes for comparison)
        String strippedViewTitle = title.replaceAll("§[0-9a-fk-or]", "");

        if (!strippedViewTitle.contains("Money Printer")) {
            return;
        }

        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }

        Player player = (Player) event.getWhoClicked();
        int slot = event.getRawSlot();

        // Get the printer location from the player's open GUI
        Location loc = plugin.getPrinterGUI().getOpenPrinter(player);
        if (loc == null) {
            player.closeInventory();
            return;
        }

        PrinterData.PrinterInfo printer = plugin.getPrinterData().getPrinter(loc);
        if (printer == null) {
            player.closeInventory();
            return;
        }

        ConfigManager config = plugin.getConfigManager();

        // Handle different button clicks based on configured slots
        if (slot == config.getButtonSlot("add-fuel")) {
            handleAddFuel(player, loc, printer);
        } else if (slot == config.getButtonSlot("collect-money")) {
            handleCollectMoney(player, loc, printer);
        } else {
            // Check tier upgrade buttons
            for (int tier : config.getTiers().keySet()) {
                String buttonKey = "upgrade-tier-" + tier;
                if (slot == config.getButtonSlot(buttonKey)) {
                    handleUpgrade(player, loc, printer, tier);
                    break;
                }
            }
        }
    }

    /**
     * Clean up when GUI is closed
     */
    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        String title = event.getView().getTitle();
        String strippedTitle = title.replaceAll("§[0-9a-fk-or]", "");

        if (strippedTitle.contains("Money Printer")) {
            Player player = (Player) event.getPlayer();
            plugin.getPrinterGUI().closeGUI(player);
        }
    }

    /**
     * Handle adding fuel
     */
    private void handleAddFuel(Player player, Location loc, PrinterData.PrinterInfo printer) {
        ConfigManager config = plugin.getConfigManager();
        Material fuelMaterial = config.getFuelMaterial();
        int fuelMinutes = config.getFuelMinutesPerItem();
        int maxFuelMinutes = config.getMaxFuelMinutes();

        // Check if already at max
        if (printer.getFuelTime() >= maxFuelMinutes * 60) {
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("max", String.valueOf(maxFuelMinutes));
            player.sendMessage(config.getMessage("fuel-max", placeholders));
            return;
        }

        // Check if player has fuel
        ItemStack fuelItem = new ItemStack(fuelMaterial, 1);
        if (!player.getInventory().containsAtLeast(fuelItem, 1)) {
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("fuel", fuelMaterial.name().toLowerCase().replace("_", " "));
            player.sendMessage(config.getMessage("no-fuel-item", placeholders));
            return;
        }

        // Remove fuel and add time
        player.getInventory().removeItem(fuelItem);
        int secondsToAdd = fuelMinutes * 60;
        int newFuelTime = Math.min(printer.getFuelTime() + secondsToAdd, maxFuelMinutes * 60);
        printer.setFuelTime(newFuelTime);

        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("minutes", String.valueOf(fuelMinutes));
        player.sendMessage(config.getMessage("fuel-added", placeholders));

        plugin.getPrinterGUI().updateGUI(player, loc);
        plugin.getPrinterData().saveData();
    }

    /**
     * Handle collecting money
     */
    private void handleCollectMoney(Player player, Location loc, PrinterData.PrinterInfo printer) {
        ConfigManager config = plugin.getConfigManager();
        double earnings = printer.getEarnings();

        if (earnings <= 0) {
            player.sendMessage(config.getMessage("no-money"));
            return;
        }

        plugin.getEconomy().depositPlayer(player, earnings);
        printer.setEarnings(0);
        printer.setNotifiedFull(false); // Reset full notification

        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("amount", String.format("%.2f", earnings));
        player.sendMessage(config.getMessage("money-collected", placeholders));

        plugin.getPrinterGUI().updateGUI(player, loc);
        plugin.getPrinterData().saveData();
    }

    /**
     * Handle tier upgrades
     */
    private void handleUpgrade(Player player, Location loc, PrinterData.PrinterInfo printer, int targetTier) {
        ConfigManager config = plugin.getConfigManager();
        ConfigManager.TierConfig tierConfig = config.getTier(targetTier);

        if (tierConfig == null) {
            return;
        }

        // Check if already at this tier or higher
        if (printer.getTier() >= targetTier) {
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("tier", String.valueOf(targetTier));
            player.sendMessage(config.getMessage("already-tier", placeholders));
            return;
        }

        // Check tier permission
        if (!player.hasPermission("printer.tier." + targetTier) && !player.hasPermission("printer.admin")) {
            player.sendMessage("§cYou don't have permission to upgrade to this tier.");
            return;
        }

        double cost = tierConfig.getUpgradeCost();

        // Check if can afford
        if (plugin.getEconomy().getBalance(player) < cost) {
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("cost", String.format("%.2f", cost));
            player.sendMessage(config.getMessage("cannot-afford", placeholders));
            return;
        }

        // Perform upgrade
        plugin.getEconomy().withdrawPlayer(player, cost);
        printer.setTier(targetTier);

        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("tier", tierConfig.getName());
        placeholders.put("earnings", String.format("%.2f", tierConfig.getEarnings()));
        player.sendMessage(config.getMessage("upgraded", placeholders));

        plugin.getPrinterGUI().updateGUI(player, loc);
        plugin.getPrinterData().saveData();
    }
}