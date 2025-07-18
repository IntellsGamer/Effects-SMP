package com.idebugger.effectsSMP;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerAdvancementDoneEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class OneMacePerServer implements Listener {
    private final EffectsSMP plugin;

    public OneMacePerServer(EffectsSMP plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onMaceCrafted(PlayerAdvancementDoneEvent event) {
        if (!event.getAdvancement().getKey().toString().equals("minecraft:crafting/mace")) return;
        try {
            PreparedStatement createTable = plugin.getConnection().prepareStatement(
                "CREATE TABLE IF NOT EXISTS mace_owners (uuid TEXT PRIMARY KEY)"
            );
            createTable.executeUpdate();
            createTable.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                PreparedStatement ps = plugin.getConnection().prepareStatement("SELECT COUNT(*) FROM mace_owners WHERE uuid = ?");
                ps.setString(1, player.getUniqueId().toString());
                ResultSet rs = ps.executeQuery();

                if (rs.next() && rs.getInt(1) > 0) {
                    player.sendMessage("§cA mace has already been crafted by another player on this server.");
                    player.getInventory().removeItem(new ItemStack(Material.HEAVY_CORE, 1));
                    rs.close();
                    return;
                }

                PreparedStatement insert = plugin.getConnection().prepareStatement("INSERT INTO mace_owners (uuid) VALUES (?)");
                insert.setString(1, player.getUniqueId().toString());
                insert.executeUpdate();
                insert.close();

                player.sendMessage("§aYou have successfully crafted a mace! You are the first to do so on this server.");
            } catch (SQLException e) {
                e.printStackTrace();
            }
        });
    }
}
