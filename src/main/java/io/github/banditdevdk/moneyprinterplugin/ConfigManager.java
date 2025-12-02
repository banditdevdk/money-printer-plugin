package io.github.banditdevdk.moneyprinterplugin;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.*;

/**
 * Manages plugin configuration
 */
public class ConfigManager {
    private final MoneyPrinterPlugin plugin;
    private FileConfiguration config;

    // Cached config values
    private int maxPrintersPerPlayer;
    private List<String> disabledWorlds;
    private Material fuelMaterial;
    private int fuelMinutesPerItem;
    private int maxFuelMinutes;
    private int generationInterval;
    private double maxMoneyStorage;
    private boolean notifyFuelEmpty;
    private boolean notifyStorageFull;
    private Map<Integer, TierConfig> tiers;

    public ConfigManager(MoneyPrinterPlugin plugin) {
        this.plugin = plugin;
        loadConfig();
    }

    /**
     * Load/reload configuration
     */
    public void loadConfig() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        config = plugin.getConfig();

        // Load general settings
        maxPrintersPerPlayer = config.getInt("settings.max-printers-per-player", 1);
        disabledWorlds = config.getStringList("settings.disabled-worlds");

        // Load fuel settings
        String fuelMaterialName = config.getString("fuel.material", "PAPER");
        try {
            fuelMaterial = Material.valueOf(fuelMaterialName.toUpperCase());
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Invalid fuel material: " + fuelMaterialName + ". Using PAPER.");
            fuelMaterial = Material.PAPER;
        }
        fuelMinutesPerItem = config.getInt("fuel.minutes-per-item", 5);
        maxFuelMinutes = config.getInt("fuel.max-fuel-minutes", 60);

        // Load money settings
        generationInterval = config.getInt("money.generation-interval", 5);
        maxMoneyStorage = config.getDouble("money.max-storage", 10000.0);

        // Load notification settings
        notifyFuelEmpty = config.getBoolean("notifications.fuel-empty", true);
        notifyStorageFull = config.getBoolean("notifications.storage-full", true);

        // Load tiers
        loadTiers();

