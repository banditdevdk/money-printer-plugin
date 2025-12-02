package io.github.banditdevdk.moneyprinterplugin;

import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * Manages all printer data storage and persistence
 */
public class PrinterData {
    private final MoneyPrinterPlugin plugin;
    private final Map<Location, PrinterInfo> printers = new HashMap<>();
    private File dataFile;
    private FileConfiguration dataConfig;

    public PrinterData(MoneyPrinterPlugin plugin) {
        this.plugin = plugin;
        loadData();
    }

    /**
     * Register a new printer
     */
    public void registerPrinter(Location loc, UUID owner, int tier) {
        PrinterInfo info = new PrinterInfo(owner, tier);
        printers.put(loc, info);
        saveData();
    }

    /**
     * Remove a printer
     */
    public void removePrinter(Location loc) {
        printers.remove(loc);
        saveData();
    }

    /**
     * Get printer info at location
     */
    public PrinterInfo getPrinter(Location loc) {
        return printers.get(loc);
    }

    /**
     * Check if location has a printer
     */
    public boolean isPrinter(Location loc) {
        return printers.containsKey(loc);
    }

    /**
     * Get all printers
     */
    public Map<Location, PrinterInfo> getAllPrinters() {
        return new HashMap<>(printers);
    }

    /**
     * Get all printers owned by a player
     */
    public List<PrinterInfo> getPrintersByOwner(UUID owner) {
        List<PrinterInfo> result = new ArrayList<>();
        for (PrinterInfo info : printers.values()) {
            if (info.getOwner().equals(owner)) {
                result.add(info);
            }
        }
        return result;
    }

    /**
     * Count printers owned by a player
     */
    public int countPrintersByOwner(UUID owner) {
        int count = 0;
        for (PrinterInfo info : printers.values()) {
            if (info.getOwner().equals(owner)) {
                count++;
            }
        }
        return count;
    }

    /**
     * Load data from file
     */
    private void loadData() {
        dataFile = new File(plugin.getDataFolder(), "printers.yml");
        if (!dataFile.exists()) {
            plugin.getDataFolder().mkdirs();
            try {
                dataFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("Could not create printers.yml!");
                e.printStackTrace();
            }
        }
        dataConfig = YamlConfiguration.loadConfiguration(dataFile);

        // Load printers from config
        if (dataConfig.contains("printers")) {
            for (String key : dataConfig.getConfigurationSection("printers").getKeys(false)) {
                String path = "printers." + key;

                // Parse location from key (world_x_y_z)
                String[] parts = key.split("_");
                if (parts.length != 4) continue;

                Location loc = new Location(
                        plugin.getServer().getWorld(parts[0]),
                        Double.parseDouble(parts[1]),
                        Double.parseDouble(parts[2]),
                        Double.parseDouble(parts[3])
                );

                UUID owner = UUID.fromString(dataConfig.getString(path + ".owner"));
                int tier = dataConfig.getInt(path + ".tier", 1);
                int fuelTime = dataConfig.getInt(path + ".fuelTime", 0);
                int fuelTicks = dataConfig.getInt(path + ".fuelTicks", 0);
                double earnings = dataConfig.getDouble(path + ".earnings", 0.0);
                List<String> friendUUIDs = dataConfig.getStringList(path + ".friends");

                PrinterInfo info = new PrinterInfo(owner, tier);
                info.setFuelTime(fuelTime);
                info.setFuelTicks(fuelTicks);
                info.setEarnings(earnings);

                // Load friends
                for (String friendUUID : friendUUIDs) {
                    try {
                        info.addFriend(UUID.fromString(friendUUID));
                    } catch (IllegalArgumentException e) {
                        plugin.getLogger().warning("Invalid friend UUID in printer data: " + friendUUID);
                    }
                }

                printers.put(loc, info);
            }
        }

        plugin.getLogger().info("Loaded " + printers.size() + " printers");
    }

