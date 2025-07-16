package com.idebugger.effectsSMP;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityResurrectEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.sql.*;
import java.util.*;
import java.util.stream.Collectors;

public class EffectsSMP extends JavaPlugin implements Listener, CommandExecutor, TabCompleter {

    private final Map<UUID, Integer> guiPages = new HashMap<>();
    private Connection connection;
    private List<PotionEffectType> allowedEffects = new ArrayList<>();
    private boolean autoRandomEnabled = false;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadAllowedEffects();
        initDatabase();
        getServer().getPluginManager().registerEvents(this, this);
        getServer().getPluginManager().registerEvents(new DragonEggEffectAmplifier(this), this);
        getCommand("smp").setExecutor(this);
        getCommand("smp").setTabCompleter(this);
    }

    private void loadAllowedEffects() {
        List<String> effects = getConfig().getStringList("allowed-effects");
        for (String name : effects) {
            PotionEffectType type = PotionEffectType.getByName(name.toUpperCase());
            if (type != null) allowedEffects.add(type);
        }
    }

    @Override
    public void onDisable() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void initDatabase() {
        try {
            connection = DriverManager.getConnection("jdbc:sqlite:" + getDataFolder() + "/effects.db");
            Statement statement = connection.createStatement();
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS permanent_effects (uuid TEXT PRIMARY KEY, effect TEXT, amplifier INTEGER)");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS autorandom_global (enabled INTEGER DEFAULT 0)");
            ResultSet rs = statement.executeQuery("SELECT enabled FROM autorandom_global");
            if (rs.next()) {
                autoRandomEnabled = rs.getInt("enabled") == 1;
            } else {
                statement.executeUpdate("INSERT INTO autorandom_global (enabled) VALUES (0)");
            }
            statement.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void applyEffect(Player player) {
        try {
            PreparedStatement ps = connection.prepareStatement("SELECT effect, amplifier FROM permanent_effects WHERE uuid = ?");
            ps.setString(1, player.getUniqueId().toString());
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                PotionEffectType type = PotionEffectType.getByName(rs.getString("effect"));
                int amp = rs.getInt("amplifier");
                if (type != null) {
                    player.addPotionEffect(new PotionEffect(type, Integer.MAX_VALUE, amp, true, false, false));
                }
            }
            ps.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void setEffect(UUID uuid, PotionEffectType type, int amp) {
        try {
            removeEffect(uuid);
            PreparedStatement ps = connection.prepareStatement("INSERT INTO permanent_effects (uuid, effect, amplifier) VALUES (?, ?, ?)");
            ps.setString(1, uuid.toString());
            ps.setString(2, type.getName());
            ps.setInt(3, amp);
            ps.executeUpdate();
            ps.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void removeEffect(UUID uuid) {
        try {
            PreparedStatement del = connection.prepareStatement("DELETE FROM permanent_effects WHERE uuid = ?");
            del.setString(1, uuid.toString());
            del.executeUpdate();
            del.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void giveRandomEffect(Player player) {
        if (allowedEffects.isEmpty()) return;

        player.getActivePotionEffects().forEach(effect -> player.removePotionEffect(effect.getType()));
        removeEffect(player.getUniqueId());

        PotionEffectType randomType = allowedEffects.get(new Random().nextInt(allowedEffects.size()));
        int amp = new Random().nextInt(3);
        setEffect(player.getUniqueId(), randomType, amp);
        applyEffect(player);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (autoRandomEnabled && !hasEffect(e.getPlayer().getUniqueId())) {
                giveRandomEffect(e.getPlayer());
            } else {
                applyEffect(e.getPlayer());
            }
        }, 20L);
    }

    private boolean hasEffect(UUID uuid) {
        try {
            PreparedStatement ps = connection.prepareStatement("SELECT 1 FROM permanent_effects WHERE uuid = ?");
            ps.setString(1, uuid.toString());
            ResultSet rs = ps.executeQuery();
            boolean exists = rs.next();
            ps.close();
            return exists;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent e) {
        Bukkit.getScheduler().runTaskLater(this, () -> applyEffect(e.getPlayer()), 20L);
    }

    @EventHandler
    public void onMilk(PlayerItemConsumeEvent e) {
        if (e.getItem().getType() == Material.MILK_BUCKET) {
            Bukkit.getScheduler().runTaskLater(this, () -> applyEffect(e.getPlayer()), 10L);
        }
    }

    @EventHandler
    public void onTotem(EntityResurrectEvent e) {
        if (e.getEntity() instanceof Player p) {
            Bukkit.getScheduler().runTaskLater(this, () -> applyEffect(p), 10L);
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(ChatColor.RED + "Usage: /smp <set|remove|random|randomall|autorandom|list|gui>");
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "autorandom":
                if (sender.hasPermission("effects.admin")) {
                    autoRandomEnabled = !autoRandomEnabled;
                    try {
                        PreparedStatement ps = connection.prepareStatement("UPDATE autorandom_global SET enabled = ?");
                        ps.setInt(1, autoRandomEnabled ? 1 : 0);
                        ps.executeUpdate();
                        ps.close();
                    } catch (SQLException e) {
                        e.printStackTrace();
                    }
                    sender.sendMessage(ChatColor.GREEN + "Global auto-random is now " + (autoRandomEnabled ? "enabled" : "disabled"));
                }
                return true;
            case "set":
                if (args.length < 4) {
                    sender.sendMessage(ChatColor.RED + "Usage: /smp set <player> <effect> <amplifier>");
                    return true;
                }
                if (sender.hasPermission("effects.admin")) {
                    Player target = Bukkit.getPlayer(args[1]);
                    if (target == null) {
                        sender.sendMessage(ChatColor.RED + "Player not found.");
                        return true;
                    }
                    PotionEffectType type = PotionEffectType.getByName(args[2].toUpperCase());
                    if (type == null) {
                        sender.sendMessage(ChatColor.RED + "Invalid effect type.");
                        return true;
                    }
                    int amp;
                    try {
                        amp = Integer.parseInt(args[3]) - 1;
                        if (amp < 0) throw new NumberFormatException();
                    } catch (NumberFormatException ex) {
                        sender.sendMessage(ChatColor.RED + "Amplifier must be a number 1 or higher.");
                        return true;
                    }
                    target.removePotionEffect(type);
                    setEffect(target.getUniqueId(), type, amp);
                    applyEffect(target);
                    sender.sendMessage(ChatColor.GREEN + "Effect " + type.getName() + " " + (amp + 1) + " applied to " + target.getName());
                }
                return true;
            case "remove":
                if (args.length < 2) {
                    sender.sendMessage(ChatColor.RED + "Usage: /smp remove <player>");
                    return true;
                }
                if (sender.hasPermission("effects.admin")) {
                    Player targetRemove = Bukkit.getPlayer(args[1]);
                    if (targetRemove == null) {
                        sender.sendMessage(ChatColor.RED + "Player not found.");
                        return true;
                    }
                    targetRemove.getActivePotionEffects().forEach(effect -> targetRemove.removePotionEffect(effect.getType()));
                    removeEffect(targetRemove.getUniqueId());
                    sender.sendMessage(ChatColor.GREEN + "Effect removed from " + targetRemove.getName());
                    return true;
                }
            case "random":
                if (args.length < 2) {
                    sender.sendMessage(ChatColor.RED + "Usage: /smp random <player>");
                    return true;
                }
                if (sender.hasPermission("effects.admin")) {
                    Player randomTarget = Bukkit.getPlayer(args[1]);

                    if (randomTarget == null) {
                        sender.sendMessage(ChatColor.RED + "Player not found.");
                        return true;
                    }
                    giveRandomEffect(randomTarget);
                    sender.sendMessage(ChatColor.GREEN + "Random effect given to " + randomTarget.getName());
                    return true;
                }
            case "randomall":
                if (sender.hasPermission("effects.admin")) {
                    Bukkit.getOnlinePlayers().forEach(this::giveRandomEffect);
                    sender.sendMessage(ChatColor.GREEN + "Random effects given to all players.");
                    return true;
                }
            case "list":
                try {
                    PreparedStatement ps = connection.prepareStatement("SELECT uuid, effect, amplifier FROM permanent_effects");
                    ResultSet rs = ps.executeQuery();

                    sender.sendMessage(ChatColor.GOLD + String.format("%-25s %-20s", "Player", "Effect"));
                    while (rs.next()) {
                        String uuid = rs.getString("uuid");
                        String effect = rs.getString("effect");
                        int amplifier = rs.getInt("amplifier") + 1;
                        OfflinePlayer offp = Bukkit.getOfflinePlayer(UUID.fromString(uuid));
                        String name = offp.getName() != null ? offp.getName() : uuid;
                        sender.sendMessage(ChatColor.YELLOW + String.format("%-25s %-20s", name, effect + " " + amplifier));
                    }
                    ps.close();
                } catch (SQLException e) {
                    sender.sendMessage(ChatColor.RED + "An error occurred while accessing the database.");
                    e.printStackTrace();
                }
                return true;

            case "gui":
                if (!(sender instanceof Player)) {
                    sender.sendMessage(ChatColor.RED + "Only players can use this command.");
                    return true;
                }
                Player viewer = (Player) sender;
                Bukkit.getScheduler().runTask(this, () -> openEffectsGui(viewer, 0));
                return true;

            default:
                sender.sendMessage(ChatColor.RED + "Unknown subcommand. Use /smp <set|remove|random|randomall|autorandom|list|gui>");
                return true;
        }
    }

    private void openEffectsGui(Player player, int page) {
        guiPages.put(player.getUniqueId(), page);

        List<ItemStack> heads = new ArrayList<>();
        try {
            PreparedStatement ps = connection.prepareStatement("SELECT uuid, effect, amplifier FROM permanent_effects");
            ResultSet rs = ps.executeQuery();

            while (rs.next()) {
                UUID uuid = UUID.fromString(rs.getString("uuid"));
                String effect = rs.getString("effect");
                int amplifier = rs.getInt("amplifier") + 1;
                OfflinePlayer offp = Bukkit.getOfflinePlayer(uuid);

                ItemStack head = new ItemStack(Material.PLAYER_HEAD);
                SkullMeta meta = (SkullMeta) head.getItemMeta();
                if (meta != null) {
                    meta.setOwningPlayer(offp);
                    meta.setDisplayName(ChatColor.AQUA + (offp.getName() != null ? offp.getName() : uuid.toString()));
                    meta.setLore(Arrays.asList(ChatColor.GRAY + "Effect: " + effect, ChatColor.GRAY + "Level: " + amplifier));
                    head.setItemMeta(meta);
                }
                heads.add(head);
            }
            ps.close();
        } catch (SQLException e) {
            e.printStackTrace();
            return;
        }

        int start = page * 45;
        int end = Math.min(start + 45, heads.size());

        Inventory inv = Bukkit.createInventory(null, 54, ChatColor.DARK_PURPLE + "Permanent Effects");

        for (int i = start; i < end; i++) {
            inv.setItem(i - start, heads.get(i));
        }

        ItemStack prev = new ItemStack(Material.ARROW);
        ItemMeta prevMeta = prev.getItemMeta();
        prevMeta.setDisplayName(ChatColor.YELLOW + "Previous Page");
        prev.setItemMeta(prevMeta);

        ItemStack next = new ItemStack(Material.ARROW);
        ItemMeta nextMeta = next.getItemMeta();
        nextMeta.setDisplayName(ChatColor.YELLOW + "Next Page");
        next.setItemMeta(nextMeta);

        inv.setItem(45, prev);
        inv.setItem(53, next);

        player.openInventory(inv);
    }

    private boolean isNetheriteArmor(ItemStack item) {
        if (item == null) return false;
        Material type = item.getType();
        return type == Material.NETHERITE_HELMET ||
                type == Material.NETHERITE_CHESTPLATE ||
                type == Material.NETHERITE_LEGGINGS ||
                type == Material.NETHERITE_BOOTS;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        HumanEntity clicker = event.getWhoClicked();
        if (!(clicker instanceof Player player)) return;
        if (!event.getView().getTitle().equals(ChatColor.DARK_PURPLE + "Permanent Effects")) {
            if (event.getSlotType() == InventoryType.SlotType.ARMOR) {
                ItemStack newItem = event.getCursor();
                if (isNetheriteArmor(newItem)) {
                    event.setCancelled(true);
                    ((Player) event.getWhoClicked()).sendMessage("§cNetherite armor is not allowed!");
                }
            }

            ItemStack currentItem = event.getCurrentItem();
            if (isNetheriteArmor(currentItem) && event.getSlotType() == InventoryType.SlotType.CONTAINER && event.isShiftClick()) {
                event.setCancelled(true);
                ((Player) event.getWhoClicked()).sendMessage("§cNetherite armor is not allowed!");
            }
            return;
        };

        event.setCancelled(true);

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() != Material.ARROW || !clicked.hasItemMeta()) return;

        String name = clicked.getItemMeta().getDisplayName();
        int currentPage = guiPages.getOrDefault(player.getUniqueId(), 0);

        if (name.contains("Next Page")) {
            openEffectsGui(player, currentPage + 1);
        } else if (name.contains("Previous Page") && currentPage > 0) {
            openEffectsGui(player, currentPage - 1);
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getView().getTitle().equals(ChatColor.DARK_PURPLE + "Permanent Effects")) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        ItemStack item = event.getItem();
        if (isNetheriteArmor(item) && event.getHand() == EquipmentSlot.HAND) {
            event.setCancelled(true);
            event.getPlayer().sendMessage("§cYou can't equip Netherite armor!");
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return Arrays.asList("set", "remove", "random", "randomall", "autorandom", "list", "gui");
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("set")) {
            return Arrays.stream(PotionEffectType.values()).map(PotionEffectType::getName).collect(Collectors.toList());
        }
        return null;
    }

    public Connection getConnection() {
        return connection;
    }
}
