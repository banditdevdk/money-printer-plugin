package io.github.banditdevdk.moneyprinterplugin;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Task that runs every 10 seconds to manage printers
 */
public class PrinterTask extends BukkitRunnable {
    private final MoneyPrinterPlugin plugin;

    public PrinterTask(MoneyPrinterPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        ConfigManager config = plugin.getConfigManager();
        Map<Location, PrinterData.PrinterInfo> printers = plugin.getPrinterData().getAllPrinters();
        List<Location> toRemove = new ArrayList<>();

        // Calculate how many ticks equal one generation interval
        int generationIntervalMinutes = config.getGenerationInterval();
        int ticksPerInterval = (generationIntervalMinutes * 60) / 10; // Each tick = 10 seconds

        for (Map.Entry<Location, PrinterData.PrinterInfo> entry : printers.entrySet()) {
            Location loc = entry.getKey();
            PrinterData.PrinterInfo printer = entry.getValue();

            // Check if block still exists
            if (loc.getBlock().getType() != Material.PLAYER_HEAD) {
                toRemove.add(loc);
                continue;
            }

            // Process fuel and earnings
            if (printer.getFuelTime() > 0) {
                // Consume fuel (10 seconds)
                printer.setFuelTime(Math.max(0, printer.getFuelTime() - 10));

                // Check if printer is at max storage
                double maxStorage = config.getMaxMoneyStorage();
                if (printer.getEarnings() >= maxStorage) {
                    // Notify if enabled and not already notified
                    if (config.shouldNotifyStorageFull() && !printer.hasNotifiedFull()) {
                        printer.setNotifiedFull(true);
                        notifyPlayer(printer.getOwner(), "storage-full-notification",
                                createPlaceholders("money", String.format("%.2f", printer.getEarnings())));
                    }
                    // Don't generate more money
                    continue;
                }

                // Increment tick counter
                printer.setFuelTicks(printer.getFuelTicks() + 1);

                // Pay every X ticks based on generation interval
                if (printer.getFuelTicks() >= ticksPerInterval) {
                    printer.setFuelTicks(0);

                    // Add earnings based on tier from config
                    double earnings = printer.getEarningsRate(config);
                    double newEarnings = Math.min(printer.getEarnings() + earnings, maxStorage);
                    printer.setEarnings(newEarnings);

                    // Check if just reached max and notify
                    if (newEarnings >= maxStorage && config.shouldNotifyStorageFull() && !printer.hasNotifiedFull()) {
                        printer.setNotifiedFull(true);
                        notifyPlayer(printer.getOwner(), "storage-full-notification",
                                createPlaceholders("money", String.format("%.2f", newEarnings)));
                    }
                }
            } else {
                // Printer is out of fuel
                if (config.shouldNotifyFuelEmpty() && !printer.hasNotifiedEmpty()) {
                    printer.setNotifiedEmpty(true);

                    // Notify owner
                    Map<String, String> placeholders = createPlaceholders("fuel",
                            config.getFuelMaterial().name().toLowerCase().replace("_", " "));
                    notifyPlayer(printer.getOwner(), "fuel-empty-notification", placeholders);
                }
            }
        }

        // Remove invalid printers
        for (Location loc : toRemove) {
            plugin.getPrinterData().removePrinter(loc);
        }

        // Save data periodically
        if (!toRemove.isEmpty() || !printers.isEmpty()) {
            plugin.getPrinterData().saveData();
        }
    }

    /**
     * Notify a player with a message from config
     */
    private void notifyPlayer(java.util.UUID ownerUUID, String messageKey, Map<String, String> placeholders) {
        OfflinePlayer owner = Bukkit.getOfflinePlayer(ownerUUID);
        if (owner.isOnline() && owner.getPlayer() != null) {
            String message = plugin.getConfigManager().getMessage(messageKey, placeholders);
            owner.getPlayer().sendMessage(message);
        }
    }

    /**
     * Create placeholder map
     */
    private Map<String, String> createPlaceholders(String key, String value) {
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put(key, value);
        return placeholders;
    }
}