    /**
     * Save data to file
     */
    public void saveData() {
        dataConfig.set("printers", null); // Clear existing data

        for (Map.Entry<Location, PrinterInfo> entry : printers.entrySet()) {
            Location loc = entry.getKey();
            PrinterInfo info = entry.getValue();

            // Create key from location (world_x_y_z)
            String key = loc.getWorld().getName() + "_" +
                    loc.getBlockX() + "_" +
                    loc.getBlockY() + "_" +
                    loc.getBlockZ();

            String path = "printers." + key;
            dataConfig.set(path + ".owner", info.getOwner().toString());
            dataConfig.set(path + ".tier", info.getTier());
            dataConfig.set(path + ".fuelTime", info.getFuelTime());
            dataConfig.set(path + ".fuelTicks", info.getFuelTicks());
            dataConfig.set(path + ".earnings", info.getEarnings());

            // Save friends
            List<String> friendUUIDs = new ArrayList<>();
            for (UUID friendUUID : info.getFriends()) {
                friendUUIDs.add(friendUUID.toString());
            }
            dataConfig.set(path + ".friends", friendUUIDs);
        }

        try {
            dataConfig.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save printers.yml!");
            e.printStackTrace();
        }
    }

    /**
     * Inner class to hold printer information
     */
    public static class PrinterInfo {
        private UUID owner;
        private int tier;
        private int fuelTime; // Seconds remaining
        private int fuelTicks; // Ticks toward next payout
        private double earnings;
        private boolean notifiedEmpty;
        private boolean notifiedFull;
        private Set<UUID> friends; // Friends who can access this printer

        public PrinterInfo(UUID owner, int tier) {
            this.owner = owner;
            this.tier = tier;
            this.fuelTime = 0;
            this.fuelTicks = 0;
            this.earnings = 0.0;
            this.notifiedEmpty = false;
            this.notifiedFull = false;
            this.friends = new HashSet<>();
        }

        // Getters and setters
        public UUID getOwner() { return owner; }
        public int getTier() { return tier; }
        public void setTier(int tier) { this.tier = tier; }

        public int getFuelTime() { return fuelTime; }
        public void setFuelTime(int fuelTime) {
            this.fuelTime = fuelTime;
            if (fuelTime > 0) notifiedEmpty = false;
        }
        public void addFuelTime(int seconds) {
            this.fuelTime += seconds;
        }

        public int getFuelTicks() { return fuelTicks; }
        public void setFuelTicks(int ticks) { this.fuelTicks = ticks; }

        public double getEarnings() { return earnings; }
        public void setEarnings(double earnings) {
            this.earnings = earnings;
        }
        public void addEarnings(double amount) {
            this.earnings += amount;
        }

        public boolean hasNotifiedEmpty() { return notifiedEmpty; }
        public void setNotifiedEmpty(boolean notified) { this.notifiedEmpty = notified; }

        public boolean hasNotifiedFull() { return notifiedFull; }
        public void setNotifiedFull(boolean notified) { this.notifiedFull = notified; }

        // Friends management
        public Set<UUID> getFriends() { return new HashSet<>(friends); }
        public void addFriend(UUID friendUUID) { friends.add(friendUUID); }
        public void removeFriend(UUID friendUUID) { friends.remove(friendUUID); }
        public boolean isFriend(UUID playerUUID) { return friends.contains(playerUUID); }
        public boolean canAccess(UUID playerUUID) {
            return owner.equals(playerUUID) || friends.contains(playerUUID);
        }

        /**
         * Get earnings rate based on tier from config
         */
        public double getEarningsRate(ConfigManager config) {
            ConfigManager.TierConfig tierConfig = config.getTier(tier);
            return tierConfig != null ? tierConfig.getEarnings() : 10.0;
        }

        /**
         * Get formatted fuel time string (MM:SS)
         */
        public String getFormattedFuelTime() {
            if (fuelTime <= 0) return "00:00";

            int minutes = fuelTime / 60;
            int seconds = fuelTime % 60;

            return String.format("%02d:%02d", minutes, seconds);
        }
    }
}