        plugin.getLogger().info("Configuration loaded successfully!");
    }

    /**
     * Load tier configurations
     */
    private void loadTiers() {
        tiers = new HashMap<>();
        ConfigurationSection tiersSection = config.getConfigurationSection("tiers");

        if (tiersSection == null) {
            plugin.getLogger().warning("No tiers configured! Using defaults.");
            createDefaultTiers();
            return;
        }

        for (String key : tiersSection.getKeys(false)) {
            try {
                int tierNum = Integer.parseInt(key);
                String path = "tiers." + key;

                String name = config.getString(path + ".name", "Tier " + tierNum);
                String blockName = config.getString(path + ".block", "PLAYER_HEAD");
                Material block;
                try {
                    block = Material.valueOf(blockName.toUpperCase());
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("Invalid block for tier " + tierNum + ": " + blockName);
                    block = Material.PLAYER_HEAD;
                }

                String skullTexture = config.getString(path + ".skull-texture", "");
                double earnings = config.getDouble(path + ".earnings", 10.0);
                double upgradeCost = config.getDouble(path + ".upgrade-cost", 0.0);

                TierConfig tierConfig = new TierConfig(tierNum, name, block, skullTexture, earnings, upgradeCost);
                tiers.put(tierNum, tierConfig);

            } catch (NumberFormatException e) {
                plugin.getLogger().warning("Invalid tier number: " + key);
            }
        }

        if (tiers.isEmpty()) {
            plugin.getLogger().warning("No valid tiers loaded! Using defaults.");
            createDefaultTiers();
        }
    }

    /**
     * Create default tiers if none exist
     */
    private void createDefaultTiers() {
        tiers.put(1, new TierConfig(1, "Basic Printer", Material.PLAYER_HEAD, "", 10.0, 0.0));
        tiers.put(2, new TierConfig(2, "Advanced Printer", Material.PLAYER_HEAD, "", 20.0, 500.0));
        tiers.put(3, new TierConfig(3, "Superior Printer", Material.PLAYER_HEAD, "", 35.0, 1500.0));
        tiers.put(4, new TierConfig(4, "Elite Printer", Material.PLAYER_HEAD, "", 50.0, 2500.0));
    }

    /**
     * Get a message from config with color codes translated
     */
    public String getMessage(String path) {
        String message = config.getString("messages." + path, path);
        message = ChatColor.translateAlternateColorCodes('&', message);

        // Replace {prefix} with the actual prefix
        String prefix = config.getString("messages.prefix", "");
        prefix = ChatColor.translateAlternateColorCodes('&', prefix);
        message = message.replace("{prefix}", prefix);

        return message;
    }

    /**
     * Get a message with placeholders replaced
     */
    public String getMessage(String path, Map<String, String> placeholders) {
        String message = getMessage(path);
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            message = message.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return message;
    }

    // Getters
    public int getMaxPrintersPerPlayer() { return maxPrintersPerPlayer; }
    public List<String> getDisabledWorlds() { return disabledWorlds; }
    public Material getFuelMaterial() { return fuelMaterial; }
    public int getFuelMinutesPerItem() { return fuelMinutesPerItem; }
    public int getMaxFuelMinutes() { return maxFuelMinutes; }
    public int getGenerationInterval() { return generationInterval; }
    public double getMaxMoneyStorage() { return maxMoneyStorage; }
    public boolean shouldNotifyFuelEmpty() { return notifyFuelEmpty; }
    public boolean shouldNotifyStorageFull() { return notifyStorageFull; }
    public Map<Integer, TierConfig> getTiers() { return tiers; }
    public TierConfig getTier(int tier) { return tiers.get(tier); }
    public int getHighestTier() {
        return tiers.keySet().stream().max(Integer::compareTo).orElse(1);
    }

    /**
     * Get GUI configuration value
     */
    public String getGUITitle() {
        return ChatColor.translateAlternateColorCodes('&',
                config.getString("gui.title", "&8&aMoney Printer"));
    }

    public int getGUIRows() {
        return Math.max(1, Math.min(6, config.getInt("gui.rows", 5)));
    }

    public Material getGUIFiller() {
        String material = config.getString("gui.filler.material", "BLACK_STAINED_GLASS_PANE");
        try {
            return Material.valueOf(material.toUpperCase());
        } catch (IllegalArgumentException e) {
            return Material.BLACK_STAINED_GLASS_PANE;
        }
    }

    public String getGUIFillerName() {
        return ChatColor.translateAlternateColorCodes('&',
                config.getString("gui.filler.name", " "));
    }

    public int getButtonSlot(String button) {
        return config.getInt("gui.buttons." + button + ".slot", 0);
    }

    public Material getButtonMaterial(String button) {
        String material = config.getString("gui.buttons." + button + ".material", "PAPER");
        try {
            return Material.valueOf(material.toUpperCase());
        } catch (IllegalArgumentException e) {
            return Material.PAPER;
        }
    }

    public String getButtonName(String button, Map<String, String> placeholders) {
        String name = config.getString("gui.buttons." + button + ".name", button);
        name = ChatColor.translateAlternateColorCodes('&', name);

        if (placeholders != null) {
            for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                name = name.replace("{" + entry.getKey() + "}", entry.getValue());
            }
        }

        return name;
    }

    /**
     * Tier configuration data class
     */
    public static class TierConfig {
        private final int tier;
        private final String name;
        private final Material block;
        private final String skullTexture;
        private final double earnings;
        private final double upgradeCost;

        public TierConfig(int tier, String name, Material block, String skullTexture,
                          double earnings, double upgradeCost) {
            this.tier = tier;
            this.name = name;
            this.block = block;
            this.skullTexture = skullTexture;
            this.earnings = earnings;
            this.upgradeCost = upgradeCost;
        }

        public int getTier() { return tier; }
        public String getName() { return name; }
        public Material getBlock() { return block; }
        public String getSkullTexture() { return skullTexture; }
        public double getEarnings() { return earnings; }
        public double getUpgradeCost() { return upgradeCost; }
        public boolean hasCustomTexture() { return skullTexture != null && !skullTexture.isEmpty(); }
    }
}