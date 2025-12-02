package io.github.banditdevdk.moneyprinterplugin;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

public final class MoneyPrinterPlugin extends JavaPlugin {

    private static MoneyPrinterPlugin instance;
    private Economy economy;
    private ConfigManager configManager;
    private PrinterData printerData;
    private PrinterGUI printerGUI;

    @Override
    public void onEnable() {
        instance = this;

        // Setup Vault economy
        if (!setupEconomy()) {
            getLogger().severe("Vault not found! Disabling plugin.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // Load configuration
        configManager = new ConfigManager(this);

        // Initialize data management
        printerData = new PrinterData(this);

        // Initialize GUI system
        printerGUI = new PrinterGUI(this);

        // Register events
        getServer().getPluginManager().registerEvents(new PrinterEvents(this), this);

        // Register commands
        PrinterCommand commandExecutor = new PrinterCommand(this);
        getCommand("mp").setExecutor(commandExecutor);
        getCommand("moneyprinter").setExecutor(commandExecutor);
        getCommand("printer").setExecutor(commandExecutor);

        // Start printer task (runs every 10 seconds)
        new PrinterTask(this).runTaskTimer(this, 200L, 200L); // 200 ticks = 10 seconds

        getLogger().info("MoneyPrinterPlugin enabled successfully!");
    }

    @Override
    public void onDisable() {
        // Save all printer data
        if (printerData != null) {
            printerData.saveData();
        }

        getLogger().info("MoneyPrinterPlugin disabled!");
    }

    /**
     * Setup Vault economy
     */
    private boolean setupEconomy() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) {
            return false;
        }

        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager()
                .getRegistration(Economy.class);

        if (rsp == null) {
            return false;
        }

        economy = rsp.getProvider();
        return economy != null;
    }

    // Getters
    public static MoneyPrinterPlugin getInstance() {
        return instance;
    }

    public Economy getEconomy() {
        return economy;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public PrinterData getPrinterData() {
        return printerData;
    }

    public PrinterGUI getPrinterGUI() {
        return printerGUI;
    }
}