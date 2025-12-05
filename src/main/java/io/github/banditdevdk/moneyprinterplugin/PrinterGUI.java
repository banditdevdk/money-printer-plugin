package io.github.banditdevdk.moneyprinterplugin;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.profile.PlayerProfile;
import org.bukkit.profile.PlayerTextures;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;

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
     * Update GUI content with modern design
     */
    private void updateGUIContent(Inventory inv, Location loc) {
        PrinterData.PrinterInfo printer = plugin.getPrinterData().getPrinter(loc);
        if (printer == null) {
            return;
        }

        ConfigManager config = plugin.getConfigManager();
        ConfigManager.TierConfig tierConfig = config.getTier(printer.getTier());

        // Fill with background filler
        ItemStack filler = createItem(config.getGUIFiller(), config.getGUIFillerName(), null);
        for (int i = 0; i < inv.getSize(); i++) {
            inv.setItem(i, filler);
        }

        // Create placeholders for all buttons
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("fuel", printer.getFormattedFuelTime());
        placeholders.put("tier", tierConfig != null ? tierConfig.getName() : String.valueOf(printer.getTier()));
        placeholders.put("money", String.format("%.2f", printer.getEarnings()));
        placeholders.put("earnings", tierConfig != null ? String.format("%.2f", tierConfig.getEarnings()) : "0.00");
        placeholders.put("max-fuel", String.valueOf(config.getMaxFuelMinutes()));

        // Status display (top center) - using printer head with tier texture
        ItemStack statusItem = createPrinterHead(tierConfig);
        ItemMeta statusMeta = statusItem.getItemMeta();
        if (statusMeta != null) {
            statusMeta.setDisplayName(config.getButtonName("status", placeholders));
            statusMeta.setLore(config.getButtonLore("status", placeholders));
            statusItem.setItemMeta(statusMeta);
        }
        inv.setItem(config.getButtonSlot("status"), statusItem);

        // Add fuel button (only if fuel is enabled)
        if (config.isFuelEnabled()) {
            Material fuelMaterial = config.getButtonMaterial("add-fuel");
            String fuelButtonName = config.getButtonName("add-fuel", placeholders);
            List<String> fuelLore = config.getButtonLore("add-fuel", placeholders);
            inv.setItem(config.getButtonSlot("add-fuel"), createItem(fuelMaterial, fuelButtonName, fuelLore));
        }

        // Collect money button
        Material collectMaterial = config.getButtonMaterial("collect-money");
        String collectButtonName = config.getButtonName("collect-money", placeholders);
        List<String> collectLore = config.getButtonLore("collect-money", placeholders);
        inv.setItem(config.getButtonSlot("collect-money"), createItem(collectMaterial, collectButtonName, collectLore));

        // Upgrade button - changes based on current tier
        ConfigManager.TierConfig nextTier = config.getNextTier(printer.getTier());

        if (nextTier != null) {
            // There is a next tier available
            placeholders.put("next-tier", nextTier.getName());
            placeholders.put("cost", String.format("%.2f", nextTier.getUpgradeCost()));
            placeholders.put("next-earnings", String.format("%.2f", nextTier.getEarnings()));

            Material upgradeMaterial = config.getButtonMaterial("upgrade");
            String upgradeName = config.getButtonName("upgrade", placeholders);
            List<String> upgradeLore = config.getButtonLore("upgrade", placeholders);

            inv.setItem(config.getButtonSlot("upgrade"), createItem(upgradeMaterial, upgradeName, upgradeLore));
        } else {
            // At max tier
            String maxTierMaterial = config.getConfig().getString("gui.buttons.upgrade.max-tier-material", "BARRIER");
            Material material;
            try {
                material = Material.valueOf(maxTierMaterial.toUpperCase());
            } catch (IllegalArgumentException e) {
                material = Material.BARRIER;
            }

            String maxTierName = config.getConfig().getString("gui.buttons.upgrade.max-tier-name", "§c&lMax Tier Reached");
            maxTierName = org.bukkit.ChatColor.translateAlternateColorCodes('&', maxTierName);

            List<String> maxTierLore = config.getConfig().getStringList("gui.buttons.upgrade.max-tier-lore");
            List<String> coloredMaxLore = new ArrayList<>();
            for (String line : maxTierLore) {
                coloredMaxLore.add(org.bukkit.ChatColor.translateAlternateColorCodes('&', line));
            }

            inv.setItem(config.getButtonSlot("upgrade"), createItem(material, maxTierName, coloredMaxLore));
        }
    }

    /**
     * Create a printer head item with custom texture
     */
    private ItemStack createPrinterHead(ConfigManager.TierConfig tierConfig) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);

        if (tierConfig != null && tierConfig.hasCustomTexture()) {
            SkullMeta meta = (SkullMeta) item.getItemMeta();
            if (meta != null) {
                try {
                    PlayerProfile profile = Bukkit.createPlayerProfile(UUID.randomUUID());
                    PlayerTextures textures = profile.getTextures();

                    String textureValue = tierConfig.getSkullTexture();
                    if (textureValue.startsWith("http")) {
                        textures.setSkin(new URL(textureValue));
                        profile.setTextures(textures);
                        meta.setOwnerProfile(profile);
                    }

                    item.setItemMeta(meta);
                } catch (MalformedURLException e) {
                    plugin.getLogger().warning("Invalid skull texture URL for tier " + tierConfig.getTier());
                }
            }
        }

        return item;
    }

    /**
     * Create an item with a display name and lore
     */
    private ItemStack createItem(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            if (lore != null && !lore.isEmpty()) {
                meta.setLore(lore);
            }
            item.setItemMeta(meta);
        }
        return item;
    }
}