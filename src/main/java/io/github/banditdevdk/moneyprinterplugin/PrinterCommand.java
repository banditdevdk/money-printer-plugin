package io.github.banditdevdk.moneyprinterplugin;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.profile.PlayerProfile;
import org.bukkit.profile.PlayerTextures;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;

/**
 * Handles /moneyprinter (/mp) commands
 */
public class PrinterCommand implements CommandExecutor {
    private final MoneyPrinterPlugin plugin;

    public PrinterCommand(MoneyPrinterPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        // No args or "help" - show help
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            showHelp(sender);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "remove":
            case "fjern":
                return handleRemove(sender);

            case "give":
                return handleGive(sender, args);

            case "list":
                return handleList(sender);

            case "reload":
                return handleReload(sender);

            case "addfriend":
            case "friend":
                return handleAddFriend(sender, args);

            case "removefriend":
            case "unfriend":
                return handleRemoveFriend(sender, args);

            case "friends":
            case "listfriends":
                return handleListFriends(sender);

            default:
                showHelp(sender);
                return true;
        }
    }

    /**
     * Show help message
     */
    private void showHelp(CommandSender sender) {
        ConfigManager config = plugin.getConfigManager();
        sender.sendMessage(config.getMessage("help-header"));
        sender.sendMessage(config.getMessage("help-remove"));
        sender.sendMessage(config.getMessage("help-addfriend"));
        sender.sendMessage(config.getMessage("help-removefriend"));
        sender.sendMessage(config.getMessage("help-friends"));

        if (sender.hasPermission("printer.admin")) {
            sender.sendMessage(config.getMessage("help-give"));
            sender.sendMessage(config.getMessage("help-list"));
            sender.sendMessage(config.getMessage("help-reload"));
        }
    }

    /**
     * Handle /mp remove command
     */
    private boolean handleRemove(CommandSender sender) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("This command can only be used by players.");
            return true;
        }

        Player player = (Player) sender;
        ConfigManager config = plugin.getConfigManager();

        if (!player.hasPermission("printer.remove")) {
            player.sendMessage("§cYou don't have permission to use this command.");
            return true;
        }

        // Get the block the player is looking at
        Block block = player.getTargetBlockExact(5);
        if (block == null || block.getType() != Material.PLAYER_HEAD) {
            player.sendMessage(config.getMessage("look-at-printer"));
            return true;
        }

        Location loc = block.getLocation();
        PrinterData.PrinterInfo printer = plugin.getPrinterData().getPrinter(loc);

        if (printer == null) {
            player.sendMessage(config.getMessage("not-printer"));
            return true;
        }

        // Check ownership or admin permission
        if (!printer.getOwner().equals(player.getUniqueId()) && !player.hasPermission("printer.admin")) {
            player.sendMessage(config.getMessage("not-owner"));
            return true;
        }

        // Pay out remaining earnings
        double earnings = printer.getEarnings();
        if (earnings > 0) {
            plugin.getEconomy().depositPlayer(player, earnings);
        }

        // Get tier for the item
        int tier = printer.getTier();
        ConfigManager.TierConfig tierConfig = config.getTier(tier);

        // Remove printer data
        plugin.getPrinterData().removePrinter(loc);

        // Remove block
        block.setType(Material.AIR);

        // Give printer item back
        ItemStack printerItem = createPrinterItem(tier);
        player.getInventory().addItem(printerItem);

        // Send message
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("tier", tierConfig.getName());
        player.sendMessage(config.getMessage("removed", placeholders));

        return true;
    }

    /**
     * Handle /mp give command
     */
    private boolean handleGive(CommandSender sender, String[] args) {
        ConfigManager config = plugin.getConfigManager();

        if (!sender.hasPermission("printer.admin")) {
            sender.sendMessage("§cYou don't have permission to use this command.");
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage("§cUsage: /mp give <player> [tier]");
            return true;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage("§cPlayer not found.");
            return true;
        }

        int tier = 1;
        if (args.length >= 3) {
            try {
                tier = Integer.parseInt(args[2]);
                if (!config.getTiers().containsKey(tier)) {
                    sender.sendMessage("§cInvalid tier. Available tiers: " + config.getTiers().keySet());
                    return true;
                }
            } catch (NumberFormatException e) {
                sender.sendMessage("§cInvalid tier number.");
                return true;
            }
        }

        ItemStack printerItem = createPrinterItem(tier);
        target.getInventory().addItem(printerItem);

        ConfigManager.TierConfig tierConfig = config.getTier(tier);
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("player", target.getName());
        placeholders.put("tier", tierConfig.getName());

        sender.sendMessage(config.getMessage("given", placeholders));
        target.sendMessage(config.getMessage("received", placeholders));

        return true;
    }

    /**
     * Handle /mp list command
     */
    private boolean handleList(CommandSender sender) {
        if (!sender.hasPermission("printer.admin")) {
            sender.sendMessage("§cYou don't have permission to use this command.");
            return true;
        }

        Map<Location, PrinterData.PrinterInfo> printers = plugin.getPrinterData().getAllPrinters();

        if (printers.isEmpty()) {
            sender.sendMessage("§cThere are no printers on the server.");
            return true;
        }

        sender.sendMessage("§7§m-----§r §ePrinter List §7§m-----");
        for (Map.Entry<Location, PrinterData.PrinterInfo> entry : printers.entrySet()) {
            Location loc = entry.getKey();
            PrinterData.PrinterInfo printer = entry.getValue();

            String owner = Bukkit.getOfflinePlayer(printer.getOwner()).getName();
            ConfigManager.TierConfig tierConfig = plugin.getConfigManager().getTier(printer.getTier());

            sender.sendMessage(String.format("§7Owner: §f%s §7| Tier: §a%s §7| Money: §a%.2f$ §7| Fuel: §f%s",
                    owner, tierConfig.getName(), printer.getEarnings(), printer.getFormattedFuelTime()));
        }

        return true;
    }

    /**
     * Handle /mp reload command
     */
    private boolean handleReload(CommandSender sender) {
        if (!sender.hasPermission("printer.admin")) {
            sender.sendMessage("§cYou don't have permission to use this command.");
            return true;
        }

        plugin.getConfigManager().loadConfig();
        sender.sendMessage("§aConfiguration reloaded successfully!");

        return true;
    }

    /**
     * Create a printer item with custom skull texture
     */
    private ItemStack createPrinterItem(int tier) {
        ConfigManager config = plugin.getConfigManager();
        ConfigManager.TierConfig tierConfig = config.getTier(tier);

        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) item.getItemMeta();

        if (meta != null) {
            meta.setDisplayName("§aMoney Printer §7(" + tierConfig.getName() + ")");

            // Apply custom texture if available
            if (tierConfig.hasCustomTexture()) {
                try {
                    // Create a player profile with the custom texture
                    PlayerProfile profile = Bukkit.createPlayerProfile(UUID.randomUUID());
                    PlayerTextures textures = profile.getTextures();

                    // The texture URL format: http://textures.minecraft.net/texture/[base64_value]
                    String textureValue = tierConfig.getSkullTexture();

                    // If it's a base64 value (starts with eyJ), convert to URL
                    if (textureValue.startsWith("eyJ")) {
                        // For now, just log that custom textures need URLs
                        plugin.getLogger().warning("Custom skull textures require texture URLs, not base64 values.");
                        plugin.getLogger().warning("Please use format: http://textures.minecraft.net/texture/HASH");
                    } else if (textureValue.startsWith("http")) {
                        // It's already a URL
                        textures.setSkin(new URL(textureValue));
                        profile.setTextures(textures);
                        meta.setOwnerProfile(profile);
                    }
                } catch (MalformedURLException e) {
                    plugin.getLogger().warning("Invalid skull texture URL for tier " + tier);
                }
            }

            item.setItemMeta(meta);
        }

        return item;
    }

    /**
     * Handle /mp addfriend command
     */
    private boolean handleAddFriend(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("This command can only be used by players.");
            return true;
        }

        Player player = (Player) sender;

        if (args.length < 2) {
            player.sendMessage("§cUsage: /mp addfriend <player>");
            return true;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            player.sendMessage("§cPlayer not found.");
            return true;
        }

        if (target.equals(player)) {
            player.sendMessage("§cYou can't add yourself as a friend!");
            return true;
        }

        Block block = player.getTargetBlockExact(5);
        if (block == null || block.getType() != Material.PLAYER_HEAD) {
            player.sendMessage("§cYou must be looking at a printer to add friends.");
            return true;
        }

        Location loc = block.getLocation();
        PrinterData.PrinterInfo printer = plugin.getPrinterData().getPrinter(loc);

        if (printer == null) {
            player.sendMessage(plugin.getConfigManager().getMessage("not-printer"));
            return true;
        }

        if (!printer.getOwner().equals(player.getUniqueId())) {
            player.sendMessage(plugin.getConfigManager().getMessage("not-owner"));
            return true;
        }

        if (printer.isFriend(target.getUniqueId())) {
            player.sendMessage("§c" + target.getName() + " is already a friend of this printer!");
            return true;
        }

        printer.addFriend(target.getUniqueId());
        plugin.getPrinterData().saveData();

        player.sendMessage("§aAdded " + target.getName() + " as a friend to this printer!");
        target.sendMessage("§a" + player.getName() + " added you as a friend to their printer!");

        return true;
    }

    /**
     * Handle /mp removefriend command
     */
    private boolean handleRemoveFriend(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("This command can only be used by players.");
            return true;
        }

        Player player = (Player) sender;

        if (args.length < 2) {
            player.sendMessage("§cUsage: /mp removefriend <player>");
            return true;
        }

        OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
        if (!target.hasPlayedBefore()) {
            player.sendMessage("§cPlayer not found.");
            return true;
        }

        Block block = player.getTargetBlockExact(5);
        if (block == null || block.getType() != Material.PLAYER_HEAD) {
            player.sendMessage("§cYou must be looking at a printer to remove friends.");
            return true;
        }

        Location loc = block.getLocation();
        PrinterData.PrinterInfo printer = plugin.getPrinterData().getPrinter(loc);

        if (printer == null) {
            player.sendMessage(plugin.getConfigManager().getMessage("not-printer"));
            return true;
        }

        if (!printer.getOwner().equals(player.getUniqueId())) {
            player.sendMessage(plugin.getConfigManager().getMessage("not-owner"));
            return true;
        }

        if (!printer.isFriend(target.getUniqueId())) {
            player.sendMessage("§c" + target.getName() + " is not a friend of this printer!");
            return true;
        }

        printer.removeFriend(target.getUniqueId());
        plugin.getPrinterData().saveData();

        player.sendMessage("§aRemoved " + target.getName() + " from this printer's friends!");
        if (target.isOnline()) {
            target.getPlayer().sendMessage("§c" + player.getName() + " removed you as a friend from their printer.");
        }

        return true;
    }

    /**
     * Handle /mp friends command
     */
    private boolean handleListFriends(CommandSender sender) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("This command can only be used by players.");
            return true;
        }

        Player player = (Player) sender;

        Block block = player.getTargetBlockExact(5);
        if (block == null || block.getType() != Material.PLAYER_HEAD) {
            player.sendMessage("§cYou must be looking at a printer to list friends.");
            return true;
        }

        Location loc = block.getLocation();
        PrinterData.PrinterInfo printer = plugin.getPrinterData().getPrinter(loc);

        if (printer == null) {
            player.sendMessage(plugin.getConfigManager().getMessage("not-printer"));
            return true;
        }

        if (!printer.canAccess(player.getUniqueId()) && !player.hasPermission("printer.admin")) {
            player.sendMessage(plugin.getConfigManager().getMessage("not-owner"));
            return true;
        }

        Set<UUID> friends = printer.getFriends();

        if (friends.isEmpty()) {
            player.sendMessage("§7This printer has no friends added.");
            return true;
        }

        player.sendMessage("§7§m-----§r §ePrinter Friends §7§m-----");
        for (UUID friendUUID : friends) {
            OfflinePlayer friend = Bukkit.getOfflinePlayer(friendUUID);
            String status = friend.isOnline() ? "§a●" : "§7●";
            player.sendMessage(status + " §f" + friend.getName());
        }

        return true;
    }
}