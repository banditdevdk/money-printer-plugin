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
    private boolean fuelEnabled;
    private Material fuelMaterial;
    private int fuelMinutesPerItem;
    private int maxFuelMinutes;
    private int generationInterval;
    private double maxMoneyStorage;
    private boolean notifyFuelEmpty;
    private boolean notifyStorageFull;
    private boolean notifyFuelEmptyOnLogin;
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
        fuelEnabled = config.getBoolean("fuel.enabled", true);
        String fuelMaterialName = config.getString("fuel.material", "COAL");
        try {
            fuelMaterial = Material.valueOf(fuelMaterialName.toUpperCase());
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Invalid fuel material: " + fuelMaterialName + ". Using COAL.");
            fuelMaterial = Material.COAL;
        }
        fuelMinutesPerItem = config.getInt("fuel.minutes-per-item", 5);
        maxFuelMinutes = config.getInt("fuel.max-fuel-minutes", 60);

        // Load money settings
        generationInterval = config.getInt("money.generation-interval", 5);
        maxMoneyStorage = config.getDouble("money.max-storage", 10000.0);

        // Load notification settings
        notifyFuelEmpty = config.getBoolean("notifications.fuel-empty", true);
        notifyStorageFull = config.getBoolean("notifications.storage-full", true);
        notifyFuelEmptyOnLogin = config.getBoolean("notifications.fuel-empty-on-login", false);

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
                    // Force PLAYER_HEAD - don't allow other block types
                    if (block != Material.PLAYER_HEAD) {
                        plugin.getLogger().warning("Block type for tier " + tierNum + " must be PLAYER_HEAD! Using PLAYER_HEAD instead of " + blockName);
                        block = Material.PLAYER_HEAD;
                    }
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("Invalid block for tier " + tierNum + ": " + blockName + ". Using PLAYER_HEAD.");
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
        tiers.put(1, new TierConfig(1, "Iron Printer", Material.PLAYER_HEAD,
                "http://textures.minecraft.net/texture/f8eecae423359d3f5efd1063a9a7bcfaa43839d75d3b223c808df7961dd173d0",
                10.0, 0.0));
        tiers.put(2, new TierConfig(2, "Gold Printer", Material.PLAYER_HEAD,
                "http://textures.minecraft.net/texture/6c07d48fd8764bc8d01a10cc6426578862090d9e856f3a8dd7f974a7521efc43",
                20.0, 500.0));
        tiers.put(3, new TierConfig(3, "Diamond Printer", Material.PLAYER_HEAD,
                "http://textures.minecraft.net/texture/666070ce03a545ee4d263bcf27f36338d249d7cb7a2376f92c1673ae134e04b6",
                35.0, 1500.0));
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
    public FileConfiguration getConfig() { return config; }
    public int getMaxPrintersPerPlayer() { return maxPrintersPerPlayer; }
    public List<String> getDisabledWorlds() { return disabledWorlds; }
    public boolean isFuelEnabled() { return fuelEnabled; }
    public Material getFuelMaterial() { return fuelMaterial; }
    public int getFuelMinutesPerItem() { return fuelMinutesPerItem; }
    public int getMaxFuelMinutes() { return maxFuelMinutes; }
    public int getGenerationInterval() { return generationInterval; }
    public double getMaxMoneyStorage() { return maxMoneyStorage; }
    public boolean shouldNotifyFuelEmpty() { return notifyFuelEmpty; }
    public boolean shouldNotifyStorageFull() { return notifyStorageFull; }
    public boolean shouldNotifyFuelEmptyOnLogin() { return notifyFuelEmptyOnLogin; }
    public Map<Integer, TierConfig> getTiers() { return tiers; }
    public TierConfig getTier(int tier) { return tiers.get(tier); }
    public int getHighestTier() {
        return tiers.keySet().stream().max(Integer::compareTo).orElse(1);
    }

    /**
     * Get the next tier after the given tier, or null if at max
     */
    public TierConfig getNextTier(int currentTier) {
        int nextTierNum = currentTier + 1;
        return tiers.get(nextTierNum);
    }

    /**
     * Get GUI configuration value
     */
    public String getGUITitle() {
        return ChatColor.translateAlternateColorCodes('&',
                config.getString("gui.title", "&8&lMoney Printer"));
    }

    public int getGUIRows() {
        return Math.max(1, Math.min(6, config.getInt("gui.rows", 4)));
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

    public List<String> getButtonLore(String button, Map<String, String> placeholders) {
        List<String> lore = config.getStringList("gui.buttons." + button + ".lore");
        List<String> coloredLore = new ArrayList<>();

        for (String line : lore) {
            line = ChatColor.translateAlternateColorCodes('&', line);
            if (placeholders != null) {
                for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                    line = line.replace("{" + entry.getKey() + "}", entry.getValue());
                }
            }
            coloredLore.add(line);
        }

        return coloredLore;
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