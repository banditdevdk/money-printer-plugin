package io.github.banditdevdk.moneyprinterplugin;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Handles GUI creation and updates
 */
public class PrinterGUI {
    private final MoneyPrinterPlugin plugin;
    private final Map<UUID, Location> openGUIs = new HashMap<>();

    public PrinterGUI(MoneyPrinterPlugin plugin) {
        this.plugin = plugin;

        // Start GUI update task (every 2 seconds)
        Bukkit.getScheduler().runTaskTimer(plugin, this::updateAllGUIs, 40L, 40L);
    }

    /**
     * Open GUI for a player
     */
    public void openGUI(Player player, Location loc) {
        ConfigManager config = plugin.getConfigManager();
        int rows = config.getGUIRows();
        String title = config.getGUITitle();

        Inventory inv = Bukkit.createInventory(null, rows * 9, title);
        updateGUIContent(inv, loc);
        player.openInventory(inv);

        // Store location AFTER opening inventory (delay by 1 tick to avoid close event)
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            openGUIs.put(player.getUniqueId(), loc);
        }, 1L);
    }

    /**
     * Update GUI for a player
     */
    public void updateGUI(Player player, Location loc) {
        if (!openGUIs.containsKey(player.getUniqueId())) {
            return;
        }

        Inventory inv = player.getOpenInventory().getTopInventory();
        if (inv == null) {
            return;
        }

        updateGUIContent(inv, loc);
    }

    /**
     * Close GUI for a player
     */
    public void closeGUI(Player player) {
        openGUIs.remove(player.getUniqueId());
    }

    /**
     * Get the printer location a player has open
     */
    public Location getOpenPrinter(Player player) {
        return openGUIs.get(player.getUniqueId());
    }

    /**
     * Update all open GUIs
     */
    private void updateAllGUIs() {
        for (Map.Entry<UUID, Location> entry : new HashMap<>(openGUIs).entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player != null && player.isOnline()) {
                String title = player.getOpenInventory().getTitle();
                if (title != null) {
                    String stripped = title.replaceAll("§[0-9a-fk-or]", "");
                    if (stripped.contains("Money Printer")) {
                        updateGUI(player, entry.getValue());
                    }
                }
            } else {
                // Clean up disconnected players
                openGUIs.remove(entry.getKey());
            }
        }
    }

    /**
     * Update GUI content
     */
    private void updateGUIContent(Inventory inv, Location loc) {
        PrinterData.PrinterInfo printer = plugin.getPrinterData().getPrinter(loc);
        if (printer == null) {
            return;
        }

        ConfigManager config = plugin.getConfigManager();

        // Fill with background filler
        ItemStack filler = createItem(config.getGUIFiller(), config.getGUIFillerName());
        for (int i = 0; i < inv.getSize(); i++) {
            inv.setItem(i, filler);
        }

        // Create placeholders for buttons
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("fuel", printer.getFormattedFuelTime());
        placeholders.put("tier", String.valueOf(printer.getTier()));
        placeholders.put("money", String.format("%.2f", printer.getEarnings()));

        ConfigManager.TierConfig tierConfig = config.getTier(printer.getTier());
        if (tierConfig != null) {
            placeholders.put("tier-name", tierConfig.getName());
        }

        // Add fuel button
        Material fuelMaterial = config.getButtonMaterial("add-fuel");
        placeholders.put("fuel", config.getFuelMaterial().name().toLowerCase().replace("_", " "));
        String fuelButtonName = config.getButtonName("add-fuel", placeholders);
        inv.setItem(config.getButtonSlot("add-fuel"), createItem(fuelMaterial, fuelButtonName));

        // Collect money button
        placeholders.put("money", String.format("%.2f", printer.getEarnings()));
        Material collectMaterial = config.getButtonMaterial("collect-money");
        String collectButtonName = config.getButtonName("collect-money", placeholders);
        inv.setItem(config.getButtonSlot("collect-money"), createItem(collectMaterial, collectButtonName));

        // Status button
        placeholders.put("fuel", printer.getFormattedFuelTime());
        placeholders.put("tier", tierConfig != null ? tierConfig.getName() : String.valueOf(printer.getTier()));
        Material statusMaterial = config.getButtonMaterial("status");
        String statusButtonName = config.getButtonName("status", placeholders);
        inv.setItem(config.getButtonSlot("status"), createItem(statusMaterial, statusButtonName));

        // Upgrade buttons
        for (int tier : config.getTiers().keySet()) {
            ConfigManager.TierConfig upgradeTier = config.getTier(tier);
            if (upgradeTier == null) continue;

            String buttonKey = "upgrade-tier-" + tier;

            // Only show upgrade buttons for higher tiers
            if (tier <= printer.getTier()) {
                continue;
            }

            placeholders.put("cost", String.format("%.2f", upgradeTier.getUpgradeCost()));
            placeholders.put("earnings", String.format("%.2f", upgradeTier.getEarnings()));
            placeholders.put("tier", upgradeTier.getName());

            Material upgradeMaterial = config.getButtonMaterial(buttonKey);
            String upgradeButtonName = config.getButtonName(buttonKey, placeholders);

            inv.setItem(config.getButtonSlot(buttonKey), createItem(upgradeMaterial, upgradeButtonName));
        }
    }

    /**
     * Create an item with a display name
     */
    private ItemStack createItem(Material material, String name) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            item.setItemMeta(meta);
        }
        return item;
    }
}