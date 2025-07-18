package com.idebugger.effectsSMP;

import org.bukkit.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Collections;

public class RerollBeacon implements Listener {

    private final EffectsSMP plugin;
    private final NamespacedKey recipeKey;

    public RerollBeacon(EffectsSMP plugin) {
        this.plugin = plugin;
        this.recipeKey = new NamespacedKey(plugin, "reroll_beacon");
        registerRecipe();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    private void registerRecipe() {
        ItemStack beacon = new ItemStack(Material.BEACON);
        ItemMeta meta = beacon.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.LIGHT_PURPLE + "Beacon of Reroll");
            meta.setCustomModelData(1);
            meta.setLore(Collections.singletonList(ChatColor.GRAY + "Right-click in main hand to reroll your effect"));
            beacon.setItemMeta(meta);
        }

        ShapedRecipe recipe = new ShapedRecipe(recipeKey, beacon);
        recipe.shape("DND", "IWI", "DND");
        recipe.setIngredient('D', Material.DIAMOND_BLOCK);
        recipe.setIngredient('N', Material.NETHERITE_BLOCK);
        recipe.setIngredient('I', Material.IRON_BLOCK);
        recipe.setIngredient('W', Material.NETHER_STAR);

        Bukkit.addRecipe(recipe);
    }

    @EventHandler
    public void onRightClick(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;

        ItemStack item = event.getItem();
        if (item == null || item.getType() != Material.BEACON) return;

        ItemMeta meta = item.getItemMeta();
        if (meta == null || !(ChatColor.LIGHT_PURPLE + "Beacon of Reroll").equals(meta.getDisplayName())) return;

        event.setCancelled(true);

        plugin.getServer().getScheduler().runTask(plugin, () -> {
            plugin.getLogger().info("Rerolling effect for " + event.getPlayer().getName());
            plugin.getServer().broadcastMessage(ChatColor.AQUA + event.getPlayer().getName() + " has rerolled their effect!");

            plugin.giveRandomEffect(event.getPlayer());

            item.setAmount(item.getAmount() - 1);
            event.getPlayer().setItemInHand(item);
            event.getPlayer().playSound(event.getPlayer().getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.2f);
        });
    }
}
