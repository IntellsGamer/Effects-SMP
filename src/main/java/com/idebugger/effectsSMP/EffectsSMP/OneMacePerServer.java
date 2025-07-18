package com.idebugger.effectsSMP;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.block.CrafterCraftEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Level;

public class OneMacePerServer implements Listener {

    private final EffectsSMP plugin;
    private boolean allowMace;

    private NamespacedKey recipeKey;

    public OneMacePerServer(EffectsSMP plugin) {
        this.plugin = plugin;
        this.allowMace = plugin.getConfig().getBoolean("mace", true);
        this.recipeKey = new NamespacedKey(plugin, "mace");

        registerRecipe();
        if (!allowMace) {
            removeRecipe();
        }

        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    private void registerRecipe() {
        ItemStack mace = new ItemStack(Material.MACE);
        ShapedRecipe recipe = new ShapedRecipe(recipeKey, mace);

        recipe.shape(" I ", " I ", " S ");
        recipe.setIngredient('I', Material.IRON_INGOT);
        recipe.setIngredient('S', Material.STICK);

        Bukkit.addRecipe(recipe);
        plugin.getLogger().log(Level.INFO, "Mace recipe registered.");
    }

    private void removeRecipe() {
        if (Bukkit.removeRecipe(recipeKey)) {
            plugin.getLogger().log(Level.INFO, "Mace recipe removed.");
        }
    }

    @EventHandler
    public void onPrepareItemCraft(PrepareItemCraftEvent event) {
        ItemStack result = event.getInventory().getResult();
        if (result != null && result.getType() == Material.MACE && !allowMace) {
            event.getInventory().setResult(new ItemStack(Material.AIR));
        }
    }

    @EventHandler
    public void onCraftItem(CraftItemEvent event) {
        ItemStack result = event.getInventory().getResult();
        if (result != null && result.getType() == Material.MACE) {
            if (!allowMace) {
                event.setCancelled(true);
                event.getInventory().setResult(new ItemStack(Material.AIR));
                event.getWhoClicked().sendMessage("§cCrafting the mace is currently disabled.");
            } else {
                allowMace = false;
                plugin.getConfig().set("mace", false);
                plugin.saveConfig();
                removeRecipe();
                event.getWhoClicked().sendMessage("§aYou have crafted the only mace allowed on this server.");
                plugin.getLogger().info(event.getWhoClicked().getName() + " crafted the mace. Crafting disabled.");
            }
        }
    }

    @EventHandler
    public void onCrafterCraft(CrafterCraftEvent event) {
        ItemStack result = event.getResult();
        if (result != null && result.getType() == Material.MACE) {
            if (!allowMace || !plugin.getConfig().getBoolean("AllowCrafter", true)) {
                event.setCancelled(true);
                event.setResult(new ItemStack(Material.AIR));
            } else {
                allowMace = false;
                plugin.getConfig().set("mace", false);
                plugin.saveConfig();
                removeRecipe();
            }
        }
    }
}
